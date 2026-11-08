package etl.fontes;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Assembleia Legislativa do Estado de Goiás (ALEGO, ADR-0022).
 * Coleta os deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados de mandato, órgãos e frentes parlamentares, fotografias oficiais em alta resolução e links institucionais.
 */
public final class Alego implements Fonte {

    public static final String FONTE_DEPUTADOS = "alego-deputados";
    public static final String FONTE_DETALHES = "alego-deputados-detalhes";
    public static final String FONTE_PORTAL = "alego-portal";

    public static final String SITE = "https://portal.al.go.leg.br";
    public static final String URL_DEPUTADOS = "https://portal.al.go.leg.br/deputados/em-exercicio";
    public static final String FONTE_DESPESAS = "alego-despesas";
    public static final String URL_DESPESAS = "https://transparencia.al.go.leg.br/api/transparencia/verbas_indenizatorias";
    public static final String FONTE_VOTACOES = "alego-votacoes";
    public static final String URL_VOTACOES = "https://transparencia.al.go.leg.br/api/transparencia/votacoes.json";
    /** Primeiro ano das despesas: a 20ª legislatura começa em 2023. */
    static final int PRIMEIRO_ANO_DESPESAS = 2023;
    public static final String UF = "GO";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    public static final Pattern LINHA_DEPUTADO_PATTERN = Pattern.compile(
            "<tr[^>]*data-target=[\"']search\\.filterable[\"'][^>]*>\\s*" +
                    "<td[^>]*data-title=[\"']Nome[\"'][^>]*>\\s*" +
                    "<a[^>]*href=[^>]*deputados/perfil/([0-9]+)[^>]*>([^<]+)</a>\\s*</td>\\s*" +
                    "<td[^>]*data-title=[\"']Partido[\"'][^>]*>([^<]+)</td>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern SIGLA_PARTIDO_PATTERN = Pattern.compile(
            "\\(([^)]+)\\)\\s*$");

    public static final Pattern FOTO_PATTERN = Pattern.compile(
            "<figure class=[\"']perfil__foto[\"']>\\s*<img[^>]+src=[\"']([^\"']+)[\"']");

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<b>Nome Civil:</b>\\s*<span>([^<]+)</span>");

    public static final Pattern NASCIMENTO_PATTERN = Pattern.compile(
            "<strong>(?:Data de )?Nascimento:?</strong>\\s*:?\\s*([0-9]{1,2}\\s+de\\s+[a-zçA-ZÇ]+\\s+de\\s+[0-9]{4}|[0-9]{2}/[0-9]{2}/[0-9]{4})",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NATURALIDADE_PATTERN = Pattern.compile(
            "<strong>Naturalidade:</strong>\\s*([^<]+)<br", Pattern.CASE_INSENSITIVE);

    public static final Pattern TITULO_COMISSAO_PATTERN = Pattern.compile(
            "<header class=[\"']heading[\"']>\\s*<h2 class=[\"']titulo[\"']>\\s*<div class=[\"']ico-texto[\"']>.*?<span class=[\"']texto[\"']>([^<]+)</span>",
            Pattern.DOTALL);

    public static final Pattern COMISSAO_PRESIDENTE_PATTERN = Pattern.compile(
            "<li><b>Presidente:</b>\\s*<a[^>]*href=[^>]*deputados/perfil/([0-9]+)[^>]*>([^<]+)</a>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern COMISSAO_VICE_PATTERN = Pattern.compile(
            "<li class=[\"']lista__item[\"']>\\s*<b>Vice-presidente:\\s*</b>\\s*<a[^>]*href=[^>]*deputados/perfil/([0-9]+)[^>]*>([^<]+)</a>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern COMISSAO_MEMBRO_PATTERN = Pattern.compile(
            "<li class=[\"']lista__item[\"']>\\s*<a class=[\"']link[\"'] href=[\"']?/deputados/perfil/([0-9]+)[\"']?>([^<]+)</a>",
            Pattern.CASE_INSENSITIVE);

    public static final List<String> COMISSOES_IDS = List.of(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "16", "18", "19", "21", "22",
            "25", "39", "40", "42", "46", "47", "48", "49", "50", "54",
            "55", "58", "59", "60", "62", "63", "65", "66", "68", "69",
            "70", "71", "79", "80", "81", "82", "83", "91", "94"
    );

    private static final Map<String, Integer> MESES_PT = Map.ofEntries(
            Map.entry("janeiro", 1), Map.entry("fevereiro", 2), Map.entry("marco", 3), Map.entry("março", 3),
            Map.entry("abril", 4), Map.entry("maio", 5), Map.entry("junho", 6), Map.entry("julho", 7),
            Map.entry("agosto", 8), Map.entry("setembro", 9), Map.entry("outubro", 10), Map.entry("novembro", 11),
            Map.entry("dezembro", 12)
    );

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alego";
    }

