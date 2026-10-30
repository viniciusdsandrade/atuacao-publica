package etl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Leitura e escrita de JSON e YAML com as convenções do projeto (snake_case, datas ISO). */
public final class Json {

    /** Saída publicada: snake_case, BigDecimal sem notação científica. */
    public static final ObjectMapper SAIDA = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    /** Leitura de JSON das fontes, sem convenção de nomes. */
    public static final ObjectMapper FONTE = JsonMapper.builder().build();

    public static final ObjectMapper YAML = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private static final ObjectMapper SAIDA_LEGIVEL = SAIDA.rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private Json() {
    }

    /** Abre o arquivo, descompactando quando termina em .gz. */
    public static InputStream abrir(Path arquivo) throws IOException {
        InputStream entrada = Files.newInputStream(arquivo);
        if (arquivo.getFileName().toString().endsWith(".gz")) {
            return new GZIPInputStream(entrada, 1 << 16);
        }
        return entrada;
    }

    public static JsonNode ler(Path arquivo) {
        try (InputStream entrada = abrir(arquivo)) {
            return FONTE.readTree(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + arquivo, e);
        }
    }

    public static JsonNode lerYaml(Path arquivo) {
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            return YAML.readTree(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + arquivo, e);
        }
    }

    public static <T> T ler(Path arquivo, Class<T> tipo) {
        try (InputStream entrada = abrir(arquivo)) {
            return SAIDA.readValue(entrada, tipo);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + arquivo, e);
        }
    }

    /** Grava de forma atômica: escreve num temporário e renomeia. */
    public static void gravar(Path arquivo, Object valor, boolean legivel) {
        try {
            Files.createDirectories(arquivo.getParent());
            Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
            try (OutputStream saida = Files.newOutputStream(temporario)) {
                (legivel ? SAIDA_LEGIVEL : SAIDA).writeValue(saida, valor);
            }
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar " + arquivo, e);
        }
    }

    public static void gravar(Path arquivo, Object valor) {
        gravar(arquivo, valor, false);
    }

    public static JsonNode arvore(Object valor) {
        return SAIDA.valueToTree(valor);
    }

    /**
     * Percorre em streaming o array do campo de topo (por exemplo, "dados"), um elemento por vez.
     * Evita carregar arquivos de centenas de MB como árvore.
     */
    public static void paraCada(InputStream entrada, String campo, Consumer<JsonNode> acao) {
        try (JsonParser parser = FONTE.createParser(entrada)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.PROPERTY_NAME && campo.equals(parser.currentName()) && parser.streamReadContext().getParent().inRoot()) {
                    if (parser.nextToken() == JsonToken.START_ARRAY) {
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            acao.accept(FONTE.readTree(parser));
                        }
                    }
                    return;
                }
            }
        }
    }

    public static void paraCada(Path arquivo, String campo, Consumer<JsonNode> acao) {
        try (InputStream entrada = abrir(arquivo)) {
            paraCada(entrada, campo, acao);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + arquivo, e);
        }
    }

    /** Primeiro arquivo .json dentro de um ZIP, em streaming. */
    public static void paraCadaNoZip(Path zip, String campo, Consumer<JsonNode> acao) {
        try (ZipInputStream entrada = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry item;
            while ((item = entrada.getNextEntry()) != null) {
                if (item.getName().endsWith(".json")) {
                    paraCada(entrada, campo, acao);
                    return;
                }
            }
            throw new IOException("nenhum .json em " + zip);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + zip, e);
        }
    }

    /** As APIs em XML convertido para JSON trazem objeto quando há um só elemento e array quando há vários. */
    public static List<JsonNode> lista(JsonNode no) {
        List<JsonNode> itens = new ArrayList<>();
        if (no == null || no.isMissingNode() || no.isNull()) {
            return itens;
        }
        if (no.isArray()) {
            for (JsonNode item : no) {
                itens.add(item);
            }
        } else {
            itens.add(no);
        }
        return itens;
    }

    /** Texto do campo, ou null quando ausente, nulo ou vazio. */
    public static String texto(JsonNode no, String campo) {
        JsonNode valor = no == null ? null : no.get(campo);
        if (valor == null || valor.isNull() || valor.isMissingNode()) {
            return null;
        }
        String texto = valor.isString() ? valor.stringValue() : valor.toString();
        texto = texto.strip();
        return texto.isEmpty() ? null : texto;
    }

    public static Integer inteiro(JsonNode no, String campo) {
        String texto = texto(no, campo);
        if (texto == null) {
            return null;
        }
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
