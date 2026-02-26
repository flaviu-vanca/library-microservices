package com.tus.microservices.gateway.health;

import com.tus.microservices.gateway.config.GatewayReadinessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiredServicesHealthIndicatorTest {

    @Test
    void reportsUpWhenAllRequiredServicesHaveInstances() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        GatewayReadinessProperties properties = properties("AUTH-SERVICE", "LIBRARY-SERVICE");

        when(discoveryClient.getInstances("AUTH-SERVICE")).thenReturn(List.of(instance("AUTH-SERVICE")));
        when(discoveryClient.getInstances("LIBRARY-SERVICE")).thenReturn(List.of(instance("LIBRARY-SERVICE")));

        RequiredServicesHealthIndicator indicator =
            new RequiredServicesHealthIndicator(discoveryClient, properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenAnyRequiredServiceIsMissing() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        GatewayReadinessProperties properties = properties("AUTH-SERVICE", "LIBRARY-SERVICE");

        when(discoveryClient.getInstances("AUTH-SERVICE")).thenReturn(List.of(instance("AUTH-SERVICE")));
        when(discoveryClient.getInstances("LIBRARY-SERVICE")).thenReturn(List.of());

        RequiredServicesHealthIndicator indicator =
            new RequiredServicesHealthIndicator(discoveryClient, properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails())
            .containsEntry("missingServices", List.of("LIBRARY-SERVICE"));
    }

    private GatewayReadinessProperties properties(String... services) {
        GatewayReadinessProperties properties = new GatewayReadinessProperties();
        properties.setRequiredServices(List.of(services));
        return properties;
    }

    private DefaultServiceInstance instance(String serviceId) {
        return new DefaultServiceInstance(serviceId + "-1", serviceId, "localhost", 8080, false);
    }
}
