package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.event.*;
import com.dawmaz.apimonitor.model.EndpointStatus;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class EndpointStatusService {

    private final ConcurrentHashMap<String, EndpointStatus> statuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<EndpointStatus>> listeners = new CopyOnWriteArrayList<>();
    private final Disposable subscription;

    public EndpointStatusService(EventBus eventBus) {
        this.subscription = eventBus.events().subscribe(this::handleEvent);
    }

    private void handleEvent(MonitoringEvent event) {
        switch (event) {
            case CheckCompletedEvent e -> {
                var status = e.success() ? EndpointStatus.Status.UP : EndpointStatus.Status.DOWN;
                update(new EndpointStatus(e.endpoint(), status, e.responseTimeMs(), e.statusCode(), e.errorMessage(), e.timestamp()));
            }
            case SlowResponseDetectedEvent e -> {
                var current = statuses.get(e.endpoint().id());
                var statusCode = current != null ? current.statusCode() : 0;
                update(new EndpointStatus(e.endpoint(), EndpointStatus.Status.SLOW, e.responseTimeMs(), statusCode, null, e.timestamp()));
            }
            case IncidentOpenedEvent e -> {
                var current = statuses.get(e.endpoint().id());
                update(new EndpointStatus(
                        e.endpoint(), EndpointStatus.Status.DOWN,
                        current != null ? current.responseTimeMs() : 0,
                        current != null ? current.statusCode() : 0,
                        e.reason(), e.timestamp()));
            }
            case IncidentRecoveredEvent e -> {
                var current = statuses.get(e.endpoint().id());
                update(new EndpointStatus(
                        e.endpoint(), EndpointStatus.Status.UP,
                        current != null ? current.responseTimeMs() : 0,
                        current != null ? current.statusCode() : 0,
                        null, e.timestamp()));
            }
            default -> { }
        }
    }

    private void update(EndpointStatus status) {
        statuses.put(status.endpoint().id(), status);
        listeners.forEach(l -> l.accept(status));
    }

    public Map<String, EndpointStatus> getStatuses() {
        return Collections.unmodifiableMap(statuses);
    }

    public Registration addUpdateListener(Consumer<EndpointStatus> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @PreDestroy
    public void destroy() {
        if (!subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
