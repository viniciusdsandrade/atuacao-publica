package etl.fontes;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Csv;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import etl.fontes.ExecutivoSp.Agregado;
import etl.fontes.ExecutivoSp.Ato;
import tools.jackson.databind.JsonNode;

/**
 * Poder Executivo do Estado de Goiás (ADR-0076):
 * 1. decretos numerados na API de legislação da Casa Civil;
 * 2. execução orçamentária mensal por órgão no portal de dados abertos (CKAN), já agregada pela fonte e sem favorecido.
 * O estado não publica a agenda do governador em formato estruturado.
 */
public final class ExecutivoGo implements Fonte {

    public static final String FONTE_LEGISLACAO = "go-legisla";
    public static final String FONTE_DESPESA = "go-execucao-orcamentaria";
    public static final String URL_LEGISLACAO = "https://legisla.casacivil.go.gov.br/api/v2/pesquisa/legislacoes";
    public static final String URL_PACOTE = "https://dadosabertos.go.gov.br/api/3/action/package_show?id=execucao-orcamentaria-natureza-despesa";
    public static final String UF = "GO";

    /** Início da cobertura (ADR-0009). */
    static final int ANO_INICIAL = 2022;
    static final int POR_PAGINA = 100;
    static final int MAX_PAGINAS = 20;
    /** Tipo "Decreto Numerado" na API (lista em /api/v1/tipos_legislacoes). */
    static final int TIPO_DECRETO = 3;

    /** Recurso anual ("Execução Orçamentária Natureza Despesa - 2025") ou mensal ("... - Março/2026"). */
    /** O ZIP de vários anos ("... Despesa 2003 - 2025") fica de fora: somaria o último ano duas vezes. */
    private static final Pattern RECURSO_ANUAL = Pattern.compile("(?i)despesa - (\\d{4})$");
    private static final Pattern RECURSO_MENSAL = Pattern.compile("(?i)despesa - ([a-zç]+)/(\\d{4})$");
    private static final List<String> MESES = List.of("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho",
            "agosto", "setembro", "outubro", "novembro", "dezembro");

