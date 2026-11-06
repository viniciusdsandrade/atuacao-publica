package etl.fontes;

import java.nio.file.DirectoryStream;
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
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado de Pernambuco (ALEPE, ADR-0025).
 * Coleta os 49 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados biográficos, fotos oficiais, comissões temáticas e mandatos.
 */
public final class Alepe implements Fonte {

    public static final String FONTE_DEPUTADOS = "alepe-deputados";
    public static final String FONTE_DETALHES = "alepe-deputados-detalhes";
    public static final String FONTE_PORTAL = "alepe-portal";

    public static final String SITE = "https://www.alepe.pe.gov.br";
    public static final String URL_DEPUTADOS = "https://www.alepe.pe.gov.br/parlamentares/";
    public static final String UF = "PE";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    public static final Pattern SELECT_DEPUTADOS_PATTERN = Pattern.compile(
            "<option value=[\"']/parlamentar/([^\"']+)[\"']>([^<]+)</option>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern CARD_PATTERN = Pattern.compile(
            "<li[^>]*>\\s*<a href=[\"']/parlamentar/([^\"']+)[\"'][^>]*>.*?<img[^>]+(?:src|data-alepe-fallback)=[\"']([^\"']+)[\"'].*?<h2[^>]*class=[\"'][^\"']*parlamentares-nome[^\"']*[\"'][^>]*>([^<]+)</h2>\\s*<span[^>]*class=[\"'][^\"']*parlamentares-partido[^\"']*[\"'][^>]*>([^<]*)</span>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern SELECT_COMISSOES_PATTERN = Pattern.compile(
            "<option value=[\"'](/comissao/[^\"']+)[\"']>([^<]+)</option>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern SHORTLINK_PATTERN = Pattern.compile(
            "<link rel=['\"]shortlink['\"] href=['\"]https://www.alepe.pe.gov.br/\\?p=([0-9]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<dt>Nome civil:</dt>\\s*<dd>([^<]+)</dd>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NATURALIDADE_PATTERN = Pattern.compile(
            "<dt>Naturalidade:</dt>\\s*<dd>([^<]+)</dd>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern BIO_PATTERN = Pattern.compile(
            "<div class=[\"']resume[\"']>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PERFIL_PATTERN = Pattern.compile(
            "<figure class=[\"']figure figure-avatar[\"']>\\s*<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern REDES_SOCIAIS_PATTERN = Pattern.compile(
            "href=[\"'](https?://(?:www\\.)?(?:facebook|instagram|twitter|x)\\.com/[^\"'#\\s]+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern COMISSAO_TITULO_PATTERN = Pattern.compile(
            "<h1 class=[\"']title news-title[\"']>\\s*(?:<img[^>]*>)?\\s*([^<]+)</h1>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern MEMBRO_COMISSAO_PATTERN = Pattern.compile(
            "<a href=[\"']/parlamentar/([^\"']+)[\"'][^>]*>.*?<h2[^>]*class=[\"'][^\"']*parlamentares-nome[^\"']*[\"'][^>]*>([^<]+)</h2>\\s*<span[^>]*class=[\"'][^\"']*parlamentares-partido[^\"']*[\"'][^>]*>([^<]*)</span>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alepe";
    }

