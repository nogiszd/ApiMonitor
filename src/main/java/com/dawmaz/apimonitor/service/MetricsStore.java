package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.model.MetricsSnapshot;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class MetricsStore {

    private static final Logger log = LoggerFactory.getLogger(MetricsStore.class);
    private static final int MAX_IN_MEMORY = 240;

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Deque<MetricsSnapshot> recent = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<MetricsSnapshot>> listeners = new CopyOnWriteArrayList<>();

    public MetricsStore(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        this.file = Files.createTempFile("apimonitor-metrics-", ".ndjson");
        this.file.toFile().deleteOnExit();
        log.info("[METRICS] Persisting metrics to {}", file);
    }

    public synchronized void append(MetricsSnapshot snapshot) {
        recent.addLast(snapshot);
        while (recent.size() > MAX_IN_MEMORY) {
            recent.pollFirst();
        }
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (Exception ex) {
            log.warn("[METRICS] Failed to persist snapshot: {}", ex.getMessage());
        }
        notifyListeners(snapshot);
    }

    public synchronized List<MetricsSnapshot> recent() {
        return new ArrayList<>(recent);
    }

    public Registration addListener(Consumer<MetricsSnapshot> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public Path file() {
        return file;
    }

    @PreDestroy
    public void cleanup() {
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("[METRICS] Deleted temp file {}", file);
            }
        } catch (Exception ex) {
            log.warn("[METRICS] Failed to delete temp file {}: {}", file, ex.getMessage());
        }
    }

    private void notifyListeners(MetricsSnapshot snapshot) {
        for (Consumer<MetricsSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (Exception ex) {
                log.warn("[METRICS] listener failed: {}", ex.getMessage());
            }
        }
    }
}
