package com.tus.microservices.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new Book.
 */
public record CreateBookDTO(
    @NotBlank(message = "ISBN is required")
    @Size(max = 20, message = "ISBN cannot exceed 20 characters")
    String isbn,

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    String title,

    @Size(max = 255, message = "Author cannot exceed 255 characters")
    String author,

    Integer publicationYear,

    @Size(max = 50, message = "Genre cannot exceed 50 characters")
    String genre,

    Long libraryId
) {}