    public record DeputadoBruto(String id, String nome, String partido) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alego: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/deputados/perfil/" + d.id();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);

            String urlBio = SITE + "/deputados/perfil-biografico/" + d.id();
            coleta.obterOuVazio(urlBio, "biografias/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        for (String comId : COMISSOES_IDS) {
            String urlCom = SITE + "/comissoes/" + comId;
            coleta.obterOuVazio(urlCom, "comissoes/" + comId + ".html", "text/html", Politica.SEMANAL);
        }

        // Verba indenizatória: o resumo do mês diz quem tem lançamento, e o detalhe traz cada lançamento.
        // O id do deputado no portal da transparência é o mesmo do portal institucional.
        Set<String> ids = new HashSet<>();
        deputados.forEach(d -> ids.add(d.id()));
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            Politica politica = Politica.porAnoSemanal(ano, ctx.hoje());
            int ultimoMes = ano == ctx.hoje().getYear() ? ctx.hoje().getMonthValue() : 12;
            for (int mes = 1; mes <= ultimoMes; mes++) {
                Coleta.Arquivo resumo = coleta.obterOpcional(URL_DESPESAS + ".json?ano=" + ano + "&mes=" + mes + "&todos=true",
                        arquivoDoResumo(ano, mes), "application/json", politica);
                if (resumo == null || resumo.bytes() == 0) {
                    continue;
                }
                int anoDoMes = ano;
                int mesDoAno = mes;
                // Os deputados do mês em paralelo, no limite de concorrência do cliente HTTP.
                List<String> falhas = Camara.paraCadaDeputado(deputadosDoResumo(Json.ler(Coleta.caminho(ctx, resumo)), ids),
                        id -> coleta.obterOpcional(URL_DESPESAS + "/exibir.json?ano=" + anoDoMes + "&mes=" + mesDoAno
                                + "&deputado_id=" + id, arquivoDoDetalhe(anoDoMes, mesDoAno, id), "application/json", politica));
                for (String falha : falhas) {
                    coleta.falhaParcial("despesas", falha);
                }
            }
        }

        // Votações nominais em plenário: JSON anual com tipo=detalhes.
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            coleta.obterOpcional(URL_VOTACOES + "?todos=true&ano=" + ano + "&tipo=detalhes",
                    arquivoDeVotacoes(ano), "application/json", Politica.porAnoSemanal(ano, ctx.hoje()));
        }
    }

    private static String arquivoDoResumo(int ano, int mes) {
        return String.format(Locale.ROOT, "despesas/%d-%02d.json", ano, mes);
    }

    private static String arquivoDoDetalhe(int ano, int mes, String id) {
        return String.format(Locale.ROOT, "despesas/%d-%02d/%s.json", ano, mes, id);
    }

    /** Ids do resumo mensal que estão entre os deputados em exercício. */
    static List<String> deputadosDoResumo(JsonNode resumo, Set<String> ids) {
        List<String> presentes = new ArrayList<>();
        for (JsonNode linha : resumo) {
            String id = Json.texto(linha.path("deputado"), "id");
            if (id != null && ids.contains(id) && !presentes.contains(id)) {
                presentes.add(id);
            }
        }
        return presentes;
    }

    /**
     * Lançamentos de um mês de verba indenizatória. O valor é o indenizado, que é o que a Casa pagou.
     * O ano e o mês são os da verba; a data é a do documento. CNPJ fica; CPF de prestador pessoa física sai.
     */
    public static List<Despesa> despesasDoMes(JsonNode detalhe, Prov prov) {
        int ano = detalhe.path("ano").asInt();
        int mes = detalhe.path("mes").asInt();
        List<Despesa> despesas = new ArrayList<>();
        for (JsonNode grupo : detalhe.path("grupos")) {
            String nomeGrupo = semNumeracao(Json.texto(grupo, "descricao"));
            for (JsonNode subgrupo : grupo.path("subgrupos")) {
                String nomeSubgrupo = semNumeracao(Json.texto(subgrupo, "descricao"));
                String categoria = nomeSubgrupo == null || nomeSubgrupo.equalsIgnoreCase("geral") ? nomeGrupo
                        : nomeGrupo + " - " + nomeSubgrupo;
                for (JsonNode lancamento : subgrupo.path("lancamentos")) {
                    JsonNode fornecedor = lancamento.path("fornecedor");
                    BigDecimal valor = Texto.valor(Json.texto(fornecedor, "valor_indenizado"));
                    if (valor == null || categoria == null) {
                        continue;
                    }
                    String documento = Json.texto(fornecedor, "cnpj_cpf");
                    String digitos = documento == null ? "" : documento.replaceAll("\\D", "");
                    despesas.add(new Despesa(diaDoDocumento(Json.texto(fornecedor, "data")),
                            ano, mes, categoria, Guarda.semCpf(Json.texto(fornecedor, "nome")),
                            digitos.length() == 14 ? digitos : null, valor, Json.texto(fornecedor, "numero"), null, prov));
                }
            }
        }
        return despesas;
    }

    /** Data do documento ("2024-02-21T00:00:00.000-03:00"), ou null quando a fonte traz outro formato. */
    static LocalDate diaDoDocumento(String data) {
        if (data == null || data.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(data.substring(0, 10));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** "09 - VEÍCULOS" vira "VEÍCULOS". */
    private static String semNumeracao(String descricao) {
        if (descricao == null) {
            return null;
        }
        String texto = descricao.replaceFirst("^\\d+\\s*-\\s*", "").replaceAll("\\s+", " ").strip();
        return texto.isEmpty() ? null : texto;
    }

    /** Despesas de cada deputado em exercício, pelos detalhes mensais em cache. */
    private Map<String, List<Despesa>> despesasPorDeputado(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, List<Despesa>> porDeputado = new HashMap<>();
        int lancamentos = 0;
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            for (int mes = 1; mes <= 12; mes++) {
                for (DeputadoBruto d : deputados) {
                    Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDoDetalhe(ano, mes, d.id()));
                    if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                        continue;
                    }
                    List<Despesa> doMes = despesasDoMes(Json.ler(Coleta.caminho(ctx, arq)), Prov.de(FONTE_DESPESAS, arq.url(), arq));
                    porDeputado.computeIfAbsent(d.id(), k -> new ArrayList<>()).addAll(doMes);
                    lancamentos += doMes.size();
                }
            }
        }
        System.out.println("alego: " + lancamentos + " lançamentos de verba indenizatória em " + porDeputado.size() + " deputados");
        return porDeputado;
    }

    public static String arquivoDeVotacoes(int ano) {
        return String.format(Locale.ROOT, "votacoes/%d.json", ano);
    }

    public record Votacoes(Map<String, List<Voto>> votos, Map<String, List<Participacao>> participacao) {
    }

    public static Votacoes votacoesPorId(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, String> idPorNome = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            idPorNome.put(chaveDoNome(d.nome()), d.id());
        }

        Map<String, List<Voto>> votos = new HashMap<>();
        Map<String, Map<Integer, int[]>> contagem = new HashMap<>();
        Map<String, Map<Integer, Map<String, Integer>>> porTipo = new HashMap<>();
        Map<Integer, Coleta.Arquivo> arqPorAno = new HashMap<>();
        int semPerfil = 0;

        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alego", arquivoDeVotacoes(ano));
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            arqPorAno.put(ano, arq);
            try {
                JsonNode lista = Json.ler(Coleta.caminho(ctx, arq));
                if (!lista.isArray()) {
                    continue;
                }
                Prov prov = Prov.de(FONTE_VOTACOES, arq.url(), arq);
                for (JsonNode item : lista) {
                    String parlamentar = Json.texto(item, "parlamentar");
                    if (parlamentar == null || parlamentar.isBlank()) {
                        continue;
                    }
                    String id = idPorNome.get(chaveDoNome(parlamentar));
                    if (id == null) {
                        semPerfil++;
                        continue;
                    }
                    String dataSessao = Json.texto(item, "data_sessao");
                    if (dataSessao == null || dataSessao.isBlank()) {
                        continue;
                    }
                    int anoVoto = ano;
                    try {
                        anoVoto = LocalDate.parse(dataSessao).getYear();
                    } catch (Exception ignored) {
                    }
                    int pautaId = item.path("pauta_id").asInt();
                    String idVotacao = "pauta-" + pautaId + "-" + dataSessao;
                    String ementa = Guarda.semCpf(Json.texto(item, "ementa_projeto"));
                    String votoBruto = Json.texto(item, "tipo_voto");
                    String votoTexto = formatarVoto(votoBruto);
                    String partido = Json.texto(item, "partido_sigla");

                    votos.computeIfAbsent(id, k -> new ArrayList<>()).add(new Voto(
                            idVotacao, dataSessao, "PLEN", null, ementa, votoTexto,
                            partido, null, null, null, prov));

                    int[] n = contagem.computeIfAbsent(id, k -> new HashMap<>())
                            .computeIfAbsent(anoVoto, k -> new int[2]);
                    n[0]++;
                    if (!"Sem voto registrado".equals(votoTexto)) {
                        n[1]++;
                    }
                    porTipo.computeIfAbsent(id, k -> new HashMap<>())
                            .computeIfAbsent(anoVoto, k -> new java.util.TreeMap<>())
                            .merge(votoTexto, 1, Integer::sum);
                }
            } catch (Exception e) {
                System.err.println("alego: erro ao ler votações de " + ano + ": " + e.getMessage());
            }
        }

        Map<String, List<Participacao>> participacao = new HashMap<>();
        for (var porDeputado : contagem.entrySet()) {
            String id = porDeputado.getKey();
            for (var entryAno : new java.util.TreeMap<>(porDeputado.getValue()).entrySet()) {
                int anoInt = entryAno.getKey();
                int totalVot = entryAno.getValue()[0];
                int presencas = entryAno.getValue()[1];
                int ausencias = totalVot - presencas;
                Coleta.Arquivo arqAno = arqPorAno.get(anoInt);
                Prov provAno = arqAno != null
                        ? Prov.de(FONTE_VOTACOES, arqAno.url(), arqAno)
                        : new Prov(FONTE_VOTACOES, URL_VOTACOES + "?todos=true&ano=" + anoInt + "&tipo=detalhes", ctx.agora());
                participacao.computeIfAbsent(id, k -> new ArrayList<>()).add(new Participacao(
                        String.valueOf(anoInt), totalVot, presencas, ausencias,
                        porTipo.get(id).get(anoInt), provAno));
            }
        }

        int totalVotos = votos.values().stream().mapToInt(List::size).sum();
        System.out.println("alego: " + totalVotos + " votos nominais em " + votos.size() + " deputados; "
                + semPerfil + " linhas de quem não tem perfil em exercício");
        return new Votacoes(votos, participacao);
    }

    public static String formatarVoto(String voto) {
        if (voto == null || voto.isBlank()) {
            return "Sem voto registrado";
        }
        String v = voto.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "SIM" -> "Sim";
            case "NÃO", "NAO" -> "Não";
            case "ABSTENÇÃO", "ABSTENCAO" -> "Abstenção";
            default -> v.substring(0, 1).toUpperCase(Locale.ROOT) + v.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    public static String chaveDoNome(String nome) {
        if (nome == null) {
            return "";
        }
        String s = nome.toUpperCase(Locale.ROOT).trim();
        s = s.replaceAll("^(?i)(DR[ªA]?\\.?|DOUTOR[A]?|DEL\\.?|DELEGAD[OA]|CORONEL|MAJOR|PROFESSOR[A]?|PR\\.?)\\s+", "");
        return Texto.normalizarNome(s);
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alego: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alego/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alego: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairComissoes(ctx);
        Map<String, List<Despesa>> despesasPorDeputado = despesasPorDeputado(ctx, deputados);
        Votacoes votacoes = votacoesPorId(ctx, deputados);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeFormatado = formatarNome(d.nome());
            String partido = extrairSiglaPartido(d.partido());

            String urlPerfil = SITE + "/deputados/perfil/" + id;
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            String fotoUrl = null;
            String nomeCivil = null;

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                    Matcher mFoto = FOTO_PATTERN.matcher(htmlPerfil);
                    if (mFoto.find()) {
                        fotoUrl = mFoto.group(1).trim();
                    }
                    Matcher mCivil = NOME_CIVIL_PATTERN.matcher(htmlPerfil);
                    if (mCivil.find()) {
                        nomeCivil = formatarNome(mCivil.group(1).trim());
                    }
                } catch (Exception e) {
                    System.err.println("alego: aviso ao ler perfil " + id + ": " + e.getMessage());
                }
            }

            LocalDate nascimento = null;
            Coleta.Arquivo arqBio = Coleta.metadados(ctx, nome(), "biografias/" + id + ".html");
            if (arqBio != null && Files.exists(Coleta.caminho(ctx, arqBio))) {
                try {
                    String htmlBio = Texto.lerArquivo(Coleta.caminho(ctx, arqBio));
                    Matcher mNasc = NASCIMENTO_PATTERN.matcher(htmlBio);
                    if (mNasc.find()) {
                        nascimento = parseDataNascimento(mNasc.group(1));
                    }
                } catch (Exception e) {
                    System.err.println("alego: aviso ao ler biografia " + id + ": " + e.getMessage());
                }
            }

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, "Em exercício", "Titular", LEGISLATURA_20, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALEGO", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alego", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(id, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesasPorDeputado.getOrDefault(id, List.of())));
            blocosNode.set("votos", Json.SAIDA.valueToTree(votacoes.votos().getOrDefault(id, List.of())));
            blocosNode.set("participacao_votacoes", Json.SAIDA.valueToTree(votacoes.participacao().getOrDefault(id, List.of())));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("alego/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alego: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alego", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alego: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        List<DeputadoBruto> lista = new ArrayList<>();
        Set<String> idsVistos = new HashSet<>();
        Matcher m = LINHA_DEPUTADO_PATTERN.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            String nome = m.group(2).trim();
            String partido = m.group(3).trim();
            if (idsVistos.add(id)) {
                lista.add(new DeputadoBruto(id, nome, partido));
            }
        }
        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx) {
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        for (String comId : COMISSOES_IDS) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alego", "comissoes/" + comId + ".html");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                String urlOrigem = SITE + "/comissoes/" + comId;
                Prov prov = Prov.de(FONTE_DETALHES, urlOrigem, arq);
                Map<String, List<OrgaoOuFrente>> doHtml = extrairComissoesDoHtml(html, comId, prov);
                for (var entry : doHtml.entrySet()) {
                    mapa.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("alego: erro ao extrair comissao " + comId + ": " + e.getMessage());
            }
        }
        return mapa;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(String html, String comId, Prov prov) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        String titulo = null;
        Matcher mTitulo = TITULO_COMISSAO_PATTERN.matcher(html);
        if (mTitulo.find()) {
            titulo = mTitulo.group(1).trim();
        } else {
            titulo = "Comissão " + comId;
        }

        String tipo = titulo.toLowerCase(Locale.ROOT).contains("frente") ? "frente" : "comissao";

        // Segmentar a seção equipe se existir
        int idxEquipe = html.indexOf("class=\"equipe\"");
        String fragmentoEquipe = idxEquipe != -1 ? html.substring(idxEquipe) : html;

        // Separar bloco Titulares e bloco Suplentes
        int idxSuplentes = fragmentoEquipe.indexOf("Suplentes");
        String blocoTitulares = idxSuplentes != -1 ? fragmentoEquipe.substring(0, idxSuplentes) : fragmentoEquipe;
        String blocoSuplentes = idxSuplentes != -1 ? fragmentoEquipe.substring(idxSuplentes) : "";

        // No bloco de Titulares:
        Set<String> idsTitularesProcessados = new HashSet<>();

        // Presidente
        Matcher mPres = COMISSAO_PRESIDENTE_PATTERN.matcher(blocoTitulares);
        if (mPres.find()) {
            String idPres = mPres.group(1).trim();
            resultado.computeIfAbsent(idPres, k -> new ArrayList<>())
                    .add(new OrgaoOuFrente(tipo, titulo, null, "Presidente", INICIO_LEG_20, null, prov));
            idsTitularesProcessados.add(idPres);
        }

        // Vice-presidente
        Matcher mVice = COMISSAO_VICE_PATTERN.matcher(blocoTitulares);
        if (mVice.find()) {
            String idVice = mVice.group(1).trim();
            resultado.computeIfAbsent(idVice, k -> new ArrayList<>())
                    .add(new OrgaoOuFrente(tipo, titulo, null, "Vice-Presidente", INICIO_LEG_20, null, prov));
            idsTitularesProcessados.add(idVice);
        }

        // Demais titulares
        Matcher mMembroTit = COMISSAO_MEMBRO_PATTERN.matcher(blocoTitulares);
        while (mMembroTit.find()) {
            String idMembro = mMembroTit.group(1).trim();
            if (idsTitularesProcessados.add(idMembro)) {
                resultado.computeIfAbsent(idMembro, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente(tipo, titulo, null, "Titular", INICIO_LEG_20, null, prov));
            }
        }

        // No bloco de Suplentes:
        Matcher mMembroSupl = COMISSAO_MEMBRO_PATTERN.matcher(blocoSuplentes);
        Set<String> idsSuplentesProcessados = new HashSet<>();
        while (mMembroSupl.find()) {
            String idSupl = mMembroSupl.group(1).trim();
            if (idsSuplentesProcessados.add(idSupl)) {
                resultado.computeIfAbsent(idSupl, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente(tipo, titulo, null, "Suplente", INICIO_LEG_20, null, prov));
            }
        }

        return resultado;
    }

    public static String extrairSiglaPartido(String partidoCompleto) {
        if (partidoCompleto == null || partidoCompleto.isBlank()) {
            return "S/PARTIDO";
        }
        Matcher m = SIGLA_PARTIDO_PATTERN.matcher(partidoCompleto);
        if (m.find()) {
            return m.group(1).trim().toUpperCase(Locale.ROOT);
        }
        return partidoCompleto.trim().toUpperCase(Locale.ROOT);
    }

    public static LocalDate parseDataNascimento(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        if (limpo.contains("/")) {
            try {
                return LocalDate.parse(limpo, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (Exception ignored) {
            }
        }

        String[] partes = limpo.toLowerCase(Locale.ROOT).split("\\s+de\\s+");
        if (partes.length == 3) {
            try {
                int dia = Integer.parseInt(partes[0].trim());
                String mesStr = partes[1].trim();
                Integer mes = MESES_PT.get(mesStr);
                int ano = Integer.parseInt(partes[2].trim());
                if (mes != null) {
                    return LocalDate.of(ano, mes, dia);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String formatarNome(String nomeBruto) {
        if (nomeBruto == null || nomeBruto.isBlank()) {
            return "";
        }
        String[] partes = nomeBruto.trim().replaceAll("\\s+", " ").split(" ");
        List<String> formatadas = new ArrayList<>();
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            String pLower = p.toLowerCase(Locale.ROOT);
            if (i > 0 && PREPOSICOES.contains(pLower)) {
                formatadas.add(pLower);
            } else if (p.length() > 0) {
                formatadas.add(p.substring(0, 1).toUpperCase(Locale.ROOT) + p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return String.join(" ", formatadas);
    }
}
