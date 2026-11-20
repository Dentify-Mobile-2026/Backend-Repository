package com.platform.dentify.invoices.domain.services;

import com.platform.dentify.invoices.domain.model.commands.RegisterInvoicePaymentCommand;
import com.platform.dentify.invoices.domain.model.valueobjects.PaymentRegistrationResult;

public interface InvoicePaymentCommandService {
    PaymentRegistrationResult handle(RegisterInvoicePaymentCommand command);
}
