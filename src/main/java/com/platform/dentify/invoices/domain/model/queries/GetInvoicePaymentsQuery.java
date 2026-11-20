package com.platform.dentify.invoices.domain.model.queries;

public record GetInvoicePaymentsQuery(Long invoiceId) {
    public GetInvoicePaymentsQuery {
        if (invoiceId == null) throw new IllegalArgumentException("invoiceId cannot be null");
    }
}
