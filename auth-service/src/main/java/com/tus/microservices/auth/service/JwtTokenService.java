package com.tus.microservices.auth.service;

import com.tus.microservices.auth.dto.TokenResponse;
import com.tus.microservices.auth.entity.AuthRole;
import com.tus.microservices.auth.entity.AuthUser;
import com.tus.microservices.auth.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues JWTs compatible with the rest of the microservices stack.
 */
@Service
public class JwtTokenService {

    private static final String CLAIM_RENEWED = "renewed";
    private static final long RENEWAL_WINDOW_MILLIS = 5 * 60 * 1000L;

    private final SecretKey jwtSigningKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public JwtTokenService(SecretKey jwtSigningKey) {
        this.jwtSigningKey = jwtSigningKey;
    }

    public TokenResponse issueToken(AuthUser user) {
        return issueToken(user.getEmail(), user.getEmail(), user.getFullName(), user.getRole(), false);
    }

    public TokenResponse issueRenewedToken(AuthUser user) {
        return issueToken(user.getEmail(), user.getEmail(), user.getFullName(), user.getRole(), true);
    }

    public ParsedToken parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(jwtSigningKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String username = claims.getSubject();
            String email = claims.get("email", String.class);
            String fullName = claims.get("full_name", String.class);
            String roleName = claims.get("role", String.class);
            Date expiration = claims.getExpiration();

            if (username == null || email == null || fullName == null || roleName == null || expiration == null) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "Token is missing required claims.");
            }

            return new ParsedToken(
                username,
                email,
                fullName,
                AuthRole.valueOf(roleName),
                Boolean.TRUE.equals(claims.get(CLAIM_RENEWED, Boolean.class)),
                expiration.toInstant()
            );
        } catch (ExpiredJwtException ex) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Token has expired. Log in again.");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Token is invalid.");
        }
    }

    public void validateRenewal(ParsedToken token) {
        if (token.renewed()) {
            throw new AuthException(HttpStatus.CONFLICT, "This session has already been extended once.");
        }

        long millisRemaining = token.expiresAt().toEpochMilli() - Instant.now().toEpochMilli();
        if (millisRemaining > RENEWAL_WINDOW_MILLIS) {
            throw new AuthException(HttpStatus.CONFLICT, "Token can only be renewed in the last 5 minutes of the session.");
        }
    }

    private TokenResponse issueToken(String username, String email, String fullName, AuthRole role, boolean renewed) {
        List<String> roles = role.toJwtRoles();
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtExpiration);

        String token = Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .claim("email", email)
            .claim("full_name", fullName)
            .claim("role", role.name())
            .claim(CLAIM_RENEWED, renewed)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(jwtSigningKey, Jwts.SIG.HS256)
            .compact();

        return new TokenResponse(
            token,
            "Bearer",
            jwtExpiration / 1000,
            username,
            email,
            fullName,
            role.name(),
            roles
        );
    }

    public record ParsedToken(
        String username,
        String email,
        String fullName,
        AuthRole role,
        boolean renewed,
        Instant expiresAt
    ) {
    }
}
