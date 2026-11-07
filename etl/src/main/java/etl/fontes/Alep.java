package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Registros;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado do Paraná (ALEP) (ADR-0016).
 * Coleta os 54 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados biográficos individuais e composição de comissões permanentes e temporárias.
 */
public final class Alep implements Fonte {

    public static final String FONTE_DEPUTADOS = "alep-deputados";
    public static final String FONTE_DETALHES = "alep-deputados-detalhes";
    public static final String FONTE_COMISSOES = "alep-comissoes";
    public static final String FONTE_PORTAL = "alep-portal";

    public static final String SITE = "https://www.assembleia.pr.leg.br";
    public static final String UF = "PR";
    public static final String FONTE_PROPOSICOES = "alep-proposicoes";
    /** API documentada da ALEP. O HTTPS do host tem certificado autoassinado; a URL documentada é HTTP. */
    public static final String API_PROPOSICOES = "http://webservices.assembleia.pr.leg.br/api/public/proposicao/filtrar";
    /** Primeiro ano das proposições: a 20ª legislatura começa em 2023. */
    static final int PRIMEIRO_ANO_PROPOSICOES = 2023;
    /** Nome da autoria que difere do nome e do slug do perfil; cada um corresponde a um único deputado da lista da ALEP. */
    static final Map<String, String> APELIDOS_DE_AUTORIA = Map.of(
            "ARILSON CHIORATO", "arilson-maroldi-chiorato",
            "BAZANA", "pedro-paulo-bazana");

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    public static final LocalDate INICIO_LEG_19 = LocalDate.of(2019, 2, 1);
    public static final LocalDate FIM_LEG_19 = LocalDate.of(2023, 1, 31);
    public static final int LEGISLATURA_19 = 19;

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "href=[\"']https://www\\.assembleia\\.pr\\.leg\\.br/deputados/perfil/([a-z0-9-]+)[\"'].*?" +
                    "<img\\s+src=[\"'](https://storage2\\.assembleia\\.pr\\.leg\\.br/[^\"]+)[\"'].*?" +
                    "<h3[^>]*>\\s*([^<]+)\\s*</h3>.*?" +
                    "<p[^>]*class=[\"'][^\"]*italic[^\"]*[\"']>\\s*([^<]+)\\s*</p>",
            Pattern.DOTALL);

    private static final Pattern BOTAO_TITULO_PATTERN = Pattern.compile("<span[^>]*>\\s*([^<]+)\\s*</span>");
    private static final Pattern LINK_MEMBRO_PATTERN = Pattern.compile(
            "href=[\"']https://www\\.assembleia\\.pr\\.leg\\.br/deputados/perfil/([a-z0-9-]+)[\"'](.*?)(?=href=[\"']https://www\\.assembleia\\.pr\\.leg\\.br/deputados/perfil/|</a>|$)",
            Pattern.DOTALL);
    private static final Pattern PAPEL_BADGE_PATTERN = Pattern.compile("rounded-t-lg\\s+bg-[a-z0-9-]+\">\\s*([^<]+)\\s*</p>");
    private static final Pattern PAPEL_DD14_PATTERN = Pattern.compile("class=[\"'][^\"]*dd-14[^\"]*[\"'][^>]*>\\s*([^<]+)\\s*</p>");

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "e");

    @Override
    public String nome() {
        return "alep";
    }

    public record DeputadoBruto(String slug, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        // Baixa a página principal com a listagem de todos os deputados
        coleta.obter(SITE + "/deputados/conheca", "deputados/conheca.html", "text/html", Politica.SEMANAL);

        // Extrai a lista de deputados e baixa os perfis individuais
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto dep : deputados) {
            coleta.obterOuVazio(SITE + "/deputados/perfil/" + dep.slug(),
                    "perfil/" + dep.slug() + ".html", "text/html", Politica.SEMANAL);
        }

        // Baixa a página de comissões permanentes e temporárias
        coleta.obterOuVazio(SITE + "/atividade-parlamentar/comissoes-permanentes-e-temporarias",
                "comissoes.html", "text/html", Politica.SEMANAL);

        // Proposições: a API só aceita o filtro no corpo (POST). Um ano por consulta, sem limite prático de registros.
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            coleta.obterPostOpcional(API_PROPOSICOES, "{\"ano\":" + ano + ",\"numeroMaximoRegistro\":50000}",
                    "proposicoes/" + ano + ".json", "application/json", Politica.porAnoSemanal(ano, ctx.hoje()));
        }
    }

    /**
     * Chave de vínculo da autoria: sem "DEPUTADO" ou "DEPUTADA", sem acento e pontuação, com "DR" e "DRA" por extenso.
     * Serve para o nome da autoria, o nome do perfil e o slug.
     */
    static String chaveDeAutoria(String nome) {
        String texto = Texto.normalizarNome(nome.replace('-', ' ').replaceAll("[^\\p{L}\\s]", " "));
        texto = texto.replaceFirst("^DEPUTAD[OA] ", "");
        return texto.replaceAll("\\bDRA\\b", "DOUTORA").replaceAll("\\bDR\\b", "DOUTOR");
    }

    /**
     * Proposições de cada deputado na lista anual da API. A autoria é texto, com os autores separados por vírgula;
     * só os que casam com um deputado em exercício entram. A ordem na lista vira {@code ordem_assinatura}.
     */
    public static Map<String, List<Proposicao>> proposicoesDoAno(JsonNode resposta, Map<String, String> slugPorChave, Prov prov) {
        Map<String, List<Proposicao>> porSlug = new HashMap<>();
        for (JsonNode p : resposta.path("lista")) {
            String autoria = Json.texto(p, "autor");
            String tipo = Json.texto(p, "tipoProposicao");
            Integer numero = Json.inteiro(p, "numero");
            Integer ano = Json.inteiro(p, "ano");
            if (autoria == null || tipo == null || numero == null || ano == null) {
                continue;
            }
            String recebimento = Json.texto(p, "dataRecebimento");
            String[] autores = autoria.split(",");
            Set<String> jaContados = new HashSet<>();
            for (int i = 0; i < autores.length; i++) {
                String slug = slugPorChave.get(chaveDeAutoria(autores[i]));
                if (slug == null || !jaContados.add(slug)) {
                    continue;
                }
                porSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(new Proposicao(Json.texto(p, "codigo"), tipo, numero,
                        ano, tipo + " " + numero + "/" + ano, Json.texto(p, "ementa"), Json.texto(p, "status"), null,
                        recebimento == null || recebimento.length() < 10 ? null : recebimento.substring(0, 10), i + 1, null,
                        null, prov));
            }
        }
        return porSlug;
    }

    /** Proposições de cada deputado em exercício, pelas listas anuais em cache. */
    private static Map<String, List<Proposicao>> proposicoesPorSlug(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, String> slugPorChave = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            slugPorChave.put(chaveDeAutoria(formatarNome(d.nome())), d.slug());
            slugPorChave.putIfAbsent(chaveDeAutoria(d.slug()), d.slug());
        }
        Set<String> slugs = new HashSet<>(slugPorChave.values());
        APELIDOS_DE_AUTORIA.forEach((apelido, slug) -> {
            if (slugs.contains(slug)) {
                slugPorChave.putIfAbsent(apelido, slug);
            }
        });
        Map<String, List<Proposicao>> porSlug = new HashMap<>();
        int total = 0;
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alep", "proposicoes/" + ano + ".json");
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            for (var item : proposicoesDoAno(Json.ler(Coleta.caminho(ctx, arq)), slugPorChave,
                    Prov.de(FONTE_PROPOSICOES, arq.url(), arq)).entrySet()) {
                porSlug.computeIfAbsent(item.getKey(), k -> new ArrayList<>()).addAll(item.getValue());
                total += item.getValue().size();
            }
        }
        System.out.println("alep: " + total + " autorias de proposições em " + porSlug.size() + " deputados");
        return porSlug;
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alep: nenhum deputado identificado para normalização");
            return;
        }

        Coleta.Arquivo arqConheca = Coleta.metadados(ctx, "alep", "deputados/conheca.html");
        Prov provConheca = Prov.de(FONTE_DEPUTADOS, SITE + "/deputados/conheca", arqConheca);

        Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, "alep", "comissoes.html");
        Map<String, List<OrgaoOuFrente>> comissoesPorSlug = extrairComissoes(ctx, arqComissoes);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        Map<String, List<Proposicao>> proposicoesPorSlug = proposicoesPorSlug(ctx, deputados);

        for (DeputadoBruto dep : deputados) {
            String slug = dep.slug();
            Acumulado ac = new Acumulado();
            acumulados.put(slug, ac);

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, "alep", "perfil/" + slug + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, SITE + "/deputados/perfil/" + slug,
                    arqPerfil != null && arqPerfil.bytes() > 0 ? arqPerfil : arqConheca);

            String nomeFormatado = formatarNome(dep.nome());
            String fotoUrl = dep.fotoUrl();

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALEP", SITE + "/deputados/perfil/" + slug, provPerfil)
            );

            pessoas.add(new Pessoa("alep", slug, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    dep.partido(), fotoUrl, "em exercício", true, links));

            // Mandato da 20ª legislatura
            ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, INICIO_LEG_20, FIM_LEG_20,
                    "em exercício", "Efetivo", LEGISLATURA_20, 2022, null, provPerfil));
            ac.fontes.computeIfAbsent("mandatos", k -> new ArrayList<>())
                    .add(FonteUsada.de(FONTE_DEPUTADOS, arqConheca));

            // Comissões
            List<OrgaoOuFrente> comissoes = comissoesPorSlug.getOrDefault(slug, List.of());
            if (!comissoes.isEmpty()) {
                ac.orgaos.addAll(comissoes);
                if (arqComissoes != null) {
                    ac.fontes.computeIfAbsent("orgaos_e_frentes", k -> new ArrayList<>())
                            .add(FonteUsada.de(FONTE_COMISSOES, arqComissoes));
                }
            }
        }

        // Ordenar pessoas por nome
        pessoas.sort(Comparator.comparing(Pessoa::nome));

        // Gravação
        Path saida = ctx.normalizado().resolve("alep");
        Path pastaBlocos = saida.resolve("blocos");
        Files.createDirectories(pastaBlocos);
        Json.gravar(saida.resolve("pessoas.json"), Json.SAIDA.valueToTree(pessoas));

        for (var e : acumulados.entrySet()) {
            String slug = e.getKey();
            Acumulado ac = e.getValue();
            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(ac.mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(ac.orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            List<Proposicao> proposicoes = proposicoesPorSlug.getOrDefault(slug, List.of());
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            // As fontes das proposições são as listas anuais que trouxeram os registros.
            Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>(ac.fontes);
            var deProposicoes = Registros.fontesDosBlocos(Json.SAIDA.createObjectNode().set("proposicoes",
                    Json.SAIDA.valueToTree(proposicoes))).get("proposicoes");
            if (deProposicoes != null) {
                fontes.put("proposicoes", deProposicoes);
            }
            blocosNode.set("fontes", Json.SAIDA.valueToTree(fontes));
            Json.gravar(pastaBlocos.resolve(slug + ".json"), blocosNode);
        }

        System.out.println("alep: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alep", "deputados/conheca.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alep: erro ao ler deputados/conheca.html: " + e.getMessage());
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
            String slug = m.group(1).trim();
            if (vistos.add(slug)) {
                String fotoUrl = m.group(2).trim();
                String nome = Texto.semEntidades(m.group(3).trim());
                String partido = m.group(4).trim();
                lista.add(new DeputadoBruto(slug, nome, partido, fotoUrl));
            }
        }
        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx, Coleta.Arquivo arqComissoes) {
        if (arqComissoes == null || !Files.exists(Coleta.caminho(ctx, arqComissoes))) {
            return Map.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arqComissoes));
            return extrairComissoesDoHtml(html, arqComissoes);
        } catch (Exception e) {
            System.err.println("alep: erro ao ler comissoes.html: " + e.getMessage());
            return Map.of();
        }
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(String html, Coleta.Arquivo arq) {
        if (html == null || html.isBlank()) {
            return Map.of();
        }
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        Prov prov = Prov.de(FONTE_COMISSOES, SITE + "/atividade-parlamentar/comissoes-permanentes-e-temporarias", arq);

        String[] blocos = html.split("(?=<div class=[\"'][^\"']*border-gray-200|<button)");
        for (String bloco : blocos) {
            Matcher mTitulo = BOTAO_TITULO_PATTERN.matcher(bloco);
            if (!mTitulo.find()) {
                continue;
            }
            String nomeComissao = Texto.semEntidades(mTitulo.group(1).trim());
            Matcher mMembro = LINK_MEMBRO_PATTERN.matcher(bloco);
            while (mMembro.find()) {
                String slug = mMembro.group(1).trim();
                String corpo = mMembro.group(2);
                String papel = "Membro";
                Matcher mDd14 = PAPEL_DD14_PATTERN.matcher(corpo);
                if (mDd14.find()) {
                    papel = Texto.semEntidades(mDd14.group(1).trim());
                } else {
                    Matcher mBadge = PAPEL_BADGE_PATTERN.matcher(corpo);
                    if (mBadge.find()) {
                        papel = Texto.semEntidades(mBadge.group(1).trim());
                    }
                }
                var orgao = new OrgaoOuFrente("comissao", nomeComissao, null, papel, INICIO_LEG_20, null, prov);
                mapa.computeIfAbsent(slug, k -> new ArrayList<>()).add(orgao);
            }
        }
        return mapa;
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
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            if (i < partes.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static final class Acumulado {
        final List<Mandato> mandatos = new ArrayList<>();
        final List<OrgaoOuFrente> orgaos = new ArrayList<>();
        final Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
    }
}
