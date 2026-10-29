package com.robson.activation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NewActivationRequest(

        @NotBlank(message = "Informe o identificador da solicitação no sistema de origem")
        @Size(max = 64, message = "Use no máximo 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use só letras, números, - ou _")
        String externalId,

        @NotBlank(message = "Informe o cliente")
        @Size(max = 64, message = "Use no máximo 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use só letras, números, - ou _")
        String customerId,

        @NotBlank(message = "Informe o produto a ser ativado")
        @Size(max = 64, message = "Use no máximo 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use só letras, números, - ou _")
        String productCode
) {
}