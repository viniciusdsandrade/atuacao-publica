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
 * Assembleia Legislativa do Estado do Maranhão (ALEMA, ADR-0048).
 * Coleta os 43 deputados estaduais em exercício na 20ª Legislatura (2023 a 2027)
 * a partir do portal institucional oficial, dados de mandato, composição da Mesa Diretora,
 * filiação partidária e fotografias oficiais.
 */
public final class Alema implements Fonte {

    public static final String FONTE_DEPUTADOS = "alema-deputados";
    public static final String FONTE_PARLAMENTARES = "alema-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alema-mesa-diretora";
    public static final String FONTE_PORTAL = "alema-portal";

    public static final String SITE = "https://www.al.ma.leg.br";
    public static final String URL_DEPUTADOS = "https://www.al.ma.leg.br/sitealema/deputados/";
    public static final String URL_PARLAMENTAR = "https://www.al.ma.leg.br/sitealema/deputado/";
    public static final String UF = "MA";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int NUMERO_LEGISLATURA = 20;

    private static final String HTML = "text/html";

    public record DeputadoCard(String slug, String nome, String partido, String fotoUrl, String link) {
    }

    public record MesaMembro(String cargo, String nome) {
    }

    private static final Pattern PATTERN_CARD = Pattern.compile(
            "<div[^>]*class=[\"']news-card-image[\"']>\\s*" +
                    "<img[^>]+src=[\"']([^\"']+)[\"'][^>]*alt=[\"']([^\"']*)[\"'].*?" +
                    "<h3[^>]*class=[\"']news-card-title[\"']>\\s*<a href=[\"']https?://www\\.al\\.ma\\.leg\\.br/sitealema/deputado/([^/\"']+)/?[\"']>([^<]+)</a></h3>.*?" +
                    "<p[^>]*class=[\"']news-card-chapeu[\"']>([^<]+)</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);


    private static final List<MesaMembro> MESA_DIRETORA_PADRAO = List.of(
            new MesaMembro("Presidente", "Iracema Vale"),
            new MesaMembro("1º Vice-Presidente", "Antônio Pereira"),
            new MesaMembro("2ª Vice-Presidente", "Fabiana Vilar"),
            new MesaMembro("3º Vice-Presidente", "Hemetério Weba"),
            new MesaMembro("4ª Vice-Presidente", "Andreia M. Rezende"),
            new MesaMembro("1º Secretário", "Davi Brandão"),
            new MesaMembro("2º Secretário", "Glalbert Cutrim"),
            new MesaMembro("3º Secretário", "Osmar Filho"),
            new MesaMembro("4º Secretário", "Guilherme Paz")
    );

    @Override
    public String nome() {
        return "alema";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alema: coleta iniciada");

        Coleta.Arquivo arqDeputados = coleta.obter(URL_DEPUTADOS, "deputados.html", HTML, Politica.SEMANAL);
        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            return;
        }

        String htmlDeputados = Texto.lerArquivo(Coleta.caminho(ctx, arqDeputados));
        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);

        for (DeputadoCard dep : deputados) {
            String urlParl = URL_PARLAMENTAR + dep.slug() + "/";
            coleta.obterOuVazio(urlParl, "deputados/" + dep.slug() + ".html", HTML, Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            System.out.println("alema: deputados.html não encontrado");
            return;
        }

        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alema/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alema: erro ao criar pasta de blocos", e);
        }

        String htmlDeputados;
        try {
            htmlDeputados = Texto.lerArquivo(Coleta.caminho(ctx, arqDeputados));
        } catch (Exception e) {
            throw new RuntimeException("alema: erro ao ler deputados.html", e);
        }

        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);
        Map<String, String> cargoMesaPorNome = new HashMap<>();
        for (MesaMembro mm : MESA_DIRETORA_PADRAO) {
            cargoMesaPorNome.put(Texto.normalizarNome(mm.nome()), mm.cargo());
        }

        Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, URL_DEPUTADOS, arqDeputados);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorSlug = new HashMap<>();

        for (DeputadoCard d : deputados) {
            String slug = d.slug();

            Coleta.Arquivo arqDetalhe = Coleta.metadados(ctx, nome(), "deputados/" + slug + ".html");
            String urlDetalhe = URL_PARLAMENTAR + slug + "/";
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlDetalhe, arqDetalhe);

            String nomeCivil = d.nome();


            nomesCivisPorSlug.put(slug, nomeCivil);

            List<LinkOficial> links = List.of(new LinkOficial("Ficha na ALEMA", urlDetalhe, provParl));
            String situacao = "Em exercício";

            Pessoa pessoa = new Pessoa("alema", slug, d.nome(), "deputado_estadual", "estadual", UF, UF,
                    d.partido(), d.fotoUrl(), situacao, true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, situacao, "Titular", NUMERO_LEGISLATURA, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<OrgaoOuFrente> orgaos = new ArrayList<>();
            String papelMesa = cargoMesaPorNome.get(Texto.normalizarNome(d.nome()));
            if (papelMesa != null) {
                orgaos.add(new OrgaoOuFrente("orgao", "Mesa Diretora", null, papelMesa,
                        INICIO_LEG_20, FIM_LEG_20, provMesa));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));

            try {
                Path arquivoBloco = pastaBlocos.resolve(slug + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alema: erro ao gravar bloco de " + slug, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alema/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorSlug.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alema/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorSlug));
            }
        } catch (Exception e) {
            throw new RuntimeException("alema: erro ao gravar pessoas.json", e);
        }

        System.out.printf("alema: %d deputados estaduais normalizados%n", pessoas.size());
    }

    public static List<DeputadoCard> parseDeputados(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoCard> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_CARD.matcher(html);
        while (m.find()) {
            String fotoUrl = m.group(1).trim();
            String slug = m.group(3).trim();
            String nome = decodificarHtml(m.group(4).trim());
            String partido = decodificarHtml(m.group(5).trim());
            String link = URL_PARLAMENTAR + slug + "/";


            if (vistos.add(slug)) {
                lista.add(new DeputadoCard(slug, nome, partido, fotoUrl, link));
            }
        }
        lista.sort(Comparator.comparing(DeputadoCard::nome));
        return lista;
    }


    public static String decodificarHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&ordm;", "º")
                .replace("&ordf;", "ª")
                .replace("&aacute;", "á")
                .replace("&Aacute;", "Á")
                .replace("&eacute;", "é")
                .replace("&Eacute;", "É")
                .replace("&iacute;", "í")
                .replace("&Iacute;", "Í")
                .replace("&oacute;", "ó")
                .replace("&Oacute;", "Ó")
                .replace("&uacute;", "ú")
                .replace("&Uacute;", "Ú")
                .replace("&atilde;", "ã")
                .replace("&Atilde;", "Ã")
                .replace("&otilde;", "õ")
                .replace("&Otilde;", "Õ")
                .replace("&acirc;", "â")
                .replace("&ecirc;", "ê")
                .replace("&icirc;", "î")
                .replace("&ocirc;", "ô")
                .replace("&ucirc;", "û")
                .replace("&ccedil;", "ç")
                .replace("&Ccedil;", "Ç")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
