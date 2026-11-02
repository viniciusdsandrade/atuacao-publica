package etl.fontes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Csv;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Cartão de pagamento e viagens da Presidência da República, pelos arquivos abertos do Portal da Transparência (ADR-0010).
 * Sem chave: `download-de-dados/cpgf/{AAAAMM}` (um ZIP por mês) e `download-de-dados/viagens/{ano}` (um ZIP por ano).
 * O ETL soma por mês e categoria. Nenhum lançamento individual é publicado: os arquivos trazem nome de portador,
 * de favorecido e de viajante.
 */
public final class Cgu implements Fonte {

    public static final String FONTE_ID = "cgu-portal-arquivos";
    public static final String PORTAL = "https://portaldatransparencia.gov.br/download-de-dados";
    /** Órgão superior Presidência da República e o órgão Presidência da República, no SIAFI. */
    static final String ORGAO_SUPERIOR = "20000";
    static final String ORGAO = "20101";
    static final String ENTE = "BR";
    static final String REALIZADA = "Realizada";

    /** Soma de um mês, por categoria. */
    public record Agregado(String ente, int ano, int mes, String categoria, BigDecimal valor, int quantidade,
                           @JsonUnwrapped Prov prov) {
    }

    @Override
    public String nome() {
        return "cgu";
    }

