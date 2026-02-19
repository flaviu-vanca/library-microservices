package com.tus.microservices.library.dto;

/**
 * DTO representing inventory status from the Inventory Service.
 * This is the response from GET /api/inventory/{isbn}
 */
public record InventoryStatusDTO(
    String isbn,
    int totalCopies,
    int availableCopies,
    int reservedCopies,
    boolean available,
    int branchCount
) {
    /**
     * Creates an unavailable/unknown status (used for fallback).
     */
    public static InventoryStatusDTO unavailable(String isbn) {
        return new InventoryStatusDTO(isbn, 0, 0, 0, false, 0);
    }
}
