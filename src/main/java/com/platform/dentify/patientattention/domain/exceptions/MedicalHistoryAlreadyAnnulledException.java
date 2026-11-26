package com.platform.dentify.patientattention.domain.exceptions;

/** Se lanza al intentar anular una entrada de la historia clínica que ya estaba anulada (US18). */
public class MedicalHistoryAlreadyAnnulledException extends IllegalStateException {
    public MedicalHistoryAlreadyAnnulledException(String message) {
        super(message);
    }
}
