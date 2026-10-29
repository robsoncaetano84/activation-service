package com.robson.activation.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ActivationRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private ActivationRequest newRequest() {
        return ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
    }

    @Test
    void deveNascerPendenteESemTentativas() {
        var request = newRequest();

        assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        assertThat(request.getAttempts()).isZero();
        assertThat(request.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void deveConcluirAposProcessamento() {
        var request = newRequest();

        request.startProcessing(NOW);
        request.complete(LATER);

        assertThat(request.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void deveVoltarParaPendenteEmFalhaTemporaria() {
        var request = newRequest();
        request.startProcessing(NOW);

        request.scheduleRetry("HTTP 500", LATER, NOW);

        assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        assertThat(request.getNextAttemptAt()).isEqualTo(LATER);
        assertThat(request.getLastError()).isEqualTo("HTTP 500");
    }

    @Test
    void naoDeveConcluirSemTerPassadoPorProcessamento() {
        var request = newRequest();

        assertThatThrownBy(() -> request.complete(NOW))
                .isInstanceOfSatisfying(InvalidStatusTransitionException.class, ex -> {
                    assertThat(ex.getFrom()).isEqualTo(Status.PENDING);
                    assertThat(ex.getTo()).isEqualTo(Status.COMPLETED);
                });
    }

    @Test
    void naoDeveSairDeUmEstadoFinal() {
        var request = newRequest();
        request.startProcessing(NOW);
        request.complete(NOW);

        assertThatThrownBy(() -> request.startProcessing(LATER))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void deveCortarMensagemDeErroLonga() {
        var request = newRequest();
        request.startProcessing(NOW);

        request.fail("x".repeat(800), NOW);

        assertThat(request.getLastError()).hasSize(500);
    }
}