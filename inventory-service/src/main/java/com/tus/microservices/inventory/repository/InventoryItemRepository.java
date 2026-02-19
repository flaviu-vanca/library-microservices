package com.tus.microservices.inventory.repository;

import com.tus.microservices.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for InventoryItem entity operations.
 */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    /**
     * Find all inventory items for an ISBN (across all branches).
     */
    List<InventoryItem> findByIsbn(String isbn);

    /**
     * Find inventory item by ISBN and branch.
     */
    Optional<InventoryItem> findByIsbnAndBranchId(String isbn, String branchId);

    /**
     * Find all inventory items at a specific branch.
     */
    List<InventoryItem> findByBranchId(String branchId);

    /**
     * Check if inventory exists for an ISBN.
     */
    boolean existsByIsbn(String isbn);

    /**
     * Count branches that have this ISBN.
     */
    @Query("SELECT COUNT(DISTINCT i.branchId) FROM InventoryItem i WHERE i.isbn = :isbn")
    int countBranchesWithIsbn(@Param("isbn") String isbn);

    /**
     * Get total copies across all branches for an ISBN.
     */
    @Query("SELECT COALESCE(SUM(i.totalCopies), 0) FROM InventoryItem i WHERE i.isbn = :isbn")
    int sumTotalCopiesByIsbn(@Param("isbn") String isbn);

    /**
     * Get available copies across all branches for an ISBN.
     */
    @Query("SELECT COALESCE(SUM(i.availableCopies), 0) FROM InventoryItem i WHERE i.isbn = :isbn")
    int sumAvailableCopiesByIsbn(@Param("isbn") String isbn);

    /**
     * Get reserved copies across all branches for an ISBN.
     */
    @Query("SELECT COALESCE(SUM(i.reservedCopies), 0) FROM InventoryItem i WHERE i.isbn = :isbn")
    int sumReservedCopiesByIsbn(@Param("isbn") String isbn);

    /**
     * Find all ISBNs that have available copies.
     */
    @Query("SELECT DISTINCT i.isbn FROM InventoryItem i WHERE i.availableCopies > 0")
    List<String> findAvailableIsbns();
}
