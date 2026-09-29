package storage.exception.dto;

/** Corpo de toda resposta de erro da API: {"erro": "..."}. Usado só pelo GlobalExceptionHandler. */
public record ErroResponse(String erro) {
}
