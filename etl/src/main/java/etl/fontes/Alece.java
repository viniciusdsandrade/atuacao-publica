package etl.fontes;

import java.math.BigDecimal;
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
import etl.Planilha;
import etl.Registros;
import etl.Registros.Despesa;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado do Ceará (ALECE) (ADR-0020).
 * Coleta os deputados estaduais em exercício na 31ª legislatura (2023–2027),
 * dados de mandato, fotografias oficiais e links institucionais.
 */
public final class Alece implements Fonte {

    public static final String FONTE_DEPUTADOS = "alece-deputados";
    public static final String FONTE_DETALHES = "alece-deputados-detalhes";
    public static final String FONTE_PORTAL = "alece-portal";

    public static final String SITE = "https://www.al.ce.gov.br";
    public static final String UF = "CE";
    public static final String FONTE_DESPESAS = "alece-despesas";
    public static final String URL_DESPESAS = "https://transparencia.al.ce.gov.br/despesas/verba-desempenho-parlamentar/csv";
    /** A fonte não classifica a despesa: a descrição do empenho é texto livre. A categoria é o nome da verba. */
    public static final String CATEGORIA_DESPESAS = "Verba de desempenho parlamentar";
    public static final String FONTE_VOTACOES = "alece-votacoes";
    public static final String URL_VOTACOES = "https://transparencia.al.ce.gov.br/consultas-gerais/votacao-nominal";
    /** Primeiro ano das despesas: a 31ª legislatura começa em 2023. */
    static final int PRIMEIRO_ANO_DESPESAS = 2023;

    public static final LocalDate INICIO_LEG_31 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_31 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_31 = 31;

    private static final Pattern CARD_CONTAINER_PATTERN = Pattern.compile(
            "<div\\s+[^>]*class=\"deputado_card[^\"]*\"[^>]*>");

    private static final Pattern IMG_PATTERN = Pattern.compile("src=\"([^\"]+)\"");
    private static final Pattern SLUG_NOME_PATTERN = Pattern.compile(
            "href=\"https://www\\.al\\.ce\\.gov\\.br/deputados/([^\"]+)\">\\s*([^<]+)</a>");
    private static final Pattern PARTIDO_PATTERN = Pattern.compile(
            "class=\"deputado_card--partido[^\"]*\">\\s*([^<]+)\\s*</p>");

    public static final List<String> COMISSOES_SLUGS = List.of(
            "agropecuaria",
            "ciencia-tecnologia-e-educacao-superior",
            "constituicao-justica-e-redacao",
            "cultura-e-esportes",
            "defesa-do-consumidor",
            "defesa-e-direitos-da-mulher",
            "defesa-social",
            "desenvolvimento-regional-recursos-hidricos-minas-e-pesca",
            "direitos-humanos-e-cidadania",
            "educacao-basica",
            "fiscalizacao-e-controle",
            "industria-desenvolvimento-economico-e-comercio",
            "da-infancia-e-adolescencia",
            "juventude",
            "orcamento-financas-e-tributacao",
            "previdencia-social-e-saude",
            "protecao-social-e-combate-a-fome",
            "trabalho-administracao-e-servico-publico",
            "turismo-e-servicos",
            "viacao-transporte-desenvolvimento-urbano"
    );

