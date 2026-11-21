package etl.fontes;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Texto;
import etl.Registros.Prov;
import etl.fontes.ExecutivoSp.Agregado;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Poder Executivo do Estado do Rio Grande do Sul (ADR-0082): despesa paga por mês e órgão, a partir dos arquivos
 * mensais "Despesa do Estado" do portal de dados abertos. Cada arquivo traz um lançamento por linha, com o favorecido:
 * a coleta soma o arquivo assim que baixa e guarda só a soma. O favorecido nunca é lido.
 */
public final class ExecutivoRs implements Fonte {

    public static final String FONTE_DESPESA = "rs-despesa-do-estado";
    public static final String CKAN = "https://dados.rs.gov.br/api/3/action/";
    public static final String UF = "RS";
    static final int ANO_INICIAL = 2022;

    /** O nome do arquivo traz o mês ("despesas-do-estado-202603.zip"); o nome do recurso pode apontar o mês errado. */
    private static final Pattern MES_DO_ARQUIVO = Pattern.compile("(20\\d{2})(\\d{2})\\.zip$");
    private static final Pattern DATA = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern RECURSO = Pattern.compile("/resource/([0-9a-f-]{36})/");

    @Override
    public String nome() {
        return "executivors";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        YearMonth atual = YearMonth.from(ctx.hoje());
        Coleta.Arquivo busca = coleta.obter(CKAN + "package_search?q=%22Despesa%20do%20Estado%22&rows=50", "despesa/pacotes.json",
                "application/json", Politica.SEMANAL);
        for (String pacote : pacotes(Json.ler(Coleta.caminho(ctx, busca)))) {
            Coleta.Arquivo arq = coleta.obterOpcional(CKAN + "package_show?id=" + pacote, "despesa/pacote-" + pacote + ".json",
                    "application/json", Politica.SEMANAL);
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            for (String url : arquivosMensais(Json.ler(Coleta.caminho(ctx, arq)))) {
                YearMonth mes = mes(url);
                if (mes == null || mes.getYear() < ANO_INICIAL || mes.isAfter(atual) || coleta.orcamentoExaurido()) {
                    continue;
                }
                // Os meses recentes ainda chegam incompletos (agosto e setembro de 2026 tinham poucos lançamentos em novembro).
                Politica politica = mes.isBefore(atual.minusMonths(3)) ? Politica.SE_AUSENTE : Politica.SEMANAL;
                coleta.obterDerivado(url, caminho(url, mes), "application/zip", politica, ExecutivoRs::somar);
            }
        }
        coleta.periodo(new Meta.Periodo(LocalDate.of(ANO_INICIAL, 1, 1), ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path saida = ctx.normalizado().resolve(nome());
        Files.createDirectories(saida);
        List<Agregado> despesas = new ArrayList<>();
        Path pasta = ctx.raw().resolve(nome()).resolve("despesa");
        List<String> arquivos = new ArrayList<>();
        if (Files.isDirectory(pasta)) {
            try (var lista = Files.list(pasta)) {
                lista.map(p -> p.getFileName().toString()).filter(n -> n.matches("\\d{4}-\\d{2}-[0-9a-f]+\\.json")).sorted()
                        .forEach(arquivos::add);
            }
        }
        // Só o arquivo que a coleta escolheu para cada mês (arquivosMensais) entra; uma soma antiga de outro recurso do
        // mesmo mês fica de fora. A soma usa a data de cada pagamento.
        Set<String> escolhidos = new HashSet<>();
        try (var pacotes = Files.list(pasta)) {
            for (Path p : pacotes.filter(x -> x.getFileName().toString().matches("pacote-.*\\.json(\\.gz)?")).toList()) {
                for (String url : arquivosMensais(Json.ler(p))) {
                    YearMonth mes = mes(url);
                    if (mes != null) {
                        escolhidos.add(caminho(url, mes).substring("despesa/".length()));
                    }
                }
            }
        }
        for (String nomeDoArquivo : arquivos) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "despesa/" + nomeDoArquivo);
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq)) || !escolhidos.contains(nomeDoArquivo)) {
                continue;
            }
            Prov prov = Prov.de(FONTE_DESPESA, arq.url(), arq);
            for (JsonNode s : Json.ler(Coleta.caminho(ctx, arq))) {
                despesas.add(new Agregado(UF, s.get("ano").asInt(), s.get("mes").asInt(), Json.texto(s, "orgao"), Json.texto(s, "orgao"),
                        s.get("valor").decimalValue(), s.get("quantidade").asInt(), prov));
            }
        }
        Map<String, Agregado> unicos = new TreeMap<>();
        for (Agregado a : despesas) {
            String chave = a.ano() + "|" + a.mes() + "|" + a.categoria();
            Agregado anterior = unicos.get(chave);
            // O mesmo mês pode aparecer em dois arquivos diferentes (lançamento de fim de mês): as somas se juntam.
            unicos.put(chave, anterior == null ? a : new Agregado(UF, a.ano(), a.mes(), a.orgao(), a.categoria(),
                    anterior.valor().add(a.valor()), anterior.quantidade() + a.quantidade(), anterior.prov()));
        }
        despesas = new ArrayList<>(unicos.values());
        despesas.removeIf(a -> a.ano() < ANO_INICIAL);
        Json.gravar(saida.resolve("despesas.json"), Json.SAIDA.valueToTree(despesas));
        Json.gravar(saida.resolve("atos.json"), Json.SAIDA.createArrayNode());
        Json.gravar(saida.resolve("compromissos.json"), Json.SAIDA.createArrayNode());
        System.out.println("executivors: " + despesas.size() + " agregados de despesa normalizados");
    }

    /** Caminho da soma de um recurso: o mês do nome do arquivo e o id do recurso no CKAN. */
    static String caminho(String url, YearMonth mes) {
        Matcher id = RECURSO.matcher(url);
        String sufixo = id.find() ? id.group(1).replace("-", "") : Integer.toHexString(url.hashCode());
        return "despesa/" + mes + "-" + sufixo + ".json";
    }

    /** Pacotes anuais "AAAA - Despesa(s) do Estado" a partir do ano inicial. */
    static List<String> pacotes(JsonNode busca) {
        List<String> nomes = new ArrayList<>();
        for (JsonNode p : busca.path("result").path("results")) {
            String titulo = Json.texto(p, "title");
            Matcher ano = titulo == null ? null : Pattern.compile("^(20\\d{2}) - Despesas? do Estado$").matcher(titulo.strip());
            if (ano != null && ano.matches() && Integer.parseInt(ano.group(1)) >= ANO_INICIAL) {
                nomes.add(Json.texto(p, "name"));
            }
        }
        return nomes;
    }

    private static final List<String> MESES = List.of("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro");

    /**
     * Um arquivo por mês. Há recurso cujo nome diz um mês e o arquivo, outro (o "Abril" de 2022 baixa o de maio): os dois
     * são versões do mesmo mês e não podem ser somados. Vale o recurso cujo nome é o mês do arquivo; o outro fica de fora.
     */
    static List<String> arquivosMensais(JsonNode pacote) {
        Map<YearMonth, String> porMes = new TreeMap<>();
        Map<YearMonth, Boolean> nomeConfere = new TreeMap<>();
        for (JsonNode r : pacote.path("result").path("resources")) {
            String url = Json.texto(r, "url");
            YearMonth mes = url == null ? null : mes(url);
            if (mes == null) {
                continue;
            }
            String nome = Texto.normalizarNome(Json.texto(r, "name"));
            boolean confere = nome != null && nome.toLowerCase().equals(MESES.get(mes.getMonthValue() - 1));
            if (!porMes.containsKey(mes) || confere && !nomeConfere.get(mes)) {
                porMes.put(mes, url);
                nomeConfere.put(mes, confere);
            }
        }
        return new ArrayList<>(porMes.values());
    }

    static YearMonth mes(String url) {
        Matcher m = MES_DO_ARQUIVO.matcher(url);
        return m.find() ? YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    /** Derivação: soma o valor pago do Poder Executivo por mês da data do pagamento e por órgão. */
    static void somar(Path zip, Path destino) throws IOException {
        Map<String, BigDecimal> valor = new TreeMap<>();
        Map<String, Integer> pagamentos = new TreeMap<>();
        try (InputStream in = Files.newInputStream(zip); ZipInputStream z = new ZipInputStream(in, StandardCharsets.ISO_8859_1)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.getName().toLowerCase().endsWith(".csv")) {
                    continue;
                }
                int descartadas = somar(new NaoFecha(z), valor, pagamentos);
                if (descartadas > 0) {
                    System.out.println("executivors: " + descartadas + " pagamento(s) com data ou valor ilegível em " + e.getName());
                }
            }
        }
        ArrayNode lista = Json.SAIDA.createArrayNode();
        valor.forEach((chave, v) -> {
            String[] partes = chave.split("\\|", 3);
            ObjectNode s = lista.addObject();
            s.put("ano", Integer.parseInt(partes[0])).put("mes", Integer.parseInt(partes[1])).put("orgao", partes[2])
                    .put("valor", v).put("quantidade", pagamentos.get(chave));
        });
        Json.gravar(destino, lista);
    }

    /**
     * Lê linha a linha, separando por ponto e vírgula. O arquivo tem linhas com o número de colunas errado (cerca de
     * 0,35% em julho de 2026), em geral pelo histórico, que fica depois das colunas usadas. A linha cuja data ou valor
     * não se lê fica de fora e entra na contagem de descartadas.
     */
    static int somar(InputStream csv, Map<String, BigDecimal> valor, Map<String, Integer> pagamentos) throws IOException {
        java.io.BufferedReader leitor = new java.io.BufferedReader(new java.io.InputStreamReader(csv, StandardCharsets.ISO_8859_1));
        String cabecalho = leitor.readLine();
        if (cabecalho == null) {
            return 0;
        }
        List<String> colunas = List.of(cabecalho.replace("\uFEFF", "").split(";", -1));
        int fase = colunas.indexOf("FaseGasto");
        int poder = colunas.indexOf("Poder");
        int orgao = colunas.indexOf("Orgao");
        int data = colunas.indexOf("Data");
        int valorCol = colunas.indexOf("Valor");
        int ultima = Math.max(Math.max(fase, poder), Math.max(Math.max(orgao, data), valorCol));
        if (fase < 0 || poder < 0 || orgao < 0 || data < 0 || valorCol < 0) {
            throw new IOException("colunas esperadas ausentes no cabeçalho: " + cabecalho);
        }
        int descartadas = 0;
        String linha;
        while ((linha = leitor.readLine()) != null) {
            String[] c = linha.split(";", -1);
            // Até 2025-01 o poder vem em maiúsculas ("PODER EXECUTIVO").
            if (c.length <= ultima || !"Pagamento".equalsIgnoreCase(c[fase]) || !"Poder Executivo".equalsIgnoreCase(c[poder])) {
                continue;
            }
            Matcher d = DATA.matcher(c[data]);
            BigDecimal v = decimal(c[valorCol]);
            if (!d.matches() || v == null || c[orgao].isBlank()) {
                descartadas++;
                continue;
            }
            String chave = d.group(3) + "|" + d.group(2) + "|" + c[orgao].strip();
            valor.merge(chave, v, BigDecimal::add);
            pagamentos.merge(chave, 1, Integer::sum);
        }
        return descartadas;
    }

    /** Valor com vírgula decimal ("-776,76", "1.234,56"). */
    static BigDecimal decimal(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.strip();
        if (t.contains(",")) {
            t = t.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** O leitor de CSV fecha o fluxo ao terminar; a entrada do ZIP não pode fechar o ZIP inteiro. */
    private static final class NaoFecha extends java.io.FilterInputStream {
        NaoFecha(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }
}
