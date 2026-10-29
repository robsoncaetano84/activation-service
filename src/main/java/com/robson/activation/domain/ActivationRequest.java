package com.robson.activation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_request")
public class ActivationRequest {

    private static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, updatable = false, length = 64)
    private String externalId;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 64)
    private String customerId;

    @Column(name = "product_code", nullable = false, updatable = false, length = 64)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ActivationRequest() {
    }

    public static ActivationRequest create(String externalId, String customerId,
                                           String productCode, Instant now) {
        var request = new ActivationRequest();
        request.id = UUID.randomUUID();
        request.externalId = externalId;
        request.customerId = customerId;
        request.productCode = productCode;
        request.status = Status.PENDING;
        request.attempts = 0;
        request.nextAttemptAt = now;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    public void startProcessing(Instant now) {
        transitionTo(Status.PROCESSING, now);
        attempts++;
    }

    public void complete(Instant now) {
        transitionTo(Status.COMPLETED, now);
        nextAttemptAt = null;
        lastError = null;
    }

    public void fail(String reason, Instant now) {
        transitionTo(Status.FAILED, now);
        nextAttemptAt = null;
        lastError = truncate(reason);
    }

    public void scheduleRetry(String reason, Instant nextAttempt, Instant now) {
        transitionTo(Status.PENDING, now);
        nextAttemptAt = nextAttempt;
        lastError = truncate(reason);
    }

    public boolean hasSameContent(String customerId, String productCode) {
        return this.customerId.equals(customerId) && this.productCode.equals(productCode);
    }

    private void transitionTo(Status target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        status = target;
        updatedAt = now;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= LAST_ERROR_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getCustomerId() { return customerId; }
    public String getProductCode() { return productCode; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}