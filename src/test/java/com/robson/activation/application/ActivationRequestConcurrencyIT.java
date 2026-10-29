package com.robson.activation.application;

import com.robson.activation.TestcontainersConfiguration;
import com.robson.activation.infrastructure.ActivationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "activation.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class ActivationRequestConcurrencyIT {

    private static final int THREADS = 10;

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private ActivationRequestRepository repository;

    @Test
    void requisicoesSimultaneasComMesmoExternalIdGeramUmUnicoRegistro() throws Exception {
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<CreationResult>>();

        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                results.add(executor.submit(() -> {
                    start.await(); 
                    return service.create("EXT-CONCORRENTE", "CUST-1", "INTERNET-500");
                }));
            }
            start.countDown(); 
        }

        var created = 0;
        for (var result : results) {
            if (result.get().created()) {
                created++;
            }
        }

        assertThat(created).isEqualTo(1);
        assertThat(repository.findByExternalId("EXT-CONCORRENTE")).isPresent();
        assertThat(repository.count()).isEqualTo(1);
    }
}