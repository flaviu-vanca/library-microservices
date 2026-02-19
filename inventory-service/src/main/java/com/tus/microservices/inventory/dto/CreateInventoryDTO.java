package com.tus.microservices.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a new inventory item.
 */
public record CreateInventoryDTO(
    @NotBlank(message = "ISBN is required")
    @Size(max = 20, message = "ISBN cannot exceed 20 characters")
    String isbn,

    @NotBlank(message = "Branch ID is required")
    @Size(max = 50, message = "Branch ID cannot exceed 50 characters")
    String branchId,

    @Size(max = 100, message = "Branch name cannot exceed 100 characters")
    String branchName,

    @Min(value = 0, message = "Total copies cannot be negative")
    int totalCopies,

    @Min(value = 0, message = "Available copies cannot be negative")
    int availableCopies
) {}
