package com.platform.dentify.patientattention.domain.exceptions;

/**
 * Se lanza cuando una cita nueva o editada se solapa con otra cita del mismo profesional.
 */
public class AppointmentConflictException extends RuntimeException {
    public AppointmentConflictException(String message) {
        super(message);
    }
}
