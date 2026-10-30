package etl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;

/** Validação contra os JSON Schemas de schemas/ (biblioteca networknt, draft 2020-12). */
public final class Validador {

    /** IRI base dos schemas locais. O domínio .invalid é reservado e nunca resolve na rede. */
    private static final String BASE = "https://esquemas.invalid/";

    private final Path pasta;
    private final SchemaRegistry registro;
    private final Map<String, Schema> cache = new ConcurrentHashMap<>();

    public Validador(Path pastaSchemas) {
        this.pasta = pastaSchemas.toAbsolutePath().normalize();
        this.registro = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                construtor -> construtor.schemas(this::conteudo));
    }

    /** Valida contra schemas/{nome}.schema.json e devolve as mensagens de erro (vazia quando válido). */
    public List<String> validar(String nome, JsonNode documento) {
        Schema schema = cache.computeIfAbsent(nome, n -> registro.getSchema(SchemaLocation.of(BASE + n + ".schema.json")));
        List<String> mensagens = new ArrayList<>();
        for (Error erro : schema.validate(documento)) {
            mensagens.add(erro.getInstanceLocation() + ": " + erro.getMessage());
        }
        return mensagens;
    }

    private String conteudo(String iri) {
        if (!iri.startsWith(BASE)) {
            return null;
        }
        Path arquivo = pasta.resolve(iri.substring(BASE.length())).normalize();
        if (!arquivo.startsWith(pasta) || !Files.exists(arquivo)) {
            return null;
        }
        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
