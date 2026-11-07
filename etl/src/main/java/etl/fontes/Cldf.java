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
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Câmara Legislativa do Distrito Federal (CLDF, ADR-0026).
 * Coleta os 24 deputados distritais em exercício na 9ª legislatura (2023–2027),
 * fotografias oficiais, biografias e comissões permanentes.
 */
public final class Cldf implements Fonte {

    public static final String FONTE_DEPUTADOS = "cldf-deputados";
    public static final String FONTE_DETALHES = "cldf-deputados-detalhes";
    public static final String FONTE_PORTAL = "cldf-portal";
    public static final String FONTE_PROPOSICOES = "cldf-proposicoes";

    public static final String API_PROPOSICOES = "https://ple.cl.df.gov.br/pleservico/api/public/proposicao/filter";
    /** Página da proposição no PLe, que carrega o item por /pleservico/api/public/proposicao/{id}/detalhe. */
    public static final String PAGINA_PROPOSICAO = "https://ple.cl.df.gov.br/#/proposicao/";
    static final int PRIMEIRO_ANO_PROPOSICOES = 2022;
    /** Maior página que a API entrega: pedido maior volta com 2.000 itens. */
    static final int TAMANHO_DA_PAGINA = 2000;
    private static final Pattern NUMERO_E_ANO = Pattern.compile("(\\d+)/(\\d{4})$");

    public static final String SITE = "https://www.cl.df.gov.br";
    public static final String URL_DEPUTADOS = "https://www.cl.df.gov.br/deputados-2023-2026";
    public static final String URL_COMISSOES = "https://www.cl.df.gov.br/comissoes-permanentes";
    public static final String UF = "DF";

    public static final LocalDate INICIO_LEG_9 = LocalDate.of(2023, 1, 1);
    public static final LocalDate FIM_LEG_9 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_9 = 9;

    public static final Pattern CARD_PATTERN = Pattern.compile(
            "<a[^>]+class=[\"'][^\"']*template-card[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>.*?<img[^>]+src=[\"']([^\"']+)[\"'].*?<span class=[\"']card-title[\"']>([^<]+)</span>\\s*<span class=[\"']card-text[\"']>([^<]+)</span>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern PARAGRAFO_PATTERN = Pattern.compile(
            "<p[^>]*>(.*?)</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern REDES_PATTERN = Pattern.compile(
            "href=[\"'](https?://(?:www\\.)?(?:facebook|instagram|twitter|x|youtube)\\.com/[^\"'#\\s]+)[\"']",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "cldf";
    }

