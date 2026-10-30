package com.robson.activation.api;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.robson.activation.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "activation.worker.enabled=false")
@Import(TestcontainersConfiguration.class)
class JwtValidationIT {

    private static final String ISSUER = "https://emissor.teste";
    private static final String AUDIENCE = "activation-service";
    private static final RSAKey CHAVE = gerarChave("chave-do-emissor");

    @RegisterExtension
    static WireMockExtension emissor = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configurarEmissor(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> emissor.baseUrl() + "/jwks");
    }

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void publicarChaves() {
        emissor.stubFor(get("/jwks").willReturn(okJson(new JWKSet(CHAVE.toPublicJWK()).toString())));
    }

    @Test
    void deveAceitarTokenValido() throws JOSEException {
        var token = assinar(CHAVE, claims().build());

        // passou pela segurança e chegou ao controller: o id não existe
        assertThat(consultar(token)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deveRecusarTokenDeOutraAudiencia() throws JOSEException {
        var token = assinar(CHAVE, claims().audience("outra-api").build());

        assertThat(consultar(token)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRecusarTokenDeOutroEmissor() throws JOSEException {
        var token = assinar(CHAVE, claims().issuer("https://outro-emissor").build());

        assertThat(consultar(token)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRecusarTokenExpirado() throws JOSEException {
        var token = assinar(CHAVE, claims().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build());

        assertThat(consultar(token)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveRecusarTokenAssinadoComOutraChave() throws JOSEException {
        var token = assinar(gerarChave("chave-do-emissor"), claims().build());

        assertThat(consultar(token)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpStatusCode consultar(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange("/api/v1/requests/{id}", HttpMethod.GET, new HttpEntity<>(headers), String.class,
                UUID.randomUUID()).getStatusCode();
    }

    private static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("sistema-origem")
                .audience(AUDIENCE)
                .claim("scope", "activation:read")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String assinar(RSAKey chave, JWTClaimsSet claims) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(chave.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(chave));
        return jwt.serialize();
    }

    private static RSAKey gerarChave(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
