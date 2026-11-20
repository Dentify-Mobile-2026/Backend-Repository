package com.platform.dentify.invoices.domain.exceptions;

/**
 * El comprobante no existe o no pertenece al usuario autenticado. Ambos casos se responden
 * igual (404) para no revelar comprobantes de otros usuarios.
 */
public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(Long invoiceId) {
        super("El comprobante " + invoiceId + " no existe o no pertenece al usuario");
    }
}
