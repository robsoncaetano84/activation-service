package com.robson.activation.application;

import java.util.UUID;

public class ActivationRequestNotFoundException extends RuntimeException {

    public ActivationRequestNotFoundException(UUID id) {
        super("Não encontrada solicitação com o id: " + id);
    }
}
