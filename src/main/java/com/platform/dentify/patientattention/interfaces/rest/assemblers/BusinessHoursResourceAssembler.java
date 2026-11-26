package com.platform.dentify.patientattention.interfaces.rest.assemblers;

import com.platform.dentify.patientattention.domain.model.commands.UpdateBusinessHoursCommand;
import com.platform.dentify.patientattention.domain.model.entities.BusinessDay;
import com.platform.dentify.patientattention.interfaces.rest.dtos.BusinessDayResource;
import com.platform.dentify.patientattention.interfaces.rest.dtos.BusinessHoursResource;
import com.platform.dentify.patientattention.interfaces.rest.dtos.UpdateBusinessHoursResource;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class BusinessHoursResourceAssembler {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(18, 0);

    private BusinessHoursResourceAssembler() {
    }

    public static BusinessHoursResource toResource(List<BusinessDay> days) {
        if (days.isEmpty()) {
            var proposal = Arrays.stream(DayOfWeek.values())
                    .map(d -> d.getValue() <= 5
                            ? new BusinessDayResource(d.name(), true, DEFAULT_OPEN.format(HOUR), DEFAULT_CLOSE.format(HOUR))
                            : new BusinessDayResource(d.name(), false, null, null))
                    .toList();
            return new BusinessHoursResource(false, proposal);
        }
        var resources = days.stream()
                .map(d -> new BusinessDayResource(d.getDayOfWeek().name(), d.isOpen(),
                        d.getOpensAt() == null ? null : d.getOpensAt().format(HOUR),
                        d.getClosesAt() == null ? null : d.getClosesAt().format(HOUR)))
                .toList();
        return new BusinessHoursResource(true, resources);
    }

    public static UpdateBusinessHoursCommand toCommand(UpdateBusinessHoursResource resource) {
        if (resource == null || resource.days() == null) {
            throw new IllegalArgumentException("El horario debe tener entre 1 y 7 días");
        }
        var days = resource.days().stream()
                .map(d -> new UpdateBusinessHoursCommand.Day(dayOf(d.dayOfWeek()), d.open(),
                        timeOf(d.opensAt()), timeOf(d.closesAt())))
                .toList();
        return new UpdateBusinessHoursCommand(days);
    }

    private static DayOfWeek dayOf(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El día de la semana es obligatorio");
        }
        try {
            return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Día de la semana no válido: " + value);
        }
    }

    private static LocalTime timeOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), HOUR);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Hora no válida (use HH:mm): " + value);
        }
    }
}
