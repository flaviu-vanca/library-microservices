package com.tus.microservices.inventory.dto;

/**
 * DTO for aggregated inventory status across all branches.
 * This is the primary response returned to the Library Service.
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
     * Create a status indicating no inventory found.
     */
    public static InventoryStatusDTO notFound(String isbn) {
        return new InventoryStatusDTO(isbn, 0, 0, 0, false, 0);
    }
}
