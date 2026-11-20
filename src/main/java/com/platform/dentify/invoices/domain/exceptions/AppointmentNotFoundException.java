package com.platform.dentify.invoices.domain.exceptions;

/**
 * La cita no existe o no pertenece al usuario autenticado. Ambos casos se responden igual
 * (404) para no revelar citas de otros usuarios.
 */
public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException(Long appointmentId) {
        super("La cita " + appointmentId + " no existe o no pertenece al usuario");
    }
}
