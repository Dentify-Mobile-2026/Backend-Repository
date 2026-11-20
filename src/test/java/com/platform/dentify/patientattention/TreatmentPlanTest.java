package com.platform.dentify.patientattention;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.patientattention.domain.model.aggregates.*;
import com.platform.dentify.patientattention.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentPlanTest {
    private TreatmentPlan plan() {
        var patient = new Patient();
        patient.setUser(new User());
        var plan = new TreatmentPlan(patient, "Rehabilitación", "Dos piezas");
        var treatment = new Treatment();
        treatment.setName("Restauración");
        plan.addItem(treatment, "11", new BigDecimal("120.50"));
        plan.addItem(treatment, "21", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(plan.getItems().get(0), "id", 1L);
        ReflectionTestUtils.setField(plan.getItems().get(1), "id", 2L);
        return plan;
    }

    @Test void totalsUseExactDecimalArithmeticAndCatalogSnapshots() {
        var plan = plan();
        plan.getItems().get(0).getTreatment().setName("Nuevo nombre");
        assertEquals(new BigDecimal("120.60"), plan.getTotalAmount());
        assertEquals("Restauración", plan.getItems().get(0).getTreatmentName());
        assertEquals(0, plan.getCompletedItems());
    }

    @Test void progressFollowsItemsIncludingReopeningAnIncorrectlyCompletedItem() {
        var plan = plan();
        plan.changeStatus(TreatmentPlanStatus.APPROVED);
        plan.changeItemStatus(1L, TreatmentPlanItemStatus.DONE);
        assertEquals(TreatmentPlanStatus.IN_PROGRESS, plan.getStatus());
        assertEquals(new BigDecimal("50.00"), plan.getProgressPercentage());
        plan.changeItemStatus(2L, TreatmentPlanItemStatus.DONE);
        assertEquals(TreatmentPlanStatus.COMPLETED, plan.getStatus());
        assertEquals(new BigDecimal("100.00"), plan.getProgressPercentage());
        plan.changeItemStatus(2L, TreatmentPlanItemStatus.PENDING);
        assertEquals(TreatmentPlanStatus.IN_PROGRESS, plan.getStatus());
        plan.changeItemStatus(1L, TreatmentPlanItemStatus.PENDING);
        assertEquals(TreatmentPlanStatus.APPROVED, plan.getStatus());
    }

    @Test void rejectsInvalidLifecycleTransitionsAndForeignItems() {
        var plan = plan();
        assertThrows(IllegalArgumentException.class, () -> plan.changeItemStatus(1L, TreatmentPlanItemStatus.DONE));
        assertThrows(IllegalArgumentException.class, () -> plan.changeStatus(TreatmentPlanStatus.COMPLETED));
        plan.changeStatus(TreatmentPlanStatus.APPROVED);
        assertThrows(IllegalArgumentException.class, () -> plan.changeItemStatus(999L, TreatmentPlanItemStatus.DONE));
        plan.changeStatus(TreatmentPlanStatus.CANCELLED);
        assertThrows(IllegalArgumentException.class, () -> plan.changeItemStatus(1L, TreatmentPlanItemStatus.DONE));
        assertThrows(IllegalArgumentException.class, () -> plan.changeStatus(TreatmentPlanStatus.APPROVED));
    }

    @Test void rejectsNegativeFractionalPricesAndInvalidTeeth() {
        var plan = plan();
        var treatment = plan.getItems().get(0).getTreatment();
        assertThrows(IllegalArgumentException.class, () -> plan.addItem(treatment, "11", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> plan.addItem(treatment, "11", new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class, () -> plan.addItem(treatment, "99", BigDecimal.ONE));
        assertThrows(UnsupportedOperationException.class, () -> plan.getItems().clear());
    }

    @Test void approvalRequiresMultipleItems() {
        var patient = new Patient();
        patient.setUser(new User());
        var plan = new TreatmentPlan(patient, "Evaluación", "");
        assertThrows(IllegalArgumentException.class, () -> plan.changeStatus(TreatmentPlanStatus.APPROVED));
    }
}
