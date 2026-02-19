package com.tus.microservices.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Library Service (Service A)
 *
 * Primary business service that manages Libraries and Books.
 * Makes runtime calls to Inventory Service (Service B) to check availability.
 *
 * Features:
 * - CRUD operations for Libraries and Books
 * - Circuit breaker for Inventory Service calls
 * - Distributed tracing
 *
 * Endpoints:
 * - http://localhost:8081/api/libraries
 * - http://localhost:8081/api/books
 * - http://localhost:8081/swagger-ui.html
 */
@SpringBootApplication
public class LibraryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryServiceApplication.class, args);
    }
}
