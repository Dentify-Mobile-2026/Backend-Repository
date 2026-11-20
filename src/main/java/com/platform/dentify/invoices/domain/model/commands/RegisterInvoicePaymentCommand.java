package com.platform.dentify.invoices.domain.model.commands;

import java.math.BigDecimal;

/**
 * Registro de un abono. {@code requestId} identifica la operación del cliente: un reintento
 * con el mismo {@code requestId} y los mismos datos no crea un segundo abono.
 * Las reglas de monto (mayor que cero, 2 decimales, no superar el saldo) las aplica el
 * agregado {@code Invoice}.
 */
public record RegisterInvoicePaymentCommand(Long invoiceId, BigDecimal amount, Long paymentMethodId,
                                            String notes, String requestId) {

    public static final int MAX_REQUEST_ID_LENGTH = 64;
    public static final int MAX_NOTES_LENGTH = 255;

    public RegisterInvoicePaymentCommand {
        if (invoiceId == null) {
            throw new IllegalArgumentException("El comprobante es obligatorio");
        }
        if (amount == null) {
            throw new IllegalArgumentException("El monto del abono es obligatorio");
        }
        if (paymentMethodId == null) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la operación (requestId) es obligatorio");
        }
        requestId = requestId.trim();
        if (requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw new IllegalArgumentException("El requestId admite como máximo " + MAX_REQUEST_ID_LENGTH + " caracteres");
        }
        notes = notes == null || notes.isBlank() ? null : notes.trim();
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Las notas admiten como máximo " + MAX_NOTES_LENGTH + " caracteres");
        }
    }
}
