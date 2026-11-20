package com.platform.dentify.patientattention.domain.model.commands;

import java.math.BigDecimal;
import java.util.List;

public record CreateTreatmentPlanCommand(Long patientId, String title, String notes, List<Item> items) {
    public record Item(Long treatmentId, String toothNumber, BigDecimal agreedPrice) {}
}
