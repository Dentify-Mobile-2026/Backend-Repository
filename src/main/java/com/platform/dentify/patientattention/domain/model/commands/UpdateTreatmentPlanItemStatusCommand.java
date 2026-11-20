package com.platform.dentify.patientattention.domain.model.commands;

import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentPlanItemStatus;

public record UpdateTreatmentPlanItemStatusCommand(Long planId, Long itemId, TreatmentPlanItemStatus status) {}
