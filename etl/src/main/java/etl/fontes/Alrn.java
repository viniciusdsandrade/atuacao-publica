package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
import etl.Guarda;
import etl.Json;
import etl.Registros;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Rio Grande do Norte (ALRN, ADR-0044).
 * Coleta os 24 deputados estaduais em exercício na 63ª Legislatura (2023–2027)
 * a partir do portal oficial, dados de mandato, composição da Mesa Diretora,
 * filiação partidária, biografias e fotografias oficiais.
 */
public final class Alrn implements Fonte {

    public static final String FONTE_DEPUTADOS = "alrn-deputados";
    public static final String FONTE_PARLAMENTARES = "alrn-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alrn-mesa-diretora";
    public static final String FONTE_PORTAL = "alrn-portal";

    public static final String SITE = "https://www.al.rn.leg.br";
    public static final String URL_DEPUTADOS = "https://www.al.rn.leg.br/deputados";
    public static final String URL_MESA = "https://www.al.rn.leg.br/deputados/mesadiretora";
    public static final String UF = "RN";

    public static final LocalDate INICIO_LEG_63 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_63 = LocalDate.of(2027, 1, 31);
    public static final int NUMERO_LEGISLATURA = 63;

    private static final String HTML = "text/html";

    public record DeputadoCard(int id, String slug, String nome, String partido, String fotoUrl) {
    }

    public record MesaMembro(int deputadoId, String nome, String papel) {
    }

    private static final Pattern PATTERN_CARD = Pattern.compile(
            "<a[^>]+href=[\"']https?://www\\.al\\.rn\\.leg\\.br/deputado/([0-9]+)/([^\"']+)[\"'][^>]*class=[\"'][^\"']*card-deputies[^\"']*[\"'][^>]*>.*?" +
                    "<div[^>]+class=[\"']image-deputies[\"'][^>]*style=[\"'][^\"']*url\\(['\"]?([^'\")]+)['\"]?\\)[^\"']*[\"'].*?" +
                    "<strong[^>]+class=[\"']name-deputies[\"'][^>]*>([^<]+)</strong>.*?" +
                    "<p[^>]+class=[\"']party-deputies[\"'][^>]*>([^<]+)</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MESA = Pattern.compile(
            "<a[^>]+href=[\"']https?://www\\.al\\.rn\\.leg\\.br/deputado/([0-9]+)/[^\"']*[\"'][^>]*class=[\"'][^\"']*card-deputies[^\"']*[\"'][^>]*>.*?" +
                    "<strong[^>]+class=[\"']name-deputies[\"'][^>]*>([^<]+)</strong>.*?" +
                    "<p[^>]+class=[\"']party-deputies[\"'][^>]*>([^<]+)</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_BIO = Pattern.compile(
            "<div[^>]+class=[\"'][^\"']*p-3[^\"']*[\"'][^>]*>(.*?)</div>\\s*<div",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "alrn";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alrn: coleta iniciada");

        // 1. Lista de deputados e Mesa Diretora
        Coleta.Arquivo arqDeputados = coleta.obter(URL_DEPUTADOS, "deputados.html", HTML, Politica.SEMANAL);
        coleta.obterOuVazio(URL_MESA, "mesa_diretora.html", HTML, Politica.SEMANAL);

        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            return;
        }

