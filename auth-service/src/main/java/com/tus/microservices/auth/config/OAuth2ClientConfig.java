package com.tus.microservices.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

/**
 * Registers only the OAuth2 providers that have credentials configured.
 */
@Configuration
@EnableConfigurationProperties(OAuth2LoginProperties.class)
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2LoginProperties properties) {
        return new StaticClientRegistrationRepository(buildRegistrations(properties));
    }

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public JwtDecoderFactory<ClientRegistration> jwtDecoderFactory() {
        OidcIdTokenDecoderFactory decoderFactory = new OidcIdTokenDecoderFactory();
        decoderFactory.setJwtValidatorFactory(clientRegistration -> {
            OidcIdTokenValidator validator = new OidcIdTokenValidator(clientRegistration);
            // Some providers can issue an ID token slightly ahead of the local JVM clock.
            validator.setClockSkew(Duration.ofMinutes(5));
            return JwtValidators.createDefaultWithValidators(validator);
        });
        return decoderFactory;
    }

    private List<ClientRegistration> buildRegistrations(OAuth2LoginProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();

        if (properties.getProviders().getGoogle().isConfigured()) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(properties.getProviders().getGoogle().getClientId())
                .clientSecret(properties.getProviders().getGoogle().getClientSecret())
                .scope("openid", "profile", "email")
                .redirectUri(properties.getCallbackRedirectUri())
                .build());
        }

        if (properties.getProviders().getGithub().isConfigured()) {
            registrations.add(CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId(properties.getProviders().getGithub().getClientId())
                .clientSecret(properties.getProviders().getGithub().getClientSecret())
                .scope("read:user", "user:email")
                .redirectUri(properties.getCallbackRedirectUri())
                .build());
        }

        if (properties.getProviders().getFacebook().isConfigured()) {
            registrations.add(CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
                .clientId(properties.getProviders().getFacebook().getClientId())
                .clientSecret(properties.getProviders().getFacebook().getClientSecret())
                .scope("public_profile", "email")
                .redirectUri(properties.getCallbackRedirectUri())
                .build());
        }

        return registrations;
    }
}
