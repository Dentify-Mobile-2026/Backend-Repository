package com.platform.dentify.patientattention;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.iam.infrastructure.repositories.UserRepository;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.application.internal.AppointmentCommandServiceImpl;
import com.platform.dentify.patientattention.application.internal.BusinessHoursServiceImpl;
import com.platform.dentify.patientattention.domain.exceptions.AppointmentOutsideBusinessHoursException;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateBusinessHoursCommand;
import com.platform.dentify.patientattention.domain.model.entities.BusinessDay;
import com.platform.dentify.patientattention.infrastructure.repositories.AppointmentRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.BusinessDayRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.MedicalHistoryRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.PatientRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.TreatmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de US09: horario de atención del consultorio. */
class BusinessHoursTest {

    private static final Long USER_ID = 7L;
    private static final Long PATIENT_ID = 3L;
    /** Lunes 30/11/2026. */
    private static final LocalDateTime MONDAY_10 = LocalDateTime.of(2026, 11, 30, 10, 0);
    private static final LocalDateTime SATURDAY_10 = LocalDateTime.of(2026, 12, 5, 10, 0);
    private static final LocalTime ONE_HOUR = LocalTime.of(1, 0);

    private AppointmentRepository appointments;
    private BusinessHoursServiceImpl businessHours;
    private AppointmentCommandServiceImpl service;
    private final List<BusinessDay> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        BusinessDayRepository days = mock(BusinessDayRepository.class);
        appointments = mock(AppointmentRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        PatientRepository patients = mock(PatientRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(auth.getCurrentUserId()).thenReturn(USER_ID);
        when(patients.findByIdAndUser_Id(PATIENT_ID, USER_ID)).thenReturn(Optional.of(new Patient()));
        when(users.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(days.findAllByUser_Id(USER_ID)).thenAnswer(inv -> List.copyOf(stored));
        when(days.findByUser_IdAndDayOfWeek(eq(USER_ID), any())).thenAnswer(inv ->
                stored.stream().filter(d -> d.getDayOfWeek() == inv.getArgument(1)).findFirst());
        when(days.save(any(BusinessDay.class))).thenAnswer(inv -> {
            BusinessDay day = inv.getArgument(0);
            if (!stored.contains(day)) {
                stored.add(day);
            }
            return day;
        });
        businessHours = new BusinessHoursServiceImpl(days, users, auth);
        service = new AppointmentCommandServiceImpl(patients, auth, appointments,
                mock(TreatmentRepository.class), mock(MedicalHistoryRepository.class), businessHours);
    }

    private static UpdateBusinessHoursCommand mondayToFriday(LocalTime opens, LocalTime closes) {
        var list = new ArrayList<UpdateBusinessHoursCommand.Day>();
        for (DayOfWeek d : DayOfWeek.values()) {
            boolean open = d.getValue() <= 5;
            list.add(new UpdateBusinessHoursCommand.Day(d, open, open ? opens : null, open ? closes : null));
        }
        return new UpdateBusinessHoursCommand(list);
    }

    private static CreateAppointmentCommand at(LocalDateTime start) {
        return new CreateAppointmentCommand(start, "Limpieza", ONE_HOUR, PATIENT_ID);
    }

    @Test
    void withoutBusinessHoursNoAppointmentIsRestricted() {
        assertTrue(service.handle(at(SATURDAY_10)).isPresent());
    }

    @Test
    void rejectsAppointmentOnSaturdayWhenOfficeWorksMondayToFriday() {
        businessHours.handle(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(18, 0)));

        var error = assertThrows(AppointmentOutsideBusinessHoursException.class, () -> service.handle(at(SATURDAY_10)));

        assertEquals("El consultorio no atiende ese día.", error.getMessage());
        verify(appointments, never()).save(any());
    }

    @Test
    void rejectsAppointmentOnADayMarkedAsClosed() {
        var list = new ArrayList<>(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(18, 0)).days());
        list.set(0, new UpdateBusinessHoursCommand.Day(DayOfWeek.MONDAY, false, null, null));
        businessHours.handle(new UpdateBusinessHoursCommand(list));

        assertThrows(AppointmentOutsideBusinessHoursException.class, () -> service.handle(at(MONDAY_10)));
    }

    @Test
    void rejectsAppointmentThatEndsAfterClosingTime() {
        businessHours.handle(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(18, 0)));

        var error = assertThrows(AppointmentOutsideBusinessHoursException.class,
                () -> service.handle(at(MONDAY_10.withHour(17).withMinute(30))));

        assertTrue(error.getMessage().contains("09:00 a 18:00"));
    }

    @Test
    void acceptsAppointmentInsideBusinessHours() {
        businessHours.handle(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(18, 0)));

        assertTrue(service.handle(at(MONDAY_10)).isPresent());
        assertTrue(service.handle(at(MONDAY_10.withHour(17))).isPresent());
    }

    @Test
    void savedChangeAppliesToNewAppointments() {
        businessHours.handle(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(18, 0)));
        assertThrows(AppointmentOutsideBusinessHoursException.class, () -> service.handle(at(MONDAY_10.withHour(18))));

        businessHours.handle(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(20, 0)));

        assertTrue(service.handle(at(MONDAY_10.withHour(18))).isPresent());
        assertEquals(7, stored.size(), "Each day is updated in place, never duplicated");
    }

    @Test
    void editingTheReasonOfAnOldAppointmentIgnoresNewBusinessHours() {
        var old = new Appointment(at(SATURDAY_10));
        ReflectionTestUtils.setField(old, "id", 1L);
        when(appointments.findByIdAndPatient_User_Id(1L, USER_ID)).thenReturn(Optional.of(old));
        businessHours.handle(mondayToFriday(LocalTime.of(9, 0), LocalTime.of(18, 0)));

        assertDoesNotThrow(() -> service.handle(new UpdateAppointmentCommand(1L, SATURDAY_10, "Control", ONE_HOUR)));
        assertThrows(AppointmentOutsideBusinessHoursException.class,
                () -> service.handle(new UpdateAppointmentCommand(1L, SATURDAY_10.plusHours(1), "Control", ONE_HOUR)));
    }

    @Test
    void openDayNeedsOpeningBeforeClosing() {
        assertThrows(IllegalArgumentException.class,
                () -> new BusinessDay(null, DayOfWeek.MONDAY, true, LocalTime.of(18, 0), LocalTime.of(9, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new BusinessDay(null, DayOfWeek.MONDAY, true, null, LocalTime.of(9, 0)));
        var closed = new BusinessDay(null, DayOfWeek.SUNDAY, false, LocalTime.of(9, 0), LocalTime.of(18, 0));
        assertFalse(closed.isOpen());
        assertNull(closed.getOpensAt());
    }

    @Test
    void repeatedOrMissingDaysAreRejected() {
        var day = new UpdateBusinessHoursCommand.Day(DayOfWeek.MONDAY, false, null, null);
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessHoursCommand(List.of(day, day)));
        assertThrows(IllegalArgumentException.class, () -> new UpdateBusinessHoursCommand(List.of()));
    }

    @Test
    void appointmentCrossingMidnightNeverFits() {
        var day = new BusinessDay(null, DayOfWeek.MONDAY, true, LocalTime.of(0, 0), LocalTime.of(23, 59));
        assertFalse(day.admits(MONDAY_10.withHour(23).withMinute(30), MONDAY_10.plusDays(1).withHour(0).withMinute(30)));
    }
}
