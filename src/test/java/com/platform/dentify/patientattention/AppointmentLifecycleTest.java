package com.platform.dentify.patientattention;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.application.internal.AppointmentCommandServiceImpl;
import com.platform.dentify.patientattention.domain.exceptions.PastAppointmentException;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.DeleteAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentStatusCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.infrastructure.repositories.AppointmentRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.MedicalHistoryRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.PatientRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.TreatmentRepository;
import com.platform.dentify.shared.domain.model.valueobjects.BusinessTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de US05 y US07 (citas pasadas) y US43 (una cita atendida no vuelve atrás). */
class AppointmentLifecycleTest {

    private static final Long USER_ID = 7L;
    /** Ahora: jueves 26/11/2026 a las 10:00 en Lima. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);

    private AppointmentRepository appointments;
    private AppointmentCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getCurrentUserId()).thenReturn(USER_ID);
        when(appointments.findAllByPatient_User_IdAndAppointmentDateBetweenOrderByAppointmentDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        service = new AppointmentCommandServiceImpl(mock(PatientRepository.class), auth, appointments,
                mock(TreatmentRepository.class), mock(MedicalHistoryRepository.class));
        service.useClock(Clock.fixed(NOW.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE));
    }

    private Appointment stored(long id, LocalDateTime start) {
        var a = new Appointment(new CreateAppointmentCommand(start, "Control", LocalTime.of(0, 30), 3L));
        ReflectionTestUtils.setField(a, "id", id);
        a.setPatient(new Patient());
        when(appointments.findByIdAndPatient_User_Id(id, USER_ID)).thenReturn(Optional.of(a));
        when(appointments.findForStatusUpdate(id, USER_ID)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void aFutureAppointmentCanBeRescheduled() {
        stored(1, NOW.plusDays(1));
        assertDoesNotThrow(() -> service.handle(new UpdateAppointmentCommand(1L, NOW.plusDays(2), "Control", LocalTime.of(0, 30))));
    }

    @Test
    void aPastAppointmentCannotBeRescheduled() {
        stored(1, NOW.minusDays(1));
        var error = assertThrows(PastAppointmentException.class,
                () -> service.handle(new UpdateAppointmentCommand(1L, NOW.plusDays(1), "Control", LocalTime.of(0, 30))));
        assertEquals("No se puede reprogramar una cita que ya pasó.", error.getMessage());
        verify(appointments, never()).save(any());
    }

    @Test
    void anAppointmentThatAlreadyStartedCannotBeRescheduled() {
        stored(1, NOW.minusMinutes(10));
        assertThrows(PastAppointmentException.class,
                () -> service.handle(new UpdateAppointmentCommand(1L, NOW.plusDays(1), "Control", LocalTime.of(0, 30))));
    }

    @Test
    void aFutureAppointmentCanBeDeleted() {
        stored(1, NOW.plusHours(2));
        service.handle(new DeleteAppointmentCommand(1L));
        verify(appointments).deleteById(1L);
    }

    @Test
    void aPastAppointmentCannotBeDeleted() {
        stored(1, NOW.minusDays(3));
        assertThrows(PastAppointmentException.class, () -> service.handle(new DeleteAppointmentCommand(1L)));
        verify(appointments, never()).deleteById(any());
    }

    @Test
    void aPastAppointmentCanStillBeMarkedAsAttended() {
        var past = stored(1, NOW.minusDays(1));
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "Limpieza realizada"));
        assertEquals(AppointmentStatus.COMPLETED, past.getStatus());
    }

    @Test
    void anAttendedAppointmentCannotGoBackToScheduled() {
        var attended = stored(1, NOW.minusDays(1));
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "Atendida"));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.handle(new UpdateAppointmentStatusCommand(1L, "SCHEDULED", null)));
        assertEquals("Una cita atendida no puede cambiar de estado.", error.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new UpdateAppointmentStatusCommand(1L, "CANCELLED", null)));
        assertEquals(AppointmentStatus.COMPLETED, attended.getStatus());
    }

    @Test
    void anAttendedAppointmentCanUpdateItsNotes() {
        var attended = stored(1, NOW.minusDays(1));
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "Primera nota"));
        service.handle(new UpdateAppointmentStatusCommand(1L, "COMPLETED", "Nota corregida"));
        assertEquals("Nota corregida", attended.getClinicalNotes());
    }
}
