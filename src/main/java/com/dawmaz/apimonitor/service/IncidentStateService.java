package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.event.CheckCompletedEvent;
import com.dawmaz.apimonitor.event.IncidentOpenedEvent;
import com.dawmaz.apimonitor.event.IncidentRecoveredEvent;
import com.dawmaz.apimonitor.event.MonitoringEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IncidentStateService {

    private final Map<String, Boolean> incidentOpenMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailuresMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveSuccessesMap = new ConcurrentHashMap<>();

    public MonitoringEvent process(CheckCompletedEvent event, AppConfig config) {
        String endpointId = event.endpoint().id();

        incidentOpenMap.putIfAbsent(endpointId, false);
        consecutiveFailuresMap.putIfAbsent(endpointId, 0);
        consecutiveSuccessesMap.putIfAbsent(endpointId, 0);

        boolean incidentOpen = incidentOpenMap.get(endpointId);

        if (event.success()) {
            consecutiveFailuresMap.put(endpointId, 0);
            consecutiveSuccessesMap.put(endpointId, consecutiveSuccessesMap.get(endpointId) + 1);

            if (incidentOpen
                    && consecutiveSuccessesMap.get(endpointId) >= config.incidentRecoverySuccessThreshold()) {

                incidentOpenMap.put(endpointId, false);
                consecutiveSuccessesMap.put(endpointId, 0);

                return new IncidentRecoveredEvent(
                        event.endpoint(),
                        "Detected %d consecutive successful checks".formatted(config.incidentRecoverySuccessThreshold()),
                        Instant.now()
                );
            }

            return null;
        }

        consecutiveSuccessesMap.put(endpointId, 0);
        consecutiveFailuresMap.put(endpointId, consecutiveFailuresMap.get(endpointId) + 1);

        if (!incidentOpen
                && consecutiveFailuresMap.get(endpointId) >= config.incidentFailureThreshold()) {

            incidentOpenMap.put(endpointId, true);
            consecutiveFailuresMap.put(endpointId, 0);

            return new IncidentOpenedEvent(
                    event.endpoint(),
                    "Detected %d consecutive failures".formatted(config.incidentFailureThreshold()),
                    Instant.now()
            );
        }

        return null;
    }
}
