package com.platform.dentify.invoices.domain.model.aggregates;

import com.platform.dentify.invoices.domain.exceptions.InvoiceDeletionNotAllowedException;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceDomainTest {

    @Test
    void newInvoiceKeepsExactTotalAndDoubleMirror() {
        var invoice = new Invoice(new CreateInvoiceCommand(new BigDecimal("120.55"), 1L, 1L));

        assertEquals(new BigDecimal("120.55"), invoice.getTotalAmount());
        assertEquals(120.55, invoice.getAmount());
    }

    @Test
    void legacyInvoiceWithoutTotalDerivesItFromTheDoubleRoundedToCents() {
        var legacy = new Invoice();
        ReflectionTestUtils.setField(legacy, "amount", 0.1 + 0.2); // 0.30000000000000004

        assertEquals(new BigDecimal("0.30"), legacy.getTotalAmount());
    }

    @Test
    void jpaRemovalIsAlwaysRejected() {
        var invoice = new Invoice(new CreateInvoiceCommand(new BigDecimal("50"), 1L, 1L));
        ReflectionTestUtils.setField(invoice, "id", 15L);

        var ex = assertThrows(InvoiceDeletionNotAllowedException.class, invoice::preventRemoval);
        assertEquals(true, ex.getMessage().contains("15"));
    }
}
