package com.platform.dentify.invoices.domain.model.commands;

public record CreatePaymentMethodCommand(String name, String description) {
    public CreatePaymentMethodCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description cannot be empty");
        }
    }
}
