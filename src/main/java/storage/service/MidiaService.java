package storage.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import storage.exception.exception.FormatoNaoAceitoException;
import storage.exception.exception.MidiaNaoEncontradaException;
import storage.exception.exception.TamanhoExcedidoException;
import storage.model.Midia;
import storage.model.TipoMidia;
import storage.repository.MidiaRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regras do storage. Não conhece HTTP nem MongoDB: recebe dados simples e delega a gravação ao repository. */
@ApplicationScoped
public class MidiaService {

    @Inject
    MidiaRepository repository;

    /** Valida formato e tamanho de um arquivo que já está em disco e, se estiver tudo certo, grava. */
    public Midia salvar(Path arquivo) throws IOException {
        TipoMidia tipo = TipoMidia.detectar(lerAssinatura(arquivo))
                .orElseThrow(FormatoNaoAceitoException::new);

        if (Files.size(arquivo) > tipo.categoria.tamanhoMaximo) {
            throw new TamanhoExcedidoException(tipo.categoria);
        }

        try (InputStream conteudo = Files.newInputStream(arquivo)) {
            return repository.salvar(tipo, conteudo);
        }
    }

    public Midia buscar(String id) {
        return repository.buscar(id).orElseThrow(MidiaNaoEncontradaException::new);
    }

    public InputStream abrirConteudo(Midia midia) {
        return repository.abrir(midia.id());
    }

    public void apagar(String id) {
        if (!repository.apagar(id)) {
            throw new MidiaNaoEncontradaException();
        }
    }

    private static byte[] lerAssinatura(Path arquivo) throws IOException {
        try (InputStream in = Files.newInputStream(arquivo)) {
            return in.readNBytes(TipoMidia.BYTES_ASSINATURA);
        }
    }
}
