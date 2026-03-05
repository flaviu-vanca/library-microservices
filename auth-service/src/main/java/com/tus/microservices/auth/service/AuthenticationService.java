package com.tus.microservices.auth.service;

import com.tus.microservices.auth.config.OAuth2LoginProperties;
import com.tus.microservices.auth.dto.AuthInfoResponse;
import com.tus.microservices.auth.dto.PasswordLoginRequest;
import com.tus.microservices.auth.dto.PasswordSignupRequest;
import com.tus.microservices.auth.dto.TokenResponse;
import com.tus.microservices.auth.entity.AuthIdentity;
import com.tus.microservices.auth.entity.AuthProvider;
import com.tus.microservices.auth.entity.AuthRole;
import com.tus.microservices.auth.entity.AuthUser;
import com.tus.microservices.auth.exception.AuthException;
import com.tus.microservices.auth.repository.AuthIdentityRepository;
import com.tus.microservices.auth.repository.AuthUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates password-based and OAuth2-based member authentication.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AuthUserRepository authUserRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final OAuth2LoginProperties oAuth2LoginProperties;

    public AuthenticationService(
            AuthUserRepository authUserRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            OAuth2LoginProperties oAuth2LoginProperties) {
        this.authUserRepository = authUserRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.oAuth2LoginProperties = oAuth2LoginProperties;
    }

    @Transactional
    public TokenResponse signupWithPassword(PasswordSignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (authUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AuthException(HttpStatus.CONFLICT, "Account already exists. Use the login flow instead.");
        }

        AuthUser user = new AuthUser();
        user.setEmail(normalizedEmail);
        user.setFullName(resolveFullName(request.fullName(), normalizedEmail));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(AuthRole.USER);
        user.setEmailVerified(true);
        user.setActive(true);

        authUserRepository.save(user);

        log.info("Completed password signup for {}", normalizedEmail);
        return jwtTokenService.issueToken(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse loginWithPassword(PasswordLoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        AuthUser user = requireVerifiedActiveUser(normalizedEmail);

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Password login is not configured for this account.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Email or password is invalid.");
        }

        log.info("Completed password login for {}", normalizedEmail);
        return jwtTokenService.issueToken(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse renewToken(String authorizationHeader) {
        String rawToken = extractBearerToken(authorizationHeader);
        JwtTokenService.ParsedToken parsedToken = jwtTokenService.parseToken(rawToken);
        jwtTokenService.validateRenewal(parsedToken);

        AuthUser user = requireVerifiedActiveUser(normalizeEmail(parsedToken.email()));
        log.info("Renewed token for {}", user.getEmail());
        return jwtTokenService.issueRenewedToken(user);
    }

    @Transactional
    public TokenResponse loginWithOAuth2(String registrationId, Map<String, Object> attributes) {
        AuthProvider provider = resolveProvider(registrationId);
        OAuthLoginProfile profile = toOAuthLoginProfile(provider, attributes);

        AuthIdentity identity = authIdentityRepository
            .findByProviderAndProviderUserId(provider, profile.providerUserId())
            .orElse(null);

        AuthUser user;
        boolean userUpdated = false;

        if (identity != null) {
            user = identity.getUser();
            validateActiveUser(user);
        } else {
            if (!profile.emailVerified()) {
                throw new AuthException(HttpStatus.CONFLICT, "This provider account does not expose a verified email address.");
            }

            user = authUserRepository.findByEmailIgnoreCase(profile.email())
                .orElseGet(() -> createOAuthUser(profile));

            validateActiveUser(user);

            AuthIdentity newIdentity = new AuthIdentity();
            newIdentity.setUser(user);
            newIdentity.setProvider(provider);
            newIdentity.setProviderUserId(profile.providerUserId());
            authIdentityRepository.save(newIdentity);
        }

        if (!user.isEmailVerified() && profile.emailVerified()) {
            user.setEmailVerified(true);
            userUpdated = true;
        }

        if (!StringUtils.hasText(user.getFullName()) || user.getFullName().equalsIgnoreCase(user.getEmail())) {
            user.setFullName(profile.fullName());
            userUpdated = true;
        }

        if (userUpdated) {
            authUserRepository.save(user);
        }

        log.info("Completed {} login for {}", provider.registrationId(), user.getEmail());
        return jwtTokenService.issueToken(user);
    }

    public AuthInfoResponse getAuthInfo() {
        List<String> enabledProviders = oAuth2LoginProperties.enabledProviderIds();
        boolean oauthReady = !enabledProviders.isEmpty();

        return new AuthInfoResponse(
            AuthRole.USER.name(),
            oauthReady ? "email_password_jwt_oauth2" : "email_password_jwt",
            oauthReady,
            enabledProviders,
            buildAuthNote(enabledProviders)
        );
    }

    private AuthUser requireVerifiedActiveUser(String email) {
        AuthUser user = authUserRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "No account exists for this email."));

        validateEmailVerified(user);
        validateActiveUser(user);
        return user;
    }

    private void validateEmailVerified(AuthUser user) {
        if (!user.isEmailVerified()) {
            throw new AuthException(HttpStatus.CONFLICT, "Email has not been verified yet.");
        }
    }

    private void validateActiveUser(AuthUser user) {
        if (!user.isActive()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "This account is inactive.");
        }
    }

    private AuthProvider resolveProvider(String registrationId) {
        try {
            return AuthProvider.fromRegistrationId(registrationId);
        } catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unsupported OAuth2 provider: " + registrationId);
        }
    }

    private AuthUser createOAuthUser(OAuthLoginProfile profile) {
        AuthUser user = new AuthUser();
        user.setEmail(profile.email());
        user.setFullName(profile.fullName());
        user.setPasswordHash(null);
        user.setRole(AuthRole.USER);
        user.setEmailVerified(profile.emailVerified());
        user.setActive(true);
        return authUserRepository.save(user);
    }

    private OAuthLoginProfile toOAuthLoginProfile(AuthProvider provider, Map<String, Object> attributes) {
        String providerUserId = switch (provider) {
            case GOOGLE -> firstNonBlank(attributeAsString(attributes, "sub"), attributeAsString(attributes, "id"));
            case GITHUB, FACEBOOK -> attributeAsString(attributes, "id");
        };

        if (!StringUtils.hasText(providerUserId)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Provider account did not return a stable identifier.");
        }

        String email = normalizeEmail(attributeAsString(attributes, "email"));
        if (!StringUtils.hasText(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "Provider account did not return an email address for this login.");
        }

        String fullName = resolveFullName(firstNonBlank(
            attributeAsString(attributes, "name"),
            attributeAsString(attributes, "login"),
            email
        ), email);

        return new OAuthLoginProfile(
            provider,
            providerUserId,
            email,
            fullName,
            resolveEmailVerified(provider, attributes)
        );
    }

    private boolean resolveEmailVerified(AuthProvider provider, Map<String, Object> attributes) {
        if (provider == AuthProvider.FACEBOOK) {
            return true;
        }

        Object value = attributes.get("email_verified");
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return provider == AuthProvider.GITHUB && StringUtils.hasText(attributeAsString(attributes, "email"));
    }

    private String attributeAsString(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String buildAuthNote(List<String> enabledProviders) {
        if (enabledProviders.isEmpty()) {
            return "Members authenticate with email and password. Social OAuth can be enabled once provider keys are configured.";
        }

        String providers = enabledProviders.stream()
            .map(this::providerLabel)
            .reduce((left, right) -> left + ", " + right)
            .orElse("OAuth providers");

        return "Members can authenticate with email/password or " + providers + ". Social login still ends with the same JWT member session.";
    }

    private String providerLabel(String providerId) {
        return switch (providerId) {
            case "google" -> "Google";
            case "github" -> "GitHub";
            case "facebook" -> "Facebook";
            default -> providerId;
        };
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String resolveFullName(String fullName, String fallbackEmail) {
        String normalized = fullName == null ? "" : fullName.trim();
        return StringUtils.hasText(normalized) ? normalized : fallbackEmail;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Authorization header is required.");
        }

        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Bearer token is required.");
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Bearer token is required.");
        }

        return token;
    }

    private record OAuthLoginProfile(
        AuthProvider provider,
        String providerUserId,
        String email,
        String fullName,
        boolean emailVerified
    ) {
    }
}
