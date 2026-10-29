package com.robson.activation.application;

public class DuplicateRequestConflictException extends RuntimeException {

    public DuplicateRequestConflictException(String externalId) {
        super("O externalId " + externalId + " já foi usado em outra solicitação, com cliente ou produto diferente");
    }
}
