package com.platform.dentify.patientattention.interfaces.rest.dtos;

import java.util.List;

/**
 * Horario de atención del consultorio. {@code configured} es falso mientras el consultorio no lo
 * haya guardado; en ese caso {@code days} trae una propuesta (lunes a viernes de 09:00 a 18:00) y
 * todavía no se restringe ninguna cita.
 */
public record BusinessHoursResource(boolean configured, List<BusinessDayResource> days) {
}
