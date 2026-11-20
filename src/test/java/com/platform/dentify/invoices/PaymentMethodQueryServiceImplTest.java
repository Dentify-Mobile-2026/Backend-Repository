package com.platform.dentify.invoices;

import com.platform.dentify.invoices.application.internal.queryservices.PaymentMethodQueryServiceImpl;
import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.queries.GetAllPaymentMethodsQuery;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMethodQueryServiceImplTest {

    @Test
    void returnsPaymentMethodsOrderedById() {
        PaymentMethodRepository repository = mock(PaymentMethodRepository.class);
        var cash = new PaymentMethod(new CreatePaymentMethodCommand("Efectivo", "Pago en efectivo"));
        var transfer = new PaymentMethod(new CreatePaymentMethodCommand("Transferencia", "Transferencia bancaria / Yape / Plin"));
        when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(cash, transfer));

        var result = new PaymentMethodQueryServiceImpl(repository).handle(new GetAllPaymentMethodsQuery());

        assertEquals(List.of(cash, transfer), result);
    }

    @Test
    void paymentMethodCommandRejectsMissingFieldsWithoutNullPointer() {
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentMethodCommand(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentMethodCommand("x", null));
        assertThrows(IllegalArgumentException.class, () -> new CreatePaymentMethodCommand(" ", "x"));
    }
}
