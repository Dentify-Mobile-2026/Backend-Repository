package com.platform.dentify.inventory;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.application.internal.commandservices.LogEntryCommandServiceImpl;
import com.platform.dentify.inventory.domain.model.commands.CreateLogEntryCommand;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.ItemRepository;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.LogEntryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LogEntryOwnershipTest {

    @Test
    void cannotConsumeStockFromAnotherUsersItem() {
        LogEntryRepository logEntryRepository = mock(LogEntryRepository.class);
        ItemRepository itemRepository = mock(ItemRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);

        when(auth.getCurrentUserId()).thenReturn(99L);
        when(itemRepository.findByIdAndUser_Id(10L, 99L)).thenReturn(Optional.empty());

        var service = new LogEntryCommandServiceImpl(logEntryRepository, itemRepository, auth);

        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new CreateLogEntryCommand(10L, 2, 1L)));

        verify(itemRepository, never()).findById(any());
        verifyNoInteractions(logEntryRepository);
    }
}
