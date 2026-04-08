package com.tus.microservices.gateway.health;

import com.tus.microservices.gateway.config.GatewayReadinessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequiredServicesHealthIndicatorTest {

    @Test
    void reportsUpWhenAllRequiredServicesHaveInstances() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        GatewayReadinessProperties properties = properties();

        when(discoveryClient.getInstances("AUTH-SERVICE")).thenReturn(List.of(instance("AUTH-SERVICE")));
        when(discoveryClient.getInstances("LIBRARY-SERVICE")).thenReturn(List.of(instance("LIBRARY-SERVICE")));

        RequiredServicesHealthIndicator indicator =
            new RequiredServicesHealthIndicator(discoveryClient, properties);

        assertThat(Objects.requireNonNull(indicator.health()).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenAnyRequiredServiceIsMissing() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        GatewayReadinessProperties properties = properties();

        when(discoveryClient.getInstances("AUTH-SERVICE")).thenReturn(List.of(instance("AUTH-SERVICE")));
        when(discoveryClient.getInstances("LIBRARY-SERVICE")).thenReturn(List.of());

        RequiredServicesHealthIndicator indicator =
            new RequiredServicesHealthIndicator(discoveryClient, properties);

        assertThat(Objects.requireNonNull(indicator.health()).getStatus()).isEqualTo(Status.DOWN);
        assertThat(Objects.requireNonNull(indicator.health()).getDetails())
            .containsEntry("missingServices", List.of("LIBRARY-SERVICE"));
    }

    private GatewayReadinessProperties properties() {
        GatewayReadinessProperties properties = new GatewayReadinessProperties();
        properties.setRequiredServices(List.of(new String[]{"AUTH-SERVICE", "LIBRARY-SERVICE"}));
        return properties;
    }

    private DefaultServiceInstance instance(String serviceId) {
        return new DefaultServiceInstance(serviceId + "-1", serviceId, "localhost", 8080, false);
    }
}
