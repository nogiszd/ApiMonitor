package com.dawmaz.apimonitor.event;

import com.dawmaz.apimonitor.config.EndpointConfig;

import java.time.Instant;

public record IncidentOpenedEvent(
        EndpointConfig endpoint,
        String reason,
        Instant timestamp
) implements MonitoringEvent {
}
