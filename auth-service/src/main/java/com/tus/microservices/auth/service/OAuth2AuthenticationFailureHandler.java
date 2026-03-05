package com.tus.microservices.auth.service;

import com.tus.microservices.auth.config.OAuth2LoginProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Sends provider login failures back to the gateway UI as a URL fragment for the static client to display.
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2LoginProperties oAuth2LoginProperties;

    public OAuth2AuthenticationFailureHandler(OAuth2LoginProperties oAuth2LoginProperties) {
        this.oAuth2LoginProperties = oAuth2LoginProperties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String fragment = UriComponentsBuilder.newInstance()
            .queryParam("oauth_error", exception.getMessage())
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
