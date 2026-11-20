package com.platform.dentify.patientattention;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.patientattention.application.internal.TreatmentPlanServiceImpl;
import com.platform.dentify.patientattention.domain.model.aggregates.*;
import com.platform.dentify.patientattention.domain.model.commands.*;
import com.platform.dentify.patientattention.domain.model.queries.GetTreatmentPlansByPatientQuery;
import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentPlanItemStatus;
import com.platform.dentify.patientattention.infrastructure.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TreatmentPlanServiceTest {
    private final TreatmentPlanRepository plans = mock(TreatmentPlanRepository.class);
    private final PatientRepository patients = mock(PatientRepository.class);
    private final TreatmentRepository treatments = mock(TreatmentRepository.class);
    private final AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
    private final TreatmentPlanServiceImpl service = new TreatmentPlanServiceImpl(plans, patients, treatments, auth);

    @BeforeEach void setUp() { when(auth.getCurrentUserId()).thenReturn(7L); }

    private CreateTreatmentPlanCommand command() {
        return new CreateTreatmentPlanCommand(3L, "Plan", "", List.of(
                new CreateTreatmentPlanCommand.Item(4L, "11", new BigDecimal("120.50")),
                new CreateTreatmentPlanCommand.Item(4L, "21", new BigDecimal("90.00"))));
    }

    @Test void createsQuoteOnlyUsingOwnedPatientsAndTreatments() {
        var patient = new Patient(); patient.setUser(new User());
        var treatment = new Treatment(); treatment.setName("Restauración"); treatment.setActive(true);
        when(patients.findByIdAndUser_Id(3L, 7L)).thenReturn(Optional.of(patient));
        when(treatments.findByIdAndUser_Id(4L, 7L)).thenReturn(Optional.of(treatment));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(new BigDecimal("210.50"), service.handle(command()).getTotalAmount());
    }

    @Test void foreignPatientCannotBeQuotedOrListed() {
        assertThrows(ResponseStatusException.class, () -> service.handle(command()));
        assertThrows(ResponseStatusException.class, () -> service.handle(new GetTreatmentPlansByPatientQuery(3L)));
        verifyNoInteractions(plans, treatments);
    }

    @Test void foreignCatalogTreatmentCannotBeQuoted() {
        var patient = new Patient(); patient.setUser(new User());
        when(patients.findByIdAndUser_Id(3L, 7L)).thenReturn(Optional.of(patient));
        assertThrows(ResponseStatusException.class, () -> service.handle(command()));
        verify(plans, never()).save(any());
    }

    @Test void foreignPlanCannotBeUpdated() {
        assertThrows(ResponseStatusException.class, () -> service.handle(
                new UpdateTreatmentPlanItemStatusCommand(99L, 1L, TreatmentPlanItemStatus.DONE)));
        verify(plans, never()).save(any());
    }
}
