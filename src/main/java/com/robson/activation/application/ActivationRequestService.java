package com.robson.activation.application;

import com.robson.activation.domain.ActivationRequest;
import com.robson.activation.infrastructure.ActivationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class ActivationRequestService {

    private final ActivationRequestRepository repository;
    private final Clock clock;

    public ActivationRequestService(ActivationRequestRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ActivationRequest create(String externalId, String customerId, String productCode) {
        var request = ActivationRequest.create(externalId, customerId, productCode, clock.instant());
        return repository.save(request);
    }

    @Transactional(readOnly = true)
    public Optional<ActivationRequest> findById(UUID id) {
        return repository.findById(id);
    }
}