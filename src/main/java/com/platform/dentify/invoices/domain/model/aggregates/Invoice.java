package com.platform.dentify.invoices.domain.model.aggregates;

import com.platform.dentify.invoices.domain.exceptions.InvoiceDeletionNotAllowedException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceOverpaymentException;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.entities.InvoicePayment;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceCollection;
import com.platform.dentify.invoices.domain.model.valueobjects.InvoiceStatus;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comprobante de una cita y sus abonos.
 * <p>
 * Invariantes (Sprint 2):
 * <ul>
 *   <li>total &gt; 0, con 2 decimales como máximo;</li>
 *   <li>cada abono &gt; 0, con 2 decimales como máximo, y nunca mayor que el saldo pendiente;</li>
 *   <li>pagado = Σ abonos ≤ total, y saldo = total − pagado ≥ 0;</li>
 *   <li>estado coherente: PENDING ⇔ pagado = 0, PARTIAL ⇔ 0 &lt; pagado &lt; total, PAID ⇔ pagado = total.</li>
 * </ul>
 * Los comprobantes anteriores a los abonos ({@code status} NULL en la base) se tratan como
 * cobrados en su totalidad en la fecha de emisión, igual que los contaba el sistema antes;
 * no se les fabrican abonos.
 */
@Entity
@Getter
public class Invoice extends AuditableAbstractAggregateRoot<Invoice> {

    /**
     * Espejo en {@code double} del total, que se conserva por compatibilidad con los lectores
     * existentes (dashboard). La fuente de verdad es {@link #totalAmount}.
     */
    @Column(nullable = false)
    private Double amount;

    /** Total exacto del comprobante. Es NULL solo en filas creadas antes de esta columna. */
    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /**
     * Sin {@code @OnDelete(CASCADE)}: en esquemas nuevos la FK se crea sin cascada (RESTRICT).
     * Las bases existentes se corrigen con la migración manual
     * {@code db/migration-manual/V2026_11_20__invoice_fk_restrict.sql}.
     */
    @OneToOne
    @JoinColumn(name = "appointment_id", foreignKey = @ForeignKey(name = "fk_invoice_appointment"))
    private Appointment appointment;

    /**
     * Método de pago a nivel de comprobante. Desde los abonos, la fuente de verdad es el
     * método de cada {@link InvoicePayment}; aquí solo se conserva el del cobro inicial y el
     * de los comprobantes históricos.
     */
    @ManyToOne
    @JoinColumn(name = "paymentmethod_id")
    private PaymentMethod paymentMethod;

    /** NULL solo en comprobantes anteriores a los abonos; ver {@link #isLegacySettled()}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InvoiceStatus status;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.PERSIST)
    @OrderBy("paidAt ASC, id ASC")
    private List<InvoicePayment> payments = new ArrayList<>();

    public Invoice() {}

    public Invoice(CreateInvoiceCommand command) {
        this.totalAmount = command.amount();
        this.amount = command.amount().doubleValue();
        //appointment y paymentMethod se asignan en el servicio de aplicación
    }

    /**
     * Abre el cobro del comprobante recién creado y registra, si corresponde, el abono inicial.
     * Un abono inicial de 0 deja el comprobante PENDING sin abonos ni método de pago.
     */
    public void openBilling(BigDecimal initialPayment, PaymentMethod method, Instant paidAt) {
        if (status != null) {
            throw new IllegalStateException("El cobro del comprobante ya fue abierto");
        }
        this.status = InvoiceStatus.PENDING;
        if (initialPayment != null && initialPayment.signum() > 0) {
            this.paymentMethod = method;
            registerPayment(initialPayment, method, paidAt, null, null);
        }
    }

    /**
     * Registra un abono validando los invariantes. Si el abono no es válido no se modifica
     * nada: ni la lista de abonos ni el estado.
     */
    public InvoicePayment registerPayment(BigDecimal paymentAmount, PaymentMethod method, Instant paidAt,
                                          String notes, String requestId) {
        if (paymentAmount == null) {
            throw new IllegalArgumentException("El monto del abono es obligatorio");
        }
        if (paymentAmount.signum() <= 0) {
            throw new IllegalArgumentException("El monto del abono debe ser mayor que cero");
        }
        if (paymentAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto del abono admite como máximo 2 decimales");
        }
        if (method == null) {
            throw new IllegalArgumentException("El método de pago es obligatorio");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("La fecha del abono es obligatoria");
        }
        BigDecimal pending = getPendingBalance();
        if (paymentAmount.compareTo(pending) > 0) {
            throw new InvoiceOverpaymentException(paymentAmount.setScale(2, RoundingMode.UNNECESSARY), pending);
        }
        InvoicePayment payment = new InvoicePayment(this, paymentAmount.setScale(2, RoundingMode.UNNECESSARY),
                method, paidAt, notes, requestId);
        payments.add(payment);
        recalculateStatus();
        return payment;
    }

