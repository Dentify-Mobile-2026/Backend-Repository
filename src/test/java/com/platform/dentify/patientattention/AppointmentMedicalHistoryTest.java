package com.platform.dentify.patientattention;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.application.internal.AppointmentCommandServiceImpl;
import com.platform.dentify.patientattention.domain.model.aggregates.*;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentStatusCommand;
import com.platform.dentify.patientattention.domain.model.entities.MedicalHistory;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.infrastructure.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentMedicalHistoryTest {
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final MedicalHistoryRepository histories = mock(MedicalHistoryRepository.class);
    private final AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
    private final AppointmentCommandServiceImpl service = new AppointmentCommandServiceImpl(
            mock(PatientRepository.class), auth, appointments, mock(TreatmentRepository.class), histories);
    private Appointment appointment;

    @BeforeEach void setUp() {
        when(auth.getCurrentUserId()).thenReturn(7L);
        appointment = new Appointment();
        ReflectionTestUtils.setField(appointment, "id", 1L);
        var patient = new Patient(); ReflectionTestUtils.setField(patient, "id", 3L);
        var treatment = new Treatment(); treatment.setName("Restauración");
        appointment.setPatient(patient); appointment.setTreatment(treatment);
        appointment.setAppointmentDate(LocalDateTime.of(2026, 11, 19, 10, 0));
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        when(appointments.findForStatusUpdate(1L, 7L)).thenReturn(Optional.of(appointment));
    }

    @Test void recordsTreatmentDateAndFullClinicalNotesAtomically() {
        String notes = "a".repeat(500);
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", notes, true));
        var capture = ArgumentCaptor.forClass(MedicalHistory.class);
        verify(histories).save(capture.capture());
        assertEquals("Restauración", capture.getValue().getDescription());
        assertEquals(notes, capture.getValue().getRecord());
        assertEquals(appointment.getAppointmentDate(), capture.getValue().getAppointmentDate());
        assertEquals(1L, capture.getValue().getSourceAppointmentId());
        assertEquals(appointment.getPatient(), capture.getValue().getPatient());
        assertTrue(appointment.getCompleted());
    }

    @Test void retriesAndRecompletionsDoNotDuplicateMedicalHistory() {
        when(histories.existsBySourceAppointmentId(1L)).thenReturn(true);
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "Notas", true));
        verify(histories, never()).save(any());
    }

    @Test void oldClientsCanCompleteWithoutCreatingHistory() {
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "Notas"));
        verifyNoInteractions(histories);
        assertTrue(appointment.getCompleted());
    }

    @Test void rejectsHistoryWithoutTreatmentOrCompletion() {
        assertThrows(IllegalArgumentException.class, () -> service.handle(
                new UpdateAppointmentStatusCommand(1L, "CANCELLED", "", true)));
        appointment.setTreatment(null);
        assertThrows(IllegalArgumentException.class, () -> service.handle(
                new UpdateAppointmentStatusCommand(1L, "COMPLETED", "", true)));
        verifyNoInteractions(histories);
        verify(appointments, never()).save(any());
    }

    @Test void foreignAppointmentCannotCreateHistory() {
        when(appointments.findForStatusUpdate(1L, 7L)).thenReturn(Optional.empty());
        assertTrue(service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "", true)).isEmpty());
        verifyNoInteractions(histories);
    }
}
