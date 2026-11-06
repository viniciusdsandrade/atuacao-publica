package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
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
import etl.Registros;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Pará (ALEPA, ADR-0024).
 * Coleta os 41 deputados estaduais em exercício na 60ª legislatura (2023–2027),
 * dados de mandato, comissões permanentes e representações, biografias, fotografias e links institucionais.
 */
public final class Alepa implements Fonte {

    public static final String FONTE_DEPUTADOS = "alepa-deputados";
    public static final String FONTE_DETALHES = "alepa-deputados-detalhes";
    public static final String FONTE_PORTAL = "alepa-portal";

    public static final String SITE = "https://www.alepa.pa.gov.br";
    public static final String URL_DEPUTADOS = "https://www.alepa.pa.gov.br/Home/Page/Deputados";
    public static final String URL_COMISSOES = "https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes";
    public static final String UF = "PA";

    public static final LocalDate INICIO_LEG_60 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_60 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_60 = 60;

    public static final Pattern KEYS_PATTERN = Pattern.compile(
            "'stateObject':\\s*\\{[^}]*'keys':\\s*\\[([^\\]]+)\\]",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern CARD_PATTERN = Pattern.compile(
            "<div id=[\"']cardView_DXDataCard([0-9]+)[\"'][^>]*>(.*?)</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_CARD_PATTERN = Pattern.compile(
            "<img class=[\"']deputado-image[\"'][^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern INFO_CARD_PATTERN = Pattern.compile(
            "<div class=[\"']card-info[\"']>\\s*<span>(?:Deputado|Deputada)</span>\\s*<span>([^<]+)</span>\\s*<p>([^<]+)</p>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<span>Nome completo:\\s*<b>([^<]+)</b></span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NASCIMENTO_PATTERN = Pattern.compile(
            "<span>Nascimento:\\s*<b>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</b></span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTIDO_PERFIL_PATTERN = Pattern.compile(
            "<span>Partido Político:\\s*<b>([^<]+)</b></span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PERFIL_PATTERN = Pattern.compile(
            "<div class=[\"']card-politico-img-wrapper[\"']>.*?<img src=[\"']([^\"']+)[\"']",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern BIO_PATTERN = Pattern.compile(
            "<div id=[\"']bioContent[\"']>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern COMISSAO_ITEM_PATTERN = Pattern.compile(
            "<div class=[\"']comissoes-item[\"']>.*?<div class=[\"']comissoes-header[\"']><h1>([^<]+)</h1></div>.*?<div class=[\"']comissoes-content[\"']>(.*?)</div>\\s*</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTICIPANTE_PATTERN = Pattern.compile(
            "<a href=[\"']/Institucional/Deputado/([0-9]+)[\"'][^>]*>\\s*<p[^>]*>([^<]+)</p>\\s*</a>",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alepa";
    }

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alepa: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Institucional/Deputado/" + d.id();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_COMISSOES, "comissoes.html", "text/html", Politica.SEMANAL);
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alepa: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alepa/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alepa: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairTodasComissoes(ctx);

        List<Pessoa> pessoas = new ArrayList<>();
        DateTimeFormatter fmtNasc = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nome = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();
            String nomeCivil = null;
            LocalDate nascimento = null;
            String biografia = null;

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, SITE + "/Institucional/Deputado/" + id, arqPerfil);

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));

                    Matcher mNomeCivil = NOME_CIVIL_PATTERN.matcher(htmlPerfil);
                    if (mNomeCivil.find()) {
                        nomeCivil = formatarNome(decodificarHtml(mNomeCivil.group(1).trim()));
                    }

                    Matcher mPart = PARTIDO_PERFIL_PATTERN.matcher(htmlPerfil);
                    if (mPart.find()) {
                        String p = mPart.group(1).trim();
                        if (!p.isBlank()) {
                            partido = p;
                        }
                    }

                    Matcher mFoto = FOTO_PERFIL_PATTERN.matcher(htmlPerfil);
                    if (mFoto.find()) {
                        String f = normalizarUrlFoto(mFoto.group(1).trim());
                        if (!f.isBlank()) {
                            fotoUrl = f;
                        }
                    }

                    Matcher mNasc = NASCIMENTO_PATTERN.matcher(htmlPerfil);
                    if (mNasc.find()) {
                        try {
                            nascimento = LocalDate.parse(mNasc.group(1).trim(), fmtNasc);
                        } catch (Exception ignored) {
                        }
                    }

                    Matcher mBio = BIO_PATTERN.matcher(htmlPerfil);
                    if (mBio.find()) {
                        String bioTexto = mBio.group(1).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                        if (!bioTexto.isBlank()) {
                            biografia = decodificarHtml(bioTexto);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("alepa: erro ao ler perfil de " + id + ": " + e.getMessage());
                }
            }

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALEPA", SITE + "/Institucional/Deputado/" + id, provPerfil)
            );

            Pessoa pessoa = new Pessoa("alepa", id, nome, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_60, FIM_LEG_60, "Em exercício", "Titular", LEGISLATURA_60, 2022,
                    "Deputado Estadual na 60ª Legislatura (2023–2027)", provDeputados));

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(id, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("alepa/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alepa: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alepa", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alepa: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        List<DeputadoBruto> lista = new ArrayList<>();

        Matcher mKeys = KEYS_PATTERN.matcher(html);
        if (!mKeys.find()) {
            return List.of();
        }
        String keysBruto = mKeys.group(1);
        String[] keys = keysBruto.replace("'", "").replace("\"", "").split(",");
        List<String> ids = new ArrayList<>();
        for (String k : keys) {
            String kTrim = k.trim();
            if (!kTrim.isBlank()) {
                ids.add(kTrim);
            }
        }

        Matcher mCard = CARD_PATTERN.matcher(html);
        int idx = 0;
        while (mCard.find() && idx < ids.size()) {
            String cardHtml = mCard.group(2);
            String id = ids.get(idx);

            String nome = "";
            String partido = "";
            Matcher mInfo = INFO_CARD_PATTERN.matcher(cardHtml);
            if (mInfo.find()) {
                nome = decodificarHtml(mInfo.group(1).trim());
                partido = mInfo.group(2).trim();
            }

            String fotoUrl = "";
            Matcher mFoto = FOTO_CARD_PATTERN.matcher(cardHtml);
            if (mFoto.find()) {
                fotoUrl = normalizarUrlFoto(mFoto.group(1).trim());
            }

            if (!nome.isBlank()) {
                lista.add(new DeputadoBruto(id, nome, partido, fotoUrl));
            }
            idx++;
        }

        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairTodasComissoes(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alepa", "comissoes.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return Map.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            Prov prov = Prov.de(FONTE_DETALHES, URL_COMISSOES, arq);
            return extrairComissoesDoHtml(html, prov);
        } catch (Exception e) {
            System.err.println("alepa: erro ao extrair comissoes: " + e.getMessage());
            return Map.of();
        }
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(String html, Prov prov) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        String[] blocos = html.split("<div[^>]+class=[\"']comissoes-item[\"'][^>]*>");
        Pattern pHeader = Pattern.compile("<div[^>]+class=[\"']comissoes-header[\"'][^>]*>\\s*<h1>([^<]+)</h1>", Pattern.CASE_INSENSITIVE);

        for (int i = 1; i < blocos.length; i++) {
            String bloco = blocos[i];
            Matcher mHeader = pHeader.matcher(bloco);
            String titulo = "Comissão";
            if (mHeader.find()) {
                titulo = formatarTituloComissao(decodificarHtml(mHeader.group(1).trim()));
            }

            // Divide em TITULARES e SUPLENTES se houver divisão
            String[] partes = bloco.split("SUPLENTES");
            String titularesHtml = partes[0];
            String suplentesHtml = partes.length > 1 ? partes[1] : "";

            // Titulares
            Matcher mTit = PARTICIPANTE_PATTERN.matcher(titularesHtml);
            while (mTit.find()) {
                String id = mTit.group(1);
                String texto = mTit.group(2);

                String papel = "Titular";
                if (texto.toUpperCase(Locale.ROOT).contains("(PRESIDENTE)")) {
                    papel = "Presidente";
                } else if (texto.toUpperCase(Locale.ROOT).contains("(VICE-PRESIDENTE)")) {
                    papel = "Vice-Presidente";
                }

                resultado.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", titulo, null, papel, INICIO_LEG_60, null, prov));
            }

            // Suplentes
            if (!suplentesHtml.isBlank()) {
                Matcher mSup = PARTICIPANTE_PATTERN.matcher(suplentesHtml);
                while (mSup.find()) {
                    String id = mSup.group(1);
                    resultado.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", titulo, null, "Suplente", INICIO_LEG_60, null, prov));
                }
            }
        }

        return resultado;
    }

    public static String formatarTituloComissao(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return "Comissão";
        }
        String limpo = bruto.replaceAll("\\s+", " ").trim();
        // Capitaliza de forma agradável preservando siglas
        return limpo;
    }

    public static String normalizarUrlFoto(String urlBruta) {
        if (urlBruta == null || urlBruta.isBlank()) {
            return null;
        }
        String corrigida = urlBruta.replace("\\", "/").trim();
        if (corrigida.startsWith("//")) {
            corrigida = "https:" + corrigida;
        } else if (corrigida.startsWith("/")) {
            corrigida = SITE + corrigida;
        }
        return corrigida.replace(" ", "%20");
    }

    public static String decodificarHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&#227;", "ã")
                .replace("&#245;", "õ")
                .replace("&#225;", "á")
                .replace("&#233;", "é")
                .replace("&#237;", "í")
                .replace("&#243;", "ó")
                .replace("&#250;", "ú")
                .replace("&#224;", "à")
                .replace("&#226;", "â")
                .replace("&#234;", "ê")
                .replace("&#244;", "ô")
                .replace("&#231;", "ç")
                .replace("&#193;", "Á")
                .replace("&#201;", "É")
                .replace("&#205;", "Í")
                .replace("&#211;", "Ó")
                .replace("&#218;", "Ú")
                .replace("&#192;", "À")
                .replace("&#194;", "Â")
                .replace("&#202;", "Ê")
                .replace("&#212;", "Ô")
                .replace("&#195;", "Ã")
                .replace("&#213;", "Õ")
                .replace("&#199;", "Ç")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String formatarNome(String nomeBruto) {
        if (nomeBruto == null || nomeBruto.isBlank()) {
            return "";
        }
        String decodificado = decodificarHtml(nomeBruto);
        String[] partes = decodificado.replaceAll("\\s+", " ").split(" ");
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
