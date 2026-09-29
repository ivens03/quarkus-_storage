package storage.exception.exception;

public class MidiaNaoEncontradaException extends RuntimeException {

    public MidiaNaoEncontradaException() {
        super("Mídia não encontrada");
    }
}
