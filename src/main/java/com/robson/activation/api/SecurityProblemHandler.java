package com.robson.activation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robson.activation.application.CorrelationId;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;

class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // O Spring continua cuidando do status e do WWW-Authenticate. Aqui só entra o corpo do erro
    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    SecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, ex);
        write(request, response, "unauthorized", "Não autenticado",
                "Envie um token de acesso válido no cabeçalho Authorization.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException, ServletException {
        bearerAccessDeniedHandler.handle(request, response, ex);
        write(request, response, "forbidden", "Acesso negado",
                "O token não tem permissão para esta operação.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       String type, String title, String detail) throws IOException {
        var problem = ApiProblems.of(HttpStatusCode.valueOf(response.getStatus()), type, title, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", CorrelationId.current());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
