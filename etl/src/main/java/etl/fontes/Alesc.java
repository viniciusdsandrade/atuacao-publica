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
import etl.Registros.Evento;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Registros.Voto;
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
    public static final String FONTE_VOTACOES = "alesc-votacoes";
    public static final String FONTE_PRESENCA = "alesc-presenca";

    public static final String SITE = "https://www.alesc.sc.gov.br";
    public static final String URL_DEPUTADOS = "https://www.alesc.sc.gov.br/deputados/";
    public static final String URL_COMISSOES = "https://portalelegis.alesc.sc.gov.br/comissoes-permanentes";
    public static final String URL_DESPESAS = "https://transparencia.alesc.sc.gov.br/gabinetes-parlamentares/csv/";
    public static final String URL_ELEGIS = "https://portalelegis.alesc.sc.gov.br";
    public static final String URL_PROPOSICOES = "https://portalelegis.alesc.sc.gov.br/proposicoes/processo-legislativo";
    public static final String URL_SESSOES = "https://portalelegis.alesc.sc.gov.br/sessoes-plenarias";
    public static final String URL_EXTRATO = "https://portalelegis.alesc.sc.gov.br/extrato-votacao/";
    public static final String UF = "SC";

    /** Primeiras páginas da lista de sessões: a lista vem da sessão mais nova e desloca a cada sessão, e essas renovam todo dia. */
    static final int PAGINAS_RECENTES = 3;
    /** Cabeçalho que a própria página do e-Legis manda ao abrir o extrato e o modal de comissão. Sem ele, o extrato responde 405. */
    private static final Map<String, String> AJAX = Map.of("X-Requested-With", "XMLHttpRequest");
    private static final Pattern PAGINA_DA_LISTA = Pattern.compile("href=\"/sessoes-plenarias\\?page=([0-9]+)\"");
    private static final String MARCA_DA_SESSAO = "<div class=\"card card-alesc mb-4\">";
    private static final Pattern TITULO_DA_SESSAO = Pattern.compile("<h4 class=\"mb-1\">\\s*([^<]+?)\\s*</h4>");
    private static final Pattern DATA_DA_SESSAO = Pattern.compile("([0-9]{2}/[0-9]{2}/[0-9]{4}) ·");
    private static final Pattern SITUACAO_DA_SESSAO = Pattern.compile(
            "<span class=\"badge rounded-pill[^\"]*\">(?:<i[^>]*></i>)?\\s*([^<]+?)\\s*</span>");
    private static final Pattern ORDEM_DO_DIA = Pattern.compile(
            "<a href=\"/sessoes-plenarias/([A-Za-z0-9]+)/ordem-do-dia\" class=\"([^\"]*)\"");
    private static final Pattern PRESENCA_DA_SESSAO = Pattern.compile(
            "<a href=\"/sessoes-plenarias/[A-Za-z0-9]+/presenca\" class=\"([^\"]*)\"");
    private static final Pattern LINHA_DE_PRESENCA = Pattern.compile(
            "<tr>\\s*<td>\\s*([^<]+?)\\s*</td>\\s*<td>\\s*([^<]+?)\\s*</td>\\s*</tr>");
    /** Situação de presença da fonte e o valor publicado; outra situação fica fora e aparece na saída do normalize. */
    static final Map<String, String> PRESENCAS = Map.of("PRESENTE", "registrada", "AUSENCIA JUSTIFICADA", "ausencia_justificada");
    private static final String MARCA_DO_ITEM = "<div class=\"border-bottom mb-3\">";
    private static final Pattern PROPOSICAO_DO_ITEM = Pattern.compile("<a href=\"/proposicoes/[A-Za-z0-9]+\">([^<]+)</a>");
    private static final Pattern SELO_DO_ITEM = Pattern.compile("<span class=\"badge text-bg-([a-z-]+)\">([^<]+)</span>");
    private static final Pattern EMENTA_DO_ITEM = Pattern.compile("<p class=\"fst-italic text-secondary mb-1\">(.*?)</p>", Pattern.DOTALL);
    private static final Pattern EXTRATO_DO_ITEM = Pattern.compile("hx-get=\"/extrato-votacao/([A-Za-z0-9]+)\"");
    private static final Pattern INICIO_DA_SESSAO = Pattern.compile("<th>Início</th>\\s*<td>([0-9]{2}/[0-9]{2}/[0-9]{4})");
    private static final Pattern VOTO_DO_EXTRATO = Pattern.compile(
            "<div>\\s*([^<]+?)\\s*</div>\\s*<div><span class=\"badge[^\"]*\">\\s*([^<]+?)\\s*</span></div>");

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
            coleta.obterOuVazio(urlModal, "comissoes/" + com.id() + ".html", "text/html", Politica.SEMANAL, AJAX);
        }

        for (int ano = 2023; ano <= 2026; ano++) {
            coleta.obterOuVazio(URL_DESPESAS + ano, "despesas/" + ano + ".csv", "text/csv", Politica.porAno(ano, ctx.hoje()));
        }

        // Proposições da 20ª legislatura por iniciativa (e-Legis). A lista desloca a cada proposição nova, e todas as páginas
        // renovam no mesmo dia.
        for (DeputadoBruto d : deputados) {
            String inic = iniciativaParaSlug(d.slug());
            int p = 1;
            while (true) {
                String url = URL_PROPOSICOES + "?iniciativa=" + inic + "&page=" + p;
                String nomeArquivo = arquivoDeProposicoes(d.slug(), p);
                coleta.obterOuVazio(url, nomeArquivo, "text/html", Politica.SEMANAL_EM_LISTA);
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

        coletarVotacoes(ctx, coleta);
    }

    /**
     * Votações nominais da 20ª legislatura no e-Legis: a lista de sessões plenárias, a ordem do dia de cada sessão realizada
     * e o extrato de cada votação nominal. Fora as primeiras páginas da lista, que deslocam a cada sessão nova, cada página
     * só é baixada quando falta: a sessão fica pelo id, que não muda de uma página para outra.
     */
    private void coletarVotacoes(Contexto ctx, Coleta coleta) throws Exception {
        Coleta.Arquivo primeira = coleta.obterOpcional(URL_SESSOES, arquivoDaLista(1), "text/html", Politica.SEMPRE);
        if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
            return;
        }
        int paginas = paginasDaLista(Texto.lerArquivo(Coleta.caminho(ctx, primeira)));
        for (int p = 2; p <= paginas; p++) {
            coleta.obterOpcional(URL_SESSOES + "?page=" + p, arquivoDaLista(p), "text/html",
                    p <= PAGINAS_RECENTES ? Politica.SEMPRE : Politica.SE_AUSENTE);
        }
        Map<String, SessaoListada> realizadas = new java.util.LinkedHashMap<>();
        for (SessaoListada s : sessoesEmCache(ctx)) {
            if (s.realizada()) {
                realizadas.put(s.id(), s);
            }
        }
        // Em paralelo, com o limite de conexões e o intervalo por host do cliente HTTP.
        for (String falha : Camara.paraCadaDeputado(new ArrayList<>(realizadas.keySet()), id -> coletarSessao(ctx, coleta, realizadas.get(id)))) {
            System.err.println("alesc: falha na sessão " + falha);
        }
    }

    private static void coletarSessao(Contexto ctx, Coleta coleta, SessaoListada sessao) throws Exception {
        Politica politica = Politica.porAnoSemanal(sessao.data().getYear(), ctx.hoje());
        Coleta.Arquivo ordem = coleta.obterOpcional(urlDaOrdemDoDia(sessao.id()), arquivoDaOrdemDoDia(sessao.id()), "text/html", politica);
        if (ordem == null || !Files.exists(Coleta.caminho(ctx, ordem))) {
            return;
        }
        for (VotacaoNominal v : votacoesNominais(Texto.lerArquivo(Coleta.caminho(ctx, ordem)))) {
            coleta.obterOpcional(URL_EXTRATO + v.idExtrato(), arquivoDoExtrato(v.idExtrato()), "text/html", politica, AJAX);
        }
        if (sessao.comPresenca()) {
            coleta.obterOpcional(urlDaPresenca(sessao.id()), arquivoDaPresenca(sessao.id()), "text/html", politica);
        }
    }

    /** Sessão da lista: id, título, data, situação e se a ordem do dia e a presença estão abertas. */
    record SessaoListada(String id, String titulo, LocalDate data, String situacao, boolean comOrdemDoDia, boolean comPresenca) {

        boolean realizada() {
            return comOrdemDoDia && data != null && "Realizada".equalsIgnoreCase(situacao);
        }
    }

    /** Votação nominal de um item da ordem do dia: o extrato, a proposição, o resultado e a ementa. */
    record VotacaoNominal(String idExtrato, String proposicao, String resultado, String ementa) {
    }

    /** Voto de um deputado no extrato, como a página o dá. */
    record VotoNoExtrato(String nome, String voto) {
    }

    static String urlDaOrdemDoDia(String idSessao) {
        return URL_SESSOES + "/" + idSessao + "/ordem-do-dia";
    }

    static String urlDaPresenca(String idSessao) {
        return URL_SESSOES + "/" + idSessao + "/presenca";
    }

    private static String arquivoDaPresenca(String idSessao) {
        return "sessoes/presenca/" + nomeDeArquivo(idSessao) + ".html";
    }

    private static String arquivoDaLista(int pagina) {
        return "sessoes/pagina-" + pagina + ".html";
    }

    private static String arquivoDaOrdemDoDia(String idSessao) {
        return "sessoes/ordem-do-dia/" + nomeDeArquivo(idSessao) + ".html";
    }

    private static String arquivoDoExtrato(String idExtrato) {
        return "sessoes/extratos/" + nomeDeArquivo(idExtrato) + ".html";
    }

    /**
     * Id do e-Legis como nome de arquivo. O id diferencia maiúsculas ("5bnZ5"), e o disco do mantenedor pode não
     * diferenciar: cada maiúscula vira "_" e a minúscula, para dois ids nunca caírem no mesmo arquivo.
     */
    static String nomeDeArquivo(String id) {
        StringBuilder nome = new StringBuilder();
        for (char c : id.toCharArray()) {
            if (Character.isUpperCase(c)) {
                nome.append('_').append(Character.toLowerCase(c));
            } else {
                nome.append(c);
            }
        }
        return nome.toString();
    }

    /** Número de páginas da lista de sessões, pelo maior número de página dos links. */
    static int paginasDaLista(String html) {
        int maior = 1;
        Matcher m = PAGINA_DA_LISTA.matcher(html);
        while (m.find()) {
            maior = Math.max(maior, Integer.parseInt(m.group(1)));
        }
        return maior;
    }

    /** Sessões de uma página da lista, na ordem da página. */
    static List<SessaoListada> sessoesDaPagina(String html) {
        List<SessaoListada> sessoes = new ArrayList<>();
        String[] cartoes = html.split(Pattern.quote(MARCA_DA_SESSAO));
        for (int i = 1; i < cartoes.length; i++) {
            String cartao = cartoes[i];
            Matcher ordem = ORDEM_DO_DIA.matcher(cartao);
            if (!ordem.find()) {
                continue;
            }
            Matcher titulo = TITULO_DA_SESSAO.matcher(cartao);
            Matcher data = DATA_DA_SESSAO.matcher(cartao);
            Matcher situacao = SITUACAO_DA_SESSAO.matcher(cartao);
            Matcher presenca = PRESENCA_DA_SESSAO.matcher(cartao);
            sessoes.add(new SessaoListada(ordem.group(1), titulo.find() ? Texto.semEntidades(titulo.group(1)) : null,
                    data.find() ? LocalDate.parse(data.group(1), DATA_BR) : null,
                    situacao.find() ? Texto.semEntidades(situacao.group(1)) : null, !ordem.group(2).contains("disabled"),
                    presenca.find() && !presenca.group(1).contains("disabled")));
        }
        return sessoes;
    }

    /**
     * Sessões de todas as páginas em cache, sem repetição. O total de páginas vem da primeira, e página que falhou não
     * interrompe a leitura das seguintes. A página mais nova vale quando a sessão aparece em duas.
     */
    static List<SessaoListada> sessoesEmCache(Contexto ctx) {
        Map<String, SessaoListada> porId = new java.util.LinkedHashMap<>();
        int paginas = 1;
        for (int p = 1; p <= paginas; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", arquivoDaLista(p));
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                if (p == 1) {
                    paginas = paginasDaLista(html);
                }
                for (SessaoListada s : sessoesDaPagina(html)) {
                    porId.putIfAbsent(s.id(), s);
                }
            } catch (java.io.IOException e) {
                System.err.println("alesc: erro ao ler a página " + p + " das sessões: " + e.getMessage());
            }
        }
        return new ArrayList<>(porId.values());
    }

    /** Data de início da sessão, no cartão da ordem do dia. */
    static LocalDate dataDaOrdemDoDia(String html) {
        Matcher m = INICIO_DA_SESSAO.matcher(html);
        return m.find() ? LocalDate.parse(m.group(1), DATA_BR) : null;
    }

    /** Itens da ordem do dia votados por votação nominal, com o extrato. Votação simbólica não tem voto individual. */
    static List<VotacaoNominal> votacoesNominais(String html) {
        List<VotacaoNominal> votacoes = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        String[] itens = html.split(Pattern.quote(MARCA_DO_ITEM));
        for (int i = 1; i < itens.length; i++) {
            String item = itens[i];
            Matcher extrato = EXTRATO_DO_ITEM.matcher(item);
            if (!extrato.find() || !vistos.add(extrato.group(1))) {
                continue;
            }
            String resultado = null;
            boolean nominal = false;
            Matcher selo = SELO_DO_ITEM.matcher(item);
            while (selo.find()) {
                String texto = Texto.semEntidades(selo.group(2)).strip();
                if (selo.group(1).equals("light")) {
                    nominal |= texto.equalsIgnoreCase("Votação nominal");
                } else if (resultado == null) {
                    resultado = texto;
                }
            }
            if (!nominal) {
                continue;
            }
            Matcher proposicao = PROPOSICAO_DO_ITEM.matcher(item);
            Matcher ementa = EMENTA_DO_ITEM.matcher(item);
            votacoes.add(new VotacaoNominal(extrato.group(1), proposicao.find() ? identificacao(Texto.semEntidades(proposicao.group(1))) : null,
                    resultado, ementa.find() ? Guarda.semCpf(Texto.semEntidades(ementa.group(1).strip())) : null));
        }
        return votacoes;
    }

    /** "PLC/0007/2026" vira "PLC 7/2026"; o que não segue o padrão fica como veio. */
    static String identificacao(String texto) {
        Matcher m = IDENTIFICACAO_PATTERN.matcher(texto.strip());
        return m.matches() ? m.group(1).toUpperCase(Locale.ROOT) + " " + Integer.parseInt(m.group(2)) + "/" + m.group(3) : texto.strip();
    }

    /** Votos do extrato de uma votação: o nome e o voto de quem votou. */
    static List<VotoNoExtrato> votosDoExtrato(String html) {
        List<VotoNoExtrato> votos = new ArrayList<>();
        Matcher m = VOTO_DO_EXTRATO.matcher(html);
        while (m.find()) {
            votos.add(new VotoNoExtrato(Texto.semEntidades(m.group(1)), Texto.semEntidades(m.group(2))));
        }
        return votos;
    }

    /** Linhas da página de presença: o nome e a situação de cada deputado, como a fonte dá. */
    static List<String[]> linhasDePresenca(String html) {
        List<String[]> linhas = new ArrayList<>();
        Matcher m = LINHA_DE_PRESENCA.matcher(html);
        while (m.find()) {
            linhas.add(new String[] {Texto.semEntidades(m.group(1)), Texto.semEntidades(m.group(2))});
        }
        return linhas;
    }

    /** "100ª Sessão Ordinária" vira "Sessão Ordinária". */
    static String tipoDaSessao(String titulo) {
        return titulo == null ? null : titulo.replaceFirst("^[0-9]+[ªº]?\\s*", "").strip();
    }

    /** Presença em sessão plenária de cada deputado em exercício, ligada pelo nome. Cada sessão vira um evento. */
    record Presencas(Map<String, List<Evento>> porSlug, java.time.Instant capturadaEm) {
    }

    static Presencas extrairPresencas(Contexto ctx, Map<String, String> slugPorNomeNorm) {
        Map<String, List<Evento>> porSlug = new HashMap<>();
        Map<String, Integer> semPerfil = new java.util.TreeMap<>();
        Map<String, Integer> outrasSituacoes = new java.util.TreeMap<>();
        java.time.Instant capturadaEm = null;
        int sessoes = 0;
        for (SessaoListada sessao : sessoesEmCache(ctx)) {
            if (!sessao.realizada() || !sessao.comPresenca()) {
                continue;
            }
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alesc", arquivoDaPresenca(sessao.id()));
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                List<String[]> linhas = linhasDePresenca(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                if (linhas.isEmpty()) {
                    continue;
                }
                sessoes++;
                capturadaEm = capturadaEm == null || arq.capturadoEm().isAfter(capturadaEm) ? arq.capturadoEm() : capturadaEm;
                Prov prov = Prov.de(FONTE_PRESENCA, urlDaPresenca(sessao.id()), arq);
                for (String[] linha : linhas) {
                    String presenca = PRESENCAS.get(Texto.normalizarNome(linha[1]));
                    if (presenca == null) {
                        outrasSituacoes.merge(linha[1], 1, Integer::sum);
                        continue;
                    }
                    String slug = resolverSlugPorNome(linha[0], slugPorNomeNorm);
                    if (slug == null) {
                        semPerfil.merge(linha[0], 1, Integer::sum);
                        continue;
                    }
                    porSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(new Evento("sessao-" + sessao.id(), sessao.data().toString(),
                            tipoDaSessao(sessao.titulo()), sessao.titulo(), "Plenário", sessao.situacao(), presenca, prov));
                }
            } catch (java.io.IOException e) {
                System.err.println("alesc: erro ao ler a presença da sessão " + sessao.id() + ": " + e.getMessage());
            }
        }
        for (List<Evento> eventos : porSlug.values()) {
            eventos.sort(Comparator.comparing(Evento::data).reversed());
        }
        int total = porSlug.values().stream().mapToInt(List::size).sum();
        System.out.println("alesc: presença em " + sessoes + " sessões, " + total + " registros em " + porSlug.size()
                + " deputados; outras situações: " + outrasSituacoes + "; nomes sem perfil em exercício: " + semPerfil.size()
                + " " + semPerfil.keySet());
        return new Presencas(porSlug, capturadaEm);
    }

    /** Votos de cada deputado em exercício, pelas ordens do dia e extratos em cache, ligados pelo nome. */
    static Map<String, List<Voto>> extrairVotos(Contexto ctx, Map<String, String> slugPorNomeNorm) {
        Map<String, List<Voto>> porSlug = new HashMap<>();
        Map<String, Integer> semPerfil = new java.util.TreeMap<>();
        int votacoes = 0;
        for (SessaoListada sessao : sessoesEmCache(ctx)) {
            if (!sessao.realizada()) {
                continue;
            }
            Coleta.Arquivo ordem = Coleta.metadados(ctx, "alesc", arquivoDaOrdemDoDia(sessao.id()));
            if (ordem == null || ordem.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, ordem))) {
                continue;
            }
            try {
                String htmlOrdem = Texto.lerArquivo(Coleta.caminho(ctx, ordem));
                LocalDate data = java.util.Objects.requireNonNullElse(dataDaOrdemDoDia(htmlOrdem), sessao.data());
                if (data.isBefore(INICIO_LEG_20)) {
                    continue;
                }
                for (VotacaoNominal v : votacoesNominais(htmlOrdem)) {
                    Coleta.Arquivo extrato = Coleta.metadados(ctx, "alesc", arquivoDoExtrato(v.idExtrato()));
                    if (extrato == null || extrato.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, extrato))) {
                        continue;
                    }
                    List<VotoNoExtrato> votos = votosDoExtrato(Texto.lerArquivo(Coleta.caminho(ctx, extrato)));
                    if (votos.isEmpty()) {
                        continue;
                    }
                    votacoes++;
                    // O extrato só abre pelo botão da ordem do dia: o registro aponta para a página que o leitor consegue abrir.
                    Prov prov = Prov.de(FONTE_VOTACOES, urlDaOrdemDoDia(sessao.id()), extrato);
                    Set<String> jaContados = new HashSet<>();
                    for (VotoNoExtrato voto : votos) {
                        String slug = resolverSlugPorNome(voto.nome(), slugPorNomeNorm);
                        if (slug == null) {
                            semPerfil.merge(voto.nome(), 1, Integer::sum);
                            continue;
                        }
                        if (jaContados.add(slug)) {
                            porSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(new Voto(v.idExtrato(), data.toString(), "PLEN",
                                    v.proposicao(), v.ementa(), voto.voto(), null, null, null, null, prov));
                        }
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("alesc: erro ao ler a ordem do dia da sessão " + sessao.id() + ": " + e.getMessage());
            }
        }
        int total = porSlug.values().stream().mapToInt(List::size).sum();
        System.out.println("alesc: " + votacoes + " votações nominais, " + total + " votos em " + porSlug.size()
                + " deputados; nomes sem perfil em exercício: " + semPerfil.size() + " " + semPerfil.keySet());
        return porSlug;
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
        Map<String, List<Voto>> votosPorSlug = extrairVotos(ctx, slugPorNomeNorm);
        Presencas presencas = extrairPresencas(ctx, slugPorNomeNorm);

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
            blocosNode.set("votos", Json.SAIDA.valueToTree(votosPorSlug.getOrDefault(slug, List.of())));
            List<Evento> eventos = presencas.porSlug().getOrDefault(slug, List.of());
            blocosNode.set("eventos", Json.SAIDA.valueToTree(eventos));
            var fontes = Registros.fontesDosBlocos(blocosNode);
            if (!eventos.isEmpty()) {
                // Uma página de presença por sessão: o bloco cita a lista de sessões, com a captura mais recente.
                fontes.put("eventos", List.of(new FonteUsada(FONTE_PRESENCA, URL_SESSOES, presencas.capturadaEm())));
            }
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
