package com.robson.activation.domain;

public class InvalidStatusTransitionException extends RuntimeException {

    private final Status from;
    private final Status to;

    public InvalidStatusTransitionException(Status from, Status to) {
        super("Transição de status inválida. Status de: " + from + " para: " + to);
        this.from = from;
        this.to = to;
    }

    public Status getFrom() { return from; }
    public Status getTo() { return to; }
}