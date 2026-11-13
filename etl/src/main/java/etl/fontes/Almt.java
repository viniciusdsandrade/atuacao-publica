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
import etl.Texto;

/**
 * Assembleia Legislativa do Estado de Mato Grosso (ALMT, ADR-0037).
 * Coleta os 24 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados de mandato, composição da Mesa Diretora, fotografias e links institucionais.
 */
public final class Almt implements Fonte {

    public static final String FONTE_DEPUTADOS = "almt-deputados";
    public static final String FONTE_DETALHES = "almt-deputados-detalhes";
    public static final String FONTE_MESA_DIRETORA = "almt-mesa-diretora";
    public static final String FONTE_PORTAL = "almt-portal";

    public static final String SITE = "https://www.al.mt.gov.br";
    public static final String URL_DEPUTADOS = "https://www.al.mt.gov.br/parlamento/deputados";
    public static final String URL_MESA_DIRETORA = "https://www.al.mt.gov.br/parlamento/legislatura/21/sessao-legislativa";
    public static final String UF = "MT";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    private static final DateTimeFormatter FORMATO_DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl, String href) {
    }

    public record PerfilDeputado(String nomeCivil, String nomeParlamentar, String partido, String fotoUrl,
                                 List<String> redes) {
    }

    public static final Pattern ID_PATTERN = Pattern.compile(
            "href=[\"']/parlamento/deputados/([0-9]+)/perfil[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CARD_PATTERN = Pattern.compile(
            "<h6[^>]*class=[\"'][^\"']*card-title[^\"']*[\"'][^>]*>([^<]+)</h6>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_CARD_PATTERN = Pattern.compile(
            "<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTIDO_CARD_PATTERN = Pattern.compile(
            "<div class=[\"']card-body[\"']>\\s*([A-Za-z0-9]+)\\s*</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "Nome civil:</span>\\s*([^<]+)",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_PARL_PATTERN = Pattern.compile(
            "Nome parlamentar:</span>\\s*([^<]+)",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTIDO_PERFIL_PATTERN = Pattern.compile(
            "Filiação partidária:</span>\\s*([^<]+)",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PERFIL_PATTERN = Pattern.compile(
            "<div class=[\"']col-md-3 mb-2[\"']>.*?<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);


    public static final Pattern REDES_PATTERN = Pattern.compile(
            "<a href=[\"'](https?://[^\"']+)[\"'][^>]*class=[\"']btn-icon[^\"']*[\"'][^>]*>\\s*<i class=[\"']bi bi-(twitter-x|twitter|facebook|youtube|instagram)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern SESSAO_SPLIT_PATTERN = Pattern.compile(
            "<h6>\\s*([0-9]+)ª Sessão Legislativa",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern INTERVALO_DATAS_PATTERN = Pattern.compile(
            "([0-9]{2}/[0-9]{2}/[0-9]{4})\\s*-\\s*([0-9]{2}/[0-9]{2}/[0-9]{4})");

    public static final Pattern CARGO_MEMBRO_PATTERN = Pattern.compile(
            "<h6>\\s*<b>([^<]+)</b>\\s*<br>\\s*([^<]+)\\s*</h6>",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "almt";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("almt: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/parlamento/deputados/" + d.id() + "/perfil";
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_MESA_DIRETORA, "mesa-diretora.html", "text/html", Politica.SEMANAL);
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("almt: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa-diretora.html");
        Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, URL_MESA_DIRETORA, arqMesa);

        Path pastaBlocos = ctx.normalizado().resolve("almt/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("almt: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> mesaPorNome = extrairMesaDiretora(ctx, provMesa);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeParlamentar = d.nome();
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();

            String urlPerfil = d.href().startsWith("http") ? d.href() : SITE + d.href();
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil);

            List<LinkOficial> links = new ArrayList<>();
            links.add(new LinkOficial("Página na ALMT", urlPerfil, provPerfil));

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                    PerfilDeputado perfil = parsePerfil(htmlPerfil);

                    if (perfil.nomeCivil() != null) {
                        nomesCivisPorId.put(id, perfil.nomeCivil());
                    }
                    if (perfil.nomeParlamentar() != null && !perfil.nomeParlamentar().isBlank()) {
                        nomeParlamentar = perfil.nomeParlamentar();
                    }
                    if (perfil.partido() != null && !perfil.partido().isBlank()) {
                        partido = perfil.partido();
                    }
                    if (perfil.fotoUrl() != null && !perfil.fotoUrl().isBlank()) {
                        fotoUrl = perfil.fotoUrl();
                    }
                    for (String rede : perfil.redes()) {
                        links.add(new LinkOficial("Rede social", rede, provPerfil));
                    }
                } catch (Exception e) {
                    System.err.println("almt: aviso ao processar perfil de " + id + ": " + e.getMessage());
                }
            }

            Pessoa pessoa = new Pessoa("almt", id, nomeParlamentar, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, "Em exercício", "Titular", LEGISLATURA_20, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            String chaveNome = normalizarChaveNome(nomeParlamentar);
            List<OrgaoOuFrente> orgaos = mesaPorNome.getOrDefault(chaveNome, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));

            try {
                Path arquivoBloco = pastaBlocos.resolve(id + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("almt: erro ao gravar bloco de " + id, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("almt/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("almt/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("almt: erro ao gravar pessoas.json", e);
        }
    }

    public List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return parseDeputados(html);
        } catch (Exception e) {
            System.err.println("almt: erro ao ler deputados.html: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> parseDeputados(String html) {
        List<DeputadoBruto> resultado = new ArrayList<>();
        String[] partes = html.split("<div class=[\"']card rounded-0 my-3[\"']>");
        for (int i = 1; i < partes.length; i++) {
            String card = partes[i];
            Matcher mId = ID_PATTERN.matcher(card);
            Matcher mNome = NOME_CARD_PATTERN.matcher(card);
            Matcher mFoto = FOTO_CARD_PATTERN.matcher(card);
            Matcher mPartido = PARTIDO_CARD_PATTERN.matcher(card);

            if (mId.find()) {
                String id = mId.group(1).trim();
                String href = "/parlamento/deputados/" + id + "/perfil";
                String nome = mNome.find() ? capitalizar(mNome.group(1).trim()) : "Deputado " + id;
                String fotoUrl = null;
                if (mFoto.find()) {
                    fotoUrl = normalizarUrlFoto(mFoto.group(1).trim());
                }
                String partido = mPartido.find() ? mPartido.group(1).trim() : null;

                resultado.add(new DeputadoBruto(id, nome, partido, fotoUrl, href));
            }
        }
        return resultado;
    }

    public static PerfilDeputado parsePerfil(String html) {
        String nomeCivil = null;
        String nomeParlamentar = null;
        String partido = null;
        String fotoUrl = null;
        List<String> redes = new ArrayList<>();

        Matcher mCivil = NOME_CIVIL_PATTERN.matcher(html);
        if (mCivil.find()) {
            nomeCivil = limparEspacos(mCivil.group(1).trim());
        }

        Matcher mParl = NOME_PARL_PATTERN.matcher(html);
        if (mParl.find()) {
            nomeParlamentar = limparEspacos(mParl.group(1).trim());
        }

        Matcher mPart = PARTIDO_PERFIL_PATTERN.matcher(html);
        if (mPart.find()) {
            partido = limparEspacos(mPart.group(1).trim());
        }

        Matcher mFoto = FOTO_PERFIL_PATTERN.matcher(html);
        if (mFoto.find()) {
            fotoUrl = normalizarUrlFoto(mFoto.group(1).trim());
        }


        Matcher mRedes = REDES_PATTERN.matcher(html);
        Set<String> redesVistas = new HashSet<>();
        while (mRedes.find()) {
            String url = mRedes.group(1).trim();
            if (redeValida(url) && redesVistas.add(url)) {
                redes.add(url);
            }
        }

        return new PerfilDeputado(nomeCivil, nomeParlamentar, partido, fotoUrl, redes);
    }

    public Map<String, List<OrgaoOuFrente>> extrairMesaDiretora(Contexto ctx, Prov prov) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "mesa-diretora.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return Map.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return parseMesaDiretora(html, prov);
        } catch (Exception e) {
            System.err.println("almt: erro ao ler mesa-diretora.html: " + e.getMessage());
            return Map.of();
        }
    }

    public static Map<String, List<OrgaoOuFrente>> parseMesaDiretora(String html, Prov prov) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();
        String[] sessoes = html.split("<h6>\\s*([0-9]+)ª Sessão Legislativa");
        for (int i = 1; i < sessoes.length; i++) {
            String bloco = sessoes[i];
            LocalDate dataInicio = INICIO_LEG_20;
            LocalDate dataFim = FIM_LEG_20;

            Matcher mDatas = INTERVALO_DATAS_PATTERN.matcher(bloco);
            if (mDatas.find()) {
                try {
                    dataInicio = LocalDate.parse(mDatas.group(1), FORMATO_DATA_BR);
                    dataFim = LocalDate.parse(mDatas.group(2), FORMATO_DATA_BR);
                } catch (Exception ignored) {
                }
            }

            Matcher mMembros = CARGO_MEMBRO_PATTERN.matcher(bloco);
            while (mMembros.find()) {
                String cargo = limparEspacos(mMembros.group(1).trim());
                String nome = limparEspacos(mMembros.group(2).trim());
                String chave = normalizarChaveNome(nome);

                OrgaoOuFrente orgao = new OrgaoOuFrente("orgao", "Mesa Diretora", null, cargo,
                        dataInicio, dataFim, prov);
                resultado.computeIfAbsent(chave, k -> new ArrayList<>()).add(orgao);
            }
        }
        return resultado;
    }

    public static String normalizarChaveNome(String nome) {
        return Texto.normalizarNome(nome);
    }

    private static String normalizarUrlFoto(String url) {
        String limpa = url.replace("&amp;", "&");
        if (limpa.startsWith("http://") || limpa.startsWith("https://")) {
            return limpa;
        }
        if (limpa.startsWith("/")) {
            return SITE + limpa;
        }
        return SITE + "/" + limpa;
    }

    private static boolean redeValida(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return (u.contains("twitter.com") || u.contains("x.com") || u.contains("facebook.com")
                || u.contains("youtube.com") || u.contains("instagram.com"))
                && !u.contains("whatsapp") && !u.contains("telegram");
    }

    public static String limparEspacos(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    public static String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String[] partes = texto.trim().split("\\s+");
        List<String> res = new ArrayList<>();
        Set<String> minusculas = Set.of("de", "da", "do", "das", "dos", "e", "a", "o");
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            if (p.isEmpty()) continue;
            String lower = p.toLowerCase(Locale.ROOT);
            if (i > 0 && minusculas.contains(lower)) {
                res.add(lower);
            } else if (lower.length() <= 2 && (lower.equals("dr") || lower.equals("sr"))) {
                res.add(Character.toUpperCase(lower.charAt(0)) + lower.substring(1) + ".");
            } else {
                res.add(Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
            }
        }
        return String.join(" ", res);
    }
}
