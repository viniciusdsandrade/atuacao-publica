package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import etl.Json;
import etl.Registros;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado do Ceará (ALECE) (ADR-0020).
 * Coleta os deputados estaduais em exercício na 31ª legislatura (2023–2027),
 * dados de mandato, fotografias oficiais e links institucionais.
 */
public final class Alece implements Fonte {

    public static final String FONTE_DEPUTADOS = "alece-deputados";
    public static final String FONTE_DETALHES = "alece-deputados-detalhes";
    public static final String FONTE_PORTAL = "alece-portal";

    public static final String SITE = "https://www.al.ce.gov.br";
    public static final String UF = "CE";

    public static final LocalDate INICIO_LEG_31 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_31 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_31 = 31;

    private static final Pattern CARD_CONTAINER_PATTERN = Pattern.compile(
            "<div\\s+[^>]*class=\"deputado_card[^\"]*\"[^>]*>");

    private static final Pattern IMG_PATTERN = Pattern.compile("src=\"([^\"]+)\"");
    private static final Pattern SLUG_NOME_PATTERN = Pattern.compile(
            "href=\"https://www\\.al\\.ce\\.gov\\.br/deputados/([^\"]+)\">\\s*([^<]+)</a>");
    private static final Pattern PARTIDO_PATTERN = Pattern.compile(
            "class=\"deputado_card--partido[^\"]*\">\\s*([^<]+)\\s*</p>");

    public static final List<String> COMISSOES_SLUGS = List.of(
            "agropecuaria",
            "ciencia-tecnologia-e-educacao-superior",
            "constituicao-justica-e-redacao",
            "cultura-e-esportes",
            "defesa-do-consumidor",
            "defesa-e-direitos-da-mulher",
            "defesa-social",
            "desenvolvimento-regional-recursos-hidricos-minas-e-pesca",
            "direitos-humanos-e-cidadania",
            "educacao-basica",
            "fiscalizacao-e-controle",
            "industria-desenvolvimento-economico-e-comercio",
            "da-infancia-e-adolescencia",
            "juventude",
            "orcamento-financas-e-tributacao",
            "previdencia-social-e-saude",
            "protecao-social-e-combate-a-fome",
            "trabalho-administracao-e-servico-publico",
            "turismo-e-servicos",
            "viacao-transporte-desenvolvimento-urbano"
    );

