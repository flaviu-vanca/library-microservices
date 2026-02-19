package com.tus.microservices.library.dto;

import java.time.LocalDateTime;

/**
 * DTO for Library responses.
 */
public record LibraryDTO(
    Long id,
    String name,
    String address,
    String city,
    String country,
    int bookCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
