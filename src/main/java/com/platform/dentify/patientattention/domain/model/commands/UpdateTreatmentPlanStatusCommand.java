package com.platform.dentify.patientattention.domain.model.commands;

import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentPlanStatus;

public record UpdateTreatmentPlanStatusCommand(Long patientId, Long planId, TreatmentPlanStatus status) {}
