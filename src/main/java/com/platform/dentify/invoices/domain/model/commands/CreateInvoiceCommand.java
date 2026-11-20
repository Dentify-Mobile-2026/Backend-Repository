package com.platform.dentify.invoices.domain.model.commands;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Comando de creación de un comprobante.
 * <p>
 * El monto se valida como decimal exacto: mayor que cero, con 2 decimales como máximo y
 * dentro de DECIMAL(10,2). Se normaliza a escala 2 (120.5 → 120.50) sin redondear.
 */
public record CreateInvoiceCommand(BigDecimal amount, Long appointmentId, Long paymentMethodId) {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    public CreateInvoiceCommand {
        if (amount == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto admite como máximo 2 decimales");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El monto excede el máximo permitido (S/. 99999999.99)");
        }
        if (appointmentId == null) {
            throw new IllegalArgumentException("La cita es obligatoria");
        }
        if (paymentMethodId == null) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Compatibilidad con código que construye el comando a partir de un {@code double}
     * (por ejemplo, las pruebas del dashboard). {@link BigDecimal#valueOf(double)} usa la
     * representación decimal más corta del double, por lo que 350.0 se convierte en 350.00.
     */
    public CreateInvoiceCommand(Double amount, Long appointmentId, Long paymentMethodId) {
        this(amount == null ? null : BigDecimal.valueOf(amount), appointmentId, paymentMethodId);
    }
}
