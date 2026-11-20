package com.platform.dentify.invoices;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.invoices.application.external.AppointmentACL;
import com.platform.dentify.invoices.application.external.ExternalAppointmentDTO;
import com.platform.dentify.invoices.application.internal.commandservices.InvoiceCommandServiceImpl;
import com.platform.dentify.invoices.domain.exceptions.AppointmentNotFoundException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceAlreadyExistsException;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.infrastructure.repositories.InvoiceRepository;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceCommandServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long APPOINTMENT_ID = 11L;
    private static final Long METHOD_ID = 9L;

    private InvoiceRepository invoiceRepository;
    private PaymentMethodRepository paymentMethodRepository;
    private AppointmentACL appointmentACL;
    private PlatformTransactionManager transactionManager;
    private InvoiceCommandServiceImpl service;
    private PaymentMethod card;

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        paymentMethodRepository = mock(PaymentMethodRepository.class);
        appointmentACL = mock(AppointmentACL.class);
        transactionManager = mock(PlatformTransactionManager.class);
        AuthenticatedUserProvider userProvider = mock(AuthenticatedUserProvider.class);
        when(userProvider.getCurrentUserId()).thenReturn(USER_ID);

        service = new InvoiceCommandServiceImpl(userProvider, invoiceRepository, appointmentACL,
                paymentMethodRepository, transactionManager);
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.getReference(eq(Appointment.class), any())).thenReturn(new Appointment());
        ReflectionTestUtils.setField(service, "entityManager", entityManager);

        card = new PaymentMethod(new CreatePaymentMethodCommand("Tarjeta de Credito", "Pago con tarjeta de credito"));
        ReflectionTestUtils.setField(card, "id", METHOD_ID);
        when(paymentMethodRepository.findPaymentMethodById(METHOD_ID)).thenReturn(Optional.of(card));
        when(appointmentACL.findByIdAndUserId(APPOINTMENT_ID, USER_ID)).thenReturn(Optional.of(
                new ExternalAppointmentDTO(APPOINTMENT_ID, LocalDateTime.now(), "Control", false,
                        LocalTime.of(0, 30), 3L, LocalDateTime.now())));
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateInvoiceCommand command(String amount) {
        return new CreateInvoiceCommand(new BigDecimal(amount), APPOINTMENT_ID, METHOD_ID);
    }

    @Test
    void createsInvoiceWithExactTotalAndChosenMethodWithoutTouchingTheAppointment() {
        when(invoiceRepository.findInvoiceByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());

        Invoice invoice = service.handle(command("120.5")).orElseThrow();

        assertEquals(new BigDecimal("120.50"), invoice.getTotalAmount());
        assertEquals(120.5, invoice.getAmount());
        // Desde los abonos (Sprint 2) el método elegido se guarda en el cobro, no en el comprobante.
        assertEquals(1, invoice.getPayments().size());
        assertSame(card, invoice.getPayments().get(0).getPaymentMethod());
        assertEquals(new BigDecimal("120.50"), invoice.getPayments().get(0).getAmount());
        assertEquals(List.of(card), invoice.getPaymentMethodsUsed());
        assertNull(invoice.getPaymentMethod());
        // Solo se consulta la pertenencia de la cita: facturar no cambia su estado clínico.
        verify(appointmentACL).findByIdAndUserId(APPOINTMENT_ID, USER_ID);
        verifyNoMoreInteractions(appointmentACL);
        verify(transactionManager).commit(any());
    }

    @Test
    void appointmentOfAnotherUserIsReportedAsNotFound() {
        when(appointmentACL.findByIdAndUserId(APPOINTMENT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(AppointmentNotFoundException.class, () -> service.handle(command("50")));
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownPaymentMethodIsAnInvalidRequest() {
        when(paymentMethodRepository.findPaymentMethodById(METHOD_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.handle(command("50")));
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void existingInvoiceIsAConflict() {
        when(invoiceRepository.findInvoiceByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(new Invoice()));

        assertThrows(InvoiceAlreadyExistsException.class, () -> service.handle(command("50")));
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateDetectedAfterRollbackIsAConflict() {
        // Dentro de la transacción aún no existe; al guardar, la otra solicitud ya lo creó.
        when(invoiceRepository.findInvoiceByAppointmentId(APPOINTMENT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new Invoice()));
        when(invoiceRepository.saveAndFlush(any(Invoice.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key appointment_id"));

        assertThrows(InvoiceAlreadyExistsException.class, () -> service.handle(command("50")));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void otherIntegrityViolationsAreNotDisguisedAsDuplicates() {
        when(invoiceRepository.findInvoiceByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());
        var violation = new DataIntegrityViolationException("Cannot add or update a child row");
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenThrow(violation);

        var thrown = assertThrows(DataIntegrityViolationException.class, () -> service.handle(command("50")));
        assertSame(violation, thrown);
    }
}
