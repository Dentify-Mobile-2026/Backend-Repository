package com.platform.dentify.invoices.domain.model.commands;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Comando de creación de un comprobante.
 * <p>
 * El monto se valida como decimal exacto: mayor que cero, con 2 decimales como máximo y
 * dentro de DECIMAL(10,2). Se normaliza a escala 2 (120.5 → 120.50) sin redondear.
 * <p>
 * {@code initialPayment} es el abono que se cobra al emitir el comprobante:
 * <ul>
 *   <li>ausente (null): se cobra el total, igual que antes de los abonos;</li>
 *   <li>0: no se registra ningún abono y no se exige método de pago;</li>
 *   <li>entre 0 y el total: abono parcial; mayor que el total: inválido.</li>
 * </ul>
 */
public record CreateInvoiceCommand(BigDecimal amount, Long appointmentId, Long paymentMethodId,
                                   BigDecimal initialPayment) {

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
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);

        if (initialPayment == null) {
            initialPayment = amount;
        } else {
            if (initialPayment.signum() < 0) {
                throw new IllegalArgumentException("El abono inicial no puede ser negativo");
            }
            if (initialPayment.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("El abono inicial admite como máximo 2 decimales");
            }
            if (initialPayment.compareTo(amount) > 0) {
                throw new IllegalArgumentException("El abono inicial no puede superar el total");
            }
            initialPayment = initialPayment.setScale(2, RoundingMode.UNNECESSARY);
        }
        if (initialPayment.signum() > 0 && paymentMethodId == null) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }
    }

    /** Cobro del total al emitir (comportamiento anterior a los abonos). */
    public CreateInvoiceCommand(BigDecimal amount, Long appointmentId, Long paymentMethodId) {
        this(amount, appointmentId, paymentMethodId, null);
    }

    /**
     * Compatibilidad con código que construye el comando a partir de un {@code double}
     * (por ejemplo, las pruebas del dashboard). {@link BigDecimal#valueOf(double)} usa la
     * representación decimal más corta del double, por lo que 350.0 se convierte en 350.00.
     */
    public CreateInvoiceCommand(Double amount, Long appointmentId, Long paymentMethodId) {
        this(amount == null ? null : BigDecimal.valueOf(amount), appointmentId, paymentMethodId, null);
    }
}
