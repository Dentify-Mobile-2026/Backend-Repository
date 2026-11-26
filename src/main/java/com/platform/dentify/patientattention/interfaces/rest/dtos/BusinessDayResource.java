package com.platform.dentify.patientattention.interfaces.rest.dtos;

/**
 * Un día del horario de atención. {@code dayOfWeek} va en inglés y mayúsculas (MONDAY a SUNDAY);
 * las horas, en formato HH:mm y solo cuando el día está abierto.
 */
public record BusinessDayResource(String dayOfWeek, boolean open, String opensAt, String closesAt) {
}
