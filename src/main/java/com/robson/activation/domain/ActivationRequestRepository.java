package com.robson.activation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {

    Optional<ActivationRequest> findByExternalId(String externalId);

    @Query(value = """
            select * from activation_request
             where status = 'PENDING' and next_attempt_at <= :now
             order by next_attempt_at
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<ActivationRequest> findReadyToProcess(@Param("now") Instant now, @Param("limit") int limit);

    // SKIP LOCKED aqui também: duas instâncias reconciliando não pegam as mesmas linhas
    @Query(value = """
            select * from activation_request
             where status = 'PROCESSING' and updated_at < :cutoff
             for update skip locked
            """, nativeQuery = true)
    List<ActivationRequest> findStuckInProcessing(@Param("cutoff") Instant cutoff);
}
