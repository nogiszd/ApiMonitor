package com.dawmaz.apimonitor.helper;

import com.dawmaz.apimonitor.config.DiscordConfig;

import java.util.Map;

public class MessageTemplateHelper {
    public static String templateValue(DiscordConfig.DiscordMessageTemplateConfig template, String key, String fallback) {
        if (template == null) {
            return fallback;
        }

        String value = switch (key) {
            case "title" -> template.title();
            case "description" -> template.description();
            default -> null;
        };

        return value == null || value.isBlank() ? fallback : value;
    }

    public static int parseColor(DiscordConfig.DiscordMessageTemplateConfig template, int fallback) {
        if (template == null || template.color() == null || template.color().isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(template.color().replace("#", "").trim(), 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static String replaceTokens(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
