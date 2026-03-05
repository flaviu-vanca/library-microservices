package com.tus.microservices.auth.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lightweight client registration repository that tolerates zero configured providers.
 */
public class StaticClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final List<ClientRegistration> registrations;
    private final Map<String, ClientRegistration> registrationsById;

    public StaticClientRegistrationRepository(List<ClientRegistration> registrations) {
        this.registrations = List.copyOf(registrations);
        this.registrationsById = this.registrations.stream()
            .collect(Collectors.toUnmodifiableMap(ClientRegistration::getRegistrationId, Function.identity()));
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrationsById.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.iterator();
    }
}
