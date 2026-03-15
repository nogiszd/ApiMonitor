package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.EndpointConfig;

import java.time.Instant;

public record CheckRequestedEvent(
        EndpointConfig endpoint,
        Instant timestamp
) implements MonitoringEvent {
}
