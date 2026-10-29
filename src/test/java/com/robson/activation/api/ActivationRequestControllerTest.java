package com.robson.activation.api;

import com.robson.activation.application.ActivationRequestService;
import com.robson.activation.domain.ActivationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivationRequestController.class)
class ActivationRequestControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivationRequestService service;

    @Test
    void deveCriarSolicitacaoERetornar201ComLocation() throws Exception {
        var created = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
        when(service.create("EXT-1", "CUST-1", "INTERNET-500")).thenReturn(created);

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId": "EXT-1", "customerId": "CUST-1", "productCode": "INTERNET-500"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/requests/" + created.getId())))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-29T10:00:00Z"));
    }

    @Test
    void deveRetornar400QuandoCampoObrigatorioAusente() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId": "EXT-1", "productCode": "INTERNET-500"}
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void deveRetornarSolicitacaoExistente() throws Exception {
        var existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
        when(service.findById(existing.getId())).thenReturn(Optional.of(existing));

        mockMvc.perform(get("/api/v1/requests/{id}", existing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-1"));
    }

    @Test
    void deveRetornar404QuandoSolicitacaoNaoExiste() throws Exception {
        when(service.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}