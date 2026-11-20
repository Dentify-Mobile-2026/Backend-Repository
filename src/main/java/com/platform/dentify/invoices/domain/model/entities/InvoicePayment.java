package com.platform.dentify.invoices.domain.model.entities;

import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Abono registrado sobre un comprobante. Es inmutable: no hay operaciones de edición ni de
 * borrado, y la FK hacia {@code invoice} no tiene cascada, de modo que un comprobante con
 * abonos no puede eliminarse por SQL.
 * <p>
 * {@code paidAt} es el instante real del cobro (UTC). {@code requestId} identifica la
 * operación del cliente para que un reintento no duplique el abono.
 */
@Entity
@Getter
@Table(name = "invoice_payment",
        uniqueConstraints = @UniqueConstraint(name = InvoicePayment.REQUEST_ID_CONSTRAINT, columnNames = "request_id"))
public class InvoicePayment extends AuditableModel {

    public static final String REQUEST_ID_CONSTRAINT = "uk_invoice_payment_request_id";

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_invoice_payment_invoice"))
    private Invoice invoice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_invoice_payment_method"))
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(length = 255)
    private String notes;

    @Column(name = "request_id", length = 64)
    private String requestId;

    protected InvoicePayment() {
    }

    /** Solo el agregado {@link Invoice} crea abonos, tras validar los invariantes. */
    public InvoicePayment(Invoice invoice, BigDecimal amount, PaymentMethod paymentMethod,
                          Instant paidAt, String notes, String requestId) {
        this.invoice = invoice;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
        this.notes = notes;
        this.requestId = requestId;
    }
}
