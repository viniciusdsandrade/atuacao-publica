package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import etl.fontes.ExecutivoSp.Ato;
import etl.fontes.ExecutivoSp.Compromisso;
import tools.jackson.databind.JsonNode;

/**
 * Poder Executivo do Estado de Minas Gerais (ADR-0075):
 * 1. agenda oficial do governador na API da Agência Minas;
 * 2. decretos numerados da legislação mineira nos dados abertos da ALMG.
 * A despesa não entra: o CKAN estadual publica a despesa por empenho, com o credor, e o pacote agregado está vazio.
 */
public final class ExecutivoMg implements Fonte {

    public static final String FONTE_AGENDA = "mg-agenda-governador";
    public static final String FONTE_LEGISLACAO = "almg-legislacao";
    public static final String URL_AGENDA = "https://www.agenciaminas.mg.gov.br/api/governorSchedule";
    public static final String URL_LEGISLACAO = "https://dadosabertos.almg.gov.br/api/v2/legislacao/mineira/pesquisa/direcionada";
    public static final String URL_NORMA = "https://www.almg.gov.br/legislacao-mineira/texto/";
    public static final String UF = "MG";

    /** Início da cobertura (ADR-0009). */
    static final LocalDate INICIO = LocalDate.of(2022, 1, 1);
    static final int POR_PAGINA = 100;
    /** Limite de páginas por mês ou ano: a API da Agência Minas não informa o total filtrado. */
    static final int MAX_PAGINAS = 20;

    private static final Pattern PARAGRAFO = Pattern.compile("(?is)<p[^>]*>(.*?)</p>");
    private static final Pattern IMPRENSA = Pattern.compile("(?i)^(fechado|aberto) (para|à) (a )?imprensa\\.?$");
    private static final Pattern MUNICIPIO = Pattern.compile("^[^,;:]{2,80}\\([A-Z]{2}\\)\\.?$");

