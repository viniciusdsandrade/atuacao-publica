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
import etl.Guarda;
import etl.Json;
import etl.Registros;
import etl.Registros.Despesa;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado de Santa Catarina (ALESC, ADR-0021).
 * Coleta os 40 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados de mandato, comissões permanentes, fotografias oficiais em alta resolução e links institucionais.
 */
public final class Alesc implements Fonte {

    public static final String FONTE_DEPUTADOS = "alesc-deputados";
    public static final String FONTE_DETALHES = "alesc-deputados-detalhes";
    public static final String FONTE_PORTAL = "alesc-portal";
    public static final String FONTE_DESPESAS = "alesc-despesas";
    public static final String FONTE_PROPOSICOES = "alesc-proposicoes";

    public static final String SITE = "https://www.alesc.sc.gov.br";
    public static final String URL_DEPUTADOS = "https://www.alesc.sc.gov.br/deputados/";
    public static final String URL_COMISSOES = "https://portalelegis.alesc.sc.gov.br/comissoes-permanentes";
    public static final String URL_DESPESAS = "https://transparencia.alesc.sc.gov.br/gabinetes-parlamentares/csv/";
    public static final String URL_ELEGIS = "https://portalelegis.alesc.sc.gov.br";
    public static final String URL_PROPOSICOES = "https://portalelegis.alesc.sc.gov.br/proposicoes/processo-legislativo";
    public static final String UF = "SC";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    private static final java.time.format.DateTimeFormatter DATA_BR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final Pattern CARD_PATTERN = Pattern.compile(
            "<article class=\"lab-card-team[^\"]*\".*?" +
                    "href=\"https://www\\.alesc\\.sc\\.gov\\.br/deputado/([a-z0-9-]+)/\".*?" +
                    "<img[^>]+src=\"([^\"]+)\".*?" +
                    "<h3 class=\"lab-title-news\">([^<]+)</h3>.*?" +
                    "<span class=\"lab-button[^\"]*\"[^>]*>([^<]+)</span>",
            Pattern.DOTALL);

    public static final Pattern COMISSAO_LINK_PATTERN = Pattern.compile(
            "href=\"/comissoes-permanentes/([a-zA-Z0-9]+)\"\\s+data-title=\"([^\"]+)\"");

    public static final Pattern PRESIDENTE_PATTERN = Pattern.compile(
            "<dt>Presidente</dt>\\s*<dd>([^<]+)</dd>", Pattern.DOTALL);

    public static final Pattern VICE_PATTERN = Pattern.compile(
            "<dt>Vice-Presidente</dt>\\s*<dd>([^<]+)</dd>", Pattern.DOTALL);

    public static final Pattern MEMBROS_BLOCO_PATTERN = Pattern.compile(
            "<dt>Membro</dt>(.*?)</dl>", Pattern.DOTALL);

    public static final Pattern DD_PATTERN = Pattern.compile(
            "<dd>([^<]+)</dd>");

    public static final Pattern TOTAL_ITEMS_PATTERN = Pattern.compile(
            "Exibindo\\s+\\d+\\s*-\\s*\\d+\\s+de\\s+([0-9.]+)", Pattern.CASE_INSENSITIVE);

    public static final Pattern TITULO_PROPOSICAO_PATTERN = Pattern.compile(
            "<h4 class=\"card-title\">\\s*<a href=\"/proposicoes/([^\"]+)\">([^<]+)</a>", Pattern.DOTALL);

    public static final Pattern EMENTA_PATTERN = Pattern.compile(
            "<p class=\"mb-1 fst-italic\"[^>]*>(.*?)</p>", Pattern.DOTALL);

    public static final Pattern ENTRADA_PATTERN = Pattern.compile(
            ">Entrada</div>\\s*<div[^>]*>([0-9/]+)</div>", Pattern.DOTALL);

    public static final Pattern SITUACAO_PATTERN = Pattern.compile(
            ">Situação atual</div>\\s*<div[^>]*>([^<]+)</div>", Pattern.DOTALL);

    public static final Pattern AUTORIA_PATTERN = Pattern.compile(
            ">Autoria</div>\\s*<div[^>]*>(.*?)</div>\\s*</div>", Pattern.DOTALL);

