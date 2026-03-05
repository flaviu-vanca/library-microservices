package com.tus.microservices.auth.repository;

import com.tus.microservices.auth.entity.AuthIdentity;
import com.tus.microservices.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for external OAuth2 identity links.
 */
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
