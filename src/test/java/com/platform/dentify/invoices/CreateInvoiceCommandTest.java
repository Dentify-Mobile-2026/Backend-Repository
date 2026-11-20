package com.platform.dentify.invoices;

import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreateInvoiceCommandTest {

    @Test
    void normalizesValidAmountsToTwoDecimalsWithoutRounding() {
        assertEquals(new BigDecimal("120.50"), new CreateInvoiceCommand(new BigDecimal("120.5"), 1L, 1L).amount());
        assertEquals(new BigDecimal("120.50"), new CreateInvoiceCommand(new BigDecimal("120.50"), 1L, 1L).amount());
        assertEquals(new BigDecimal("0.01"), new CreateInvoiceCommand(new BigDecimal("0.01"), 1L, 1L).amount());
        assertEquals(new BigDecimal("99999999.99"),
                new CreateInvoiceCommand(new BigDecimal("99999999.99"), 1L, 1L).amount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "120.555", "0.001", "100000000.00"})
    void rejectsInvalidAmounts(String amount) {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new CreateInvoiceCommand(new BigDecimal(amount), 1L, 1L));
        assertFalse(ex.getMessage().isBlank());
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand((BigDecimal) null, 1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand(new BigDecimal("10"), null, 1L));
        assertThrows(IllegalArgumentException.class, () -> new CreateInvoiceCommand(new BigDecimal("10"), 1L, null));
    }

    @Test
    void doubleConstructorKeepsExistingCallersWorking() {
        assertEquals(new BigDecimal("350.00"), new CreateInvoiceCommand(350.0, 1L, 1L).amount());
        assertEquals(new BigDecimal("120.50"), new CreateInvoiceCommand(120.5, 1L, 1L).amount());
    }
}
