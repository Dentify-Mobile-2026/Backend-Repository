package com.platform.dentify.patientattention.domain.model.aggregates;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@Setter
public class Appointment extends AuditableAbstractAggregateRoot<Appointment> {

    @Column(nullable = false)
    private LocalDateTime appointmentDate;

    @Column(nullable = false)
    @Size(min = 1, max = 200)
    private String reason;

    @Column(nullable = false)
    private Boolean completed;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AppointmentStatus status;

    @Size(max = 500)
    @Column(length = 500)
    private String clinicalNotes;

    @Column(nullable = false)
    @JsonFormat(pattern = "HH:mm")
    private LocalTime duration;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointment_patient"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "treatment_id", foreignKey = @ForeignKey(name = "fk_appointment_treatment"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Treatment treatment;

    @OneToOne(mappedBy = "appointment", cascade = CascadeType.ALL)
    private Invoice invoice;

    public Appointment(CreateAppointmentCommand command) {
        this.appointmentDate = command._appointmentDate();
        this.reason = command._reason();
        this.completed = false;
        this.status = AppointmentStatus.SCHEDULED;
        this.clinicalNotes = "";
        this.duration = command._duration();
    }

    public Appointment() {}

    public void updateStatus(AppointmentStatus newStatus, String notes) {
        this.status = newStatus;
        this.completed = (newStatus == AppointmentStatus.COMPLETED);
        if (notes != null) {
            this.clinicalNotes = notes.trim();
        }
    }

    public AppointmentStatus resolveStatus() {
        if (this.status != null) {
            return this.status;
        }
        return Boolean.TRUE.equals(this.completed)
                ? AppointmentStatus.COMPLETED
                : AppointmentStatus.SCHEDULED;
    }

    /** Convierte la duración almacenada como hora (HH:mm) en una Duration. */
    public static Duration toDuration(LocalTime duration) {
        return Duration.between(LocalTime.MIDNIGHT, duration);
    }

    /**
     * Indica si el intervalo [start, start + duration) se solapa con esta cita.
     * Dos citas con la misma hora de inicio siempre se consideran en conflicto.
     */
    public boolean overlapsWith(LocalDateTime start, LocalTime duration) {
        LocalDateTime thisEnd = appointmentDate.plus(toDuration(this.duration));
        LocalDateTime otherEnd = start.plus(toDuration(duration));
        return appointmentDate.equals(start)
                || (start.isBefore(thisEnd) && appointmentDate.isBefore(otherEnd));
    }
}
