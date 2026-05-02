package com.dawmaz.apimonitor.config;

import java.util.Map;

public record DiscordConfig(
        boolean enabled,
        String webhookUrl,
        Map<String, DiscordMessagesGroup> messages
) {

    public DiscordMessagesGroup templatesFor(String language) {
        if (messages == null) {
            return null;
        }
        DiscordMessagesGroup group = language == null ? null : messages.get(language);
        if (group != null) {
            return group;
        }
        return messages.get("en");
    }

    public record DiscordMessagesGroup(
            DiscordMessageTemplateConfig incidentStart,
            DiscordMessageTemplateConfig incidentRecovered,
            DiscordMessageTemplateConfig slowResponse,
            DiscordMessageTemplateConfig domainCertificateExpirationWarning,
            DiscordMessageTemplateConfig domainCertificateExpired
    ) {
    }

    public record DiscordMessageTemplateConfig(
            String title,
            String description,
            String color
    ) {
    }
}
