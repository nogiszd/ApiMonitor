package com.dawmaz.apimonitor.app;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.ConfigStateService;
import com.dawmaz.apimonitor.event.*;
import com.dawmaz.apimonitor.service.DiscordAlertService;
import com.dawmaz.apimonitor.service.DomainSchedulerService;
import com.dawmaz.apimonitor.service.IncidentStateService;
import com.dawmaz.apimonitor.service.SchedulerService;
import com.dawmaz.apimonitor.service.SmtpService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MonitoringStartup implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MonitoringStartup.class);

    private final ConfigStateService configStateService;
    private final SchedulerService schedulerService;
    private final DomainSchedulerService domainSchedulerService;
    private final EventBus eventBus;
    private final DiscordAlertService discordAlertService;
    private final SmtpService smtpService;
    private final IncidentStateService incidentStateService;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<Disposable> subscriptions = new ArrayList<>();

    public MonitoringStartup(ConfigStateService configStateService,
                             SchedulerService schedulerService,
                             DomainSchedulerService domainSchedulerService,
                             EventBus eventBus,
                             DiscordAlertService discordAlertService,
                             SmtpService smtpService,
                             IncidentStateService incidentStateService) {
        this.configStateService = configStateService;
        this.schedulerService = schedulerService;
        this.domainSchedulerService = domainSchedulerService;
        this.eventBus = eventBus;
        this.discordAlertService = discordAlertService;
        this.smtpService = smtpService;
        this.incidentStateService = incidentStateService;
    }

    @Override
    public void run(String ...args) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        AppConfig config = configStateService.initialize();

        subscriptions.add(startLoggingStream());
        subscriptions.add(startSlowResponseStream());
        subscriptions.add(startIncidentStateStream());
        subscriptions.add(startAlertStream());
        subscriptions.add(startDomainExpirationStream());
        subscriptions.add(startDiscordWebhookStream());
        subscriptions.add(startSmtpStream());
        subscriptions.add(startMetricsStream(config));

        subscriptions.add(configStateService.startLiveReload());
        subscriptions.add(schedulerService.start(configStateService));
        subscriptions.add(domainSchedulerService.start(configStateService));

        log.info("[SYSTEM] Monitoring started for {} endpoints.", config.endpoints().size());
    }

    @PreDestroy
    public void stop() {
        subscriptions.forEach(disposable -> {
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        });
    }

    private Disposable startLoggingStream() {
        return eventBus.events()
                .subscribe(e -> log.debug("[EVENT] {}", e));
    }

    private Disposable startSlowResponseStream() {
        return eventBus.events()
                .ofType(CheckCompletedEvent.class)
                .filter(CheckCompletedEvent::success)
                .filter(e -> e.responseTimeMs() > e.endpoint().slowThresholdMs())
                .map(e -> new SlowResponseDetectedEvent(
                        e.endpoint(),
                        e.responseTimeMs(),
                        Instant.now()
                ))
                .subscribe(eventBus::emit);
    }

    private Disposable startIncidentStateStream() {
        return eventBus.events()
                .ofType(CheckCompletedEvent.class)
                .flatMap(event -> {
                    MonitoringEvent monitoringEvent = incidentStateService.process(event, configStateService.current());
                    return monitoringEvent == null
                            ? Mono.empty()
                            : Mono.just(monitoringEvent);
                })
                .subscribe(eventBus::emit);
    }

    private Disposable startAlertStream() {
        Disposable slowResponseSubscription = eventBus.events()
                .ofType(SlowResponseDetectedEvent.class)
                .subscribe(e -> log.warn(
                        "[ALERT] Slow response on %s: %d ms".formatted(
                                e.endpoint().name(),
                                e.responseTimeMs()
                        )
                ));

        Disposable incidentOpenedSubscription = eventBus.events()
                .ofType(IncidentOpenedEvent.class)
                .subscribe(e -> log.error(
                        "[INCIDENT OPENED] %s -> %s".formatted(
                                e.endpoint().name(),
                                e.reason()
                        )
                ));

        Disposable incidentRecoveredSubscription = eventBus.events()
                .ofType(IncidentRecoveredEvent.class)
                .subscribe(event -> log.info(
                        "[INCIDENT RECOVERED] %s -> %s".formatted(
                                event.endpoint().name(),
                                event.reason()
                        )
                ));

        return () -> {
            slowResponseSubscription.dispose();
            incidentOpenedSubscription.dispose();
            incidentRecoveredSubscription.dispose();
        };
    }

    private Disposable startDiscordWebhookStream() {
        Disposable incidentOpenedSubscription = eventBus.events()
                .ofType(IncidentOpenedEvent.class)
                .flatMap(event -> discordAlertService.sendIncidentOpened(configStateService.current(), event))
                .subscribe();

        Disposable incidentRecoveredSubscription = eventBus.events()
                .ofType(IncidentRecoveredEvent.class)
                .flatMap(event -> discordAlertService.sendIncidentRecovered(configStateService.current(), event))
                .subscribe();

        Disposable slowResponseSubscription = eventBus.events()
                .ofType(SlowResponseDetectedEvent.class)
                .flatMap(event -> discordAlertService.sendSlowResponse(configStateService.current(), event))
                .subscribe();

        Disposable domainCertificateExpiringSubscription = eventBus.events()
                .ofType(DomainCertificateExpiringEvent.class)
                .flatMap(event -> discordAlertService.sendDomainCertificateExpiring(configStateService.current(), event))
                .subscribe();

        Disposable domainCertificateExpiredSubscription = eventBus.events()
                .ofType(DomainCertificateExpiredEvent.class)
                .flatMap(event -> discordAlertService.sendDomainCertificateExpired(configStateService.current(), event))
                .subscribe();

        return () -> {
            incidentOpenedSubscription.dispose();
            incidentRecoveredSubscription.dispose();
            slowResponseSubscription.dispose();
            domainCertificateExpiringSubscription.dispose();
            domainCertificateExpiredSubscription.dispose();
        };
    }

    private Disposable startSmtpStream() {
        Disposable expiringSubscription = eventBus.events()
                .ofType(DomainCertificateExpiringEvent.class)
                .flatMap(event -> smtpService.sendDomainCertificateExpiring(configStateService.current(), event))
                .subscribe();

        Disposable expiredSubscription = eventBus.events()
                .ofType(DomainCertificateExpiredEvent.class)
                .flatMap(event -> smtpService.sendDomainCertificateExpired(configStateService.current(), event))
                .subscribe();

        return () -> {
            expiringSubscription.dispose();
            expiredSubscription.dispose();
        };
    }

    private Disposable startDomainExpirationStream() {
        return eventBus.events()
                .ofType(DomainCheckCompletedEvent.class)
                .filter(DomainCheckCompletedEvent::success)
                .flatMap(e -> {
                    if (e.daysUntilExpiry() < 0) {
                        return Mono.just(new DomainCertificateExpiredEvent(
                                e.domain(),
                                e.expiresAt(),
                                -e.daysUntilExpiry(),
                                e.issuer(),
                                Instant.now()
                        ));
                    }
                    if (e.daysUntilExpiry() <= e.domain().thresholdDays()) {
                        return Mono.just(new DomainCertificateExpiringEvent(
                                e.domain(),
                                e.expiresAt(),
                                e.daysUntilExpiry(),
                                e.issuer(),
                                Instant.now()
                        ));
                    }
                    return Mono.empty();
                })
                .subscribe(eventBus::emit);
    }

    private Disposable startMetricsStream(AppConfig config) {
        return eventBus.events()
                .ofType(CheckCompletedEvent.class)
                .window(Duration.ofSeconds(config.metricsWindowSeconds()))
                .flatMap(Flux::collectList)
                .filter(list -> !list.isEmpty())
                .subscribe(list -> {
                    double avg = list.stream()
                            .mapToLong(CheckCompletedEvent::responseTimeMs)
                            .average()
                            .orElse(0.0);

                    long successCount = list.stream().filter(CheckCompletedEvent::success).count();
                    long failureCount = list.size() - successCount;

                    log.info("[METRICS] window=%ds total=%d success=%d failure=%d avg=%.2f ms"
                            .formatted(
                                    config.metricsWindowSeconds(),
                                    list.size(),
                                    successCount,
                                    failureCount,
                                    avg
                            ));
                });
    }
}
