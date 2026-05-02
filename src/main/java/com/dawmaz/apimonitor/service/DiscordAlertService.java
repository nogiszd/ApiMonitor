package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.AppConfig;
import com.dawmaz.apimonitor.config.DiscordConfig;
import com.dawmaz.apimonitor.config.DiscordConfig.DiscordMessageTemplateConfig;
import com.dawmaz.apimonitor.config.DiscordConfig.DiscordMessagesGroup;
import com.dawmaz.apimonitor.config.DomainConfig;
import com.dawmaz.apimonitor.event.DomainCertificateExpiredEvent;
import com.dawmaz.apimonitor.event.DomainCertificateExpiringEvent;
import com.dawmaz.apimonitor.event.IncidentOpenedEvent;
import com.dawmaz.apimonitor.event.IncidentRecoveredEvent;
import com.dawmaz.apimonitor.event.SlowResponseDetectedEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dawmaz.apimonitor.helper.MessageTemplateHelper.*;

@Service
public class DiscordAlertService {

    private static final String APP_NAME = "API Monitor";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private static final int COLOR_INCIDENT_OPENED = 0xE74C3C;
    private static final int COLOR_INCIDENT_RECOVERED = 0x2ECC71;
    private static final int COLOR_SLOW_RESPONSE = 0xF39C12;
    private static final int COLOR_DOMAIN_CERTIFICATE_EXPIRING = 0xE67E22;
    private static final int COLOR_DOMAIN_CERTIFICATE_EXPIRED = 0xC0392B;

    private final DiscordWebhookService discordWebhookService;

    public DiscordAlertService(DiscordWebhookService discordWebhookService) {
        this.discordWebhookService = discordWebhookService;
    }

    public Mono<Void> sendIncidentOpened(AppConfig appConfig, IncidentOpenedEvent event) {
        DiscordConfig discordConfig = appConfig.discord();
        DiscordMessageTemplateConfig template = templateFor(discordConfig, appConfig.language(), DiscordMessagesGroup::incidentStart);

        Map<String, Object> embed = discordWebhookService.buildEmbed(
                templateValue(template, "title", "INCIDENT OPENED"),
                replaceTokens(
                        templateValue(template, "description", "Wykryto problem z endpointem {endpointName}."),
                        Map.of(
                                "endpointName", event.endpoint().name(),
                                "endpointUrl", event.endpoint().url(),
                                "reason", event.reason()
                        )
                ),
                parseColor(template, COLOR_INCIDENT_OPENED),
                event.timestamp(),
                List.of(
                        discordWebhookService.buildField("Endpoint", event.endpoint().name(), false),
                        discordWebhookService.buildField("URL", event.endpoint().url(), false),
                        discordWebhookService.buildField("Reason", event.reason(), false)
                )
        );

        return discordWebhookService.sendEmbeds(discordConfig, List.of(embed));
    }

    public Mono<Void> sendIncidentRecovered(AppConfig appConfig, IncidentRecoveredEvent event) {
        DiscordConfig discordConfig = appConfig.discord();
        DiscordMessageTemplateConfig template = templateFor(discordConfig, appConfig.language(), DiscordMessagesGroup::incidentRecovered);

        Map<String, Object> embed = discordWebhookService.buildEmbed(
                templateValue(template, "title", "INCIDENT RECOVERED"),
                replaceTokens(
                        templateValue(template, "description", "Endpoint {endpointName} wrocił do poprawnego stanu."),
                        Map.of(
                                "endpointName", event.endpoint().name(),
                                "endpointUrl", event.endpoint().url(),
                                "reason", event.reason()
                        )
                ),
                parseColor(template, COLOR_INCIDENT_RECOVERED),
                event.timestamp(),
                List.of(
                        discordWebhookService.buildField("Endpoint", event.endpoint().name(), false),
                        discordWebhookService.buildField("URL", event.endpoint().url(), false),
                        discordWebhookService.buildField("Reason", event.reason(), false)
                )
        );

        return discordWebhookService.sendEmbeds(discordConfig, List.of(embed));
    }

