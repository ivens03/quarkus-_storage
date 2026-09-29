package storage.exception.exception;

public class ArquivoAusenteException extends RuntimeException {

    public ArquivoAusenteException() {
        super("Envie o arquivo no campo 'arquivo' do formulário");
    }
}
