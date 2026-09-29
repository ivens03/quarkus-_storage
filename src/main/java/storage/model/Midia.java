package storage.model;

/** Uma mídia guardada no storage, do jeito que o sistema a enxerga por dentro. */
public record Midia(String id, String nome, String contentType, long tamanho) {
}
