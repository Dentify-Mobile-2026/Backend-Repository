package com.platform.dentify.patientattention.application.internal;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.domain.exceptions.AppointmentConflictException;
import com.platform.dentify.patientattention.domain.exceptions.PastAppointmentException;
import com.platform.dentify.patientattention.domain.model.aggregates.Appointment;
import com.platform.dentify.patientattention.domain.model.commands.CreateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.DeleteAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateAppointmentStatusCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.AppointmentStatus;
import com.platform.dentify.patientattention.domain.services.AppointmentCommandService;
import com.platform.dentify.patientattention.domain.services.BusinessHoursService;
import com.platform.dentify.patientattention.infrastructure.repositories.AppointmentRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.PatientRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.TreatmentRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.MedicalHistoryRepository;
import com.platform.dentify.patientattention.domain.model.entities.MedicalHistory;
import com.platform.dentify.patientattention.domain.model.commands.CreateMedicalHistoryCommand;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.platform.dentify.shared.domain.model.valueobjects.BusinessTime;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Service
public class AppointmentCommandServiceImpl implements AppointmentCommandService {

    private final PatientRepository patientRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final AppointmentRepository appointmentRepository;
    private final TreatmentRepository treatmentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final BusinessHoursService businessHoursService;
    private Clock clock = Clock.system(BusinessTime.ZONE);

    @Autowired
    public AppointmentCommandServiceImpl(
            PatientRepository patientRepository,
            AuthenticatedUserProvider authenticatedUserProvider,
            AppointmentRepository appointmentRepository,
            TreatmentRepository treatmentRepository,
            MedicalHistoryRepository medicalHistoryRepository,
            BusinessHoursService businessHoursService
    ) {
        this.patientRepository = patientRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.appointmentRepository = appointmentRepository;
        this.treatmentRepository = treatmentRepository;
        this.medicalHistoryRepository = medicalHistoryRepository;
        this.businessHoursService = businessHoursService;
    }

    public AppointmentCommandServiceImpl(
            PatientRepository patientRepository,
            AuthenticatedUserProvider authenticatedUserProvider,
            AppointmentRepository appointmentRepository,
            TreatmentRepository treatmentRepository,
            MedicalHistoryRepository medicalHistoryRepository
    ) {
        this(patientRepository, authenticatedUserProvider, appointmentRepository,
                treatmentRepository, medicalHistoryRepository, null);
    }

    public AppointmentCommandServiceImpl(
            PatientRepository patientRepository,
            AuthenticatedUserProvider authenticatedUserProvider,
            AppointmentRepository appointmentRepository
    ) {
        this(patientRepository, authenticatedUserProvider, appointmentRepository, null, null);
    }