    public record DeputadoBruto(String slug, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alepe: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/parlamentar/" + d.slug() + "/";
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.slug() + ".html", "text/html", Politica.SEMANAL);
        }

        List<String> caminhosComissoes = extrairCaminhosComissoes(ctx);
        for (String caminho : caminhosComissoes) {
            String slugComissao = caminho.replace("/comissao/", "").replaceAll("/$", "");
            String urlComissao = SITE + caminho + "/";
            coleta.obterOuVazio(urlComissao, "comissoes/" + slugComissao + ".html", "text/html", Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alepe: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alepe/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alepe: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairTodasComissoes(ctx);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String id = slug;
            String nome = formatarNome(d.nome());
            String partido = normalizarPartido(d.partido());
            String fotoUrl = normalizarUrlFoto(d.fotoUrl());
            String nomeCivil = null;
            String biografia = null;

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + slug + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, SITE + "/parlamentar/" + slug + "/", arqPerfil);

            List<LinkOficial> links = new ArrayList<>();
            links.add(new LinkOficial("Página na ALEPE", SITE + "/parlamentar/" + slug + "/", provPerfil));

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));

                    Matcher mNomeCivil = NOME_CIVIL_PATTERN.matcher(htmlPerfil);
                    if (mNomeCivil.find()) {
                        String rawCivil = mNomeCivil.group(1).trim();
                        if (!rawCivil.isBlank()) {
                            nomeCivil = formatarNome(rawCivil);
                        }
                    }

                    Matcher mFoto = FOTO_PERFIL_PATTERN.matcher(htmlPerfil);
                    if (mFoto.find()) {
                        String fotoCandidata = normalizarUrlFoto(mFoto.group(1));
                        if (fotoCandidata != null) {
                            fotoUrl = fotoCandidata;
                        }
                    }

                    Matcher mBio = BIO_PATTERN.matcher(htmlPerfil);
                    if (mBio.find()) {
                        String bioTexto = limparHtml(mBio.group(1));
                        if (bioTexto != null && bioTexto.length() > 30) {
                            biografia = bioTexto;
                        }
                    }

                    Matcher mRedes = REDES_SOCIAIS_PATTERN.matcher(htmlPerfil);
                    Set<String> redesVistas = new HashSet<>();
                    while (mRedes.find()) {
                        String urlRede = mRedes.group(1).trim();
                        if (redeValida(urlRede) && redesVistas.add(urlRede)) {
                            links.add(new LinkOficial("Rede social", urlRede, provPerfil));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("alepe: aviso ao processar perfil de " + slug + ": " + e.getMessage());
                }
            }

            Pessoa pessoa = new Pessoa("alepe", id, nome, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, "Em exercício", "Titular", LEGISLATURA_20, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<FonteUsada> fontes = new ArrayList<>();
            fontes.add(new FonteUsada(FONTE_DEPUTADOS, URL_DEPUTADOS,
                    arqDeputados != null ? arqDeputados.capturadoEm() : null));
            if (arqPerfil != null) {
                fontes.add(new FonteUsada(FONTE_DETALHES, SITE + "/parlamentar/" + slug + "/",
                        arqPerfil.capturadoEm()));
            }

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(slug, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(fontes));
            if (biografia != null) {
                blocosNode.put("biografia", biografia);
            }
            if (nomeCivil != null) {
                blocosNode.put("nome_civil", nomeCivil);
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(id + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alepe: erro ao gravar bloco de " + id, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alepe/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        } catch (Exception e) {
            throw new RuntimeException("alepe: erro ao gravar pessoas.json", e);
        }

        System.out.println("alepe: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alepe", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDeHtml(html);
        } catch (Exception e) {
            System.err.println("alepe: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDeHtml(String html) {
        Map<String, DeputadoBruto> map = new HashMap<>();

        Matcher mCard = CARD_PATTERN.matcher(html);
        while (mCard.find()) {
            String slug = mCard.group(1).trim().replaceAll("^/+|/+$", "");
            String fotoUrl = mCard.group(2).trim();
            String nome = mCard.group(3).trim();
            String partido = mCard.group(4).trim();
            map.put(slug, new DeputadoBruto(slug, nome, partido, fotoUrl));
        }

        // Fallback para quem estiver no select mas porventura não no card
        Matcher mSelect = SELECT_DEPUTADOS_PATTERN.matcher(html);
        while (mSelect.find()) {
            String slug = mSelect.group(1).trim().replaceAll("^/+|/+$", "");
            String nome = mSelect.group(2).trim();
            if (!slug.isBlank() && !map.containsKey(slug)) {
                map.put(slug, new DeputadoBruto(slug, nome, null, null));
            }
        }

        List<DeputadoBruto> lista = new ArrayList<>(map.values());
        lista.sort(Comparator.comparing(DeputadoBruto::slug));
        return lista;
    }

    public static List<String> extrairCaminhosComissoes(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alepe", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairCaminhosComissoesDeHtml(html);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<String> extrairCaminhosComissoesDeHtml(String html) {
        Set<String> caminhos = new HashSet<>();
        Matcher m = SELECT_COMISSOES_PATTERN.matcher(html);
        while (m.find()) {
            String c = m.group(1).trim();
            if (!c.isBlank()) {
                caminhos.add(c);
            }
        }
        List<String> ordenados = new ArrayList<>(caminhos);
        ordenados.sort(String::compareTo);
        return ordenados;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairTodasComissoes(Contexto ctx) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();
        List<String> caminhos = extrairCaminhosComissoes(ctx);
        for (String caminho : caminhos) {
            String slugComissao = caminho.replace("/comissao/", "").replaceAll("/$", "");
            String urlComissao = SITE + caminho + "/";
            Coleta.Arquivo meta = Coleta.metadados(ctx, "alepe", "comissoes/" + slugComissao + ".html");
            if (meta != null && Files.exists(Coleta.caminho(ctx, meta))) {
                try {
                    Prov prov = Prov.de(FONTE_DETALHES, urlComissao, meta);
                    String html = Texto.lerArquivo(Coleta.caminho(ctx, meta));
                    extrairMembrosComissao(html, urlComissao, prov, resultado);
                } catch (Exception e) {
                    System.err.println("alepe: aviso ao carregar comissao " + slugComissao + ": " + e.getMessage());
                }
            }
        }
        return resultado;
    }

    public static void extrairMembrosComissao(String html, String urlComissao, Prov prov,
                                              Map<String, List<OrgaoOuFrente>> resultado) {
        Matcher mTitulo = COMISSAO_TITULO_PATTERN.matcher(html);
        String titulo = "Comissão Parlamentar";
        if (mTitulo.find()) {
            titulo = mTitulo.group(1).replaceAll("\\s+", " ").trim();
        }

        Matcher mMembro = MEMBRO_COMISSAO_PATTERN.matcher(html);
        while (mMembro.find()) {
            String slugDeputado = mMembro.group(1).trim().replaceAll("^/+|/+$", "");
            String cargoRaw = mMembro.group(3).trim();

            String papel = "Membro";
            if (cargoRaw.toUpperCase(Locale.ROOT).contains("VICE-PRESIDENTE")) {
                papel = "Vice-Presidente";
            } else if (cargoRaw.toUpperCase(Locale.ROOT).contains("PRESIDENTE")) {
                papel = "Presidente";
            }

            resultado.computeIfAbsent(slugDeputado, k -> new ArrayList<>())
                    .add(new OrgaoOuFrente("comissao", titulo, null, papel, INICIO_LEG_20, null, prov));
        }
    }

    public static String normalizarUrlFoto(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String limpa = raw.replace("&amp;", "&").trim();
        if (limpa.startsWith("//")) {
            limpa = "https:" + limpa;
        } else if (limpa.startsWith("/")) {
            limpa = SITE + limpa;
        }
        limpa = limpa.replace(" ", "%20");
        if (limpa.matches("^https?://[^\\s]+$")) {
            return limpa;
        }
        return null;
    }

    public static String normalizarPartido(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String p = raw.trim();
        return switch (p.toUpperCase(Locale.ROOT)) {
            case "NOVO" -> "NOVO";
            case "PODEMOS", "PODE" -> "PODE";
            case "REPUBLICANOS" -> "REPUBLICANOS";
            case "UNIÃO", "UNIAO" -> "UNIÃO";
            default -> p.replaceAll("\\s+", " ").trim();
        };
    }

    public static String[] extrairMunicipioUf(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{null, null};
        }
        String limpo = raw.replaceAll("\\s+", " ").trim();

        // Formato: Recife - PE
        Pattern p1 = Pattern.compile("^([^-]+)\\s*-\\s*([A-Za-z]{2})$");
        Matcher m1 = p1.matcher(limpo);
        if (m1.find()) {
            return new String[]{m1.group(1).trim(), m1.group(2).toUpperCase(Locale.ROOT)};
        }

        // Formato: Canhotinho (PE)
        Pattern p2 = Pattern.compile("^([^(]+)\\s*\\(([A-Za-z]{2})\\)$");
        Matcher m2 = p2.matcher(limpo);
        if (m2.find()) {
            return new String[]{m2.group(1).trim(), m2.group(2).toUpperCase(Locale.ROOT)};
        }

        return new String[]{limpo, null};
    }

    public static String limparHtml(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String semTags = raw.replaceAll("(?s)<[^>]+>", " ");
        semTags = semTags.replace("&nbsp;", " ");
        semTags = semTags.replaceAll("\\*Com informações da assessoria.*", "");
        semTags = semTags.replaceAll("\\s+", " ").trim();
        return semTags.isBlank() ? null : semTags;
    }

    public static boolean redeValida(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return !u.endsWith("facebook.com")
                && !u.endsWith("instagram.com")
                && !u.endsWith("twitter.com")
                && !u.endsWith("x.com");
    }

    public static String formatarNome(String nomeRaw) {
        if (nomeRaw == null || nomeRaw.isBlank()) {
            return "";
        }
        String limpo = nomeRaw.replaceAll("\\s+", " ").trim();
        String[] partes = limpo.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            if (p.isBlank()) {
                continue;
            }
            if (i > 0) {
                sb.append(" ");
            }
            String pLower = p.toLowerCase(Locale.ROOT);
            if (i > 0 && PREPOSICOES.contains(pLower)) {
                sb.append(pLower);
            } else if (p.length() <= 3 && p.equals(p.toUpperCase(Locale.ROOT))) {
                // Siglas como 'DR', 'SD'
                sb.append(p);
            } else {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) {
                    sb.append(p.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return sb.toString();
    }
}
