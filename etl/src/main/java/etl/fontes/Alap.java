package etl.fontes;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Http;
import etl.Json;
import etl.Registros;
import etl.Registros.Despesa;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Amapá (ALAP, ADR-0051).
 * Coleta os 24 deputados estaduais em exercício na 9ª Legislatura (2023 a 2027)
 * a partir do portal institucional oficial, dados de mandato, composição da Mesa Diretora,
 * filiação partidária, fotografias oficiais e a CEAP do portal de transparência.
 */
public final class Alap implements Fonte {

    public static final String FONTE_DEPUTADOS = "alap-deputados";
    public static final String FONTE_PARLAMENTARES = "alap-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alap-mesa-diretora";
    public static final String FONTE_CEAP = "alap-ceap";
    public static final String FONTE_PORTAL = "alap-portal";

    public static final String SITE = "https://www.al.ap.leg.br";
    public static final String URL_LEGISLATURA = "https://www.al.ap.leg.br/pagina.php?pg=exibir_legislatura";
    public static final String URL_MESA_DIRETORA = "https://www.al.ap.leg.br/pagina.php?pg=exibir_mesadiretora";
    public static final String URL_PARLAMENTAR = "https://www.al.ap.leg.br/pagina.php?pg=exibir_parlamentar&iddeputado=";
    public static final String URL_TRANSPARENCIA = "https://www.al.ap.leg.br/transparencia/";
    public static final String URL_CEAP_MESES = URL_TRANSPARENCIA + "mes_ceap_json.php";
    public static final String URL_CEAP_GABINETES = URL_TRANSPARENCIA + "gabinete_ceap_json.php";
    public static final String URL_CEAP_BUSCAR = URL_TRANSPARENCIA + "pagina.php?pg=ceap&acao=buscar";
    public static final String URL_CEAP_DETALHE = URL_TRANSPARENCIA + "ceap_exibir_detalhado.php";
    public static final String UF = "AP";

    public static final LocalDate INICIO_LEG_9 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_9 = LocalDate.of(2027, 1, 31);
    public static final int NUMERO_LEGISLATURA = 9;
    public static final YearMonth INICIO_CEAP = YearMonth.of(2023, 2);
    private static final int CONCORRENCIA_CEAP = 6;

    private static final String HTML = "text/html";

    public record DeputadoCard(String id, String nomeParlamentar, String nomeCompleto, String partido,
                              String fotoUrl, String link) {
    }

    public record MesaMembro(String id, String cargo, String nome) {
    }

    public record GabineteCeap(String id, String nome) {
    }

    public record SubelementoCeap(String idSubelemento, String categoria, String urlDetalhada) {
    }

    private static final Pattern PATTERN_BOX = Pattern.compile(
            "<div[^>]*class=[\"']ls-box[^\"']*[\"'][^>]*>\\s*" +
                    "<div[^>]*class=[\"']box-foto-deputados[\"']>\\s*" +
                    "<a[^>]*href=[\"']pagina\\.php\\?pg=exibir_parlamentar&iddeputado=([0-9]+)[\"'][^>]*onmouseover=[\"']Tip\\('(.*?)'\\s*,.*?<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>\\s*</a>\\s*</div>\\s*" +
                    "<p[^>]*class=[\"']ls-title-5[\"']>\\s*(?:Deputad[ao]\\s+)?([^<]+)\\s*</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_MESA = Pattern.compile(
            "<div[^>]*class=[\"']box-foto-deputados[\"']>\\s*" +
                    "<a[^>]*href=[\"']pagina\\.php\\?pg=exibir_parlamentar&iddeputado=([0-9]+)[\"'].*?</div>\\s*" +
                    "<p[^>]*class=[\"']ls-title-5[\"']>\\s*([^<]+)<br>\\s*<strong>([^<]+)</strong>\\s*</p>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "alap";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alap: coleta iniciada");

        Coleta.Arquivo arqLeg = coleta.obter(URL_LEGISLATURA, "legislatura.html", HTML, Politica.SEMANAL);
        coleta.obterOuVazio(URL_MESA_DIRETORA, "mesa_diretora.html", HTML, Politica.SEMANAL);

        if (arqLeg == null || !Files.exists(Coleta.caminho(ctx, arqLeg))) {
            return;
        }

        String htmlLeg = Texto.lerArquivo(Coleta.caminho(ctx, arqLeg));
        List<DeputadoCard> deputados = parseDeputados(htmlLeg);

