package com.dawmaz.apimonitor.config;

import java.util.List;

public record AppConfig(
        String language,
        int intervalSeconds,
        int timeoutSeconds,
        int incidentFailureThreshold,
        int incidentRecoverySuccessThreshold,
        int metricsWindowSeconds,
        DiscordConfig discord,
        SmtpConfig smtp,
        List<EndpointConfig> endpoints,
        List<DomainConfig> domains
) {
}
