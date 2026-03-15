package com.dawmaz.apimonitor.config;

import java.util.List;

public record AppConfig(
        int intervalSeconds,
        int timeoutSeconds,
        int incidentFailureThreshold,
        int incidentRecoverySuccessThreshold,
        int metricsWindowSeconds,
        DiscordConfig discord,
        List<EndpointConfig> endpoints
) {
}
