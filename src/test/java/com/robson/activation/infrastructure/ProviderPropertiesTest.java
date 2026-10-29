package com.robson.activation.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderConfig.class);

    @Test
    void naoDeveSubirSemOsTimeoutsDoProvedor() {
        runner.withPropertyValues("provider.base-url=http://localhost:8089")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .hasMessageContaining("provider"));
    }

    @Test
    void deveSubirComTudoConfigurado() {
        runner.withPropertyValues(
                        "provider.base-url=http://localhost:8089",
                        "provider.connect-timeout=2s",
                        "provider.read-timeout=5s")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ProviderProperties.class));
    }
}
