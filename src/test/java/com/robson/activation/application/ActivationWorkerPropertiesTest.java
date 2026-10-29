package com.robson.activation.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationWorkerPropertiesTest {

    private final ActivationWorkerProperties properties = new ActivationWorkerProperties(
            true, 20, 5, Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofMinutes(2));

    @Test
    void deveDobrarAEsperaACadaTentativa() {
        assertThat(properties.retryDelay(1, 0)).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.retryDelay(2, 0)).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.retryDelay(3, 0)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void naoDevePassarDoLimiteMaximo() {
        assertThat(properties.retryDelay(30, 0)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void deveSomarAteVintePorCentoDeVariacao() {
        assertThat(properties.retryDelay(1, 1.0)).isEqualTo(Duration.ofSeconds(6));
    }
}
