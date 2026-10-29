package com.robson.activation.api;

import com.robson.activation.application.ActivationRequestNotFoundException;
import com.robson.activation.application.ActivationRequestService;
import com.robson.activation.application.CreationResult;
import com.robson.activation.application.DuplicateRequestConflictException;
import com.robson.activation.domain.ActivationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivationRequestController.class)
@Import(SecurityConfig.class)
class ActivationRequestControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private static final String BODY_VALIDO = """
            {"externalId": "EXT-1", "customerId": "CUST-1", "productCode": "INTERNET-500"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivationRequestService service;

    // Nos testes não existe emissor de token; o jwt() abaixo já entrega o usuário autenticado
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void deveCriarSolicitacaoERetornar201ComLocation() throws Exception {
        var created = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
        when(service.create("EXT-1", "CUST-1", "INTERNET-500")).thenReturn(new CreationResult(created, true));

        mockMvc.perform(post("/api/v1/requests")
                        .with(escrita())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/requests/" + created.getId())))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-29T10:00:00Z"));
    }

    @Test
    void deveRetornar200ComRegistroExistenteQuandoRetry() throws Exception {
        var existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
        when(service.create("EXT-1", "CUST-1", "INTERNET-500")).thenReturn(new CreationResult(existing, false));

        mockMvc.perform(post("/api/v1/requests")
                        .with(escrita())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(existing.getId().toString()));
    }

    @Test
    void deveRetornar409QuandoMesmoExternalIdComConteudoDiferente() throws Exception {
        when(service.create("EXT-1", "CUST-1", "TV-PREMIUM"))
                .thenThrow(new DuplicateRequestConflictException("EXT-1"));

        mockMvc.perform(post("/api/v1/requests")
                        .with(escrita())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId": "EXT-1", "customerId": "CUST-1", "productCode": "TV-PREMIUM"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Solicitação duplicada"));
    }

    @Test
    void deveRetornar400ComOsCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(escrita())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId": "EXT 1", "productCode": "INTERNET-500"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'customerId')].message").value("Informe o cliente"))
                .andExpect(jsonPath("$.errors[?(@.field == 'externalId')].message").value("Use só letras, números, - ou _"))
                .andExpect(jsonPath("$.traceId").value(not(emptyOrNullString())));

        verify(service, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void deveRetornar400QuandoJsonMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(escrita())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ isso não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    @Test
    void deveRetornarSolicitacaoExistente() throws Exception {
        var existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500", NOW);
        when(service.getById(existing.getId())).thenReturn(existing);

        mockMvc.perform(get("/api/v1/requests/{id}", existing.getId()).with(leitura()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-1"));
    }

    @Test
    void deveRetornar404ComProblemDetailsQuandoSolicitacaoNaoExiste() throws Exception {
        var id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new ActivationRequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(leitura()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Solicitação não encontrada"))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests/" + id));
    }

    @Test
    void deveRetornar500SemExporDetalhesInternos() throws Exception {
        when(service.getById(any())).thenThrow(new IllegalStateException("falha no pool de conexões"));

        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()).with(leitura()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value(not(containsString("pool"))));
    }

    @Test
    void deveReaproveitarOCorrelationIdRecebido() throws Exception {
        var id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new ActivationRequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(leitura()).header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.traceId").value("abc-123"));
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());

        verify(service, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void deveRetornar403AoCriarComEscopoSoDeLeitura() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(leitura())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden());

        verify(service, never()).create(anyString(), anyString(), anyString());
    }

    @Test
    void deveRetornar403AoConsultarComEscopoSoDeEscrita() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()).with(escrita()))
                .andExpect(status().isForbidden());

        verify(service, never()).getById(any());
    }

    private static RequestPostProcessor escrita() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write"));
    }

    private static RequestPostProcessor leitura() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:read"));
    }
}
