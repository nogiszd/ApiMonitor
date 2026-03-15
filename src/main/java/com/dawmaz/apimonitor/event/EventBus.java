package com.dawmaz.apimonitor.event;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class EventBus {

    private final Sinks.Many<MonitoringEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    public void emit(MonitoringEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<MonitoringEvent> events() {
        return sink.asFlux();
    }
}
