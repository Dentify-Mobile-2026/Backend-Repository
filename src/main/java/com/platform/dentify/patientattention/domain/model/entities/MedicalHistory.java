package com.platform.dentify.patientattention.domain.model.entities;


import com.platform.dentify.patientattention.domain.exceptions.MedicalHistoryAlreadyAnnulledException;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.domain.model.commands.CreateMedicalHistoryCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.Diagnosis;
import com.platform.dentify.patientattention.domain.model.valueobjects.Medication;
import com.platform.dentify.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Setter
public class MedicalHistory extends AuditableModel {
    @Size(max = 250)
    private String description;

    @Size(max = 500)
    @Column(length = 500)
    private String record;

    // Kept as a scalar so deleting an appointment does not erase its clinical record.
    @Column(unique = true)
    private Long sourceAppointmentId;

    private java.time.LocalDateTime appointmentDate;

    @Embedded
    private Diagnosis diagnosis;

    @Embedded
    private Medication medication;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_medicalhistory_patient"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Patient patient;

    /** Anulación con motivo (US18): la entrada no se borra, queda marcada y visible. */
    @Column(name = "annulled")
    private Boolean annulled;

    @Size(max = 250)
    @Column(name = "annulment_reason", length = 250)
    private String annulmentReason;

    @Column(name = "annulled_at")
    private java.time.LocalDateTime annulledAt;

    public MedicalHistory() {
    }

    /** Las filas creadas antes de US18 tienen la columna en NULL y cuentan como vigentes. */
    public boolean isAnnulled() {
        return Boolean.TRUE.equals(annulled);
    }

    /**
     * Anula la entrada con un motivo obligatorio. No se puede anular dos veces.
     *
     * @throws IllegalArgumentException si el motivo está vacío o supera 250 caracteres
     * @throws MedicalHistoryAlreadyAnnulledException si la entrada ya estaba anulada
     */
    public void annul(String reason, java.time.LocalDateTime when) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo de la anulación es obligatorio");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 250) {
            throw new IllegalArgumentException("El motivo de la anulación admite hasta 250 caracteres");
        }
        if (isAnnulled()) {
            throw new MedicalHistoryAlreadyAnnulledException("La entrada de la historia clínica ya está anulada");
        }
        this.annulled = true;
        this.annulmentReason = trimmed;
        this.annulledAt = when;
    }

    public MedicalHistory(CreateMedicalHistoryCommand command) {
        description = command.description();
        record = command.record();
        diagnosis = new Diagnosis(command.diagnosis());
        medication = new Medication(command.medication());

    }
}
