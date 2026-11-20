package com.platform.dentify.patientattention.interfaces.rest.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CreateTreatmentPlanResource(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String notes,
        @NotNull @Size(min = 2, max = 100) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Positive Long treatmentId,
                       @Pattern(regexp = "^$|[1-4][1-8]|[5-8][1-5]") String toothNumber,
                       @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal agreedPrice) {}
}
