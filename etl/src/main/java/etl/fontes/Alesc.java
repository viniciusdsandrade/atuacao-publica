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
import etl.Registros.Despesa;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
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

    public static final String SITE = "https://www.alesc.sc.gov.br";
    public static final String URL_DEPUTADOS = "https://www.alesc.sc.gov.br/deputados/";
    public static final String URL_COMISSOES = "https://portalelegis.alesc.sc.gov.br/comissoes-permanentes";
    public static final String URL_DESPESAS = "https://transparencia.alesc.sc.gov.br/gabinetes-parlamentares/csv/";
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
            Map.entry("CARLOS HUMBERTO", "carlos-humberto")
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

            List<FonteUsada> fontes = new ArrayList<>();
            fontes.add(new FonteUsada(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados != null ? arqDeputados.capturadoEm() : null));
            if (arqPerfil != null) {
                fontes.add(new FonteUsada(FONTE_DETALHES, urlPerfil, arqPerfil.capturadoEm()));
            }

            List<OrgaoOuFrente> orgaos = comissoesPorSlug.getOrDefault(slug, List.of());
            if (!orgaos.isEmpty()) {
                Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, nome(), "comissoes.html");
                if (arqComissoes != null) {
                    fontes.add(new FonteUsada(FONTE_DETALHES, URL_COMISSOES, arqComissoes.capturadoEm()));
                }
            }

            List<Despesa> despesas = despesasPorSlug.getOrDefault(slug, List.of());
            if (!despesas.isEmpty()) {
                Coleta.Arquivo arqDesp = Coleta.metadados(ctx, nome(), "despesas/2026.csv");
                if (arqDesp == null) {
                    arqDesp = Coleta.metadados(ctx, nome(), "despesas/2025.csv");
                }
                fontes.add(new FonteUsada(FONTE_DESPESAS, URL_DESPESAS, arqDesp != null ? arqDesp.capturadoEm() : null));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesas));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(fontes));
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

                    String categoria = !verba.isEmpty() ? formatarNome(verba) : "Outras despesas";
                    if (!descricao.isEmpty() && !descricao.equalsIgnoreCase(verba)) {
                        categoria += " - " + descricao;
                    }

                    Despesa desp = new Despesa(data, anoLinha, mes, categoria,
                            favorecido.isEmpty() ? null : favorecido,
                            null, valor, null, null, prov);

                    despesasPorSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(desp);
                }
            } catch (Exception e) {
                System.err.println("alesc: erro ao ler despesas de " + ano + ": " + e.getMessage());
            }
        }
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
}
