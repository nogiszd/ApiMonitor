package com.dawmaz.apimonitor.service;

import com.dawmaz.apimonitor.config.EndpointConfig;
import com.dawmaz.apimonitor.event.CheckCompletedEvent;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@Service
public class HttpCheckService {

    private final WebClient webClient;

    public HttpCheckService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<CheckCompletedEvent> check(EndpointConfig endpoint, int timeoutSeconds) {
        long start = System.currentTimeMillis();

        return webClient
                .method(HttpMethod.valueOf(endpoint.method()))
                .uri(endpoint.url())
                .retrieve()
                .toBodilessEntity()
                .map(r -> {
                    long duration = System.currentTimeMillis() - start;
                    int[] expectedStatuses = endpoint.expectedStatuses() == null
                            ? new int[0]
                            : endpoint.expectedStatuses();
                    boolean success = Arrays.stream(expectedStatuses)
                            .anyMatch(s -> s == r.getStatusCode().value());

                    return new CheckCompletedEvent(
                            endpoint,
                            r.getStatusCode().value(),
                            duration,
                            success,
                            null,
                            Instant.now()
                    );
                })
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume(ex -> Mono.just(
                        new CheckCompletedEvent(
                                endpoint,
                                0,
                                System.currentTimeMillis() - start,
                                false,
                                ex.getMessage(),
                                Instant.now()
                        )
                ));
    }
}
