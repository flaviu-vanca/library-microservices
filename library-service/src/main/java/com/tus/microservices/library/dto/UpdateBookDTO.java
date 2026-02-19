package com.tus.microservices.library.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for updating a Book. All fields are optional.
 */
public record UpdateBookDTO(
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    String title,

    @Size(max = 255, message = "Author cannot exceed 255 characters")
    String author,

    Integer publicationYear,

    @Size(max = 50, message = "Genre cannot exceed 50 characters")
    String genre,

    Long libraryId
) {}
