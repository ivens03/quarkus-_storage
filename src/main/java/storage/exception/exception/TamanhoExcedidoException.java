package storage.exception.exception;

import storage.model.Categoria;

public class TamanhoExcedidoException extends RuntimeException {

    public TamanhoExcedidoException(Categoria categoria) {
        super("Arquivo maior que o limite de %d MB para %s"
                .formatted(categoria.tamanhoMaximo / (1024 * 1024), categoria));
    }
}
