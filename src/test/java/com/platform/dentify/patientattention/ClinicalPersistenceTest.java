package com.platform.dentify.patientattention;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.patientattention.domain.model.aggregates.*;
import com.platform.dentify.patientattention.domain.model.commands.*;
import com.platform.dentify.patientattention.domain.model.entities.MedicalHistory;
import com.platform.dentify.patientattention.domain.model.valueobjects.*;
import com.platform.dentify.patientattention.infrastructure.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the new JPA mappings on an isolated in-memory database. */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:clinical;NON_KEYWORDS=USER",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class ClinicalPersistenceTest {
    @Autowired TestEntityManager em;
    @Autowired TreatmentPlanRepository plans;
    @Autowired AppointmentRepository appointments;
    @Autowired MedicalHistoryRepository histories;

    private Patient patient() {
        var user = new User(); user.setUsername("clinical-test"); user.setPassword("test-only");
        em.persist(user);
        var patient = new Patient(new CreatePatientCommand("12345678", "Ana", "Paciente", "ana@example.com",
                java.time.LocalDate.of(2000, 1, 1), "Lima", "987654321", "", "", "", ""));
        patient.setUser(user);
        return em.persist(patient);
    }

    private Treatment treatment(Patient patient) {
        var treatment = new Treatment();
        treatment.setUser(patient.getUser()); treatment.setName("Restauración");
        treatment.setPrice(120.50); treatment.setEstimatedDurationMinutes(30);
        treatment.setCategory(TreatmentCategory.values()[0]); treatment.setActive(true);
        return em.persist(treatment);
    }

    @Test void persistsQuoteChildrenAuditAndProgressWithOwnerScopedQueries() {
        var patient = patient(); var treatment = treatment(patient);
        var plan = new TreatmentPlan(patient, "Plan dental", "Dos piezas");
        plan.addItem(treatment, "11", new BigDecimal("120.50"));
        plan.addItem(treatment, "21", new BigDecimal("90.10"));
        plans.saveAndFlush(plan);
        Long planId = plan.getId(), userId = patient.getUser().getId(), patientId = patient.getId();
        em.clear();
        var loaded = plans.findByIdAndUser_Id(planId, userId).orElseThrow();
        assertEquals(new BigDecimal("210.60"), loaded.getTotalAmount());
        assertNotNull(loaded.getCreatedAt());
        assertEquals(2, loaded.getItems().size());
        loaded.changeStatus(TreatmentPlanStatus.APPROVED);
        loaded.changeItemStatus(loaded.getItems().get(0).getId(), TreatmentPlanItemStatus.DONE);
        plans.saveAndFlush(loaded); em.clear();
        loaded = plans.findAllByPatient_IdAndUser_IdOrderByCreatedAtDesc(patientId, userId).get(0);
        assertEquals(new BigDecimal("50.00"), loaded.getProgressPercentage());
        assertEquals(TreatmentPlanStatus.IN_PROGRESS, loaded.getStatus());
        assertTrue(plans.findByIdAndUser_Id(planId, userId + 1).isEmpty());
    }

    @Test void historyStoresAllNotesAndSurvivesAppointmentDeletion() {
        var patient = patient(); var treatment = treatment(patient);
        var appointment = new Appointment(new CreateAppointmentCommand(
                LocalDateTime.of(2026, 11, 19, 10, 0), "Control", LocalTime.of(0, 30), patient.getId()));
        appointment.setPatient(patient); appointment.setTreatment(treatment);
        em.persistAndFlush(appointment);
        var history = new MedicalHistory(new CreateMedicalHistoryCommand("Restauración", "a".repeat(500), "", "No registrada", patient.getId()));
        history.setPatient(patient); history.setSourceAppointmentId(appointment.getId());
        history.setAppointmentDate(appointment.getAppointmentDate());
        histories.saveAndFlush(history);
        Long appointmentId = appointment.getId(), userId = patient.getUser().getId(), historyId = history.getId();
        assertTrue(appointments.findForStatusUpdate(appointmentId, userId).isPresent());
        assertTrue(histories.existsBySourceAppointmentId(appointmentId));
        appointments.delete(appointment); em.flush(); em.clear();
        assertEquals(500, histories.findById(historyId).orElseThrow().getRecord().length());
        assertTrue(histories.existsBySourceAppointmentId(appointmentId));
    }
}
