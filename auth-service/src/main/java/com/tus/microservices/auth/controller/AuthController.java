package com.tus.microservices.auth.controller;

import com.tus.microservices.auth.dto.*;
import com.tus.microservices.auth.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public auth endpoints for member signup and login.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Email/password JWT authentication endpoints")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @GetMapping("/info")
    @Operation(summary = "Get public auth metadata")
    public ResponseEntity<AuthInfoResponse> getAuthInfo() {
        log.info("GET /auth/info");
        return ResponseEntity.ok(authenticationService.getAuthInfo());
    }

    @PostMapping("/signup")
    @Operation(summary = "Create a member account with email and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account created and JWT issued"),
        @ApiResponse(responseCode = "409", description = "Account already exists")
    })
    public ResponseEntity<TokenResponse> signup(
            @Valid @RequestBody PasswordSignupRequest request) {
        log.info("POST /auth/signup for {}", request.email());
        return ResponseEntity.ok(authenticationService.signupWithPassword(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "JWT issued"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody PasswordLoginRequest request) {
        log.info("POST /auth/login for {}", request.email());
        return ResponseEntity.ok(authenticationService.loginWithPassword(request));
    }

    @PostMapping("/renew")
    @Operation(summary = "Renew an expiring JWT once in the last 5 minutes of the session")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "JWT renewed successfully"),
        @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired bearer token"),
        @ApiResponse(responseCode = "409", description = "Token is not eligible for renewal")
    })
    public ResponseEntity<TokenResponse> renew(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        log.info("POST /auth/renew");
        return ResponseEntity.ok(authenticationService.renewToken(authorizationHeader));
    }
}
