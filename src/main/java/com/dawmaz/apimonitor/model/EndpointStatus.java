package com.dawmaz.apimonitor.model;

import com.dawmaz.apimonitor.config.EndpointConfig;

import java.time.Instant;

public record EndpointStatus(
        EndpointConfig endpoint,
        Status status,
        long responseTimeMs,
        int statusCode,
        String errorMessage,
        Instant lastChecked
) {
    public enum Status {
        UNKNOWN, UP, DOWN, SLOW
    }
}
