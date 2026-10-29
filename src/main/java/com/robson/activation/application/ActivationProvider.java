package com.robson.activation.application;

import com.robson.activation.domain.ActivationRequest;

public interface ActivationProvider {

    ProviderResult activate(ActivationRequest request);
}
