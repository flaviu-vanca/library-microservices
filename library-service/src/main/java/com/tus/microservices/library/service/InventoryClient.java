package com.tus.microservices.library.service;

import com.tus.microservices.library.dto.InventoryStatusDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;


/**
 * Client for calling the Inventory Service.
 *
 * Uses Resilience4j patterns:
 * - Circuit Breaker: Prevents cascading failures
 * - Retry: Automatically retries failed requests
 *
 * The fallback method provides a graceful degradation when the
 * Inventory Service is unavailable.
 */
@Service
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);
    private static final String INVENTORY_SERVICE_URL = "http://INVENTORY-SERVICE";
    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 3000;

    private final WebClient inventoryWebClient;

    public InventoryClient(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
            .responseTimeout(Duration.ofMillis(READ_TIMEOUT_MS));

        // The first inventory call after startup can take longer than a warm call.
        // Keep the timeout bounded, but high enough to avoid false fallbacks on replicas.
        this.inventoryWebClient = webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    /**
     * Get inventory status for a book by ISBN.
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
    @Retry(name = "inventoryService")
    public InventoryStatusDTO getInventoryStatus(String isbn) {
        log.debug("Calling Inventory Service for ISBN: {}", isbn);

        String bearerToken = extractBearerToken();

        InventoryStatusDTO inventoryStatus = inventoryWebClient
            .get()
            .uri(INVENTORY_SERVICE_URL + "/api/inventory/{isbn}", isbn)
            .headers(headers -> applyBearerToken(headers, bearerToken, isbn))
            .retrieve()
            .bodyToMono(InventoryStatusDTO.class)
            .switchIfEmpty(Mono.error(new IllegalStateException("Inventory Service returned an empty response")))
            .block();

        return inventoryStatus;
    }

    /**
     * Fallback method when Inventory Service is unavailable.
     * Returns a response indicating inventory status is unknown.
     */
    public InventoryStatusDTO getInventoryFallback(String isbn, Throwable throwable) {
        log.warn("Fallback triggered for ISBN: {} due to: {}", isbn, throwable.getMessage());

        // Determine the type of failure for logging
        String failureType = "Unknown";
        if (throwable instanceof WebClientResponseException) {
            failureType = "HTTP " + ((WebClientResponseException) throwable).getStatusCode();
        } else if (throwable instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            failureType = "Circuit Breaker OPEN";
        }

        log.info("Returning fallback inventory status for ISBN: {} (failure type: {})", isbn, failureType);

        // Return unavailable status - caller can handle this gracefully
        return InventoryStatusDTO.unavailable(isbn);
    }

    private String extractBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }

        return null;
    }

    private void applyBearerToken(HttpHeaders headers, String bearerToken, String isbn) {
        if (bearerToken == null || bearerToken.isBlank()) {
            log.warn("No JWT available to forward when calling Inventory Service for ISBN: {}", isbn);
            return;
        }

        headers.setBearerAuth(bearerToken);
    }
}
