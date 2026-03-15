package com.dawmaz.apimonitor.config;

public record DiscordConfig(
        boolean enabled,
        String webhookUrl,
        DiscordMessagesConfig messages
) {

    public record DiscordMessagesConfig(
            DiscordMessageTemplateConfig incidentStart,
            DiscordMessageTemplateConfig incidentRecovered,
            DiscordMessageTemplateConfig slowResponse
    ) {
    }

    public record DiscordMessageTemplateConfig(
            String title,
            String description,
            String color
    ) {
    }
}
