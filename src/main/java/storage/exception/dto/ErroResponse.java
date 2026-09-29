package storage.exception.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Corpo de toda resposta de erro da API: {"erro": "..."}. Usado só pelo GlobalExceptionHandler. */
@Schema(name = "Erro", description = "Formato único de toda resposta de erro da API.",
        requiredProperties = "erro")
public record ErroResponse(

        @Schema(description = "Mensagem legível explicando o motivo do erro.", example = "Mídia não encontrada")
        String erro) {
}
