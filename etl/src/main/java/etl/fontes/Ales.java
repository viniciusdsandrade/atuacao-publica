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
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Espírito Santo (ALES, ADR-0023).
 * Coleta os 30 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados de mandato, comissões permanentes, fotografias oficiais em alta resolução e links institucionais.
 */
public final class Ales implements Fonte {

    public static final String FONTE_DEPUTADOS = "ales-deputados";
    public static final String FONTE_DETALHES = "ales-deputados-detalhes";
    public static final String FONTE_PORTAL = "ales-portal";

    public static final String SITE = "https://www.al.es.gov.br";
    public static final String URL_DEPUTADOS = "https://www.al.es.gov.br/Deputado/Lista";
    public static final String URL_COMISSOES = "https://www.al.es.gov.br/Comissao/ListaComissao?tipoComissao=permanente";
    public static final String UF = "ES";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    public static final Pattern SELECT_DEPUTADOS_PATTERN = Pattern.compile(
            "<select[^>]*id=[\"']ddlistDeputado[\"'][^>]*>(.*?)</select>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern OPTION_DEPUTADO_PATTERN = Pattern.compile(
            "<option value=[\"']([0-9]+)[\"']>([^<]+)</option>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PATTERN = Pattern.compile(
            "<div id=[\"']divFotoDeputado[\"'][^>]*>.*?<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTIDO_PATTERN = Pattern.compile(
            "<label><b>Partido:</b></label>\\s*<span>\\s*([^<]+)</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<label>Nome Civil:\\s*</label>\\s*<span>\\s*([^<]+)</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NASCIMENTO_PATTERN = Pattern.compile(
            "<label>Data de Nascimento:\\s*</label>\\s*<span>\\s*([0-9]{2}/[0-9]{2}/[0-9]{4})</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern TITULO_COMISSAO_PATTERN = Pattern.compile(
            "<div class=[\"']fonte-titulo-deputado[\"']>\\s*<span>([^<]+)</span>\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern PRESIDENTE_PATTERN = Pattern.compile(
            "<div>\\s*<label>Presidente:\\s*</label>\\s*<span>\\s*([^<]+)</span>\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern VICE_PATTERN = Pattern.compile(
            "<div>\\s*<label>Vice-Presidente:\\s*</label>\\s*<span>\\s*([^<]+)</span>\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern MEMBROS_EFETIVOS_PATTERN = Pattern.compile(
            "<div id=[\"']divTextoMembrosEfetivos[\"'][^>]*>.*?<div class=[\"']texto-conteudo-interno[\"'][^>]*>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern MEMBROS_SUPLENTES_PATTERN = Pattern.compile(
            "<div id=[\"']divTextoMembrosSuplentes[\"'][^>]*>.*?<div class=[\"']texto-conteudo-interno[\"'][^>]*>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final List<String> COMISSOES_SLUGS = List.of(
            "Agricultura",
            "AssistenciaSocial",
            "CienciaeTecnologia",
            "ConstituicaoeJustica",
            "Cooperativismo",
            "Cultura",
            "DefesadoConsumidor",
            "DireitosHumanos",
            "Educacao",
            "Financas",
            "Infraestrutura",
            "PoliticasobreDrogas",
            "ProtecaoaCriancaeaoAdolescente",
            "MeioAmbiente",
            "DosAnimais",
            "Saude",
            "Seguranca",
            "Turismo"
    );

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "ales";
    }

    public record DeputadoBruto(String id, String nome, String slug) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("ales: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Deputado/" + d.slug();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_COMISSOES, "comissoes.html", "text/html", Politica.SEMANAL);
        for (String comSlug : COMISSOES_SLUGS) {
            String urlCom = SITE + "/Comissao/" + comSlug;
            coleta.obterOuVazio(urlCom, "comissoes/" + comSlug + ".html", "text/html", Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("ales: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("ales/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("ales: erro ao criar pasta de blocos", e);
        }

        Map<String, String> idPorNomeNorm = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            idPorNomeNorm.put(Texto.normalizarNome(d.nome()), d.id());
            idPorNomeNorm.put(Texto.normalizarNome(formatarNome(d.nome())), d.id());
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairComissoes(ctx, idPorNomeNorm);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeFormatado = formatarNome(d.nome());
            String slug = d.slug();

            String urlPerfil = SITE + "/Deputado/" + slug;
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            String fotoUrl = null;
            String nomeCivil = null;
            String partido = null;

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                    Matcher mFoto = FOTO_PATTERN.matcher(htmlPerfil);
                    if (mFoto.find()) {
                        String f = mFoto.group(1).trim();
                        fotoUrl = f.startsWith("http") ? f : SITE + (f.startsWith("/") ? f : "/" + f);
                    }
                    Matcher mCivil = NOME_CIVIL_PATTERN.matcher(htmlPerfil);
                    if (mCivil.find()) {
                        nomeCivil = formatarNome(mCivil.group(1).trim());
                    }
                    Matcher mPartido = PARTIDO_PATTERN.matcher(htmlPerfil);
                    if (mPartido.find()) {
                        partido = mPartido.group(1).trim().toUpperCase(Locale.ROOT);
                    }
                } catch (Exception e) {
                    System.err.println("ales: aviso ao ler perfil " + id + ": " + e.getMessage());
                }
            }

            if (partido == null || partido.isBlank()) {
                partido = "S/PARTIDO";
            }

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, "Em exercício", "Titular", LEGISLATURA_20, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALES", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "ales", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<FonteUsada> fontes = new ArrayList<>();
            fontes.add(new FonteUsada(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados != null ? arqDeputados.capturadoEm() : null));
            if (arqPerfil != null) {
                fontes.add(new FonteUsada(FONTE_DETALHES, urlPerfil, arqPerfil.capturadoEm()));
            }

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(id, List.of());
            if (!orgaos.isEmpty()) {
                fontes.add(new FonteUsada(FONTE_DETALHES, URL_COMISSOES, arqDeputados != null ? arqDeputados.capturadoEm() : null));
            }

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(fontes));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("ales/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("ales: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "ales", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("ales: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        List<DeputadoBruto> lista = new ArrayList<>();
        Matcher mSelect = SELECT_DEPUTADOS_PATTERN.matcher(html);
        if (!mSelect.find()) {
            return List.of();
        }

        String selectHtml = mSelect.group(1);
        Matcher mOpt = OPTION_DEPUTADO_PATTERN.matcher(selectHtml);
        Set<String> idsVistos = new HashSet<>();

        while (mOpt.find()) {
            String id = mOpt.group(1).trim();
            if ("0".equals(id)) {
                continue;
            }
            String nomeRaw = decodificarHtml(mOpt.group(2).trim());
            String slug = gerarSlug(nomeRaw);
            if (idsVistos.add(id)) {
                lista.add(new DeputadoBruto(id, nomeRaw, slug));
            }
        }
        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx, Map<String, String> idPorNomeNorm) {
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        for (String comSlug : COMISSOES_SLUGS) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "ales", "comissoes/" + comSlug + ".html");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                String urlOrigem = SITE + "/Comissao/" + comSlug;
                Prov prov = Prov.de(FONTE_DETALHES, urlOrigem, arq);
                Map<String, List<OrgaoOuFrente>> doHtml = extrairComissoesDoHtml(html, comSlug, prov, idPorNomeNorm);
                for (var entry : doHtml.entrySet()) {
                    mapa.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("ales: erro ao extrair comissao " + comSlug + ": " + e.getMessage());
            }
        }
        return mapa;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(
            String html, String comSlug, Prov prov, Map<String, String> idPorNomeNorm) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        String titulo = null;
        Matcher mTitulo = TITULO_COMISSAO_PATTERN.matcher(html);
        if (mTitulo.find()) {
            String bruto = decodificarHtml(mTitulo.group(1).trim());
            titulo = bruto.toLowerCase(Locale.ROOT).startsWith("comiss") ? bruto : "Comissão de " + bruto;
        } else {
            titulo = "Comissão de " + comSlug;
        }

        // Presidente
        Matcher mPres = PRESIDENTE_PATTERN.matcher(html);
        if (mPres.find()) {
            String nomePres = decodificarHtml(mPres.group(1).trim());
            String idPres = idPorNomeNorm.get(Texto.normalizarNome(nomePres));
            if (idPres != null) {
                resultado.computeIfAbsent(idPres, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", titulo, null, "Presidente", INICIO_LEG_20, null, prov));
            }
        }

        // Vice-Presidente
        Matcher mVice = VICE_PATTERN.matcher(html);
        if (mVice.find()) {
            String nomeVice = decodificarHtml(mVice.group(1).trim());
            String idVice = idPorNomeNorm.get(Texto.normalizarNome(nomeVice));
            if (idVice != null) {
                resultado.computeIfAbsent(idVice, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", titulo, null, "Vice-Presidente", INICIO_LEG_20, null, prov));
            }
        }

        // Membros Efetivos
        Matcher mMembros = MEMBROS_EFETIVOS_PATTERN.matcher(html);
        if (mMembros.find()) {
            String bloco = mMembros.group(1);
            String[] linhas = bloco.split("<br\\s*/?>|\\r?\\n");
            for (String linha : linhas) {
                String nome = decodificarHtml(linha.replaceAll("<[^>]+>", "").trim());
                if (!nome.isBlank()) {
                    String id = idPorNomeNorm.get(Texto.normalizarNome(nome));
                    if (id != null) {
                        resultado.computeIfAbsent(id, k -> new ArrayList<>())
                                .add(new OrgaoOuFrente("comissao", titulo, null, "Membro Efetivo", INICIO_LEG_20, null, prov));
                    }
                }
            }
        }

        // Membros Suplentes
        Matcher mSuplentes = MEMBROS_SUPLENTES_PATTERN.matcher(html);
        if (mSuplentes.find()) {
            String bloco = mSuplentes.group(1);
            String[] linhas = bloco.split("<br\\s*/?>|\\r?\\n");
            for (String linha : linhas) {
                String nome = decodificarHtml(linha.replaceAll("<[^>]+>", "").trim());
                if (!nome.isBlank()) {
                    String id = idPorNomeNorm.get(Texto.normalizarNome(nome));
                    if (id != null) {
                        resultado.computeIfAbsent(id, k -> new ArrayList<>())
                                .add(new OrgaoOuFrente("comissao", titulo, null, "Membro Suplente", INICIO_LEG_20, null, prov));
                    }
                }
            }
        }

        return resultado;
    }

    public static String gerarSlug(String nomeBruto) {
        if (nomeBruto == null || nomeBruto.isBlank()) {
            return "";
        }
        String semAcentos = Normalizer.normalize(nomeBruto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static String decodificarHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&#227;", "ã")
                .replace("&#225;", "á")
                .replace("&#233;", "é")
                .replace("&#237;", "í")
                .replace("&#243;", "ó")
                .replace("&#250;", "ú")
                .replace("&#226;", "â")
                .replace("&#234;", "ê")
                .replace("&#244;", "ô")
                .replace("&#231;", "ç")
                .replace("&#193;", "Á")
                .replace("&#201;", "É")
                .replace("&#205;", "Í")
                .replace("&#211;", "Ó")
                .replace("&#218;", "Ú")
                .replace("&#194;", "Â")
                .replace("&#202;", "Ê")
                .replace("&#212;", "Ô")
                .replace("&#195;", "Ã")
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
