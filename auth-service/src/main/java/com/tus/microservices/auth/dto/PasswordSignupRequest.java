package com.tus.microservices.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create a member account with email and password.
 */
public record PasswordSignupRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must be 255 characters or fewer")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
        message = "Password must contain at least one letter and one number"
    )
    String password
) {
}
