package com.robson.activation.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final ActivationWorkerProperties properties = new ActivationWorkerProperties(
            20, 5, Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofMinutes(5));

    private final RetryPolicy semVariacao = new RetryPolicy(properties, () -> 0.0);

    @Test
    void deveDobrarAEsperaACadaTentativa() {
        assertThat(semVariacao.nextAttempt(1, null, NOW)).contains(NOW.plusSeconds(5));
        assertThat(semVariacao.nextAttempt(2, null, NOW)).contains(NOW.plusSeconds(10));
        assertThat(semVariacao.nextAttempt(3, null, NOW)).contains(NOW.plusSeconds(20));
    }

    @Test
    void naoDevePassarDaEsperaMaxima() {
        var semLimiteDeTentativas = new RetryPolicy(new ActivationWorkerProperties(
                20, 100, Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofMinutes(5)), () -> 0.0);

        assertThat(semLimiteDeTentativas.nextAttempt(30, null, NOW)).contains(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void deveSomarAteVintePorCentoDeVariacao() {
        var variacaoMaxima = new RetryPolicy(properties, () -> 1.0);

        assertThat(variacaoMaxima.nextAttempt(1, null, NOW)).contains(NOW.plusSeconds(6));
    }

    @Test
    void deveRespeitarORetryAfterDoProvedor() {
        assertThat(semVariacao.nextAttempt(1, Duration.ofSeconds(30), NOW)).contains(NOW.plusSeconds(30));
    }

    @Test
    void naoDeveTentarDeNovoDepoisDoLimite() {
        assertThat(semVariacao.nextAttempt(5, null, NOW)).isEmpty();
        assertThat(semVariacao.nextAttempt(5, Duration.ofSeconds(30), NOW)).isEmpty();
    }
}
