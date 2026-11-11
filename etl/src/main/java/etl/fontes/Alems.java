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
 * Assembleia Legislativa do Estado de Mato Grosso do Sul (ALEMS, ADR-0038).
 * Coleta os 24 deputados estaduais em exercício na 12ª legislatura (2023–2027),
 * dados de mandato, composição da Mesa Diretora, biografias, fotografias e links institucionais.
 */
public final class Alems implements Fonte {

    public static final String FONTE_DEPUTADOS = "alems-deputados";
    public static final String FONTE_DETALHES = "alems-deputados-detalhes";
    public static final String FONTE_MESA_DIRETORA = "alems-mesa-diretora";
    public static final String FONTE_PORTAL = "alems-portal";

    public static final String SITE = "https://al.ms.gov.br";
    public static final String URL_DEPUTADOS = "https://al.ms.gov.br/Partidos/Lista";
    public static final String URL_MESA_DIRETORA = "https://al.ms.gov.br/Paginas/783/12-legislatura";
    public static final String UF = "MS";

    public static final LocalDate INICIO_LEG_12 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_12 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_12 = 12;

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl, String href) {
    }

    public record PerfilDeputado(String nomeParlamentar, String partido, String fotoUrl,
                                 String biografia, List<String> redes) {
    }

    public static final Pattern PARTY_BLOCK_PATTERN = Pattern.compile(
            "<div class=[\"']party[\"']\\s*>(.*?)(?=<div class=[\"']party[\"']\\s*>|</div>\\s*</div>\\s*</div>|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTY_NAME_PATTERN = Pattern.compile(
            "<div class=[\"']party-name[\"'][^>]*>([^<]+)</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern DEPUTY_LINK_PATTERN = Pattern.compile(
            "<a class=[\"']party-deputy[\"']\\s+href=[\"']/Deputados/Visualizar/([0-9]+)[\"']>\\s*<img\\s+src=[\"']([^\"']+)[\"'][^>]*>\\s*<div class=[\"'][^\"']*[\"']>([^<]+)</div>\\s*</a>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern WIDGET_HEADING_PATTERN = Pattern.compile(
            "<h5 class=[\"']widgetheading[\"']>\\s*([^<]+?)\\s*-\\s*([A-Za-z0-9]+)\\s*</h5>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PERFIL_PATTERN = Pattern.compile(
            "<img[^>]+src=[\"'](/upload/Deputy/[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern BIO_PATTERN = Pattern.compile(
            "<div class=[\"']col-lg-7 text-justify[\"']>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern MESA_ROW_PATTERN = Pattern.compile(
            "<tr>\\s*<td>\\s*([^<]+?)\\s*</td>\\s*<td>\\s*([^<]+?)\\s*</td>\\s*</tr>",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "alems";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alems: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Deputados/Visualizar/" + d.id();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_MESA_DIRETORA, "mesa-diretora.html", "text/html", Politica.SEMANAL);
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alems: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa-diretora.html");
        Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, URL_MESA_DIRETORA, arqMesa);

        Path pastaBlocos = ctx.normalizado().resolve("alems/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alems: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> mesaPorNome = extrairMesaDiretora(ctx, provMesa);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeParlamentar = d.nome();
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();
            String biografia = null;

            String urlPerfil = d.href().startsWith("http") ? d.href() : SITE + d.href();
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil);

            List<LinkOficial> links = new ArrayList<>();
            links.add(new LinkOficial("Página na ALEMS", urlPerfil, provPerfil));

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                    PerfilDeputado perfil = parsePerfil(htmlPerfil);

                    if (perfil.nomeParlamentar() != null && !perfil.nomeParlamentar().isBlank()) {
                        nomeParlamentar = perfil.nomeParlamentar();
                    }
                    if (perfil.partido() != null && !perfil.partido().isBlank()) {
                        partido = perfil.partido();
                    }
                    if (perfil.fotoUrl() != null && !perfil.fotoUrl().isBlank()) {
                        fotoUrl = perfil.fotoUrl();
                    }
                    if (perfil.biografia() != null && !perfil.biografia().isBlank()) {
                        biografia = Guarda.semCpf(perfil.biografia());
                    }
                    for (String rede : perfil.redes()) {
                        links.add(new LinkOficial("Rede social", rede, provPerfil));
                    }
                } catch (Exception e) {
                    System.err.println("alems: aviso ao processar perfil de " + id + ": " + e.getMessage());
                }
            }

            Pessoa pessoa = new Pessoa("alems", id, nomeParlamentar, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_12, FIM_LEG_12, "Em exercício", "Titular", LEGISLATURA_12, 2022,
                    "Deputado Estadual na 12ª Legislatura (2023–2027)", provDeputados));

            String chaveNome = normalizarChaveNome(nomeParlamentar);
            List<OrgaoOuFrente> orgaos = mesaPorNome.getOrDefault(chaveNome, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            if (biografia != null) {
                blocosNode.put("biografia", biografia);
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(id + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alems: erro ao gravar bloco de " + id, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alems/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        } catch (Exception e) {
            throw new RuntimeException("alems: erro ao gravar pessoas.json", e);
        }
    }

    public List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return parseDeputados(html);
        } catch (Exception e) {
            System.err.println("alems: erro ao ler deputados.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> parseDeputados(String html) {
        List<DeputadoBruto> resultado = new ArrayList<>();
        Set<String> idsVistos = new HashSet<>();
        String[] partes = html.split("<div class=[\"']party[\"']\\s*>");
        for (int i = 1; i < partes.length; i++) {
            String bloco = partes[i];
            Matcher mPartido = PARTY_NAME_PATTERN.matcher(bloco);
            String partido = "S/P";
            if (mPartido.find()) {
                partido = limparTexto(Texto.semEntidades(mPartido.group(1)));
            }

            Matcher mDep = DEPUTY_LINK_PATTERN.matcher(bloco);
            while (mDep.find()) {
                String id = mDep.group(1);
                if (!idsVistos.add(id)) {
                    continue;
                }
                String foto = mDep.group(2).trim();
                if (!foto.startsWith("http")) {
                    foto = SITE + (foto.startsWith("/") ? "" : "/") + foto;
                }
                String nome = limparTexto(Texto.semEntidades(mDep.group(3)));
                String href = "/Deputados/Visualizar/" + id;
                resultado.add(new DeputadoBruto(id, nome, partido, foto, href));
            }
        }
        return resultado;
    }

    public static PerfilDeputado parsePerfil(String html) {
        String nomeParlamentar = null;
        String partido = null;
        String fotoUrl = null;
        String biografia = null;
        List<String> redes = new ArrayList<>();

        Matcher mHeading = WIDGET_HEADING_PATTERN.matcher(html);
        if (mHeading.find()) {
            nomeParlamentar = limparTexto(Texto.semEntidades(mHeading.group(1)));
            partido = limparTexto(Texto.semEntidades(mHeading.group(2)));
        }

        Matcher mFoto = FOTO_PERFIL_PATTERN.matcher(html);
        if (mFoto.find()) {
            String caminhoFoto = mFoto.group(1).trim();
            fotoUrl = SITE + (caminhoFoto.startsWith("/") ? "" : "/") + caminhoFoto;
        }

        Matcher mBio = BIO_PATTERN.matcher(html);
        if (mBio.find()) {
            String bioHtml = mBio.group(1);
            String textoBio = bioHtml.replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&amp;", "&")
                    .replaceAll("\\s+", " ").trim();
            textoBio = Texto.semEntidades(textoBio);
            if (!textoBio.isBlank()) {
                biografia = Guarda.semCpf(textoBio);
            }
        }

        // Extrai redes sociais de parlamentares se houver (fora do rodapé)
        int idxContent = html.indexOf("id=\"content\"");
        int idxFooter = html.indexOf("id=\"footer_content\"");
        String areaPerfil = html;
        if (idxContent >= 0 && idxFooter > idxContent) {
            areaPerfil = html.substring(idxContent, idxFooter);
        }
        Pattern redePattern = Pattern.compile(
                "<a\\s+[^>]*href=[\"'](https?://(?:www\\.)?(?:instagram\\.com|facebook\\.com|twitter\\.com|x\\.com|youtube\\.com)/[^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE);
        Matcher mRede = redePattern.matcher(areaPerfil);
        Set<String> redesVistas = new HashSet<>();
        while (mRede.find()) {
            String url = mRede.group(1).trim();
            if (!url.toLowerCase(Locale.ROOT).contains("assembleiams")
                    && !url.toLowerCase(Locale.ROOT).contains("assembleia_ms")
                    && redesVistas.add(url)) {
                redes.add(url);
            }
        }

        return new PerfilDeputado(nomeParlamentar, partido, fotoUrl, biografia, redes);
    }

    public Map<String, List<OrgaoOuFrente>> extrairMesaDiretora(Contexto ctx, Prov provMesa) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "mesa-diretora.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return Map.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return parseMesaDiretora(html, provMesa);
        } catch (Exception e) {
            System.err.println("alems: erro ao ler mesa-diretora.html: " + e.getMessage());
            return Map.of();
        }
    }

    public static Map<String, List<OrgaoOuFrente>> parseMesaDiretora(String html, Prov provMesa) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        // Localiza a seção Mesa Diretora
        int idxMesa = html.indexOf("Mesa Diretora");
        if (idxMesa < 0) {
            return resultado;
        }

        String trechoMesa = html.substring(idxMesa);
        int idxTabelaFim = trechoMesa.indexOf("</table>");
        if (idxTabelaFim > 0) {
            trechoMesa = trechoMesa.substring(0, idxTabelaFim);
        }

        Matcher mRow = MESA_ROW_PATTERN.matcher(trechoMesa);
        while (mRow.find()) {
            String celulaNome = limparTexto(Texto.semEntidades(mRow.group(1)));
            String celulaCargo = limparTexto(Texto.semEntidades(mRow.group(2)));

            if (celulaNome.equalsIgnoreCase("Nome") || celulaCargo.equalsIgnoreCase("Cargo")) {
                continue;
            }

            // Remove partido do nome: "Gerson Claro (PP)" -> "Gerson Claro"
            String nomeParlamentar = celulaNome.replaceAll("\\s*\\([^)]*\\).*", "").trim();
            String cargoFormatado = capitalizarCargo(celulaCargo);

            OrgaoOuFrente orgao = new OrgaoOuFrente(
                    "orgao",
                    "Mesa Diretora",
                    null,
                    cargoFormatado,
                    LocalDate.of(2023, 2, 1),
                    LocalDate.of(2025, 1, 31),
                    provMesa);

            String chave = normalizarChaveNome(nomeParlamentar);
            resultado.computeIfAbsent(chave, k -> new ArrayList<>()).add(orgao);
        }

        return resultado;
    }

    public static String capitalizarCargo(String raw) {
        String limpo = raw.replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        String lower = limpo.toLowerCase(Locale.ROOT);
        if (lower.contains("presidente") && !lower.contains("vice")) {
            return "Presidente";
        }
        if (lower.contains("1") && lower.contains("secret")) {
            return "1º Secretário";
        }
        if (lower.contains("2") && lower.contains("secret")) {
            return "2º Secretário";
        }
        if (lower.contains("3") && lower.contains("secret")) {
            return "3º Secretário";
        }
        if (lower.contains("1") && lower.contains("vice")) {
            return "1º Vice-Presidente";
        }
        if (lower.contains("2") && lower.contains("vice")) {
            return "2º Vice-Presidente";
        }
        if (lower.contains("3") && lower.contains("vice")) {
            return "3º Vice-Presidente";
        }
        // Fallback genérico capitalizado
        return Character.toUpperCase(limpo.charAt(0)) + limpo.substring(1);
    }

    public static String normalizarChaveNome(String s) {
        if (s == null) {
            return "";
        }
        return Texto.normalizarNome(s);
    }

    private static String limparTexto(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }
}
