package com.tus.microservices.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JWT response returned after successful signup or login.
 */
public record TokenResponse(
    @JsonProperty("access_token")
    String accessToken,
    @JsonProperty("token_type")
    String tokenType,
    @JsonProperty("expires_in")
    long expiresIn,
    String username,
    String email,
    @JsonProperty("full_name")
    String fullName,
    String role,
    List<String> roles
) {
}
