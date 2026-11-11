package etl.fontes;

import java.io.IOException;
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
 * Assembleia Legislativa do Estado do Amapá (ALAP, ADR-0051).
 * Coleta os 24 deputados estaduais em exercício na 9ª Legislatura (2023 a 2027)
 * a partir do portal institucional oficial, dados de mandato, composição da Mesa Diretora,
 * filiação partidária, profissão/biografia e fotografias oficiais.
 */
public final class Alap implements Fonte {

    public static final String FONTE_DEPUTADOS = "alap-deputados";
    public static final String FONTE_PARLAMENTARES = "alap-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alap-mesa-diretora";
    public static final String FONTE_PORTAL = "alap-portal";

    public static final String SITE = "https://www.al.ap.leg.br";
    public static final String URL_LEGISLATURA = "https://www.al.ap.leg.br/pagina.php?pg=exibir_legislatura";
    public static final String URL_MESA_DIRETORA = "https://www.al.ap.leg.br/pagina.php?pg=exibir_mesadiretora";
    public static final String URL_PARLAMENTAR = "https://www.al.ap.leg.br/pagina.php?pg=exibir_parlamentar&iddeputado=";
    public static final String UF = "AP";

    public static final LocalDate INICIO_LEG_9 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_9 = LocalDate.of(2027, 1, 31);
    public static final int NUMERO_LEGISLATURA = 9;

    private static final String HTML = "text/html; charset=iso-8859-1";

    public record DeputadoCard(String id, String nomeParlamentar, String nomeCompleto, String partido,
                              String fotoUrl, String link, String profissao) {
    }

    public record MesaMembro(String id, String cargo, String nome) {
    }

    private static final Pattern PATTERN_BOX = Pattern.compile(
            "<div[^>]*class=[\"']ls-box[^\"']*[\"'][^>]*>\\s*" +
                    "<div[^>]*class=[\"']box-foto-deputados[\"']>\\s*" +
                    "<a[^>]*href=[\"']pagina\\.php\\?pg=exibir_parlamentar&iddeputado=([0-9]+)[\"'][^>]*onmouseover=[\"']Tip\\('(.*?)'\\s*,.*?<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>\\s*</a>\\s*</div>\\s*" +
                    "<p[^>]*class=[\"']ls-title-5[\"']>\\s*(?:Deputad[ao]\\s+)?([^<]+)\\s*</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MESA = Pattern.compile(
            "<div[^>]*class=[\"']box-foto-deputados[\"']>\\s*" +
                    "<a[^>]*href=[\"']pagina\\.php\\?pg=exibir_parlamentar&iddeputado=([0-9]+)[\"'].*?</div>\\s*" +
                    "<p[^>]*class=[\"']ls-title-5[\"']>\\s*([^<]+)<br>\\s*<strong>([^<]+)</strong>\\s*</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "alap";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alap: coleta iniciada");

        Coleta.Arquivo arqLeg = coleta.obter(URL_LEGISLATURA, "legislatura.html", HTML, Politica.SEMANAL);
        coleta.obterOuVazio(URL_MESA_DIRETORA, "mesa_diretora.html", HTML, Politica.SEMANAL);

        if (arqLeg == null || !Files.exists(Coleta.caminho(ctx, arqLeg))) {
            return;
        }

        String htmlLeg = Files.readString(Coleta.caminho(ctx, arqLeg));
        List<DeputadoCard> deputados = parseDeputados(htmlLeg);

        for (DeputadoCard dep : deputados) {
            String urlParl = URL_PARLAMENTAR + dep.id();
            coleta.obterOuVazio(urlParl, "parlamentares/" + dep.id() + ".html", HTML, Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqLeg = Coleta.metadados(ctx, nome(), "legislatura.html");
        if (arqLeg == null || !Files.exists(Coleta.caminho(ctx, arqLeg))) {
            System.out.println("alap: legislatura.html não encontrado");
            return;
        }

        Prov provLeg = Prov.de(FONTE_DEPUTADOS, URL_LEGISLATURA, arqLeg);

        Path pastaBlocos = ctx.normalizado().resolve("alap/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alap: erro ao criar pasta de blocos", e);
        }

        String htmlLeg;
        try {
            htmlLeg = Files.readString(Coleta.caminho(ctx, arqLeg));
        } catch (Exception e) {
            throw new RuntimeException("alap: erro ao ler legislatura.html", e);
        }

        List<DeputadoCard> deputados = parseDeputados(htmlLeg);

        // Mesa Diretora
        Map<String, String> cargoMesaPorId = new HashMap<>();
        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, URL_MESA_DIRETORA, arqMesa != null ? arqMesa : arqLeg);
        if (arqMesa != null && Files.exists(Coleta.caminho(ctx, arqMesa))) {
            try {
                String htmlMesa = Files.readString(Coleta.caminho(ctx, arqMesa));
                for (MesaMembro mm : parseMesa(htmlMesa)) {
                    cargoMesaPorId.put(mm.id(), mm.cargo());
                }
            } catch (Exception e) {
                System.err.println("alap: aviso ao ler mesa_diretora.html: " + e.getMessage());
            }
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();

        for (DeputadoCard d : deputados) {
            String idStr = d.id();
            String urlFicha = URL_PARLAMENTAR + idStr;

            Coleta.Arquivo arqParl = Coleta.metadados(ctx, nome(), "parlamentares/" + idStr + ".html");
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlFicha, arqParl != null ? arqParl : arqLeg);

            String nomeCivil = d.nomeCompleto() != null && !d.nomeCompleto().isBlank() ? d.nomeCompleto() : d.nomeParlamentar();
            nomesCivisPorId.put(idStr, nomeCivil);

            List<LinkOficial> links = List.of(new LinkOficial("Ficha na ALAP", urlFicha, provParl));
            String situacao = "Em exercício";

            Pessoa pessoa = new Pessoa("alap", idStr, d.nomeParlamentar(), "deputado_estadual", "estadual", UF, UF,
                    d.partido(), d.fotoUrl(), situacao, true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_9, FIM_LEG_9, situacao, "Titular", NUMERO_LEGISLATURA, 2022,
                    "Deputado Estadual na 9ª Legislatura (2023–2027)", provLeg));

