package storage.midia;

/** Resposta do upload: o id é o "comprovante" que o cliente guarda para buscar o arquivo depois. */
public record MidiaSalva(String id, String nome, String contentType, long tamanho) {
}
