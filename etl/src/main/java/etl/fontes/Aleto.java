package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Tocantins (ALETO, ADR-0046).
 * Coleta os deputados estaduais em exercício na 10ª Legislatura (2023 a 2027) via API REST JSON
 * do SAPL 3 (Sistema de Apoio ao Processo Legislativo do Interlegis), dados de mandato,
 * composição da Mesa Diretora, biografias, fotografias oficiais e proposições legislativas.
 */
public final class Aleto implements Fonte {

    public static final String FONTE_DEPUTADOS = "aleto-deputados";
    public static final String FONTE_PARLAMENTARES = "aleto-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "aleto-mesa-diretora";
    public static final String FONTE_FILIACAO = "aleto-filiacao";
    public static final String FONTE_PROPOSICOES = "aleto-proposicoes";
    public static final String FONTE_PORTAL = "aleto-portal";

    public static final String API = "https://sapl.al.to.leg.br/api";
    public static final String SITE = "https://sapl.al.to.leg.br";
    public static final String UF = "TO";

    public static final LocalDate INICIO_LEG_10 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_10 = LocalDate.of(2027, 1, 31);
    public static final int ID_LEGISLATURA_10 = 11;
    public static final int NUMERO_LEGISLATURA = 10;

    private static final String JSON = "application/json";
    private static final int PAGINA = 100;

    public record MandatoInfo(int id, int parlamentarId, boolean titular, LocalDate inicio, LocalDate fim,
                              String causaFim, Integer votos) {
    }

    public record MesaInfo(int id, String descricao, LocalDate inicio, LocalDate fim) {
    }

    public record ComposicaoMesaInfo(int parlamentarId, int mesaId, String cargo) {
    }

