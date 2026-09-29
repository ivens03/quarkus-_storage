package storage.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import storage.exception.dto.ErroResponse;
import storage.exception.exception.ArquivoAusenteException;
import storage.exception.exception.FormatoNaoAceitoException;
import storage.exception.exception.IntervaloInvalidoException;
import storage.exception.exception.MidiaNaoEncontradaException;
import storage.exception.exception.TamanhoExcedidoException;

/**
 * Único ponto da aplicação que transforma erro em resposta HTTP.
 * O Quarkus escolhe o método cujo parâmetro é o tipo mais específico da exceção lançada,
 * então Exception só é usado quando nenhum outro método serve.
 */
public class GlobalExceptionHandler {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    // ---------- Erros do storage ----------

    @ServerExceptionMapper
    public Response arquivoAusente(ArquivoAusenteException e) {
        return erro(Status.BAD_REQUEST, e.getMessage());
    }

    @ServerExceptionMapper
    public Response naoEncontrada(MidiaNaoEncontradaException e) {
        return erro(Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    public Response formatoNaoAceito(FormatoNaoAceitoException e) {
        return erro(Status.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ServerExceptionMapper
    public Response tamanhoExcedido(TamanhoExcedidoException e) {
        return erro(Status.REQUEST_ENTITY_TOO_LARGE, e.getMessage());
    }

    /** O HTTP pede o tamanho real do arquivo no Content-Range de uma resposta 416. */
    @ServerExceptionMapper
    public Response intervaloInvalido(IntervaloInvalidoException e) {
        return Response.status(Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header("Content-Range", "bytes */" + e.tamanhoTotal())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroResponse(e.getMessage()))
                .build();
    }

    // ---------- Erros do próprio Quarkus/JAX-RS ----------

    /**
     * Rota inexistente (404), método não permitido (405), Content-Type errado (415)...
     * Mantém o status e os cabeçalhos originais, só troca o corpo pelo formato padrão da API.
     */
    @ServerExceptionMapper
    public Response http(WebApplicationException e) {
        return Response.fromResponse(e.getResponse())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroResponse(e.getMessage()))
                .build();
    }

    // ---------- Qualquer outro erro ----------

    /** Erro não previsto: detalhes só no log, nunca na resposta (podem expor dados internos). */
    @ServerExceptionMapper
    public Response inesperado(Exception e) {
        LOG.error("Erro inesperado ao processar a requisição", e);
        return erro(Status.INTERNAL_SERVER_ERROR, "Erro interno no servidor");
    }

    private static Response erro(Status status, String mensagem) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErroResponse(mensagem))
                .build();
    }
}
