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

    private static final long DEFAULT_CHECK_INTERVAL_SECONDS = 86400L;

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

        AppConfig startupConfig = configStateService.current();
        List<DomainConfig> domains = startupConfig.domains();

        if (domains == null || domains.isEmpty()) {
            return Disposables.disposed();
        }

        Disposable newSubscription = Flux.fromIterable(domains)
                .flatMap(d -> {
                    long intervalSeconds = d.checkIntervalSeconds() != null ? d.checkIntervalSeconds() : DEFAULT_CHECK_INTERVAL_SECONDS;
                    return Flux.interval(Duration.ZERO, Duration.ofSeconds(intervalSeconds))
                            .flatMap(tick -> domainCertificateService.check(d, configStateService.current().timeoutSeconds()));
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
