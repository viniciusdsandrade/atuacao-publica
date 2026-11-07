package etl.fontes;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Registros;
import etl.Registros.Despesa;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Adaptador da Assembleia Legislativa do Estado do Rio de Janeiro (ALERJ) (ADR-0018).
 * Coleta os 70 deputados estaduais em exercício na 13ª legislatura (2023–2027),
 * dados de posse, mandatos anteriores e links oficiais.
 */
public final class Alerj implements Fonte {

    public static final String FONTE_DEPUTADOS = "alerj-deputados";
    public static final String FONTE_DETALHES = "alerj-deputados-detalhes";
    public static final String FONTE_PORTAL = "alerj-portal";

    public static final String SITE = "https://www.alerj.rj.gov.br";
    public static final String UF = "RJ";
    public static final String FONTE_DESPESAS = "alerj-docigp";
    public static final String API_DOCIGP = "https://docigp.alerj.rj.gov.br/api/v1";
    /** Legislatura de 2023 a 2027 no DOCIGP. */
    static final int LEGISLATURA_DOCIGP = 2;

    /** Centro de custo do DOCIGP. Código em algarismo romano é despesa; código numérico é movimento da conta. */
    public record CentroDeCusto(String codigo, String nome) {
        boolean despesa() {
            return codigo != null && !codigo.matches("\\d+");
        }
    }

    public static final LocalDate INICIO_LEG_13 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_13 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_13 = 13;

