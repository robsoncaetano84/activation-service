package com.robson.activation.application;

import com.robson.activation.domain.ActivationRequest;
import com.robson.activation.domain.ActivationRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivationRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private ActivationRequestRepository repository;
    private ActivationRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(ActivationRequestRepository.class);
        service = new ActivationRequestService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void deveCriarQuandoExternalIdNaoExiste() {
        when(repository.findByExternalId("EXT-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create("EXT-1", "CUST-1", "INTERNET-500");

        assertThat(result.created()).isTrue();
        assertThat(result.request().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void deveGuardarOTraceIdDaRequisicaoQueCriou() {
        MDC.put(CorrelationId.MDC_KEY, "trace-da-requisicao");
        when(repository.findByExternalId("EXT-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create("EXT-1", "CUST-1", "INTERNET-500");

        assertThat(result.request().getTraceId()).isEqualTo("trace-da-requisicao");
    }

    @Test
    void deveDevolverExistenteQuandoRetryComMesmoConteudo() {
        var existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", "trace-1", NOW);
        when(repository.findByExternalId("EXT-1")).thenReturn(Optional.of(existing));

        var result = service.create("EXT-1", "CUST-1", "INTERNET-500");

        assertThat(result.created()).isFalse();
        assertThat(result.request()).isSameAs(existing);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deveRejeitarMesmoExternalIdComConteudoDiferente() {
        var existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", "trace-1", NOW);
        when(repository.findByExternalId("EXT-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create("EXT-1", "CUST-1", "TV-PREMIUM"))
                .isInstanceOf(DuplicateRequestConflictException.class);
    }

    @Test
    void deveTratarCorridaQuandoOutraRequisicaoGravaPrimeiro() {
        var winner = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", "trace-1", NOW);
        when(repository.findByExternalId("EXT-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_activation_request_external_id"));

        var result = service.create("EXT-1", "CUST-1", "INTERNET-500");

        assertThat(result.created()).isFalse();
        assertThat(result.request()).isSameAs(winner);
    }
}
