package com.platform.dentify.invoices.domain.exceptions;

/** El requestId ya se usó para una operación con datos distintos. */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String requestId) {
        super("El identificador de operación " + requestId
                + " ya se usó con datos distintos; genere uno nuevo para registrar otro abono");
    }
}
