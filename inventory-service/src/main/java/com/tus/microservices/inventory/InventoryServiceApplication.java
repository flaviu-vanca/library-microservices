package com.tus.microservices.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Inventory Service (Service B)
 *
 * Manages book inventory and stock levels across library branches.
 * Consumed by Library Service (Service A) via WebClient.
 *
 * Features:
 * - Inventory tracking per ISBN per branch
 * - Stock availability queries
 * - Reserve/return operations
 *
 * Endpoints:
 * - http://localhost:8083/api/inventory
 * - http://localhost:8083/swagger-ui.html
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
