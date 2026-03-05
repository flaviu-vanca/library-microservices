package com.tus.microservices.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a verified email for GitHub accounts when the default user-info response omits it.
 */
@Service
public class GitHubEmailOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(GitHubEmailOAuth2UserService.class);

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient = RestClient.builder()
        .baseUrl("https://api.github.com")
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if (!"github".equalsIgnoreCase(registrationId)) {
            return user;
        }

        Map<String, Object> attributes = new LinkedHashMap<>(user.getAttributes());
        String email = asText(attributes.get("email"));
        if (!StringUtils.hasText(email)) {
            GitHubEmailAddress primaryEmail = fetchPrimaryEmail(userRequest.getAccessToken().getTokenValue());
            if (primaryEmail == null || !StringUtils.hasText(primaryEmail.email())) {
                throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email"),
                    "GitHub did not return an email address for this account."
                );
            }
            attributes.put("email", primaryEmail.email());
            attributes.put("email_verified", primaryEmail.verified());
        } else {
            attributes.putIfAbsent("email_verified", Boolean.TRUE);
        }

        String nameAttributeKey = userRequest.getClientRegistration()
            .getProviderDetails()
            .getUserInfoEndpoint()
            .getUserNameAttributeName();

        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttributeKey);
    }

    private GitHubEmailAddress fetchPrimaryEmail(String accessToken) {
        List<GitHubEmailAddress> emails = restClient.get()
            .uri("/user/emails")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(new ParameterizedTypeReference<List<GitHubEmailAddress>>() {
            });

        if (emails == null || emails.isEmpty()) {
            return null;
        }

        GitHubEmailAddress primaryEmail = emails.stream()
            .filter(entry -> StringUtils.hasText(entry.email()))
            .filter(GitHubEmailAddress::verified)
            .filter(GitHubEmailAddress::primary)
            .findFirst()
            .orElseGet(() -> emails.stream()
                .filter(entry -> StringUtils.hasText(entry.email()))
                .filter(GitHubEmailAddress::verified)
                .findFirst()
                .orElseGet(() -> emails.stream()
                    .filter(entry -> StringUtils.hasText(entry.email()))
                    .findFirst()
                    .orElse(null)));

        if (primaryEmail != null) {
            log.debug("Resolved GitHub email {}", primaryEmail.email());
        }

        return primaryEmail;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record GitHubEmailAddress(
        String email,
        boolean primary,
        boolean verified
    ) {
    }
}
