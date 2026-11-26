package com.platform.dentify.patientattention.domain.model.commands;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Reemplaza el horario de atención del consultorio (US09). Se envían de 1 a 7 días, sin repetir.
 */
public record UpdateBusinessHoursCommand(List<Day> days) {

    public record Day(DayOfWeek dayOfWeek, boolean open, LocalTime opensAt, LocalTime closesAt) {
        public Day {
            if (dayOfWeek == null) {
                throw new IllegalArgumentException("El día de la semana es obligatorio");
            }
        }
    }

    public UpdateBusinessHoursCommand {
        if (days == null || days.isEmpty() || days.size() > 7) {
            throw new IllegalArgumentException("El horario debe tener entre 1 y 7 días");
        }
        var seen = EnumSet.noneOf(DayOfWeek.class);
        for (Day day : days) {
            if (!seen.add(day.dayOfWeek())) {
                throw new IllegalArgumentException("El día " + day.dayOfWeek() + " está repetido");
            }
        }
        days = List.copyOf(days);
    }
}
