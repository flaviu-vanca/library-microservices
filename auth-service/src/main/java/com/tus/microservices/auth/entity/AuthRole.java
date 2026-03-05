package com.tus.microservices.auth.entity;

import java.util.List;

/**
 * Supported application roles.
 */
public enum AuthRole {
    USER,
    ADMIN;

    public List<String> toJwtRoles() {
        return this == ADMIN
            ? List.of("ROLE_USER", "ROLE_ADMIN")
            : List.of("ROLE_USER");
    }
}