    @Override
    @Transactional
    public Optional<Appointment> handle(CreateAppointmentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var patient = patientRepository.findByIdAndUser_Id(command.patientId(), userId);
        if (patient.isEmpty()) {
            return Optional.empty();
        }

        assertWithinBusinessHours(userId, command._appointmentDate(), command._duration());
        assertNoScheduleConflict(userId, command._appointmentDate(), command._duration(), null);

        var appointment = new Appointment(command);
        appointment.setPatient(patient.get());

        if (command.treatmentId() != null && treatmentRepository != null) {
            treatmentRepository.findByIdAndUser_Id(command.treatmentId(), userId)
                    .ifPresent(appointment::setTreatment);
        }

        try {
            appointmentRepository.save(appointment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return Optional.of(appointment);
    }

    @Override
    @Transactional
    public Optional<Appointment> handle(UpdateAppointmentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        Appointment appointment = appointmentRepository.findByIdAndPatient_User_Id(command.id(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found or does not belong to the user"));

        assertNotStarted(appointment, "reprogramar");

        // El horario solo se exige si la cita cambia de hora o de duración: editar el motivo de una
        // cita antigua no debe fallar porque el consultorio cambió su horario después.
        boolean rescheduled = !command._appointmentDate().equals(appointment.getAppointmentDate())
                || !command._duration().equals(appointment.getDuration());
        if (rescheduled) {
            assertWithinBusinessHours(userId, command._appointmentDate(), command._duration());
        }
        assertNoScheduleConflict(userId, command._appointmentDate(), command._duration(), appointment.getId());

        appointment.setAppointmentDate(command._appointmentDate());
        appointment.setReason(command._reason());
        appointment.setDuration(command._duration());

        appointmentRepository.save(appointment);

        return Optional.of(appointment);
    }

    @Override
    @Transactional
    public Optional<Appointment> handle(UpdateAppointmentStatusCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        var appointmentOpt = appointmentRepository.findForStatusUpdate(command.id(), userId);
        if (appointmentOpt.isEmpty()) {
            return Optional.empty();
        }

        Appointment appointment = appointmentOpt.get();
        AppointmentStatus newStatus = AppointmentStatus.valueOf(command.status().trim().toUpperCase());
        // US43: una cita atendida no vuelve a agendada ni a ningún otro estado.
        boolean attended = Boolean.TRUE.equals(appointment.getCompleted())
                || appointment.getStatus() == AppointmentStatus.COMPLETED;
        if (attended && newStatus != AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Una cita atendida no puede cambiar de estado.");
        }
        if (newStatus == AppointmentStatus.IN_PROGRESS
                && appointment.getAppointmentDate() != null
                && LocalDateTime.now(com.platform.dentify.shared.domain.model.valueobjects.BusinessTime.ZONE)
                        .isBefore(appointment.getAppointmentDate())) {
            throw new IllegalArgumentException(
                    "La cita no puede pasar a 'En Atención' antes de su hora programada.");
        }
        if (command.recordInMedicalHistory() && newStatus != AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Medical history can only be recorded for completed appointments");
        }
        if (command.recordInMedicalHistory() && appointment.getTreatment() == null) {
            throw new IllegalArgumentException("Link a treatment before recording the appointment in medical history");
        }
        appointment.updateStatus(newStatus, command.clinicalNotes());
        if (command.recordInMedicalHistory()
                && !medicalHistoryRepository.existsBySourceAppointmentId(appointment.getId())) {
            var history = new MedicalHistory(new CreateMedicalHistoryCommand(
                    appointment.getTreatment().getName(),
                    appointment.getClinicalNotes() == null ? "" : appointment.getClinicalNotes(), "", "No registrada",
                    appointment.getPatient().getId()));
            history.setPatient(appointment.getPatient());
            history.setSourceAppointmentId(appointment.getId());
            history.setAppointmentDate(appointment.getAppointmentDate());
            medicalHistoryRepository.save(history);
        }
        appointmentRepository.save(appointment);

        return Optional.of(appointment);
    }

    @Override
    @Transactional
    public void handle(DeleteAppointmentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var appointment = appointmentRepository.findByIdAndPatient_User_Id(command.id(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found or does not belong to the user"));
        assertNotStarted(appointment, "eliminar");

        appointmentRepository.deleteById(command.id());
    }

    /** Permite fijar la hora en las pruebas; en producción se usa la hora de Lima. */
    public void useClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * US05 y US07: una cita que ya empezó no se reprograma ni se elimina. Su estado y su nota de
     * evolución sí se pueden actualizar.
     */
    private void assertNotStarted(Appointment appointment, String action) {
        if (appointment.getAppointmentDate() != null
                && !appointment.getAppointmentDate().isAfter(LocalDateTime.now(clock))) {
            throw new PastAppointmentException("No se puede " + action + " una cita que ya pasó.");
        }
    }

    /** Rechaza la cita si cae fuera del horario de atención del consultorio (US09). */
    private void assertWithinBusinessHours(Long userId, LocalDateTime start, LocalTime duration) {
        if (businessHoursService != null) {
            businessHoursService.assertWithinBusinessHours(userId, start, duration);
        }
    }

    /**
     * Verifica que el intervalo solicitado no se solape con otra cita activa del mismo usuario (profesional).
     * Citas canceladas o no asistidas no bloquean el horario.
     *
     * @param excludeAppointmentId cita que se está editando (se ignora a sí misma); null al crear
     */
    private void assertNoScheduleConflict(Long userId, LocalDateTime start, LocalTime duration, Long excludeAppointmentId) {
        LocalDateTime end = start.plus(Appointment.toDuration(duration));
        var sameDay = appointmentRepository
                .findAllByPatient_User_IdAndAppointmentDateBetweenOrderByAppointmentDateAsc(
                        userId, start.toLocalDate().atStartOfDay(), end);

        boolean conflict = sameDay.stream()
                .filter(other -> excludeAppointmentId == null || !excludeAppointmentId.equals(other.getId()))
                .filter(other -> other.resolveStatus() != AppointmentStatus.CANCELLED
                        && other.resolveStatus() != AppointmentStatus.NO_SHOW)
                .anyMatch(other -> other.overlapsWith(start, duration));

        if (conflict) {
            throw new AppointmentConflictException("The selected time overlaps with another appointment");
        }
    }
}
