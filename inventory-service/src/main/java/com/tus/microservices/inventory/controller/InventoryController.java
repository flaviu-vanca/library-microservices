package com.tus.microservices.inventory.controller;

import com.tus.microservices.inventory.dto.*;
import com.tus.microservices.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Inventory operations.
 * The GET /api/inventory/{isbn} endpoint is called by Library Service.
 */
@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Book inventory management endpoints")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{isbn}")
    @Operation(
        summary = "Get inventory status by ISBN",
        description = "Returns aggregated inventory status across all branches. " +
                      "This is the primary endpoint called by Library Service."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inventory status returned (may show 0 copies if not found)")
    })
    public ResponseEntity<InventoryStatusDTO> getInventoryStatus(
            @Parameter(description = "Book ISBN") @PathVariable String isbn) {
        log.info("GET /api/inventory/{}", isbn);
        InventoryStatusDTO status = inventoryService.getInventoryStatus(isbn);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{isbn}/branches")
    @Operation(
        summary = "Get per-branch inventory",
        description = "Returns inventory breakdown by branch for an ISBN"
    )
    public ResponseEntity<List<BranchInventoryDTO>> getBranchInventory(
            @Parameter(description = "Book ISBN") @PathVariable String isbn) {
        log.info("GET /api/inventory/{}/branches", isbn);
        List<BranchInventoryDTO> branches = inventoryService.getBranchInventory(isbn);
        return ResponseEntity.ok(branches);
    }

    @GetMapping("/branch/{branchId}")
    @Operation(
        summary = "Get all inventory at a branch",
        description = "Returns all inventory items at a specific branch"
    )
    public ResponseEntity<List<InventoryItemDTO>> getInventoryByBranch(
            @Parameter(description = "Branch ID") @PathVariable String branchId) {
        log.info("GET /api/inventory/branch/{}", branchId);
        List<InventoryItemDTO> items = inventoryService.getInventoryByBranch(branchId);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/item/{id}")
    @Operation(summary = "Get inventory item by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved inventory item"),
        @ApiResponse(responseCode = "404", description = "Inventory item not found")
    })
    public ResponseEntity<InventoryItemDTO> getInventoryById(
            @Parameter(description = "Inventory item ID") @PathVariable Long id) {
        log.info("GET /api/inventory/item/{}", id);
        InventoryItemDTO item = inventoryService.getInventoryById(id);
        return ResponseEntity.ok(item);
    }

    @PostMapping
    @Operation(summary = "Create inventory item", description = "Creates a new inventory record for an ISBN at a branch")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Inventory item created"),
        @ApiResponse(responseCode = "400", description = "Invalid input or duplicate record")
    })
    public ResponseEntity<InventoryItemDTO> createInventory(
            @Valid @RequestBody CreateInventoryDTO dto) {
        log.info("POST /api/inventory - Creating inventory for ISBN: {} at branch: {}", dto.isbn(), dto.branchId());
        InventoryItemDTO created = inventoryService.createInventory(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/item/{id}")
    @Operation(summary = "Update inventory item")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inventory item updated"),
        @ApiResponse(responseCode = "404", description = "Inventory item not found")
    })
    public ResponseEntity<InventoryItemDTO> updateInventory(
            @Parameter(description = "Inventory item ID") @PathVariable Long id,
            @Valid @RequestBody UpdateInventoryDTO dto) {
        log.info("PUT /api/inventory/item/{}", id);
        InventoryItemDTO updated = inventoryService.updateInventory(id, dto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{isbn}/reserve")
    @Operation(
        summary = "Reserve a copy",
        description = "Reserves one copy of a book at a specific branch"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Copy reserved successfully"),
        @ApiResponse(responseCode = "404", description = "Inventory not found"),
        @ApiResponse(responseCode = "409", description = "No copies available")
    })
    public ResponseEntity<InventoryStatusDTO> reserveCopy(
            @Parameter(description = "Book ISBN") @PathVariable String isbn,
            @Valid @RequestBody ReservationRequestDTO request) {
        log.info("POST /api/inventory/{}/reserve at branch: {}", isbn, request.branchId());
        InventoryStatusDTO status = inventoryService.reserveCopy(isbn, request.branchId());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/{isbn}/return")
    @Operation(
        summary = "Return a copy",
        description = "Returns one copy of a book at a specific branch"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Copy returned successfully"),
        @ApiResponse(responseCode = "404", description = "Inventory not found")
    })
    public ResponseEntity<InventoryStatusDTO> returnCopy(
            @Parameter(description = "Book ISBN") @PathVariable String isbn,
            @Valid @RequestBody ReservationRequestDTO request) {
        log.info("POST /api/inventory/{}/return at branch: {}", isbn, request.branchId());
        InventoryStatusDTO status = inventoryService.returnCopy(isbn, request.branchId());
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/item/{id}")
    @Operation(summary = "Delete inventory item")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Inventory item deleted"),
        @ApiResponse(responseCode = "404", description = "Inventory item not found")
    })
    public ResponseEntity<Void> deleteInventory(
            @Parameter(description = "Inventory item ID") @PathVariable Long id) {
        log.info("DELETE /api/inventory/item/{}", id);
        inventoryService.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }
}