        String htmlDeputados = Texto.lerArquivo(Coleta.caminho(ctx, arqDeputados));
        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);

        for (DeputadoCard d : deputados) {
            String urlDetalhe = SITE + "/deputado/" + d.id() + "/" + d.slug();
            coleta.obterOuVazio(urlDetalhe, "deputados/" + d.id() + ".html", HTML, Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            System.out.println("alrn: deputados.html não encontrado");
            return;
        }

        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alrn/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alrn: erro ao criar pasta de blocos", e);
        }

        String htmlDeputados;
        try {
            htmlDeputados = Texto.lerArquivo(Coleta.caminho(ctx, arqDeputados));
        } catch (Exception e) {
            throw new RuntimeException("alrn: erro ao ler deputados.html", e);
        }

        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);
        Map<Integer, MesaMembro> mesaPorDeputado = carregarMesa(ctx);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();

        for (DeputadoCard d : deputados) {
            int id = d.id();
            String idStr = String.valueOf(id);

            Coleta.Arquivo arqDetalhe = Coleta.metadados(ctx, nome(), "deputados/" + id + ".html");
            String urlDetalhe = SITE + "/deputado/" + id + "/" + d.slug();
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlDetalhe, arqDetalhe);

            String biografia = null;
            String nomeCivil = null;

            if (arqDetalhe != null && Files.exists(Coleta.caminho(ctx, arqDetalhe))) {
                try {
                    String htmlDetalhe = Texto.lerArquivo(Coleta.caminho(ctx, arqDetalhe));
                    biografia = extrairBiografia(htmlDetalhe);
                    nomeCivil = extrairNomeCivilDaBio(biografia);
                } catch (Exception e) {
                    System.err.println("alrn: aviso ao ler detalhe de " + id + ": " + e.getMessage());
                }
            }

            if (nomeCivil != null && !nomeCivil.isBlank()) {
                nomesCivisPorId.put(idStr, nomeCivil);
            }

            String partidoSigla = extrairSiglaPartido(d.partido());

            List<LinkOficial> links = List.of(new LinkOficial("Ficha na ALRN", urlDetalhe, provParl));
            String situacao = "Em exercício";

            Pessoa pessoa = new Pessoa("alrn", idStr, d.nome(), "deputado_estadual", "estadual", UF, UF,
                    partidoSigla, d.fotoUrl(), situacao, true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_63, FIM_LEG_63, situacao, "Titular", NUMERO_LEGISLATURA, 2022,
                    "Deputado Estadual na 63ª Legislatura (2023–2027)", provDeputados));

            List<OrgaoOuFrente> orgaos = new ArrayList<>();
            MesaMembro mm = mesaPorDeputado.get(id);
            if (mm != null) {
                Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
                Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, URL_MESA, arqMesa);
                orgaos.add(new OrgaoOuFrente("orgao", "Mesa Diretora", null, formatarPapelMesa(mm.papel()),
                        INICIO_LEG_63, FIM_LEG_63, provMesa));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            if (biografia != null && !biografia.isBlank()) {
                blocosNode.put("biografia", biografia);
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(idStr + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alrn: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alrn/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alrn/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
            System.out.printf("alrn: %d deputados estaduais normalizados%n", pessoas.size());
        } catch (Exception e) {
            throw new RuntimeException("alrn: erro ao gravar pessoas.json", e);
        }
    }

    public static List<DeputadoCard> parseDeputados(String html) {
        List<DeputadoCard> lista = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return lista;
        }

        Matcher m = PATTERN_CARD.matcher(html);
        Set<Integer> vistos = new HashSet<>();
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            String slug = m.group(2).trim();
            String fotoUrl = m.group(3).trim();
            String nome = m.group(4).trim();
            String partido = m.group(5).trim();

            if (vistos.add(id)) {
                lista.add(new DeputadoCard(id, slug, nome, partido, fotoUrl));
            }
        }
        return lista;
    }

    public static Map<Integer, MesaMembro> parseMesa(String html) {
        Map<Integer, MesaMembro> mapa = new LinkedHashMap<>();
        if (html == null || html.isBlank()) {
            return mapa;
        }

        Matcher m = PATTERN_MESA.matcher(html);
        while (m.find()) {
            int id = Integer.parseInt(m.group(1));
            String nome = m.group(2).trim();
            String papel = m.group(3).trim();
            mapa.put(id, new MesaMembro(id, nome, papel));
        }
        return mapa;
    }

    public static String extrairBiografia(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = PATTERN_BIO.matcher(html);
        if (m.find()) {
            String bruto = m.group(1);
            String limpo = bruto.replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&amp;", "&")
                    .replaceAll("\\s+", " ").trim();
            limpo = Texto.semEntidades(limpo);
            if (!limpo.isBlank()) {
                return Guarda.semCpf(limpo);
            }
        }
        return null;
    }

    public static String extrairNomeCivilDaBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return null;
        }
        // Procura padrões comuns como "Natural de ..., [Nome Civil] é..." ou "[Nome Civil], nascido em..."
        Matcher m = Pattern.compile("Natural de [^,]+,\\s*([A-ZÁÉÍÓÚÂÊÎÔÛÃÕÇ][a-záéíóúâêîôûãõç]+(?:\\s+(?:[A-ZÁÉÍÓÚÂÊÎÔÛÃÕÇ][a-záéíóúâêîôûãõç]+|d[aeo]s?|e))+)\\s+é").matcher(bio);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    public static String extrairSiglaPartido(String partidoRaw) {
        if (partidoRaw == null || partidoRaw.isBlank()) {
            return "S/P";
        }
        Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(partidoRaw);
        if (m.find()) {
            return m.group(1).trim();
        }
        return partidoRaw.trim();
    }

    public static String formatarPapelMesa(String papelRaw) {
        if (papelRaw == null || papelRaw.isBlank()) {
            return "Membro da Mesa";
        }
        String p = papelRaw.trim();
        if (p.equalsIgnoreCase("PRESIDENTE")) {
            return "Presidente";
        } else if (p.equalsIgnoreCase("1º VICE-PRESIDENTE")) {
            return "1º Vice-Presidente";
        } else if (p.equalsIgnoreCase("2º VICE-PRESIDENTE")) {
            return "2º Vice-Presidente";
        } else if (p.toUpperCase(Locale.ROOT).startsWith("1º SECRET")) {
            return "1º Secretário";
        } else if (p.toUpperCase(Locale.ROOT).startsWith("2º SECRET")) {
            return "2º Secretário";
        } else if (p.toUpperCase(Locale.ROOT).startsWith("3º SECRET")) {
            return "3º Secretário";
        } else if (p.toUpperCase(Locale.ROOT).startsWith("4º SECRET")) {
            return "4º Secretário";
        }
        return p;
    }

    private Map<Integer, MesaMembro> carregarMesa(Contexto ctx) {
        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        if (arqMesa == null || !Files.exists(Coleta.caminho(ctx, arqMesa))) {
            return Map.of();
        }
        try {
            String htmlMesa = Texto.lerArquivo(Coleta.caminho(ctx, arqMesa));
            return parseMesa(htmlMesa);
        } catch (Exception e) {
            System.err.println("alrn: erro ao ler mesa_diretora.html: " + e.getMessage());
            return Map.of();
        }
    }
}
