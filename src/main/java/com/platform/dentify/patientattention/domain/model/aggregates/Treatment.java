package com.platform.dentify.patientattention.domain.model.aggregates;

import com.platform.dentify.iam.domain.model.aggregates.User;
import com.platform.dentify.patientattention.domain.model.commands.CreateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.UpdateTreatmentCommand;
import com.platform.dentify.patientattention.domain.model.valueobjects.TreatmentCategory;
import com.platform.dentify.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Treatment extends AuditableAbstractAggregateRoot<Treatment> {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @Size(max = 350)
    @Column(length = 350)
    private String description;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false, length = 40)
    private TreatmentCategory category;

    @Min(0)
    @Column(nullable = false)
    private Double price;

    @Min(5)
    @Column(nullable = false)
    private Integer estimatedDurationMinutes;

    @Column(nullable = false)
    private boolean isActive;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Treatment() {}

    public Treatment(CreateTreatmentCommand command) {
        this.name = command.name().trim();
        this.description = command.description();
        this.category = TreatmentCategory.valueOf(command.category().trim().toUpperCase());
        this.price = command.price();
        this.estimatedDurationMinutes = command.estimatedDurationMinutes();
        this.isActive = true;
    }

    public void update(UpdateTreatmentCommand command) {
        this.name = command.name().trim();
        this.description = command.description();
        this.category = TreatmentCategory.valueOf(command.category().trim().toUpperCase());
        this.price = command.price();
        this.estimatedDurationMinutes = command.estimatedDurationMinutes();
        this.isActive = Boolean.TRUE.equals(command.isActive());
    }
}
