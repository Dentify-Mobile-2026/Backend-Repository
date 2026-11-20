package com.platform.dentify.invoices;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.invoices.application.internal.commandservices.InvoicePaymentCommandServiceImpl;
import com.platform.dentify.invoices.application.internal.commandservices.InvoicePaymentTransactionalHandler;
import com.platform.dentify.invoices.domain.exceptions.IdempotencyKeyConflictException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceNotFoundException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceOverpaymentException;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.commands.RegisterInvoicePaymentCommand;
import com.platform.dentify.invoices.domain.model.entities.InvoicePayment;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceStatus;
import com.platform.dentify.invoices.domain.model.valueobjects.PaymentRegistrationResult;
import com.platform.dentify.invoices.infrastructure.repositories.InvoicePaymentRepository;
import com.platform.dentify.invoices.infrastructure.repositories.InvoiceRepository;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Registro de abonos: reglas, idempotencia y recuperación tras rollback (Sprint 2). */
class InvoicePaymentRegistrationTest {

    private static final Long USER_ID = 7L;
    private static final Long INVOICE_ID = 30L;
    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private static PaymentMethod method(long id, String name) {
        var method = new PaymentMethod(new CreatePaymentMethodCommand(name, name));
        ReflectionTestUtils.setField(method, "id", id);
        return method;
    }

    private static Invoice openInvoice(String total) {
        var invoice = new Invoice(new CreateInvoiceCommand(new BigDecimal(total), 1L, null, BigDecimal.ZERO));
        ReflectionTestUtils.setField(invoice, "id", INVOICE_ID);
        invoice.openBilling(BigDecimal.ZERO, null, NOW);
        return invoice;
    }

    private static RegisterInvoicePaymentCommand command(String amount, long methodId, String notes, String requestId) {
        return new RegisterInvoicePaymentCommand(INVOICE_ID, new BigDecimal(amount), methodId, notes, requestId);
    }

    @Nested
    class TransactionalHandler {

        private InvoiceRepository invoiceRepository;
        private InvoicePaymentRepository paymentRepository;
        private InvoicePaymentTransactionalHandler handler;
        private final PaymentMethod cash = method(1, "Efectivo");

