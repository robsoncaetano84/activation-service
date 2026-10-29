package com.robson.activation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@ConfigurationProperties(prefix = "activation.worker")
public record ActivationWorkerProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        Duration baseDelay,
        Duration maxDelay,
        Duration stuckAfter
) {

    private static final double MAX_JITTER = 0.2;

    public Duration retryDelay(int attempt) {
        return retryDelay(attempt, ThreadLocalRandom.current().nextDouble());
    }

    // Backoff exponencial (5s, 10s, 20s...) limitado ao máximo, com até 20% de variação
    Duration retryDelay(int attempt, double random) {
        var exponential = baseDelay.toMillis() * (1L << Math.min(attempt - 1, 20));
        var capped = Math.min(exponential, maxDelay.toMillis());
        var jitter = (long) (capped * MAX_JITTER * random);
        return Duration.ofMillis(capped + jitter);
    }
}
