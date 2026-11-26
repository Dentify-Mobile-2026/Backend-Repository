package com.platform.dentify.patientattention.domain.model.commands;

/** Anula una entrada de la historia clínica con un motivo (US18). */
public record AnnulMedicalHistoryCommand(Long patientId, Long medicalHistoryId, String reason) {
}