    public Mono<Void> sendSlowResponse(AppConfig appConfig, SlowResponseDetectedEvent event) {
        DiscordConfig discordConfig = appConfig.discord();
        DiscordMessageTemplateConfig template = templateFor(discordConfig, appConfig.language(), DiscordMessagesGroup::slowResponse);

        Map<String, Object> embed = discordWebhookService.buildEmbed(
                templateValue(template, "title", "SLOW RESPONSE DETECTED"),
                replaceTokens(
                        templateValue(template, "description", "Wolna odpowiedź endpointu {endpointName}."),
                        Map.of(
                                "endpointName", event.endpoint().name(),
                                "endpointUrl", event.endpoint().url(),
                                "responseTimeMs", String.valueOf(event.responseTimeMs()),
                                "slowThresholdMs", String.valueOf(event.endpoint().slowThresholdMs())
                        )
                ),
                parseColor(template, COLOR_SLOW_RESPONSE),
                event.timestamp(),
                List.of(
                        discordWebhookService.buildField("Endpoint", event.endpoint().name(), false),
                        discordWebhookService.buildField("URL", event.endpoint().url(), false),
                        discordWebhookService.buildField("Response time", event.responseTimeMs() + " ms", true),
                        discordWebhookService.buildField("Slow threshold", event.endpoint().slowThresholdMs() + " ms", true)
                )
        );

        return discordWebhookService.sendEmbeds(discordConfig, List.of(embed));
    }

    public Mono<Void> sendDomainCertificateExpiring(AppConfig appConfig, DomainCertificateExpiringEvent event) {
        DiscordConfig discordConfig = appConfig.discord();
        DiscordMessageTemplateConfig template = templateFor(discordConfig, appConfig.language(), DiscordMessagesGroup::domainCertificateExpirationWarning);

        Map<String, String> tokens = domainTokens(event.domain(), event.expiresAt(), event.issuer(), event.timestamp());
        tokens.put("days", String.valueOf(event.daysUntilExpiry()));

        Map<String, Object> embed = discordWebhookService.buildEmbed(
                templateValue(template, "title", "DOMAIN CERTIFICATE EXPIRING"),
                replaceTokens(
                        templateValue(template, "description", "Certyfikat dla domeny {domain} wygasa za {days} dni."),
                        tokens
                ),
                parseColor(template, COLOR_DOMAIN_CERTIFICATE_EXPIRING),
                event.timestamp(),
                List.of(
                        discordWebhookService.buildField("Domain", event.domain().domain(), false),
                        discordWebhookService.buildField("Expires at", event.expiresAt() == null ? "?" : event.expiresAt().toString(), true),
                        discordWebhookService.buildField("Days until expiry", String.valueOf(event.daysUntilExpiry()), true)
                )
        );

        return discordWebhookService.sendEmbeds(discordConfig, List.of(embed));
    }

    public Mono<Void> sendDomainCertificateExpired(AppConfig appConfig, DomainCertificateExpiredEvent event) {
        DiscordConfig discordConfig = appConfig.discord();
        DiscordMessageTemplateConfig template = templateFor(discordConfig, appConfig.language(), DiscordMessagesGroup::domainCertificateExpired);

        Map<String, String> tokens = domainTokens(event.domain(), event.expiresAt(), event.issuer(), event.timestamp());
        tokens.put("days", String.valueOf(event.daysSinceExpiry()));

        Map<String, Object> embed = discordWebhookService.buildEmbed(
                templateValue(template, "title", "DOMAIN CERTIFICATE EXPIRED"),
                replaceTokens(
                        templateValue(template, "description", "Certyfikat dla domeny {domain} wygasł {days} dni temu."),
                        tokens
                ),
                parseColor(template, COLOR_DOMAIN_CERTIFICATE_EXPIRED),
                event.timestamp(),
                List.of(
                        discordWebhookService.buildField("Domain", event.domain().domain(), false),
                        discordWebhookService.buildField("Expired at", event.expiresAt() == null ? "?" : event.expiresAt().toString(), true),
                        discordWebhookService.buildField("Days since expiry", String.valueOf(event.daysSinceExpiry()), true)
                )
        );

        return discordWebhookService.sendEmbeds(discordConfig, List.of(embed));
    }

    private static Map<String, String> domainTokens(DomainConfig domain, Instant expiresAt, String issuer, Instant timestamp) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("domain", domain.domain());
        tokens.put("thresholdDays", String.valueOf(domain.thresholdDays()));
        tokens.put("expiresAt", expiresAt == null ? "" : expiresAt.toString());
        tokens.put("expiresAtDate", expiresAt == null ? "" : DATE_FORMATTER.format(expiresAt));
        tokens.put("issuer", issuer == null ? "" : issuer);
        tokens.put("timestamp", timestamp == null ? "" : timestamp.toString());
        tokens.put("appName", APP_NAME);
        return tokens;
    }

    private static DiscordMessageTemplateConfig templateFor(DiscordConfig discordConfig,
                                                            String language,
                                                            java.util.function.Function<DiscordMessagesGroup, DiscordMessageTemplateConfig> selector) {
        if (discordConfig == null) {
            return null;
        }
        DiscordMessagesGroup group = discordConfig.templatesFor(language);
        return group == null ? null : selector.apply(group);
    }
}
