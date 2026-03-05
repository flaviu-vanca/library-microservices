package com.tus.microservices.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional social login provider configuration for auth-service.
 */
@ConfigurationProperties(prefix = "auth.oauth2")
public class OAuth2LoginProperties {

    private String successRedirectUri = "http://localhost:8085/";
    private String callbackRedirectUri = "http://localhost:8085/auth/login/oauth2/code/{registrationId}";
    private final Providers providers = new Providers();

    public String getSuccessRedirectUri() {
        return successRedirectUri;
    }

    public void setSuccessRedirectUri(String successRedirectUri) {
        this.successRedirectUri = successRedirectUri;
    }

    public String getCallbackRedirectUri() {
        return callbackRedirectUri;
    }

    public void setCallbackRedirectUri(String callbackRedirectUri) {
        this.callbackRedirectUri = callbackRedirectUri;
    }

    public Providers getProviders() {
        return providers;
    }

    public List<String> enabledProviderIds() {
        List<String> enabled = new ArrayList<>();
        if (providers.google.isConfigured()) {
            enabled.add("google");
        }
        if (providers.github.isConfigured()) {
            enabled.add("github");
        }
        if (providers.facebook.isConfigured()) {
            enabled.add("facebook");
        }
        return List.copyOf(enabled);
    }

    public static class Providers {
        private final ClientCredentials google = new ClientCredentials();
        private final ClientCredentials github = new ClientCredentials();
        private final ClientCredentials facebook = new ClientCredentials();

        public ClientCredentials getGoogle() {
            return google;
        }

        public ClientCredentials getGithub() {
            return github;
        }

        public ClientCredentials getFacebook() {
            return facebook;
        }
    }

    public static class ClientCredentials {
        private String clientId;
        private String clientSecret;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public boolean isConfigured() {
            return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
        }
    }
}
