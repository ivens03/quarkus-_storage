package storage.model;

/** Grupo de formatos, com o tamanho máximo aceito para cada um. */
public enum Categoria {

    IMAGEM(20L * 1024 * 1024),          // 20 MB
    VIDEO(2L * 1024 * 1024 * 1024);     // 2 GB

    public final long tamanhoMaximo;

    Categoria(long tamanhoMaximo) {
        this.tamanhoMaximo = tamanhoMaximo;
    }
}
