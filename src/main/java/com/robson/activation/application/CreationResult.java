package com.robson.activation.application;

import com.robson.activation.domain.ActivationRequest;

public record CreationResult(ActivationRequest request, boolean created) {

    static CreationResult created(ActivationRequest request) {
        return new CreationResult(request, true);
    }

    static CreationResult existing(ActivationRequest request) {
        return new CreationResult(request, false);
    }
}
