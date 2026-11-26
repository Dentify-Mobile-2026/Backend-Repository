package com.platform.dentify.inventory;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.inventory.application.internal.commandservices.LogEntryCommandServiceImpl;
import com.platform.dentify.inventory.domain.model.aggregates.Item;
import com.platform.dentify.inventory.domain.model.aggregates.LogEntry;
import com.platform.dentify.inventory.domain.model.commands.CreateLogEntryCommand;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.ItemRepository;
import com.platform.dentify.inventory.infrastructure.persistence.jpa.repositories.LogEntryRepository;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.infrastructure.repositories.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US27: el consumo de un insumo queda ligado al comprobante de la atención. */
class LogEntryInvoiceLinkTest {

    private static final Long USER_ID = 7L;

    private LogEntryRepository logEntries;
    private ItemRepository items;
    private InvoiceRepository invoices;
    private LogEntryCommandServiceImpl service;
    private Item item;

    @BeforeEach
    void setUp() {
        logEntries = mock(LogEntryRepository.class);
        items = mock(ItemRepository.class);
        invoices = mock(InvoiceRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getCurrentUserId()).thenReturn(USER_ID);
        item = mock(Item.class);
        when(item.getStockQuantity()).thenReturn(10);
        when(items.findByIdAndUser_Id(1L, USER_ID)).thenReturn(Optional.of(item));
        service = new LogEntryCommandServiceImpl(logEntries, items, auth, invoices);
    }

    @Test
    void theConsumptionKeepsTheInvoiceOfTheAttention() {
        var invoice = mock(Invoice.class);
        when(invoices.findByIdAndAppointment_Patient_User_Id(5L, USER_ID)).thenReturn(Optional.of(invoice));

        service.handle(new CreateLogEntryCommand(1L, 3, 5L));

        var saved = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntries).save(saved.capture());
        assertSame(invoice, saved.getValue().getInvoice());
        assertEquals(10, saved.getValue().getPreviousStock());
        assertEquals(7, saved.getValue().getNewStock());
    }

    @Test
    void anInvoiceOfAnotherOfficeIsRejectedWithoutTouchingTheStock() {
        when(invoices.findByIdAndAppointment_Patient_User_Id(5L, USER_ID)).thenReturn(Optional.empty());

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.handle(new CreateLogEntryCommand(1L, 3, 5L)));

        assertEquals("Invalid invoice id", error.getMessage());
        verify(item, never()).updateStockQuantity(anyInt());
        verify(logEntries, never()).save(any());
    }
}
