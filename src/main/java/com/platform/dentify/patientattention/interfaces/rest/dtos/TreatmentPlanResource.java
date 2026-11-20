package com.platform.dentify.patientattention.interfaces.rest.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TreatmentPlanResource(Long id, Long patientId, String title, String status,
        BigDecimal totalAmount, String notes, long completedItems, int totalItems,
        BigDecimal progressPercentage, LocalDateTime createdAt, LocalDateTime updatedAt, List<Item> items) {
    public record Item(Long id, Long treatmentId, String treatmentName, String toothNumber,
                       BigDecimal agreedPrice, String itemStatus) {}
}
