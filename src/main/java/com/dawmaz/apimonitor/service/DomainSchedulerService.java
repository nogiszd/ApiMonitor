package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.event.EventBus;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DomainSchedulerService {

    private static final Duration CHECK_INTERVAL = Duration.ofHours(1);

    private final EventBus eventBus;
    private final DomainCertificateService domainCertificateService;
    private final AtomicReference<Disposable> schedulerSubscription = new AtomicReference<>();

    public DomainSchedulerService(EventBus eventBus, DomainCertificateService domainCertificateService) {
        this.eventBus = eventBus;
        this.domainCertificateService = domainCertificateService;
    }

    public Disposable start(ConfigStateService configStateService) {
        Disposable existingSubscription = schedulerSubscription.get();
        if (existingSubscription != null && !existingSubscription.isDisposed()) {
            return existingSubscription;
        }

        Disposable newSubscription = Flux.interval(Duration.ZERO, CHECK_INTERVAL)
                .flatMap(tick -> {
                    AppConfig currentConfig = configStateService.current();
                    List<DomainConfig> domains = currentConfig.domains();
                    if (domains == null || domains.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(domains)
                            .flatMap(d -> domainCertificateService.check(d, currentConfig.timeoutSeconds()));
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
