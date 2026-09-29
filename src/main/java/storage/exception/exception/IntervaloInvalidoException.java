package storage.exception.exception;

/** Range pedido é inválido ou está fora do arquivo; guarda o tamanho total para o cabeçalho Content-Range. */
public class IntervaloInvalidoException extends RuntimeException {

    private final long tamanhoTotal;

    public IntervaloInvalidoException(long tamanhoTotal) {
        super("Intervalo de bytes inválido ou fora do arquivo (tamanho: %d bytes)".formatted(tamanhoTotal));
        this.tamanhoTotal = tamanhoTotal;
    }

    public long tamanhoTotal() {
        return tamanhoTotal;
    }
}
