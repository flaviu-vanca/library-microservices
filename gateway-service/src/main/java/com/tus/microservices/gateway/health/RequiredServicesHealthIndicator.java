package com.tus.microservices.gateway.health;

import com.tus.microservices.gateway.config.GatewayReadinessProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component("requiredServices")
public class RequiredServicesHealthIndicator implements HealthIndicator {

    private final DiscoveryClient discoveryClient;
    private final GatewayReadinessProperties readinessProperties;

    public RequiredServicesHealthIndicator(
        DiscoveryClient discoveryClient,
        GatewayReadinessProperties readinessProperties
    ) {
        this.discoveryClient = discoveryClient;
        this.readinessProperties = readinessProperties;
    }

    @Override
    public Health health() {
        List<String> requiredServices = normalizedRequiredServices();
        Map<String, Integer> instanceCounts = new LinkedHashMap<>();
        List<String> missingServices = new ArrayList<>();

        for (String serviceId : requiredServices) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            int instanceCount = instances.size();

            instanceCounts.put(serviceId, instanceCount);
            if (instanceCount == 0) {
                missingServices.add(serviceId);
            }
        }

        Health.Builder builder = missingServices.isEmpty() ? Health.up() : Health.down();
        return builder
            .withDetail("requiredServices", requiredServices)
            .withDetail("instanceCounts", instanceCounts)
            .withDetail("missingServices", missingServices)
            .build();
    }

    private List<String> normalizedRequiredServices() {
        return readinessProperties.getRequiredServices().stream()
            .map(String::trim)
            .filter(serviceId -> !serviceId.isEmpty())
            .map(serviceId -> serviceId.toUpperCase(Locale.ROOT))
            .toList();
    }
}
