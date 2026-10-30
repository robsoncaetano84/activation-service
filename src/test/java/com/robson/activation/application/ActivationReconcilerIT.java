package com.robson.activation.application;

import com.robson.activation.TestcontainersConfiguration;
import com.robson.activation.domain.ActivationRequestRepository;
import com.robson.activation.domain.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "activation.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class ActivationReconcilerIT {

    @Autowired
    private ActivationReconciler reconciler;

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private ActivationRequestRepository repository;

    @BeforeEach
    void limparBanco() {
        repository.deleteAll();
    }

    @Test
    void deveDevolverParaAFilaSolicitacaoPresaEmProcessamento() {
        var id = criarSolicitacao("EXT-PRESA");
        colocarEmProcessamentoDesde(id, Instant.now().minus(10, ChronoUnit.MINUTES));

        var devolvidas = reconciler.releaseStuck();

        var request = repository.findById(id).orElseThrow();
        assertThat(devolvidas).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(Status.PENDING);
        assertThat(request.getLastError()).startsWith("Processamento interrompido");
    }

    @Test
    void naoDeveDevolverSolicitacaoQueAindaEstaNoPrazo() {
        var id = criarSolicitacao("EXT-EM-ANDAMENTO");
        colocarEmProcessamentoDesde(id, Instant.now());

        var devolvidas = reconciler.releaseStuck();

        assertThat(devolvidas).isZero();
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(Status.PROCESSING);
    }

    // Simula o worker caindo no meio do processamento
    private void colocarEmProcessamentoDesde(UUID id, Instant inicio) {
        var request = repository.findById(id).orElseThrow();
        request.startProcessing(inicio);
        repository.save(request);
    }

    private UUID criarSolicitacao(String externalId) {
        return service.create(externalId, "CUST-1", "INTERNET-500").request().getId();
    }
}
