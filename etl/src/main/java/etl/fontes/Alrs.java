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
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado do Rio Grande do Sul (ALRS) (ADR-0019).
 * Coleta os 55 deputados estaduais em exercício na 56ª legislatura (2023–2027),
 * dados de mandato, fotografias oficiais em alta resolução e links institucionais.
 */
public final class Alrs implements Fonte {

    public static final String FONTE_DEPUTADOS = "alrs-deputados";
    public static final String FONTE_DETALHES = "alrs-deputados-detalhes";
    public static final String FONTE_PORTAL = "alrs-portal";
    public static final String FONTE_VOTOS = "alrs-votos-plenario";
    public static final String FONTE_PROPOSICOES = "alrs-proposicoes";
    public static final String FONTE_GASTOS = "alrs-gastos";

    public static final String URL_GASTOS = "https://transparencia.al.rs.gov.br/parlamentares/gastos/pesquisa";
    static final int PRIMEIRO_ANO_GASTOS = 2022;
    static final int PRIMEIRO_ANO_PROPOSICOES = 2022;
    private static final Pattern LINHA_DA_TABELA = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELULA = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern LINK_DA_PROPOSICAO = Pattern.compile(
            "href=\"(/proposicao/([A-Za-z]+)/([0-9]+)/([0-9]{4})/([^\"/]+))\"");
    /** Categoria e valor do bloco de despesas do mês: o texto aparece também na versão larga, sem o ":" no fim. */
    private static final Pattern CATEGORIA_E_VALOR = Pattern.compile(
            "responsive-value[^>]*>\\s*([^<]+?):\\s*</span><br>\\s*<span[^>]*responsive-value[^>]*>\\s*-\\s*R\\$\\s*([0-9.]+,[0-9]{2})\\s*</span>");

    public static final String URL_VOTOS = "https://transparencia.al.rs.gov.br/parlamentares/votos-plenario/pesquisa";
    static final int PRIMEIRO_ANO_VOTOS = 2022;
    /** Cada voto da página é um JSON no atributo data-item, com as entidades HTML do atributo. */
    private static final Pattern ITEM_DE_VOTO = Pattern.compile("data-item='([^']*)'");

    public static final String SITE = "https://ww4.al.rs.gov.br";
    public static final String API_DEPUTADOS = "https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados";
    public static final String UF = "RS";

    public static final LocalDate INICIO_LEG_56 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_56 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_56 = 56;

