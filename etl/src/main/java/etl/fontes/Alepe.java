package etl.fontes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.time.Duration;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
import tools.jackson.databind.JsonNode;

/**
 * Assembleia Legislativa do Estado de Pernambuco (ALEPE, ADR-0025).
 * Coleta os 49 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * fotos oficiais, comissões temáticas e mandatos.
 */
public final class Alepe implements Fonte {

    public static final String FONTE_DEPUTADOS = "alepe-deputados";
    public static final String FONTE_DETALHES = "alepe-deputados-detalhes";
    public static final String FONTE_PORTAL = "alepe-portal";
    public static final String FONTE_PROPOSICOES = "alepe-proposicoes";
    public static final String FONTE_DESPESAS = "alepe-despesas";

    static final String API_VERBAS = "https://www.alepe.pe.gov.br/servicos/transparencia/adm/";
    static final String URL_DEPUTADOS_DA_VERBA = "https://www.alepe.pe.gov.br/servicos/transparencia/dep/deputados.php?leg=-16";
    /** A verba indenizatória atual, com rubricas por ano, começa em maio de 2022. */
    static final int PRIMEIRO_ANO_DESPESAS = 2022;
    static final Duration INTERVALO_DA_VERBA = Duration.ofMillis(900);
    /** Consultas para aceitar uma lista vazia da verba: a primeira e duas confirmações. */
    static final int TENTATIVAS_DA_LISTA_VAZIA = 3;
    private static final Duration PAUSA_ENTRE_CONFIRMACOES = Duration.ofSeconds(3);
    /** Depois da coleta, o que seguiu vazio é consultado de novo após esta pausa: alguns documentos seguiram vazios por segundos. */
    private static final Duration PAUSA_DA_VARREDURA_FINAL = Duration.ofSeconds(45);

    /** Consulta da verba que seguiu vazia depois das confirmações. */
    record Pendente(String url, String caminho, Politica politica) {
    }

    public static final String API_PROPOSICOES = "https://dadosabertos.alepe.pe.gov.br/api/v1/proposicoes/";
    /** Página da proposição no sistema oficial, que carrega o item pela API com o mesmo docid e tipoprop. */
    public static final String PAGINA_PROPOSICAO = "https://proposicoes.alepe.pe.gov.br/proposicoes/detalhes";
    static final int PRIMEIRO_ANO_PROPOSICOES = 2022;

    /** Lista anual da API: nome na URL, elemento de cada item, código do tipo na página oficial e tipo fixo. */
    record ListaDeProposicoes(String lista, String elemento, String codigo, String tipoFixo) {
    }

    static final List<ListaDeProposicoes> LISTAS = List.of(
            new ListaDeProposicoes("projetos", "projeto", "p", null),
            new ListaDeProposicoes("indicacoes", "indicacao", "i", "INDICAÇÃO"),
            new ListaDeProposicoes("requerimentos", "requerimento", "r", "REQUERIMENTO"));

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

