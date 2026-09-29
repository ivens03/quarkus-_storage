package storage.exception.exception;

public class FormatoNaoAceitoException extends RuntimeException {

    public FormatoNaoAceitoException() {
        super("Só são aceitas imagens (JPEG, PNG, GIF, WebP, HEIC, HEIF, AVIF) e vídeos (MP4, MOV, 3GP, WebM)");
    }
}
