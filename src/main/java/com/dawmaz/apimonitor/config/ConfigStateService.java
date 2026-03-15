package com.dawmaz.apimonitor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConfigStateService {

    private static final Logger log = LoggerFactory.getLogger(ConfigStateService.class);

    private final ConfigLoader configLoader;
    private final AtomicReference<AppConfig> currentConfig = new AtomicReference<>();
    private final AtomicReference<Disposable> reloadSubscription = new AtomicReference<>();
    private volatile String lastRawConfig;

    public ConfigStateService(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public AppConfig current() {
        AppConfig config = currentConfig.get();
        if (config == null) {
            throw new IllegalStateException("Runtime config is not initialized");
        }
        return config;
    }

    public AppConfig initialize() {
        ConfigLoader.ConfigSnapshot snapshot = configLoader.loadSnapshot();
        currentConfig.set(snapshot.config());
        lastRawConfig = snapshot.rawJson();
        return snapshot.config();
    }

    public Disposable startLiveReload() {
        Disposable existingSubscription = reloadSubscription.get();
        if (existingSubscription != null && !existingSubscription.isDisposed()) {
            return existingSubscription;
        }

        Disposable newSubscription = Flux.interval(Duration.ofSeconds(2))
                .doOnNext(tick -> reloadIfChanged())
                .subscribe();

        if (reloadSubscription.compareAndSet(existingSubscription, newSubscription)) {
            return newSubscription;
        }

        newSubscription.dispose();
        Disposable currentSubscription = reloadSubscription.get();
        return currentSubscription == null ? Disposables.disposed() : currentSubscription;
    }

    private void reloadIfChanged() {
        try {
            ConfigLoader.ConfigSnapshot snapshot = configLoader.loadSnapshot();
            if (!snapshot.rawJson().equals(lastRawConfig)) {
                currentConfig.set(snapshot.config());
                lastRawConfig = snapshot.rawJson();
                log.info("[CONFIG] Reloaded config.json; monitoring {} endpoints.", snapshot.config().endpoints().size());
            }
        } catch (Exception ex) {
            log.warn("[CONFIG] Reload failed, keeping last known config: {}", ex.getMessage());
        }
    }
}

