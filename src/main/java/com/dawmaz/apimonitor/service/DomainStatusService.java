package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.event.DomainCertificateExpiredEvent;
import com.dawmaz.apimonitor.event.DomainCertificateExpiringEvent;
import com.dawmaz.apimonitor.event.DomainCheckCompletedEvent;
import com.dawmaz.apimonitor.event.EventBus;
import com.dawmaz.apimonitor.event.MonitoringEvent;
import com.dawmaz.apimonitor.model.DomainCertificateStatus;
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
public class DomainStatusService {

    private final ConcurrentHashMap<String, DomainCertificateStatus> statuses = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<DomainCertificateStatus>> listeners = new CopyOnWriteArrayList<>();
    private final Disposable subscription;

    public DomainStatusService(EventBus eventBus) {
        this.subscription = eventBus.events().subscribe(this::handleEvent);
    }

    private void handleEvent(MonitoringEvent event) {
        switch (event) {
            case DomainCheckCompletedEvent e -> update(toStatus(e));
            case DomainCertificateExpiringEvent e -> overrideStatus(e.domain().domain(), DomainCertificateStatus.Status.EXPIRING_SOON);
            case DomainCertificateExpiredEvent e -> overrideStatus(e.domain().domain(), DomainCertificateStatus.Status.EXPIRED);
            default -> { }
        }
    }

    private void overrideStatus(String domain, DomainCertificateStatus.Status status) {
        DomainCertificateStatus current = statuses.get(domain);
        if (current == null) {
            return;
        }
        update(new DomainCertificateStatus(
                current.domain(),
                status,
                current.expiresAt(),
                current.daysUntilExpiry(),
                current.issuer(),
                current.errorMessage(),
                current.lastChecked()
        ));
    }

    private DomainCertificateStatus toStatus(DomainCheckCompletedEvent e) {
        if (!e.success()) {
            return new DomainCertificateStatus(
                    e.domain(),
                    DomainCertificateStatus.Status.ERROR,
                    null, 0L, null, e.errorMessage(), e.timestamp()
            );
        }
        DomainCertificateStatus.Status status;
        if (e.daysUntilExpiry() < 0) {
            status = DomainCertificateStatus.Status.EXPIRED;
        } else if (e.daysUntilExpiry() <= e.domain().thresholdDays()) {
            status = DomainCertificateStatus.Status.EXPIRING_SOON;
        } else {
            status = DomainCertificateStatus.Status.VALID;
        }
        return new DomainCertificateStatus(
                e.domain(), status, e.expiresAt(), e.daysUntilExpiry(),
                e.issuer(), null, e.timestamp()
        );
    }

    private void update(DomainCertificateStatus status) {
        statuses.put(status.domain().domain(), status);
        listeners.forEach(l -> l.accept(status));
    }

    public Map<String, DomainCertificateStatus> getStatuses() {
        return Collections.unmodifiableMap(statuses);
    }

    public Registration addUpdateListener(Consumer<DomainCertificateStatus> listener) {
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
