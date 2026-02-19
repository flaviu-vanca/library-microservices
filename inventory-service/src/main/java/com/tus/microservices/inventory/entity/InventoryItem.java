package com.tus.microservices.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * InventoryItem entity representing book stock at a specific library branch.
 * Each ISBN can have multiple inventory records (one per branch).
 */
@Entity
@Table(name = "inventory_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"isbn", "branch_id"}))
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "ISBN is required")
    @Size(max = 20)
    @Column(nullable = false)
    private String isbn;

    @NotBlank(message = "Branch ID is required")
    @Size(max = 50)
    @Column(name = "branch_id", nullable = false)
    private String branchId;

    @Size(max = 100)
    @Column(name = "branch_name")
    private String branchName;

    @Min(value = 0, message = "Total copies cannot be negative")
    @Column(name = "total_copies", nullable = false)
    private Integer totalCopies = 0;

    @Min(value = 0, message = "Available copies cannot be negative")
    @Column(name = "available_copies", nullable = false)
    private Integer availableCopies = 0;

    @Min(value = 0, message = "Reserved copies cannot be negative")
    @Column(name = "reserved_copies", nullable = false)
    private Integer reservedCopies = 0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    // Lifecycle callbacks
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }

    // Constructors
    public InventoryItem() {}

    public InventoryItem(String isbn, String branchId, String branchName,
                         Integer totalCopies, Integer availableCopies, Integer reservedCopies) {
        this.isbn = isbn;
        this.branchId = branchId;
        this.branchName = branchName;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
        this.reservedCopies = reservedCopies;
    }

    // Business logic methods
    public boolean isAvailable() {
        return availableCopies > 0;
    }

    public boolean canReserve() {
        return availableCopies > 0;
    }

    public void reserve() {
        if (!canReserve()) {
            throw new IllegalStateException("No copies available to reserve");
        }
        this.availableCopies--;
        this.reservedCopies++;
    }

    public void returnCopy() {
        if (reservedCopies > 0) {
            this.reservedCopies--;
        }
        this.availableCopies++;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public Integer getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(Integer totalCopies) {
        this.totalCopies = totalCopies;
    }

    public Integer getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(Integer availableCopies) {
        this.availableCopies = availableCopies;
    }

    public Integer getReservedCopies() {
        return reservedCopies;
    }

    public void setReservedCopies(Integer reservedCopies) {
        this.reservedCopies = reservedCopies;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
}
