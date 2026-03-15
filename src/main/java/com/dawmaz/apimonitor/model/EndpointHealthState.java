package com.dawmaz.apimonitor.model;

import com.dawmaz.apimonitor.config.EndpointConfig;
import com.dawmaz.apimonitor.event.CheckCompletedEvent;

public record EndpointHealthState(
        EndpointConfig endpoint,
        int consecutiveFailures,
        int consecutiveSuccesses,
        boolean incidentOpen,
        CheckCompletedEvent lastEvent
) {

    public static EndpointHealthState initial(EndpointConfig endpoint) {
        return new EndpointHealthState(endpoint, 0, 0, false, null);
    }

    public EndpointHealthState next(CheckCompletedEvent event) {
        if (event.success()) {
            return new EndpointHealthState(
                    endpoint,
                    0,
                    consecutiveSuccesses + 1,
                    incidentOpen,
                    event
            );
        }

        return new EndpointHealthState(
                endpoint,
                consecutiveFailures + 1,
                0,
                incidentOpen,
                event
        );
    }

    public EndpointHealthState withIncidentOpen(boolean open) {
        return new EndpointHealthState(
                endpoint,
                consecutiveFailures,
                consecutiveSuccesses,
                open,
                lastEvent
        );
    }
}
