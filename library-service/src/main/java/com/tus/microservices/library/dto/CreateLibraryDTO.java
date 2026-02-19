package com.tus.microservices.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new Library.
 */
public record CreateLibraryDTO(
    @NotBlank(message = "Library name is required")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    String name,

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address cannot exceed 255 characters")
    String address,

    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    String country
) {}
