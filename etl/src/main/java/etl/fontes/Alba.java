package etl.fontes;

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
import etl.Json;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
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

    public static final String SITE = "https://www.al.ba.gov.br";
    public static final String UF = "BA";

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
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
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
