package com.tus.microservices.inventory.dto;

/**
 * DTO for inventory at a specific branch.
 */
public record BranchInventoryDTO(
    String branchId,
    String branchName,
    int totalCopies,
    int availableCopies,
    int reservedCopies,
    boolean available
) {}
