package com.robson.activation.infrastructure;

import com.robson.activation.application.ActivationProcessor;
import com.robson.activation.application.ActivationReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "activation.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ActivationWorker {

    private static final Logger log = LoggerFactory.getLogger(ActivationWorker.class);

    private final ActivationProcessor processor;
    private final ActivationReconciler reconciler;

    public ActivationWorker(ActivationProcessor processor, ActivationReconciler reconciler) {
        this.processor = processor;
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${activation.worker.poll-interval}")
    public void processPending() {
        var processed = processor.processPending();
        if (processed > 0) {
            log.info("{} solicitações enviadas ao provedor", processed);
        }
    }

    @Scheduled(fixedDelayString = "${activation.worker.reconcile-interval}")
    public void releaseStuck() {
        var released = reconciler.releaseStuck();
        if (released > 0) {
            log.warn("{} solicitações presas em PROCESSING foram devolvidas para a fila", released);
        }
    }
}
