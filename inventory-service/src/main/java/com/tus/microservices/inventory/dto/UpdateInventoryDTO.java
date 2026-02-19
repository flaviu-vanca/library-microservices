package com.tus.microservices.inventory.dto;

import jakarta.validation.constraints.Min;

/**
 * DTO for updating an inventory item.
 */
public record UpdateInventoryDTO(
    @Min(value = 0, message = "Total copies cannot be negative")
    Integer totalCopies,

    @Min(value = 0, message = "Available copies cannot be negative")
    Integer availableCopies,

    @Min(value = 0, message = "Reserved copies cannot be negative")
    Integer reservedCopies,

    String branchName
) {}