    @Override
    public String nome() {
        return "executivogo";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        int anoAtual = ctx.hoje().getYear();
        for (int ano = ANO_INICIAL; ano <= anoAtual && !coleta.orcamentoExaurido(); ano++) {
            Politica politica = Politica.porAno(ano, ctx.hoje());
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                Coleta.Arquivo arq = coleta.obter(urlDecretos(ano, pagina), caminhoDecretos(ano, pagina), "application/json", politica);
                int total = Json.ler(Coleta.caminho(ctx, arq)).path("total_resultados").asInt();
                if (pagina * POR_PAGINA >= total) {
                    break;
                }
            }
        }
        // O pacote lista um ZIP por ano fechado e um CSV por mês do ano corrente: os endereços vêm dele.
        Coleta.Arquivo pacote = coleta.obter(URL_PACOTE, "despesa/pacote.json", "application/json", Politica.SEMANAL);
        for (Recurso r : recursos(Json.ler(Coleta.caminho(ctx, pacote)))) {
            if (coleta.orcamentoExaurido()) {
                break;
            }
            Politica politica = r.mes() == 0 ? Politica.porAnoSemanal(r.ano(), ctx.hoje()) : Politica.porAno(r.ano(), ctx.hoje());
            coleta.obter(r.url(), r.caminho(), r.mes() == 0 ? "application/zip" : "text/csv", politica);
        }
        coleta.periodo(new Meta.Periodo(LocalDate.of(ANO_INICIAL, 1, 1), ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path saida = ctx.normalizado().resolve(nome());
        Files.createDirectories(saida);
        int anoAtual = ctx.hoje().getYear();

        Map<String, Ato> porId = new LinkedHashMap<>();
        for (int ano = ANO_INICIAL; ano <= anoAtual; ano++) {
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), caminhoDecretos(ano, pagina));
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                for (Ato a : decretos(Json.ler(Coleta.caminho(ctx, arq)), arq)) {
                    porId.putIfAbsent(a.idFonte(), a);
                }
            }
        }
        List<Ato> atos = new ArrayList<>(porId.values());
        atos.sort(Comparator.comparing(Ato::data).reversed().thenComparing(Ato::idFonte));

        Somas somas = new Somas();
        Coleta.Arquivo pacote = Coleta.metadados(ctx, nome(), "despesa/pacote.json");
        if (pacote != null && Files.exists(Coleta.caminho(ctx, pacote))) {
            List<Recurso> recursos = recursos(Json.ler(Coleta.caminho(ctx, pacote)));
            for (Recurso r : recursos) {
                // O ano fechado vem do ZIP; os CSVs mensais só contam no ano que ainda não tem ZIP.
                boolean temAnual = recursos.stream().anyMatch(o -> o.mes() == 0 && o.ano() == r.ano());
                if (r.mes() != 0 && temAnual) {
                    continue;
                }
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), r.caminho());
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                Prov prov = Prov.de(FONTE_DESPESA, r.url(), arq);
                if (r.mes() == 0) {
                    try (ZipFile zip = new ZipFile(Coleta.caminho(ctx, arq).toFile())) {
                        for (ZipEntry entrada : java.util.Collections.list(zip.entries())) {
                            if (entrada.getName().toLowerCase().endsWith(".csv")) {
                                try (InputStream in = zip.getInputStream(entrada)) {
                                    somas.ler(in, prov);
                                }
                            }
                        }
                    }
                } else {
                    try (InputStream in = Json.abrir(Coleta.caminho(ctx, arq))) {
                        somas.ler(in, prov);
                    }
                }
            }
        }
        List<Agregado> despesas = somas.agregados();

        Json.gravar(saida.resolve("atos.json"), Json.SAIDA.valueToTree(atos));
        Json.gravar(saida.resolve("despesas.json"), Json.SAIDA.valueToTree(despesas));
        Json.gravar(saida.resolve("compromissos.json"), Json.SAIDA.createArrayNode());
        System.out.println("executivogo: " + atos.size() + " decretos e " + despesas.size() + " agregados de despesa normalizados");
    }

    /** Recurso de despesa do pacote: mês 0 é o ZIP do ano. */
    record Recurso(int ano, int mes, String url) {
        String caminho() {
            return mes == 0 ? "despesa/" + ano + ".zip" : String.format("despesa/%04d-%02d.csv", ano, mes);
        }
    }

    /** Recursos de despesa do pacote a partir do ano inicial, em ordem. */
    static List<Recurso> recursos(JsonNode pacote) {
        List<Recurso> lista = new ArrayList<>();
        for (JsonNode r : pacote.path("result").path("resources")) {
            String nome = Json.texto(r, "name");
            String url = Json.texto(r, "url");
            if (nome == null || url == null) {
                continue;
            }
            Matcher anual = RECURSO_ANUAL.matcher(nome.strip());
            Matcher mensal = RECURSO_MENSAL.matcher(nome.strip());
            if (anual.find() && url.toLowerCase().endsWith(".zip")) {
                lista.add(new Recurso(Integer.parseInt(anual.group(1)), 0, url));
            } else if (mensal.find() && url.toLowerCase().endsWith(".csv")) {
                int mes = MESES.indexOf(mensal.group(1).toLowerCase()) + 1;
                if (mes > 0) {
                    lista.add(new Recurso(Integer.parseInt(mensal.group(2)), mes, url));
                }
            }
        }
        lista.removeIf(r -> r.ano() < ANO_INICIAL);
        lista.sort(Comparator.comparingInt(Recurso::ano).thenComparingInt(Recurso::mes));
        return lista;
    }

    /** Decretos numerados de uma página da API. O número sai sem o ponto de milhar ("10.891" vira "10891"). */
    static List<Ato> decretos(JsonNode pagina, Coleta.Arquivo arq) {
        List<Ato> lista = new ArrayList<>();
        for (JsonNode item : pagina.path("resultados")) {
            String numero = Json.texto(item, "numero");
            String data = Json.texto(item, "data_legislacao");
            if (numero == null || data == null || !data.matches("\\d{2}/\\d{2}/\\d{4}")
                    || item.path("tipo_legislacao").path("id").asInt() != TIPO_DECRETO) {
                continue;
            }
            String limpo = numero.replace(".", "").strip();
            String iso = data.substring(6, 10) + "-" + data.substring(3, 5) + "-" + data.substring(0, 2);
            String ementa = Texto.semEntidades(Json.texto(item, "ementa"));
            lista.add(new Ato(UF, "go-dec-" + limpo, "DEC", limpo, iso, "Decreto nº " + numero.strip(),
                    ementa == null ? null : Guarda.sanitizar(ementa), Prov.de(FONTE_LEGISLACAO, arq.url(), arq)));
        }
        return lista;
    }

    /** Soma do valor pago por mês e órgão. A quantidade conta as linhas da fonte com valor pago, não pagamentos. */
    static final class Somas {
        private final Map<String, BigDecimal> valor = new TreeMap<>();
        private final Map<String, Integer> linhas = new TreeMap<>();
        private final Map<String, Prov> provs = new TreeMap<>();

        void ler(InputStream in, Prov prov) {
            Csv.paraCada(in, StandardCharsets.UTF_8, 0, linha -> {
                String anoMes = linha.get("NUMR_ANO_MES");
                String orgao = linha.get("NOME_ORGAO_REDUZIDO_ATUAL");
                String pago = linha.get("VALOR_PAGO");
                if (anoMes == null || !anoMes.matches("\\d{6}") || orgao == null || orgao.isBlank() || pago == null || pago.isBlank()) {
                    return;
                }
                BigDecimal v = new BigDecimal(pago.strip());
                if (v.signum() == 0) {
                    return;
                }
                String chave = anoMes + "|" + orgao.strip();
                valor.merge(chave, v, BigDecimal::add);
                linhas.merge(chave, 1, Integer::sum);
                provs.putIfAbsent(chave, prov);
            });
        }

        List<Agregado> agregados() {
            List<Agregado> lista = new ArrayList<>();
            valor.forEach((chave, v) -> {
                String[] partes = chave.split("\\|", 2);
                int ano = Integer.parseInt(partes[0].substring(0, 4));
                int mes = Integer.parseInt(partes[0].substring(4, 6));
                if (ano >= ANO_INICIAL) {
                    lista.add(new Agregado(UF, ano, mes, partes[1], partes[1], v, linhas.get(chave), provs.get(chave)));
                }
            });
            return lista;
        }
    }

    static String urlDecretos(int ano, int pagina) {
        return URL_LEGISLACAO + "?page=" + pagina + "&qtd_por_pagina=" + POR_PAGINA + "&tipo_legislacao=" + TIPO_DECRETO
                + "&periodo_inicial_legislacao=" + ano + "-01-01&periodo_final_legislacao=" + ano + "-12-31&ordenarPor=data";
    }

    private static String caminhoDecretos(int ano, int pagina) {
        return "decretos/" + ano + "-" + pagina + ".json";
    }
}
