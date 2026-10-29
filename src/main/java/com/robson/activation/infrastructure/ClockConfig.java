package com.robson.activation.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class ClockConfig {

    // O PostgreSQL guarda até microssegundos; sem cortar aqui, o POST devolveria uma data
    // com nanossegundos diferente da que o GET lê do banco
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}
