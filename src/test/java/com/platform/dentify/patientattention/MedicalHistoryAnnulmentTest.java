package com.platform.dentify.patientattention;

import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.application.internal.MedicalHistoryCommandServiceImpl;
import com.platform.dentify.patientattention.domain.exceptions.MedicalHistoryAlreadyAnnulledException;
import com.platform.dentify.patientattention.domain.model.commands.AnnulMedicalHistoryCommand;
import com.platform.dentify.patientattention.domain.model.commands.CreateMedicalHistoryCommand;
import com.platform.dentify.patientattention.domain.model.entities.MedicalHistory;
import com.platform.dentify.patientattention.infrastructure.repositories.MedicalHistoryRepository;
import com.platform.dentify.patientattention.infrastructure.repositories.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de US18: anular una entrada de la historia clínica con motivo, sin borrarla. */
class MedicalHistoryAnnulmentTest {

    private static final Long USER_ID = 7L;
    private static final Long PATIENT_ID = 3L;
    private static final Long HISTORY_ID = 11L;

    private MedicalHistoryRepository histories;
    private MedicalHistoryCommandServiceImpl service;
    private MedicalHistory entry;

    @BeforeEach
    void setUp() {
        histories = mock(MedicalHistoryRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getCurrentUserId()).thenReturn(USER_ID);
        entry = new MedicalHistory(new CreateMedicalHistoryCommand("Restauración", "Pieza 21", "Caries", "Ninguna", PATIENT_ID));
        when(histories.findByIdAndPatient_IdAndPatient_User_Id(HISTORY_ID, PATIENT_ID, USER_ID)).thenReturn(Optional.of(entry));
        when(histories.save(any(MedicalHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new MedicalHistoryCommandServiceImpl(histories, mock(PatientRepository.class), auth);
    }

    @Test
    void annulsTheEntryWithItsReasonAndKeepsIt() {
        var result = service.handle(new AnnulMedicalHistoryCommand(PATIENT_ID, HISTORY_ID, "  Se registró en otro paciente "));

        assertTrue(result.isPresent());
        assertTrue(entry.isAnnulled());
        assertEquals("Se registró en otro paciente", entry.getAnnulmentReason());
        assertNotNull(entry.getAnnulledAt());
        assertEquals("Pieza 21", entry.getRecord(), "The clinical content is kept");
        verify(histories, never()).delete(any());
    }

    @Test
    void annulmentWithoutReasonIsNotAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new AnnulMedicalHistoryCommand(PATIENT_ID, HISTORY_ID, "   ")));
        assertFalse(entry.isAnnulled());
        verify(histories, never()).save(any());
    }

    @Test
    void anEntryCannotBeAnnulledTwice() {
        service.handle(new AnnulMedicalHistoryCommand(PATIENT_ID, HISTORY_ID, "Error de registro"));

        assertThrows(MedicalHistoryAlreadyAnnulledException.class,
                () -> service.handle(new AnnulMedicalHistoryCommand(PATIENT_ID, HISTORY_ID, "Otra vez")));
        assertEquals("Error de registro", entry.getAnnulmentReason());
    }

    @Test
    void entriesOfOtherPatientsOrOfficesAreNotFound() {
        assertTrue(service.handle(new AnnulMedicalHistoryCommand(PATIENT_ID + 1, HISTORY_ID, "Motivo")).isEmpty());
        assertFalse(entry.isAnnulled());
    }

    @Test
    void entriesCreatedBeforeAnnulmentExistedCountAsValid() {
        assertFalse(new MedicalHistory().isAnnulled());
    }

    @Test
    void reasonLongerThan250CharactersIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.handle(new AnnulMedicalHistoryCommand(PATIENT_ID, HISTORY_ID, "x".repeat(251))));
    }
}
