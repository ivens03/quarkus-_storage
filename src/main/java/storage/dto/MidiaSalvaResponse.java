package storage.dto;

import storage.model.Midia;

/** Resposta do upload: o id é o "comprovante" que o cliente guarda para buscar o arquivo depois. */
public record MidiaSalvaResponse(String id, String contentType, long tamanho) {

    public static MidiaSalvaResponse de(Midia midia) {
        return new MidiaSalvaResponse(midia.id(), midia.contentType(), midia.tamanho());
    }
}
