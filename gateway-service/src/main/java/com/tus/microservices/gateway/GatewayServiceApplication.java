package com.tus.microservices.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Service - Spring Cloud Gateway
 *
 * Single entry point for all external API requests.
 * Routes requests to appropriate microservices using service discovery.
 *
 * Start this service LAST (after all business services).
 *
 * Endpoint: http://localhost:8080
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
