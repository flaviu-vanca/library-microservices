package com.tus.microservices.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Lightweight public metadata for the active auth strategy.
 */
public record AuthInfoResponse(
    @JsonProperty("registration_role")
    String registrationRole,
    @JsonProperty("authentication_mode")
    String authenticationMode,
    @JsonProperty("oauth_ready")
    boolean oauthReady,
    @JsonProperty("oauth_providers")
    List<String> oauthProviders,
    String note
) {
}
