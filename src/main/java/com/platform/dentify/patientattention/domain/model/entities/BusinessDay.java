package com.platform.dentify.patientattention.domain.model.entities;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Un día del horario de atención del consultorio (US09).
 * <p>
 * Cada consultorio (usuario) tiene como máximo una fila por día de la semana. Un día cerrado no
 * admite citas; un día abierto admite citas que empiezan y terminan dentro de su franja.
 */
@Entity
@Table(name = "business_day",
        uniqueConstraints = @UniqueConstraint(name = "uk_business_day_user_day", columnNames = {"user_id", "day_of_week"}))
@Getter
public class BusinessDay extends AuditableModel {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_business_day_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    protected BusinessDay() {
    }

    public BusinessDay(User user, DayOfWeek dayOfWeek, boolean open, LocalTime opensAt, LocalTime closesAt) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("El día de la semana es obligatorio");
        }
        this.user = user;
        this.dayOfWeek = dayOfWeek;
        update(open, opensAt, closesAt);
    }

    /** Cambia la franja del día. Un día abierto exige apertura anterior al cierre. */
    public void update(boolean open, LocalTime opensAt, LocalTime closesAt) {
        if (open) {
            if (opensAt == null || closesAt == null) {
                throw new IllegalArgumentException("Un día abierto necesita hora de apertura y de cierre");
            }
            if (!opensAt.isBefore(closesAt)) {
                throw new IllegalArgumentException("La hora de apertura debe ser anterior a la de cierre");
            }
        }
        this.open = open;
        this.opensAt = open ? opensAt : null;
        this.closesAt = open ? closesAt : null;
    }

    /** Indica si una cita que va de {@code start} a {@code end} cabe en este día. */
    public boolean admits(LocalDateTime start, LocalDateTime end) {
        if (!open) {
            return false;
        }
        // Una cita que pasa de medianoche nunca cabe: el cierre siempre es anterior a las 24:00.
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        return !start.toLocalTime().isBefore(opensAt) && !end.toLocalTime().isAfter(closesAt);
    }
}
