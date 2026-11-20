package com.platform.dentify.invoices.interfaces.rest.assemblers;

import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.interfaces.rest.dtos.PaymentMethodResource;

public class PaymentMethodResourceFromEntityAssembler {
    public static PaymentMethodResource toResourceFromEntity(PaymentMethod paymentMethod) {
        return new PaymentMethodResource(
                paymentMethod.getId(),
                paymentMethod.getName(),
                paymentMethod.getDescription()
        );
    }
}
