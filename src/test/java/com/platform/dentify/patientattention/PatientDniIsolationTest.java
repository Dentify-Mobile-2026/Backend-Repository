package com.platform.dentify.patientattention;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.iam.infrastructure.repositories.UserRepository;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.application.internal.PatientCommandServiceImpl;
import com.platform.dentify.patientattention.domain.model.aggregates.Patient;
import com.platform.dentify.patientattention.domain.model.commands.CreatePatientCommand;
import com.platform.dentify.patientattention.infrastructure.repositories.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatientDniIsolationTest {

    @Test
    void rejectsDuplicateDniWithinSameUserAndAllowsAcrossDifferentUsers() {
        PatientRepository patientRepository = mock(PatientRepository.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        UserRepository userRepository = mock(UserRepository.class);
        var service = new PatientCommandServiceImpl(patientRepository, auth, userRepository);

        var cmd = new CreatePatientCommand(
                "74589632", "Ana", "Lopez", "ana@test.com", LocalDate.of(1995, 5, 10),
                "Av. Lima 123", "999888777", "", "", "", "");

        when(auth.getCurrentUserId()).thenReturn(1L);
        when(patientRepository.existsByDni_DniAndUser_Id("74589632", 1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.handle(cmd));

        when(auth.getCurrentUserId()).thenReturn(2L);
        when(patientRepository.existsByDni_DniAndUser_Id("74589632", 2L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(new User()));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 42L);
            return p;
        });

        Long id = service.handle(cmd);
        assertEquals(42L, id);
    }
}
