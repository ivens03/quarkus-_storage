package storage.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.Components;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import storage.exception.dto.ErroResponse;

/**
 * Dados gerais da documentação OpenAPI (Swagger): o que aparece no topo do Swagger UI
 * e os componentes reaproveitados pelos endpoints (cabeçalho de rastreio e erro 500).
 * A classe não tem código: só existe para carregar as anotações. Estende Application porque é
 * nela que o SmallRye OpenAPI procura o @OpenAPIDefinition; sem @ApplicationPath, as rotas não mudam.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Storage de Mídias",
                version = "1.0.0",
                description = """
                        API para guardar e servir imagens e vídeos. Os arquivos ficam no MongoDB (GridFS), \
                        divididos em pedaços de 255 KB, e nunca são carregados inteiros na memória.

                        ### Formatos aceitos
                        O formato é descoberto pelos **primeiros bytes do arquivo** (assinatura). \
                        A extensão do nome e o `Content-Type` enviados pelo cliente são ignorados.

                        | Categoria | Formato | Content-Type devolvido | Limite |
                        |-----------|---------|------------------------|--------|
                        | IMAGEM | JPEG | `image/jpeg` | 20 MB |
                        | IMAGEM | PNG | `image/png` | 20 MB |
                        | IMAGEM | GIF (87a e 89a) | `image/gif` | 20 MB |
                        | IMAGEM | WebP | `image/webp` | 20 MB |
                        | IMAGEM | HEIC (fotos do iPhone) | `image/heic` | 20 MB |
                        | IMAGEM | HEIF | `image/heif` | 20 MB |
                        | IMAGEM | AVIF | `image/avif` | 20 MB |
                        | VIDEO | MP4 | `video/mp4` | 2 GB |
                        | VIDEO | MOV (vídeos do iPhone) | `video/quicktime` | 2 GB |
                        | VIDEO | 3GP (Android antigos) | `video/3gpp` | 2 GB |
                        | VIDEO | WebM | `video/webm` | 2 GB |

                        ### Identificador
                        Cada mídia recebe um **UUID aleatório** no upload. Ele é o único jeito de buscar \
                        ou apagar o arquivo depois, então o cliente precisa guardá-lo.

                        ### Erros
                        Toda resposta de erro tem o mesmo corpo JSON: `{"erro": "mensagem"}`. \
                        Erros inesperados (500) nunca expõem detalhes internos; a causa fica só no log.

                        ### Rastreio
                        Toda resposta traz o cabeçalho `X-Request-Id`, o mesmo id que aparece em cada \
                        linha de log da requisição. Informe-o ao reportar um problema.
                        """),
        tags = @Tag(
                name = OpenApiDefinicao.TAG_MIDIAS,
                description = "Envio, download (inteiro ou por trechos) e remoção de imagens e vídeos"),
        components = @Components(
                headers = @Header(
                        name = OpenApiDefinicao.X_REQUEST_ID,
                        description = "Id curto (8 caracteres) gerado para a requisição. "
                                + "É o mesmo id que aparece em todas as linhas de log dela.",
                        schema = @Schema(type = SchemaType.STRING, minLength = 8, maxLength = 8,
                                example = "a1b2c3d4")),
                responses = @APIResponse(
                        name = OpenApiDefinicao.ERRO_INTERNO,
                        responseCode = "500",
                        description = "Erro não previsto no servidor (ex.: MongoDB fora do ar). "
                                + "A mensagem é sempre genérica; a causa real fica só no log, "
                                + "junto com o X-Request-Id.",
                        headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID),
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErroResponse.class),
                                examples = @ExampleObject(
                                        name = "erro-interno",
                                        value = "{\"erro\": \"Erro interno no servidor\"}")))))
public class OpenApiDefinicao extends Application {

    /** Nomes compartilhados entre esta definição e as anotações dos endpoints. */
    public static final String TAG_MIDIAS = "Mídias";
    public static final String X_REQUEST_ID = "X-Request-Id";
    public static final String ERRO_INTERNO = "ErroInterno";
}
