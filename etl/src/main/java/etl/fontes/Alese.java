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
 * Assembleia Legislativa do Estado de Sergipe (ALESE, ADR-0047).
 * Coleta os 24 deputados estaduais em exercício na 20ª Legislatura (2023 a 2027)
 * a partir do portal institucional oficial, dados de mandato, composição da Mesa Diretora,
 * filiação partidária, biografias e fotografias oficiais.
 */
public final class Alese implements Fonte {

    public static final String FONTE_DEPUTADOS = "alese-deputados";
    public static final String FONTE_PARLAMENTARES = "alese-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alese-mesa-diretora";
    public static final String FONTE_PORTAL = "alese-portal";

    public static final String SITE = "https://al.se.leg.br";
    public static final String URL_DEPUTADOS = "https://al.se.leg.br/deputados/";
    public static final String URL_MESA = "https://al.se.leg.br/mesa-diretora/";
    public static final String URL_PARLAMENTAR = "https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id=";
    public static final String UF = "SE";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int NUMERO_LEGISLATURA = 20;

    private static final String HTML = "text/html";

    public record DeputadoCard(int id, String nome, String partido, String fotoUrl, String link) {
    }

    public record MesaMembro(String cargo, String nome) {
    }

    private static final Pattern PATTERN_CARD = Pattern.compile(
            "<article[^>]*class=[\"'][^\"']*qodef-portfolio-item[^\"']*[\"'][^>]*>.*?" +
                    "<img[^>]+src=[\"']([^\"']+)[\"'][^>]*class=[\"'][^\"']*wp-post-image[^\"']*[\"'].*?" +
                    "<h5[^>]*class=[\"']qodef-item-title[\"'][^>]*>\\s*" +
                    "<a[^>]+href=[\"']https?://aleselegis\\.al\\.se\\.leg\\.br/spl/parlamentar\\.aspx\\?id=([0-9]+)[\"'][^>]*>\\s*([^<]+?)\\s*</a>.*?" +
                    "<div[^>]*class=[\"']qodef-ptf-category-holder[\"'][^>]*>\\s*<span>([^<]+)</span>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_NOME_CIVIL = Pattern.compile(
            "Nome civil:</span>\\s*<span[^>]*>([^<]+)</span>",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_BIO = Pattern.compile(
            "id=[\"']texto_biografia[\"'][^>]*>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MESA = Pattern.compile(
            "<div[^>]*align=[\"']left[\"']>\\s*([^<:]+):\\s*</div>.*?</td>\\s*<td><b>([^<]+)</b>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "alese";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alese: coleta iniciada");

        // 1. Lista de deputados e Mesa Diretora
        Coleta.Arquivo arqDeputados = coleta.obter(URL_DEPUTADOS, "deputados.html", HTML, Politica.SEMANAL);
        coleta.obterFormularioOpcional(URL_MESA, Map.of("cmbNumeroLegislatura", "20"), "mesa_diretora.html", HTML, Politica.SEMANAL);

        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            return;
        }

