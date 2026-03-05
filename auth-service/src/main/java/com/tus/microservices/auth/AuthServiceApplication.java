package com.tus.microservices.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth Service
 *
 * Provides email-code authentication for USER and ADMIN accounts.
 * Issues JWT tokens compatible with the existing gateway, library, and inventory services.
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
