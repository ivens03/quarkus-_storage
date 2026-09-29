package storage.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static storage.util.Bytes.temEm;

/**
 * Lista branca dos formatos aceitos pelo storage.
 * Cada formato carrega a própria regra de reconhecimento, aplicada aos primeiros bytes do arquivo;
 * a extensão e o Content-Type informados pelo cliente nunca são usados.
 */
public enum TipoMidia {

    JPEG("image/jpeg", Categoria.IMAGEM, b -> temEm(b, 0, 0xFF, 0xD8, 0xFF)),
    PNG("image/png", Categoria.IMAGEM, b -> temEm(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)),
    GIF("image/gif", Categoria.IMAGEM, b -> temEm(b, 0, "GIF87a") || temEm(b, 0, "GIF89a")),
    WEBP("image/webp", Categoria.IMAGEM, b -> temEm(b, 0, "RIFF") && temEm(b, 8, "WEBP")),
    WEBM("video/webm", Categoria.VIDEO, b -> temEm(b, 0, 0x1A, 0x45, 0xDF, 0xA3)),

    // Família ISO (caixa "ftyp"): o formato real está na marca de 4 letras logo depois do "ftyp"
    HEIC("image/heic", Categoria.IMAGEM, ftyp("heic", "heix")),            // fotos do iPhone e de alguns Android
    HEIF("image/heif", Categoria.IMAGEM, ftyp("mif1")),
    AVIF("image/avif", Categoria.IMAGEM, ftyp("avif")),
    MP4("video/mp4", Categoria.VIDEO,
            ftyp("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash", "MSNV", "XAVC")),
    MOV("video/quicktime", Categoria.VIDEO, ftyp("qt  ")),                 // vídeos do iPhone
    VIDEO_3GP("video/3gpp", Categoria.VIDEO, ftyp("3gp4", "3gp5", "3gp6")); // Android mais antigos

    /** Quantos bytes iniciais bastam para reconhecer qualquer formato da lista. */
    public static final int BYTES_ASSINATURA = 12;

    public final String contentType;
    public final Categoria categoria;
    private final Predicate<byte[]> assinatura;

    TipoMidia(String contentType, Categoria categoria, Predicate<byte[]> assinatura) {
        this.contentType = contentType;
        this.categoria = categoria;
        this.assinatura = assinatura;
    }

    /** Vence o primeiro formato cuja regra bater; por isso as assinaturas não podem se sobrepor. */
    public static Optional<TipoMidia> detectar(byte[] inicio) {
        return Arrays.stream(values()).filter(tipo -> tipo.assinatura.test(inicio)).findFirst();
    }

    /** Monta a regra da família ISO: "ftyp" na posição 4 e uma das marcas aceitas logo depois. */
    private static Predicate<byte[]> ftyp(String... marcas) {
        Set<String> aceitas = Set.of(marcas);
        return b -> b.length >= 12 && temEm(b, 4, "ftyp")
                && aceitas.contains(new String(b, 8, 4, StandardCharsets.ISO_8859_1));
    }
}
