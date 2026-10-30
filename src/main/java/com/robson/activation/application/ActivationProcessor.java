package com.robson.activation.application;

import com.robson.activation.application.ProviderResult.PermanentFailure;
import com.robson.activation.application.ProviderResult.Success;
import com.robson.activation.application.ProviderResult.TransientFailure;
import com.robson.activation.domain.ActivationRequest;
import com.robson.activation.domain.ActivationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ActivationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ActivationProcessor.class);

    private final ActivationRequestRepository repository;
    private final ActivationProvider provider;
    private final TransactionTemplate transaction;
    private final ActivationWorkerProperties properties;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public ActivationProcessor(ActivationRequestRepository repository, ActivationProvider provider,
                               TransactionTemplate transaction, ActivationWorkerProperties properties,
                               RetryPolicy retryPolicy, Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.transaction = transaction;
        this.properties = properties;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    public int processPending() {
        List<ActivationRequest> claimed = transaction.execute(status -> claimBatch());
        claimed.forEach(this::process);
        return claimed.size();
    }

    private List<ActivationRequest> claimBatch() {
        var now = clock.instant();
        var batch = repository.findReadyToProcess(now, properties.batchSize());
        batch.forEach(request -> request.startProcessing(now));
        return batch; // o Hibernate grava o PROCESSING no commit desta transação
    }

    private void process(ActivationRequest request) {
        try (var ignored = MDC.putCloseable(CorrelationId.MDC_KEY, traceIdOrRequestId(request))) {
            var result = callProvider(request);
            transaction.executeWithoutResult(status -> applyResult(request.getId(), result));
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.warn("A solicitação {} foi alterada por outro processo; resultado descartado", request.getId());
        }
    }

    private static String traceIdOrRequestId(ActivationRequest request) {
        return Objects.requireNonNullElse(request.getTraceId(), request.getId().toString());
    }

    private ProviderResult callProvider(ActivationRequest request) {
        try {
            return provider.activate(request);
        } catch (RuntimeException ex) {
            log.error("Erro inesperado ao chamar o provedor para a solicitação {}", request.getId(), ex);
            return new TransientFailure("Erro inesperado ao chamar o provedor", null);
        }
    }

    private void applyResult(UUID id, ProviderResult result) {
        var request = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Solicitação " + id + " sumiu antes de gravar o resultado"));
        var now = clock.instant();
        switch (result) {
            case Success success -> request.complete(now);
            case PermanentFailure failure -> request.fail(failure.reason(), now);
            case TransientFailure failure -> retryOrFail(request, failure, now);
        }
    }

    private void retryOrFail(ActivationRequest request, TransientFailure failure, Instant now) {
        retryPolicy.nextAttempt(request.getAttempts(), failure.retryAfter(), now).ifPresentOrElse(
                nextAttempt -> request.scheduleRetry(failure.reason(), nextAttempt, now),
                () -> request.fail("Limite de tentativas atingido. Último erro: " + failure.reason(), now));
    }
}