    public static final Pattern DRUPAL_SETTINGS_PATTERN = Pattern.compile(
            "data-drupal-selector=\"drupal-settings-json\">\\s*(\\{.*?\\})\\s*</script>", Pattern.DOTALL);

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alrs";
    }

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alrs: coleta iniciada");
        coleta.obter(API_DEPUTADOS, "deputados.json", "application/json", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/deputados/" + d.id();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
            String urlComissoes = SITE + "/deputados/" + d.id() + "/comissoes";
            coleta.obterOuVazio(urlComissoes, "comissoes/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        // Votos em plenário: uma página por deputado e ano no portal da transparência, pelo mesmo id do perfil.
        for (DeputadoBruto d : deputados) {
            for (int ano = PRIMEIRO_ANO_VOTOS; ano <= ctx.hoje().getYear(); ano++) {
                coleta.obterOpcional(urlVotos(d.id(), ano), arquivoDeVotos(d.id(), ano), "text/html",
                        Politica.porAnoSemanal(ano, ctx.hoje()));
            }
        }

        // Proposições (uma página por deputado, com todas as da legislatura) e gastos da cota (uma página por deputado
        // e mês, no portal da transparência). Os deputados em paralelo, no limite de concorrência do cliente HTTP.
        List<String> ids = new ArrayList<>();
        deputados.forEach(d -> ids.add(d.id()));
        for (String falha : Camara.paraCadaDeputado(ids, id -> coletarAtuacao(ctx, coleta, id))) {
            coleta.falhaParcial("atuacao", falha);
        }
    }

    private static void coletarAtuacao(Contexto ctx, Coleta coleta, String id) throws Exception {
        coleta.obterOpcional(urlProposicoes(id), arquivoDeProposicoes(id), "text/html", Politica.SEMANAL);
        for (int ano = PRIMEIRO_ANO_GASTOS; ano <= ctx.hoje().getYear(); ano++) {
            int ultimoMes = ano == ctx.hoje().getYear() ? ctx.hoje().getMonthValue() : 12;
            for (int mes = 1; mes <= ultimoMes; mes++) {
                coleta.obterOpcional(urlGastos(id, ano, mes), arquivoDeGastos(id, ano, mes), "text/html",
                        Politica.porAnoSemanal(ano, ctx.hoje()));
            }
        }
    }

    static String urlProposicoes(String id) {
        return SITE + "/deputados/" + id + "/proposicoes";
    }

    private static String arquivoDeProposicoes(String id) {
        return "proposicoes/" + id + ".html";
    }

    static String urlGastos(String id, int ano, int mes) {
        return URL_GASTOS + "?ano=" + ano + "&mes=" + mes + "&solicitante=" + id;
    }

    private static String arquivoDeGastos(String id, int ano, int mes) {
        return String.format(Locale.ROOT, "gastos/%s-%d-%02d.html", id, ano, mes);
    }

    /** Texto de um trecho de HTML: sem marcação, com as entidades trocadas e os espaços simples. */
    private static String semMarcacao(String html) {
        return Texto.semEntidades(html.replaceAll("(?s)<[^>]+>", " "));
    }

    /**
     * Proposições da página do deputado, uma por linha da tabela (proposição, proponente, situação, tramitação e
     * ementa). A página não traz data: o ano da identificação é o ano da apresentação. O tipo é a sigla da fonte (PL,
     * REQ, RAP), e a tramitação, quando existe, é o órgão onde a proposição está.
     */
    static List<Proposicao> proposicoesDaPagina(String html, Prov prov) {
        Map<String, Proposicao> porId = new LinkedHashMap<>();
        Matcher linhas = LINHA_DA_TABELA.matcher(html);
        while (linhas.find()) {
            List<String> celulas = new ArrayList<>();
            Matcher c = CELULA.matcher(linhas.group(1));
            while (c.find()) {
                celulas.add(c.group(1));
            }
            if (celulas.size() < 5) {
                continue;
            }
            Matcher link = LINK_DA_PROPOSICAO.matcher(celulas.get(0));
            if (!link.find()) {
                continue;
            }
            int numero = Integer.parseInt(link.group(3));
            int ano = Integer.parseInt(link.group(4));
            if (ano < PRIMEIRO_ANO_PROPOSICOES) {
                continue;
            }
            String situacao = semMarcacao(celulas.get(2));
            String tramitacao = semMarcacao(celulas.get(3));
            String ementa = semMarcacao(celulas.get(4));
            porId.putIfAbsent(link.group(5), new Proposicao(link.group(5), link.group(2), numero, ano,
                    link.group(2) + " " + numero + "/" + ano, ementa == null ? null : Guarda.semCpf(ementa),
                    situacao == null ? null : tramitacao == null ? situacao : situacao + " (" + tramitacao + ")", null, null, null,
                    null, SITE + link.group(1), prov));
        }
        return new ArrayList<>(porId.values());
    }

    /** Categoria de gasto e valor do mês, como a fonte soma. */
    record Gasto(String categoria, BigDecimal valor) {
    }

    /** Gastos do mês e o total que a página declara, para conferir a soma. */
    record GastosDoMes(List<Gasto> categorias, BigDecimal totalDeclarado) {
    }

    /**
     * Gastos da cota num mês: o total por categoria, sem fornecedor nem nota. Mês anterior ao mandato vem sem o bloco
     * "Despesas do Mês", e a página volta sem categoria.
     */
    static GastosDoMes gastosDoMes(String html) {
        String semScript = html.replaceAll("(?s)<script.*?</script>", "");
        int inicio = semScript.indexOf("4- Despesas do M");
        int fim = semScript.indexOf("5- Outros Cr");
        if (inicio < 0 || fim <= inicio) {
            return new GastosDoMes(List.of(), null);
        }
        List<Gasto> categorias = new ArrayList<>();
        BigDecimal total = null;
        Matcher m = CATEGORIA_E_VALOR.matcher(semScript.substring(inicio, fim));
        while (m.find()) {
            String categoria = Texto.semEntidades(m.group(1));
            BigDecimal valor = Texto.valor(m.group(2));
            if (categoria == null || valor == null) {
                continue;
            }
            if (categoria.equalsIgnoreCase("Total")) {
                total = valor;
            } else {
                categorias.add(new Gasto(categoria, valor));
            }
        }
        return new GastosDoMes(categorias, total);
    }

    /** Proposições do deputado na página em cache. */
    private List<Proposicao> proposicoesDoDeputado(Contexto ctx, String id) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDeProposicoes(id));
        if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            return proposicoesDaPagina(Texto.lerArquivo(Coleta.caminho(ctx, arq)), Prov.de(FONTE_PROPOSICOES, arq.url(), arq));
        } catch (Exception e) {
            System.err.println("alrs: erro ao ler proposições do deputado " + id + ": " + e.getMessage());
            return List.of();
        }
    }

    /** Gastos do deputado nas páginas mensais em cache, uma linha por categoria e mês. */
    private List<Despesa> gastosDoDeputado(Contexto ctx, String id, int[] totaisDiferentes) {
        List<Despesa> despesas = new ArrayList<>();
        for (int ano = PRIMEIRO_ANO_GASTOS; ano <= ctx.hoje().getYear(); ano++) {
            for (int mes = 1; mes <= 12; mes++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDeGastos(id, ano, mes));
                if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                try {
                    GastosDoMes gastos = gastosDoMes(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                    BigDecimal soma = BigDecimal.ZERO;
                    for (Gasto g : gastos.categorias()) {
                        soma = soma.add(g.valor());
                        despesas.add(new Despesa(null, ano, mes, g.categoria(), null, null, g.valor(), null, null,
                                Prov.de(FONTE_GASTOS, arq.url(), arq)));
                    }
                    if (gastos.totalDeclarado() != null && soma.compareTo(gastos.totalDeclarado()) != 0) {
                        totaisDiferentes[0]++;
                    }
                } catch (Exception e) {
                    System.err.println("alrs: erro ao ler gastos do deputado " + id + " em " + ano + "-" + mes + ": " + e.getMessage());
                }
            }
        }
        return despesas;
    }

    static String urlVotos(String id, int ano) {
        return URL_VOTOS + "?solicitante=" + id + "&ano=" + ano;
    }

    private static String arquivoDeVotos(String id, int ano) {
        return "votos/" + id + "-" + ano + ".html";
    }

    /** Votos do deputado nas páginas anuais em cache. */
    private List<Voto> votosDoDeputado(Contexto ctx, String id) {
        List<Voto> votos = new ArrayList<>();
        for (int ano = PRIMEIRO_ANO_VOTOS; ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDeVotos(id, ano));
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                votos.addAll(votosDaPagina(Texto.lerArquivo(Coleta.caminho(ctx, arq)), Prov.de(FONTE_VOTOS, arq.url(), arq)));
            } catch (Exception e) {
                System.err.println("alrs: erro ao ler votos do deputado " + id + " em " + ano + ": " + e.getMessage());
            }
        }
        return votos;
    }

    /**
     * Votos de uma página anual do deputado. A página só lista o voto dado (Sim ou Não): não informa ausência, e por
     * isso não há bloco de participação. A votação não tem id na fonte: o id junta proposição e data, com um sufixo
     * quando a mesma proposição tem mais de uma votação no dia. Número de processo SEI na matéria ("000005461-01.00/21")
     * tem a forma de CPF: a sequência sai, para a guarda do publish não descartar o voto.
     */
    static List<Voto> votosDaPagina(String html, Prov prov) {
        List<Voto> votos = new ArrayList<>();
        Map<String, Integer> vezes = new HashMap<>();
        Matcher m = ITEM_DE_VOTO.matcher(html);
        while (m.find()) {
            String json = Texto.semEntidades(m.group(1));
            if (json == null) {
                continue;
            }
            JsonNode item = Json.FONTE.readTree(json);
            String tipo = Json.texto(item, "tipoProjeto");
            Integer numero = Json.inteiro(item, "numProposicao");
            Integer ano = Json.inteiro(item, "anoProposicao");
            String data = Texto.dataBr(Json.texto(item, "dataVotacao"));
            if (tipo == null || numero == null || ano == null || data == null) {
                continue;
            }
            String id = tipo + "-" + numero + "-" + ano + "-" + data;
            int vez = vezes.merge(id, 1, Integer::sum);
            votos.add(new Voto(vez == 1 ? id : id + "-" + vez, data, "PLEN", tipo + " " + numero + "/" + ano,
                    Guarda.semCpf(Json.texto(item, "materia")), Json.texto(item, "voto"), null, null, null, null, prov));
        }
        return votos;
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alrs: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.json");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, API_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alrs/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alrs: erro ao criar pasta de blocos", e);
        }

        List<Pessoa> pessoas = new ArrayList<>();
        int totalDeVotos = 0;
        int totalDeProposicoes = 0;
        int totalDeGastos = 0;
        int[] totaisDiferentes = {0};
        List<String> semProposicao = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeFormatado = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();

            String urlPerfil = SITE + "/deputados/" + id;
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_56, FIM_LEG_56, "Em exercício", "Titular", LEGISLATURA_56, 2022,
                    "Deputado Estadual na 56ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALRS", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alrs", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            String urlComissoes = SITE + "/deputados/" + id + "/comissoes";
            Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, nome(), "comissoes/" + id + ".html");
            List<OrgaoOuFrente> orgaos = List.of();
            if (arqComissoes != null && Files.exists(Coleta.caminho(ctx, arqComissoes))) {
                try {
                    String htmlComissoes = Texto.lerArquivo(Coleta.caminho(ctx, arqComissoes));
                    Prov provComissoes = Prov.de(FONTE_DETALHES, urlComissoes, arqComissoes);
                    orgaos = extrairComissoesDoHtml(htmlComissoes, provComissoes);
                } catch (Exception e) {
                    System.err.println("alrs: erro ao ler comissoes do deputado " + id + ": " + e.getMessage());
                }
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            List<Despesa> gastos = gastosDoDeputado(ctx, id, totaisDiferentes);
            totalDeGastos += gastos.size();
            blocosNode.set("despesas", Json.SAIDA.valueToTree(gastos));
            List<Proposicao> proposicoes = proposicoesDoDeputado(ctx, id);
            totalDeProposicoes += proposicoes.size();
            if (proposicoes.isEmpty()) {
                semProposicao.add(id);
            }
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            List<Voto> votos = votosDoDeputado(ctx, id);
            totalDeVotos += votos.size();
            blocosNode.set("votos", Json.SAIDA.valueToTree(votos));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }
        System.out.println("alrs: " + totalDeVotos + " votos em plenário desde " + PRIMEIRO_ANO_VOTOS);
        System.out.println("alrs: " + totalDeProposicoes + " proposições desde " + PRIMEIRO_ANO_PROPOSICOES + "; "
                + semProposicao.size() + " deputados sem proposição " + semProposicao);
        System.out.println("alrs: " + totalDeGastos + " lançamentos de gasto por categoria e mês; " + totaisDiferentes[0]
                + " meses com total diferente da soma das categorias");

        Json.gravar(ctx.normalizado().resolve("alrs/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alrs: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<OrgaoOuFrente> extrairComissoesDoHtml(String html, Prov prov) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Matcher m = DRUPAL_SETTINGS_PATTERN.matcher(html);
        if (!m.find()) {
            return List.of();
        }
        try {
            JsonNode root = Json.SAIDA.readTree(m.group(1));
            JsonNode comissoes = root.path("alergs_deputados").path("comissoes");
            if (!comissoes.isArray()) {
                return List.of();
            }
            List<OrgaoOuFrente> lista = new ArrayList<>();
            for (JsonNode c : comissoes) {
                String nome = c.path("nomeComissao").asText("").trim();
                if (nome.isEmpty()) continue;
                String tipoStr = c.path("tipoComissao").asText("").toLowerCase(Locale.ROOT);
                String tipo = tipoStr.contains("frente") ? "frente" : (tipoStr.contains("orgao") ? "orgao" : "comissao");
                String papel = "Membro";
                LocalDate inicio = INICIO_LEG_56;
                LocalDate fim = "SIM".equalsIgnoreCase(c.path("situacaoComissao").asText()) ? FIM_LEG_56 : null;
                lista.add(new OrgaoOuFrente(tipo, nome, null, papel, inicio, fim, prov));
            }
            return lista;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alrs", "deputados.json");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoJson(root);
        } catch (Exception e) {
            System.err.println("alrs: erro ao ler deputados.json: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoJson(JsonNode root) {
        if (root == null || !root.has("lista")) {
            return List.of();
        }
        JsonNode listaNode = root.get("lista");
        if (!listaNode.isArray()) {
            return List.of();
        }
        List<DeputadoBruto> deputados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (JsonNode item : listaNode) {
            long idNum = item.path("idDeputado").asLong(0);
            if (idNum <= 0) {
                continue;
            }
            String id = String.valueOf(idNum);
            if (vistos.add(id)) {
                String nome = item.path("nomeDeputado").asText("").trim();
                String partido = item.path("siglaPartido").asText("").trim();
                String foto = item.path("fotoGrandeDeputado").asText("").trim().replace(" ", "%20");
                deputados.add(new DeputadoBruto(id, nome, partido, foto));
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
