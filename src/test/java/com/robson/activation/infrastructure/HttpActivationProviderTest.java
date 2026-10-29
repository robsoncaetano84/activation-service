package com.robson.activation.infrastructure;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.robson.activation.application.ProviderResult.PermanentFailure;
import com.robson.activation.application.ProviderResult.Success;
import com.robson.activation.application.ProviderResult.TransientFailure;
import com.robson.activation.domain.ActivationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class HttpActivationProviderTest {

    private static final String PATH = "/provider/v1/activate";

    @RegisterExtension
    static WireMockExtension provider = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final ActivationRequest request =
            ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", "trace-1", Instant.parse("2026-10-29T10:00:00Z"));

    private HttpActivationProvider client;

    @BeforeEach
    void setUp() {
        var properties = new ProviderProperties(
                URI.create(provider.baseUrl()), Duration.ofSeconds(1), Duration.ofSeconds(1));
        client = new HttpActivationProvider(RestClient.builder(), properties);
    }

    @Test
    void deveRetornarSucessoEEnviarChaveDeIdempotenciaECorrelationId() {
        provider.stubFor(post(PATH).willReturn(ok()));

        var result = client.activate(request);

        assertThat(result).isInstanceOf(Success.class);
        provider.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Idempotency-Key", equalTo(request.getId().toString()))
                .withHeader("X-Correlation-Id", equalTo("trace-1"))
                .withRequestBody(matchingJsonPath("$.customerId", equalTo("CUST-1")))
                .withRequestBody(matchingJsonPath("$.productCode", equalTo("INTERNET-500"))));
    }

    @Test
    void deveTratar400ComoFalhaDefinitiva() {
        provider.stubFor(post(PATH).willReturn(badRequest()));

        assertThat(client.activate(request)).isInstanceOf(PermanentFailure.class);
    }

    @Test
    void deveTratar500ComoFalhaTemporaria() {
        provider.stubFor(post(PATH).willReturn(serverError()));

        var result = client.activate(request);

        assertThat(result).isInstanceOf(TransientFailure.class);
        assertThat(((TransientFailure) result).retryAfter()).isNull();
    }

    @Test
    void deveRespeitarRetryAfterQuandoProvedorSobrecarregado() {
        provider.stubFor(post(PATH).willReturn(serviceUnavailable().withHeader("Retry-After", "30")));

        var result = client.activate(request);

        assertThat(result).isInstanceOf(TransientFailure.class);
        assertThat(((TransientFailure) result).retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void deveTratarTimeoutComoFalhaTemporaria() {
        provider.stubFor(post(PATH).willReturn(ok().withFixedDelay(3_000)));

        var result = client.activate(request);

        assertThat(result).isInstanceOf(TransientFailure.class);
    }
}
