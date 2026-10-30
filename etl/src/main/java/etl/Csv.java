package etl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/** Leitura de CSV das fontes oficiais (latin-1, ponto e vírgula) com Jackson CSV. */
public final class Csv {

    private static final CsvMapper MAPPER = CsvMapper.builder().build();
    /** Aspa no meio de um campo, sem escape ("Raul"s Eventos"): vira aspa escapada. */
    private static final Pattern ASPA_SOLTA = Pattern.compile("(?<=[^;\\r\\n])\"(?=[^;\\r\\n])");

    private Csv() {
    }

    /**
     * Percorre as linhas como mapa coluna → valor.
     *
     * @param pularLinhas linhas antes do cabeçalho (o CEAPS traz uma linha de metadado)
     */
    public static void paraCada(InputStream entrada, Charset codificacao, int pularLinhas, Consumer<Map<String, String>> acao) {
        try {
            String texto = new String(entrada.readAllBytes(), codificacao);
            for (int i = 0; i < pularLinhas; i++) {
                texto = texto.substring(texto.indexOf('\n') + 1);
            }
            texto = ASPA_SOLTA.matcher(texto).replaceAll("\"\"");
            CsvSchema schema = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');
            try (MappingIterator<Map<String, String>> linhas = MAPPER.readerForMapOf(String.class).with(schema).readValues(texto)) {
                while (linhas.hasNextValue()) {
                    acao.accept(linhas.nextValue());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