    /** Meses de mandato presidencial já iniciados, desde o início da cobertura (contrib/mandatos.yaml). */
    static List<YearMonth> meses(Contexto ctx) {
        TreeSet<YearMonth> meses = new TreeSet<>();
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            if ("presidente".equals(Json.texto(regra, "cargo"))) {
                meses.addAll(TceSp.meses(LocalDate.parse(Json.texto(regra, "inicio")), LocalDate.parse(Json.texto(regra, "fim")), ctx.hoje()));
            }
        }
        return new ArrayList<>(meses);
    }

    static String caminhoCartao(YearMonth mes) {
        return "cpgf/" + mes.getYear() + String.format("%02d", mes.getMonthValue()) + ".zip";
    }

    static String urlCartao(YearMonth mes) {
        return PORTAL + "/cpgf/" + mes.getYear() + String.format("%02d", mes.getMonthValue());
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        List<YearMonth> meses = meses(ctx);
        TreeSet<Integer> anos = new TreeSet<>();
        for (YearMonth mes : meses) {
            // Mês ainda não publicado responde 403: fica um marcador vazio, renovado pelo rodízio semanal.
            coleta.obterOuVazio(urlCartao(mes), caminhoCartao(mes), "application/zip", TceSp.politica(mes, ctx.hoje()));
            anos.add(mes.getYear());
        }
        for (int ano : anos) {
            // O ZIP anual de viagens passa de 150 MB; só as linhas da Presidência ficam no cache.
            coleta.obterDerivado(PORTAL + "/viagens/" + ano, "viagens/" + ano + "-presidencia.csv", "application/zip",
                    Politica.porAnoSemanal(ano, ctx.hoje()), (zip, destino) -> filtrarViagens(zip, destino));
        }
        coleta.contar("meses", meses.size());
        if (!meses.isEmpty()) {
            coleta.periodo(new Meta.Periodo(meses.getFirst().atDay(1), ctx.hoje()));
        }
    }

    /** Guarda do `{ano}_Viagem.csv` só as viagens do órgão superior Presidência da República, no mesmo formato. */
    static void filtrarViagens(Path zip, Path destino) throws IOException {
        try (ZipFile arquivo = new ZipFile(zip.toFile())) {
            ZipEntry viagens = arquivo.stream().filter(e -> e.getName().endsWith("_Viagem.csv")).findFirst()
                    .orElseThrow(() -> new IOException("sem _Viagem.csv em " + zip.getFileName()));
            try (InputStream entrada = arquivo.getInputStream(viagens);
                 BufferedWriter saida = Files.newBufferedWriter(destino, StandardCharsets.ISO_8859_1)) {
                boolean[] cabecalho = {false};
                Csv.paraCada(entrada, StandardCharsets.ISO_8859_1, 0, linha -> {
                    if (!ORGAO_SUPERIOR.equals(linha.get("Código do órgão superior"))) {
                        return;
                    }
                    try {
                        if (!cabecalho[0]) {
                            escrever(saida, linha.keySet());
                            cabecalho[0] = true;
                        }
                        escrever(saida, linha.values());
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
        }
    }

    private static void escrever(BufferedWriter saida, Iterable<String> valores) throws IOException {
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

    @Override
    public void normalize(Contexto ctx) {
        List<Agregado> agregados = new ArrayList<>();
        int mesesSemDado = 0;
        TreeSet<Integer> anos = new TreeSet<>();
        for (YearMonth mes : meses(ctx)) {
            anos.add(mes.getYear());
            Coleta.Arquivo arq = Coleta.metadados(ctx, "cgu", caminhoCartao(mes));
            if (arq == null || arq.bytes() == 0) {
                mesesSemDado++;
                continue;
            }
            Path zip = Coleta.caminho(ctx, arq);
            Prov prov = Prov.de(FONTE_ID, arq.url(), arq);
            Somas somas = new Somas();
            try (ZipFile arquivo = new ZipFile(zip.toFile())) {
                ZipEntry csv = arquivo.stream().filter(e -> e.getName().endsWith(".csv")).findFirst()
                        .orElseThrow(() -> new IOException("sem CSV em " + zip.getFileName()));
                try (InputStream entrada = arquivo.getInputStream(csv)) {
                    Csv.paraCada(entrada, StandardCharsets.ISO_8859_1, 0, linha -> somarCartao(linha, somas));
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            agregados.addAll(somas.agregados(prov));
        }
        for (int ano : anos) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "cgu", "viagens/" + ano + "-presidencia.csv");
            if (arq == null) {
                continue;
            }
            Prov prov = Prov.de(FONTE_ID, arq.url(), arq);
            Somas somas = new Somas();
            try (InputStream entrada = Files.newInputStream(Coleta.caminho(ctx, arq))) {
                Csv.paraCada(entrada, StandardCharsets.ISO_8859_1, 0, linha -> somarViagem(linha, somas));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            agregados.addAll(somas.agregados(prov));
        }
        Json.gravar(ctx.normalizado().resolve("cgu").resolve("despesas.json"), agregados);
        System.out.println("cgu: " + agregados.size() + " agregados de despesa normalizados"
                + (mesesSemDado > 0 ? ", " + mesesSemDado + " mês(es) de cartão sem arquivo" : ""));
    }

    /** Somas por (ano, mês, categoria), com a quantidade de lançamentos. */
    static final class Somas {
        private final Map<String, BigDecimal> valores = new TreeMap<>();
        private final Map<String, Integer> quantidades = new LinkedHashMap<>();

        void somar(int ano, int mes, String categoria, BigDecimal valor) {
            String chave = ano + "|" + String.format("%02d", mes) + "|" + categoria;
            valores.merge(chave, valor, BigDecimal::add);
            quantidades.merge(chave, 1, Integer::sum);
        }

        List<Agregado> agregados(Prov prov) {
            List<Agregado> lista = new ArrayList<>();
            for (var soma : valores.entrySet()) {
                String[] partes = soma.getKey().split("\\|", 3);
                lista.add(new Agregado(ENTE, Integer.parseInt(partes[0]), Integer.parseInt(partes[1]), partes[2], soma.getValue(),
                        quantidades.get(soma.getKey()), prov));
            }
            return lista;
        }
    }

    /**
     * Uma transação do cartão do órgão Presidência da República. Transação sob sigilo entra pelo valor, que a fonte publica,
     * sem data nem favorecido. Portador e favorecido nunca são lidos.
     */
    static void somarCartao(Map<String, String> linha, Somas somas) {
        if (!ORGAO.equals(linha.get("CÓDIGO ÓRGÃO"))) {
            return;
        }
        Integer ano = inteiro(linha.get("ANO EXTRATO"));
        Integer mes = inteiro(linha.get("MÊS EXTRATO"));
        BigDecimal valor = Texto.valor(linha.get("VALOR TRANSAÇÃO"));
        if (ano == null || mes == null || valor == null) {
            return;
        }
        String unidade = Texto.vazioParaNulo(linha.get("NOME UNIDADE GESTORA"));
        somas.somar(ano, mes, "Cartão de pagamento" + (unidade == null ? "" : ": " + unidade), valor);
    }

    /** Uma viagem realizada do órgão Presidência da República, no mês da data de início. Viajante nunca é lido. */
    static void somarViagem(Map<String, String> linha, Somas somas) {
        if (!ORGAO.equals(linha.get("Código órgão solicitante")) || !REALIZADA.equals(linha.get("Situação"))) {
            return;
        }
        String inicio = Texto.dataBr(linha.get("Período - Data de início"));
        if (inicio == null) {
            return;
        }
        int ano = Integer.parseInt(inicio.substring(0, 4));
        int mes = Integer.parseInt(inicio.substring(5, 7));
        BigDecimal diarias = Texto.valor(linha.get("Valor diárias"));
        BigDecimal devolucao = Texto.valor(linha.get("Valor devolução"));
        BigDecimal passagens = Texto.valor(linha.get("Valor passagens"));
        BigDecimal outros = Texto.valor(linha.get("Valor outros gastos"));
        if (diarias != null) {
            somas.somar(ano, mes, "Diárias de viagem", devolucao == null ? diarias : diarias.subtract(devolucao));
        }
        if (passagens != null && passagens.signum() != 0) {
            somas.somar(ano, mes, "Passagens", passagens);
        }
        if (outros != null && outros.signum() != 0) {
            somas.somar(ano, mes, "Outros gastos de viagem", outros);
        }
    }

    private static Integer inteiro(String texto) {
        try {
            return texto == null ? null : Integer.valueOf(texto.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Para testes: percorre um CSV em memória com o mesmo leitor da coleta. */
    static void paraCada(String csv, Consumer<Map<String, String>> acao) {
        Csv.paraCada(new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.ISO_8859_1, 0, acao);
    }
}
