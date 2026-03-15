package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.DiscordConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DiscordWebhookService {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookService.class);

    private final WebClient webClient;

    public DiscordWebhookService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Void> sendContent(DiscordConfig discordConfig, String content) {
        if (isDiscordDisabled(discordConfig)) {
            return Mono.empty();
        }

        return send(discordConfig.webhookUrl(), buildContentPayload(content));
    }

    public Mono<Void> sendEmbeds(DiscordConfig discordConfig, List<Map<String, Object>> embeds) {
        if (isDiscordDisabled(discordConfig)) {
            return Mono.empty();
        }

        return send(discordConfig.webhookUrl(), buildEmbedsPayload(embeds));
    }

    public Map<String, Object> buildEmbed(String title,
                                          String description,
                                          int color,
                                          Instant timestamp,
                                          List<Map<String, Object>> fields) {
        Map<String, Object> embed = new java.util.LinkedHashMap<>();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color);

        if (timestamp != null) {
            embed.put("timestamp", timestamp.toString());
        }

        if (fields != null && !fields.isEmpty()) {
            embed.put("fields", fields);
        }

        return Map.copyOf(embed);
    }

    public Map<String, Object> buildField(String name, String value, boolean inline) {
        return Map.of(
                "name", Objects.requireNonNullElse(name, ""),
                "value", Objects.requireNonNullElse(value, ""),
                "inline", inline
        );
    }

    private Map<String, Object> buildContentPayload(String content) {
        return Map.of("content", Objects.requireNonNullElse(content, ""));
    }

    private Map<String, Object> buildEmbedsPayload(List<Map<String, Object>> embeds) {
        return Map.of("embeds", embeds == null ? List.of() : embeds);
    }

    private Mono<Void> send(String webhookUrl, Map<String, Object> payload) {
        return webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(ex -> {
                    log.warn("[DISCORD] Error sending webhook to {}: {}", webhookUrl, ex.getMessage());
                    return Mono.empty();
                });
    }

    private boolean isDiscordDisabled(DiscordConfig config) {
        return !config.enabled() || config.webhookUrl() == null || config.webhookUrl().isBlank();
    }
}
