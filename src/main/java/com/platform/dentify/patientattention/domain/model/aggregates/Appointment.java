package com.platform.dentify.patientattention.domain.model.aggregates;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
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
public class Appointment extends AuditableAbstractAggregateRoot<Appointment>  {

    @Column(nullable = false)
    private LocalDateTime appointmentDate;

    @Column(nullable = false)
    @Size(min = 1, max = 200)
    private String reason;

    @Column(nullable = false)
    private Boolean completed;

    @Column(nullable = false)
    @JsonFormat(pattern = "HH:mm")
    private LocalTime duration;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointment_patient"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Patient patient;

    @OneToOne(mappedBy = "appointment", cascade = CascadeType.ALL)
    private Invoice invoice;

    public Appointment(CreateAppointmentCommand command) {
        appointmentDate = command._appointmentDate();
        reason = command._reason();
        completed = false;
        duration = command._duration();


    }

    public Appointment(){}

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