        for (DeputadoCard dep : deputados) {
            String urlParl = URL_PARLAMENTAR + dep.id();
            coleta.obterOuVazio(urlParl, "parlamentares/" + dep.id() + ".html", HTML, Politica.SEMANAL);
        }

        coletarCeap(ctx, coleta);
    }

    private void coletarCeap(Contexto ctx, Coleta coleta) throws Exception {
        YearMonth ate = YearMonth.from(ctx.hoje());
        List<YearMonth> mesesColeta = new ArrayList<>();
        for (int ano = INICIO_CEAP.getYear(); ano <= ate.getYear(); ano++) {
            Politica pol = Politica.porAnoSemanal(ano, ctx.hoje());
            Coleta.Arquivo arqMeses = coleta.obterFormularioOpcional(URL_CEAP_MESES,
                    Map.of("ano_verbaB", String.valueOf(ano)), "ceap/meses/" + ano + ".html", HTML, pol);
            if (arqMeses == null || arqMeses.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqMeses))) {
                continue;
            }
            String htmlMeses = lerHtmlPortal(Coleta.caminho(ctx, arqMeses));
            for (int mes : parseMesesCeap(htmlMeses)) {
                YearMonth ym = YearMonth.of(ano, mes);
                if (!ym.isBefore(INICIO_CEAP) && !ym.isAfter(ate)) {
                    mesesColeta.add(ym);
                }
            }
        }

        record TarefaGab(YearMonth ym, Politica pol, GabineteCeap gab) {
        }
        List<TarefaGab> tarefas = new ArrayList<>();
        for (YearMonth ym : mesesColeta) {
            Politica pol = Politica.porAnoSemanal(ym.getYear(), ctx.hoje());
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("ano_verbaB", String.valueOf(ym.getYear()));
            campos.put("mes_verbaB", String.format("%02d", ym.getMonthValue()));
            Coleta.Arquivo arqGabs = coleta.obterFormularioOpcional(URL_CEAP_GABINETES, campos,
                    "ceap/gabinetes/" + ym + ".html", HTML, pol);
            if (arqGabs == null || arqGabs.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqGabs))) {
                continue;
            }
            for (GabineteCeap gab : parseGabinetesCeap(lerHtmlPortal(Coleta.caminho(ctx, arqGabs)))) {
                tarefas.add(new TarefaGab(ym, pol, gab));
            }
        }

        if (tarefas.isEmpty()) {
            return;
        }
        Semaphore vagas = new Semaphore(CONCORRENCIA_CEAP);
        List<Future<?>> futuros = new ArrayList<>(tarefas.size());
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TarefaGab t : tarefas) {
                futuros.add(exec.submit(() -> {
                    vagas.acquire();
                    try {
                        Map<String, String> campos = new LinkedHashMap<>();
                        campos.put("ano_verbaB", String.valueOf(t.ym().getYear()));
                        campos.put("mes_verbaB", String.format("%02d", t.ym().getMonthValue()));
                        campos.put("idgabineteB", t.gab().id());
                        Coleta.Arquivo arqBusca = coleta.obterFormularioOpcional(URL_CEAP_BUSCAR, campos,
                                "ceap/buscar/" + t.ym() + "/" + t.gab().id() + ".html", HTML, t.pol());
                        if (arqBusca == null || arqBusca.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqBusca))) {
                            return null;
                        }
                        String htmlBusca = lerHtmlPortal(Coleta.caminho(ctx, arqBusca));
                        for (SubelementoCeap sub : parseSubelementosCeap(htmlBusca)) {
                            coleta.obterOpcional(sub.urlDetalhada(),
                                    "ceap/detalhe/" + t.ym() + "/" + t.gab().id() + "_" + sub.idSubelemento() + ".html",
                                    HTML, t.pol());
                        }
                        return null;
                    } finally {
                        vagas.release();
                    }
                }));
            }
            for (Future<?> f : futuros) {
                try {
                    f.get();
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
                    throw new RuntimeException("alap: erro ao coletar CEAP", causa);
                }
            }
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqLeg = Coleta.metadados(ctx, nome(), "legislatura.html");
        if (arqLeg == null || !Files.exists(Coleta.caminho(ctx, arqLeg))) {
            System.out.println("alap: legislatura.html não encontrado");
            return;
        }

        Prov provLeg = Prov.de(FONTE_DEPUTADOS, URL_LEGISLATURA, arqLeg);

        Path pastaBlocos = ctx.normalizado().resolve("alap/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alap: erro ao criar pasta de blocos", e);
        }

        String htmlLeg;
        try {
            htmlLeg = Texto.lerArquivo(Coleta.caminho(ctx, arqLeg));
        } catch (Exception e) {
            throw new RuntimeException("alap: erro ao ler legislatura.html", e);
        }

        List<DeputadoCard> deputados = parseDeputados(htmlLeg);

        // Mesa Diretora
        Map<String, String> cargoMesaPorId = new HashMap<>();
        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "mesa_diretora.html");
        Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, URL_MESA_DIRETORA, arqMesa != null ? arqMesa : arqLeg);
        if (arqMesa != null && Files.exists(Coleta.caminho(ctx, arqMesa))) {
            try {
                String htmlMesa = Texto.lerArquivo(Coleta.caminho(ctx, arqMesa));
                for (MesaMembro mm : parseMesa(htmlMesa)) {
                    cargoMesaPorId.put(mm.id(), mm.cargo());
                }
            } catch (Exception e) {
                System.err.println("alap: aviso ao ler mesa_diretora.html: " + e.getMessage());
            }
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();
        ResultadoCeap ceap = normalizarCeap(ctx, deputados);

        for (DeputadoCard d : deputados) {
            String idStr = d.id();
            String urlFicha = URL_PARLAMENTAR + idStr;

            Coleta.Arquivo arqParl = Coleta.metadados(ctx, nome(), "parlamentares/" + idStr + ".html");
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlFicha, arqParl != null ? arqParl : arqLeg);

            String nomeCivil = d.nomeCompleto() != null && !d.nomeCompleto().isBlank() ? d.nomeCompleto() : d.nomeParlamentar();
            nomesCivisPorId.put(idStr, nomeCivil);

            List<LinkOficial> links = List.of(new LinkOficial("Ficha na ALAP", urlFicha, provParl));
            String situacao = "Em exercício";

            Pessoa pessoa = new Pessoa("alap", idStr, d.nomeParlamentar(), "deputado_estadual", "estadual", UF, UF,
                    d.partido(), d.fotoUrl(), situacao, true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_9, FIM_LEG_9, situacao, "Titular", NUMERO_LEGISLATURA, 2022,
                    "Deputado Estadual na 9ª Legislatura (2023–2027)", provLeg));

            List<OrgaoOuFrente> orgaos = new ArrayList<>();
            String papelMesa = cargoMesaPorId.get(idStr);
            if (papelMesa != null) {
                orgaos.add(new OrgaoOuFrente("orgao", "Mesa Diretora", null, papelMesa,
                        INICIO_LEG_9, FIM_LEG_9, provMesa));
            }

            List<Despesa> despesas = ceap.porDeputado().getOrDefault(idStr, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(despesas));
            blocosNode.set("proposicoes", Json.SAIDA.createArrayNode());
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));

            try {
                Path arquivoBloco = pastaBlocos.resolve(idStr + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("alap: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alap/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alap/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("alap: erro ao gravar pessoas.json", e);
        }

        System.out.printf("alap: %d deputados estaduais normalizados; %d despesas CEAP em %d deputado(s)%s%n",
                pessoas.size(), ceap.totalLancamentos(), ceap.deputadosComDespesa(),
                ceap.gabinetesSemVinculo().isEmpty() ? ""
                        : " (gabinetes sem perfil em exercício: " + ceap.gabinetesSemVinculo() + ")");
    }

    public static List<DeputadoCard> parseDeputados(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<DeputadoCard> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_BOX.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            String tip = m.group(2);
            String fotoSrc = m.group(3).trim();
            String nomeTitulo = decodificarHtml(m.group(4).trim());

            if (!vistos.add(id)) {
                continue;
            }

            String nomeParlamentar = nomeTitulo;
            String nomeCompleto = null;
            String partido = "S/P";

            if (tip != null && !tip.isBlank()) {
                Matcher mNomeComp = Pattern.compile("<b>Nome Completo:</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(tip);
                if (mNomeComp.find()) {
                    nomeCompleto = decodificarHtml(mNomeComp.group(1).trim());
                }
                Matcher mPart = Pattern.compile("<b>Partido:</b>\\s*([^<]+)", Pattern.CASE_INSENSITIVE).matcher(tip);
                if (mPart.find()) {
                    String partRaw = decodificarHtml(mPart.group(1).trim());
                    partido = normalizarPartido(partRaw);
                }
            }

            String fotoUrl = formatarUrlFoto(fotoSrc);
            String link = URL_PARLAMENTAR + id;
            lista.add(new DeputadoCard(id, nomeParlamentar, nomeCompleto, partido, fotoUrl, link));
        }
        lista.sort(Comparator.comparing(DeputadoCard::nomeParlamentar));
        return lista;
    }

    public static List<MesaMembro> parseMesa(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<MesaMembro> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = PATTERN_MESA.matcher(html);
        while (m.find()) {
            String id = m.group(1).trim();
            String nome = decodificarHtml(m.group(2).trim());
            String cargo = decodificarHtml(m.group(3).trim());
            if (vistos.add(id)) {
                lista.add(new MesaMembro(id, cargo, nome));
            }
        }
        return lista;
    }

    public static String normalizarPartido(String raw) {
        if (raw == null || raw.isBlank()) {
            return "S/P";
        }
        String p = raw.trim();
        if (p.contains("UNIÃO BRASIL")) {
            return "UNIÃO";
        }
        if (p.contains("REDE SUSTENTABILIDADE")) {
            return "REDE";
        }
        if (p.contains("PARTIDO VERDE")) {
            return "PV";
        }
        return p;
    }

    public static String formatarUrlFoto(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String limpa = src.trim();
        if (limpa.startsWith("http://www.al.ap.gov.br/")) {
            limpa = "https://www.al.ap.leg.br/" + limpa.substring("http://www.al.ap.gov.br/".length());
        } else if (limpa.startsWith("http://www.al.ap.leg.br/")) {
            limpa = "https://www.al.ap.leg.br/" + limpa.substring("http://www.al.ap.leg.br/".length());
        } else if (limpa.startsWith("/")) {
            limpa = SITE + limpa;
        } else if (!limpa.startsWith("http://") && !limpa.startsWith("https://")) {
            limpa = SITE + "/" + limpa;
        }
        return limpa.replace(" ", "%20");
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

    record ResultadoCeap(Map<String, List<Despesa>> porDeputado, int totalLancamentos, int deputadosComDespesa,
                         Set<String> gabinetesSemVinculo) {
    }

    private static ResultadoCeap normalizarCeap(Contexto ctx, List<DeputadoCard> deputados) {
        Map<String, List<Despesa>> porDeputado = new HashMap<>();
        Set<String> semVinculo = new java.util.TreeSet<>();
        int lancamentos = 0;

        YearMonth ate = YearMonth.from(ctx.hoje());
        for (YearMonth ym = INICIO_CEAP; !ym.isAfter(ate); ym = ym.plusMonths(1)) {
            Coleta.Arquivo arqGabs = Coleta.metadados(ctx, "alap", "ceap/gabinetes/" + ym + ".html");
            if (arqGabs == null || arqGabs.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqGabs))) {
                continue;
            }
            List<GabineteCeap> gabs;
            try {
                gabs = parseGabinetesCeap(lerHtmlPortal(Coleta.caminho(ctx, arqGabs)));
            } catch (IOException e) {
                System.err.println("alap: aviso ao ler ceap/gabinetes/" + ym + ".html: " + e.getMessage());
                continue;
            }
            for (GabineteCeap gab : gabs) {
                DeputadoCard dep = resolverGabinete(gab, deputados);
                if (dep == null) {
                    semVinculo.add(gab.nome());
                    continue;
                }
                Coleta.Arquivo arqBusca = Coleta.metadados(ctx, "alap",
                        "ceap/buscar/" + ym + "/" + gab.id() + ".html");
                if (arqBusca == null || arqBusca.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqBusca))) {
                    continue;
                }
                List<SubelementoCeap> subs;
                try {
                    subs = parseSubelementosCeap(lerHtmlPortal(Coleta.caminho(ctx, arqBusca)));
                } catch (IOException e) {
                    continue;
                }
                for (SubelementoCeap sub : subs) {
                    Coleta.Arquivo arqDet = Coleta.metadados(ctx, "alap",
                            "ceap/detalhe/" + ym + "/" + gab.id() + "_" + sub.idSubelemento() + ".html");
                    if (arqDet == null || arqDet.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arqDet))) {
                        continue;
                    }
                    try {
                        String htmlDet = lerHtmlPortal(Coleta.caminho(ctx, arqDet));
                        Prov prov = Prov.de(FONTE_CEAP, arqDet.url(), arqDet);
                        List<Despesa> itens = parseDetalheCeap(htmlDet, ym.getYear(), ym.getMonthValue(),
                                sub.categoria(), arqDet.url(), prov);
                        if (!itens.isEmpty()) {
                            porDeputado.computeIfAbsent(dep.id(), k -> new ArrayList<>()).addAll(itens);
                            lancamentos += itens.size();
                        }
                    } catch (IOException e) {
                        System.err.println("alap: aviso ao ler detalhe CEAP de " + dep.nomeParlamentar() + ": " + e.getMessage());
                    }
                }
            }
        }

        for (List<Despesa> lista : porDeputado.values()) {
            lista.sort(Comparator.comparingInt(Despesa::ano).thenComparingInt(Despesa::mes).reversed()
                    .thenComparing(Despesa::valor, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(d -> d.fornecedorNome() == null ? "" : d.fornecedorNome()));
        }
        return new ResultadoCeap(porDeputado, lancamentos, porDeputado.size(), semVinculo);
    }

    static List<Integer> parseMesesCeap(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<Integer> meses = new ArrayList<>();
        Matcher m = Pattern.compile("<option[^>]*value=[\"'](\\d{1,2})[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            int mes = Integer.parseInt(m.group(1));
            if (mes >= 1 && mes <= 12 && !meses.contains(mes)) {
                meses.add(mes);
            }
        }
        return meses;
    }

    static List<GabineteCeap> parseGabinetesCeap(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<GabineteCeap> lista = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        Matcher m = Pattern.compile("<option[^>]*value=[\"'](\\d+)[\"'][^>]*>([^<]+)</option>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String id = m.group(1).strip();
            String bruto = limparTextoPortal(m.group(2));
            if (bruto != null && vistos.add(id)) {
                lista.add(new GabineteCeap(id, bruto));
            }
        }
        return lista;
    }

    static List<SubelementoCeap> parseSubelementosCeap(String htmlBusca) {
        if (htmlBusca == null || htmlBusca.isBlank()) {
            return List.of();
        }
        Map<String, SubelementoCeap> porId = new LinkedHashMap<>();
        Matcher m = Pattern.compile("href=[\"'](ceap_exibir_detalhado\\.php\\?[^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(htmlBusca);
        while (m.find()) {
            String hrefDecod = limparTextoPortal(m.group(1));
            if (hrefDecod == null) {
                continue;
            }
            int inter = hrefDecod.indexOf('?');
            if (inter < 0) {
                continue;
            }
            String query = hrefDecod.substring(inter + 1);
            Map<String, String> params = new LinkedHashMap<>();
            // Parametros conhecidos evitam quebra por '&' dentro de razão social ou subelemento.
            String[] partes = query.split("&(?=(?:idgabineteB|nome_gabineteB|mes_verbaB|ano_verbaB|cod_despesa|idsubelemento|cod_subelemento|nome_subelemento)=)");
            for (String p : partes) {
                int eq = p.indexOf('=');
                if (eq > 0) {
                    params.put(p.substring(0, eq).strip(), p.substring(eq + 1).strip());
                }
            }
            String idSub = params.getOrDefault("idsubelemento", "");
            if (idSub.isBlank()) {
                idSub = (params.getOrDefault("cod_despesa", "") + "_" + params.getOrDefault("cod_subelemento", ""))
                        .replaceAll("[^A-Za-z0-9_-]", "");
            }
            if (idSub.isBlank() || porId.containsKey(idSub)) {
                continue;
            }
            StringJoiner sj = new StringJoiner("&");
            for (var e : params.entrySet()) {
                sj.add(URLEncoder.encode(e.getKey(), StandardCharsets.ISO_8859_1) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.ISO_8859_1).replace("+", "%20"));
            }
            String categoria = Texto.vazioParaNulo(params.get("nome_subelemento"));
            porId.put(idSub, new SubelementoCeap(idSub, categoria, URL_CEAP_DETALHE + "?" + sj));
        }
        return List.copyOf(porId.values());
    }

    static List<Despesa> parseDetalheCeap(String html, int ano, int mes, String categoriaFallback,
            String documentoUrl, Prov prov) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String categoria = extrairCategoriaDetalhe(html);
        if (categoria == null) {
            categoria = categoriaFallback != null ? categoriaFallback : "Verba indenizatória (CEAP)";
        }
        List<Despesa> despesas = new ArrayList<>();
        Matcher trs = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        while (trs.find()) {
            String row = trs.group(1);
            List<String> cols = new ArrayList<>();
            Matcher tds = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(row);
            while (tds.find()) {
                String texto = limparTextoPortal(tds.group(1).replaceAll("<[^>]+>", " "));
                cols.add(texto == null ? "" : texto);
            }
            if (cols.size() < 4) {
                continue;
            }
            String empRaw = cols.get(0);
            String nfRaw = cols.get(2);
            BigDecimal valor = Texto.valor(cols.get(3));
            if (valor == null || empRaw.equalsIgnoreCase("Empresa") || empRaw.equalsIgnoreCase("TOTAL")) {
                continue;
            }
            String cnpj = null;
            String fornecedor = empRaw;
            Matcher mDoc = Pattern.compile("^\\s*([0-9./-]+)\\s*-\\s*(.+)$").matcher(empRaw);
            if (mDoc.matches()) {
                cnpj = Texto.cnpj(mDoc.group(1));
                fornecedor = mDoc.group(2);
            }
            fornecedor = Guarda.sanitizar(fornecedor);
            String docNum = Texto.vazioParaNulo(nfRaw);
            if (docNum != null && docNum.matches("(?i)s/?n\\.?|sem\\s*n[uú]mero|-+")) {
                docNum = null;
            }
            despesas.add(new Despesa(null, ano, mes, categoria, fornecedor, cnpj, valor, docNum, documentoUrl, prov));
        }
        return despesas;
    }

    static DeputadoCard resolverGabinete(GabineteCeap gab, List<DeputadoCard> deputados) {
        String semPartido = gab.nome().replaceFirst("\\s*-\\s*[A-Za-zÀ-ÿ0-9/ .]+$", "").strip();
        String norm = Texto.normalizarNome(semPartido);
        if (norm == null || norm.isBlank()) {
            return null;
        }
        for (DeputadoCard d : deputados) {
            if (norm.equals(Texto.normalizarNome(d.nomeParlamentar()))
                    || (d.nomeCompleto() != null && norm.equals(Texto.normalizarNome(d.nomeCompleto())))) {
                return d;
            }
        }
        String[] tokensGab = norm.split(" ");
        DeputadoCard unico = null;
        for (DeputadoCard d : deputados) {
            String civil = Texto.normalizarNome(d.nomeCompleto());
            if (civil == null || tokensGab.length < 2) {
                continue;
            }
            if (contemTokensEmOrdem(civil.split(" "), tokensGab)) {
                if (unico != null) {
                    return null;
                }
                unico = d;
            }
        }
        return unico;
    }

    private static boolean contemTokensEmOrdem(String[] frase, String[] busca) {
        int i = 0;
        for (String token : frase) {
            if (token.equals(busca[i])) {
                i++;
                if (i == busca.length) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String extrairCategoriaDetalhe(String html) {
        Matcher m = Pattern.compile("\\b\\d{4}\\.\\d{2}\\.\\d{2}\\s*-\\s*([^<\\r\\n]+)").matcher(html);
        if (!m.find()) {
            return null;
        }
        return limparTextoPortal(m.group(1));
    }

    private static String lerHtmlPortal(Path arquivo) throws IOException {
        byte[] bytes;
        if (arquivo.getFileName().toString().endsWith(".gz")) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(arquivo), 1 << 16)) {
                bytes = in.readAllBytes();
            }
        } else {
            bytes = Files.readAllBytes(arquivo);
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    static String limparTextoPortal(String bruto) {
        if (bruto == null) {
            return null;
        }
        // Consertamos UTF-8 lido como latin-1 palavra a palavra para preservar símbolos nativos em latin-1 (ex.: "Nº").
        String[] partes = bruto.split("(?<=\\s)|(?=\\s)");
        StringBuilder sb = new StringBuilder(bruto.length());
        for (String parte : partes) {
            sb.append(repararUtf8Token(parte));
        }
        return Texto.semEntidades(sb.toString());
    }

    private static String repararUtf8Token(String token) {
        String atual = token;
        CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        // Alguns endpoints da ALAP servem UTF-8 duplo (bytes UTF-8 re-encodados de latin-1 para UTF-8).
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            byte[] bytes;
            try {
                ByteBuffer bb = encoder.reset().encode(CharBuffer.wrap(atual));
                bytes = new byte[bb.remaining()];
                bb.get(bytes);
            } catch (CharacterCodingException e) {
                return atual;
            }
            boolean temAlto = false;
            for (byte b : bytes) {
                if ((b & 0x80) != 0) {
                    temAlto = true;
                    break;
                }
            }
            if (!temAlto) {
                return atual;
            }
            try {
                String decodificado = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
                if (decodificado.equals(atual)) {
                    return atual;
                }
                atual = decodificado;
            } catch (CharacterCodingException e) {
                return atual;
            }
        }
        return atual;
    }
}
