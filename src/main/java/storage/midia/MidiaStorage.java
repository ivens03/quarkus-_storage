package storage.midia;

import com.mongodb.MongoGridFSException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.util.Optional;

/**
 * Acesso ao GridFS. O bucket "midias" vira as collections
 * midias.files (a ficha de cada arquivo) e midias.chunks (os pedaços de 255 KB).
 */
@ApplicationScoped
public class MidiaStorage {

    private static final String BUCKET = "midias";

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String database;

    private GridFSBucket bucket;

    @PostConstruct
    void iniciar() {
        bucket = GridFSBuckets.create(mongoClient.getDatabase(database), BUCKET);
    }

    /** O driver corta o stream em chunks, grava cada um e só no fim grava a ficha. */
    public ObjectId salvar(String nome, TipoMidia tipo, InputStream conteudo) {
        var metadados = new Document("contentType", tipo.contentType)
                .append("categoria", tipo.categoria.name());
        return bucket.uploadFromStream(nome, conteudo, new GridFSUploadOptions().metadata(metadados));
    }

    /** Lê só a ficha (midias.files), sem tocar nos chunks. */
    public Optional<GridFSFile> buscar(ObjectId id) {
        return Optional.ofNullable(bucket.find(Filters.eq("_id", id)).first());
    }

    /** Stream que busca os chunks sob demanda, em ordem, conforme é lido. */
    public GridFSDownloadStream abrir(ObjectId id) {
        return bucket.openDownloadStream(id);
    }

    public boolean apagar(ObjectId id) {
        try {
            bucket.delete(id);
            return true;
        } catch (MongoGridFSException e) {
            // o driver lança essa exceção quando não existe ficha com esse id
            return false;
        }
    }
}
