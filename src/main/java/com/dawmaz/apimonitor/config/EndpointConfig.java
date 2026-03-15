package com.dawmaz.apimonitor.config;

import com.fasterxml.jackson.annotation.JsonAlias;

public record EndpointConfig(
        String id,
        String name,
        String url,
        String method,
        @JsonAlias("expectedStatusCodes")
        int[] expectedStatuses,
        long slowThresholdMs
) {
}
