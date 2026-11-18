package com.platform.dentify.patientattention.application.internal;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.iam.infrastructure.repositories.UserRepository;
import com.platform.dentify.iam.infrastructure.security.AuthenticatedUserProvider;
import com.platform.dentify.patientattention.domain.model.aggregates.Treatment;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.DeleteTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateTreatmentCommand;
import com.platform.dentify.patientattention.domain.services.TreatmentCommandService;
import com.platform.dentify.patientattention.infrastructure.repositories.TreatmentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TreatmentCommandServiceImpl implements TreatmentCommandService {

    private final TreatmentRepository treatmentRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final UserRepository userRepository;

    public TreatmentCommandServiceImpl(
            TreatmentRepository treatmentRepository,
            AuthenticatedUserProvider authenticatedUserProvider,
            UserRepository userRepository
    ) {
        this.treatmentRepository = treatmentRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Long handle(CreateTreatmentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();

        if (treatmentRepository.existsByNameIgnoreCaseAndUser_Id(command.name().trim(), userId)) {
            throw new IllegalArgumentException("Treatment with name '" + command.name() + "' already exists");
        }

        Treatment treatment = new Treatment(command);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        treatment.setUser(user);
        treatmentRepository.save(treatment);
        return treatment.getId();
    }

    @Override
    public Optional<Treatment> handle(UpdateTreatmentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var treatmentOpt = treatmentRepository.findByIdAndUser_Id(command.id(), userId);

        if (treatmentOpt.isEmpty()) {
            return Optional.empty();
        }

        var treatment = treatmentOpt.get();
        treatment.update(command);
        return Optional.of(treatmentRepository.save(treatment));
    }

    @Override
    @Transactional
    public void handle(DeleteTreatmentCommand command) {
        Long userId = authenticatedUserProvider.getCurrentUserId();
        var treatmentOpt = treatmentRepository.findByIdAndUser_Id(command.id(), userId);

        if (treatmentOpt.isEmpty()) {
            throw new IllegalArgumentException("Treatment not found");
        }

        treatmentRepository.deleteByIdAndUser_Id(command.id(), userId);
    }
}