        // Proposições: uma lista XML por tipo e ano na API de dados abertos.
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            for (ListaDeProposicoes lista : LISTAS) {
                coleta.obterOpcional(API_PROPOSICOES + lista.lista() + "/?ano=" + ano, caminhoDaLista(lista, ano),
                        "application/xml", Politica.porAnoSemanal(ano, ctx.hoje()));
            }
        }

        coletarVerbas(ctx, coleta, deputados);
    }

    private static String caminhoDaLista(ListaDeProposicoes lista, int ano) {
        return "proposicoes/" + lista.lista() + "-" + ano + ".xml";
    }

    /**
     * Verba indenizatória: o portal de transparência usa um id próprio de deputado. A lista do portal liga o id ao nome;
     * por mês, cada deputado tem o pedido (e complementos), e cada documento traz as notas com empresa, CNPJ e valor.
     */
    private void coletarVerbas(Contexto ctx, Coleta coleta, List<DeputadoBruto> deputados) throws Exception {
        Coleta.Arquivo lista = coleta.obterOpcional(URL_DEPUTADOS_DA_VERBA, "despesas/deputados.json", "application/json",
                Politica.SEMANAL);
        if (lista == null || !Files.exists(Coleta.caminho(ctx, lista))) {
            return;
        }
        // O servidor responde 200 com lista vazia, em vez de erro, quando recebe consultas demais: no ritmo padrão de
        // 4 por segundo, 7% dos documentos e 39 de 74 listas de meses vieram vazios e foram guardados assim.
        ctx.http().respeitar("www.alepe.pe.gov.br", INTERVALO_DA_VERBA);
        List<String> ids = new ArrayList<>(idsDoPortalPorSlug(Json.ler(Coleta.caminho(ctx, lista)), slugPorChave(deputados)).values());
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            coleta.obterOpcional(API_VERBAS + "verbaindenizatoria-rubricas.php?ano=" + ano, arquivoDasRubricas(ano),
                    "application/json", Politica.porAnoSemanal(ano, ctx.hoje()));
        }
        Queue<Pendente> pendentes = new ConcurrentLinkedQueue<>();
        List<String> falhas = Camara.paraCadaDeputado(ids, id -> coletarVerbasDoDeputado(ctx, coleta, id, pendentes));
        for (String falha : falhas) {
            coleta.falhaParcial("despesas", falha);
        }
        varrerPendentes(ctx, coleta, pendentes);
    }

    /** Uma última rodada, depois de uma pausa maior, sobre o que seguiu vazio. O resto fica como veio da fonte. */
    private static void varrerPendentes(Contexto ctx, Coleta coleta, Queue<Pendente> pendentes) throws Exception {
        if (pendentes.isEmpty()) {
            return;
        }
        int total = pendentes.size();
        Thread.sleep(PAUSA_DA_VARREDURA_FINAL);
        int seguemVazios = 0;
        for (Pendente p : pendentes) {
            Coleta.Arquivo atual = Confirmacao.confirmandoVazio(nova -> coleta.obterOpcional(p.url(), p.caminho(), "application/json", nova),
                    p.politica(), arq -> vazia(ctx, arq), coleta.obterOpcional(p.url(), p.caminho(), "application/json", p.politica()),
                    TENTATIVAS_DA_LISTA_VAZIA, PAUSA_ENTRE_CONFIRMACOES);
            if (vazia(ctx, atual)) {
                seguemVazios++;
            }
        }
        System.out.println("alepe: " + total + " consultas da verba seguiram vazias depois das confirmações; "
                + seguemVazios + " seguem vazias depois da varredura final");
    }

    private static boolean vazia(Contexto ctx, Coleta.Arquivo arq) {
        return arq != null && Files.exists(Coleta.caminho(ctx, arq)) && Json.ler(Coleta.caminho(ctx, arq)).isEmpty();
    }

    private static void coletarVerbasDoDeputado(Contexto ctx, Coleta coleta, String id, Queue<Pendente> pendentes) throws Exception {
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            Politica politica = Politica.porAnoSemanal(ano, ctx.hoje());
            Coleta.Arquivo meses = obterConfirmandoVazio(ctx, coleta, API_VERBAS + "verbaindenizatoria-dep-meses.php?dep=" + id
                    + "&ano=" + ano, arquivoDosMeses(id, ano), politica, true, pendentes);
            if (meses == null || !Files.exists(Coleta.caminho(ctx, meses))) {
                continue;
            }
            for (JsonNode m : Json.ler(Coleta.caminho(ctx, meses))) {
                int mes = m.path("mes").asInt(0);
                if (mes < 1 || mes > 12) {
                    continue;
                }
                // Mês que a lista de meses traz tem documento: lista vazia aqui também é resposta de servidor sobrecarregado.
                Coleta.Arquivo documentos = obterConfirmandoVazio(ctx, coleta, API_VERBAS + "verbaindenizatoria.php?dep=" + id
                        + "&ano=" + ano + "&mes=" + mes, arquivoDosDocumentos(id, ano, mes), politica, true, pendentes);
                if (documentos == null || !Files.exists(Coleta.caminho(ctx, documentos))) {
                    continue;
                }
                for (JsonNode documento : Json.ler(Coleta.caminho(ctx, documentos))) {
                    String docid = Json.texto(documento, "docid");
                    if (docid != null) {
                        BigDecimal total = Texto.valor(Json.texto(documento, "total"));
                        obterConfirmandoVazio(ctx, coleta, API_VERBAS + "verbaindenizatorianotas.php?docid=" + docid,
                                arquivoDasNotas(docid), politica, total != null && total.signum() > 0, pendentes);
                    }
                }
            }
        }
    }

    /**
     * Busca o arquivo e, se vier lista vazia onde deveria haver dado, consulta de novo ({@code confirmar}). Vale também
     * para a lista vazia já guardada em coletas anteriores: ela é refeita antes de ser aceita.
     */
    private static Coleta.Arquivo obterConfirmandoVazio(Contexto ctx, Coleta coleta, String url, String caminho, Politica politica,
                                                        boolean confirmar, Queue<Pendente> pendentes) throws Exception {
        Coleta.Arquivo primeira = coleta.obterOpcional(url, caminho, "application/json", politica);
        if (!confirmar) {
            return primeira;
        }
        Coleta.Arquivo confirmada = Confirmacao.confirmandoVazio(nova -> coleta.obterOpcional(url, caminho, "application/json", nova), politica,
                arq -> vazia(ctx, arq), primeira, TENTATIVAS_DA_LISTA_VAZIA, PAUSA_ENTRE_CONFIRMACOES);
        if (vazia(ctx, confirmada)) {
            pendentes.add(new Pendente(url, caminho, politica));
        }
        return confirmada;
    }

    private static String arquivoDasRubricas(int ano) {
        return "despesas/rubricas-" + ano + ".json";
    }

    private static String arquivoDosMeses(String id, int ano) {
        return "despesas/" + id + "/meses-" + ano + ".json";
    }

    private static String arquivoDosDocumentos(String id, int ano, int mes) {
        return String.format(Locale.ROOT, "despesas/%s/%d-%02d.json", id, ano, mes);
    }

    private static String arquivoDasNotas(String docid) {
        return "despesas/notas/" + docid + ".json";
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
        Map<String, List<Proposicao>> proposicoesPorDeputado = proposicoesPorSlug(ctx, deputados);
        Map<String, List<Despesa>> despesasPorDeputado = despesasPorSlug(ctx, deputados);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String slug = d.slug();
            String id = slug;
            String nome = formatarNome(d.nome());
            String partido = normalizarPartido(d.partido());
            String fotoUrl = normalizarUrlFoto(d.fotoUrl());
            String nomeCivil = null;

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

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(slug, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesasPorDeputado.getOrDefault(slug, List.of())));
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoesPorDeputado.getOrDefault(slug, List.of())));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
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

    /** Chave de vínculo da autoria: nome sem acento e sem pontuação, em maiúsculas. Serve ao autor e ao perfil. */
    static String chaveDeAutoria(String nome) {
        return Texto.normalizarNome(nome.replaceAll("[^\\p{L}\\s]", " "));
    }

    /** Chave de autoria do perfil → slug. */
    private static Map<String, String> slugPorChave(List<DeputadoBruto> deputados) {
        Map<String, String> slugPorChave = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            slugPorChave.put(chaveDeAutoria(formatarNome(d.nome())), d.slug());
        }
        return slugPorChave;
    }

    /**
     * Slug do perfil → id do deputado no portal de transparência, pelo nome sem acento e pontuação. Nome repetido na
     * lista do portal (homônimo de outra legislatura) fica fora: sem certeza de quem é, não se atribui despesa.
     */
    static Map<String, String> idsDoPortalPorSlug(JsonNode lista, Map<String, String> slugPorChave) {
        Map<String, List<String>> idsPorChave = new HashMap<>();
        for (JsonNode d : lista) {
            String id = Json.texto(d, "id");
            String nome = Json.texto(d, "nome");
            if (id != null && nome != null) {
                idsPorChave.computeIfAbsent(chaveDeAutoria(nome), k -> new ArrayList<>()).add(id);
            }
        }
        Map<String, String> idPorSlug = new HashMap<>();
        for (var perfil : slugPorChave.entrySet()) {
            List<String> ids = idsPorChave.getOrDefault(perfil.getKey(), List.of());
            if (ids.size() == 1) {
                idPorSlug.put(perfil.getValue(), ids.getFirst());
            }
        }
        return idPorSlug;
    }

    /** Rubrica do ano → nome da categoria, pelo número da rubrica. */
    static Map<Integer, String> rubricasDoAno(JsonNode rubricas) {
        Map<Integer, String> nomes = new HashMap<>();
        for (JsonNode r : rubricas) {
            Integer numero = Json.inteiro(r, "numero_categoria");
            String nome = Json.texto(r, "nome_categoria");
            if (numero != null && nome != null) {
                nomes.put(numero, nome);
            }
        }
        return nomes;
    }

    /** Categoria do documento que a fonte publica com total e sem nenhuma nota. */
    static final String SEM_DETALHAMENTO = "Sem detalhamento na fonte";

    /**
     * Notas de um documento da verba (o pedido do mês ou um complemento). O ano e o mês são os da verba; a data é a da
     * nota. CNPJ de 14 dígitos fica; CPF de prestador pessoa física sai, do campo e do nome da empresa. Documento com
     * total e sem nota vira uma linha só, com o total, para o valor do deputado não ficar abaixo do que a fonte declara.
     */
    static List<Despesa> despesasDoDocumento(JsonNode documento, JsonNode notas, Map<Integer, String> rubricas, int ano, int mes,
                                            Prov provDasNotas, Prov provDoDocumento) {
        String numero = Json.texto(documento, "numero");
        String rotulo = "P".equals(Json.texto(documento, "tipo")) ? "Pedido " : "Complemento ";
        List<Despesa> despesas = new ArrayList<>();
        for (JsonNode nota : notas) {
            BigDecimal valor = Texto.valor(Json.texto(nota, "valor"));
            if (valor == null) {
                continue;
            }
            Integer rubrica = Json.inteiro(nota, "rubrica");
            String categoria = rubrica == null ? null : rubricas.get(rubrica);
            String documentoCnpj = Json.texto(nota, "cnpj");
            String digitos = documentoCnpj == null ? "" : documentoCnpj.replaceAll("\\D", "");
            String empresa = Json.texto(nota, "empresa");
            despesas.add(new Despesa(dia(Json.texto(nota, "data")), ano, mes,
                    categoria != null ? categoria : rubrica == null ? "Sem rubrica" : "Rubrica " + rubrica,
                    empresa == null ? null : Guarda.semCpf(empresa.replaceAll("\\s+", " ")),
                    digitos.length() == 14 ? digitos : null, valor, numero == null ? null : rotulo + numero, null, provDasNotas));
        }
        BigDecimal total = Texto.valor(Json.texto(documento, "total"));
        if (despesas.isEmpty() && total != null && total.signum() > 0) {
            despesas.add(new Despesa(null, ano, mes, SEM_DETALHAMENTO, null, null, total,
                    numero == null ? null : rotulo + numero, null, provDoDocumento));
        }
        return despesas;
    }

    private static LocalDate dia(String dataBr) {
        String iso = Texto.dataBr(dataBr);
        try {
            return iso == null ? null : LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Despesas de cada deputado em exercício, pelos arquivos em cache. */
    private static Map<String, List<Despesa>> despesasPorSlug(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, List<Despesa>> porSlug = new HashMap<>();
        Coleta.Arquivo lista = Coleta.metadados(ctx, "alepe", "despesas/deputados.json");
        if (lista == null || lista.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, lista))) {
            return porSlug;
        }
        Map<String, String> idPorSlug = idsDoPortalPorSlug(Json.ler(Coleta.caminho(ctx, lista)), slugPorChave(deputados));
        int lancamentos = 0;
        int totaisDiferentes = 0;
        int semNotas = 0;
        for (int ano = PRIMEIRO_ANO_DESPESAS; ano <= ctx.hoje().getYear(); ano++) {
            Map<Integer, String> rubricas = Map.of();
            Coleta.Arquivo arqRubricas = Coleta.metadados(ctx, "alepe", arquivoDasRubricas(ano));
            if (arqRubricas != null && arqRubricas.bytes() > 0 && Files.exists(Coleta.caminho(ctx, arqRubricas))) {
                rubricas = rubricasDoAno(Json.ler(Coleta.caminho(ctx, arqRubricas)));
            }
            for (var perfil : idPorSlug.entrySet()) {
                String id = perfil.getValue();
                Coleta.Arquivo meses = Coleta.metadados(ctx, "alepe", arquivoDosMeses(id, ano));
                if (meses == null || meses.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, meses))) {
                    continue;
                }
                for (JsonNode m : Json.ler(Coleta.caminho(ctx, meses))) {
                    int mes = m.path("mes").asInt(0);
                    Coleta.Arquivo documentos = Coleta.metadados(ctx, "alepe", arquivoDosDocumentos(id, ano, mes));
                    if (documentos == null || documentos.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, documentos))) {
                        continue;
                    }
                    for (JsonNode documento : Json.ler(Coleta.caminho(ctx, documentos))) {
                        String docid = Json.texto(documento, "docid");
                        Coleta.Arquivo notas = docid == null ? null : Coleta.metadados(ctx, "alepe", arquivoDasNotas(docid));
                        if (notas == null || notas.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, notas))) {
                            continue;
                        }
                        List<Despesa> doDocumento = despesasDoDocumento(documento, Json.ler(Coleta.caminho(ctx, notas)), rubricas,
                                ano, mes, Prov.de(FONTE_DESPESAS, notas.url(), notas),
                                Prov.de(FONTE_DESPESAS, documentos.url(), documentos));
                        BigDecimal soma = BigDecimal.ZERO;
                        for (Despesa d : doDocumento) {
                            soma = soma.add(d.valor());
                        }
                        BigDecimal total = Texto.valor(Json.texto(documento, "total"));
                        if (total != null && soma.compareTo(total) != 0) {
                            totaisDiferentes++;
                        }
                        if (doDocumento.size() == 1 && SEM_DETALHAMENTO.equals(doDocumento.getFirst().categoria())) {
                            semNotas++;
                        }
                        porSlug.computeIfAbsent(perfil.getKey(), k -> new ArrayList<>()).addAll(doDocumento);
                        lancamentos += doDocumento.size();
                    }
                }
            }
        }
        System.out.println("alepe: " + lancamentos + " lançamentos de verba indenizatória em " + porSlug.size() + " de "
                + idPorSlug.size() + " deputados com id no portal; " + semNotas + " documentos sem nota entram como linha única, e "
                + totaisDiferentes + " têm total diferente da soma das notas");
        return porSlug;
    }

    /** Proposições de cada deputado em exercício, pelas listas anuais em cache. */
    private static Map<String, List<Proposicao>> proposicoesPorSlug(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, String> slugPorChave = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            slugPorChave.put(chaveDeAutoria(formatarNome(d.nome())), d.slug());
        }
        Map<String, List<Proposicao>> porSlug = new HashMap<>();
        int total = 0;
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            for (ListaDeProposicoes lista : LISTAS) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "alepe", caminhoDaLista(lista, ano));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                try (InputStream entrada = Json.abrir(Coleta.caminho(ctx, arq))) {
                    var daLista = proposicoesDaLista(entrada, lista, slugPorChave, Prov.de(FONTE_PROPOSICOES, arq.url(), arq));
                    for (var e : daLista.entrySet()) {
                        porSlug.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
                        total += e.getValue().size();
                    }
                } catch (IOException | XMLStreamException e) {
                    throw new IllegalStateException("alepe: falha ao ler " + arq.caminho(), e);
                }
            }
        }
        System.out.println("alepe: " + total + " autorias de proposições em " + porSlug.size() + " deputados");
        return porSlug;
    }

    /**
     * Proposições de uma lista anual da API, por slug do autor. O autor vem pelo nome; só o autor do tipo DEPUTADO cujo
     * nome casa com um deputado em exercício entra. A posição na lista de autores vira {@code ordem_assinatura}.
     */
    static Map<String, List<Proposicao>> proposicoesDaLista(InputStream xml, ListaDeProposicoes lista,
                                                            Map<String, String> slugPorChave, Prov prov) throws XMLStreamException {
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        // A ementa em HTML escapado soma mais de 100 mil caracteres de entidades por arquivo, o limite do JDK desde a
        // versão 24. Sem DTD, só existem as entidades predefinidas do XML, de um caractere cada: sem risco de expansão.
        fabrica.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
        fabrica.setProperty("jdk.xml.totalEntitySizeLimit", 0);
        XMLStreamReader leitor = fabrica.createXMLStreamReader(xml);
        Map<String, List<Proposicao>> porSlug = new HashMap<>();
        Map<String, String> item = null;
        List<String[]> autores = new ArrayList<>();
        try {
            while (leitor.hasNext()) {
                int evento = leitor.next();
                if (evento == XMLStreamConstants.START_ELEMENT && leitor.getLocalName().equals(lista.elemento())) {
                    item = new HashMap<>();
                    for (int i = 0; i < leitor.getAttributeCount(); i++) {
                        item.put(leitor.getAttributeLocalName(i), leitor.getAttributeValue(i));
                    }
                    autores.clear();
                } else if (evento == XMLStreamConstants.START_ELEMENT && leitor.getLocalName().equals("autor") && item != null) {
                    autores.add(new String[] {leitor.getAttributeValue(null, "nome"), leitor.getAttributeValue(null, "tipo")});
                } else if (evento == XMLStreamConstants.END_ELEMENT && leitor.getLocalName().equals(lista.elemento()) && item != null) {
                    adicionarProposicao(item, autores, lista, slugPorChave, prov, porSlug);
                    item = null;
                }
            }
        } finally {
            leitor.close();
        }
        return porSlug;
    }

    private static void adicionarProposicao(Map<String, String> item, List<String[]> autores, ListaDeProposicoes lista,
                                            Map<String, String> slugPorChave, Prov prov, Map<String, List<Proposicao>> porSlug) {
        String docid = Texto.vazioParaNulo(item.get("docid"));
        Integer numero = inteiro(item.get("numero"));
        Integer ano = inteiro(item.get("ano"));
        String tipo = lista.tipoFixo() != null ? lista.tipoFixo() : Texto.vazioParaNulo(item.get("tipo"));
        if (docid == null || numero == null || ano == null || tipo == null) {
            return;
        }
        String ementa = ementaLegivel(item.get("ementa"));
        String url = PAGINA_PROPOSICAO + "?docid=" + docid + "&tipoprop=" + lista.codigo();
        Set<String> jaContados = new HashSet<>();
        for (int i = 0; i < autores.size(); i++) {
            String nome = autores.get(i)[0];
            String slug = nome == null || !"DEPUTADO".equals(autores.get(i)[1]) ? null : slugPorChave.get(chaveDeAutoria(nome));
            if (slug == null || !jaContados.add(slug)) {
                continue;
            }
            porSlug.computeIfAbsent(slug, k -> new ArrayList<>()).add(new Proposicao(docid, tipo, numero, ano,
                    tipo + " " + numero + "/" + ano, ementa, null, null, Texto.dataBr(item.get("dataPublicacao")), i + 1,
                    null, url, prov));
        }
    }

    /** Ementa sem marcação: indicações e requerimentos trazem o texto em HTML, com entidades. */
    static String ementaLegivel(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return Guarda.semCpf(Texto.semEntidades(html.replaceAll("(?s)<[^>]+>", " ")));
    }

    private static Integer inteiro(String texto) {
        try {
            return texto == null ? null : Integer.valueOf(texto.strip());
        } catch (NumberFormatException e) {
            return null;
        }
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
