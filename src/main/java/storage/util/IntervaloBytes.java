package storage.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Trecho do arquivo pedido pelo cabeçalho HTTP Range; início e fim são inclusivos. */
public record IntervaloBytes(long inicio, long fim) {

    private static final Pattern FORMATO = Pattern.compile("bytes=(\\d{0,18})-(\\d{0,18})");

    public long tamanho() {
        return fim - inicio + 1;
    }

    public static IntervaloBytes inteiro(long total) {
        return new IntervaloBytes(0, total - 1);
    }

    /**
     * Aceita um único intervalo, nos três formatos do HTTP:
     *   bytes=100-199  do byte 100 ao 199
     *   bytes=100-     do byte 100 até o fim
     *   bytes=-500     os últimos 500 bytes
     * Vazio quando o cabeçalho é inválido ou o trecho está fora do arquivo.
     */
    public static Optional<IntervaloBytes> ler(String cabecalho, long total) {
        Matcher m = FORMATO.matcher(cabecalho.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String de = m.group(1);
        String ate = m.group(2);

        long inicio;
        long fim;
        if (de.isEmpty()) {
            if (ate.isEmpty() || Long.parseLong(ate) == 0) {
                return Optional.empty();
            }
            inicio = Math.max(0, total - Long.parseLong(ate));
            fim = total - 1;
        } else {
            inicio = Long.parseLong(de);
            fim = ate.isEmpty() ? total - 1 : Math.min(Long.parseLong(ate), total - 1);
        }

        if (inicio >= total || inicio > fim) {
            return Optional.empty();
        }
        return Optional.of(new IntervaloBytes(inicio, fim));
    }
}
