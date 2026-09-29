package storage.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import storage.model.Midia;

/** Resposta do upload: o id é o "comprovante" que o cliente guarda para buscar o arquivo depois. */
@Schema(name = "MidiaSalva",
        description = "Dados da mídia gravada. O id é o único jeito de baixar ou apagar o arquivo depois.",
        requiredProperties = {"id", "contentType", "tamanho"})
public record MidiaSalvaResponse(

        @Schema(description = "Identificador da mídia (UUID aleatório gerado pelo servidor).",
                format = "uuid", example = "3f2b8c1e-9a4d-4e6f-b7a1-2c5d8e9f0a1b")
        String id,

        @Schema(description = "Tipo real do arquivo, descoberto pelos primeiros bytes do conteúdo "
                + "(nunca pelo nome nem pelo Content-Type enviado). É o Content-Type devolvido no download.",
                enumeration = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif",
                        "image/avif", "video/mp4", "video/quicktime", "video/3gpp", "video/webm"},
                example = "image/png")
        String contentType,

        @Schema(description = "Tamanho do arquivo gravado, em bytes.", minimum = "1", example = "48213")
        long tamanho) {

    public static MidiaSalvaResponse de(Midia midia) {
        return new MidiaSalvaResponse(midia.id(), midia.contentType(), midia.tamanho());
    }
}
