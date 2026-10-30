package com.robson.activation.application;

import com.robson.activation.TestcontainersConfiguration;
import com.robson.activation.application.ProviderResult.PermanentFailure;
import com.robson.activation.application.ProviderResult.Success;
import com.robson.activation.application.ProviderResult.TransientFailure;
import com.robson.activation.domain.ActivationRequestRepository;
import com.robson.activation.domain.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "activation.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class ActivationProcessorIT {

    @Autowired
    private ActivationProcessor processor;

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private ActivationRequestRepository repository;

    @MockitoBean
    private ActivationProvider provider;

    @BeforeEach
    void limparBanco() {
        repository.deleteAll();
    }

    @Test
    void deveConcluirQuandoProvedorAtiva() {
        var id = criarSolicitacao("EXT-OK");
        when(provider.activate(any())).thenReturn(new Success());

        processor.processPending();

        var request = repository.findById(id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(Status.COMPLETED);
        assertThat(request.getAttempts()).isEqualTo(1);
    }

    @Test
    void deveFalharSemNovaTentativaQuandoProvedorRecusa() {
        var id = criarSolicitacao("EXT-RECUSADA");
        when(provider.activate(any())).thenReturn(new PermanentFailure("O provedor recusou a ativação (HTTP 400)"));

        processor.processPending();

        var request = repository.findById(id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(Status.FAILED);
        assertThat(request.getLastError()).contains("HTTP 400");
    }

    @Test
    void deveReagendarQuandoFalhaTemporaria() {
        var id = criarSolicitacao("EXT-INSTAVEL");
        when(provider.activate(any())).thenReturn(new TransientFailure("Erro no provedor (HTTP 500)", null));
        var antes = Instant.now();

        processor.processPending();

        var request = repository.findById(id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getNextAttemptAt()).isAfter(antes);
    }

    @Test
    void deveFalharAoAtingirOLimiteDeTentativas() {
        var id = criarSolicitacao("EXT-SEMPRE-FALHA");
        // Retry-After zero para o teste não ficar esperando o backoff
        when(provider.activate(any())).thenReturn(new TransientFailure("Erro no provedor (HTTP 500)", Duration.ZERO));

        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            processor.processPending();
        }

        var request = repository.findById(id).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(Status.FAILED);
        assertThat(request.getAttempts()).isEqualTo(5);
        assertThat(request.getLastError()).startsWith("Limite de tentativas atingido");
    }

    @Test
    void deveChamarOProvedorComOTraceIdDaRequisicaoOriginal() {
        MDC.put(CorrelationId.MDC_KEY, "trace-da-origem");
        criarSolicitacao("EXT-RASTREIO");
        MDC.clear();
        var traceIdNaChamada = new AtomicReference<String>();
        when(provider.activate(any())).thenAnswer(invocation -> {
            traceIdNaChamada.set(MDC.get(CorrelationId.MDC_KEY));
            return new Success();
        });

        processor.processPending();

        assertThat(traceIdNaChamada.get()).isEqualTo("trace-da-origem");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    private UUID criarSolicitacao(String externalId) {
        return service.create(externalId, "CUST-1", "INTERNET-500").request().getId();
    }
}
