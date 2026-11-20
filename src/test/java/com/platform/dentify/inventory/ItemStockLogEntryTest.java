package com.platform.dentify.inventory;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.iam.infrastructure.repositories.UserRepository;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.application.internal.commandservices.ItemCommandServiceImpl;
import com.platform.dentify.inventory.application.internal.queryservices.LogEntryQueryServiceImpl;
import com.platform.dentify.inventory.domain.model.aggregates.Item;
import com.platform.dentify.inventory.domain.model.aggregates.LogEntry;
import com.platform.dentify.inventory.domain.model.commands.CreateItemCommand;
import com.platform.dentify.inventory.domain.model.commands.UpdateItemCommand;
import com.platform.dentify.inventory.domain.model.queries.GetAllLogEntriesByUserIdQuery;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.ItemRepository;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.LogEntryRepository;
import com.platform.dentify.inventory.interfaces.rest.assemblers.LogEntryResourceFromEntityAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemStockLogEntryTest {

    private static final Long USER_ID = 5L;

    private ItemRepository itemRepository;
    private LogEntryRepository logEntryRepository;
    private UserRepository userRepository;
    private ItemCommandServiceImpl itemCommandService;
    private LogEntryQueryServiceImpl logEntryQueryService;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        logEntryRepository = mock(LogEntryRepository.class);
        userRepository = mock(UserRepository.class);
        AuthenticatedUserProvider userProvider = mock(AuthenticatedUserProvider.class);
        when(userProvider.getCurrentUserId()).thenReturn(USER_ID);

        itemCommandService = new ItemCommandServiceImpl(
                itemRepository,
                logEntryRepository,
                userProvider,
                userRepository
        );
        logEntryQueryService = new LogEntryQueryServiceImpl(logEntryRepository, userProvider);
    }

    private Item buildItem(Long id, int stockQuantity) {
        var item = new Item(new CreateItemCommand("Guantes Nitrilo", 25.0, stockQuantity, "CONSUMABLES"));
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void createsInitialLogEntryWhenItemIsCreated() {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(itemRepository.existsByNameAndUser_Id("Guantes Nitrilo", USER_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        Long itemId = itemCommandService.handle(new CreateItemCommand("Guantes Nitrilo", 25.0, 20, "CONSUMABLES"));

        assertEquals(10L, itemId);
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryRepository).save(captor.capture());

        LogEntry log = captor.getValue();
        assertEquals(0, log.getPreviousStock());
        assertEquals(20, log.getNewStock());
        assertEquals(0, log.getConsumedQuantity());
        assertEquals("Stock inicial", log.getReason());
    }

    @Test
    void createsLogEntryWhenStockIncreases() {
        Item existing = buildItem(10L, 12);
        when(itemRepository.findByIdAndUser_Id(10L, USER_ID)).thenReturn(Optional.of(existing));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemCommandService.handle(new UpdateItemCommand(10L, "Guantes Nitrilo", 25.0, 30, true, "CONSUMABLES"));

        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryRepository).save(captor.capture());

        LogEntry log = captor.getValue();
        assertEquals(12, log.getPreviousStock());
        assertEquals(30, log.getNewStock());
        assertEquals(0, log.getConsumedQuantity());
        assertEquals("Reposición de stock", log.getReason());
    }

    @Test
    void createsLogEntryWhenStockDecreasesWithCustomReason() {
        Item existing = buildItem(10L, 15);
        when(itemRepository.findByIdAndUser_Id(10L, USER_ID)).thenReturn(Optional.of(existing));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemCommandService.handle(new UpdateItemCommand(
                10L, "Guantes Nitrilo", 25.0, 10, true, "CONSUMABLES", "Uso en cirugía"
        ));

        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryRepository).save(captor.capture());

        LogEntry log = captor.getValue();
        assertEquals(15, log.getPreviousStock());
        assertEquals(10, log.getNewStock());
        assertEquals(5, log.getConsumedQuantity());
        assertEquals("Uso en cirugía", log.getReason());
    }

    @Test
    void doesNotCreateLogEntryWhenStockIsUnchanged() {
        Item existing = buildItem(10L, 15);
        when(itemRepository.findByIdAndUser_Id(10L, USER_ID)).thenReturn(Optional.of(existing));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        itemCommandService.handle(new UpdateItemCommand(10L, "Guantes Nitrilo L", 28.0, 15, true, "CONSUMABLES"));

        verify(logEntryRepository, never()).save(any());
    }

    @Test
    void returnsUserLogEntriesOrderedAndMapsNullableInvoice() {
        Item item = buildItem(10L, 20);
        LogEntry entry = new LogEntry(item, 0, 20, "Stock inicial");
        ReflectionTestUtils.setField(entry, "id", 99L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 10, 30);
        ReflectionTestUtils.setField(entry, "createdAt", now);

        when(logEntryRepository.findAllByItem_User_IdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(entry));

        List<LogEntry> entries = logEntryQueryService.handle(new GetAllLogEntriesByUserIdQuery());
        assertEquals(1, entries.size());

        var resource = LogEntryResourceFromEntityAssembler.toResourceFromEntity(entries.get(0));
        assertEquals(99L, resource.id());
        assertEquals(10L, resource.itemId());
        assertEquals("Guantes Nitrilo", resource.itemName());
        assertEquals(0, resource.previousStock());
        assertEquals(20, resource.newStock());
        assertEquals("Stock inicial", resource.reason());
        assertNull(resource.invoiceId());
        assertEquals(now, resource.createdAt());
    }
}
