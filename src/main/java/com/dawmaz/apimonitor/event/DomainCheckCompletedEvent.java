package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.DomainConfig;

import java.time.Instant;

public record DomainCheckCompletedEvent(
        DomainConfig domain,
        boolean success,
        Instant expiresAt,
        long daysUntilExpiry,
        String issuer,
        String errorMessage,
        Instant timestamp
) implements MonitoringEvent {
}