        @BeforeEach
        void setUp() {
            invoiceRepository = mock(InvoiceRepository.class);
            paymentRepository = mock(InvoicePaymentRepository.class);
            PaymentMethodRepository methodRepository = mock(PaymentMethodRepository.class);
            when(methodRepository.findPaymentMethodById(1L)).thenReturn(Optional.of(cash));
            when(paymentRepository.findByRequestId(any())).thenReturn(Optional.empty());
            handler = new InvoicePaymentTransactionalHandler(invoiceRepository, paymentRepository, methodRepository,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Test
        void registersThePaymentUnderLockWithTheServerInstant() {
            var invoice = openInvoice("300.00");
            when(invoiceRepository.findByIdAndOwnerForUpdate(INVOICE_ID, USER_ID)).thenReturn(Optional.of(invoice));

            PaymentRegistrationResult result = handler.register(command("250", 1, null, "req-1"), USER_ID);

            assertTrue(result.created());
            assertEquals(InvoiceStatus.PARTIAL, result.invoice().getStatus());
            InvoicePayment payment = invoice.getPayments().get(0);
            assertEquals(NOW, payment.getPaidAt());
            assertEquals("req-1", payment.getRequestId());
            verify(invoiceRepository).findByIdAndOwnerForUpdate(INVOICE_ID, USER_ID);
            verify(invoiceRepository).saveAndFlush(invoice);
        }

        @Test
        void invoiceOfAnotherUserIsNotFound() {
            when(invoiceRepository.findByIdAndOwnerForUpdate(INVOICE_ID, USER_ID)).thenReturn(Optional.empty());

            assertThrows(InvoiceNotFoundException.class, () -> handler.register(command("10", 1, null, "r"), USER_ID));
            verify(invoiceRepository, never()).saveAndFlush(any());
        }

        @Test
        void overpaymentIsNeverSaved() {
            var invoice = openInvoice("300.00");
            when(invoiceRepository.findByIdAndOwnerForUpdate(INVOICE_ID, USER_ID)).thenReturn(Optional.of(invoice));

            assertThrows(InvoiceOverpaymentException.class,
                    () -> handler.register(command("300.01", 1, null, "r"), USER_ID));
            verify(invoiceRepository, never()).saveAndFlush(any());
            assertTrue(invoice.getPayments().isEmpty());
        }

        @Test
        void identicalRetryReturnsTheCurrentStateWithoutSaving() {
            var invoice = openInvoice("300.00");
            InvoicePayment previous = invoice.registerPayment(new BigDecimal("250"), cash, NOW, "abono", "req-1");
            when(invoiceRepository.findByIdAndOwnerForUpdate(INVOICE_ID, USER_ID)).thenReturn(Optional.of(invoice));
            when(paymentRepository.findByRequestId("req-1")).thenReturn(Optional.of(previous));

            var result = handler.register(command("250.00", 1, " abono ", "req-1"), USER_ID);

            assertFalse(result.created());
            assertEquals(1, invoice.getPayments().size());
            verify(invoiceRepository, never()).saveAndFlush(any());
        }

        @Test
        void reusedRequestIdWithDifferentDataIsAConflict() {
            var invoice = openInvoice("300.00");
            InvoicePayment previous = invoice.registerPayment(new BigDecimal("250"), cash, NOW, null, "req-1");
            when(invoiceRepository.findByIdAndOwnerForUpdate(INVOICE_ID, USER_ID)).thenReturn(Optional.of(invoice));
            when(paymentRepository.findByRequestId("req-1")).thenReturn(Optional.of(previous));

            assertThrows(IdempotencyKeyConflictException.class,
                    () -> handler.register(command("40", 1, null, "req-1"), USER_ID));
            verify(invoiceRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class NonTransactionalRecovery {

        private InvoicePaymentTransactionalHandler handler;
        private InvoicePaymentCommandServiceImpl service;

        @BeforeEach
        void setUp() {
            handler = mock(InvoicePaymentTransactionalHandler.class);
            AuthenticatedUserProvider userProvider = mock(AuthenticatedUserProvider.class);
            when(userProvider.getCurrentUserId()).thenReturn(USER_ID);
            service = new InvoicePaymentCommandServiceImpl(userProvider, handler);
        }

        private static DataIntegrityViolationException violation(String constraintName) {
            var hibernate = new ConstraintViolationException("could not execute statement",
                    new SQLException("Duplicate entry"), constraintName);
            return new DataIntegrityViolationException("could not execute statement", hibernate);
        }

        @Test
        void duplicateRequestIdIsRecoveredInANewTransactionAfterTheRollback() {
            var cmd = command("250", 1, null, "req-1");
            var replay = new PaymentRegistrationResult(openInvoice("300.00"), false);
            when(handler.register(cmd, USER_ID)).thenThrow(violation("invoice_payment.uk_invoice_payment_request_id"));
            when(handler.replayAfterDuplicateRequest(cmd, USER_ID)).thenReturn(replay);

            assertSame(replay, service.handle(cmd));

            InOrder order = inOrder(handler);
            order.verify(handler).register(cmd, USER_ID);
            order.verify(handler).replayAfterDuplicateRequest(cmd, USER_ID);
        }

        @Test
        void otherIntegrityViolationsAreNotTreatedAsReplays() {
            var cmd = command("250", 1, null, "req-1");
            var foreignKeyViolation = violation("fk_invoice_payment_method");
            when(handler.register(cmd, USER_ID)).thenThrow(foreignKeyViolation);

            var thrown = assertThrows(DataIntegrityViolationException.class, () -> service.handle(cmd));

            assertSame(foreignKeyViolation, thrown);
            verify(handler, never()).replayAfterDuplicateRequest(any(), any());
        }

        @Test
        void violationWithoutConstraintNameIsNotTreatedAsReplay() {
            var cmd = command("250", 1, null, "req-1");
            when(handler.register(cmd, USER_ID)).thenThrow(new DataIntegrityViolationException("unknown"));

            assertThrows(DataIntegrityViolationException.class, () -> service.handle(cmd));
            verify(handler, never()).replayAfterDuplicateRequest(any(), any());
        }
    }

    @Nested
    class CommandValidation {

        @Test
        void commandValidatesRequiredFields() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RegisterInvoicePaymentCommand(INVOICE_ID, new BigDecimal("1"), 1L, null, " "));
            assertThrows(IllegalArgumentException.class,
                    () -> new RegisterInvoicePaymentCommand(INVOICE_ID, new BigDecimal("1"), 1L, null, "x".repeat(65)));
            assertThrows(IllegalArgumentException.class,
                    () -> new RegisterInvoicePaymentCommand(INVOICE_ID, new BigDecimal("1"), null, null, "r"));
            assertThrows(IllegalArgumentException.class,
                    () -> new RegisterInvoicePaymentCommand(INVOICE_ID, new BigDecimal("1"), 1L, "n".repeat(256), "r"));
            assertNull(new RegisterInvoicePaymentCommand(INVOICE_ID, new BigDecimal("1"), 1L, "   ", "r").notes());
        }
    }
}