    public static final Pattern LI_PATTERN = Pattern.compile("<li>([^<]+)</li>");

    public static final Pattern IDENTIFICACAO_PATTERN = Pattern.compile(
            "([A-Za-z]+)\\.?/0*(\\d+)/(\\d{4})");

    private static final Map<String, String> SLUG_PARA_INICIATIVA = Map.of(
            "antidio-lunelli", "lunelli",
            "padre-pedro-baldissera", "pedro-baldissera",
            "dr-vicente-caropreso", "vicente-caropreso"
    );

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    private static final Map<String, String> NOMES_ALTERNATIVOS = Map.ofEntries(
            Map.entry("ANA PAULA DA SILVA", "paulinha"),
            Map.entry("PAULINHA", "paulinha"),
            Map.entry("VICENTE CAROPRESO", "dr-vicente-caropreso"),
            Map.entry("ANA CAROLINE CAMPAGNOLO", "ana-campagnolo"),
            Map.entry("MARCIUS MACHADO", "marcius-machado"),
            Map.entry("MARCIUS DA SILVA MACHADO", "marcius-machado"),
            Map.entry("MARCOS VIEIRA", "marcos-vieira"),
            Map.entry("MARCOS LUIZ VIEIRA", "marcos-vieira"),
            Map.entry("MAURICIO ESKUDLARK", "mauricio-eskudlark"),
            Map.entry("MAURICIO JOSE ESKUDLARK", "mauricio-eskudlark"),
            Map.entry("SERGIO MOTTA", "sergio-motta"),
            Map.entry("SERGIO MOTTA RIBEIRO", "sergio-motta"),
            Map.entry("CARLOS HENRIQUE LIMA", "sargento-lima"),
            Map.entry("CARLOS HENRIQUE DE LIMA", "sargento-lima"),
            Map.entry("SARGENTO LIMA", "sargento-lima"),
            Map.entry("JAIR ANTONIO MIOTTO", "jair-miotto"),
            Map.entry("JESSE LOPES", "jesse-lopes"),
            Map.entry("JESSE DE FARIA LOPES", "jesse-lopes"),
            Map.entry("PEDRO BALDISSERA", "padre-pedro-baldissera"),
            Map.entry("CARLOS HUMBERTO METZNER SILVA", "carlos-humberto"),
            Map.entry("CARLOS HUMBERTO", "carlos-humberto"),
            Map.entry("JERRY EDSON COMPER", "jerry-comper")
    );

    @Override
    public String nome() {
        return "alesc";
    }

    public record DeputadoBruto(String slug, String nome, String partido, String fotoUrl) {
    }

