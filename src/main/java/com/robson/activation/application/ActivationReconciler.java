package com.robson.activation.application;

import com.robson.activation.domain.ActivationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ActivationReconciler {

    private final ActivationRequestRepository repository;
    private final ActivationWorkerProperties properties;
    private final Clock clock;

    public ActivationReconciler(ActivationRequestRepository repository, ActivationWorkerProperties properties,
                                Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    // Pode reenviar: com a chave de idempotência o provedor não ativa duas vezes
    @Transactional
    public int releaseStuck() {
        var now = clock.instant();
        var stuck = repository.findStuckInProcessing(now.minus(properties.stuckAfter()));
        stuck.forEach(request -> request.scheduleRetry("Processamento interrompido, reenviando", now, now));
        return stuck.size();
    }
}
