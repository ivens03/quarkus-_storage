package storage.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Encoding;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import storage.config.OpenApiDefinicao;
import storage.dto.MidiaSalvaResponse;
import storage.exception.dto.ErroResponse;
import storage.exception.exception.ArquivoAusenteException;
import storage.exception.exception.IntervaloInvalidoException;
import storage.model.Midia;
import storage.service.MidiaService;
import storage.util.Bytes;
import storage.util.IntervaloBytes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Path("/midias")
@Tag(name = OpenApiDefinicao.TAG_MIDIAS)
public class MidiaResource {

    private static final String ID_EXEMPLO = "3f2b8c1e-9a4d-4e6f-b7a1-2c5d8e9f0a1b";

    @Inject
    MidiaService service;

    /**
     * O Quarkus já grava o upload num arquivo temporário em disco (e apaga no fim da requisição),
     * então o conteúdo nunca fica inteiro na memória.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "enviarMidia",
            summary = "Envia uma imagem ou um vídeo",
            description = """
                    Recebe um arquivo por `multipart/form-data` no campo **`arquivo`** e grava no storage.

                    **Como o arquivo é validado**
                    1. O formato é descoberto pelos **12 primeiros bytes** do conteúdo (assinatura do formato). \
                    O nome do arquivo, a extensão e o `Content-Type` da parte são ignorados: um `.exe` renomeado \
                    para `.jpg` é recusado, e uma foto sem extensão é aceita.
                    2. O tamanho é conferido contra o limite da categoria: **20 MB para imagens** e \
                    **2 GB para vídeos**.

                    **O que é gravado**
                    - Id: um UUID aleatório gerado pelo servidor (não dá para adivinhar ids de outros arquivos).
                    - Nome: `<id>.<extensão do tipo real>`; o nome original não é guardado.
                    - Conteúdo: dividido em pedaços de 255 KB no GridFS, sem passar inteiro pela memória.

                    A resposta traz o id no corpo e a URL de download no cabeçalho `Location`. \
                    **Guarde o id**: não existe listagem nem busca por nome.""")
    @RequestBody(
            required = true,
            description = "Formulário com um único campo, `arquivo`, contendo o conteúdo binário da mídia.",
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA,
                    schema = @Schema(implementation = FormularioEnvio.class),
                    encoding = @Encoding(name = "arquivo", contentType = "application/octet-stream")))
    @APIResponse(
            responseCode = "201",
            description = "Mídia gravada. O corpo traz o id, o tipo real detectado e o tamanho em bytes.",
            headers = {
                    @Header(name = HttpHeaders.LOCATION,
                            description = "URL absoluta para baixar a mídia recém-criada (`GET /midias/{id}`).",
                            schema = @Schema(type = SchemaType.STRING, format = "uri",
                                    example = "http://localhost:1818/midias/" + ID_EXEMPLO)),
                    @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID)
            },
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = MidiaSalvaResponse.class),
                    examples = {
                            @ExampleObject(name = "imagem", summary = "Foto PNG",
                                    value = "{\"id\": \"" + ID_EXEMPLO + "\", \"contentType\": \"image/png\", "
                                            + "\"tamanho\": 48213}"),
                            @ExampleObject(name = "video", summary = "Vídeo MOV gravado no iPhone",
                                    value = "{\"id\": \"b8e1d2c3-4f5a-4b6c-9d7e-8f9a0b1c2d3e\", "
                                            + "\"contentType\": \"video/quicktime\", \"tamanho\": 157286400}")
                    }))
    @APIResponse(
            responseCode = "400",
            description = "O formulário não tem o campo `arquivo` (nome do campo diferente ou campo ausente).",
            headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID),
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(name = "arquivo-ausente",
                            value = "{\"erro\": \"Envie o arquivo no campo 'arquivo' do formulário\"}")))
    @APIResponse(
            responseCode = "413",
            description = """
                    Arquivo maior que o permitido. Acontece em dois pontos:
                    - **Limite da categoria** (20 MB para imagem, 2 GB para vídeo): responde com o corpo JSON padrão.
                    - **Limite geral do servidor** (`quarkus.http.limits.max-body-size`, 2 GB): a requisição é \
                    cortada antes de chegar à aplicação e a resposta pode vir sem corpo.""",
            headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID),
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErroResponse.class),
                    examples = {
                            @ExampleObject(name = "imagem-grande", summary = "Imagem acima de 20 MB",
                                    value = "{\"erro\": \"Arquivo maior que o limite de 20 MB para IMAGEM\"}"),
                            @ExampleObject(name = "video-grande", summary = "Vídeo acima de 2 GB",
                                    value = "{\"erro\": \"Arquivo maior que o limite de 2048 MB para VIDEO\"}")
                    }))
    @APIResponse(
            responseCode = "415",
            description = """
                    Formato não aceito. Acontece em dois casos:
                    - O **conteúdo** do arquivo não bate com nenhum formato da lista branca \
                    (inclusive arquivo vazio ou com menos de 12 bytes).
                    - A requisição não foi enviada como `multipart/form-data`.""",
            headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID),
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErroResponse.class),
                    examples = {
                            @ExampleObject(name = "formato-nao-aceito", summary = "Conteúdo não é imagem nem vídeo",
                                    value = "{\"erro\": \"Só são aceitas imagens (JPEG, PNG, GIF, WebP, HEIC, HEIF, "
                                            + "AVIF) e vídeos (MP4, MOV, 3GP, WebM)\"}"),
                            @ExampleObject(name = "nao-multipart", summary = "Corpo enviado sem multipart/form-data",
                                    value = "{\"erro\": \"HTTP 415 Unsupported Media Type\"}")
                    }))
    @APIResponse(ref = OpenApiDefinicao.ERRO_INTERNO, responseCode = "500")
    public Response enviar(@RestForm("arquivo") FileUpload arquivo) throws IOException {
        if (arquivo == null) {
            throw new ArquivoAusenteException();
        }
        Midia midia = service.salvar(arquivo.uploadedFile());
        return Response.created(URI.create("/midias/" + midia.id()))
                .entity(MidiaSalvaResponse.de(midia))
                .build();
    }

    /**
     * Sem Range devolve o arquivo inteiro (200). Com Range devolve só o trecho pedido (206):
     * o GridFS pula direto para o chunk onde o trecho começa, que é o que permite avançar um vídeo.
     */
    @GET
    @Path("/{id}")
    @Operation(
            operationId = "baixarMidia",
            summary = "Baixa uma mídia, inteira ou um trecho",
            description = """
                    Devolve o conteúdo binário da mídia com o `Content-Type` real detectado no upload \
                    (ex.: `image/png`, `video/mp4`). O arquivo é enviado em streaming, sem ser carregado \
                    inteiro na memória.

                    **Download inteiro** — sem o cabeçalho `Range`: responde **200** com o arquivo completo.

                    **Download parcial** — com o cabeçalho `Range`: responde **206** só com o trecho pedido. \
                    É o que permite a um player de vídeo (`<video>` do navegador, ExoPlayer, AVPlayer) \
                    avançar para qualquer ponto sem baixar o que vem antes, e a um download interrompido \
                    continuar de onde parou. O servidor anuncia o suporte com `Accept-Ranges: bytes`.

                    Formatos de `Range` aceitos (um único intervalo; início e fim são inclusivos e contam a partir de 0):

                    | Range | Significado |
                    |-------|-------------|
                    | `bytes=0-1023` | do byte 0 ao 1023 (os primeiros 1024 bytes) |
                    | `bytes=1048576-` | do byte 1048576 até o fim |
                    | `bytes=-500` | os últimos 500 bytes |

                    Um fim além do tamanho do arquivo é ajustado para o último byte. Vários intervalos \
                    (`bytes=0-10,20-30`), unidades diferentes de `bytes` ou início depois do fim do arquivo \
                    resultam em **416**.""")
    @APIResponse(
            responseCode = "200",
            description = "Arquivo inteiro (requisição sem `Range`).",
            headers = {
                    @Header(name = HttpHeaders.CONTENT_TYPE,
                            description = "Tipo real da mídia, detectado no upload.",
                            schema = @Schema(type = SchemaType.STRING, example = "video/mp4")),
                    @Header(name = HttpHeaders.CONTENT_LENGTH,
                            description = "Tamanho total do arquivo, em bytes.",
                            schema = @Schema(type = SchemaType.INTEGER, format = "int64", example = "157286400")),
                    @Header(name = "Accept-Ranges",
                            description = "Indica que é possível pedir trechos do arquivo pelo cabeçalho `Range`.",
                            schema = @Schema(type = SchemaType.STRING, enumeration = "bytes")),
                    @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID)
            },
            content = {
                    @Content(mediaType = "image/*", schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = "video/*", schema = @Schema(type = SchemaType.STRING, format = "binary"))
            })
    @APIResponse(
            responseCode = "206",
            description = "Só o trecho pedido no `Range`.",
            headers = {
                    @Header(name = HttpHeaders.CONTENT_TYPE,
                            description = "Tipo real da mídia, detectado no upload.",
                            schema = @Schema(type = SchemaType.STRING, example = "video/mp4")),
                    @Header(name = HttpHeaders.CONTENT_LENGTH,
                            description = "Tamanho do trecho devolvido (não do arquivo inteiro), em bytes.",
                            schema = @Schema(type = SchemaType.INTEGER, format = "int64", example = "1024")),
                    @Header(name = "Content-Range",
                            description = "Posição do trecho e tamanho total do arquivo: `bytes <início>-<fim>/<total>`.",
                            schema = @Schema(type = SchemaType.STRING, pattern = "^bytes \\d+-\\d+/\\d+$",
                                    example = "bytes 0-1023/157286400")),
                    @Header(name = "Accept-Ranges",
                            description = "Indica que é possível pedir trechos do arquivo pelo cabeçalho `Range`.",
                            schema = @Schema(type = SchemaType.STRING, enumeration = "bytes")),
                    @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID)
            },
            content = {
                    @Content(mediaType = "image/*", schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = "video/*", schema = @Schema(type = SchemaType.STRING, format = "binary"))
            })
    @APIResponse(
            responseCode = "404",
            description = "Não existe mídia com esse id (nunca existiu, foi apagada ou o id não é um UUID válido).",
            headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID),
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(name = "nao-encontrada", value = "{\"erro\": \"Mídia não encontrada\"}")))
    @APIResponse(
            responseCode = "416",
            description = """
                    O `Range` é inválido ou está fora do arquivo. O cabeçalho `Content-Range` informa \
                    o tamanho real (`bytes */<total>`), para o cliente refazer o pedido.""",
            headers = {
                    @Header(name = "Content-Range",
                            description = "Tamanho total do arquivo, no formato `bytes */<total>`.",
                            schema = @Schema(type = SchemaType.STRING, pattern = "^bytes \\*/\\d+$",
                                    example = "bytes */157286400")),
                    @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID)
            },
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(name = "intervalo-invalido",
                            value = "{\"erro\": \"Intervalo de bytes inválido ou fora do arquivo "
                                    + "(tamanho: 157286400 bytes)\"}")))
    @APIResponse(ref = OpenApiDefinicao.ERRO_INTERNO, responseCode = "500")
    public Response baixar(
            @Parameter(
                    description = "Id da mídia, devolvido no upload.",
                    required = true,
                    schema = @Schema(type = SchemaType.STRING, format = "uuid"),
                    example = ID_EXEMPLO)
            @PathParam("id") String id,
            @Parameter(
                    description = "Trecho do arquivo a devolver (um único intervalo em bytes). "
                            + "Sem ele o arquivo vem inteiro.",
                    required = false,
                    schema = @Schema(type = SchemaType.STRING, pattern = "^bytes=\\d{0,18}-\\d{0,18}$"),
                    examples = {
                            @ExampleObject(name = "primeiros-1kb", summary = "Primeiros 1024 bytes",
                                    value = "bytes=0-1023"),
                            @ExampleObject(name = "a-partir-de-1mb", summary = "Do byte 1048576 até o fim",
                                    value = "bytes=1048576-"),
                            @ExampleObject(name = "ultimos-500", summary = "Últimos 500 bytes",
                                    value = "bytes=-500")
                    })
            @HeaderParam("Range") String range) {
        Midia midia = service.buscar(id);
        long total = midia.tamanho();

        IntervaloBytes intervalo = range == null
                ? IntervaloBytes.inteiro(total)
                : IntervaloBytes.ler(range, total).orElseThrow(() -> new IntervaloInvalidoException(total));

        // Roda depois que o método retorna, enquanto a resposta é enviada ao cliente
        StreamingOutput corpo = saida -> {
            try (InputStream conteudo = service.abrirConteudo(midia)) {
                conteudo.skipNBytes(intervalo.inicio());
                Bytes.copiar(conteudo, saida, intervalo.tamanho());
            }
        };

        Response.ResponseBuilder resposta = Response.status(range == null ? Status.OK : Status.PARTIAL_CONTENT)
                .entity(corpo)
                .type(midia.contentType())
                .header("Accept-Ranges", "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, intervalo.tamanho());
        if (range != null) {
            resposta.header("Content-Range", "bytes %d-%d/%d".formatted(intervalo.inicio(), intervalo.fim(), total));
        }
        return resposta.build();
    }

    /** Método void responde 204 No Content. */
    @DELETE
    @Path("/{id}")
    @Operation(
            operationId = "apagarMidia",
            summary = "Apaga uma mídia",
            description = """
                    Remove a mídia do storage de forma **definitiva**: a ficha do arquivo e todos os seus \
                    pedaços no GridFS. Não há lixeira nem como desfazer.

                    Depois de apagada, qualquer `GET` ou `DELETE` com o mesmo id responde **404**.""")
    @APIResponse(
            responseCode = "204",
            description = "Mídia apagada. A resposta não tem corpo.",
            headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID))
    @APIResponse(
            responseCode = "404",
            description = "Não existe mídia com esse id (nunca existiu, já foi apagada ou o id não é um UUID válido).",
            headers = @Header(name = OpenApiDefinicao.X_REQUEST_ID, ref = OpenApiDefinicao.X_REQUEST_ID),
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(name = "nao-encontrada", value = "{\"erro\": \"Mídia não encontrada\"}")))
    @APIResponse(ref = OpenApiDefinicao.ERRO_INTERNO, responseCode = "500")
    public void apagar(
            @Parameter(
                    description = "Id da mídia a apagar, devolvido no upload.",
                    required = true,
                    schema = @Schema(type = SchemaType.STRING, format = "uuid"),
                    example = ID_EXEMPLO)
            @PathParam("id") String id) {
        service.apagar(id);
    }

    /** Só descreve o formulário do upload no Swagger; o método recebe o arquivo como FileUpload. */
    @Schema(name = "FormularioEnvio", requiredProperties = "arquivo")
    static class FormularioEnvio {

        @Schema(type = SchemaType.STRING, format = "binary",
                description = "Conteúdo do arquivo. Imagem (JPEG, PNG, GIF, WebP, HEIC, HEIF, AVIF) até 20 MB "
                        + "ou vídeo (MP4, MOV, 3GP, WebM) até 2 GB.")
        public String arquivo;
    }
}
