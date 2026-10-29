package com.robson.activation.infrastructure;

import com.robson.activation.application.ActivationProvider;
import com.robson.activation.application.ProviderResult;
import com.robson.activation.application.ProviderResult.PermanentFailure;
import com.robson.activation.application.ProviderResult.Success;
import com.robson.activation.application.ProviderResult.TransientFailure;
import com.robson.activation.domain.ActivationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class HttpActivationProvider implements ActivationProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpActivationProvider.class);
    private static final String ACTIVATE_PATH = "/provider/v1/activate";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public HttpActivationProvider(RestClient.Builder builder, ProviderProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ProviderResult activate(ActivationRequest request) {
        try {
            return restClient.post()
                    .uri(ACTIVATE_PATH)
                    .header("Idempotency-Key", request.getId().toString())
                    .headers(headers -> {
                        if (request.getTraceId() != null) {
                            headers.set(CORRELATION_HEADER, request.getTraceId());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ActivatePayload(request.getId(), request.getCustomerId(), request.getProductCode()))
                    .exchange((httpRequest, response) -> toResult(response.getStatusCode(), response.getHeaders()));
        } catch (ResourceAccessException ex) {
            log.warn("Sem resposta do provedor para a solicitação {}: {}", request.getId(), ex.getMessage());
            return new TransientFailure("O provedor não respondeu a tempo", null);
        }
    }

    private ProviderResult toResult(HttpStatusCode status, HttpHeaders headers) {
        if (status.is2xxSuccessful()) {
            return new Success();
        }
        var code = status.value();
        if (code == 429 || code == 503) {
            return new TransientFailure("Provedor sobrecarregado (HTTP " + code + ")", retryAfter(headers));
        }
        if (status.is5xxServerError()) {
            return new TransientFailure("Erro no provedor (HTTP " + code + ")", null);
        }
        return new PermanentFailure("O provedor recusou a ativação (HTTP " + code + ")");
    }

    private Duration retryAfter(HttpHeaders headers) {
        var value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ActivatePayload(UUID requestId, String customerId, String productCode) {
    }
}
