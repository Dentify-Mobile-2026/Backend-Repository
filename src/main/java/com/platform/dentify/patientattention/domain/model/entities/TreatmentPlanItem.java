package com.platform.dentify.patientattention.domain.model.entities;

import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.aggregates.TreatmentPlan;
import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentPlanItemStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
public class TreatmentPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_plan_id", nullable = false)
    private TreatmentPlan treatmentPlan;

    @ManyToOne(optional = false)
    @JoinColumn(name = "treatment_id", nullable = false)
    private Treatment treatment;

    // Snapshot: changing the catalog must not change an accepted quote.
    @Column(nullable = false, length = 120)
    private String treatmentName;

    @Column(length = 2)
    private String toothNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal agreedPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreatmentPlanItemStatus itemStatus = TreatmentPlanItemStatus.PENDING;

    protected TreatmentPlanItem() {}

    public TreatmentPlanItem(TreatmentPlan plan, Treatment treatment, String toothNumber, BigDecimal price) {
        if (treatment == null || treatment.getName() == null) {
            throw new IllegalArgumentException("Treatment is required");
        }
        if (price == null || price.signum() < 0 || price.precision() - price.scale() > 17) {
            throw new IllegalArgumentException("Agreed price must be a non-negative amount with at most 17 integer digits");
        }
        try {
            this.agreedPrice = price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Agreed price supports at most two decimal places");
        }
        String tooth = toothNumber == null || toothNumber.isBlank() ? null : toothNumber.trim();
        if (tooth != null && !tooth.matches("[1-4][1-8]|[5-8][1-5]")) {
            throw new IllegalArgumentException("Use a valid FDI tooth number");
        }
        this.treatmentPlan = plan;
        this.treatment = treatment;
        this.treatmentName = treatment.getName();
        this.toothNumber = tooth;
    }

    public void changeStatus(TreatmentPlanItemStatus status) {
        if (status == null) throw new IllegalArgumentException("Item status is required");
        this.itemStatus = status;
    }
}
