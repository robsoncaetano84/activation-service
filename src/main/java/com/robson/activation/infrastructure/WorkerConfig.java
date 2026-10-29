package com.robson.activation.infrastructure;

import com.robson.activation.application.ActivationWorkerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ActivationWorkerProperties.class)
public class WorkerConfig {
}
