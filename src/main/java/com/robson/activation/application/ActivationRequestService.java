package com.robson.activation.application;

import com.robson.activation.domain.ActivationRequest;
import com.robson.activation.domain.ActivationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ActivationRequestService {

    private final ActivationRequestRepository repository;
    private final Clock clock;

    public ActivationRequestService(ActivationRequestRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public CreationResult create(String externalId, String customerId, String productCode) {
        var existing = repository.findByExternalId(externalId);
        if (existing.isPresent()) {
            return resolveDuplicate(existing.get(), customerId, productCode);
        }

        try {
            var request = ActivationRequest.create(externalId, customerId, productCode, clock.instant());
            return CreationResult.created(repository.saveAndFlush(request));
        } catch (DataIntegrityViolationException ex) {
            // Outra requisição com o mesmo externalId foi gravada entre a busca e o INSERT
            var winner = repository.findByExternalId(externalId).orElseThrow(() -> ex);
            return resolveDuplicate(winner, customerId, productCode);
        }
    }

    @Transactional(readOnly = true)
    public ActivationRequest getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }

    private CreationResult resolveDuplicate(ActivationRequest existing, String customerId, String productCode) {
        if (existing.hasSameContent(customerId, productCode)) {
            return CreationResult.existing(existing);
        }
        throw new DuplicateRequestConflictException(existing.getExternalId());
    }
}
