package com.tus.microservices.auth.service;

import com.tus.microservices.auth.config.OAuth2LoginProperties;
import com.tus.microservices.auth.dto.TokenResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Converts successful provider login into the same JWT response shape used by password auth.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationService authenticationService;
    private final OAuth2LoginProperties oAuth2LoginProperties;

    public OAuth2AuthenticationSuccessHandler(
            AuthenticationService authenticationService,
            OAuth2LoginProperties oAuth2LoginProperties) {
        this.authenticationService = authenticationService;
        this.oAuth2LoginProperties = oAuth2LoginProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            throw new ServletException("OAuth2 login completed without an OAuth2 authentication token.");
        }

        TokenResponse tokenResponse = authenticationService.loginWithOAuth2(
            oauth2Token.getAuthorizedClientRegistrationId(),
            oauth2Token.getPrincipal().getAttributes()
        );

        String fragment = UriComponentsBuilder.newInstance()
            .queryParam("access_token", tokenResponse.accessToken())
            .queryParam("token_type", tokenResponse.tokenType())
            .queryParam("expires_in", tokenResponse.expiresIn())
            .queryParam("username", tokenResponse.username())
            .queryParam("email", tokenResponse.email())
            .queryParam("full_name", tokenResponse.fullName())
            .queryParam("role", tokenResponse.role())
            .queryParam("roles", String.join(",", tokenResponse.roles()))
            .build()
            .encode()
            .getQuery();

        String redirectUri = UriComponentsBuilder.fromUriString(oAuth2LoginProperties.getSuccessRedirectUri())
            .fragment(fragment)
            .build(true)
            .toUriString();

        response.sendRedirect(redirectUri);
    }
}