    /** Comprobante emitido antes de los abonos: se considera cobrado en su totalidad. */
    public boolean isLegacySettled() {
        return status == null;
    }

    /**
     * Total exacto. Para filas anteriores a {@code total_amount} se deriva del {@code double}
     * redondeando a céntimos: recupera el valor original cuando se ingresó con hasta 2
     * decimales, pero no corrige imprecisiones ya presentes en el dato histórico.
     */
    public BigDecimal getTotalAmount() {
        if (totalAmount != null) {
            return totalAmount;
        }
        return amount == null ? null : BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public InvoiceStatus getStatus() {
        return status != null ? status : InvoiceStatus.PAID;
    }

    public List<InvoicePayment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    public BigDecimal getPaidAmount() {
        if (isLegacySettled()) {
            return getTotalAmount();
        }
        return payments.stream()
                .map(InvoicePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public BigDecimal getPendingBalance() {
        return getTotalAmount().subtract(getPaidAmount());
    }

    /** Monto cobrado hasta el instante indicado (inclusive). */
    public BigDecimal getPaidAmountAsOf(Instant cutoff) {
        return getCollections().stream()
                .filter(collection -> !collection.collectedAt().isAfter(cutoff))
                .map(InvoiceCollection::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Saldo pendiente en el instante indicado. */
    public BigDecimal getPendingBalanceAsOf(Instant cutoff) {
        return getTotalAmount().subtract(getPaidAmountAsOf(cutoff));
    }

    /**
     * Instante de emisión. {@code createdAt} es una fecha de auditoría sin zona, escrita en la
     * zona horaria de la JVM; se interpreta en esa misma zona tanto en registros nuevos como
     * históricos.
     */
    public Instant getIssuedAt() {
        return getCreatedAt() == null ? null : getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * Dinero efectivamente cobrado: cada abono en su instante real, o el total en la fecha de
     * emisión para comprobantes históricos.
     */
    public List<InvoiceCollection> getCollections() {
        if (isLegacySettled()) {
            Instant issuedAt = getIssuedAt();
            return issuedAt == null ? List.of() : List.of(new InvoiceCollection(getId(), getTotalAmount(), issuedAt));
        }
        return payments.stream()
                .map(payment -> new InvoiceCollection(getId(), payment.getAmount(), payment.getPaidAt()))
                .toList();
    }

    /** Métodos distintos usados en los abonos, en orden de uso. */
    public List<PaymentMethod> getPaymentMethodsUsed() {
        if (payments.isEmpty()) {
            return paymentMethod == null ? List.of() : List.of(paymentMethod);
        }
        List<PaymentMethod> methods = new ArrayList<>();
        for (InvoicePayment payment : payments) {
            PaymentMethod method = payment.getPaymentMethod();
            boolean alreadyListed = methods.stream().anyMatch(existing -> sameMethod(existing, method));
            if (!alreadyListed) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static boolean sameMethod(PaymentMethod a, PaymentMethod b) {
        if (a.getId() != null && b.getId() != null) {
            return a.getId().equals(b.getId());
        }
        return a == b;
    }

    private void recalculateStatus() {
        BigDecimal paid = getPaidAmount();
        if (paid.signum() == 0) {
            status = InvoiceStatus.PENDING;
        } else if (paid.compareTo(getTotalAmount()) < 0) {
            status = InvoiceStatus.PARTIAL;
        } else {
            status = InvoiceStatus.PAID;
        }
    }

    /** Ningún comprobante nuevo se guarda sin total exacto ni estado de cobro. */
    @PrePersist
    void ensureBillingState() {
        if (totalAmount == null || status == null) {
            throw new IllegalStateException("Un comprobante nuevo debe tener total y estado de cobro");
        }
    }

    /**
     * Bloquea cualquier eliminación vía JPA, incluidas las cascadas desde
     * {@code Appointment.invoice} y {@code Patient.appointments} (CascadeType.ALL), antes de
     * que se ejecute SQL alguno. Los borrados directos por SQL los bloquea la FK RESTRICT.
     */
    @PreRemove
    void preventRemoval() {
        throw new InvoiceDeletionNotAllowedException(getId());
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
