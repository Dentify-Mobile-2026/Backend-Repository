package com.platform.dentify.patientattention.domain.services;

import com.platform.dentify.patientattention.domain.model.commands.AnnulMedicalHistoryCommand;
import com.platform.dentify.patientattention.domain.model.commands.CreateMedicalHistoryCommand;
import com.platform.dentify.patientattention.domain.model.entities.MedicalHistory;

import java.util.Optional;

public interface MedicalHistoryCommandService {
    Optional<MedicalHistory> handle(CreateMedicalHistoryCommand command);

    /** Anula una entrada con motivo (US18); vacío si la entrada no existe o no es del consultorio. */
    Optional<MedicalHistory> handle(AnnulMedicalHistoryCommand command);
}
