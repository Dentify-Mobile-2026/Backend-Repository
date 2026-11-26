package com.platform.dentify.patientattention.domain.exceptions;

/**
 * Se lanza cuando una cita nueva o reprogramada cae fuera del horario de atención del
 * consultorio (US09). Hereda de {@link AppointmentConflictException} para responder 409, igual
 * que un cruce de citas.
 */
public class AppointmentOutsideBusinessHoursException extends AppointmentConflictException {

    /** Código que la aplicación usa para distinguir este rechazo de un cruce de citas. */
    public static final String CODE = "OUTSIDE_BUSINESS_HOURS";

    public AppointmentOutsideBusinessHoursException(String message) {
        super(message);
    }
}
