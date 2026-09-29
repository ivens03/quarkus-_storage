package storage.config;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.UUID;

public final class LogPadrao {

    public static final String CHAVE_RASTREIO = "requisicao";

    private final Logger logger;

    private LogPadrao(Class<?> origem) {
        this.logger = Logger.getLogger(origem);
    }

    /** A classe informada aparece em cada linha, mostrando por onde a operação passou. */
    public static LogPadrao de(Class<?> origem) {
        return new LogPadrao(origem);
    }

    // ---------- Rastreio ----------

    /** Gera um id curto e o associa a tudo o que for logado daqui em diante nesta operação. */
    public static String abrirRastreio() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        continuarRastreio(id);
        return id;
    }

    /** Reassocia um id já existente, para quando a operação continua em outro ponto (ex.: um callback). */
    public static void continuarRastreio(String id) {
        MDC.put(CHAVE_RASTREIO, id);
    }

    public static void fecharRastreio() {
        MDC.remove(CHAVE_RASTREIO);
    }

    /** Registra o início e devolve o instante, que deve ser passado ao fim() para calcular a duração. */
    public long inicio(String operacao) {
        logger.infof("Início %s", operacao);
        return System.nanoTime();
    }

    public void fim(String operacao, Object resultado, long inicio) {
        logger.infof("Fim %s -> %s em %d ms", operacao, resultado, (System.nanoTime() - inicio) / 1_000_000);
    }

    // ---------- Mensagens ----------

    public void info(String formato, Object... parametros) {
        logger.infof(formato, parametros);
    }

    /** Situação prevista que merece atenção (ex.: requisição recusada). Sem stack trace. */
    public void aviso(String formato, Object... parametros) {
        logger.warnf(formato, parametros);
    }

    /** Falha não prevista: sempre com a exceção, para o stack trace ir para o log. */
    public void erro(String mensagem, Throwable causa) {
        logger.error(mensagem, causa);
    }
}
