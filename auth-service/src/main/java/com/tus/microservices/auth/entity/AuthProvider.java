package com.tus.microservices.auth.entity;

/**
 * Supported external identity providers for OAuth2 login.
 */
public enum AuthProvider {
    GOOGLE("google"),
    GITHUB("github"),
    FACEBOOK("facebook");

    private final String registrationId;

    AuthProvider(String registrationId) {
        this.registrationId = registrationId;
    }

    public String registrationId() {
        return registrationId;
    }

    public static AuthProvider fromRegistrationId(String registrationId) {
        for (AuthProvider provider : values()) {
            if (provider.registrationId.equalsIgnoreCase(registrationId)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
    }
}
