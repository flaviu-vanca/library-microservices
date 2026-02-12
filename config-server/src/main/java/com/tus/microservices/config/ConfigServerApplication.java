package com.tus.microservices.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server - Spring Cloud Config Server
 *
 * Provides centralized configuration for all microservices.
 * Configuration files are stored in the config-repo directory.
 *
 * Start this service SECOND (after Discovery Server).
 *
 * Endpoints:
 * - http://localhost:8888/{service}/{profile}
 * - http://localhost:8888/{service}-{profile}.yml
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
