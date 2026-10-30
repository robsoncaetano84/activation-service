package com.robson.activation.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class ClockConfig {

    // O Postgres só guarda até microssegundos. Sem cortar aqui, o POST e o GET mostram datas diferentes
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}
