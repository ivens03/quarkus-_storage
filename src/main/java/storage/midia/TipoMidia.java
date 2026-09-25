package storage.midia;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Lista branca dos formatos aceitos pelo storage.
 * O tipo é descoberto pela assinatura nos primeiros bytes do arquivo,
 * nunca pela extensão ou pelo Content-Type que o cliente informa.
 */
public enum TipoMidia {

    JPEG("image/jpeg", Categoria.IMAGEM),
    PNG("image/png", Categoria.IMAGEM),
    GIF("image/gif", Categoria.IMAGEM),
    WEBP("image/webp", Categoria.IMAGEM),
    WEBM("video/webm", Categoria.VIDEO),

    // Formatos da família ISO (caixa "ftyp"): o tipo real está na marca de 4 letras logo depois do "ftyp"
    HEIC("image/heic", Categoria.IMAGEM, "heic", "heix"),                  // fotos do iPhone e de alguns Android
    HEIF("image/heif", Categoria.IMAGEM, "mif1"),
    AVIF("image/avif", Categoria.IMAGEM, "avif"),
    MP4("video/mp4", Categoria.VIDEO,
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash", "MSNV", "XAVC"),
    MOV("video/quicktime", Categoria.VIDEO, "qt  "),                       // vídeos do iPhone
    VIDEO_3GP("video/3gpp", Categoria.VIDEO, "3gp4", "3gp5", "3gp6");      // Android mais antigos

    public enum Categoria {
        IMAGEM(20L * 1024 * 1024),          // 20 MB
        VIDEO(2L * 1024 * 1024 * 1024);     // 2 GB

        public final long tamanhoMaximo;

        Categoria(long tamanhoMaximo) {
            this.tamanhoMaximo = tamanhoMaximo;
        }
    }

    /** Quantos bytes iniciais bastam para reconhecer qualquer formato da lista. */
    public static final int BYTES_ASSINATURA = 12;

    public final String contentType;
    public final Categoria categoria;
    private final Set<String> marcasFtyp;

    TipoMidia(String contentType, Categoria categoria, String... marcasFtyp) {
        this.contentType = contentType;
        this.categoria = categoria;
        this.marcasFtyp = Set.of(marcasFtyp);
    }

    public static Optional<TipoMidia> detectar(byte[] inicio) {
        if (bate(inicio, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (bate(inicio, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (bate(inicio, 0, "GIF87a") || bate(inicio, 0, "GIF89a")) {
            return Optional.of(GIF);
        }
        if (bate(inicio, 0, "RIFF") && bate(inicio, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        if (bate(inicio, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
            return Optional.of(WEBM);
        }
        if (bate(inicio, 4, "ftyp") && inicio.length >= 12) {
            // Marca fora da lista (ex.: "M4A " de áudio) não é aceita
            String marca = new String(inicio, 8, 4, StandardCharsets.ISO_8859_1);
            return Arrays.stream(values()).filter(tipo -> tipo.marcasFtyp.contains(marca)).findFirst();
        }
        return Optional.empty();
    }

    private static boolean bate(byte[] dados, int posicao, String esperado) {
        return bate(dados, posicao, esperado.chars().toArray());
    }

    private static boolean bate(byte[] dados, int posicao, int... esperado) {
        if (dados.length < posicao + esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            if ((dados[posicao + i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }
}
