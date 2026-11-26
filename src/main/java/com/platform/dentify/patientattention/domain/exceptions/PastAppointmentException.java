package com.platform.dentify.patientattention.domain.exceptions;

/**
 * Se lanza al intentar reprogramar o eliminar una cita que ya empezó (US05 y US07). Hereda de
 * {@link AppointmentConflictException} para responder 409.
 */
public class PastAppointmentException extends AppointmentConflictException {

    /** Código que la aplicación usa para mostrar el mensaje del servicio. */
    public static final String CODE = "PAST_APPOINTMENT";

    public PastAppointmentException(String message) {
        super(message);
    }
}
