package storage.repository;

import com.mongodb.MongoGridFSException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import storage.model.Midia;
import storage.model.TipoMidia;

import java.io.InputStream;
import java.util.Optional;

/**
 * Única classe que conhece o MongoDB. O bucket "midias" vira as collections
 * midias.files (a ficha de cada arquivo) e midias.chunks (os pedaços de 255 KB).
 * Para fora daqui o id é sempre uma String; o ObjectId não sai desta classe.
 */
@ApplicationScoped
public class MidiaRepository {

    private static final String BUCKET = "midias";
    private static final String TIPO_DESCONHECIDO = "application/octet-stream";

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
    public Midia salvar(String nome, TipoMidia tipo, InputStream conteudo) {
        var metadados = new Document("contentType", tipo.contentType)
                .append("categoria", tipo.categoria.name());
        ObjectId id = bucket.uploadFromStream(nome, conteudo, new GridFSUploadOptions().metadata(metadados));
        // Relê a ficha para devolver o que ficou gravado de fato (o tamanho é calculado pelo GridFS)
        return buscarFicha(id).map(MidiaRepository::paraMidia).orElseThrow();
    }

    /** Lê só a ficha (midias.files), sem tocar nos chunks. */
    public Optional<Midia> buscar(String id) {
        return converterId(id).flatMap(this::buscarFicha).map(MidiaRepository::paraMidia);
    }

    /** Stream que busca os chunks sob demanda, em ordem, conforme é lido. */
    public InputStream abrir(String id) {
        return bucket.openDownloadStream(new ObjectId(id));
    }

    public boolean apagar(String id) {
        Optional<ObjectId> objectId = converterId(id);
        if (objectId.isEmpty()) {
            return false;
        }
        try {
            bucket.delete(objectId.get());
            return true;
        } catch (MongoGridFSException e) {
            // o driver lança essa exceção quando não existe ficha com esse id
            return false;
        }
    }

    private Optional<GridFSFile> buscarFicha(ObjectId id) {
        return Optional.ofNullable(bucket.find(Filters.eq("_id", id)).first());
    }

    private static Optional<ObjectId> converterId(String id) {
        return ObjectId.isValid(id) ? Optional.of(new ObjectId(id)) : Optional.empty();
    }

    private static Midia paraMidia(GridFSFile ficha) {
        Document metadados = ficha.getMetadata();
        String contentType = metadados != null && metadados.containsKey("contentType")
                ? metadados.getString("contentType")
                : TIPO_DESCONHECIDO;
        return new Midia(ficha.getObjectId().toHexString(), ficha.getFilename(), contentType, ficha.getLength());
    }
}
