package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.DiscordConfig;
import com.dawmaz.apimonitor.config.DiscordConfig.DiscordMessageTemplateConfig;
import com.dawmaz.apimonitor.event.IncidentOpenedEvent;
import com.dawmaz.apimonitor.event.IncidentRecoveredEvent;
import com.dawmaz.apimonitor.event.SlowResponseDetectedEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static com.dawmaz.apimonitor.helper.MessageTemplateHelper.*;

@Service
public class DiscordAlertService {

    private static final int COLOR_INCIDENT_OPENED = 0xE74C3C;
    private static final int COLOR_INCIDENT_RECOVERED = 0x2ECC71;
    private static final int COLOR_SLOW_RESPONSE = 0xF39C12;

    private final DiscordWebhookService discordWebhookService;

    public DiscordAlertService(DiscordWebhookService discordWebhookService) {
        this.discordWebhookService = discordWebhookService;
    }

    public Mono<Void> sendIncidentOpened(DiscordConfig discordConfig, IncidentOpenedEvent event) {
        DiscordMessageTemplateConfig template = discordConfig.messages() != null
                ? discordConfig.messages().incidentStart()
                : null;

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

    public Mono<Void> sendIncidentRecovered(DiscordConfig discordConfig, IncidentRecoveredEvent event) {
        DiscordMessageTemplateConfig template = discordConfig.messages() != null
                ? discordConfig.messages().incidentRecovered()
                : null;

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

    public Mono<Void> sendSlowResponse(DiscordConfig discordConfig, SlowResponseDetectedEvent event) {
        DiscordMessageTemplateConfig template = discordConfig.messages() != null
                ? discordConfig.messages().slowResponse()
                : null;

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


}

