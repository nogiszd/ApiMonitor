package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.EndpointConfig;

import java.time.Instant;

public record CheckCompletedEvent(
        EndpointConfig endpoint,
        int statusCode,
        long responseTimeMs,
        boolean success,
        String errorMessage,
        Instant timestamp
) implements MonitoringEvent {
}
