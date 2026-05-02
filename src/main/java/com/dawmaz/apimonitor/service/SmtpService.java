package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.config.SmtpConfig;
import com.dawmaz.apimonitor.config.SmtpConfig.SmtpTemplateConfig;
import com.dawmaz.apimonitor.config.SmtpConfig.SmtpTemplatesGroup;
import com.dawmaz.apimonitor.event.DomainCertificateExpiredEvent;
import com.dawmaz.apimonitor.event.DomainCertificateExpiringEvent;
import com.dawmaz.apimonitor.helper.MessageTemplateHelper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

@Service
public class SmtpService {

    private static final Logger log = LoggerFactory.getLogger(SmtpService.class);
    private static final String APP_NAME = "API Monitor";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    public Mono<Void> sendDomainCertificateExpiring(AppConfig appConfig, DomainCertificateExpiringEvent event) {
        Map<String, String> tokens = baseTokens(event.domain(), event.expiresAt(), event.issuer(), event.timestamp());
        tokens.put("days", String.valueOf(event.daysUntilExpiry()));
        return send(
                appConfig,
                event.domain(),
                SmtpTemplatesGroup::domainCertificateExpirationWarning,
                tokens,
                "Domain certificate expiring",
                "The certificate for {domain} expires in {days} days."
        );
    }

    public Mono<Void> sendDomainCertificateExpired(AppConfig appConfig, DomainCertificateExpiredEvent event) {
        Map<String, String> tokens = baseTokens(event.domain(), event.expiresAt(), event.issuer(), event.timestamp());
        tokens.put("days", String.valueOf(event.daysSinceExpiry()));
        return send(
                appConfig,
                event.domain(),
                SmtpTemplatesGroup::domainCertificateExpired,
                tokens,
                "Domain certificate expired",
                "The certificate for {domain} expired {days} days ago."
        );
    }

    private Map<String, String> baseTokens(DomainConfig domain, Instant expiresAt, String issuer, Instant timestamp) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("domain", domain.domain());
        tokens.put("thresholdDays", String.valueOf(domain.thresholdDays()));
        tokens.put("recipient", recipientFor(domain) == null ? "" : recipientFor(domain));
        tokens.put("expiresAt", expiresAt == null ? "" : expiresAt.toString());
        tokens.put("expiresAtDate", expiresAt == null ? "" : DATE_FORMATTER.format(expiresAt));
        tokens.put("issuer", issuer == null ? "" : issuer);
        tokens.put("timestamp", timestamp == null ? "" : timestamp.toString());
        tokens.put("appName", APP_NAME);
        return tokens;
    }

    private Mono<Void> send(AppConfig appConfig,
                            DomainConfig domain,
                            Function<SmtpTemplatesGroup, SmtpTemplateConfig> selector,
                            Map<String, String> tokens,
                            String fallbackSubject,
                            String fallbackBody) {
        SmtpConfig smtp = appConfig.smtp();
        if (smtp == null || !smtp.isEnabled()) {
            return Mono.empty();
        }
        if (!isEmailEnabledForDomain(domain)) {
            return Mono.empty();
        }
        String recipient = recipientFor(domain);
        if (recipient == null) {
            return Mono.empty();
        }

        SmtpTemplatesGroup group = smtp.templatesFor(appConfig.language());
        SmtpTemplateConfig template = group == null ? null : selector.apply(group);
        String subject = MessageTemplateHelper.replaceTokens(valueOrFallback(template, true, fallbackSubject), tokens);
        String body = MessageTemplateHelper.replaceTokens(valueOrFallback(template, false, fallbackBody), tokens);

        return Mono.fromRunnable(() -> deliver(smtp, recipient, subject, body))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("[SMTP] Failed to send mail to {}: {}", recipient, ex.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private void deliver(SmtpConfig smtp, String recipient, String subject, String body) {
        try {
            JavaMailSenderImpl sender = buildSender(smtp);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (smtp.from() != null && !smtp.from().isBlank()) {
                helper.setFrom(smtp.from());
            }
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, true);
            sender.send(message);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private JavaMailSenderImpl buildSender(SmtpConfig smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        if (smtp.port() != null) {
            sender.setPort(smtp.port());
        }
        if (smtp.username() != null && !smtp.username().isBlank()) {
            sender.setUsername(smtp.username());
        }
        if (smtp.password() != null && !smtp.password().isBlank()) {
            sender.setPassword(smtp.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        if (smtp.username() != null && !smtp.username().isBlank()) {
            props.put("mail.smtp.auth", "true");
        }
        props.put("mail.smtp.starttls.enable", "true");
        return sender;
    }

    private boolean isEmailEnabledForDomain(DomainConfig domain) {
        return domain.warnings() != null
                && domain.warnings().email() != null
                && Boolean.TRUE.equals(domain.warnings().email().enabled());
    }

    private String recipientFor(DomainConfig domain) {
        if (domain.warnings() == null || domain.warnings().email() == null) {
            return null;
        }
        String to = domain.warnings().email().to();
        return to == null || to.isBlank() ? null : to.trim();
    }

    private String valueOrFallback(SmtpTemplateConfig template, boolean subject, String fallback) {
        if (template == null) {
            return fallback;
        }
        String value = subject ? template.subject() : template.body();
        return value == null || value.isBlank() ? fallback : value;
    }
}
