package com.platform.dentify.invoices.domain.model.aggregates;

import com.platform.dentify.invoices.domain.exceptions.InvoiceDeletionNotAllowedException;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    @ManyToOne
    @JoinColumn(name = "paymentmethod_id")
    private PaymentMethod paymentMethod;

    public Invoice() {}

    public Invoice(CreateInvoiceCommand command) {
        this.totalAmount = command.amount();
        this.amount = command.amount().doubleValue();
        //appointment y paymentMethod se asignan en el servicio de aplicación
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