    @Override
    public String nome() {
        return "executivomg";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        YearMonth atual = YearMonth.from(ctx.hoje());
        for (YearMonth mes = YearMonth.from(INICIO); !mes.isAfter(atual) && !coleta.orcamentoExaurido(); mes = mes.plusMonths(1)) {
            // O mês corrente e o anterior seguem abertos: a agenda recebe compromissos e correções depois do dia.
            Politica politica = mes.isBefore(atual.minusMonths(1)) ? Politica.SE_AUSENTE : Politica.SEMPRE;
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                Coleta.Arquivo arq = coleta.obter(urlAgenda(mes, pagina), caminhoAgenda(mes, pagina), "application/json", politica);
                if (Json.ler(Coleta.caminho(ctx, arq)).path("results").size() < POR_PAGINA) {
                    break;
                }
            }
        }
        for (int ano = INICIO.getYear(); ano <= atual.getYear() && !coleta.orcamentoExaurido(); ano++) {
            Politica politica = Politica.porAno(ano, ctx.hoje());
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                Coleta.Arquivo arq = coleta.obter(urlDecretos(ano, pagina), caminhoDecretos(ano, pagina), "application/json", politica);
                int total = Json.ler(Coleta.caminho(ctx, arq)).path("resultado").path("noOcorrencias").asInt();
                if (pagina * POR_PAGINA >= total) {
                    break;
                }
            }
        }
        coleta.periodo(new Meta.Periodo(INICIO, ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path saida = ctx.normalizado().resolve(nome());
        Files.createDirectories(saida);
        List<Compromisso> compromissos = new ArrayList<>();
        List<Ato> atos = new ArrayList<>();
        YearMonth atual = YearMonth.from(ctx.hoje());
        for (YearMonth mes = YearMonth.from(INICIO); !mes.isAfter(atual); mes = mes.plusMonths(1)) {
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), caminhoAgenda(mes, pagina));
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                compromissos.addAll(compromissos(Json.ler(Coleta.caminho(ctx, arq)), Prov.de(FONTE_AGENDA, arq.url(), arq)));
            }
        }
        for (int ano = INICIO.getYear(); ano <= atual.getYear(); ano++) {
            for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), caminhoDecretos(ano, pagina));
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                atos.addAll(decretos(Json.ler(Coleta.caminho(ctx, arq)), arq));
            }
        }

        Map<String, Compromisso> unicos = new LinkedHashMap<>();
        for (Compromisso c : compromissos) {
            unicos.putIfAbsent(c.data() + "|" + c.hora() + "|" + c.textoOficial(), c);
        }
        List<Compromisso> agenda = new ArrayList<>(unicos.values());
        agenda.sort(Comparator.comparing(Compromisso::data).thenComparing(c -> c.hora() == null ? "" : c.hora()));
        Map<String, Ato> porId = new LinkedHashMap<>();
        for (Ato a : atos) {
            porId.putIfAbsent(a.idFonte(), a);
        }
        List<Ato> decretos = new ArrayList<>(porId.values());
        decretos.sort(Comparator.comparing(Ato::data).reversed().thenComparing(Ato::idFonte));

        Json.gravar(saida.resolve("compromissos.json"), Json.SAIDA.valueToTree(agenda));
        Json.gravar(saida.resolve("atos.json"), Json.SAIDA.valueToTree(decretos));
        Json.gravar(saida.resolve("despesas.json"), Json.SAIDA.createArrayNode());
        System.out.println("executivomg: " + agenda.size() + " compromissos e " + decretos.size() + " decretos normalizados");
    }

    /**
     * Compromissos de uma página da API. A hora vem com o sufixo Z, mas é a hora local publicada: a posse de
     * 2023-01-01 sai às 09:00. Dia marcado "Sem agenda oficial" não vira compromisso.
     */
    static List<Compromisso> compromissos(JsonNode pagina, Prov prov) {
        List<Compromisso> lista = new ArrayList<>();
        for (JsonNode r : pagina.path("results")) {
            String data = Json.texto(r, "date");
            String titulo = limpar(Json.texto(r, "title"));
            if (data == null || data.length() < 16 || titulo == null || titulo.equalsIgnoreCase("Sem agenda oficial")) {
                continue;
            }
            if (r.has("publish") && r.get("publish").asInt() != 1) {
                continue;
            }
            String local = null;
            List<String> descricao = new ArrayList<>();
            boolean proximoELocal = false;
            Matcher m = PARAGRAFO.matcher(Json.texto(r, "body") == null ? "" : Json.texto(r, "body"));
            while (m.find()) {
                String p = limpar(m.group(1));
                if (p == null || IMPRENSA.matcher(p).matches()) {
                    continue;
                }
                if (p.equalsIgnoreCase("Local:")) {
                    proximoELocal = true;
                } else if (proximoELocal || MUNICIPIO.matcher(p).matches()) {
                    local = local == null ? p : local + ", " + p;
                    proximoELocal = false;
                } else {
                    descricao.add(p);
                }
            }
            String texto = descricao.isEmpty() ? titulo : titulo + ". " + String.join(" ", descricao);
            lista.add(new Compromisso(UF, data.substring(0, 10), data.substring(11, 16), Guarda.sanitizar(texto),
                    local == null ? null : Guarda.sanitizar(local), List.of(), null, prov));
        }
        return lista;
    }

    /** Decretos numerados do Executivo. O decreto sem número (DNE), em geral ato de pessoal, fica de fora. */
    static List<Ato> decretos(JsonNode pagina, Coleta.Arquivo arq) {
        List<Ato> lista = new ArrayList<>();
        for (JsonNode item : pagina.path("resultado").path("listaItem")) {
            String numero = Json.texto(item, "numero");
            String ano = Json.texto(item, "ano");
            String data = Json.texto(item, "data");
            if (!"DEC".equals(Json.texto(item, "tipo")) || numero == null || ano == null || data == null || data.length() != 8
                    || !"Executivo".equalsIgnoreCase(Json.texto(item, "origem"))) {
                continue;
            }
            String iso = data.substring(0, 4) + "-" + data.substring(4, 6) + "-" + data.substring(6, 8);
            if (iso.compareTo(INICIO.toString()) < 0) {
                continue;
            }
            String ementa = limpar(Json.texto(item, "ementa"));
            Prov prov = Prov.de(FONTE_LEGISLACAO, URL_NORMA + "DEC/" + numero + "/" + ano + "/", arq);
            lista.add(new Ato(UF, "mg-dec-" + numero + "-" + ano, "DEC", numero, iso, "Decreto nº " + numero,
                    ementa == null ? null : Guarda.sanitizar(ementa), prov));
        }
        return lista;
    }

    /** Tira as tags e as entidades HTML e junta os espaços. */
    static String limpar(String html) {
        if (html == null) {
            return null;
        }
        return Texto.semEntidades(html.replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " "));
    }

    static String urlAgenda(YearMonth mes, int pagina) {
        return URL_AGENDA + "?publish=1&page=" + pagina + "&limit=" + POR_PAGINA + "&orderBy=asc&orderByColumn=date&startDate="
                + mes.atDay(1) + "&endDate=" + mes.atEndOfMonth();
    }

    static String urlDecretos(int ano, int pagina) {
        return URL_LEGISLACAO + "?tp=" + POR_PAGINA + "&p=" + pagina + "&tipo=DEC&ano=" + ano + "&formato=json";
    }

    private static String caminhoAgenda(YearMonth mes, int pagina) {
        return "agenda/" + mes + "-" + pagina + ".json";
    }

    private static String caminhoDecretos(int ano, int pagina) {
        return "decretos/" + ano + "-" + pagina + ".json";
    }
}
