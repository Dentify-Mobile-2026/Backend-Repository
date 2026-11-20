package com.platform.dentify.invoices.domain.services;

import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.queries.GetAllPaymentMethodsQuery;

import java.util.List;

public interface PaymentMethodQueryService {
    List<PaymentMethod> handle(GetAllPaymentMethodsQuery query);
}
