package com.robson.activation.api;

import com.robson.activation.application.ActivationRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {

    private final ActivationRequestService service;

    public ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody NewActivationRequest body,
                                                            UriComponentsBuilder uriBuilder) {
        var created = service.create(body.externalId(), body.customerId(), body.productCode());
        var location = uriBuilder.path("/api/v1/requests/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(ActivationRequestResponse.from(created));
    }

    @GetMapping("/{id}")
    public ActivationRequestResponse findById(@PathVariable UUID id) {
        return ActivationRequestResponse.from(service.getById(id));
    }
}