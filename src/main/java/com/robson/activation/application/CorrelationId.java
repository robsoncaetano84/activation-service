package com.robson.activation.application;

import org.slf4j.MDC;

public final class CorrelationId {

    public static final String MDC_KEY = "traceId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
