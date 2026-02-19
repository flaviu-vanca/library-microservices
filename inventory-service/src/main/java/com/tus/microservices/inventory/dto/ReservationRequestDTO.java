package com.tus.microservices.inventory.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for reserve/return operations.
 */
public record ReservationRequestDTO(
    @NotBlank(message = "Branch ID is required")
    String branchId
) {}
