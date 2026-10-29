package com.robson.activation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "activation.worker.enabled=false")
class ActivationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