    public static final Pattern TITULO_COMISSAO_PATTERN = Pattern.compile(
            "<h1[^>]*>Composição\\s*-\\s*([^<]+)</h1>", Pattern.CASE_INSENSITIVE);

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alece";
    }

    public record DeputadoBruto(String slug, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alece: coleta iniciada");
        coleta.obter(SITE + "/deputados", "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/deputados/" + d.slug();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.slug() + ".html", "text/html", Politica.SEMANAL);
        }

        for (String comSlug : COMISSOES_SLUGS) {
            String url = SITE + "/comissoes-permanentes/" + comSlug + "/composicao";
            coleta.obterOuVazio(url, "comissoes/" + comSlug + ".html", "text/html", Politica.SEMANAL);
        }

        // Verba de desempenho parlamentar: um CSV por mês. O mês precisa de dois dígitos, ou o CSV vem vazio.
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            int ultimoMes = ano == ctx.hoje().getYear() ? ctx.hoje().getMonthValue() : 12;
            for (int mes = 1; mes <= ultimoMes; mes++) {
                coleta.obterOpcional(urlDespesas(ano, mes), arquivoDeDespesas(ano, mes), "text/csv",
                        Politica.porAnoSemanal(ano, ctx.hoje()));
            }
        }

        // Votações nominais: a lista traz a data e a planilha de cada matéria. Planilha publicada não muda.
        Coleta.Arquivo lista = coleta.obterOpcional(URL_VOTACOES, "votacoes/lista.html", "text/html", Politica.SEMANAL);
        if (lista != null && lista.bytes() > 0 && Files.exists(Coleta.caminho(ctx, lista))) {
            for (VotacaoListada v : votacoesDaLista(Texto.lerArquivo(Coleta.caminho(ctx, lista)))) {
                coleta.obterOpcional(v.planilha(), arquivoDaPlanilha(v.planilha()),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Politica.SE_AUSENTE);
            }
        }
    }

    /** Votação nominal da lista pública: sessão, data, matéria e endereço da planilha com os votos. */
    public record VotacaoListada(String sessao, LocalDate data, String materia, String planilha) {
    }

    /** Voto de um deputado listado na planilha; {@code voto} nulo quando nenhuma coluna está marcada. */
    public record VotoNaPlanilha(String deputado, String partido, String voto) {
    }

    private static final Pattern LINHA_DA_LISTA = Pattern.compile(
            "<tr>\\s*<td[^>]*>([^<]*)</td>\\s*<td[^>]*>([^<]*)</td>\\s*<td[^>]*>([^<]*)</td>\\s*<td[^>]*>\\s*(\\d{2}/\\d{2}/\\d{4})\\s*</td>"
                    + "\\s*<td[^>]*>(.*?)</td>\\s*<td[^>]*>(.*?)</td>\\s*</tr>", Pattern.DOTALL);
    private static final Pattern PLANILHA = Pattern.compile("href=\"(https://[^\"]+/votacao_nominal_materia_csv/[^\"]+\\.xlsx)\"");

    /** Linhas da lista de votações nominais que têm planilha. */
    public static List<VotacaoListada> votacoesDaLista(String html) {
        List<VotacaoListada> lista = new ArrayList<>();
        Matcher m = LINHA_DA_LISTA.matcher(html);
        while (m.find()) {
            Matcher planilha = PLANILHA.matcher(m.group(6));
            if (!planilha.find()) {
                continue;
            }
            String materia = Texto.semEntidades(m.group(5).replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").strip();
            lista.add(new VotacaoListada(Texto.semEntidades(m.group(3)).strip(),
                    LocalDate.parse(m.group(4), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), materia, planilha.group(1)));
        }
        return lista;
    }

    /**
     * Votos de uma planilha: um por deputado listado. O cabeçalho "DEPUTADOS" e a linha de baixo dizem a coluna de
     * SIM, NÃO e ABSTENÇÃO; a marca de presença ou voto remoto, "(P)" ou "(V)", não entra.
     */
    public static List<VotoNaPlanilha> votosDaPlanilha(List<Map<String, String>> linhas) {
        List<VotoNaPlanilha> votos = new ArrayList<>();
        Map<String, String> rotuloPorColuna = new HashMap<>();
        boolean cabecalho = false;
        for (Map<String, String> linha : linhas) {
            if (!cabecalho) {
                if ("DEPUTADOS".equalsIgnoreCase(linha.getOrDefault("B", ""))) {
                    cabecalho = true;
                }
                continue;
            }
            if (rotuloPorColuna.isEmpty()) {
                for (var celula : linha.entrySet()) {
                    String rotulo = Texto.normalizarNome(celula.getValue());
                    if (rotulo.equals("SIM") || rotulo.equals("NAO") || rotulo.equals("ABSTENCAO")) {
                        rotuloPorColuna.put(celula.getKey(), rotulo.equals("SIM") ? "Sim" : rotulo.equals("NAO") ? "Não" : "Abstenção");
                    }
                }
                if (!rotuloPorColuna.isEmpty()) {
                    continue;
                }
            }
            String deputado = linha.get("B");
            if (deputado == null || !linha.getOrDefault("A", "").matches("\\d+")) {
                continue;
            }
            String voto = null;
            for (var coluna : rotuloPorColuna.entrySet()) {
                if (linha.containsKey(coluna.getKey())) {
                    voto = coluna.getValue();
                }
            }
            votos.add(new VotoNaPlanilha(deputado, linha.get("C"), voto));
        }
        return votos;
    }

    private static String arquivoDaPlanilha(String url) {
        return "votacoes/" + url.substring(url.lastIndexOf('/') + 1);
    }

    /** Votos e participação por ano de cada deputado em exercício, pelas planilhas em cache. */
    private record Votacoes(Map<String, List<Voto>> votos, Map<String, List<Participacao>> participacao, Prov provLista) {
    }

    private Votacoes votacoesPorSlug(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, String> slugPorNome = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            slugPorNome.put(chaveDoNome(formatarNome(d.nome())), d.slug());
        }
        Map<String, List<Voto>> votos = new HashMap<>();
        Map<String, Map<Integer, int[]>> contagem = new HashMap<>();
        Map<String, Map<Integer, Map<String, Integer>>> porTipo = new HashMap<>();
        Coleta.Arquivo arqLista = Coleta.metadados(ctx, nome(), "votacoes/lista.html");
        if (arqLista == null || arqLista.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqLista))) {
            return new Votacoes(votos, Map.of(), null);
        }
        Prov provLista = Prov.de(FONTE_VOTACOES, URL_VOTACOES, arqLista);
        int semPerfil = 0;
        try {
            for (VotacaoListada v : votacoesDaLista(Texto.lerArquivo(Coleta.caminho(ctx, arqLista)))) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaPlanilha(v.planilha()));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                Prov prov = Prov.de(FONTE_VOTACOES, v.planilha(), arq);
                String numero = v.materia().contains(" - ") ? v.materia().substring(0, v.materia().indexOf(" - ")).strip() : null;
                for (VotoNaPlanilha voto : votosDaPlanilha(Planilha.linhas(Files.readAllBytes(Coleta.caminho(ctx, arq))))) {
                    String slug = slugPorNome.get(chaveDoNome(voto.deputado()));
                    if (slug == null) {
                        semPerfil++;
                        continue;
                    }
                    String texto = voto.voto() == null ? "Sem voto registrado" : voto.voto();
                    votos.computeIfAbsent(slug, k -> new ArrayList<>()).add(new Voto(arquivoDaPlanilha(v.planilha()).replace("votacoes/", "").replace(".xlsx", ""),
                            v.data().toString(), "PLEN", numero, v.materia(), texto, voto.partido(), null, null, null, prov));
                    int[] n = contagem.computeIfAbsent(slug, k -> new HashMap<>()).computeIfAbsent(v.data().getYear(), k -> new int[2]);
                    n[0]++;
                    if (voto.voto() != null) {
                        n[1]++;
                    }
                    porTipo.computeIfAbsent(slug, k -> new HashMap<>()).computeIfAbsent(v.data().getYear(), k -> new java.util.TreeMap<>())
                            .merge(texto, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            System.err.println("alece: erro ao ler votações nominais: " + e.getMessage());
        }
        Map<String, List<Participacao>> participacao = new HashMap<>();
        for (var porDeputado : contagem.entrySet()) {
            for (var ano : new java.util.TreeMap<>(porDeputado.getValue()).entrySet()) {
                participacao.computeIfAbsent(porDeputado.getKey(), k -> new ArrayList<>()).add(new Participacao(
                        String.valueOf(ano.getKey()), ano.getValue()[0], ano.getValue()[1], ano.getValue()[0] - ano.getValue()[1],
                        porTipo.get(porDeputado.getKey()).get(ano.getKey()), provLista));
            }
        }
        int total = votos.values().stream().mapToInt(List::size).sum();
        System.out.println("alece: " + total + " votos nominais em " + votos.size() + " deputados; " + semPerfil
                + " linhas de quem não tem perfil em exercício");
        return new Votacoes(votos, participacao, provLista);
    }

    static String urlDespesas(int ano, int mes) {
        return String.format(Locale.ROOT, "%s?mes=%02d&ano=%d", URL_DESPESAS, mes, ano);
    }

    private static String arquivoDeDespesas(int ano, int mes) {
        return String.format(Locale.ROOT, "despesas/%d-%02d.csv", ano, mes);
    }

    /**
     * Lançamentos de um mês da verba (DEPUTADO;PERIODO;EMPENHO;DESCRICAO;CNPJ;CREDOR;VALOR), por slug do deputado.
     * O deputado vem pelo nome ("DEP FULANO"); nome sem perfil em exercício fica fora e entra na contagem de descartes.
     * Só fica CNPJ de 14 dígitos: o CPF de pessoa física, inclusive o do próprio deputado no reembolso, sai.
     */
    public static Map<String, List<Despesa>> despesasDoMes(String csv, Map<String, String> slugPorNome, Prov prov, int[] descartes) {
        Map<String, List<Despesa>> porSlug = new HashMap<>();
        boolean cabecalho = false;
        for (String linha : csv.split("\\R")) {
            String limpa = linha.replace("﻿", "").strip();
            if (!cabecalho) {
                cabecalho = limpa.startsWith("DEPUTADO;");
                continue;
            }
            String[] partes = limpa.split(";", -1);
            if (partes.length < 7) {
                continue;
            }
            // Três campos pela esquerda e três pela direita: um ";" na descrição não desloca o valor.
            String deputado = partes[0].strip();
            String periodo = partes[1].strip();
            String empenho = partes[2].strip();
            String documento = partes[partes.length - 3].replaceAll("\\D", "");
            String credor = partes[partes.length - 2].strip();
            BigDecimal valor = Texto.valor(partes[partes.length - 1]);
            Matcher mPeriodo = PERIODO_PATTERN.matcher(periodo);
            if (valor == null || !mPeriodo.matches()) {
                continue;
            }
            String slug = slugPorNome.get(chaveDoNome(deputado));
            if (slug == null) {
                descartes[0]++;
                continue;
            }
            porSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(new Despesa(null, Integer.parseInt(mPeriodo.group(2)),
                    Integer.parseInt(mPeriodo.group(1)), CATEGORIA_DESPESAS, Guarda.semCpf(credor),
                    documento.length() == 14 ? documento : null, valor, empenho.isEmpty() ? null : empenho, null, prov));
        }
        return porSlug;
    }

    private static final Pattern PERIODO_PATTERN = Pattern.compile("(\\d{2})/(\\d{4})");

    /**
     * Chave de vínculo pelo nome: sem o "DEP" da verba, sem pontuação ("Dra." e "DRA" casam) e sem o complemento que a
     * fonte às vezes junta ao nome (" - SERVICOS POSTAIS", " POR SOLICITACAO DO DEPUTADO"). Erro de digitação não casa.
     */
    static String chaveDoNome(String nome) {
        String texto = nome.replace("﻿", "").replaceFirst("^(?i)DEP\\.?\\s+", "");
        texto = texto.replaceFirst("\\s+-\\s+.*$", "").replaceFirst("(?i)\\s+POR SOLICITA.*$", "");
        return Texto.normalizarNome(texto.replaceAll("[^\\p{L}\\s]", " "));
    }

    /** Despesas de cada deputado em exercício, pelos CSVs mensais em cache. */
    private Map<String, List<Despesa>> despesasPorSlug(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, String> slugPorNome = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            slugPorNome.put(chaveDoNome(formatarNome(d.nome())), d.slug());
        }
        Map<String, List<Despesa>> porSlug = new HashMap<>();
        int[] descartes = {0};
        int lancamentos = 0;
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            for (int mes = 1; mes <= 12; mes++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDeDespesas(ano, mes));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                try {
                    String csv = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                    for (var item : despesasDoMes(csv, slugPorNome, Prov.de(FONTE_DESPESAS, arq.url(), arq), descartes).entrySet()) {
                        porSlug.computeIfAbsent(item.getKey(), k -> new ArrayList<>()).addAll(item.getValue());
                        lancamentos += item.getValue().size();
                    }
                } catch (Exception e) {
                    System.err.println("alece: erro ao ler despesas de " + ano + "-" + mes + ": " + e.getMessage());
                }
            }
        }
        System.out.println("alece: " + lancamentos + " lançamentos da verba de desempenho parlamentar em " + porSlug.size()
                + " deputados; " + descartes[0] + " descartados por nome sem perfil de deputado em exercício");
        return porSlug;
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alece: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, SITE + "/deputados", arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alece/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alece: erro ao criar pasta de blocos", e);
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, List<OrgaoOuFrente>> comissoesPorSlug = extrairComissoes(ctx);
        Map<String, List<Despesa>> despesasPorSlug = despesasPorSlug(ctx, deputados);
        Votacoes votacoes = votacoesPorSlug(ctx, deputados);

        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String nomeFormatado = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();

            String urlPerfil = SITE + "/deputados/" + slug;
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + slug + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_31, FIM_LEG_31, "Em exercício", "Titular", LEGISLATURA_31, 2022,
                    "Deputado Estadual na 31ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALECE", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alece", slug, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<OrgaoOuFrente> orgaos = comissoesPorSlug.getOrDefault(slug, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesasPorSlug.getOrDefault(slug, List.of())));
            blocosNode.set("votos", Json.SAIDA.valueToTree(votacoes.votos().getOrDefault(slug, List.of())));
            blocosNode.set("participacao_votacoes", Json.SAIDA.valueToTree(votacoes.participacao().getOrDefault(slug, List.of())));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(slug + ".json"), blocosNode);
        }

        Json.gravar(ctx.normalizado().resolve("alece/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alece: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx) {
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        for (String comSlug : COMISSOES_SLUGS) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alece", "comissoes/" + comSlug + ".html");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                String url = SITE + "/comissoes-permanentes/" + comSlug + "/composicao";
                Prov prov = Prov.de(FONTE_DETALHES, url, arq);
                Map<String, List<OrgaoOuFrente>> doHtml = extrairComissoesDoHtml(html, comSlug, prov);
                for (var entry : doHtml.entrySet()) {
                    mapa.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("alece: erro ao ler comissao " + comSlug + ": " + e.getMessage());
            }
        }
        return mapa;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(String html, String comSlug, Prov prov) {
        if (html == null || html.isBlank()) {
            return Map.of();
        }
        String nomeComissao = "Comissão";
        Matcher mTitulo = TITULO_COMISSAO_PATTERN.matcher(html);
        if (mTitulo.find()) {
            nomeComissao = "Comissão de " + Texto.semEntidades(mTitulo.group(1).trim());
        }

        Matcher mTable = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL).matcher(html);
        if (!mTable.find()) {
            return Map.of();
        }
        String table = mTable.group(1);
        String[] sections = Pattern.compile("<span[^>]*><strong>(PRESIDENTE|VICE-PRESIDENTE|MEMBROS?)</strong></span>", Pattern.CASE_INSENSITIVE).split(table);
        Matcher mHeaders = Pattern.compile("<span[^>]*><strong>(PRESIDENTE|VICE-PRESIDENTE|MEMBROS?)</strong></span>", Pattern.CASE_INSENSITIVE).matcher(table);

        List<String> headerList = new ArrayList<>();
        while (mHeaders.find()) {
            headerList.add(mHeaders.group(1).toUpperCase(Locale.ROOT));
        }

        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();
        for (int i = 0; i < headerList.size() && i + 1 < sections.length; i++) {
            String secHeader = headerList.get(i);
            String content = sections[i + 1];
            String[] cells = Pattern.compile("<td[^>]*>").split(content);
            for (String cell : cells) {
                Matcher mDep = Pattern.compile("href=\"https://www\\.al\\.ce\\.gov\\.br/deputados/([a-z0-9-]+)\"").matcher(cell);
                if (mDep.find()) {
                    String slug = mDep.group(1).trim();
                    boolean isSuplente = cell.contains("Suplente");
                    String papel;
                    if (secHeader.contains("PRESIDENTE") && !secHeader.contains("VICE")) {
                        papel = isSuplente ? "Presidente (Suplente)" : "Presidente (Titular)";
                    } else if (secHeader.contains("VICE")) {
                        papel = isSuplente ? "Vice-Presidente (Suplente)" : "Vice-Presidente (Titular)";
                    } else {
                        papel = isSuplente ? "Suplente" : "Membro";
                    }
                    resultado.computeIfAbsent(slug, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", nomeComissao, null, papel, INICIO_LEG_31, null, prov));
                }
            }
        }
        return resultado;
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alece", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alece: erro ao ler deputados.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String[] partes = CARD_CONTAINER_PATTERN.split(html);
        if (partes.length <= 1) {
            return List.of();
        }
        List<DeputadoBruto> deputados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (int i = 1; i < partes.length; i++) {
            String trecho = partes[i];
            Matcher mImg = IMG_PATTERN.matcher(trecho);
            Matcher mSlug = SLUG_NOME_PATTERN.matcher(trecho);
            Matcher mPartido = PARTIDO_PATTERN.matcher(trecho);
            if (mImg.find() && mSlug.find() && mPartido.find()) {
                String slug = mSlug.group(1).trim();
                if (vistos.add(slug)) {
                    String foto = mImg.group(1).trim().replace(" ", "%20");
                    String nome = Texto.semEntidades(mSlug.group(2).trim());
                    String partido = Texto.semEntidades(mPartido.group(1).trim());
                    deputados.add(new DeputadoBruto(slug, nome, partido, foto));
                }
            }
        }
        return deputados;
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
}