    @Override
    public String nome() {
        return "aleto";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("aleto: coleta iniciada");

        // 1. Mandatos da 10ª Legislatura (id 11)
        String urlMandatos = API + "/parlamentares/mandato/?legislatura=" + ID_LEGISLATURA_10 + "&page_size=" + PAGINA + "&format=json";
        Coleta.Arquivo arqMandatos = coleta.obter(urlMandatos, "mandatos.json", JSON, Politica.SEMANAL);

        Set<Integer> parlamentarIds = new HashSet<>();
        if (arqMandatos != null && Files.exists(Coleta.caminho(ctx, arqMandatos))) {
            JsonNode mandatosNode = Json.ler(Coleta.caminho(ctx, arqMandatos));
            for (JsonNode m : resultados(mandatosNode)) {
                if (m.hasNonNull("parlamentar")) {
                    parlamentarIds.add(m.get("parlamentar").asInt());
                }
            }
        }

        // 2. Detalhes de cada parlamentar, filiações, autorias e proposições legislativas
        SaplMateria.fetchTipos(coleta, API);
        List<Integer> anos = SaplMateria.anosLegislatura(INICIO_LEG_10, FIM_LEG_10, ctx.hoje());

        for (int pid : parlamentarIds) {
            String urlParl = API + "/parlamentares/parlamentar/" + pid + "/?format=json";
            coleta.obterOuVazio(urlParl, "parlamentares/" + pid + ".json", JSON, Politica.SEMANAL);

            String urlFil = API + "/parlamentares/filiacao/?parlamentar=" + pid + "&format=json";
            coleta.obterOuVazio(urlFil, "filiacoes/" + pid + ".json", JSON, Politica.SEMANAL);

            SaplMateria.fetchAutorEMaterias(ctx, coleta, API, pid, anos);
        }

        // 3. Mesas Diretoras, Cargos e Composição
        Coleta.Arquivo arqMesas = coleta.obterOuVazio(API + "/parlamentares/mesadiretora/?page_size=" + PAGINA + "&format=json", "mesas.json", JSON, Politica.SEMANAL);
        coleta.obterOuVazio(API + "/parlamentares/cargomesa/?page_size=" + PAGINA + "&format=json", "cargos_mesa.json", JSON, Politica.SEMANAL);
        coleta.obterOuVazio(API + "/parlamentares/composicaomesa/?page_size=" + PAGINA + "&format=json", "composicao_mesa.json", JSON, Politica.SEMANAL);

        // Composições por mesa da 10ª legislatura
        if (arqMesas != null && Files.exists(Coleta.caminho(ctx, arqMesas))) {
            JsonNode mesasNode = Json.ler(Coleta.caminho(ctx, arqMesas));
            for (JsonNode m : resultados(mesasNode)) {
                String str = Json.texto(m, "__str__");
                int idMesa = m.path("id").asInt();
                if (str != null && str.contains("10ª") && idMesa > 0) {
                    coleta.obterOuVazio(API + "/parlamentares/composicaomesa/?mesa_diretora=" + idMesa + "&format=json",
                            "composicao_mesa_" + idMesa + ".json", JSON, Politica.SEMANAL);
                }
            }
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqMandatos = Coleta.metadados(ctx, nome(), "mandatos.json");
        if (arqMandatos == null || !Files.exists(Coleta.caminho(ctx, arqMandatos))) {
            System.out.println("aleto: mandatos.json não encontrado");
            return;
        }

        Prov provMandatos = Prov.de(FONTE_DEPUTADOS, API + "/parlamentares/mandato/?legislatura=" + ID_LEGISLATURA_10, arqMandatos);

        Path pastaBlocos = ctx.normalizado().resolve("aleto/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("aleto: erro ao criar pasta de blocos", e);
        }

        // Carrega mapas de mesas, cargos e tipos de matéria
        Map<Integer, MesaInfo> mesasPorId = carregarMesas(ctx);
        Map<Integer, String> cargosMesaPorId = carregarCargosMesa(ctx);
        Map<Integer, List<OrgaoOuFrente>> orgaosPorParlamentar = carregarMesaDiretora(ctx, mesasPorId, cargosMesaPorId);
        Map<String, String[]> tiposMateria = SaplMateria.carregarTipos(ctx, nome());
        List<Integer> anosLeg = SaplMateria.anosLegislatura(INICIO_LEG_10, FIM_LEG_10, ctx.hoje());

        JsonNode mandatosRoot = Json.ler(Coleta.caminho(ctx, arqMandatos));
        List<MandatoInfo> mandatos = parseMandatos(mandatosRoot);

        // Agrupa mandatos por parlamentar
        Map<Integer, List<MandatoInfo>> mandatosPorParlamentar = new LinkedHashMap<>();
        for (MandatoInfo m : mandatos) {
            mandatosPorParlamentar.computeIfAbsent(m.parlamentarId(), k -> new ArrayList<>()).add(m);
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivisPorId = new HashMap<>();

        for (var entry : mandatosPorParlamentar.entrySet()) {
            int pid = entry.getKey();
            String idStr = String.valueOf(pid);
            List<MandatoInfo> listaMandatos = entry.getValue();

            Coleta.Arquivo arqParl = Coleta.metadados(ctx, nome(), "parlamentares/" + pid + ".json");
            String urlParl = API + "/parlamentares/parlamentar/" + pid + "/";
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlParl, arqParl);

            String nomeParlamentar = null;
            String nomeCompleto = null;
            String biografia = null;
            String fotoUrl = null;
            boolean ativo = true;

            if (arqParl != null && Files.exists(Coleta.caminho(ctx, arqParl))) {
                JsonNode parlNode = Json.ler(Coleta.caminho(ctx, arqParl));
                nomeParlamentar = Json.texto(parlNode, "nome_parlamentar");
                nomeCompleto = Json.texto(parlNode, "nome_completo");
                ativo = parlNode.path("ativo").asBoolean(true);

                String bioHtml = Json.texto(parlNode, "biografia");
                if (bioHtml != null && !bioHtml.isBlank()) {
                    String bioLimpa = bioHtml.replaceAll("<[^>]+>", " ")
                            .replaceAll("&nbsp;", " ")
                            .replaceAll("&amp;", "&")
                            .replaceAll("\\s+", " ").trim();
                    bioLimpa = Texto.semEntidades(bioLimpa);
                    if (!bioLimpa.isBlank()) {
                        biografia = Guarda.semCpf(bioLimpa);
                    }
                }

                String fotoRaw = Json.texto(parlNode, "fotografia");
                if (fotoRaw != null && !fotoRaw.isBlank()) {
                    fotoUrl = formatarUrlFoto(fotoRaw);
                }
            }

            if (nomeParlamentar == null || nomeParlamentar.isBlank()) {
                nomeParlamentar = "Deputado " + pid;
            }
            if (nomeCompleto != null && !nomeCompleto.isBlank()) {
                nomesCivisPorId.put(idStr, nomeCompleto);
            }

            // Filiação partidária
            String partido = extrairPartido(ctx, pid);

            String urlFicha = SITE + "/parlamentar/" + pid;
            List<LinkOficial> links = List.of(new LinkOficial("Ficha no SAPL", urlFicha, provParl));

            // Verifica se está em exercício
            boolean emExercicio = ativo && listaMandatos.stream().anyMatch(m -> m.fim() == null || !m.fim().isBefore(ctx.hoje()));
            String situacao = emExercicio ? "Em exercício" : "Fora de exercício";

            Pessoa pessoa = new Pessoa("aleto", idStr, nomeParlamentar, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, situacao, emExercicio, links);
            pessoas.add(pessoa);

            List<Mandato> mandatosFormatados = new ArrayList<>();
            for (MandatoInfo mi : listaMandatos) {
                LocalDate ini = mi.inicio() != null ? mi.inicio() : INICIO_LEG_10;
                LocalDate fim = mi.fim() != null ? mi.fim() : FIM_LEG_10;
                String titularidade = mi.titular() ? "Titular" : "Suplente";
                mandatosFormatados.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                        ini, fim, situacao, titularidade, NUMERO_LEGISLATURA, 2022,
                        "Deputado Estadual na 10ª Legislatura (2023 a 2027)", provMandatos));
            }

            List<OrgaoOuFrente> orgaos = orgaosPorParlamentar.getOrDefault(pid, List.of());

            List<Registros.Proposicao> proposicoes = SaplMateria.normalizarProposicoes(
                    ctx, nome(), FONTE_PROPOSICOES, SITE, pid, anosLeg, tiposMateria, null);

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatosFormatados));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            if (biografia != null) {
                blocosNode.put("biografia", biografia);
            }

            try {
                Path arquivoBloco = pastaBlocos.resolve(idStr + ".json");
                Json.gravar(arquivoBloco, blocosNode);
            } catch (Exception e) {
                throw new RuntimeException("aleto: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("aleto/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("aleto/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("aleto: erro ao gravar pessoas.json", e);
        }
    }

    public static List<MandatoInfo> parseMandatos(JsonNode root) {
        List<MandatoInfo> lista = new ArrayList<>();
        if (root == null) {
            return lista;
        }
        for (JsonNode m : resultados(root)) {
            int id = m.path("id").asInt();
            int parlamentarId = m.path("parlamentar").asInt();
            boolean titular = m.path("titular").asBoolean(true);

            LocalDate inicio = parseData(Json.texto(m, "data_inicio_mandato"));
            LocalDate fim = parseData(Json.texto(m, "data_fim_mandato"));
            String causaFim = Json.texto(m, "tipo_causa_fim_mandato");
            Integer votos = m.hasNonNull("votos_recebidos") ? m.get("votos_recebidos").asInt() : null;

            if (parlamentarId > 0) {
                lista.add(new MandatoInfo(id, parlamentarId, titular, inicio, fim, causaFim, votos));
            }
        }
        return lista;
    }

    private Map<Integer, MesaInfo> carregarMesas(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "mesas.json");
        Map<Integer, MesaInfo> mapa = new HashMap<>();
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return mapa;
        }
        JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
        for (JsonNode m : resultados(root)) {
            int id = m.path("id").asInt();
            String desc = Json.texto(m, "descricao");
            if (desc == null || desc.isBlank()) {
                desc = Json.texto(m, "__str__");
            }
            LocalDate inicio = parseData(Json.texto(m, "data_inicio"));
            LocalDate fim = parseData(Json.texto(m, "data_fim"));
            if (id > 0) {
                mapa.put(id, new MesaInfo(id, desc, inicio, fim));
            }
        }
        return mapa;
    }

    private Map<Integer, String> carregarCargosMesa(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "cargos_mesa.json");
        Map<Integer, String> mapa = new HashMap<>();
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return mapa;
        }
        JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
        for (JsonNode c : resultados(root)) {
            int id = c.path("id").asInt();
            String desc = Json.texto(c, "descricao");
            if (id > 0 && desc != null) {
                mapa.put(id, desc.trim());
            }
        }
        return mapa;
    }

    private Map<Integer, List<OrgaoOuFrente>> carregarMesaDiretora(Contexto ctx, Map<Integer, MesaInfo> mesas, Map<Integer, String> cargos) {
        Map<Integer, List<OrgaoOuFrente>> resultado = new HashMap<>();

        // Arquivo geral e específicos por mesa
        List<String> arquivos = new ArrayList<>();
        arquivos.add("composicao_mesa.json");
        for (int idMesa : mesas.keySet()) {
            arquivos.add("composicao_mesa_" + idMesa + ".json");
        }

        Set<String> vistos = new HashSet<>();

        for (String arqNome : arquivos) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arqNome);
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
            Prov prov = Prov.de(FONTE_MESA_DIRETORA, API + "/parlamentares/composicaomesa/", arq);

            for (JsonNode item : resultados(root)) {
                int pid = item.path("parlamentar").asInt();
                int idMesa = item.path("mesa_diretora").asInt();
                int idCargo = item.path("cargo").asInt();
                String chave = pid + "-" + idMesa + "-" + idCargo;

                if (pid <= 0 || !vistos.add(chave)) {
                    continue;
                }

                String cargoNome = cargos.get(idCargo);
                if (cargoNome == null || cargoNome.isBlank()) {
                    String str = Json.texto(item, "__str__");
                    cargoNome = extrairCargoDaString(str);
                }

                MesaInfo mesa = mesas.get(idMesa);
                LocalDate inicio = mesa != null && mesa.inicio() != null ? mesa.inicio() : INICIO_LEG_10;
                LocalDate fim = mesa != null && mesa.fim() != null ? mesa.fim() : FIM_LEG_10;

                OrgaoOuFrente orgao = new OrgaoOuFrente("orgao", "Mesa Diretora", null,
                        cargoNome != null ? cargoNome : "Membro da Mesa", inicio, fim, prov);

                resultado.computeIfAbsent(pid, k -> new ArrayList<>()).add(orgao);
            }
        }
        return resultado;
    }

    public static String extrairCargoDaString(String str) {
        if (str == null || str.isBlank()) {
            return "Membro da Mesa";
        }
        if (str.contains("-")) {
            String[] partes = str.split("-");
            return partes[partes.length - 1].trim();
        }
        return str.trim();
    }

    private String extrairPartido(Contexto ctx, int pid) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "filiacoes/" + pid + ".json");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return "S/P";
        }
        JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
        for (JsonNode f : resultados(root)) {
            String dataDesfiliacao = Json.texto(f, "data_desfiliacao");
            if (dataDesfiliacao == null || dataDesfiliacao.isBlank()) {
                String partidoStr = Json.texto(f, "partido");
                if (partidoStr != null && !partidoStr.isBlank()) {
                    return formatarSiglaPartido(partidoStr);
                }
                String str = Json.texto(f, "__str__");
                if (str != null && !str.isBlank()) {
                    return formatarSiglaPartido(str);
                }
            }
        }
        return "S/P";
    }

    public static String formatarSiglaPartido(String partidoRaw) {
        if (partidoRaw == null || partidoRaw.isBlank()) {
            return "S/P";
        }
        Matcher m = Pattern.compile("^[0-9]+\\s*-\\s*([A-Za-z0-9_-]+)").matcher(partidoRaw.trim());
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher m2 = Pattern.compile("\\(([^)]+)\\)").matcher(partidoRaw);
        if (m2.find()) {
            return m2.group(1).trim().toUpperCase(Locale.ROOT);
        }
        return partidoRaw.trim().toUpperCase(Locale.ROOT);
    }

    public static String formatarUrlFoto(String fotoRaw) {
        if (fotoRaw == null || fotoRaw.isBlank()) {
            return null;
        }
        String foto = fotoRaw.trim();
        if (foto.startsWith("http://")) {
            foto = "https://" + foto.substring(7);
        } else if (!foto.startsWith("http")) {
            foto = SITE + (foto.startsWith("/") ? "" : "/") + foto;
        }
        return foto;
    }

    private static LocalDate parseData(String txt) {
        if (txt == null || txt.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(txt.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<JsonNode> resultados(JsonNode raiz) {
        if (raiz == null) {
            return List.of();
        }
        JsonNode r = raiz.get("results");
        return r != null && r.isArray() ? Json.lista(r) : List.of();
    }
}
