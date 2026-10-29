package com.robson.activation.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Validated
@ConfigurationProperties(prefix = "activation.worker")
public record ActivationWorkerProperties(
        @Positive int batchSize,
        @Positive int maxAttempts,
        @NotNull Duration baseDelay,
        @NotNull Duration maxDelay,
        @NotNull Duration stuckAfter
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
