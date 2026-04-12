package com.dawmaz.apimonitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ConfigLoader {

    private final ObjectMapper objectMapper;
    private final Path externalConfigPath;

    public ConfigLoader(ObjectMapper objectMapper,
                        @Value("${app.config.path:config.json}") String configPath) {
        this.objectMapper = objectMapper;
        this.externalConfigPath = Path.of(configPath).toAbsolutePath().normalize();
    }

    public AppConfig load() {
        return loadSnapshot().config();
    }

    public ConfigSnapshot loadSnapshot() {
        try {
            String rawJson;

            if (Files.exists(externalConfigPath)) {
                rawJson = Files.readString(externalConfigPath, StandardCharsets.UTF_8);
            } else {
                try (var inputStream = new ClassPathResource("config.json").getInputStream()) {
                    rawJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            AppConfig config = objectMapper.readValue(rawJson, AppConfig.class);
            return new ConfigSnapshot(config, rawJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.json", e);
        }
    }

    public void save(AppConfig config) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(externalConfigPath, json, StandardCharsets.UTF_8);
    }

    public record ConfigSnapshot(AppConfig config, String rawJson) {
    }
}
