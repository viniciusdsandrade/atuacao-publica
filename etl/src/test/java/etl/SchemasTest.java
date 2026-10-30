package etl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import tools.jackson.databind.JsonNode;

/** Cada exemplo de schemas/exemplos/validos passa; cada exemplo de schemas/exemplos/invalidos falha. */
class SchemasTest {

    private static final Path SCHEMAS = Contexto.acharRaiz(Path.of("").toAbsolutePath()).resolve("schemas");
    private static final Validador VALIDADOR = new Validador(SCHEMAS);

    @TestFactory
    Stream<DynamicTest> exemplosValidosPassam() throws IOException {
        return exemplos("validos").map(arquivo -> DynamicTest.dynamicTest(nome(arquivo), () -> {
            List<String> erros = VALIDADOR.validar(schemaDe(arquivo), ler(arquivo));
            assertTrue(erros.isEmpty(), () -> nome(arquivo) + " deveria ser válido: " + erros);
        }));
    }

    @TestFactory
    Stream<DynamicTest> exemplosInvalidosFalham() throws IOException {
        return exemplos("invalidos").map(arquivo -> DynamicTest.dynamicTest(nome(arquivo), () ->
                assertFalse(VALIDADOR.validar(schemaDe(arquivo), ler(arquivo)).isEmpty(),
                        () -> nome(arquivo) + " deveria ser inválido")));
    }

    @TestFactory
    Stream<DynamicTest> todoSchemaTemExemplos() throws IOException {
        try (Stream<Path> schemas = Files.list(SCHEMAS)) {
            return schemas.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".schema.json") && !n.equals("comum.schema.json") && !n.equals("registros.schema.json"))
                    .map(n -> n.replace(".schema.json", ""))
                    .sorted()
                    .toList().stream()
                    .map(schema -> DynamicTest.dynamicTest(schema, () -> {
                        assertTrue(Files.isDirectory(SCHEMAS.resolve("exemplos/validos/" + schema)), schema + " sem exemplo válido");
                        assertTrue(Files.isDirectory(SCHEMAS.resolve("exemplos/invalidos/" + schema)), schema + " sem exemplo inválido");
                    }));
        }
    }

    private static Stream<Path> exemplos(String grupo) throws IOException {
        try (Stream<Path> todos = Files.walk(SCHEMAS.resolve("exemplos").resolve(grupo))) {
            return todos.filter(Files::isRegularFile).sorted().toList().stream();
        }
    }

    private static String schemaDe(Path arquivo) {
        return arquivo.getParent().getFileName().toString();
    }

    private static String nome(Path arquivo) {
        return SCHEMAS.relativize(arquivo).toString();
    }

    private static JsonNode ler(Path arquivo) {
        return arquivo.toString().endsWith(".yaml") ? Json.lerYaml(arquivo) : Json.ler(arquivo);
    }
}
