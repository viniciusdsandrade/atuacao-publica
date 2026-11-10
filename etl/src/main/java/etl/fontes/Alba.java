package etl.fontes;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
import etl.Registros.Despesa;
import etl.Registros.Evento;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado da Bahia (ALBA) (ADR-0017).
 * Coleta os 63 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados biográficos individuais e composição de comissões permanentes e temporárias.
 */
public final class Alba implements Fonte {

    public static final String FONTE_DEPUTADOS = "alba-deputados";
    public static final String FONTE_DETALHES = "alba-deputados-detalhes";
    public static final String FONTE_COMISSOES = "alba-comissoes";
    public static final String FONTE_PORTAL = "alba-portal";
    public static final String FONTE_DESPESAS = "alba-despesas";
    public static final String FONTE_PROPOSICOES = "alba-proposicoes";
    public static final String FONTE_PRESENCA = "alba-presenca";

    public static final String SITE = "https://www.al.ba.gov.br";
    public static final String UF = "BA";
    /** A grafia "idenizatorias" é a do endereço do site. */
    public static final String URL_VERBAS = SITE + "/transparencia/verbas-idenizatorias";
    public static final String API_PROPOSICOES = "https://albalegis.nopapercloud.com.br/api/publico/proposicao/";
    /** Primeiro ano das despesas e das proposições: início da cobertura. */
    static final int PRIMEIRO_ANO = 2022;
    /** Lançamentos por página da verba (o site ignora tamanho maior) e itens por página da API (maior responde erro). */
    static final int TAMANHO_DA_PAGINA = 20;
    private static final Pattern PAGINA_DAS_VERBAS = Pattern.compile("[?&;]page=([0-9]+)&(?:amp;)?size=20");
    private static final Pattern CELULA = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern DETALHE_DA_VERBA = Pattern.compile("/transparencia/verbas-idenizatorias/([0-9]+)/");
    private static final Pattern COMPETENCIA = Pattern.compile("([0-9]{2})/([0-9]{4})");
    public static final String URL_SESSOES = "https://albalegis.nopapercloud.com.br/spl/sessoes.aspx";
    public static final String URL_PRESENCA = "https://albalegis.nopapercloud.com.br/spl/sessao-parlamentares.aspx?id=";
    private static final String CAMPO = "ctl00$ContentPlaceHolder1$";
    private static final Pattern CAMPO_OCULTO = Pattern.compile("<input type=\"hidden\" name=\"([^\"]+)\" id=\"[^\"]*\" value=\"([^\"]*)\"");
    private static final Pattern TOTAL_DE_PAGINAS = Pattern.compile(
            "name=\"ctl00\\$ContentPlaceHolder1\\$lvSessoes_TotalPages\" id=\"[^\"]*\" value=\"([0-9]+)\"");
    private static final Pattern LINK_DE_PRESENCA = Pattern.compile("sessao-parlamentares\\.aspx\\?id=([0-9]+)");
    private static final Pattern CABECALHO_DA_PRESENCA = Pattern.compile(
            "Lista de Presença da Sessão\\s+(.+?)\\s+N°\\s*([0-9]+)\\s+Data:\\s*([0-9]{2}/[0-9]{2}/[0-9]{4})");
    private static final Pattern LINHA_DE_PRESENCA = Pattern.compile(
            "<a href=\"parlamentar\\.aspx\\?id=[0-9]+\"[^>]*>\\s*([^<]+?)\\s*</a>.*?<strong>\\s*([^<]+?)\\s*</strong>", Pattern.DOTALL);
    /** Situação de presença da fonte e o valor publicado; outra situação fica fora e aparece na saída do normalize. */
    static final Map<String, String> PRESENCAS = Map.of("PRESENTE", "registrada", "FALTA JUSTIFICADA", "ausencia_justificada",
            "FALTA", "falta");

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    public static final LocalDate INICIO_LEG_19 = LocalDate.of(2019, 2, 1);
    public static final LocalDate FIM_LEG_19 = LocalDate.of(2023, 1, 31);
    public static final int LEGISLATURA_19 = 19;

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "<a\\s+href=[\"']/deputados/deputado-estadual/([0-9]+)[\"'].*?" +
                    "<img\\s+src=[\"']([^\"']+)[\"'].*?" +
                    "<div\\s+class=[\"']deputado-nome[\"']>\\s*<a[^>]*>\\s*<span>([^<]+)</span>\\s*</a>\\s*</div>\\s*" +
                    "<div\\s+class=[\"']partido-nome[\"']>\\s*([^<]+)\\s*</div>",
            Pattern.DOTALL);

    private static final Pattern COMISSAO_LINK_PATTERN = Pattern.compile(
            "href=[\"']/comissoes/composicao/([0-9]+)[\"']");

    private static final Pattern COMISSAO_TITULO_PATTERN = Pattern.compile(
            "<div class=[\"']titulo-presidente[\"']>\\s*<span>([^<]+)</span>");

    private static final Pattern PRESIDENTE_PATTERN = Pattern.compile(
            "<div class=[\"']inter-title[\"']>PRESIDENTE</div>\\s*<div>\\s*<span>([^<]+)</span>");

    private static final Pattern VICE_PRESIDENTE_PATTERN = Pattern.compile(
            "<div class=[\"']inter-title[\"']>VICE-PRESIDENTE</div>\\s*<div>\\s*<span>([^<]+)</span>");

    private static final Pattern LINHA_TABELA_PATTERN = Pattern.compile(
            "<tr class=[\"']table-itens[\"']>\\s*<td>\\s*<span>([^<]*)</span>\\s*</td>\\s*<td>\\s*<span>([^<]*)</span>\\s*</td>",
            Pattern.DOTALL);

    private static final Pattern NOME_MEMBRO_PATTERN = Pattern.compile(
            "^(.*?)(?:\\s*\\([^)]*\\))?$");

    private static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<strong>Nome:\\s*</strong>\\s*<span>([^<]+)</span>");

    private static final Pattern NASCIMENTO_PATTERN = Pattern.compile(
            "<strong>Nascimento:\\s*</strong>\\s*<span>(\\d{2}/\\d{2}/\\d{4})");

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alba";
    }

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl) {
    }

    public record DetalhePerfil(String nomeCivil, String nascimento) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        // Baixa a página principal com a listagem de deputados em exercício
        coleta.obter(SITE + "/deputados/deputados-estaduais", "deputados/deputados-estaduais.html", "text/html", Politica.SEMANAL);

        // Extrai a lista de deputados e baixa os perfis biográficos individuais
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto dep : deputados) {
            coleta.obterOuVazio(SITE + "/deputados/deputado-estadual/" + dep.id(),
                    "perfil/" + dep.id() + ".html", "text/html", Politica.SEMANAL);
        }

        // Baixa a página principal de comissões
        coleta.obterOuVazio(SITE + "/comissoes/composicao", "comissoes/composicao.html", "text/html", Politica.SEMANAL);

        // Extrai e baixa cada comissão individualmente
        List<String> comissoes = extrairIdsComissoes(ctx);
        for (String cid : comissoes) {
            coleta.obterOuVazio(SITE + "/comissoes/composicao/" + cid,
                    "comissoes/" + cid + ".html", "text/html", Politica.SEMANAL);
        }

        // Verba indenizatória (portal) e proposições (API do NoPaper), por ano. As duas listas vêm do item mais novo para
        // o mais antigo, e cada item novo desloca as páginas: o ano aberto renova inteiro no mesmo dia, uma vez por semana;
        // os fechados, só o que falta.
        List<String> tarefas = new ArrayList<>();
        for (int ano = PRIMEIRO_ANO; ano <= ctx.hoje().getYear(); ano++) {
            tarefas.add("verbas-" + ano);
            tarefas.add("proposicoes-" + ano);
        }
        tarefas.add("presenca-" + INICIO_LEG_20.getYear());
        for (String falha : Camara.paraCadaDeputado(tarefas, tarefa -> {
            int ano = Integer.parseInt(tarefa.substring(tarefa.indexOf('-') + 1));
            Politica politica = Politica.porAnoSemanalEmLista(ano, ctx.hoje());
            if (tarefa.startsWith("verbas")) {
                coletarVerbas(ctx, coleta, ano, politica);
            } else if (tarefa.startsWith("presenca")) {
                // A lista de sessões é uma cadeia de postbacks: os anos vão em sequência, na mesma tarefa.
                for (int a = INICIO_LEG_20.getYear(); a <= ctx.hoje().getYear(); a++) {
                    coletarPresencas(ctx, coleta, a, Politica.porAnoSemanalEmLista(a, ctx.hoje()));
                }
            } else {
                respostasDoAno(ano, (url, caminho) -> {
                    Coleta.Arquivo arq = coleta.obterOpcional(url, caminho, "application/json", politica);
                    return arq == null || !Files.exists(Coleta.caminho(ctx, arq)) ? null
                            : new Resposta(Json.ler(Coleta.caminho(ctx, arq)), arq);
                });
            }
        })) {
            coleta.falhaParcial("verbas-e-proposicoes", falha);
        }
    }

    private static void coletarVerbas(Contexto ctx, Coleta coleta, int ano, Politica politica) throws Exception {
        Coleta.Arquivo primeira = coleta.obterOpcional(urlDasVerbas(ano, 1), arquivoDasVerbas(ano, 1), "text/html", politica);
        if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
            return;
        }
        int paginas = paginasDasVerbas(Texto.lerArquivo(Coleta.caminho(ctx, primeira)));
        for (int pagina = 2; pagina <= paginas; pagina++) {
            coleta.obterOpcional(urlDasVerbas(ano, pagina), arquivoDasVerbas(ano, pagina), "text/html", politica);
        }
    }

    /**
     * Presença em plenário no NoPaper. A lista de sessões do ano, filtrada pelo documento "Presença em Plenário", abre por
     * GET; as páginas seguintes só saem por postback ASP.NET, com o formulário da página anterior. Cada link leva à presença
     * da sessão.
     */
    private static void coletarPresencas(Contexto ctx, Coleta coleta, int ano, Politica politica) throws Exception {
        String url = urlDaListaDePresenca(ano);
        String anterior = null;
        Set<String> ids = new java.util.LinkedHashSet<>();
        int paginas = 1;
        for (int pagina = 1; pagina <= paginas; pagina++) {
            Coleta.Arquivo arq = pagina == 1
                    ? coleta.obterOpcional(url, arquivoDaListaDePresenca(ano, pagina), "text/html", politica)
                    : coleta.obterFormularioOpcional(url, camposDaProximaPagina(anterior, ano), arquivoDaListaDePresenca(ano, pagina),
                            "text/html", politica);
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            anterior = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            if (pagina == 1) {
                paginas = totalDePaginas(anterior);
            }
            ids.addAll(presencasDaLista(anterior));
        }
        Politica daSessao = Politica.porAnoSemanal(ano, ctx.hoje());
        for (String id : ids) {
            coleta.obterOpcional(URL_PRESENCA + id, arquivoDaPresenca(id), "text/html", daSessao);
        }
    }

    /** Lista de sessões do ano com presença em plenário, filtrada pela própria URL. */
    static String urlDaListaDePresenca(int ano) {
        return URL_SESSOES + "?arquivo_tipo=" + java.net.URLEncoder.encode("Presença em Plenário", java.nio.charset.StandardCharsets.UTF_8)
                + "&dt_ini=01-01-" + ano + "&dt_fim=31-12-" + ano;
    }

    /**
     * Campos do postback de próxima página: todos os ocultos da página anterior, inclusive a página atual, que o servidor
     * lê do formulário, e o filtro do ano, que sem os campos volta a ser a lista inteira.
     */
    static Map<String, String> camposDaProximaPagina(String anterior, int ano) {
        Map<String, String> campos = new LinkedHashMap<>();
        Matcher m = CAMPO_OCULTO.matcher(anterior);
        while (m.find()) {
            campos.put(m.group(1), Texto.semEntidades(m.group(2)));
        }
        campos.put("__EVENTTARGET", CAMPO + "lvSessoes_lbNext");
        campos.put("__EVENTARGUMENT", "");
        campos.put(CAMPO + "ddl_tipo_sessao", "");
        campos.put(CAMPO + "ddl_sessao_arquivo_tipo", "Presença em Plenário");
        campos.put(CAMPO + "txt_data_inicio", ano + "-01-01");
        campos.put(CAMPO + "txt_data_final", ano + "-12-31");
        return campos;
    }

    static int totalDePaginas(String html) {
        Matcher m = TOTAL_DE_PAGINAS.matcher(html);
        return m.find() ? Math.max(1, Integer.parseInt(m.group(1))) : 1;
    }

    /** Ids das presenças em plenário de uma página da lista de sessões. */
    static List<String> presencasDaLista(String html) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        Matcher m = LINK_DE_PRESENCA.matcher(html);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return new ArrayList<>(ids);
    }

    private static String arquivoDaListaDePresenca(int ano, int pagina) {
        return "presenca/lista-" + ano + "-" + pagina + ".html";
    }

    private static String arquivoDaPresenca(String id) {
        return "presenca/sessoes/" + id + ".html";
    }

    /** Presença de uma sessão: o tipo, o número, a data e, por deputado, o nome e a situação, como a fonte dá. */
    record PresencaDaSessao(String tipo, String numero, LocalDate data, List<String[]> linhas) {
    }

    static PresencaDaSessao presencaDaSessao(String html) {
        String texto = Texto.semEntidades(html).replace('\u00a0', ' ');
        Matcher cabecalho = CABECALHO_DA_PRESENCA.matcher(texto.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " "));
        if (!cabecalho.find()) {
            return null;
        }
        List<String[]> linhas = new ArrayList<>();
        for (String celula : texto.split("<td>")) {
            Matcher linha = LINHA_DE_PRESENCA.matcher(celula);
            if (linha.find()) {
                linhas.add(new String[] {linha.group(1).replaceAll("\\s+", " "), linha.group(2).replaceAll("\\s+", " ")});
            }
        }
        return new PresencaDaSessao(cabecalho.group(1).strip(), cabecalho.group(2),
                LocalDate.parse(cabecalho.group(3), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), linhas);
    }

    /** Presença em plenário de cada deputado em exercício, ligada pelo nome parlamentar. Cada sessão vira um evento. */
    record Presencas(Map<String, List<Evento>> porId, java.time.Instant capturadaEm) {
    }

    static Presencas presencasPorDeputado(Contexto ctx, Map<String, String> idPorNomeParlamentar) {
        Map<String, List<Evento>> porId = new HashMap<>();
        Map<String, Integer> semPerfil = new java.util.TreeMap<>();
        Map<String, Integer> outrasSituacoes = new java.util.TreeMap<>();
        java.time.Instant capturadaEm = null;
        int sessoes = 0;
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (int ano = INICIO_LEG_20.getYear(); ano <= ctx.hoje().getYear(); ano++) {
            int paginas = 1;
            for (int pagina = 1; pagina <= paginas; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "alba", arquivoDaListaDePresenca(ano, pagina));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                try {
                    String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                    if (pagina == 1) {
                        paginas = totalDePaginas(html);
                    }
                    ids.addAll(presencasDaLista(html));
                } catch (java.io.IOException e) {
                    System.err.println("alba: erro ao ler a lista de sessões " + ano + "-" + pagina + ": " + e.getMessage());
                }
            }
        }
        for (String id : ids) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alba", arquivoDaPresenca(id));
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                PresencaDaSessao sessao = presencaDaSessao(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                // Sessão de antes da 20ª legislatura (janeiro de 2023) fica fora: os perfis são da legislatura atual.
                if (sessao == null || sessao.linhas().isEmpty() || sessao.data().isBefore(INICIO_LEG_20)) {
                    continue;
                }
                sessoes++;
                capturadaEm = capturadaEm == null || arq.capturadoEm().isAfter(capturadaEm) ? arq.capturadoEm() : capturadaEm;
                Prov prov = Prov.de(FONTE_PRESENCA, URL_PRESENCA + id, arq);
                String tipo = "Sessão " + sessao.tipo();
                Set<String> jaContados = new HashSet<>();
                for (String[] linha : sessao.linhas()) {
                    String presenca = PRESENCAS.get(Texto.normalizarNome(linha[1]));
                    if (presenca == null) {
                        outrasSituacoes.merge(linha[1], 1, Integer::sum);
                        continue;
                    }
                    String deputado = idPorNomeParlamentar.get(chaveDoNome(linha[0]));
                    if (deputado == null) {
                        semPerfil.merge(linha[0], 1, Integer::sum);
                        continue;
                    }
                    if (jaContados.add(deputado)) {
                        porId.computeIfAbsent(deputado, k -> new ArrayList<>()).add(new Evento("sessao-" + id, sessao.data().toString(),
                                tipo, tipo + " nº " + sessao.numero(), "Plenário", null, presenca, prov));
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("alba: erro ao ler a presença da sessão " + id + ": " + e.getMessage());
            }
        }
        for (List<Evento> eventos : porId.values()) {
            eventos.sort(Comparator.comparing(Evento::data).reversed());
        }
        int total = porId.values().stream().mapToInt(List::size).sum();
        System.out.println("alba: presença em " + sessoes + " sessões, " + total + " registros em " + porId.size()
                + " deputados; outras situações: " + outrasSituacoes + "; nomes sem perfil em exercício: " + semPerfil.size()
                + " " + semPerfil.keySet());
        return new Presencas(porId, capturadaEm);
    }

    static String urlDasVerbas(int ano, int pagina) {
        return URL_VERBAS + "?ano=" + ano + "&categoria=&page=" + pagina + "&size=" + TAMANHO_DA_PAGINA;
    }

    private static String arquivoDasVerbas(int ano, int pagina) {
        return "verbas/" + ano + "-" + pagina + ".html";
    }

    /** Número de páginas da lista de um ano, pelo maior número de página dos links. */
    static int paginasDasVerbas(String html) {
        int maior = 1;
        Matcher m = PAGINA_DAS_VERBAS.matcher(html);
        while (m.find()) {
            maior = Math.max(maior, Integer.parseInt(m.group(1)));
        }
        return maior;
    }

    /** Lançamento da lista da verba: o id do detalhe, o processo, a nota, a competência, o deputado, a categoria e o valor. */
    record LancamentoDaVerba(String id, String processo, String nota, int mes, int ano, String deputado, String categoria,
                             BigDecimal valor) {
    }

    /** Lançamentos de uma página da lista. Linha sem link de detalhe, sem competência ou sem valor fica fora. */
    static List<LancamentoDaVerba> lancamentosDaPagina(String html) {
        List<LancamentoDaVerba> lancamentos = new ArrayList<>();
        String[] linhas = html.split("<tr class=\"table-itens");
        for (int i = 1; i < linhas.length; i++) {
            String linha = linhas[i];
            int fim = linha.indexOf("</tr>");
            linha = fim < 0 ? linha : linha.substring(0, fim);
            List<String> celulas = new ArrayList<>();
            Matcher celula = CELULA.matcher(linha);
            while (celula.find()) {
                celulas.add(textoDaCelula(celula.group(1)));
            }
            Matcher detalhe = DETALHE_DA_VERBA.matcher(linha);
            if (celulas.size() < 6 || !detalhe.find()) {
                continue;
            }
            Matcher competencia = COMPETENCIA.matcher(celulas.get(2));
            BigDecimal valor = Texto.valor(celulas.get(5).replace("R$", "").strip());
            if (!competencia.find() || valor == null) {
                continue;
            }
            lancamentos.add(new LancamentoDaVerba(detalhe.group(1), celulas.get(0), Texto.vazioParaNulo(celulas.get(1)),
                    Integer.parseInt(competencia.group(1)), Integer.parseInt(competencia.group(2)), celulas.get(3), celulas.get(4), valor));
        }
        return lancamentos;
    }

    private static String textoDaCelula(String html) {
        String texto = Texto.semEntidades(html.replaceAll("<[^>]+>", " "));
        return texto == null ? "" : texto.replaceAll("\\s+", " ").strip();
    }

    /** Chave do nome: sem acento e sem pontuação, em maiúsculas. Serve ao nome da lista, ao autor e ao perfil. */
    static String chaveDoNome(String nome) {
        return nome == null ? null : Texto.normalizarNome(nome.replaceAll("[^\\p{L}\\s]", " "));
    }

    /** Despesas de cada deputado em exercício, pelas páginas em cache, ligadas pelo nome parlamentar da lista. */
    static Map<String, List<Despesa>> despesasPorDeputado(Contexto ctx, Map<String, String> idPorChave) {
        Map<String, List<Despesa>> porId = new HashMap<>();
        Set<String> vistos = new HashSet<>();
        Map<String, Integer> semPerfil = new java.util.TreeMap<>();
        int lancamentos = 0;
        for (int ano = PRIMEIRO_ANO; ano <= ctx.hoje().getYear(); ano++) {
            int paginas = 1;
            for (int pagina = 1; pagina <= paginas; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "alba", arquivoDasVerbas(ano, pagina));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                try {
                    String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                    if (pagina == 1) {
                        paginas = paginasDasVerbas(html);
                    }
                    // O registro aponta para a lista do ano; o documento, para o detalhe com o fornecedor.
                    Prov prov = Prov.de(FONTE_DESPESAS, urlDasVerbas(ano, 1), arq);
                    for (LancamentoDaVerba l : lancamentosDaPagina(html)) {
                        // Página renovada em outro dia pode repetir lançamento: o id do detalhe decide.
                        if (!vistos.add(l.id())) {
                            continue;
                        }
                        String id = idPorChave.get(chaveDoNome(l.deputado()));
                        if (id == null) {
                            semPerfil.merge(l.deputado(), 1, Integer::sum);
                            continue;
                        }
                        porId.computeIfAbsent(id, k -> new ArrayList<>()).add(new Despesa(null, l.ano(), l.mes(), l.categoria(), null,
                                null, l.valor(), l.nota(), URL_VERBAS + "/" + l.id() + "/", prov));
                        lancamentos++;
                    }
                } catch (java.io.IOException e) {
                    System.err.println("alba: erro ao ler " + arquivoDasVerbas(ano, pagina) + ": " + e.getMessage());
                }
            }
        }
        for (List<Despesa> lista : porId.values()) {
            lista.sort(Comparator.comparing(Despesa::ano).thenComparing(Despesa::mes).reversed());
        }
        System.out.println("alba: " + lancamentos + " lançamentos da verba indenizatória em " + porId.size()
                + " deputados; nomes sem perfil em exercício: " + semPerfil.size() + " " + semPerfil.keySet());
        return porId;
    }

    /** Resposta da API em cache, com o arquivo que dá a proveniência. */
    record Resposta(JsonNode corpo, Coleta.Arquivo arquivo) {
    }

    /** Busca de uma consulta: na coleta, baixa ou usa o cache; na normalização, só lê o cache. Sem cópia, null. */
    interface Leitor {
        Resposta ler(String url, String caminho) throws Exception;
    }

    /**
     * Respostas válidas da API no ano, página a página. Como na ALES, a API responde 200 com {@code {"erro": true}} para a
     * página inteira quando um item tem campo nulo: essa página é refeita item a item, e só o item com defeito fica fora.
     * O número de páginas sai do total da primeira resposta válida.
     */
    static List<Resposta> respostasDoAno(int ano, Leitor leitor) throws Exception {
        List<Resposta> validas = new ArrayList<>();
        int total = -1;
        for (int pagina = 1; pagina <= (total < 0 ? 1 : (total + TAMANHO_DA_PAGINA - 1) / TAMANHO_DA_PAGINA); pagina++) {
            Resposta resposta = leitor.ler(urlDaPagina(ano, pagina, TAMANHO_DA_PAGINA), "proposicoes/" + ano + "-" + pagina + ".json");
            if (resposta == null) {
                continue;
            }
            if (valida(resposta)) {
                validas.add(resposta);
                total = total < 0 ? resposta.corpo().path("total").asInt(0) : total;
                continue;
            }
            int primeiro = (pagina - 1) * TAMANHO_DA_PAGINA + 1;
            for (int item = primeiro; item < primeiro + TAMANHO_DA_PAGINA && (total < 0 || item <= total); item++) {
                Resposta doItem = leitor.ler(urlDaPagina(ano, item, 1), "proposicoes/" + ano + "-item-" + item + ".json");
                if (doItem != null && valida(doItem)) {
                    validas.add(doItem);
                    total = total < 0 ? doItem.corpo().path("total").asInt(0) : total;
                }
            }
        }
        return validas;
    }

    static boolean valida(Resposta resposta) {
        return resposta.corpo().path("Data").isArray() && !resposta.corpo().path("erro").asBoolean(false);
    }

    static String urlDaPagina(int ano, int pagina, int quantidade) {
        return API_PROPOSICOES + "?pag=" + pagina + "&qtd=" + quantidade + "&ano=" + ano;
    }

    /**
     * Proposições de uma página da API, por id do deputado. O autor e os coautores vêm pelo nome civil
     * ({@code nomeRazao}), ligado ao nome civil do perfil. O gabinete não serve de vínculo: na ALES há proposição cujo autor
     * não é o titular do gabinete. Autoria de órgão (Poder Executivo, tribunais, Mesa) não tem perfil e fica fora.
     */
    static Map<String, List<Proposicao>> proposicoesDaPagina(JsonNode resposta, Map<String, String> idPorNomeCivil, Prov prov,
                                                             Map<String, Integer> gabinetesSemPerfil) {
        Map<String, List<Proposicao>> porId = new HashMap<>();
        for (JsonNode p : resposta.path("Data")) {
            String id = Json.texto(p, "id");
            String sigla = Json.texto(p, "sigla");
            Integer numero = Json.inteiro(p, "numero");
            Integer ano = Json.inteiro(p, "ano");
            if (id == null || sigla == null || numero == null || ano == null) {
                continue;
            }
            List<String> autores = new ArrayList<>();
            autores.add(Json.texto(p.path("AutorRequerenteDados"), "nomeRazao"));
            for (JsonNode coautor : p.path("CoAutores")) {
                autores.add(coautor.isObject() ? Json.texto(coautor, "nomeRazao") : coautor.isString() ? coautor.stringValue() : null);
            }
            String ementa = Json.texto(p, "assunto");
            String arquivo = Json.texto(p, "arquivo");
            Set<String> jaContados = new HashSet<>();
            for (int i = 0; i < autores.size(); i++) {
                String deputado = autores.get(i) == null ? null : idPorNomeCivil.get(chaveDoNome(semSituacao(autores.get(i))));
                if (deputado == null) {
                    String setor = i == 0 ? Json.texto(p.path("AutorRequerenteDados"), "setor") : null;
                    if (setor != null && setor.startsWith("GAB DEP")) {
                        gabinetesSemPerfil.merge(setor, 1, Integer::sum);
                    }
                    continue;
                }
                if (!jaContados.add(deputado)) {
                    continue;
                }
                porId.computeIfAbsent(deputado, k -> new ArrayList<>()).add(new Proposicao(id, Json.texto(p, "tipo"), numero, ano,
                        sigla + " " + numero + "/" + ano, ementa == null ? null : Guarda.semCpf(ementa.replaceAll("\\s+", " ")),
                        Json.texto(p, "situacao"), null, Texto.dataBr(Json.texto(p, "data")), i + 1, i == 0,
                        arquivo != null && arquivo.startsWith("https://") ? arquivo : null, prov));
            }
        }
        return porId;
    }

    /** "NOME (INATIVO)" vira "NOME": a fonte marca a situação do cadastro do autor entre parênteses, no fim do nome. */
    static String semSituacao(String nome) {
        return nome.replaceFirst("\\s*\\([^)]*\\)\\s*$", "");
    }

    /** Proposições de cada deputado em exercício, pelas páginas da API em cache. */
    static Map<String, List<Proposicao>> proposicoesPorDeputado(Contexto ctx, Map<String, String> idPorNomeCivil) {
        Map<String, List<Proposicao>> porId = new HashMap<>();
        Map<String, Integer> gabinetesSemPerfil = new java.util.TreeMap<>();
        int total = 0;
        Leitor doCache = (url, caminho) -> {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alba", caminho);
            if (arq == null || arq.bytes() == 0 || !url.equals(arq.url()) || !Files.exists(Coleta.caminho(ctx, arq))) {
                return null;
            }
            return new Resposta(Json.ler(Coleta.caminho(ctx, arq)), arq);
        };
        for (int ano = PRIMEIRO_ANO; ano <= ctx.hoje().getYear(); ano++) {
            // Página renovada em outro dia pode repetir item: o id decide.
            Set<String> vistos = new HashSet<>();
            List<Resposta> respostas;
            try {
                respostas = respostasDoAno(ano, doCache);
            } catch (Exception e) {
                throw new IllegalStateException("alba: falha ao ler proposições de " + ano, e);
            }
            for (Resposta r : respostas) {
                // O registro aponta para a primeira página do ano na API; o texto, para o PDF da proposição.
                Prov prov = Prov.de(FONTE_PROPOSICOES, urlDaPagina(ano, 1, TAMANHO_DA_PAGINA), r.arquivo());
                for (var e : proposicoesDaPagina(r.corpo(), idPorNomeCivil, prov, gabinetesSemPerfil).entrySet()) {
                    for (Proposicao p : e.getValue()) {
                        if (vistos.add(e.getKey() + "|" + p.idFonte())) {
                            porId.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(p);
                            total++;
                        }
                    }
                }
            }
        }
        System.out.println("alba: " + total + " autorias de proposições em " + porId.size() + " deputados; gabinetes sem perfil: "
                + gabinetesSemPerfil.size() + " " + gabinetesSemPerfil.keySet());
        return porId;
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alba: nenhum deputado identificado para normalização");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, "alba", "deputados/deputados-estaduais.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, SITE + "/deputados/deputados-estaduais", arqDeputados);

        // Mapeamento para resolução de nomes parlamentares e civis para o ID do deputado
        Map<String, String> idPorNomeNorm = new HashMap<>();
        Map<String, DetalhePerfil> detalhesPorId = new HashMap<>();

        for (DeputadoBruto dep : deputados) {
            String id = dep.id();
            idPorNomeNorm.put(Texto.normalizarNome(dep.nome()), id);

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, "alba", "perfil/" + id + ".html");
            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                    DetalhePerfil det = extrairDetalhePerfilDoHtml(htmlPerfil);
                    if (det != null) {
                        detalhesPorId.put(id, det);
                        if (det.nomeCivil() != null) {
                            idPorNomeNorm.put(Texto.normalizarNome(det.nomeCivil()), id);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("alba: erro ao ler perfil/" + id + ".html: " + e.getMessage());
                }
            }
        }

        // Extrai as comissões de todos os arquivos de comissão baixados
        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairComissoes(ctx, idPorNomeNorm);

        // A verba lista o deputado pelo nome parlamentar; a API de proposições, pelo nome civil.
        Map<String, String> idPorNomeParlamentar = new HashMap<>();
        Map<String, String> idPorNomeCivil = new HashMap<>();
        for (DeputadoBruto dep : deputados) {
            idPorNomeParlamentar.put(chaveDoNome(dep.nome()), dep.id());
            DetalhePerfil det = detalhesPorId.get(dep.id());
            if (det != null && det.nomeCivil() != null) {
                idPorNomeCivil.put(chaveDoNome(det.nomeCivil()), dep.id());
            }
        }
        Map<String, List<Despesa>> despesasPorDeputado = despesasPorDeputado(ctx, idPorNomeParlamentar);
        Map<String, List<Proposicao>> proposicoesPorDeputado = proposicoesPorDeputado(ctx, idPorNomeCivil);
        Presencas presencas = presencasPorDeputado(ctx, idPorNomeParlamentar);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();

        for (DeputadoBruto dep : deputados) {
            String id = dep.id();
            Acumulado ac = new Acumulado();
            acumulados.put(id, ac);

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, "alba", "perfil/" + id + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, SITE + "/deputados/deputado-estadual/" + id,
                    arqPerfil != null && arqPerfil.bytes() > 0 ? arqPerfil : arqDeputados);

            String nomeFormatado = formatarNome(dep.nome());
            String fotoUrl = dep.fotoUrl();

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALBA", SITE + "/deputados/deputado-estadual/" + id, provPerfil)
            );

            pessoas.add(new Pessoa("alba", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    dep.partido(), fotoUrl, "em exercício", true, links));

            // Mandato da 20ª legislatura
            ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, INICIO_LEG_20, FIM_LEG_20,
                    "em exercício", "Efetivo", LEGISLATURA_20, 2022, null, provPerfil));
            ac.fontes.computeIfAbsent("mandatos", k -> new ArrayList<>())
                    .add(FonteUsada.de(FONTE_DEPUTADOS, arqDeputados));

            // Comissões
            List<OrgaoOuFrente> comissoes = comissoesPorDeputado.getOrDefault(id, List.of());
            if (!comissoes.isEmpty()) {
                ac.orgaos.addAll(comissoes);
                Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, "alba", "comissoes/composicao.html");
                if (arqComissoes != null) {
                    ac.fontes.computeIfAbsent("orgaos_e_frentes", k -> new ArrayList<>())
                            .add(FonteUsada.de(FONTE_COMISSOES, arqComissoes));
                }
            }
        }

        // Ordenar pessoas por nome
        pessoas.sort(Comparator.comparing(Pessoa::nome));

        // Gravação
        Path saida = ctx.normalizado().resolve("alba");
        Path pastaBlocos = saida.resolve("blocos");
        Files.createDirectories(pastaBlocos);
        Json.gravar(saida.resolve("pessoas.json"), Json.SAIDA.valueToTree(pessoas));

        // Também grava metadados biográficos auxiliares (nome civil e nascimento) para resolução de entidades
        var bioNode = Json.SAIDA.createObjectNode();
        for (var e : detalhesPorId.entrySet()) {
            var depBio = Json.SAIDA.createObjectNode();
            if (e.getValue().nomeCivil() != null) {
                depBio.put("nome_civil", e.getValue().nomeCivil());
            }
            if (e.getValue().nascimento() != null) {
                depBio.put("nascimento", e.getValue().nascimento());
            }
            bioNode.set(e.getKey(), depBio);
        }
        Json.gravar(saida.resolve("biografias.json"), bioNode);

        for (var e : acumulados.entrySet()) {
            String id = e.getKey();
            Acumulado ac = e.getValue();
            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(ac.mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(ac.orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesasPorDeputado.getOrDefault(id, List.of())));
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoesPorDeputado.getOrDefault(id, List.of())));
            List<Evento> eventos = presencas.porId().getOrDefault(id, List.of());
            blocosNode.set("eventos", Json.SAIDA.valueToTree(eventos));
            if (!eventos.isEmpty()) {
                // Uma página de presença por sessão: o bloco cita a lista de sessões, com a captura mais recente.
                ac.fontes.put("eventos", List.of(new FonteUsada(FONTE_PRESENCA, URL_SESSOES, presencas.capturadaEm())));
            }
            // Despesas e proposições citam as fontes da proveniência dos próprios registros.
            var derivadas = Registros.fontesDosBlocos(blocosNode);
            for (String bloco : List.of("despesas", "proposicoes")) {
                if (derivadas.containsKey(bloco)) {
                    ac.fontes.put(bloco, derivadas.get(bloco));
                }
            }
            blocosNode.set("fontes", Json.SAIDA.valueToTree(ac.fontes));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        System.out.println("alba: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alba", "deputados/deputados-estaduais.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alba: erro ao ler deputados/deputados-estaduais.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoBruto> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = CARD_PATTERN.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            if (vistos.add(id)) {
                String foto = m.group(2).trim();
                String fotoUrl = foto.startsWith("/") ? SITE + foto : foto;
                String nome = Texto.semEntidades(m.group(3).trim());
                String partido = m.group(4).trim();
                lista.add(new DeputadoBruto(id, nome, partido, fotoUrl));
            }
        }
        return lista;
    }

    public static List<String> extrairIdsComissoes(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alba", "comissoes/composicao.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairIdsComissoesDoHtml(html);
        } catch (Exception e) {
            System.err.println("alba: erro ao ler comissoes/composicao.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<String> extrairIdsComissoesDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<String> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = COMISSAO_LINK_PATTERN.matcher(html);
        while (m.find()) {
            String cid = m.group(1).trim();
            if (vistos.add(cid)) {
                lista.add(cid);
            }
        }
        return lista;
    }

    public static DetalhePerfil extrairDetalhePerfilDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String nomeCivil = null;
        Matcher mNome = NOME_CIVIL_PATTERN.matcher(html);
        if (mNome.find()) {
            nomeCivil = Texto.semEntidades(mNome.group(1).trim());
        }

        String nascimento = null;
        Matcher mNasc = NASCIMENTO_PATTERN.matcher(html);
        if (mNasc.find()) {
            nascimento = Texto.dataBr(mNasc.group(1).trim());
        }

        return new DetalhePerfil(nomeCivil, nascimento);
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx, Map<String, String> idPorNomeNorm) {
        List<String> ids = extrairIdsComissoes(ctx);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        for (String cid : ids) {
            Coleta.Arquivo arqCom = Coleta.metadados(ctx, "alba", "comissoes/" + cid + ".html");
            if (arqCom == null || !Files.exists(Coleta.caminho(ctx, arqCom))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arqCom));
                Prov prov = Prov.de(FONTE_COMISSOES, SITE + "/comissoes/composicao/" + cid, arqCom);
                extrairMembrosDeComissaoDoHtml(html, idPorNomeNorm, prov, mapa);
            } catch (Exception e) {
                System.err.println("alba: erro ao ler comissoes/" + cid + ".html: " + e.getMessage());
            }
        }
        return mapa;
    }

    public static void extrairMembrosDeComissaoDoHtml(String html, Map<String, String> idPorNomeNorm,
                                                      Prov prov, Map<String, List<OrgaoOuFrente>> mapa) {
        if (html == null || html.isBlank()) {
            return;
        }

        String nomeComissao = "Comissão";
        Matcher mNomeCom = COMISSAO_TITULO_PATTERN.matcher(html);
        if (mNomeCom.find()) {
            nomeComissao = formatarNome(Texto.semEntidades(mNomeCom.group(1).trim()));
        }

        String presNomeNorm = null;
        Matcher mPres = PRESIDENTE_PATTERN.matcher(html);
        if (mPres.find()) {
            presNomeNorm = normalizarNomeMembro(mPres.group(1).trim());
        }

        String viceNomeNorm = null;
        Matcher mVice = VICE_PRESIDENTE_PATTERN.matcher(html);
        if (mVice.find()) {
            viceNomeNorm = normalizarNomeMembro(mVice.group(1).trim());
        }

        Matcher mLinha = LINHA_TABELA_PATTERN.matcher(html);
        while (mLinha.find()) {
            String colTitular = mLinha.group(1).trim();
            String colSuplente = mLinha.group(2).trim();

            if (!colTitular.isBlank()) {
                String nomeTitular = limparNomeMembro(colTitular);
                String norm = Texto.normalizarNome(nomeTitular);
                String depId = idPorNomeNorm.get(norm);
                if (depId != null) {
                    String papel = "Titular";
                    if (presNomeNorm != null && presNomeNorm.equals(norm)) {
                        papel = "Presidente";
                    } else if (viceNomeNorm != null && viceNomeNorm.equals(norm)) {
                        papel = "Vice-Presidente";
                    }
                    mapa.computeIfAbsent(depId, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", nomeComissao, null, papel, INICIO_LEG_20, null, prov));
                }
            }

            if (!colSuplente.isBlank()) {
                String nomeSuplente = limparNomeMembro(colSuplente);
                String norm = Texto.normalizarNome(nomeSuplente);
                String depId = idPorNomeNorm.get(norm);
                if (depId != null) {
                    mapa.computeIfAbsent(depId, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", nomeComissao, null, "Suplente", INICIO_LEG_20, null, prov));
                }
            }
        }
    }

    public static String limparNomeMembro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String limpo = Texto.semEntidades(texto.trim());
        Matcher m = NOME_MEMBRO_PATTERN.matcher(limpo);
        if (m.find()) {
            return m.group(1).trim();
        }
        return limpo;
    }

    public static String normalizarNomeMembro(String texto) {
        return Texto.normalizarNome(limparNomeMembro(texto));
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
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            if (i < partes.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static final class Acumulado {
        final List<Mandato> mandatos = new ArrayList<>();
        final List<OrgaoOuFrente> orgaos = new ArrayList<>();
        final Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
    }
}
