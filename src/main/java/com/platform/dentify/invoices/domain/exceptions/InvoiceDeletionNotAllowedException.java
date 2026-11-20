package com.platform.dentify.invoices.domain.exceptions;

/**
 * Se lanza cuando JPA intenta eliminar un comprobante, por ejemplo al propagar en cascada
 * el borrado de una cita o de un paciente. Los comprobantes son registros financieros y no
 * deben desaparecer como efecto secundario de otros borrados.
 */
public class InvoiceDeletionNotAllowedException extends IllegalStateException {
    public InvoiceDeletionNotAllowedException(Long invoiceId) {
        super("No se puede eliminar el comprobante " + invoiceId
                + ": los registros financieros se conservan. Elimine primero la referencia desde el módulo de facturación.");
    }
}
