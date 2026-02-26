package com.tus.microservices.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller for circuit breaker responses.
 *
 * When a downstream service is unavailable and the circuit breaker opens,
 * requests are forwarded to these fallback endpoints.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/library")
    public ResponseEntity<Map<String, Object>> libraryFallback() {
        return serviceUnavailable(
            "library-service",
            "Library service is currently unavailable. Please try again later."
        );
    }

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> inventoryFallback() {
        return serviceUnavailable(
            "inventory-service",
            "Inventory service is currently unavailable. Please try again later."
        );
    }

    private ResponseEntity<Map<String, Object>> serviceUnavailable(String service, String message) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "message", message,
                "timestamp", Instant.now().toString(),
                "service", service
            ));
    }
}
