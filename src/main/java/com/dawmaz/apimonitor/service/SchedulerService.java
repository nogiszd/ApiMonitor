package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.event.CheckRequestedEvent;
import com.dawmaz.apimonitor.event.EventBus;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SchedulerService {

    private final EventBus eventBus;
    private final HttpCheckService httpCheckService;
    private final AtomicReference<Disposable> schedulerSubscription = new AtomicReference<>();

    public SchedulerService(EventBus eventBus, HttpCheckService httpCheckService) {
        this.eventBus = eventBus;
        this.httpCheckService = httpCheckService;
    }

    public Disposable start(ConfigStateService configStateService) {
        Disposable existingSubscription = schedulerSubscription.get();
        if (existingSubscription != null && !existingSubscription.isDisposed()) {
            return existingSubscription;
        }

        AppConfig startupConfig = configStateService.current();

        Disposable newSubscription = Flux.interval(Duration.ofSeconds(startupConfig.intervalSeconds()))
                .flatMap(tick -> {
                    AppConfig currentConfig = configStateService.current();
                    return Flux.fromIterable(currentConfig.endpoints())
                            .doOnNext(e -> eventBus.emit(new CheckRequestedEvent(e, Instant.now())))
                            .flatMap(e -> httpCheckService.check(e, currentConfig.timeoutSeconds()));
                })
                .subscribe(eventBus::emit);

        if (schedulerSubscription.compareAndSet(existingSubscription, newSubscription)) {
            return newSubscription;
        }

        newSubscription.dispose();
        Disposable currentSubscription = schedulerSubscription.get();
        return currentSubscription == null ? Disposables.disposed() : currentSubscription;
    }
}