    public static final Pattern TITULO_COMISSAO_PATTERN = Pattern.compile(
            "<h1[^>]*>Composição\\s*-\\s*([^<]+)</h1>", Pattern.CASE_INSENSITIVE);

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alece";
    }

    public record DeputadoBruto(String slug, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alece: coleta iniciada");
        coleta.obter(SITE + "/deputados", "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/deputados/" + d.slug();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.slug() + ".html", "text/html", Politica.SEMANAL);
        }

        for (String comSlug : COMISSOES_SLUGS) {
            String url = SITE + "/comissoes-permanentes/" + comSlug + "/composicao";
            coleta.obterOuVazio(url, "comissoes/" + comSlug + ".html", "text/html", Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alece: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, SITE + "/deputados", arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alece/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alece: erro ao criar pasta de blocos", e);
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, List<OrgaoOuFrente>> comissoesPorSlug = extrairComissoes(ctx);

        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String nomeFormatado = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();

            String urlPerfil = SITE + "/deputados/" + slug;
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + slug + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_31, FIM_LEG_31, "Em exercício", "Titular", LEGISLATURA_31, 2022,
                    "Deputado Estadual na 31ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALECE", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alece", slug, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<OrgaoOuFrente> orgaos = comissoesPorSlug.getOrDefault(slug, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(slug + ".json"), blocosNode);
        }

        Json.gravar(ctx.normalizado().resolve("alece/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alece: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx) {
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        for (String comSlug : COMISSOES_SLUGS) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alece", "comissoes/" + comSlug + ".html");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                String url = SITE + "/comissoes-permanentes/" + comSlug + "/composicao";
                Prov prov = Prov.de(FONTE_DETALHES, url, arq);
                Map<String, List<OrgaoOuFrente>> doHtml = extrairComissoesDoHtml(html, comSlug, prov);
                for (var entry : doHtml.entrySet()) {
                    mapa.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("alece: erro ao ler comissao " + comSlug + ": " + e.getMessage());
            }
        }
        return mapa;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(String html, String comSlug, Prov prov) {
        if (html == null || html.isBlank()) {
            return Map.of();
        }
        String nomeComissao = "Comissão";
        Matcher mTitulo = TITULO_COMISSAO_PATTERN.matcher(html);
        if (mTitulo.find()) {
            nomeComissao = "Comissão de " + Texto.semEntidades(mTitulo.group(1).trim());
        }

        Matcher mTable = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL).matcher(html);
        if (!mTable.find()) {
            return Map.of();
        }
        String table = mTable.group(1);
        String[] sections = Pattern.compile("<span[^>]*><strong>(PRESIDENTE|VICE-PRESIDENTE|MEMBROS?)</strong></span>", Pattern.CASE_INSENSITIVE).split(table);
        Matcher mHeaders = Pattern.compile("<span[^>]*><strong>(PRESIDENTE|VICE-PRESIDENTE|MEMBROS?)</strong></span>", Pattern.CASE_INSENSITIVE).matcher(table);

        List<String> headerList = new ArrayList<>();
        while (mHeaders.find()) {
            headerList.add(mHeaders.group(1).toUpperCase(Locale.ROOT));
        }

        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();
        for (int i = 0; i < headerList.size() && i + 1 < sections.length; i++) {
            String secHeader = headerList.get(i);
            String content = sections[i + 1];
            String[] cells = Pattern.compile("<td[^>]*>").split(content);
            for (String cell : cells) {
                Matcher mDep = Pattern.compile("href=\"https://www\\.al\\.ce\\.gov\\.br/deputados/([a-z0-9-]+)\"").matcher(cell);
                if (mDep.find()) {
                    String slug = mDep.group(1).trim();
                    boolean isSuplente = cell.contains("Suplente");
                    String papel;
                    if (secHeader.contains("PRESIDENTE") && !secHeader.contains("VICE")) {
                        papel = isSuplente ? "Presidente (Suplente)" : "Presidente (Titular)";
                    } else if (secHeader.contains("VICE")) {
                        papel = isSuplente ? "Vice-Presidente (Suplente)" : "Vice-Presidente (Titular)";
                    } else {
                        papel = isSuplente ? "Suplente" : "Membro";
                    }
                    resultado.computeIfAbsent(slug, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", nomeComissao, null, papel, INICIO_LEG_31, null, prov));
                }
            }
        }
        return resultado;
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alece", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alece: erro ao ler deputados.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String[] partes = CARD_CONTAINER_PATTERN.split(html);
        if (partes.length <= 1) {
            return List.of();
        }
        List<DeputadoBruto> deputados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (int i = 1; i < partes.length; i++) {
            String trecho = partes[i];
            Matcher mImg = IMG_PATTERN.matcher(trecho);
            Matcher mSlug = SLUG_NOME_PATTERN.matcher(trecho);
            Matcher mPartido = PARTIDO_PATTERN.matcher(trecho);
            if (mImg.find() && mSlug.find() && mPartido.find()) {
                String slug = mSlug.group(1).trim();
                if (vistos.add(slug)) {
                    String foto = mImg.group(1).trim().replace(" ", "%20");
                    String nome = Texto.semEntidades(mSlug.group(2).trim());
                    String partido = Texto.semEntidades(mPartido.group(1).trim());
                    deputados.add(new DeputadoBruto(slug, nome, partido, foto));
                }
            }
        }
        return deputados;
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
