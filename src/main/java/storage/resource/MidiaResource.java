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
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import storage.dto.MidiaSalvaResponse;
import storage.exception.exception.ArquivoAusenteException;
import storage.exception.exception.IntervaloInvalidoException;
import storage.model.Midia;
import storage.service.MidiaService;
import storage.util.Bytes;
import storage.util.IntervaloBytes;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/** Camada HTTP: recebe a requisição, delega ao service e monta a resposta. Erros viram exceções (ver GlobalExceptionHandler). */
@Path("/midias")
public class MidiaResource {

    @Inject
    MidiaService service;

    /**
     * O Quarkus já grava o upload num arquivo temporário em disco (e apaga no fim da requisição),
     * então o conteúdo nunca fica inteiro na memória.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
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
    public Response baixar(@PathParam("id") String id, @HeaderParam("Range") String range) {
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
    public void apagar(@PathParam("id") String id) {
        service.apagar(id);
    }
}
