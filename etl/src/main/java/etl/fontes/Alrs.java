package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            List<Voto> votos = votosDoDeputado(ctx, id);
            totalDeVotos += votos.size();
            blocosNode.set("votos", Json.SAIDA.valueToTree(votos));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }
        System.out.println("alrs: " + totalDeVotos + " votos em plenário desde " + PRIMEIRO_ANO_VOTOS);

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