        String htmlDeputados = Files.readString(Coleta.caminho(ctx, arqDeputados));
        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);

        for (DeputadoCard dep : deputados) {
            String urlParl = URL_PARLAMENTAR + dep.id();
            coleta.obterOuVazio(urlParl, "parlamentares/" + dep.id() + ".html", HTML, Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            System.out.println("alese: deputados.html não encontrado");
            return;
        }

        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alese/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alese: erro ao criar pasta de blocos", e);
        }

        String htmlDeputados;
        try {
            htmlDeputados = Files.readString(Coleta.caminho(ctx, arqDeputados));
        } catch (Exception e) {
            throw new RuntimeException("alese: erro ao ler deputados.html", e);
        }

        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);
        List<MesaMembro> mesaMembros = carregarMesa(ctx);
        Map<String, String> cargoMesaPorNome = new HashMap<>();
        for (MesaMembro mm : mesaMembros) {
            cargoMesaPorNome.put(Texto.normalizarNome(mm.nome()), mm.cargo());
        }

        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        Prov provMesa = arqMesa != null ? Prov.de(FONTE_MESA_DIRETORA, URL_MESA, arqMesa) : null;

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();

        for (DeputadoCard d : deputados) {
            int id = d.id();
            String idStr = String.valueOf(id);

            Coleta.Arquivo arqDetalhe = Coleta.metadados(ctx, nome(), "parlamentares/" + id + ".html");
            String urlDetalhe = URL_PARLAMENTAR + id;
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlDetalhe, arqDetalhe);

            String biografia = null;
            String nomeCivil = d.nome();

            if (arqDetalhe != null && Files.exists(Coleta.caminho(ctx, arqDetalhe))) {
                try {
                    String htmlDetalhe = Files.readString(Coleta.caminho(ctx, arqDetalhe));
                    String nc = parseNomeCivil(htmlDetalhe);
                    if (nc != null && !nc.isBlank()) {
                        nomeCivil = nc;
                    }
                    biografia = parseBiografia(htmlDetalhe);
                } catch (Exception e) {
                    System.err.println("alese: aviso ao ler detalhe de " + id + ": " + e.getMessage());
                }
            }

            if (nomeCivil != null && !nomeCivil.isBlank()) {
                nomesCivisPorId.put(idStr, nomeCivil);
            }

            List<LinkOficial> links = List.of(new LinkOficial("Ficha na ALESE", urlDetalhe, provParl));
            String situacao = "Em exercício";

            Pessoa pessoa = new Pessoa("alese", idStr, d.nome(), "deputado_estadual", "estadual", UF, UF,
                    d.partido(), d.fotoUrl(), situacao, true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, situacao, "Titular", NUMERO_LEGISLATURA, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<OrgaoOuFrente> orgaos = new ArrayList<>();
            String papelMesa = cargoMesaPorNome.get(Texto.normalizarNome(d.nome()));
            if (papelMesa != null && provMesa != null) {
                orgaos.add(new OrgaoOuFrente("orgao", "Mesa Diretora", null, papelMesa,
                        INICIO_LEG_20, FIM_LEG_20, provMesa));
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
                throw new RuntimeException("alese: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alese/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alese/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("alese: erro ao gravar pessoas.json", e);
        }

        System.out.printf("alese: %d deputados estaduais normalizados%n", pessoas.size());
    }

    private List<MesaMembro> carregarMesa(Contexto ctx) {
        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        if (arqMesa == null || !Files.exists(Coleta.caminho(ctx, arqMesa))) {
            return List.of();
        }
        try {
            String htmlMesa = Files.readString(Coleta.caminho(ctx, arqMesa));
            return parseMesa(htmlMesa);
        } catch (Exception e) {
            System.err.println("alese: aviso ao carregar mesa: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoCard> parseDeputados(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoCard> lista = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        Matcher m = PATTERN_CARD.matcher(html);
        while (m.find()) {
            String fotoUrl = m.group(1).trim();
            int id = Integer.parseInt(m.group(2).trim());
            String nome = decodificarHtml(m.group(3).trim());
            String partido = decodificarHtml(m.group(4).trim());
            String link = URL_PARLAMENTAR + id;
            if (vistos.add(id)) {
                lista.add(new DeputadoCard(id, nome, partido, fotoUrl, link));
            }
        }
        lista.sort(Comparator.comparing(DeputadoCard::nome));
        return lista;
    }

    public static String parseNomeCivil(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = PATTERN_NOME_CIVIL.matcher(html);
        if (m.find()) {
            String nc = decodificarHtml(m.group(1).trim());
            return nc.isBlank() ? null : nc;
        }
        return null;
    }

    public static String parseBiografia(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = PATTERN_BIO.matcher(html);
        if (m.find()) {
            String bruta = m.group(1);
            String limpa = bruta.replaceAll("<[^>]+>", " ");
            limpa = decodificarHtml(limpa);
            if (limpa.isBlank()) {
                return null;
            }
            limpa = limpa.replaceAll("\\(?\\d{2}\\)?\\s*\\d{4,5}-?\\d{4}", " ");
            limpa = limpa.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", " ");
            limpa = Guarda.semCpf(limpa);
            limpa = limpa.replaceAll("\\s+", " ").trim();
            return limpa.isBlank() ? null : limpa;
        }
        return null;
    }

    public static List<MesaMembro> parseMesa(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<MesaMembro> membros = new ArrayList<>();
        Matcher m = PATTERN_MESA.matcher(html);
        while (m.find()) {
            String cargoBruto = decodificarHtml(m.group(1).trim());
            String nomeBruto = decodificarHtml(m.group(2).trim());
            if (nomeBruto.isBlank() || nomeBruto.startsWith("---") ||
                    cargoBruto.toLowerCase(Locale.ROOT).contains("biênio") ||
                    cargoBruto.toLowerCase(Locale.ROOT).contains("legislatura")) {
                continue;
            }
            membros.add(new MesaMembro(cargoBruto, nomeBruto));
        }
        return membros;
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
