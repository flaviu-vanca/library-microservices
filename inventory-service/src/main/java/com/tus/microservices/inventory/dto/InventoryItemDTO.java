package com.tus.microservices.inventory.dto;

import java.time.LocalDateTime;

/**
 * DTO for full inventory item details.
 */
public record InventoryItemDTO(
    Long id,
    String isbn,
    String branchId,
    String branchName,
    int totalCopies,
    int availableCopies,
    int reservedCopies,
    boolean available,
    LocalDateTime lastUpdated
) {}
