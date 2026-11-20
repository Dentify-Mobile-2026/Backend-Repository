package com.platform.dentify.patientattention.domain.model.aggregates;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.patientattention.domain.model.entities.TreatmentPlanItem;
import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentPlanItemStatus;
import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentPlanStatus;
import com.platform.dentify.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Getter
public class TreatmentPlan extends AuditableAbstractAggregateRoot<TreatmentPlan> {
    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreatmentPlanStatus status = TreatmentPlanStatus.DRAFT;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = new BigDecimal("0.00");

    @Version
    private Long version;

    @OneToMany(mappedBy = "treatmentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TreatmentPlanItem> items = new ArrayList<>();

    protected TreatmentPlan() {}

    public TreatmentPlan(Patient patient, String title, String notes) {
        if (patient == null || patient.getUser() == null) throw new IllegalArgumentException("Patient and owner are required");
        if (title == null || title.isBlank() || title.trim().length() > 120) throw new IllegalArgumentException("Title is required (maximum 120 characters)");
        if (notes != null && notes.length() > 1000) throw new IllegalArgumentException("Notes support at most 1000 characters");
        this.patient = patient;
        this.user = patient.getUser();
        this.title = title.trim();
        this.notes = notes == null ? "" : notes.trim();
    }

    public List<TreatmentPlanItem> getItems() { return Collections.unmodifiableList(items); }

    public void addItem(Treatment treatment, String tooth, BigDecimal price) {
        if (status != TreatmentPlanStatus.DRAFT) throw new IllegalArgumentException("Only draft plans can be edited");
        var item = new TreatmentPlanItem(this, treatment, tooth, price);
        var nextTotal = totalAmount.add(item.getAgreedPrice());
        if (nextTotal.precision() - nextTotal.scale() > 17) throw new IllegalArgumentException("Plan total exceeds the supported amount");
        items.add(item);
        totalAmount = nextTotal;
    }

    public void changeStatus(TreatmentPlanStatus next) {
        if (next == null) throw new IllegalArgumentException("Plan status is required");
        if (next == status) return;
        if (status == TreatmentPlanStatus.CANCELLED || status == TreatmentPlanStatus.COMPLETED) throw new IllegalArgumentException("Closed plans cannot change status directly");
        if (next == TreatmentPlanStatus.APPROVED && status == TreatmentPlanStatus.DRAFT) {
            if (items.size() < 2) throw new IllegalArgumentException("A plan requires at least two items");
            status = next;
        } else if (next == TreatmentPlanStatus.CANCELLED) {
            status = next;
        } else {
            throw new IllegalArgumentException("Progress and completion are calculated from plan items");
        }
    }

    public void changeItemStatus(Long itemId, TreatmentPlanItemStatus next) {
        if (status == TreatmentPlanStatus.DRAFT || status == TreatmentPlanStatus.CANCELLED) throw new IllegalArgumentException("Approve the plan before recording progress");
        var item = items.stream().filter(i -> itemId != null && itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item does not belong to this plan"));
        item.changeStatus(next);
        status = getCompletedItems() == items.size() ? TreatmentPlanStatus.COMPLETED
                : items.stream().anyMatch(i -> i.getItemStatus() != TreatmentPlanItemStatus.PENDING)
                ? TreatmentPlanStatus.IN_PROGRESS : TreatmentPlanStatus.APPROVED;
    }

    public long getCompletedItems() {
        return items.stream().filter(i -> i.getItemStatus() == TreatmentPlanItemStatus.DONE).count();
    }

    public BigDecimal getProgressPercentage() {
        return items.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(getCompletedItems() * 100)
                .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    }
}
