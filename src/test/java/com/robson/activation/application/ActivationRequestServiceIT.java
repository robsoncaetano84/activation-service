package com.robson.activation.application;

import com.robson.activation.TestcontainersConfiguration;
import com.robson.activation.domain.ActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "activation.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class ActivationRequestServiceIT {

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private ActivationRequestRepository repository;

    @BeforeEach
    void limparBanco() {
        repository.deleteAll();
    }

    @Test
    void deveResponderComAsMesmasDatasQueFicamGravadas() {
        var criada = service.create("EXT-DATAS", "CUST-1", "INTERNET-500").request();

        var gravada = repository.findById(criada.getId()).orElseThrow();

        assertThat(criada.getCreatedAt()).isEqualTo(gravada.getCreatedAt());
        assertThat(criada.getUpdatedAt()).isEqualTo(gravada.getUpdatedAt());
    }
}
