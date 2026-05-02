package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.DomainConfig;

import java.time.Instant;

public record DomainCertificateExpiringEvent(
        DomainConfig domain,
        Instant expiresAt,
        long daysUntilExpiry,
        String issuer,
        Instant timestamp
) implements MonitoringEvent {
}