    public static final LocalDate INICIO_LEG_12 = LocalDate.of(2019, 2, 1);
    public static final LocalDate FIM_LEG_12 = LocalDate.of(2023, 1, 31);
    public static final int LEGISLATURA_12 = 12;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "<div class=\"controle_deputado(?:\\s+lider)?\">.*?" +
                    "<a href=\"/Deputados/PerfilDeputado/([0-9]+)\\?Legislatura=20\">.*?" +
                    "<img\\s+src=\"([^\"]+)\".*?" +
                    "<div class=\"partido\">([^<]+)</div>.*?" +
                    "<div class=\"nome\"><a[^>]*>([^<]+)</a></div>",
            Pattern.DOTALL);

    private static final Pattern POSSE_PATTERN = Pattern.compile(
            "Posse na ALERJ:[^\\d]+([0-9]{2}/[0-9]{2}/[0-9]{4})");

    private static final Pattern LEG_ANTERIOR_PATTERN = Pattern.compile(
            "12[ºª°]\\s*Legislatura|Legislaturas Anteriores");

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alerj";
    }

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alerj: coleta iniciada");
        coleta.obter(SITE + "/Deputados/QuemSao", "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Deputados/PerfilDeputado/" + d.id() + "?Legislatura=20";
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        // Verba de gabinete pelo DOCIGP. O remote_id do parlamentar é o id do perfil na ALERJ.
        coleta.obterOpcional(API_DOCIGP + "/cost-centers", "docigp/centros-de-custo.json", "application/json", Politica.SEMANAL);
        Coleta.Arquivo primeira = coleta.obterOpcional(API_DOCIGP + "/congressmen?page=1", "docigp/parlamentares-1.json",
                "application/json", Politica.SEMANAL);
        int paginasDeParlamentares = ultimaPagina(ctx, primeira);
        for (int p = 2; p <= paginasDeParlamentares; p++) {
            coleta.obterOpcional(API_DOCIGP + "/congressmen?page=" + p, "docigp/parlamentares-" + p + ".json",
                    "application/json", Politica.SEMANAL);
        }
        Map<String, String> docigpPorAlerj = parlamentaresDoDocigp(ctx, paginasDeParlamentares);
        List<String> ids = new ArrayList<>();
        for (DeputadoBruto d : deputados) {
            if (docigpPorAlerj.containsKey(d.id())) {
                ids.add(d.id());
            }
        }
        // A lista de orçamentos vem do mês mais novo para o mais velho, 20 por página: um mês novo desloca todas as
        // páginas. Quando a primeira página renova, as outras renovam junto, e o retrato do deputado fica consistente.
        List<String> falhas = Camara.paraCadaDeputado(ids, id -> {
            String base = API_DOCIGP + "/congressmen/" + docigpPorAlerj.get(id) + "/legislatures/" + LEGISLATURA_DOCIGP + "/budgets?page=";
            Coleta.Arquivo p1 = coleta.obterOpcional(base + 1, arquivoDeOrcamentos(id, 1), "application/json", Politica.SEMANAL);
            if (p1 == null) {
                return;
            }
            boolean renovada = !p1.capturadoEm().isBefore(ctx.agora());
            int paginas = ultimaPagina(ctx, p1);
            for (int p = 2; p <= paginas; p++) {
                coleta.obterOpcional(base + p, arquivoDeOrcamentos(id, p), "application/json",
                        renovada ? Politica.SEMPRE : Politica.SE_AUSENTE);
            }
        });
        for (String falha : falhas) {
            coleta.falhaParcial("docigp", falha);
        }
    }

    private static String arquivoDeOrcamentos(String id, int pagina) {
        return "docigp/orcamentos/" + id + "-" + pagina + ".json";
    }

    /** Última página de uma listagem paginada do DOCIGP; 1 se a listagem faltar. */
    private static int ultimaPagina(Contexto ctx, Coleta.Arquivo arquivo) {
        if (arquivo == null || arquivo.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arquivo))) {
            return 1;
        }
        return Math.max(1, Json.ler(Coleta.caminho(ctx, arquivo)).path("links").path("pagination").path("last_page").asInt(1));
    }

    /** remote_id (id do perfil na ALERJ) para o id do parlamentar no DOCIGP. */
    private static Map<String, String> parlamentaresDoDocigp(Contexto ctx, int paginas) {
        Map<String, String> mapa = new HashMap<>();
        for (int p = 1; p <= paginas; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alerj", "docigp/parlamentares-" + p + ".json");
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            for (JsonNode parlamentar : Json.ler(Coleta.caminho(ctx, arq)).path("rows")) {
                String alerj = Json.texto(parlamentar, "remote_id");
                String docigp = Json.texto(parlamentar, "id");
                if (alerj != null && docigp != null) {
                    mapa.put(alerj, docigp);
                }
            }
        }
        return mapa;
    }

    /** Centros de custo por id, da cópia em cache. */
    private static Map<String, CentroDeCusto> centrosDeCusto(Contexto ctx) {
        Map<String, CentroDeCusto> centros = new HashMap<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alerj", "docigp/centros-de-custo.json");
        if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
            return centros;
        }
        for (JsonNode centro : Json.ler(Coleta.caminho(ctx, arq)).path("rows")) {
            String id = Json.texto(centro, "id");
            if (id != null) {
                centros.put(id, new CentroDeCusto(Json.texto(centro, "code"), Json.texto(centro, "name")));
            }
        }
        return centros;
    }

    /**
     * Despesas de um orçamento mensal do DOCIGP. Só entram lançamentos de centro de custo de despesa; crédito,
     * transporte e devolução de saldo ficam fora. A despesa vem com sinal negativo na fonte e entra positiva.
     * A categoria é o nome do centro de custo, sem o código. CNPJ fica; CPF de pessoa física e endereço não entram.
     */
    public static List<Despesa> despesasDoOrcamento(JsonNode orcamento, Map<String, CentroDeCusto> centros, Prov prov) {
        String competencia = Json.texto(orcamento.path("budget"), "date");
        if (competencia == null || competencia.length() < 7) {
            return List.of();
        }
        int ano = Integer.parseInt(competencia.substring(0, 4));
        int mes = Integer.parseInt(competencia.substring(5, 7));
        List<Despesa> despesas = new ArrayList<>();
        for (JsonNode lancamento : orcamento.path("entries")) {
            CentroDeCusto centro = centros.get(Json.texto(lancamento, "cost_center_id"));
            BigDecimal valor = Texto.valor(Json.texto(lancamento, "value"));
            if (centro == null || !centro.despesa() || valor == null) {
                continue;
            }
            JsonNode fornecedor = lancamento.path("provider");
            String documento = Json.texto(fornecedor, "cpf_cnpj");
            String digitos = documento == null ? "" : documento.replaceAll("\\D", "");
            String nome = Json.texto(fornecedor, "name");
            String data = Json.texto(lancamento, "date");
            LocalDate dia = null;
            try {
                dia = data == null || data.length() < 10 ? null : LocalDate.parse(data.substring(0, 10));
            } catch (java.time.format.DateTimeParseException ignorada) {
                // data fora do padrão: o lançamento fica sem data, com ano e mês do orçamento
            }
            despesas.add(new Despesa(dia, ano, mes, semCodigo(centro.nome()),
                    Guarda.semCpf(nome != null ? nome : Json.texto(lancamento, "to")),
                    digitos.length() == 14 ? digitos : null, valor.negate(), Json.texto(lancamento, "document_number"), null, prov));
        }
        return despesas;
    }

    /** "VII - Combustíveis e lubrificantes" vira "Combustíveis e lubrificantes". */
    private static String semCodigo(String nome) {
        return nome == null ? null : nome.replaceFirst("^[IVXL]+(\\.[a-z])?\\s*-\\s*", "").strip();
    }

    /** Despesas de cada deputado, pelas páginas de orçamentos em cache. Um orçamento repetido entre páginas entra uma vez. */
    private Map<String, List<Despesa>> despesasPorDeputado(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, CentroDeCusto> centros = centrosDeCusto(ctx);
        Map<String, List<Despesa>> porDeputado = new HashMap<>();
        int lancamentos = 0;
        for (DeputadoBruto d : deputados) {
            Map<String, List<Despesa>> porOrcamento = new LinkedHashMap<>();
            for (int p = 1; ; p++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDeOrcamentos(d.id(), p));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                Prov prov = Prov.de(FONTE_DESPESAS, arq.url(), arq);
                for (JsonNode orcamento : Json.ler(Coleta.caminho(ctx, arq)).path("rows")) {
                    String id = Json.texto(orcamento, "id");
                    if (id != null && !porOrcamento.containsKey(id)) {
                        porOrcamento.put(id, despesasDoOrcamento(orcamento, centros, prov));
                    }
                }
            }
            List<Despesa> todas = new ArrayList<>();
            porOrcamento.values().forEach(todas::addAll);
            if (!todas.isEmpty()) {
                porDeputado.put(d.id(), todas);
                lancamentos += todas.size();
            }
        }
        System.out.println("alerj: " + lancamentos + " lançamentos da verba de gabinete (DOCIGP) em " + porDeputado.size() + " deputados");
        return porDeputado;
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alerj: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, SITE + "/Deputados/QuemSao", arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alerj/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alerj: erro ao criar pasta de blocos", e);
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, List<Despesa>> despesasPorDeputado = despesasPorDeputado(ctx, deputados);

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeFormatado = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, SITE + "/Deputados/PerfilDeputado/" + id + "?Legislatura=20", arqPerfil) : provDeputados;

            String htmlPerfil = null;
            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                } catch (Exception ignored) {
                }
            }

            LocalDate dataPosse = extrairPosseDoHtml(htmlPerfil);
            if (dataPosse == null) {
                dataPosse = INICIO_LEG_13;
            }

            List<Mandato> mandatos = new ArrayList<>();

            if (temLegislaturaAnterior(htmlPerfil)) {
                mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                        INICIO_LEG_12, FIM_LEG_12, "Encerrado", "Titular", LEGISLATURA_12, 2018,
                        "Deputado Estadual na 12ª Legislatura (2019–2023)", provPerfil));
            }

            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    dataPosse, FIM_LEG_13, "Em exercício", "Titular", LEGISLATURA_13, 2022,
                    "Deputado Estadual na 13ª Legislatura (2023–2027)", provPerfil));

            mandatos.sort(Comparator.comparing(Mandato::inicio));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALERJ", SITE + "/Deputados/PerfilDeputado/" + id + "?Legislatura=20", provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alerj", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.createArrayNode());
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesasPorDeputado.getOrDefault(id, List.of())));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        Json.gravar(ctx.normalizado().resolve("alerj/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alerj: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alerj", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alerj: erro ao ler deputados.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoBruto> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = CARD_PATTERN.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            if (vistos.add(id)) {
                String foto = m.group(2).trim().replace(" ", "%20");
                String fotoUrl = foto.startsWith("/") ? SITE + foto : foto;
                String nome = Texto.semEntidades(m.group(4).trim());
                String partido = Texto.semEntidades(m.group(3).trim());
                lista.add(new DeputadoBruto(id, nome, partido, fotoUrl));
            }
        }
        return lista;
    }

    public static LocalDate extrairPosseDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = POSSE_PATTERN.matcher(html);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1).trim(), DATA_BR);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean temLegislaturaAnterior(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return LEG_ANTERIOR_PATTERN.matcher(html).find();
    }

    public static String formatarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return nome;
        }
        String[] partes = nome.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            if (p.isEmpty()) continue;
            if (i > 0 && PREPOSICOES.contains(p)) {
                sb.append(p);
            } else {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) {
                    sb.append(p.substring(1));
                }
            }
            if (i < partes.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
