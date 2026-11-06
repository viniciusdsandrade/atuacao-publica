package etl.fontes;

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
import etl.Json;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

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

            List<FonteUsada> fontes = new ArrayList<>();
            fontes.add(new FonteUsada(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados != null ? arqDeputados.capturadoEm() : null));
            if (arqPerfil != null) {
                fontes.add(new FonteUsada(FONTE_DETALHES, urlPerfil, arqPerfil.capturadoEm()));
            }

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(id, List.of());
            if (!orgaos.isEmpty()) {
                fontes.add(new FonteUsada(FONTE_DETALHES, SITE + "/comissoes", arqDeputados != null ? arqDeputados.capturadoEm() : null));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(fontes));
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
