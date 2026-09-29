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
import org.bson.BsonString;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import storage.model.Midia;
import storage.model.TipoMidia;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Única classe que conhece o MongoDB. O bucket "midias" vira as collections
 * midias.files (a ficha de cada arquivo) e midias.chunks (os pedaços de 255 KB).
 *
 * O id de cada arquivo é um UUID aleatório, e não o ObjectId padrão do Mongo:
 * o ObjectId é sequencial (horário + contador), então com um id na mão daria para adivinhar os vizinhos.
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

    /**
     * O driver corta o stream em chunks, grava cada um e só no fim grava a ficha.
     * O nome gravado é gerado aqui ("<id>.<extensão do tipo real>"); o nome enviado pelo cliente não é usado.
     */
    public Midia salvar(TipoMidia tipo, InputStream conteudo) {
        String id = UUID.randomUUID().toString();
        var metadados = new Document("contentType", tipo.contentType)
                .append("categoria", tipo.categoria.name());
        bucket.uploadFromStream(new BsonString(id), id + "." + tipo.extensao, conteudo,
                new GridFSUploadOptions().metadata(metadados));
        // Relê a ficha para devolver o que ficou gravado de fato (o tamanho é calculado pelo GridFS)
        return buscar(id).orElseThrow();
    }

    /** Lê só a ficha (midias.files), sem tocar nos chunks. */
    public Optional<Midia> buscar(String id) {
        return Optional.ofNullable(bucket.find(Filters.eq("_id", id)).first()).map(MidiaRepository::paraMidia);
    }

    /** Stream que busca os chunks sob demanda, em ordem, conforme é lido. */
    public InputStream abrir(String id) {
        return bucket.openDownloadStream(new BsonString(id));
    }

    public boolean apagar(String id) {
        try {
            bucket.delete(new BsonString(id));
            return true;
        } catch (MongoGridFSException e) {
            // o driver lança essa exceção quando não existe ficha com esse id
            return false;
        }
    }

    private static Midia paraMidia(GridFSFile ficha) {
        Document metadados = ficha.getMetadata();
        String contentType = metadados != null && metadados.containsKey("contentType")
                ? metadados.getString("contentType")
                : TIPO_DESCONHECIDO;
        return new Midia(ficha.getId().asString().getValue(), contentType, ficha.getLength());
    }
}
