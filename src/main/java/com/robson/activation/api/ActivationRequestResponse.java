package com.robson.activation.api;

import com.robson.activation.domain.ActivationRequest;
import com.robson.activation.domain.Status;

import java.time.Instant;
import java.util.UUID;

public record ActivationRequestResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        Status status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ActivationRequestResponse from(ActivationRequest request) {
        return new ActivationRequestResponse(
                request.getId(),
                request.getExternalId(),
                request.getCustomerId(),
                request.getProductCode(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}