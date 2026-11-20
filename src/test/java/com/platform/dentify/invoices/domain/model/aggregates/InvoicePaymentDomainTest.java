package com.platform.dentify.invoices.domain.model.aggregates;

import com.platform.dentify.invoices.domain.exceptions.InvoiceOverpaymentException;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Invariantes financieras del agregado Invoice (Sprint 2). */
class InvoicePaymentDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private static PaymentMethod method(long id, String name) {
        var method = new PaymentMethod(new CreatePaymentMethodCommand(name, name));
        ReflectionTestUtils.setField(method, "id", id);
        return method;
    }

    private static final PaymentMethod CASH = method(1, "Efectivo");
    private static final PaymentMethod TRANSFER = method(4, "Transferencia");

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /** Comprobante abierto sin abono inicial. */
    private static Invoice openInvoice(String total) {
        var invoice = new Invoice(new CreateInvoiceCommand(money(total), 1L, null, BigDecimal.ZERO));
        invoice.openBilling(BigDecimal.ZERO, null, NOW);
        return invoice;
    }

    @Test
    void statusFollowsTheAmountsPendingPartialPaid() {
        var invoice = openInvoice("950.00");
        assertEquals(InvoiceStatus.PENDING, invoice.getStatus());
        assertEquals(money("0.00"), invoice.getPaidAmount());
        assertEquals(money("950.00"), invoice.getPendingBalance());

        invoice.registerPayment(money("350"), CASH, NOW, null, "r1");
        assertEquals(InvoiceStatus.PARTIAL, invoice.getStatus());
        assertEquals(money("350.00"), invoice.getPaidAmount());
        assertEquals(money("600.00"), invoice.getPendingBalance());

        invoice.registerPayment(money("600.00"), TRANSFER, NOW.plusSeconds(60), "saldo", "r2");
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(money("950.00"), invoice.getPaidAmount());
        assertEquals(0, invoice.getPendingBalance().signum());
        assertEquals(List.of(CASH, TRANSFER), invoice.getPaymentMethodsUsed());
    }

    @Test
    void decimalSumsAreExact() {
        var invoice = openInvoice("0.30");
        invoice.registerPayment(money("0.10"), CASH, NOW, null, null);
        invoice.registerPayment(money("0.20"), CASH, NOW, null, null);

        assertEquals(money("0.30"), invoice.getPaidAmount());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void overpaymentIsRejectedAndChangesNothing() {
        var invoice = openInvoice("300.00");
        invoice.registerPayment(money("250.00"), CASH, NOW, null, "r1");

        var ex = assertThrows(InvoiceOverpaymentException.class,
                () -> invoice.registerPayment(money("50.01"), CASH, NOW, null, "r2"));

        assertTrue(ex.getMessage().contains("S/. 50.01") && ex.getMessage().contains("S/. 50.00"), ex.getMessage());
        assertEquals(1, invoice.getPayments().size());
        assertEquals(InvoiceStatus.PARTIAL, invoice.getStatus());
        assertEquals(money("50.00"), invoice.getPendingBalance());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "10.001"})
    void invalidPaymentAmountsAreRejectedAndChangeNothing(String amount) {
        var invoice = openInvoice("100.00");

        assertThrows(IllegalArgumentException.class,
                () -> invoice.registerPayment(money(amount), CASH, NOW, null, null));
        assertTrue(invoice.getPayments().isEmpty());
        assertEquals(InvoiceStatus.PENDING, invoice.getStatus());
    }

    @Test
    void paymentRequiresAmountMethodAndDate() {
        var invoice = openInvoice("100.00");
        assertThrows(IllegalArgumentException.class, () -> invoice.registerPayment(null, CASH, NOW, null, null));
        assertThrows(IllegalArgumentException.class, () -> invoice.registerPayment(money("10"), null, NOW, null, null));
        assertThrows(IllegalArgumentException.class, () -> invoice.registerPayment(money("10"), CASH, null, null, null));
        assertTrue(invoice.getPayments().isEmpty());
    }

    @Test
    void initialPaymentOpensBillingWithItsMethod() {
        var partial = new Invoice(new CreateInvoiceCommand(money("950"), 1L, 1L, money("350")));
        partial.openBilling(money("350.00"), CASH, NOW);
        assertEquals(InvoiceStatus.PARTIAL, partial.getStatus());
        assertEquals(1, partial.getPayments().size());
        assertSame(CASH, partial.getPaymentMethod());

        var unpaid = new Invoice(new CreateInvoiceCommand(money("200"), 1L, null, BigDecimal.ZERO));
        unpaid.openBilling(BigDecimal.ZERO, null, NOW);
        assertEquals(InvoiceStatus.PENDING, unpaid.getStatus());
        assertTrue(unpaid.getPayments().isEmpty());
        assertNull(unpaid.getPaymentMethod());

        assertThrows(IllegalStateException.class, () -> unpaid.openBilling(BigDecimal.ZERO, null, NOW));
    }

    @Test
    void newInvoiceCannotBePersistedWithoutBillingState() {
        var invoice = new Invoice(new CreateInvoiceCommand(money("80"), 1L, 1L));
        assertThrows(IllegalStateException.class, invoice::ensureBillingState);

        invoice.openBilling(money("80.00"), CASH, NOW);
        assertDoesNotThrow(invoice::ensureBillingState);
    }

    @Test
    void legacyInvoiceCountsAsFullyCollectedOnItsIssueDate() {
        var legacy = new Invoice();
        ReflectionTestUtils.setField(legacy, "amount", 120.5);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 9, 16, 0);
        ReflectionTestUtils.setField(legacy, "createdAt", createdAt);

        assertTrue(legacy.isLegacySettled());
        assertEquals(InvoiceStatus.PAID, legacy.getStatus());
        assertEquals(money("120.50"), legacy.getPaidAmount());
        assertEquals(0, legacy.getPendingBalance().signum());
        var collections = legacy.getCollections();
        assertEquals(1, collections.size());
        assertEquals(money("120.50"), collections.get(0).amount());
        assertEquals(createdAt.atZone(ZoneId.systemDefault()).toInstant(), collections.get(0).collectedAt());

        assertThrows(InvoiceOverpaymentException.class,
                () -> legacy.registerPayment(money("0.01"), CASH, NOW, null, null));
        assertTrue(legacy.getPayments().isEmpty());
    }

    @Test
    void balancesCanBeComputedAtACutoff() {
        var invoice = openInvoice("1000.00");
        Instant november = Instant.parse("2026-11-15T15:00:00Z");
        Instant december = Instant.parse("2026-12-05T15:00:00Z");
        invoice.registerPayment(money("200"), CASH, november, null, null);
        invoice.registerPayment(money("800"), CASH, december, null, null);

        Instant endOfNovember = Instant.parse("2026-12-01T04:59:59Z");
        assertEquals(money("200.00"), invoice.getPaidAmountAsOf(endOfNovember));
        assertEquals(money("800.00"), invoice.getPendingBalanceAsOf(endOfNovember));
        assertEquals(money("1000.00"), invoice.getPaidAmountAsOf(december));
    }

    @Test
    void initialPaymentRulesOfTheCommand() {
        assertEquals(money("950.00"), new CreateInvoiceCommand(money("950"), 1L, 1L).initialPayment());
        assertEquals(money("0.00"), new CreateInvoiceCommand(money("950"), 1L, null, money("0")).initialPayment());
        assertEquals(money("350.50"), new CreateInvoiceCommand(money("950"), 1L, 1L, money("350.5")).initialPayment());

        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand(money("950"), 1L, 1L, money("950.01")));
        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand(money("950"), 1L, 1L, money("-1")));
        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand(money("950"), 1L, 1L, money("1.001")));
        // Un abono mayor que cero exige método de pago; con 0 no.
        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand(money("950"), 1L, null, money("1")));
        assertDoesNotThrow(() -> new CreateInvoiceCommand(money("950"), 1L, null, money("0.00")));
    }
}
