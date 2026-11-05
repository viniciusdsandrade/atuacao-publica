package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado do Rio de Janeiro (ALERJ) (ADR-0018).
 * Coleta os 70 deputados estaduais em exercício na 13ª legislatura (2023–2027),
 * dados de posse, mandatos anteriores e links oficiais.
 */
public final class Alerj implements Fonte {

    public static final String FONTE_DEPUTADOS = "alerj-deputados";
    public static final String FONTE_DETALHES = "alerj-deputados-detalhes";
    public static final String FONTE_PORTAL = "alerj-portal";

    public static final String SITE = "https://www.alerj.rj.gov.br";
    public static final String UF = "RJ";

    public static final LocalDate INICIO_LEG_13 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_13 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_13 = 13;

    public static final LocalDate INICIO_LEG_12 = LocalDate.of(2019, 2, 1);
    public static final LocalDate FIM_LEG_12 = LocalDate.of(2023, 1, 31);
    public static final int LEGISLATURA_12 = 12;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "<div class=\"controle_deputado(?:\\s+lider)?\">.*?" +
                    "<a href=\"/Deputados/PerfilDeputado/([0-9]+)\\?Legislatura=20\">.*?" +
                    "<img\\s+src=\"([^\"]+)\".*?" +
                    "<div class=\"partido\">([^<]+)</div>.*?" +
                    "<div class=\"nome\"><a[^>]*>([^<]+)</a></div>",
            Pattern.DOTALL);

    private static final Pattern POSSE_PATTERN = Pattern.compile(
            "Posse na ALERJ:[^\\d]+([0-9]{2}/[0-9]{2}/[0-9]{4})");

    private static final Pattern LEG_ANTERIOR_PATTERN = Pattern.compile(
            "12[ºª°]\\s*Legislatura|Legislaturas Anteriores");

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alerj";
    }

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alerj: coleta iniciada");
        coleta.obter(SITE + "/Deputados/QuemSao", "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Deputados/PerfilDeputado/" + d.id() + "?Legislatura=20";
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alerj: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, SITE + "/Deputados/QuemSao", arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alerj/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alerj: erro ao criar pasta de blocos", e);
        }

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeFormatado = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, SITE + "/Deputados/PerfilDeputado/" + id + "?Legislatura=20", arqPerfil) : provDeputados;

            String htmlPerfil = null;
            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                } catch (Exception ignored) {
                }
            }

            LocalDate dataPosse = extrairPosseDoHtml(htmlPerfil);
            if (dataPosse == null) {
                dataPosse = INICIO_LEG_13;
            }

            List<Mandato> mandatos = new ArrayList<>();

            if (temLegislaturaAnterior(htmlPerfil)) {
                mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                        INICIO_LEG_12, FIM_LEG_12, "Encerrado", "Titular", LEGISLATURA_12, 2018,
                        "Deputado Estadual na 12ª Legislatura (2019–2023)", provPerfil));
            }

            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    dataPosse, FIM_LEG_13, "Em exercício", "Titular", LEGISLATURA_13, 2022,
                    "Deputado Estadual na 13ª Legislatura (2023–2027)", provPerfil));

            mandatos.sort(Comparator.comparing(Mandato::inicio));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALERJ", SITE + "/Deputados/PerfilDeputado/" + id + "?Legislatura=20", provPerfil));

            Pessoa pessoa = new Pessoa(
                    "alerj", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<FonteUsada> fontes = new ArrayList<>();
            fontes.add(new FonteUsada(FONTE_DEPUTADOS, SITE + "/Deputados/QuemSao", arqDeputados != null ? arqDeputados.capturadoEm() : null));
            if (arqPerfil != null) {
                fontes.add(new FonteUsada(FONTE_DETALHES, SITE + "/Deputados/PerfilDeputado/" + id + "?Legislatura=20", arqPerfil.capturadoEm()));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.createArrayNode());
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(fontes));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        Json.gravar(ctx.normalizado().resolve("alerj/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alerj: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alerj", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alerj: erro ao ler deputados.html: " + e.getMessage());
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
                String foto = m.group(2).trim().replace(" ", "%20");
                String fotoUrl = foto.startsWith("/") ? SITE + foto : foto;
                String nome = Texto.semEntidades(m.group(4).trim());
                String partido = Texto.semEntidades(m.group(3).trim());
                lista.add(new DeputadoBruto(id, nome, partido, fotoUrl));
            }
        }
        return lista;
    }

    public static LocalDate extrairPosseDoHtml(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher m = POSSE_PATTERN.matcher(html);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1).trim(), DATA_BR);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean temLegislaturaAnterior(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        return LEG_ANTERIOR_PATTERN.matcher(html).find();
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
