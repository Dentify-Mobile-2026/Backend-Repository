package com.platform.dentify.invoices.application.internal.queryservices;

import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.queries.GetAllPaymentMethodsQuery;
import com.platform.dentify.invoices.domain.services.PaymentMethodQueryService;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentMethodQueryServiceImpl implements PaymentMethodQueryService {

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodQueryServiceImpl(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Override
    public List<PaymentMethod> handle(GetAllPaymentMethodsQuery query) {
        return paymentMethodRepository.findAllByOrderByIdAsc();
    }
}