    public record ComissaoMeta(String id, String titulo) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alesc: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/deputado/" + d.slug() + "/";
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.slug() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_COMISSOES, "comissoes.html", "text/html", Politica.SEMANAL);
        List<ComissaoMeta> comissoes = extrairMetadadosComissoes(ctx);
        for (ComissaoMeta com : comissoes) {
            String urlModal = URL_COMISSOES + "/" + com.id();
            coleta.obterOuVazio(urlModal, "comissoes/" + com.id() + ".html", "text/html", Politica.SEMANAL,
                    Map.of("X-Requested-With", "XMLHttpRequest"));
        }

        for (int ano = 2023; ano <= 2026; ano++) {
            coleta.obterOuVazio(URL_DESPESAS + ano, "despesas/" + ano + ".csv", "text/csv", Politica.porAno(ano, ctx.hoje()));
        }

        // Proposições da 20ª legislatura por iniciativa (e-Legis)
        for (DeputadoBruto d : deputados) {
            String inic = iniciativaParaSlug(d.slug());
            int p = 1;
            while (true) {
                String url = URL_PROPOSICOES + "?iniciativa=" + inic + "&page=" + p;
                String nomeArquivo = arquivoDeProposicoes(d.slug(), p);
                coleta.obterOuVazio(url, nomeArquivo, "text/html", Politica.SEMANAL);
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), nomeArquivo);
                if (arq == null || arq.bytes() == 0) {
                    break;
                }
                Path caminho = Coleta.caminho(ctx, arq);
                if (!Files.exists(caminho) || devePararPaginacao(caminho)) {
                    break;
                }
                p++;
            }
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alesc: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alesc/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alesc: erro ao criar pasta de blocos", e);
        }

        // Mapear deputados por nome normalizado para associação de comissões
        Map<String, String> slugPorNomeNorm = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            slugPorNomeNorm.put(Texto.normalizarNome(d.nome()), d.slug());
            slugPorNomeNorm.put(Texto.normalizarNome(formatarNome(d.nome())), d.slug());
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorSlug = extrairComissoes(ctx, slugPorNomeNorm);
        Map<String, List<Despesa>> despesasPorSlug = extrairDespesas(ctx, slugPorNomeNorm);
        Map<String, List<Proposicao>> proposicoesPorSlug = extrairProposicoes(ctx, deputados);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String nomeFormatado = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = normalizarFotoUrl(d.fotoUrl());

            String urlPerfil = SITE + "/deputado/" + slug + "/";
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + slug + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, "Em exercício", "Titular", LEGISLATURA_20, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALESC", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alesc", slug, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<OrgaoOuFrente> orgaos = comissoesPorSlug.getOrDefault(slug, List.of());
            List<Despesa> despesas = despesasPorSlug.getOrDefault(slug, List.of());
            List<Proposicao> proposicoes = proposicoesPorSlug.getOrDefault(slug, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesas));
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(slug + ".json"), blocosNode);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("alesc/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alesc: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alesc: erro ao ler deputados.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoBruto> deputados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = CARD_PATTERN.matcher(html);
        while (m.find()) {
            String slug = m.group(1).trim();
            if (vistos.add(slug)) {
                String fotoUrl = m.group(2).trim();
                String nome = Texto.semEntidades(m.group(3).trim());
                String partido = Texto.semEntidades(m.group(4).trim());
                deputados.add(new DeputadoBruto(slug, nome, partido, fotoUrl));
            }
        }
        return deputados;
    }

    public static List<ComissaoMeta> extrairMetadadosComissoes(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", "comissoes.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairMetadadosComissoesDoHtml(html);
        } catch (Exception e) {
            System.err.println("alesc: erro ao ler comissoes.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<ComissaoMeta> extrairMetadadosComissoesDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<ComissaoMeta> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = COMISSAO_LINK_PATTERN.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            String titulo = Texto.semEntidades(m.group(2).trim());
            if (vistos.add(id)) {
                lista.add(new ComissaoMeta(id, titulo));
            }
        }
        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx, Map<String, String> slugPorNomeNorm) {
        List<ComissaoMeta> metadados = extrairMetadadosComissoes(ctx);
        if (metadados.isEmpty()) {
            return Map.of();
        }
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();
        for (ComissaoMeta com : metadados) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", "comissoes/" + com.id() + ".html");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                String urlModal = URL_COMISSOES + "/" + com.id();
                Prov prov = Prov.de(FONTE_DETALHES, urlModal, arq);
                Map<String, List<OrgaoOuFrente>> doHtml = extrairComissoesDoHtml(html, com.titulo(), slugPorNomeNorm, prov);
                for (var entry : doHtml.entrySet()) {
                    resultado.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("alesc: erro ao ler comissao " + com.id() + ": " + e.getMessage());
            }
        }
        return resultado;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(
            String html, String nomeComissao, Map<String, String> slugPorNomeNorm, Prov prov) {
        if (html == null || html.isBlank()) {
            return Map.of();
        }
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        Matcher mPres = PRESIDENTE_PATTERN.matcher(html);
        if (mPres.find()) {
            String nome = Texto.semEntidades(mPres.group(1).trim());
            String slug = slugPorNomeNorm.get(Texto.normalizarNome(nome));
            if (slug != null) {
                resultado.computeIfAbsent(slug, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", nomeComissao, null, "Presidente", INICIO_LEG_20, null, prov));
            }
        }

        Matcher mVice = VICE_PATTERN.matcher(html);
        if (mVice.find()) {
            String nome = Texto.semEntidades(mVice.group(1).trim());
            String slug = slugPorNomeNorm.get(Texto.normalizarNome(nome));
            if (slug != null) {
                resultado.computeIfAbsent(slug, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", nomeComissao, null, "Vice-Presidente", INICIO_LEG_20, null, prov));
            }
        }

        Matcher mMembrosBloco = MEMBROS_BLOCO_PATTERN.matcher(html);
        if (mMembrosBloco.find()) {
            String bloco = mMembrosBloco.group(1);
            Matcher mDd = DD_PATTERN.matcher(bloco);
            while (mDd.find()) {
                String nome = Texto.semEntidades(mDd.group(1).trim());
                String slug = slugPorNomeNorm.get(Texto.normalizarNome(nome));
                if (slug != null) {
                    resultado.computeIfAbsent(slug, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", nomeComissao, null, "Membro", INICIO_LEG_20, null, prov));
                }
            }
        }

        return resultado;
    }

    public static String normalizarFotoUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.replaceAll("-150x150\\.(jpg|jpeg|png)", ".$1").trim();
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

    public static Map<String, List<Despesa>> extrairDespesas(Contexto ctx, Map<String, String> slugPorNomeNorm) {
        Map<String, List<Despesa>> despesasPorSlug = new HashMap<>();
        int vinculadas = 0;
        int semPerfil = 0;
        for (int ano = 2023; ano <= 2026; ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", "despesas/" + ano + ".csv");
            if (arq == null) {
                continue;
            }
            Path caminho = Coleta.caminho(ctx, arq);
            if (!Files.exists(caminho)) {
                continue;
            }
            Prov prov = Prov.de(FONTE_DESPESAS, URL_DESPESAS + ano, arq);
            try (java.io.BufferedReader leitor = Files.newBufferedReader(caminho, java.nio.charset.StandardCharsets.UTF_8)) {
                String linha = leitor.readLine(); // cabeçalho
                if (linha == null) {
                    continue;
                }
                while ((linha = leitor.readLine()) != null) {
                    if (linha.isBlank()) {
                        continue;
                    }
                    String[] partes = splitCsvPontoEVirgula(linha);
                    if (partes.length < 7) {
                        continue;
                    }
                    String verba = partes[0].trim();
                    String descricao = partes[1].trim();
                    String conta = partes[2].trim();
                    String favorecido = partes[3].trim();
                    String dataRef = partes[5].trim();
                    String valorStr = partes[6].trim();

                    if (conta.isEmpty() || valorStr.isEmpty()) {
                        continue;
                    }

                    String slug = resolverSlugPorNome(conta, slugPorNomeNorm);
                    if (slug == null) {
                        semPerfil++;
                        continue;
                    }

                    LocalDate data = null;
                    int mes = 1;
                    int anoLinha = ano;
                    try {
                        if (!dataRef.isEmpty()) {
                            data = LocalDate.parse(dataRef, DATA_BR);
                            mes = data.getMonthValue();
                            anoLinha = data.getYear();
                        }
                    } catch (Exception ignored) {
                    }

                    java.math.BigDecimal valor;
                    try {
                        valor = Texto.valor(valorStr.replace(".", "").replace(",", "."));
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (valor == null) {
                        continue;
                    }

                    String categoria = !verba.isEmpty() ? formatarNome(verba) : "Outras despesas";
                    if (!descricao.isEmpty() && !descricao.equalsIgnoreCase(verba)) {
                        categoria += " - " + descricao;
                    }

                    // Favorecido MEI vem como "NOME + CPF": a sequência de 11 dígitos é removida (METODOLOGIA).
                    Despesa desp = new Despesa(data, anoLinha, mes, categoria,
                            Guarda.semCpf(favorecido),
                            null, valor, null, null, prov);

                    despesasPorSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(desp);
                    vinculadas++;
                }
            } catch (Exception e) {
                System.err.println("alesc: erro ao ler despesas de " + ano + ": " + e.getMessage());
            }
        }
        System.out.println("alesc: " + vinculadas + " lançamentos de despesas vinculados; " + semPerfil
                + " descartados por conta sem perfil de deputado em exercício");
        for (List<Despesa> lista : despesasPorSlug.values()) {
            lista.sort(Comparator.comparing(Despesa::ano).thenComparing(Despesa::mes).reversed());
        }
        return despesasPorSlug;
    }

    public static String resolverSlugPorNome(String conta, Map<String, String> slugPorNomeNorm) {
        String limpo = conta.replaceAll("\\s*\\([^)]*\\)", "").trim();
        String norm = Texto.normalizarNome(limpo);
        String alternativo = NOMES_ALTERNATIVOS.get(norm);
        if (alternativo != null) {
            return alternativo;
        }
        String slug = slugPorNomeNorm.get(norm);
        if (slug != null) {
            return slug;
        }
        String normOriginal = Texto.normalizarNome(conta);
        alternativo = NOMES_ALTERNATIVOS.get(normOriginal);
        if (alternativo != null) {
            return alternativo;
        }
        return slugPorNomeNorm.get(normOriginal);
    }

    public static String[] splitCsvPontoEVirgula(String linha) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean emAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                emAspas = !emAspas;
            } else if (c == ';' && !emAspas) {
                tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
        return tokens.toArray(new String[0]);
    }

    public static String arquivoDeProposicoes(String slug, int pagina) {
        return String.format(Locale.ROOT, "proposicoes/%s-%d.html", slug, pagina);
    }

    public static String iniciativaParaSlug(String slug) {
        if (slug == null) {
            return null;
        }
        return SLUG_PARA_INICIATIVA.getOrDefault(slug, slug);
    }

    public static String nomeTipo(String sigla) {
        if (sigla == null || sigla.isBlank()) {
            return null;
        }
        return switch (sigla.toUpperCase(Locale.ROOT)) {
            case "PL" -> "Projeto de Lei";
            case "PLC" -> "Projeto de Lei Complementar";
            case "PDL" -> "Projeto de Decreto Legislativo";
            case "PEC" -> "Proposta de Emenda Constitucional";
            case "PR" -> "Projeto de Resolução";
            case "MP" -> "Medida Provisória";
            case "PSA" -> "Proposta de Sustação de Ato";
            case "REP" -> "Representação";
            case "SL" -> "Sugestão Legislativa";
            default -> sigla;
        };
    }

    public static boolean devePararPaginacao(Path caminho) {
        try {
            String html = Texto.lerArquivo(caminho);
            return devePararPaginacaoDoHtml(html);
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean devePararPaginacaoDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        if (!html.contains("<div class=\"card card-alesc mb-3\">")) {
            return true;
        }
        Matcher mData = ENTRADA_PATTERN.matcher(html);
        String ultimaData = null;
        while (mData.find()) {
            ultimaData = mData.group(1);
        }
        if (ultimaData != null) {
            try {
                LocalDate dt = LocalDate.parse(ultimaData, DATA_BR);
                if (dt.isBefore(INICIO_LEG_20)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean slugCasaComAutor(String slug, String normAutor) {
        if (slug == null || normAutor == null) {
            return false;
        }
        String cleanAutor = normAutor.replaceAll("^(?i)(DEPUTAD[OA]|DR\\.?|DRA\\.?|PROFESSOR[A]?|PADRE|SARGENTO)\\s+", "").trim();
        String slugNorm = Texto.normalizarNome(slug.replace("-", " "));
        String cleanSlug = slugNorm.replaceAll("^(?i)(DR\\.?|DRA\\.?|PROFESSOR[A]?|PADRE|SARGENTO)\\s+", "").trim();
        return cleanAutor.equals(cleanSlug) || cleanAutor.equals(slugNorm)
                || cleanAutor.contains(cleanSlug) || cleanSlug.contains(cleanAutor);
    }

    public static Map<String, List<Proposicao>> extrairProposicoes(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, List<Proposicao>> resultado = new HashMap<>();
        int total = 0;
        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String inic = iniciativaParaSlug(slug);
            int p = 1;
            while (true) {
                String nomeArquivo = arquivoDeProposicoes(slug, p);
                Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", nomeArquivo);
                if (arq == null) {
                    break;
                }
                Path caminho = Coleta.caminho(ctx, arq);
                if (!Files.exists(caminho)) {
                    break;
                }
                try {
                    String html = Texto.lerArquivo(caminho);
                    String urlOrigem = URL_PROPOSICOES + "?iniciativa=" + inic + "&page=" + p;
                    Prov prov = Prov.de(FONTE_PROPOSICOES, urlOrigem, arq);
                    List<Proposicao> props = extrairProposicoesDoHtml(html, slug, prov);
                    resultado.computeIfAbsent(slug, k -> new ArrayList<>()).addAll(props);
                    total += props.size();
                    if (devePararPaginacaoDoHtml(html)) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("alesc: erro ao ler " + nomeArquivo + ": " + e.getMessage());
                    break;
                }
                p++;
            }
        }
        System.out.println("alesc: " + total + " proposições da 20ª legislatura em " + resultado.size() + " deputados");
        return resultado;
    }

    public static List<Proposicao> extrairProposicoesDoHtml(String html, String slug, Prov prov) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<Proposicao> lista = new ArrayList<>();
        String marker = "<div class=\"card card-alesc mb-3\">";
        int pos = 0;
        while (true) {
            int start = html.indexOf(marker, pos);
            if (start == -1) {
                break;
            }
            int nextStart = html.indexOf(marker, start + marker.length());
            String cardHtml;
            if (nextStart == -1) {
                int end = html.indexOf("<ul class=\"pagination\"", start);
                if (end == -1) {
                    end = html.indexOf("</main>", start);
                }
                cardHtml = end != -1 ? html.substring(start, end) : html.substring(start);
                pos = html.length();
            } else {
                cardHtml = html.substring(start, nextStart);
                pos = nextStart;
            }

            Matcher mTitulo = TITULO_PROPOSICAO_PATTERN.matcher(cardHtml);
            if (!mTitulo.find()) {
                continue;
            }
            String idFonte = mTitulo.group(1).trim();
            String identificacao = Texto.semEntidades(mTitulo.group(2).trim());

            String ementa = null;
            Matcher mEmenta = EMENTA_PATTERN.matcher(cardHtml);
            if (mEmenta.find()) {
                ementa = Guarda.semCpf(Texto.semEntidades(mEmenta.group(1).trim()));
            }

            LocalDate data = null;
            Matcher mData = ENTRADA_PATTERN.matcher(cardHtml);
            if (mData.find()) {
                try {
                    data = LocalDate.parse(mData.group(1).trim(), DATA_BR);
                } catch (Exception ignored) {
                }
            }

            // Ignorar proposições anteriores ao início da 20ª legislatura
            if (data != null && data.isBefore(INICIO_LEG_20)) {
                continue;
            }

            String situacao = null;
            Matcher mSit = SITUACAO_PATTERN.matcher(cardHtml);
            if (mSit.find()) {
                situacao = Texto.semEntidades(mSit.group(1).trim());
            }

            String sigla = null;
            Integer numero = null;
            Integer ano = data != null ? data.getYear() : null;
            Matcher mId = IDENTIFICACAO_PATTERN.matcher(identificacao);
            if (mId.find()) {
                sigla = mId.group(1);
                try {
                    numero = Integer.parseInt(mId.group(2));
                    ano = Integer.parseInt(mId.group(3));
                } catch (Exception ignored) {
                }
            }

            String tipo = nomeTipo(sigla);
            String dataApresentacao = data != null ? data.toString() : null;
            String urlOficial = URL_ELEGIS + "/proposicoes/" + idFonte;

            int ordemAssinatura = 1;
            boolean proponente = true;
            Matcher mAutores = AUTORIA_PATTERN.matcher(cardHtml);
            if (mAutores.find()) {
                List<String> nomesAutores = new ArrayList<>();
                Matcher mLi = LI_PATTERN.matcher(mAutores.group(1));
                while (mLi.find()) {
                    nomesAutores.add(Texto.normalizarNome(Texto.semEntidades(mLi.group(1).trim())));
                }
                for (int i = 0; i < nomesAutores.size(); i++) {
                    String normAutor = nomesAutores.get(i);
                    if (slugCasaComAutor(slug, normAutor)) {
                        ordemAssinatura = i + 1;
                        proponente = (i == 0);
                        break;
                    }
                }
            }

            lista.add(new Proposicao(
                    idFonte, tipo, numero, ano, identificacao, ementa, situacao,
                    null, dataApresentacao, ordemAssinatura, proponente, urlOficial, prov));
        }
        return lista;
    }
}
