package com.dawmaz.apimonitor.model;

import com.dawmaz.apimonitor.config.DomainConfig;

import java.time.Instant;

public record DomainCertificateStatus(
        DomainConfig domain,
        Status status,
        Instant expiresAt,
        long daysUntilExpiry,
        String issuer,
        String errorMessage,
        Instant lastChecked
) {
    public enum Status {
        UNKNOWN, VALID, EXPIRING_SOON, EXPIRED, ERROR
    }
}
