package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.EndpointConfig;

import java.time.Instant;

public record SlowResponseDetectedEvent(
        EndpointConfig endpoint,
        long responseTimeMs,
        Instant timestamp
) implements MonitoringEvent {
}
