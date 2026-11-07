package etl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
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
            BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, codificacao));
            for (int i = 0; i < pularLinhas; i++) {
                leitor.readLine();
            }
            CsvSchema schema = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');
            try (MappingIterator<Map<String, String>> linhas = MAPPER.readerForMapOf(String.class).with(schema)
                    .readValues(new LinhasCorrigidas(leitor))) {
                while (linhas.hasNextValue()) {
                    acao.accept(linhas.nextValue());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uma linha no formato das fontes: campos entre aspas, separados por ponto e vírgula, com CRLF. */
    public static void escreverLinha(Writer saida, Iterable<String> valores) throws IOException {
        boolean primeiro = true;
        for (String valor : valores) {
            if (!primeiro) {
                saida.write(';');
            }
            saida.write('"');
            saida.write(valor == null ? "" : valor.replace("\"", "\"\""));
            saida.write('"');
            primeiro = false;
        }
        saida.write("\r\n");
    }

    /**
     * Entrega o CSV linha a linha, com a aspa solta corrigida, sem carregar o arquivo inteiro:
     * o CSV de candidaturas do TSE passa de 300 MB.
     */
    private static final class LinhasCorrigidas extends Reader {
        private final BufferedReader origem;
        private String atual = "";
        private int posicao;
        private boolean fim;

        LinhasCorrigidas(BufferedReader origem) {
            this.origem = origem;
        }

        @Override
        public int read(char[] destino, int inicio, int tamanho) throws IOException {
            if (tamanho == 0) {
                return 0;
            }
            while (posicao >= atual.length()) {
                if (fim) {
                    return -1;
                }
                String linha = proximaLinha();
                if (linha.isEmpty()) {
                    fim = true;
                    return -1;
                }
                atual = ASPA_SOLTA.matcher(linha).replaceAll("\"\"");
                posicao = 0;
            }
            int n = Math.min(tamanho, atual.length() - posicao);
            atual.getChars(posicao, posicao + n, destino, inicio);
            posicao += n;
            return n;
        }

        /** Uma linha com o terminador original (\r\n ou \n), para não alterar campo com quebra de linha. */
        private String proximaLinha() throws IOException {
            StringBuilder linha = new StringBuilder();
            int c;
            while ((c = origem.read()) != -1) {
                linha.append((char) c);
                if (c == '\n') {
                    break;
                }
            }
            return linha.toString();
        }

        @Override
        public void close() throws IOException {
            origem.close();
        }
    }
}
