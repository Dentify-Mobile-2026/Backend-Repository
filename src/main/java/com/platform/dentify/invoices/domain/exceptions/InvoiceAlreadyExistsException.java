package com.platform.dentify.invoices.domain.exceptions;

public class InvoiceAlreadyExistsException extends RuntimeException {
    public InvoiceAlreadyExistsException(Long appointmentId) {
        super("La cita " + appointmentId + " ya tiene un comprobante registrado");
    }
}
