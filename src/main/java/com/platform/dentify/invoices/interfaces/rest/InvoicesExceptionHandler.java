package com.platform.dentify.invoices.interfaces.rest;

import com.platform.dentify.invoices.domain.exceptions.AppointmentNotFoundException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Traduce los errores del módulo de facturación a códigos HTTP explícitos con cuerpo
 * {@code {"error": "..."}}. Solo aplica a los controladores de este módulo, de modo que no
 * altera el comportamiento de los demás. Las excepciones no previstas no se capturan aquí:
 * siguen el manejo estándar (500) sin exponer detalles internos.
 */
@RestControllerAdvice(assignableTypes = {InvoicesController.class, PaymentMethodsController.class})
public class InvoicesExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "El parámetro '" + ex.getName() + "' no es válido");
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAppointmentNotFound(AppointmentNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvoiceAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleInvoiceAlreadyExists(InvoiceAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