    public record DeputadoBruto(String slug, String nome, String partido, String fotoUrl, String href) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("cldf: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = d.href().startsWith("http") ? d.href() : SITE + d.href();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.slug() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_COMISSOES, "comissoes.html", "text/html", Politica.SEMANAL);

        // Proposições: o PLe só filtra por POST. O campo "last" vem sempre falso, e a página além do total responde 500:
        // o número de páginas sai do total da primeira. A ordem por id mantém as páginas estáveis entre coletas.
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            Politica politica = Politica.porAnoSemanal(ano, ctx.hoje());
            Coleta.Arquivo primeira = coleta.obterPostOpcional(urlDaPagina(0), corpoDoAno(ano), caminhoDaPagina(ano, 0),
                    "application/json", politica);
            if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
                continue;
            }
            int paginas = paginas(Json.ler(Coleta.caminho(ctx, primeira)).path("totalElements").asInt(0));
            for (int pagina = 1; pagina < paginas; pagina++) {
                coleta.obterPostOpcional(urlDaPagina(pagina), corpoDoAno(ano), caminhoDaPagina(ano, pagina),
                        "application/json", politica);
            }
        }
    }

    static String urlDaPagina(int pagina) {
        return API_PROPOSICOES + "?page=" + pagina + "&size=" + TAMANHO_DA_PAGINA + "&sort=id,asc";
    }

    private static String corpoDoAno(int ano) {
        return "{\"ano\":" + ano + "}";
    }

    private static String caminhoDaPagina(int ano, int pagina) {
        return "proposicoes/" + ano + "-" + pagina + ".json";
    }

    static int paginas(int total) {
        return (total + TAMANHO_DA_PAGINA - 1) / TAMANHO_DA_PAGINA;
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("cldf: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("cldf/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("cldf: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorNome = extrairComissoes(ctx);
        Set<String> chavesDosDeputados = new HashSet<>();
        for (DeputadoBruto d : deputados) {
            chavesDosDeputados.add(normalizarChaveNome(d.nome()));
        }
        Map<String, List<Proposicao>> proposicoesPorNome = proposicoesPorChave(ctx, chavesDosDeputados);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String id = slug;
            String nome = d.nome();
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();
            String biografia = null;

            String urlPerfil = d.href().startsWith("http") ? d.href() : SITE + d.href();
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + slug + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil);

            List<LinkOficial> links = new ArrayList<>();
            links.add(new LinkOficial("Página na CLDF", urlPerfil, provPerfil));

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));

                    List<String> paragrafosBio = new ArrayList<>();
                    Matcher mP = PARAGRAFO_PATTERN.matcher(htmlPerfil);
                    while (mP.find()) {
                        String textoP = mP.group(1).replaceAll("<[^>]+>", " ")
                                .replaceAll("&nbsp;", " ")
                                .replaceAll("\\s+", " ").trim();
                        if (textoP.length() > 60 && !textoP.contains("ouvidoria@cl.df.gov.br")
                                && !textoP.contains("Barra de busca")
                                && !textoP.contains("Redes Sociais")
                                && !textoP.contains("Horário de atendimento")) {
                            paragrafosBio.add(textoP);
                        }
                    }
                    if (!paragrafosBio.isEmpty()) {
                        biografia = String.join("\n\n", paragrafosBio);
                    }

                    Matcher mRedes = REDES_PATTERN.matcher(htmlPerfil);
                    Set<String> redesVistas = new HashSet<>();
                    while (mRedes.find()) {
                        String urlRede = mRedes.group(1).trim();
                        if (redeValida(urlRede) && redesVistas.add(urlRede)) {
                            links.add(new LinkOficial("Rede social", urlRede, provPerfil));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("cldf: aviso ao processar perfil de " + slug + ": " + e.getMessage());
                }
            }

            Pessoa pessoa = new Pessoa("cldf", id, nome, "deputado_distrital", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_distrital", "estadual", UF, UF,
                    INICIO_LEG_9, FIM_LEG_9, "Em exercício", "Titular", LEGISLATURA_9, 2022,
                    "Deputado Distrital na 9ª Legislatura (2023–2027)", provDeputados));

            String chaveNome = normalizarChaveNome(nome);
            List<OrgaoOuFrente> orgaos = comissoesPorNome.getOrDefault(chaveNome, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoesPorNome.getOrDefault(chaveNome, List.of())));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            if (biografia != null) {
                blocosNode.put("biografia", biografia);
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(id + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("cldf: erro ao gravar bloco de " + id, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("cldf/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        } catch (Exception e) {
            throw new RuntimeException("cldf: erro ao gravar pessoas.json", e);
        }

        System.out.println("cldf: " + pessoas.size() + " deputados distritais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "cldf", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDeHtml(html);
        } catch (Exception e) {
            System.err.println("cldf: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDeHtml(String html) {
        List<DeputadoBruto> lista = new ArrayList<>();
        Matcher m = CARD_PATTERN.matcher(html);
        Set<String> slugsVistos = new HashSet<>();

        while (m.find()) {
            String href = m.group(1).trim();
            String fotoRaw = m.group(2).trim();
            String nomeRaw = m.group(3).trim();
            String partidoRaw = m.group(4).trim();

            String slug = href.replaceAll("^/web/guest/", "")
                    .replaceAll("^/+", "")
                    .replaceAll("/+$", "");
            if (slug.isBlank() || !slugsVistos.add(slug)) {
                continue;
            }

            String nome = nomeRaw.replaceAll("\\s+", " ").trim();
            String partido = normalizarPartido(partidoRaw);
            String fotoUrl = normalizarUrlFoto(fotoRaw);

            lista.add(new DeputadoBruto(slug, nome, partido, fotoUrl, href));
        }

        lista.sort(Comparator.comparing(DeputadoBruto::slug));
        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "cldf", "comissoes.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return Map.of();
        }
        try {
            Prov prov = Prov.de(FONTE_DETALHES, URL_COMISSOES, arq);
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairComissoesDeHtml(html, prov);
        } catch (Exception e) {
            System.err.println("cldf: erro ao extrair comissoes: " + e.getMessage());
            return Map.of();
        }
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDeHtml(String html, Prov prov) {
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();

        Matcher mComissao = Pattern.compile("comissaoNome:\"([^\"]+)\",comissaoSigla:\"([^\"]+)\"").matcher(html);
        List<Integer> posicoes = new ArrayList<>();
        while (mComissao.find()) {
            posicoes.add(mComissao.start());
        }
        posicoes.add(html.length());

        Pattern pTitular = Pattern.compile("tipoMembro:\\{nome:\"([^\"]+)\"\\},autor:\\{[^}]*nome:\"([^\"]+)\"");
        Pattern pSuplente = Pattern.compile("autorSuplente:\\{[^}]*nome:\"([^\"]+)\"");

        for (int i = 0; i < posicoes.size() - 1; i++) {
            String trecho = html.substring(posicoes.get(i), posicoes.get(i + 1));
            Matcher mInfo = Pattern.compile("comissaoNome:\"([^\"]+)\",comissaoSigla:\"([^\"]+)\"").matcher(trecho);
            if (!mInfo.find()) {
                continue;
            }

            String nomeComissao = decodificarUnicode(mInfo.group(1));
            String siglaComissao = mInfo.group(2).trim();

            Matcher mTit = pTitular.matcher(trecho);
            while (mTit.find()) {
                String papel = mTit.group(1).trim();
                String nomeParlamentar = limparNomeDeputado(decodificarUnicode(mTit.group(2)));
                String chave = normalizarChaveNome(nomeParlamentar);

                mapa.computeIfAbsent(chave, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", nomeComissao, siglaComissao, papel, INICIO_LEG_9, null, prov));
            }

            Matcher mSup = pSuplente.matcher(trecho);
            while (mSup.find()) {
                String nomeParlamentar = limparNomeDeputado(decodificarUnicode(mSup.group(1)));
                String chave = normalizarChaveNome(nomeParlamentar);

                mapa.computeIfAbsent(chave, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", nomeComissao, siglaComissao, "Suplente", INICIO_LEG_9, null, prov));
            }
        }

        return mapa;
    }

    public static String normalizarUrlFoto(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String limpa = raw.replace("&amp;", "&").trim();
        if (limpa.startsWith("/")) {
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

        // Extrai sigla entre parênteses: "Partido dos Trabalhadores (PT)" -> "PT"
        Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(p);
        if (m.find()) {
            String sigla = m.group(1).trim().toUpperCase(Locale.ROOT);
            return switch (sigla) {
                case "PODEMOS", "PODE" -> "PODE";
                default -> sigla;
            };
        }

        String maiusc = p.toUpperCase(Locale.ROOT);
        return switch (maiusc) {
            case "REPUBLICANOS" -> "REPUBLICANOS";
            case "UNIÃO BRASIL", "UNIAO BRASIL", "UNIÃO", "UNIAO", "DEMOCRATA", "DEM" -> "UNIÃO";
            case "PROGRESSISTAS", "PP" -> "PP";
            case "PODEMOS", "PODE" -> "PODE";
            case "NOVO" -> "NOVO";
            default -> p.replaceAll("\\s+", " ").trim();
        };
    }

    public static String limparNomeDeputado(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceAll("^(?:Deputado|Deputada)\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Proposições de cada deputado em exercício, pela chave do nome, a partir das páginas em cache. */
    private static Map<String, List<Proposicao>> proposicoesPorChave(Contexto ctx, Set<String> chavesDosDeputados) {
        Map<String, List<Proposicao>> porChave = new HashMap<>();
        int total = 0;
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            // O total da primeira página vale para o ano todo. Página de uma coleta anterior pode repetir item: o id decide.
            int paginas = 1;
            Set<String> vistos = new HashSet<>();
            for (int pagina = 0; pagina < paginas; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "cldf", caminhoDaPagina(ano, pagina));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                JsonNode resposta = Json.ler(Coleta.caminho(ctx, arq));
                if (pagina == 0) {
                    paginas = paginas(resposta.path("totalElements").asInt(0));
                }
                for (var e : proposicoesDaPagina(resposta, chavesDosDeputados,
                        Prov.de(FONTE_PROPOSICOES, arq.url(), arq)).entrySet()) {
                    for (Proposicao p : e.getValue()) {
                        if (vistos.add(e.getKey() + "|" + p.idFonte())) {
                            porChave.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(p);
                            total++;
                        }
                    }
                }
            }
        }
        System.out.println("cldf: " + total + " autorias de proposições em " + porChave.size() + " deputados");
        return porChave;
    }

    /**
     * Proposições de uma página da API, pela chave do nome de cada autor. A autoria é texto, com os autores separados
     * por vírgula e às vezes repetidos; só entra quem é "Deputado" ou "Deputada" com nome de deputado em exercício.
     * A posição na autoria vira {@code ordem_assinatura}. A etapa da tramitação vira a situação, como a fonte escreve.
     */
    static Map<String, List<Proposicao>> proposicoesDaPagina(JsonNode resposta, Set<String> chavesDosDeputados, Prov prov) {
        Map<String, List<Proposicao>> porChave = new HashMap<>();
        for (JsonNode p : resposta.path("content")) {
            String id = Json.texto(p, "id");
            String identificacao = Texto.vazioParaNulo(Json.texto(p, "siglaNumeroAno"));
            String autoria = Json.texto(p, "autoria");
            Matcher numeroEAno = identificacao == null ? null : NUMERO_E_ANO.matcher(identificacao);
            if (id == null || autoria == null || numeroEAno == null || !numeroEAno.find()) {
                continue;
            }
            String ementa = Texto.vazioParaNulo(Json.texto(p, "ementa"));
            List<String> temas = new ArrayList<>();
            for (String tema : (Json.texto(p, "temaNome") == null ? "" : Json.texto(p, "temaNome")).split("#")) {
                if (!tema.isBlank()) {
                    temas.add(tema.strip());
                }
            }
            String[] autores = autoria.split(",");
            Set<String> jaContados = new HashSet<>();
            for (int i = 0; i < autores.length; i++) {
                String autor = autores[i].strip();
                String chave = normalizarChaveNome(autor);
                if (!autor.matches("^Deputad[oa] .*") || !chavesDosDeputados.contains(chave) || !jaContados.add(chave)) {
                    continue;
                }
                porChave.computeIfAbsent(chave, k -> new ArrayList<>()).add(new Proposicao(id, Json.texto(p, "tipoProposicao"),
                        Integer.valueOf(numeroEAno.group(1)), Integer.valueOf(numeroEAno.group(2)), identificacao,
                        ementa == null ? null : Guarda.semCpf(ementa.replaceAll("\\s+", " ")), Json.texto(p, "etapa"),
                        temas.isEmpty() ? null : temas, Json.texto(p, "dataLeitura"), i + 1, null,
                        PAGINA_PROPOSICAO + id + "/consultar", prov));
            }
        }
        return porChave;
    }

    public static String normalizarChaveNome(String nome) {
        if (nome == null) {
            return "";
        }
        return Texto.normalizarNome(nome.replaceAll("^(?:Deputado|Deputada)\\s+", ""))
                .toLowerCase(Locale.ROOT);
    }

    public static String decodificarUnicode(String input) {
        if (input == null) {
            return "";
        }
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int code = Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(Character.toString((char) code)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static boolean redeValida(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return !u.endsWith("facebook.com")
                && !u.endsWith("instagram.com")
                && !u.endsWith("twitter.com")
                && !u.endsWith("x.com")
                && !u.endsWith("youtube.com")
                && !u.contains("cl.df.gov.br");
    }
}
