package com.robson.activation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "activation.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class HealthEndpointIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void deveResponderLivenessSemToken() {
        var response = rest.getForEntity("/actuator/health/liveness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void deveResponderReadinessComBancoDisponivel() {
        var response = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void naoDeveExporOutrosEndpointsDoActuator() {
        var response = rest.getForEntity("/actuator/env", String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
