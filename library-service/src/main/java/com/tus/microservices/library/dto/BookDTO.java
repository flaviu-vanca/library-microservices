package com.tus.microservices.library.dto;

import java.time.LocalDateTime;

/**
 * DTO for Book responses.
 */
public record BookDTO(
    Long id,
    String isbn,
    String title,
    String author,
    Integer publicationYear,
    String genre,
    Long libraryId,
    String libraryName,
    LocalDateTime createdAt
) {}
