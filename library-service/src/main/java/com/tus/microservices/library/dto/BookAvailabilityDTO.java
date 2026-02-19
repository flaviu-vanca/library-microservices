package com.tus.microservices.library.dto;

/**
 * DTO combining book information with inventory status.
 * This is the response for GET /api/books/{id}/availability
 */
public record BookAvailabilityDTO(
    Long id,
    String isbn,
    String title,
    String author,
    Integer publicationYear,
    String genre,
    Long libraryId,
    String libraryName,
    InventoryStatusDTO inventory
) {}
