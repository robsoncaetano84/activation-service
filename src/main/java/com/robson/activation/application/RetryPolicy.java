package com.robson.activation.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

@Component
public class RetryPolicy {

    private static final double MAX_JITTER = 0.2;

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final DoubleSupplier random;

    @Autowired
    public RetryPolicy(ActivationWorkerProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    RetryPolicy(ActivationWorkerProperties properties, DoubleSupplier random) {
        this.maxAttempts = properties.maxAttempts();
        this.baseDelay = properties.baseDelay();
        this.maxDelay = properties.maxDelay();
        this.random = random;
    }

    // Vazio quando as tentativas acabaram. Se o provedor mandou Retry-After, ele vale mais que o backoff
    public Optional<Instant> nextAttempt(int attemptsSoFar, Duration retryAfter, Instant now) {
        if (attemptsSoFar >= maxAttempts) {
            return Optional.empty();
        }
        var delay = retryAfter != null ? retryAfter : backoff(attemptsSoFar);
        return Optional.of(now.plus(delay));
    }

    // Exponencial (5s, 10s, 20s...) até o máximo, com até 20% a mais para espalhar as tentativas
    private Duration backoff(int attempt) {
        var exponential = baseDelay.toMillis() * (1L << Math.min(attempt - 1, 20));
        var capped = Math.min(exponential, maxDelay.toMillis());
        var jitter = (long) (capped * MAX_JITTER * random.getAsDouble());
        return Duration.ofMillis(capped + jitter);
    }
}
