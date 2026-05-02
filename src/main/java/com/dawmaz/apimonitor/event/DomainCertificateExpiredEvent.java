package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.DomainConfig;

import java.time.Instant;

public record DomainCertificateExpiredEvent(
        DomainConfig domain,
        Instant expiresAt,
        long daysSinceExpiry,
        String issuer,
        Instant timestamp
) implements MonitoringEvent {
}
