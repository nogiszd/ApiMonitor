package com.dawmaz.apimonitor.event;

import java.time.Instant;

public interface MonitoringEvent {
    Instant timestamp();
}
