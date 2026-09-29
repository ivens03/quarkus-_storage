package storage.filter;

import io.vertx.ext.web.RoutingContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import storage.config.LogPadrao;

/**
 * Aplica o padrão de log (LogPadrao) a toda requisição HTTP: abre o rastreio, registra o início
 * e registra o fim com status e duração. O id de rastreio volta para o cliente no cabeçalho X-Request-Id.
 */
public class LogRequisicaoFilter {

    private static final String CABECALHO_ID = "X-Request-Id";
    private static final LogPadrao LOG = LogPadrao.de(LogRequisicaoFilter.class);

    // preMatching: roda antes de o Quarkus procurar o método, então registra também rotas inexistentes
    @ServerRequestFilter(preMatching = true)
    public void registrar(RoutingContext contexto) {
        String id = LogPadrao.abrirRastreio();
        String requisicao = contexto.request().method() + " " + contexto.request().uri();
        contexto.response().putHeader(CABECALHO_ID, id);
        long inicio = LOG.inicio(requisicao);

        // Chamado quando o último byte da resposta sai: a duração de um download inclui o envio
        contexto.addEndHandler(fim -> {
            LogPadrao.continuarRastreio(id);
            LOG.fim(requisicao, contexto.response().getStatusCode(), inicio);
            LogPadrao.fecharRastreio();
        });
    }
}
