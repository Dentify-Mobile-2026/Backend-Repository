package com.platform.dentify.patientattention.domain.services;

import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.DeleteTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateTreatmentCommand;

import java.util.Optional;

public interface TreatmentCommandService {
    Long handle(CreateTreatmentCommand command);
    Optional<Treatment> handle(UpdateTreatmentCommand command);
    void handle(DeleteTreatmentCommand command);
}
