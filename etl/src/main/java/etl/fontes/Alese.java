package etl.fontes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Http;
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
 * Assembleia Legislativa do Estado de Sergipe (ALESE, ADR-0047).
 * Coleta os 24 deputados estaduais em exercício na 20ª Legislatura (2023 a 2027)
 * a partir do portal institucional oficial, dados de mandato, composição da Mesa Diretora,
 * filiação partidária, biografias e fotografias oficiais.
 */
public final class Alese implements Fonte {

    public static final String FONTE_DEPUTADOS = "alese-deputados";
    public static final String FONTE_PARLAMENTARES = "alese-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alese-mesa-diretora";
    public static final String FONTE_PROPOSICOES = "alese-proposicoes";
    public static final String FONTE_PORTAL = "alese-portal";

    public static final String SITE = "https://al.se.leg.br";
    public static final String URL_DEPUTADOS = "https://al.se.leg.br/deputados/";
    public static final String URL_MESA = "https://al.se.leg.br/mesa-diretora/";
    public static final String URL_PARLAMENTAR = "https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id=";
    public static final String URL_PRODUCAO = "https://aleselegis.al.se.leg.br/spl/consulta-producao.aspx";
    public static final String URL_PROCESSO = "https://aleselegis.al.se.leg.br/spl/processo.aspx?id=";
    public static final String UF = "SE";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int NUMERO_LEGISLATURA = 20;
    public static final int PRIMEIRO_ANO_PROPOSICOES = 2022;
    private static final int LIMITE_PAGINAS = 60;
    private static final int CONCORRENCIA_PRODUCAO = 4;

    private static final String HTML = "text/html";

    public record DeputadoCard(int id, String nome, String partido, String fotoUrl, String link) {
    }

    public record LinkProducao(int tipoId, int autorId, String tipoNome, int contagem, String url) {
    }

    public record MesaMembro(String cargo, String nome) {
    }

    private static final Pattern PATTERN_CARD = Pattern.compile(
            "<article[^>]*class=[\"'][^\"']*qodef-portfolio-item[^\"']*[\"'][^>]*>.*?" +
                    "<img[^>]+src=[\"']([^\"']+)[\"'][^>]*class=[\"'][^\"']*wp-post-image[^\"']*[\"'].*?" +
                    "<h5[^>]*class=[\"']qodef-item-title[\"'][^>]*>\\s*" +
                    "<a[^>]+href=[\"']https?://aleselegis\\.al\\.se\\.leg\\.br/spl/parlamentar\\.aspx\\?id=([0-9]+)[\"'][^>]*>\\s*([^<]+?)\\s*</a>.*?" +
                    "<div[^>]*class=[\"']qodef-ptf-category-holder[\"'][^>]*>\\s*<span>([^<]+)</span>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_NOME_CIVIL = Pattern.compile(
            "Nome civil:</span>\\s*<span[^>]*>([^<]+)</span>",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_BIO = Pattern.compile(
            "id=[\"']texto_biografia[\"'][^>]*>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MESA = Pattern.compile(
            "<div[^>]*align=[\"']left[\"']>\\s*([^<:]+):\\s*</div>.*?</td>\\s*<td><b>([^<]+)</b>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "alese";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alese: coleta iniciada");

        // 1. Lista de deputados e Mesa Diretora
        Coleta.Arquivo arqDeputados = coleta.obter(URL_DEPUTADOS, "deputados.html", HTML, Politica.SEMANAL);
        coleta.obterFormularioOpcional(URL_MESA, Map.of("cmbNumeroLegislatura", "20"), "mesa_diretora.html", HTML, Politica.SEMANAL);

        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            return;
        }

