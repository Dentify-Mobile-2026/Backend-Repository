package com.platform.dentify.patientattention;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.application.internal.AppointmentCommandServiceImpl;
import com.platform.dentify.patientattention.domain.exceptions.AppointmentConflictException;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentCommand;
import com.platform.dentify.patientattention.infrastructure.repositories.AppointmentRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de la regla «no se permiten citas que se solapen» (HU de citas, objetivo OE1). */
class AppointmentConflictTest {

    private static final Long USER_ID = 7L;
    private static final Long PATIENT_ID = 3L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 1, 9, 0);

    private AppointmentRepository appointmentRepository;
    private AppointmentCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        AuthenticatedUserProvider userProvider = mock(AuthenticatedUserProvider.class);
        when(userProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(patientRepository.findByIdAndUser_Id(PATIENT_ID, USER_ID)).thenReturn(Optional.of(new Patient()));
        service = new AppointmentCommandServiceImpl(patientRepository, userProvider, appointmentRepository);
    }

    /** Cita existente de 9:00 a 10:00. */
    private Appointment existing(long id, LocalDateTime start, LocalTime duration) {
        var a = new Appointment(new CreateAppointmentCommand(start, "Consulta", duration, PATIENT_ID));
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private void givenExisting(Appointment... appointments) {
        when(appointmentRepository
                .findAllByPatient_User_IdAndAppointmentDateBetweenOrderByAppointmentDateAsc(
                        any(), any(), any()))
                .thenReturn(List.of(appointments));
    }

    private CreateAppointmentCommand create(LocalDateTime start, LocalTime duration) {
        return new CreateAppointmentCommand(start, "Limpieza", duration, PATIENT_ID);
    }

    @Test
    void createsAppointmentWhenThereIsNoOverlap() {
        givenExisting(existing(1, NINE, LocalTime.of(1, 0)));

        var result = service.handle(create(NINE.plusHours(2), LocalTime.of(0, 30)));

        assertTrue(result.isPresent());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void rejectsAppointmentThatStartsInsideAnotherOne() {
        givenExisting(existing(1, NINE, LocalTime.of(1, 0)));

        assertThrows(AppointmentConflictException.class,
                () -> service.handle(create(NINE.plusMinutes(30), LocalTime.of(0, 30))));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsAppointmentThatEndsInsideAnotherOne() {
        givenExisting(existing(1, NINE, LocalTime.of(1, 0)));

        assertThrows(AppointmentConflictException.class,
                () -> service.handle(create(NINE.minusMinutes(30), LocalTime.of(1, 0))));
    }

    @Test
    void rejectsAppointmentThatContainsAnotherOne() {
        givenExisting(existing(1, NINE, LocalTime.of(0, 30)));

        assertThrows(AppointmentConflictException.class,
                () -> service.handle(create(NINE.minusHours(1), LocalTime.of(3, 0))));
    }

    @Test
    void rejectsAppointmentWithTheSameStartTime() {
        givenExisting(existing(1, NINE, LocalTime.of(1, 0)));

        assertThrows(AppointmentConflictException.class,
                () -> service.handle(create(NINE, LocalTime.of(0, 15))));
    }

    @Test
    void allowsBackToBackAppointments() {
        givenExisting(existing(1, NINE, LocalTime.of(1, 0)));

        var result = service.handle(create(NINE.plusHours(1), LocalTime.of(1, 0)));

        assertTrue(result.isPresent());
    }

    @Test
    void updateIgnoresTheAppointmentBeingEdited() {
        var current = existing(1, NINE, LocalTime.of(1, 0));
        givenExisting(current);
        when(appointmentRepository.findByIdAndPatient_User_Id(1L, USER_ID)).thenReturn(Optional.of(current));

        var result = service.handle(new UpdateAppointmentCommand(1L, NINE.plusMinutes(15), "Control", LocalTime.of(1, 0)));

        assertTrue(result.isPresent());
        assertEquals(NINE.plusMinutes(15), current.getAppointmentDate());
    }

    @Test
    void updateIsRejectedWhenItOverlapsAnotherAppointment() {
        var current = existing(1, NINE, LocalTime.of(1, 0));
        var other = existing(2, NINE.plusHours(2), LocalTime.of(1, 0));
        givenExisting(current, other);
        when(appointmentRepository.findByIdAndPatient_User_Id(1L, USER_ID)).thenReturn(Optional.of(current));

        assertThrows(AppointmentConflictException.class,
                () -> service.handle(new UpdateAppointmentCommand(1L, NINE.plusMinutes(90), "Control", LocalTime.of(1, 0))));
        verify(appointmentRepository, never()).save(any());
        assertEquals(NINE, current.getAppointmentDate());
    }
}
