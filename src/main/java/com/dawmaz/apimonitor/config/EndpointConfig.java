package com.dawmaz.apimonitor.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EndpointConfig(
        String id,
        String name,
        String url,
        String method,
        @JsonProperty("expectedStatusCodes") @JsonAlias("expectedStatuses")
        int[] expectedStatuses,
        long slowThresholdMs
) {
}