        String htmlDeputados = Texto.lerArquivo(Coleta.caminho(ctx, arqDeputados));
        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);

        Map<String, LinkProducao> linksProducao = new LinkedHashMap<>();
        for (DeputadoCard dep : deputados) {
            String urlParl = URL_PARLAMENTAR + dep.id();
            Coleta.Arquivo arqParl = coleta.obterOuVazio(urlParl, "parlamentares/" + dep.id() + ".html", HTML, Politica.SEMANAL);
            if (arqParl != null && arqParl.bytes() > 0 && Files.exists(Coleta.caminho(ctx, arqParl))) {
                String htmlParl = Texto.lerArquivo(Coleta.caminho(ctx, arqParl));
                for (LinkProducao link : parseLinksProducao(htmlParl)) {
                    linksProducao.putIfAbsent(link.autorId() + ":" + link.tipoId(), link);
                }
            }
        }
        coletarProducoes(ctx, coleta, List.copyOf(linksProducao.values()));
    }

    private void coletarProducoes(Contexto ctx, Coleta coleta, List<LinkProducao> links)
            throws IOException, InterruptedException {
        if (links.isEmpty()) {
            return;
        }
        Semaphore vagas = new Semaphore(CONCORRENCIA_PRODUCAO);
        List<Future<?>> tarefas = new ArrayList<>(links.size());
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (LinkProducao link : links) {
                tarefas.add(exec.submit(() -> {
                    vagas.acquire();
                    try {
                        coletarLinkProducao(ctx, coleta, link);
                        return null;
                    } finally {
                        vagas.release();
                    }
                }));
            }
            for (Future<?> tarefa : tarefas) {
                try {
                    tarefa.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable causa = e.getCause();
                    if (causa instanceof Http.PrazoEsgotado) {
                        continue;
                    }
                    if (causa instanceof IOException io) {
                        throw io;
                    }
                    if (causa instanceof InterruptedException ie) {
                        throw ie;
                    }
                    throw new RuntimeException("alese: erro ao coletar produção legislativa", causa);
                }
            }
        }
    }

    private void coletarLinkProducao(Contexto ctx, Coleta coleta, LinkProducao link)
            throws IOException, InterruptedException {
        String pasta = "producao/" + link.autorId() + "_t" + link.tipoId();
        String caminho1 = pasta + "/p001.html";
        if (link.contagem() <= 10) {
            coleta.obterOpcional(link.url(), caminho1, HTML, Politica.SEMANAL_EM_LISTA);
            return;
        }

        Coleta.Arquivo arq = obterPaginaInicialProducao(ctx, coleta, link.url(), caminho1);
        int pagina = 1;
        while (arq != null && Files.exists(Coleta.caminho(ctx, arq)) && pagina < LIMITE_PAGINAS) {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            if (!temProximaPagina(html) || atingiuAnoAnterior(html, PRIMEIRO_ANO_PROPOSICOES)) {
                break;
            }
            pagina++;
            String caminhoProx = pasta + String.format("/p%03d.html", pagina);
            arq = obterProximaPaginaProducao(ctx, coleta, link.url(), caminhoProx, html);
        }
    }

    private Coleta.Arquivo obterPaginaInicialProducao(Contexto ctx, Coleta coleta, String url, String caminho)
            throws IOException, InterruptedException {
        if (coleta.emCacheValido(url, caminho, Politica.SEMANAL_EM_LISTA) || coleta.orcamentoExaurido()) {
            return coleta.obterFormularioOpcional(url, Map.of(), caminho, HTML, Politica.SEMANAL_EM_LISTA);
        }
        Http.Resposta resp = ctx.http().get(url, HTML);
        if (!resp.ok()) {
            coleta.falhaParcial("producao", url + ": HTTP " + resp.status());
            return null;
        }
        Map<String, String> campos = camposFormulario(new String(resp.corpo(), StandardCharsets.UTF_8),
                "ctl00$ContentPlaceHolder1$ddl_ItensExibidos");
        if (campos == null) {
            coleta.falhaParcial("producao", "sem formulário em " + url);
            return null;
        }
        return coleta.obterFormularioOpcional(url, campos, caminho, HTML, Politica.SEMANAL_EM_LISTA);
    }

    private Coleta.Arquivo obterProximaPaginaProducao(Contexto ctx, Coleta coleta, String url, String caminho,
            String htmlAnterior) throws IOException, InterruptedException {
        if (coleta.emCacheValido(url, caminho, Politica.SEMANAL_EM_LISTA) || coleta.orcamentoExaurido()) {
            return coleta.obterFormularioOpcional(url, Map.of(), caminho, HTML, Politica.SEMANAL_EM_LISTA);
        }
        Map<String, String> campos = camposFormulario(htmlAnterior, "ctl00$ContentPlaceHolder1$lbNext");
        if (campos == null) {
            coleta.falhaParcial("producao", "sem viewstate na paginação de " + url);
            return null;
        }
        return coleta.obterFormularioOpcional(url, campos, caminho, HTML, Politica.SEMANAL_EM_LISTA);
    }

    private List<Proposicao> coletarDadosDaFicha(Contexto ctx, DeputadoCard dep, List<OrgaoOuFrente> orgaos) {
        Coleta.Arquivo arqParl = Coleta.metadados(ctx, nome(), "parlamentares/" + dep.id() + ".html");
        if (arqParl == null || arqParl.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqParl))) {
            return List.of();
        }
        String htmlParl;
        try {
            htmlParl = Texto.lerArquivo(Coleta.caminho(ctx, arqParl));
        } catch (Exception e) {
            System.err.println("alese: aviso ao ler parlamentares/" + dep.id() + ".html: " + e.getMessage());
            return List.of();
        }
        String urlParl = URL_PARLAMENTAR + dep.id();
        Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlParl, arqParl);
        orgaos.addAll(parseComissoes(htmlParl, provParl));

        Map<String, Proposicao> porId = new LinkedHashMap<>();
        for (LinkProducao link : parseLinksProducao(htmlParl)) {
            String pasta = "producao/" + link.autorId() + "_t" + link.tipoId();
            for (int p = 1; p <= LIMITE_PAGINAS; p++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), pasta + String.format("/p%03d.html", p));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                String html;
                try {
                    html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                } catch (Exception e) {
                    System.err.println("alese: aviso ao ler " + pasta + "/p" + p + ".html: " + e.getMessage());
                    break;
                }
                Prov provProd = Prov.de(FONTE_PROPOSICOES, arq.url(), arq);
                for (Proposicao prop : parseProposicoesHtml(html, provProd)) {
                    Integer anoEfetivo = anoDaProposicao(prop);
                    if (anoEfetivo != null && anoEfetivo >= PRIMEIRO_ANO_PROPOSICOES) {
                        porId.putIfAbsent(prop.idFonte(), prop);
                    }
                }
                if (!temProximaPagina(html) || atingiuAnoAnterior(html, PRIMEIRO_ANO_PROPOSICOES)) {
                    break;
                }
            }
        }
        List<Proposicao> lista = new ArrayList<>(porId.values());
        lista.sort(Comparator.comparing(Proposicao::dataApresentacao, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Proposicao::ano, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Proposicao::numero, Comparator.nullsLast(Comparator.reverseOrder())));
        return lista;
    }

    private static final Pattern PATTERN_LINK_PRODUCAO = Pattern.compile(
            "href=[\"'](?:https?://aleselegis\\.al\\.se\\.leg\\.br/spl/)?(consulta-producao\\.aspx\\?tipo=(\\d+)&(?:amp;)?autor=(\\d+))[\"'][^>]*>(.*?)</a>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_COMISSAO = Pattern.compile(
            "<h3[^>]*>\\s*Cargo:\\s*([^<(]+?)\\s*\\((\\d{2}/\\d{2}/\\d{4})\\s+a\\s+(\\d{2}/\\d{2}/\\d{4})\\)\\s*</h3>"
                    + "(?:(?!kt-widget1__item).)*?<a[^>]*href=[\"']comissoes\\.aspx[^\"']*[\"'][^>]*>(.*?)</a>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_NOME_INPUT = Pattern.compile("\\bname=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_VALOR_INPUT = Pattern.compile("\\bvalue=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_TITULO_PROP = Pattern.compile("^(.*?)(?:\\s+n\\.?[°ºo]\\.?\\s*|\\s+)(\\d+)/(\\d{4})$",
            Pattern.CASE_INSENSITIVE);

    public static List<LinkProducao> parseLinksProducao(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<LinkProducao> links = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_LINK_PRODUCAO.matcher(html);
        while (m.find()) {
            int tipoId = Integer.parseInt(m.group(2));
            int autorId = Integer.parseInt(m.group(3));
            String bruto = Texto.semEntidades(m.group(4).replaceAll("<[^>]+>", " "));
            if (bruto == null) {
                continue;
            }
            Matcher num = Pattern.compile("(\\d+)\\s*$").matcher(bruto);
            if (!num.find()) {
                continue;
            }
            int contagem = Integer.parseInt(num.group(1));
            if (contagem <= 0) {
                continue;
            }
            String tipoNome = bruto.substring(0, num.start()).strip();
            String url = URL_PRODUCAO + "?tipo=" + tipoId + "&autor=" + autorId;
            if (vistos.add(autorId + ":" + tipoId)) {
                links.add(new LinkProducao(tipoId, autorId, tipoNome, contagem, url));
            }
        }
        return links;
    }

    public static List<OrgaoOuFrente> parseComissoes(String html, Prov prov) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<OrgaoOuFrente> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_COMISSAO.matcher(html);
        while (m.find()) {
            String papel = Texto.semEntidades(m.group(1));
            String iniIso = Texto.dataBr(m.group(2));
            String fimIso = Texto.dataBr(m.group(3));
            String nomeRaw = Texto.semEntidades(m.group(4).replaceAll("<[^>]+>", " "));
            if (papel == null || iniIso == null || fimIso == null || nomeRaw == null) {
                continue;
            }
            LocalDate inicio = LocalDate.parse(iniIso);
            LocalDate fim = LocalDate.parse(fimIso);
            if (fim.isBefore(INICIO_LEG_20) || inicio.isAfter(FIM_LEG_20)) {
                continue;
            }
            String norm = Texto.normalizarNome(nomeRaw);
            String tipo = norm.startsWith("CONSELHO") ? "orgao" : norm.startsWith("FRENTE") ? "frente" : "comissao";
            if (vistos.add(tipo + "|" + nomeRaw + "|" + papel + "|" + inicio + "|" + fim)) {
                lista.add(new OrgaoOuFrente(tipo, nomeRaw, null, papel, inicio, fim, prov));
            }
        }
        return lista;
    }

    public static List<Proposicao> parseProposicoesHtml(String html, Prov prov) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<Proposicao> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int cursor = 0;
        while (true) {
            int pos = acharInicioCard(html, cursor);
            if (pos < 0) {
                break;
            }
            int fim = html.length();
            int prox = acharInicioCard(html, pos + 20);
            if (prox > 0) {
                fim = prox;
            }
            int paging = html.indexOf("rptPaging", pos);
            if (paging > pos && paging < fim) {
                fim = paging;
            }
            String bloco = html.substring(pos, fim);
            cursor = pos + 20;

            Matcher mId = Pattern.compile("processo\\.aspx\\?id=(\\d+)", Pattern.CASE_INSENSITIVE).matcher(bloco);
            if (!mId.find()) {
                continue;
            }
            String id = mId.group(1);
            if (!vistos.add(id)) {
                continue;
            }
            String tituloRaw = extrairEntreTags(bloco, "kt-widget5__title");
            if (tituloRaw == null) {
                continue;
            }
            String tipo = null;
            Integer numero = null;
            Integer ano = null;
            String identificacao = tituloRaw;
            Matcher mt = PATTERN_TITULO_PROP.matcher(tituloRaw);
            if (mt.matches()) {
                tipo = Texto.vazioParaNulo(mt.group(1));
                numero = Integer.valueOf(mt.group(2));
                ano = Integer.valueOf(mt.group(3));
                identificacao = (tipo == null ? "" : tipo + " ") + numero + "/" + ano;
            }
            String descRaw = extrairEntreTags(bloco, "kt-widget5__desc");
            String ementa = descRaw == null ? null : Guarda.sanitizar(descRaw);
            String dataRaw = extrairRotuloInfo(bloco, "Data:");
            String data = Texto.dataBr(dataRaw);
            if (ano == null && data != null) {
                ano = Integer.valueOf(data.substring(0, 4));
            }
            String situacao = extrairRotuloInfo(bloco, "Situa[çc][ãa]o:");
            lista.add(new Proposicao(id, tipo, numero, ano, identificacao, ementa, situacao, null, data, null, null,
                    URL_PROCESSO + id, prov));
        }
        return lista;
    }

    static Map<String, String> camposFormulario(String html, String eventTarget) {
        String viewState = extrairValorInput(html, "__VIEWSTATE");
        String generator = extrairValorInput(html, "__VIEWSTATEGENERATOR");
        String validation = extrairValorInput(html, "__EVENTVALIDATION");
        if (viewState == null || generator == null || validation == null) {
            return null;
        }
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("__EVENTTARGET", eventTarget);
        campos.put("__EVENTARGUMENT", "");
        campos.put("__VIEWSTATE", viewState);
        campos.put("__VIEWSTATEGENERATOR", generator);
        campos.put("__EVENTVALIDATION", validation);
        campos.put("ctl00$ContentPlaceHolder1$ddl_ItensExibidos", "100");
        return campos;
    }

    static boolean temProximaPagina(String html) {
        if (html == null) {
            return false;
        }
        Matcher m = Pattern.compile("<a\\b[^>]*id=[\"']ContentPlaceHolder1_lbNext[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (!m.find()) {
            return false;
        }
        String tag = m.group();
        return tag.contains("__doPostBack") && !tag.contains("aspNetDisabled");
    }

    static boolean atingiuAnoAnterior(String html, int primeiroAno) {
        List<Proposicao> itens = parseProposicoesHtml(html, new Prov(FONTE_PROPOSICOES, URL_PRODUCAO, java.time.Instant.EPOCH));
        if (itens.isEmpty()) {
            return true;
        }
        for (int i = itens.size() - 1; i >= 0; i--) {
            Integer ano = anoDaProposicao(itens.get(i));
            if (ano != null) {
                return ano < primeiroAno;
            }
        }
        return false;
    }

    private static Integer anoDaProposicao(Proposicao p) {
        if (p.dataApresentacao() != null && p.dataApresentacao().length() >= 4) {
            try {
                return Integer.valueOf(p.dataApresentacao().substring(0, 4));
            } catch (NumberFormatException ignored) {
            }
        }
        return p.ano();
    }

    private static String extrairValorInput(String html, String nome) {
        if (html == null) {
            return null;
        }
        Matcher m = PATTERN_INPUT.matcher(html);
        while (m.find()) {
            String tag = m.group();
            Matcher mn = PATTERN_NOME_INPUT.matcher(tag);
            if (!mn.find() || !nome.equals(mn.group(1))) {
                continue;
            }
            Matcher mv = PATTERN_VALOR_INPUT.matcher(tag);
            if (mv.find()) {
                return mv.group(1);
            }
        }
        return null;
    }

    private static int acharInicioCard(String html, int de) {
        int a = html.indexOf("kt-widget5__item", de);
        if (a < 0) {
            return -1;
        }
        int div = html.lastIndexOf("<div", a);
        return div >= de ? div : a;
    }

    private static String extrairEntreTags(String bloco, String classe) {
        Matcher m = Pattern.compile("<a\\b[^>]*class=[\"'][^\"']*\\b" + classe + "\\b[^\"']*[\"'][^>]*>(.*?)</a>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(bloco);
        if (!m.find()) {
            return null;
        }
        return Texto.semEntidades(m.group(1).replaceAll("<[^>]+>", " "));
    }

    private static String extrairRotuloInfo(String bloco, String rotuloRegex) {
        Matcher m = Pattern.compile("<span[^>]*>\\s*" + rotuloRegex + "\\s*</span>\\s*<span[^>]*>(.*?)</span>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(bloco);
        return m.find() ? Texto.semEntidades(m.group(1).replaceAll("<[^>]+>", " ")) : null;
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        if (arqDeputados == null || !Files.exists(Coleta.caminho(ctx, arqDeputados))) {
            System.out.println("alese: deputados.html não encontrado");
            return;
        }

        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alese/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alese: erro ao criar pasta de blocos", e);
        }

        String htmlDeputados;
        try {
            htmlDeputados = Texto.lerArquivo(Coleta.caminho(ctx, arqDeputados));
        } catch (Exception e) {
            throw new RuntimeException("alese: erro ao ler deputados.html", e);
        }

        List<DeputadoCard> deputados = parseDeputados(htmlDeputados);
        List<MesaMembro> mesaMembros = carregarMesa(ctx);
        Map<String, String> cargoMesaPorNome = new HashMap<>();
        for (MesaMembro mm : mesaMembros) {
            cargoMesaPorNome.put(Texto.normalizarNome(mm.nome()), mm.cargo());
        }

        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        Prov provMesa = arqMesa != null ? Prov.de(FONTE_MESA_DIRETORA, URL_MESA, arqMesa) : null;

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();

        for (DeputadoCard d : deputados) {
            int id = d.id();
            String idStr = String.valueOf(id);

            Coleta.Arquivo arqDetalhe = Coleta.metadados(ctx, nome(), "parlamentares/" + id + ".html");
            String urlDetalhe = URL_PARLAMENTAR + id;
            // Sem a ficha no cache (coleta que não alcançou o sistema legislativo), o link cita a lista de deputados.
            Prov provParl = arqDetalhe != null ? Prov.de(FONTE_PARLAMENTARES, urlDetalhe, arqDetalhe) : provDeputados;

            String biografia = null;
            String nomeCivil = d.nome();

            if (arqDetalhe != null && Files.exists(Coleta.caminho(ctx, arqDetalhe))) {
                try {
                    String htmlDetalhe = Texto.lerArquivo(Coleta.caminho(ctx, arqDetalhe));
                    String nc = parseNomeCivil(htmlDetalhe);
                    if (nc != null && !nc.isBlank()) {
                        nomeCivil = nc;
                    }
                    biografia = parseBiografia(htmlDetalhe);
                } catch (Exception e) {
                    System.err.println("alese: aviso ao ler detalhe de " + id + ": " + e.getMessage());
                }
            }

            if (nomeCivil != null && !nomeCivil.isBlank()) {
                nomesCivisPorId.put(idStr, nomeCivil);
            }

            List<LinkOficial> links = List.of(new LinkOficial("Ficha na ALESE", urlDetalhe, provParl));
            String situacao = "Em exercício";

            Pessoa pessoa = new Pessoa("alese", idStr, d.nome(), "deputado_estadual", "estadual", UF, UF,
                    d.partido(), d.fotoUrl(), situacao, true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, situacao, "Titular", NUMERO_LEGISLATURA, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<OrgaoOuFrente> orgaos = new ArrayList<>();
            String papelMesa = cargoMesaPorNome.get(Texto.normalizarNome(d.nome()));
            if (papelMesa != null && provMesa != null) {
                orgaos.add(new OrgaoOuFrente("orgao", "Mesa Diretora", null, papelMesa,
                        INICIO_LEG_20, FIM_LEG_20, provMesa));
            }
            List<Proposicao> proposicoes = coletarDadosDaFicha(ctx, d, orgaos);

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            if (biografia != null && !biografia.isBlank()) {
                blocosNode.put("biografia", biografia);
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(idStr + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alese: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alese/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alese/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("alese: erro ao gravar pessoas.json", e);
        }

        System.out.printf("alese: %d deputados estaduais normalizados%n", pessoas.size());
    }

    private List<MesaMembro> carregarMesa(Contexto ctx) {
        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        if (arqMesa == null || !Files.exists(Coleta.caminho(ctx, arqMesa))) {
            return List.of();
        }
        try {
            String htmlMesa = Texto.lerArquivo(Coleta.caminho(ctx, arqMesa));
            return parseMesa(htmlMesa);
        } catch (Exception e) {
            System.err.println("alese: aviso ao carregar mesa: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoCard> parseDeputados(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoCard> lista = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        Matcher m = PATTERN_CARD.matcher(html);
        while (m.find()) {
            String fotoUrl = m.group(1).trim();
            int id = Integer.parseInt(m.group(2).trim());
            String nome = decodificarHtml(m.group(3).trim());
            String partido = decodificarHtml(m.group(4).trim());
            String link = URL_PARLAMENTAR + id;
            if (vistos.add(id)) {
                lista.add(new DeputadoCard(id, nome, partido, fotoUrl, link));
            }
        }
        lista.sort(Comparator.comparing(DeputadoCard::nome));
        return lista;
    }

    public static String parseNomeCivil(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = PATTERN_NOME_CIVIL.matcher(html);
        if (m.find()) {
            String nc = decodificarHtml(m.group(1).trim());
            return nc.isBlank() ? null : nc;
        }
        return null;
    }

    public static String parseBiografia(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = PATTERN_BIO.matcher(html);
        if (m.find()) {
            String bruta = m.group(1);
            String limpa = bruta.replaceAll("<[^>]+>", " ");
            limpa = decodificarHtml(limpa);
            if (limpa.isBlank()) {
                return null;
            }
            limpa = limpa.replaceAll("\\(?\\d{2}\\)?\\s*\\d{4,5}-?\\d{4}", " ");
            limpa = limpa.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", " ");
            limpa = Guarda.semCpf(limpa);
            limpa = limpa.replaceAll("\\s+", " ").trim();
            return limpa.isBlank() ? null : limpa;
        }
        return null;
    }

    public static List<MesaMembro> parseMesa(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<MesaMembro> membros = new ArrayList<>();
        Matcher m = PATTERN_MESA.matcher(html);
        while (m.find()) {
            String cargoBruto = decodificarHtml(m.group(1).trim());
            String nomeBruto = decodificarHtml(m.group(2).trim());
            if (nomeBruto.isBlank() || nomeBruto.startsWith("---") ||
                    cargoBruto.toLowerCase(Locale.ROOT).contains("biênio") ||
                    cargoBruto.toLowerCase(Locale.ROOT).contains("legislatura")) {
                continue;
            }
            membros.add(new MesaMembro(cargoBruto, nomeBruto));
        }
        return membros;
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
