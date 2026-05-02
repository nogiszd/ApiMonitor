package com.dawmaz.apimonitor.model;

import java.time.Instant;
import java.util.Map;

public record MetricsSnapshot(
        Instant windowStart,
        Instant windowEnd,
        int windowSeconds,
        Map<String, EndpointMetrics> perEndpoint
) {

    public long totalChecks() {
        return perEndpoint.values().stream().mapToLong(EndpointMetrics::totalChecks).sum();
    }

    public long totalSuccess() {
        return perEndpoint.values().stream().mapToLong(EndpointMetrics::successCount).sum();
    }

    public long totalFailure() {
        return perEndpoint.values().stream().mapToLong(EndpointMetrics::failureCount).sum();
    }

    public double aggregatedAvgResponseTimeMs() {
        long total = totalChecks();
        if (total == 0) {
            return 0d;
        }
        double weighted = perEndpoint.values().stream()
                .mapToDouble(m -> m.avgResponseTimeMs() * m.totalChecks())
                .sum();
        return weighted / total;
    }

    public double successRatePercent() {
        long total = totalChecks();
        if (total == 0) {
            return 0d;
        }
        return (double) totalSuccess() / total * 100d;
    }

    public record EndpointMetrics(
            String endpointId,
            String endpointName,
            long totalChecks,
            long successCount,
            long failureCount,
            double avgResponseTimeMs,
            long maxResponseTimeMs
    ) {
        public double successRatePercent() {
            if (totalChecks == 0) return 0d;
            return (double) successCount / totalChecks * 100d;
        }
    }
}
