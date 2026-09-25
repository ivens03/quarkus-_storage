package storage.midia;

import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
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
import org.bson.types.ObjectId;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

@Path("/midias")
public class MidiaResource {

    @Inject
    MidiaStorage storage;

    /**
     * O Quarkus já grava o upload num arquivo temporário em disco (e apaga no fim da requisição),
     * então o conteúdo nunca fica inteiro na memória.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response enviar(@RestForm("arquivo") FileUpload arquivo) throws IOException {
        if (arquivo == null) {
            return erro(Status.BAD_REQUEST, "Envie o arquivo no campo 'arquivo' do formulário");
        }

        byte[] assinatura;
        try (InputStream in = Files.newInputStream(arquivo.uploadedFile())) {
            assinatura = in.readNBytes(TipoMidia.BYTES_ASSINATURA);
        }
        Optional<TipoMidia> tipo = TipoMidia.detectar(assinatura);
        if (tipo.isEmpty()) {
            return erro(Status.UNSUPPORTED_MEDIA_TYPE, "Só são aceitas imagens (JPEG, PNG, GIF, WebP, HEIC, HEIF, AVIF) e vídeos (MP4, MOV, 3GP, WebM)");
        }
        if (arquivo.size() > tipo.get().categoria.tamanhoMaximo) {
            return erro(Status.REQUEST_ENTITY_TOO_LARGE, "Arquivo maior que o limite para " + tipo.get().categoria);
        }

        String nome = arquivo.fileName() == null || arquivo.fileName().isBlank() ? "sem-nome" : arquivo.fileName();
        ObjectId id;
        try (InputStream in = Files.newInputStream(arquivo.uploadedFile())) {
            id = storage.salvar(nome, tipo.get(), in);
        }

        return Response.created(URI.create("/midias/" + id.toHexString()))
                .entity(new MidiaSalva(id.toHexString(), nome, tipo.get().contentType, arquivo.size()))
                .build();
    }

    /**
     * Sem Range devolve o arquivo inteiro (200). Com Range devolve só o trecho pedido (206):
     * o GridFS pula direto para o chunk onde o trecho começa, que é o que permite avançar um vídeo.
     */
    @GET
    @Path("/{id}")
    public Response baixar(@PathParam("id") String id, @HeaderParam("Range") String range) {
        Optional<GridFSFile> encontrado = converterId(id).flatMap(storage::buscar);
        if (encontrado.isEmpty()) {
            return erro(Status.NOT_FOUND, "Mídia não encontrada");
        }
        GridFSFile arquivo = encontrado.get();
        long total = arquivo.getLength();

        IntervaloBytes intervalo;
        if (range == null) {
            intervalo = IntervaloBytes.inteiro(total);
        } else {
            Optional<IntervaloBytes> pedido = IntervaloBytes.ler(range, total);
            if (pedido.isEmpty()) {
                return Response.status(Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + total)
                        .build();
            }
            intervalo = pedido.get();
        }

        // Roda depois que o método retorna, enquanto a resposta é enviada ao cliente
        StreamingOutput corpo = saida -> {
            try (GridFSDownloadStream in = storage.abrir(arquivo.getObjectId())) {
                in.skipNBytes(intervalo.inicio());
                copiar(in, saida, intervalo.tamanho());
            }
        };

        Response.ResponseBuilder resposta = Response.status(range == null ? Status.OK : Status.PARTIAL_CONTENT)
                .entity(corpo)
                .type(contentType(arquivo))
                .header("Accept-Ranges", "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, intervalo.tamanho());
        if (range != null) {
            resposta.header("Content-Range", "bytes %d-%d/%d".formatted(intervalo.inicio(), intervalo.fim(), total));
        }
        return resposta.build();
    }

    @DELETE
    @Path("/{id}")
    public Response apagar(@PathParam("id") String id) {
        boolean apagou = converterId(id).map(storage::apagar).orElse(false);
        return apagou ? Response.noContent().build() : erro(Status.NOT_FOUND, "Mídia não encontrada");
    }

    private static Optional<ObjectId> converterId(String id) {
        return ObjectId.isValid(id) ? Optional.of(new ObjectId(id)) : Optional.empty();
    }

    private static String contentType(GridFSFile arquivo) {
        var metadados = arquivo.getMetadata();
        return metadados != null && metadados.containsKey("contentType")
                ? metadados.getString("contentType")
                : MediaType.APPLICATION_OCTET_STREAM;
    }

    private static void copiar(InputStream in, OutputStream saida, long quantidade) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long restante = quantidade;
        while (restante > 0) {
            int lidos = in.read(buffer, 0, (int) Math.min(buffer.length, restante));
            if (lidos == -1) {
                break;
            }
            saida.write(buffer, 0, lidos);
            restante -= lidos;
        }
    }

    private static Response erro(Status status, String mensagem) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("erro", mensagem))
                .build();
    }
}
