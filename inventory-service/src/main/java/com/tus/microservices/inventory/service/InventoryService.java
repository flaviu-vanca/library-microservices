package com.tus.microservices.inventory.service;

import com.tus.microservices.inventory.dto.*;
import com.tus.microservices.inventory.entity.InventoryItem;
import com.tus.microservices.inventory.exception.InsufficientInventoryException;
import com.tus.microservices.inventory.exception.ResourceNotFoundException;
import com.tus.microservices.inventory.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service class for Inventory operations.
 * Provides aggregated inventory status across all branches.
 */
@Service
@Transactional
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository repository;

    public InventoryService(InventoryItemRepository repository) {
        this.repository = repository;
    }

    /**
     * Get aggregated inventory status for an ISBN across all branches.
     * This is the primary endpoint called by Library Service.
     */
    @Transactional(readOnly = true)
    public InventoryStatusDTO getInventoryStatus(String isbn) {
        log.debug("Getting inventory status for ISBN: {}", isbn);

        List<InventoryItem> items = repository.findByIsbn(isbn);

        if (items.isEmpty()) {
            log.debug("No inventory found for ISBN: {}", isbn);
            return InventoryStatusDTO.notFound(isbn);
        }

        int totalCopies = items.stream().mapToInt(InventoryItem::getTotalCopies).sum();
        int availableCopies = items.stream().mapToInt(InventoryItem::getAvailableCopies).sum();
        int reservedCopies = items.stream().mapToInt(InventoryItem::getReservedCopies).sum();
        int branchCount = items.size();

        return new InventoryStatusDTO(
            isbn,
            totalCopies,
            availableCopies,
            reservedCopies,
            availableCopies > 0,
            branchCount
        );
    }

    /**
     * Get per-branch inventory breakdown for an ISBN.
     */
    @Transactional(readOnly = true)
    public List<BranchInventoryDTO> getBranchInventory(String isbn) {
        log.debug("Getting branch inventory for ISBN: {}", isbn);

        List<InventoryItem> items = repository.findByIsbn(isbn);

        return items.stream()
            .map(item -> new BranchInventoryDTO(
                item.getBranchId(),
                item.getBranchName(),
                item.getTotalCopies(),
                item.getAvailableCopies(),
                item.getReservedCopies(),
                item.isAvailable()
            ))
            .toList();
    }

    /**
     * Get all inventory items at a specific branch.
     */
    @Transactional(readOnly = true)
    public List<InventoryItemDTO> getInventoryByBranch(String branchId) {
        log.debug("Getting inventory for branch: {}", branchId);

        return repository.findByBranchId(branchId).stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Get inventory item by ID.
     */
    @Transactional(readOnly = true)
    public InventoryItemDTO getInventoryById(Long id) {
        log.debug("Getting inventory item with id: {}", id);
        return toDTO(findInventoryItemById(id));
    }

    /**
     * Create a new inventory item.
     */
    public InventoryItemDTO createInventory(CreateInventoryDTO dto) {
        log.debug("Creating inventory for ISBN: {} at branch: {}", dto.isbn(), dto.branchId());

        // Check for existing record
        if (repository.findByIsbnAndBranchId(dto.isbn(), dto.branchId()).isPresent()) {
            throw new IllegalArgumentException(
                String.format("Inventory already exists for ISBN '%s' at branch '%s'", dto.isbn(), dto.branchId())
            );
        }

        InventoryItem item = new InventoryItem(
            dto.isbn(),
            dto.branchId(),
            dto.branchName(),
            dto.totalCopies(),
            dto.availableCopies(),
            0  // reserved starts at 0
        );

        InventoryItem saved = repository.save(item);
        log.info("Created inventory item with id: {}", saved.getId());

        return toDTO(saved);
    }

    /**
     * Update an inventory item.
     */
    public InventoryItemDTO updateInventory(Long id, UpdateInventoryDTO dto) {
        log.debug("Updating inventory item with id: {}", id);

        InventoryItem item = findInventoryItemById(id);

        if (dto.totalCopies() != null) {
            item.setTotalCopies(dto.totalCopies());
        }
        if (dto.availableCopies() != null) {
            item.setAvailableCopies(dto.availableCopies());
        }
        if (dto.reservedCopies() != null) {
            item.setReservedCopies(dto.reservedCopies());
        }
        if (dto.branchName() != null) {
            item.setBranchName(dto.branchName());
        }

        InventoryItem updated = repository.save(item);
        log.info("Updated inventory item with id: {}", id);

        return toDTO(updated);
    }

    /**
     * Reserve a copy of a book at a specific branch.
     */
    public InventoryStatusDTO reserveCopy(String isbn, String branchId) {
        log.debug("Reserving copy of ISBN: {} at branch: {}", isbn, branchId);

        InventoryItem item = findInventoryItem(isbn, branchId);

        if (!item.canReserve()) {
            throw new InsufficientInventoryException(isbn, branchId);
        }

        item.reserve();
        repository.save(item);

        log.info("Reserved copy of ISBN: {} at branch: {}", isbn, branchId);

        // Return updated status
        return getInventoryStatus(isbn);
    }

    /**
     * Return a copy of a book at a specific branch.
     */
    public InventoryStatusDTO returnCopy(String isbn, String branchId) {
        log.debug("Returning copy of ISBN: {} at branch: {}", isbn, branchId);

        InventoryItem item = findInventoryItem(isbn, branchId);

        item.returnCopy();
        repository.save(item);

        log.info("Returned copy of ISBN: {} at branch: {}", isbn, branchId);

        // Return updated status
        return getInventoryStatus(isbn);
    }

    /**
     * Delete an inventory item.
     */
    public void deleteInventory(Long id) {
        log.debug("Deleting inventory item with id: {}", id);

        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("InventoryItem", "id", id);
        }

        repository.deleteById(id);
        log.info("Deleted inventory item with id: {}", id);
    }

    /**
     * Convert entity to DTO.
     */
    private InventoryItemDTO toDTO(InventoryItem item) {
        return new InventoryItemDTO(
            item.getId(),
            item.getIsbn(),
            item.getBranchId(),
            item.getBranchName(),
            item.getTotalCopies(),
            item.getAvailableCopies(),
            item.getReservedCopies(),
            item.isAvailable(),
            item.getLastUpdated()
        );
    }

    private InventoryItem findInventoryItemById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", "id", id));
    }

    private InventoryItem findInventoryItem(String isbn, String branchId) {
        return repository.findByIsbnAndBranchId(isbn, branchId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "InventoryItem", "ISBN/branch", isbn + "/" + branchId));
    }
}
