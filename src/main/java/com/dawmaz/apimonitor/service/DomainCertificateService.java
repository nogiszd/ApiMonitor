package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.event.DomainCheckCompletedEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

@Service
public class DomainCertificateService {

    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    public Mono<DomainCheckCompletedEvent> check(DomainConfig domain, int timeoutSeconds) {
        return Mono.fromCallable(() -> fetchCertificate(domain))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 5)))
                .onErrorResume(ex -> Mono.just(failure(domain, ex.getMessage())));
    }

    private DomainCheckCompletedEvent fetchCertificate(DomainConfig domain) throws Exception {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(domain.domain(), DEFAULT_HTTPS_PORT)) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.startHandshake();

            Certificate[] peerCertificates = socket.getSession().getPeerCertificates();
            if (peerCertificates.length == 0 || !(peerCertificates[0] instanceof X509Certificate cert)) {
                return failure(domain, "No X.509 certificate presented by host");
            }

            Instant expiresAt = cert.getNotAfter().toInstant();
            long daysUntilExpiry = Duration.between(Instant.now(), expiresAt).toDays();
            String issuer = cert.getIssuerX500Principal().getName();

            return new DomainCheckCompletedEvent(
                    domain,
                    true,
                    expiresAt,
                    daysUntilExpiry,
                    issuer,
                    null,
                    Instant.now()
            );
        }
    }

    private DomainCheckCompletedEvent failure(DomainConfig domain, String message) {
        return new DomainCheckCompletedEvent(
                domain,
                false,
                null,
                0L,
                null,
                message,
                Instant.now()
        );
    }
}
