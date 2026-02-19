package com.tus.microservices.library.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating a Library. All fields are optional.
 */
public record UpdateLibraryDTO(
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    String name,

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    String address,

    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    String country
) {}
