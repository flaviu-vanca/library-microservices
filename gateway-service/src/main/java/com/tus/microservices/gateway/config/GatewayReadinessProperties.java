package com.tus.microservices.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "gateway.readiness")
public class GatewayReadinessProperties {

    private List<String> requiredServices = List.of(
        "AUTH-SERVICE",
        "LIBRARY-SERVICE",
        "INVENTORY-SERVICE"
    );

    public List<String> getRequiredServices() {
        return requiredServices;
    }

    public void setRequiredServices(List<String> requiredServices) {
        this.requiredServices = requiredServices;
    }
}
