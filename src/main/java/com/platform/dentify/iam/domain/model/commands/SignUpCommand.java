package com.platform.dentify.iam.domain.model.commands;

//registrar
public record SignUpCommand(String firstName, String lastName, String email, String companyName, String username, String password, Boolean trial) {
    public SignUpCommand {
        if (firstName == null || firstName.length() < 3) {
            throw new IllegalArgumentException("First name must be at least 3 characters");
        }
        if (lastName == null || lastName.length() < 3) {
            throw new IllegalArgumentException("Last name must be at least 3 characters");
        }
        if (email == null || email.length() < 3) {
            throw new IllegalArgumentException("Email must be at least 3 characters");
        }
        if (companyName == null || companyName.length() < 3) {
            throw new IllegalArgumentException("Company name must be at least 3 characters");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (trial == null) {
            throw new IllegalArgumentException("Trial cannot be null");
        }
    }
}
