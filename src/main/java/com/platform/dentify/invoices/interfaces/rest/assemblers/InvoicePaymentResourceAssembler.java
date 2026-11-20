package com.platform.dentify.invoices.interfaces.rest.assemblers;

import com.platform.dentify.invoices.domain.model.commands.RegisterInvoicePaymentCommand;
import com.platform.dentify.invoices.domain.model.entities.InvoicePayment;
import com.platform.dentify.invoices.interfaces.rest.dtos.InvoicePaymentResource;
import com.platform.dentify.invoices.interfaces.rest.dtos.RegisterInvoicePaymentResource;

public class InvoicePaymentResourceAssembler {

    public static RegisterInvoicePaymentCommand toCommand(Long invoiceId, RegisterInvoicePaymentResource resource) {
        return new RegisterInvoicePaymentCommand(invoiceId, resource.amount(), resource.paymentMethodId(),
                resource.notes(), resource.requestId());
    }

    public static InvoicePaymentResource toResource(InvoicePayment payment) {
        var method = payment.getPaymentMethod();
        return new InvoicePaymentResource(
                payment.getId(),
                payment.getAmount(),
                method != null ? method.getId() : null,
                method != null ? method.getName() : null,
                payment.getNotes(),
                payment.getPaidAt()
        );
    }
}