            List<OrgaoOuFrente> orgaos = new ArrayList<>();
            String papelMesa = cargoMesaPorId.get(idStr);
            if (papelMesa != null) {
                orgaos.add(new OrgaoOuFrente("orgao", "Mesa Diretora", null, papelMesa,
                        INICIO_LEG_9, FIM_LEG_9, provMesa));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            if (d.profissao() != null && !d.profissao().isBlank()) {
                blocosNode.put("biografia", d.profissao());
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(idStr + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alap: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alap/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alap/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("alap: erro ao gravar pessoas.json", e);
        }

        System.out.printf("alap: %d deputados estaduais normalizados%n", pessoas.size());
    }

    public static List<DeputadoCard> parseDeputados(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoCard> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_BOX.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            String tip = m.group(2);
            String fotoSrc = m.group(3).trim();
            String nomeTitulo = decodificarHtml(m.group(4).trim());

            if (!vistos.add(id)) {
                continue;
            }

            String nomeParlamentar = nomeTitulo;
            String nomeCompleto = null;
            String partido = "S/P";
            String profissao = null;

            if (tip != null && !tip.isBlank()) {
                Matcher mNomeComp = Pattern.compile("<b>Nome Completo:</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(tip);
                if (mNomeComp.find()) {
                    nomeCompleto = decodificarHtml(mNomeComp.group(1).trim());
                }
                Matcher mPart = Pattern.compile("<b>Partido:</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(tip);
                if (mPart.find()) {
                    String partRaw = decodificarHtml(mPart.group(1).trim());
                    partido = normalizarPartido(partRaw);
                }
                Matcher mProf = Pattern.compile("<b>Profiss[^<]*:</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(tip);
                if (mProf.find()) {
                    String profRaw = decodificarHtml(mProf.group(1).trim());
                    if (!profRaw.isBlank()) {
                        profissao = Guarda.semCpf(profRaw);
                    }
                }
            }

            String fotoUrl = formatarUrlFoto(fotoSrc);
            String link = URL_PARLAMENTAR + id;
            lista.add(new DeputadoCard(id, nomeParlamentar, nomeCompleto, partido, fotoUrl, link, profissao));
        }
        lista.sort(Comparator.comparing(DeputadoCard::nomeParlamentar));
        return lista;
    }

    public static List<MesaMembro> parseMesa(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<MesaMembro> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_MESA.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            String nome = decodificarHtml(m.group(2).trim());
            String cargo = decodificarHtml(m.group(3).trim());
            if (vistos.add(id)) {
                lista.add(new MesaMembro(id, cargo, nome));
            }
        }
        return lista;
    }

    public static String normalizarPartido(String raw) {
        if (raw == null || raw.isBlank()) {
            return "S/P";
        }
        String p = raw.trim();
        if (p.contains("UNIÃO BRASIL")) {
            return "UNIÃO";
        }
        if (p.contains("REDE SUSTENTABILIDADE")) {
            return "REDE";
        }
        if (p.contains("PARTIDO VERDE")) {
            return "PV";
        }
        return p;
    }

    public static String formatarUrlFoto(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String limpa = src.trim();
        if (limpa.startsWith("http://www.al.ap.gov.br/")) {
            limpa = "https://www.al.ap.leg.br/" + limpa.substring("http://www.al.ap.gov.br/".length());
        } else if (limpa.startsWith("http://www.al.ap.leg.br/")) {
            limpa = "https://www.al.ap.leg.br/" + limpa.substring("http://www.al.ap.leg.br/".length());
        } else if (limpa.startsWith("/")) {
            limpa = SITE + limpa;
        } else if (!limpa.startsWith("http://") && !limpa.startsWith("https://")) {
            limpa = SITE + "/" + limpa;
        }
        return limpa.replace(" ", "%20");
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
