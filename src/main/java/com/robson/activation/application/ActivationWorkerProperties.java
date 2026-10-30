package com.robson.activation.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "activation.worker")
public record ActivationWorkerProperties(
        @Positive int batchSize,
        @Positive int maxAttempts,
        @NotNull Duration baseDelay,
        @NotNull Duration maxDelay,
        @NotNull Duration stuckAfter
) {
}
