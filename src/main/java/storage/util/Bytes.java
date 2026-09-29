package storage.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Operações genéricas com bytes: não sabem nada de mídia nem de HTTP. */
public final class Bytes {

    private Bytes() {
    }

    /** Diz se o texto (ASCII) aparece em {@code dados} a partir da posição informada. */
    public static boolean temEm(byte[] dados, int posicao, String esperado) {
        return temEm(dados, posicao, esperado.chars().toArray());
    }

    /** Diz se os bytes esperados (0 a 255) aparecem em {@code dados} a partir da posição informada. */
    public static boolean temEm(byte[] dados, int posicao, int... esperado) {
        if (dados.length < posicao + esperado.length) {
            return false;
        }
        for (int i = 0; i < esperado.length; i++) {
            // byte em Java tem sinal (-128 a 127); o & 0xFF converte para 0 a 255 antes de comparar
            if ((dados[posicao + i] & 0xFF) != esperado[i]) {
                return false;
            }
        }
        return true;
    }

    /** Copia no máximo {@code quantidade} bytes, sem carregar tudo na memória. */
    public static void copiar(InputStream origem, OutputStream destino, long quantidade) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long restante = quantidade;
        while (restante > 0) {
            int lidos = origem.read(buffer, 0, (int) Math.min(buffer.length, restante));
            if (lidos == -1) {
                break;
            }
            destino.write(buffer, 0, lidos);
            restante -= lidos;
        }
    }
}
