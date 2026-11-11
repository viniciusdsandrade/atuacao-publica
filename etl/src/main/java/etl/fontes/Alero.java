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
 * Assembleia Legislativa do Estado de Rondônia (ALERO, ADR-0042).
 * Coleta os deputados estaduais em exercício na 11ª Legislatura (2023–2027, id 11 no sistema)
 * via API REST JSON do SAPL (Sistema de Apoio ao Processo Legislativo do Interlegis),
 * dados de mandato, composição da Mesa Diretora, filiações, biografias e fotografias.
 */
public final class Alero implements Fonte {

    public static final String FONTE_DEPUTADOS = "alero-deputados";
    public static final String FONTE_PARLAMENTARES = "alero-parlamentares";
    public static final String FONTE_MESA_DIRETORA = "alero-mesa-diretora";
    public static final String FONTE_FILIACAO = "alero-filiacao";
    public static final String FONTE_PROPOSICOES = "alero-proposicoes";
    public static final String FONTE_PORTAL = "alero-portal";

    public static final String API = "https://sapl.al.ro.leg.br/api";
    public static final String SITE = "https://sapl.al.ro.leg.br";
    public static final String UF = "RO";

    public static final LocalDate INICIO_LEG_11 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_11 = LocalDate.of(2027, 1, 31);
    public static final int ID_LEGISLATURA_11 = 11;
    public static final int NUMERO_LEGISLATURA = 11;

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
        return "alero";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alero: coleta iniciada");

        // 1. Mandatos da 11ª Legislatura (id 11)
        String urlMandatos = API + "/parlamentares/mandato/?legislatura=" + ID_LEGISLATURA_11 + "&page_size=" + PAGINA + "&format=json";
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
        List<Integer> anos = SaplMateria.anosLegislatura(INICIO_LEG_11, FIM_LEG_11, ctx.hoje());

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

        // Composições por mesa da 11ª legislatura
        if (arqMesas != null && Files.exists(Coleta.caminho(ctx, arqMesas))) {
            JsonNode mesasNode = Json.ler(Coleta.caminho(ctx, arqMesas));
            for (JsonNode m : resultados(mesasNode)) {
                String str = Json.texto(m, "__str__");
                int idMesa = m.path("id").asInt();
                if (idMesa > 0 && (str == null || str.contains("11ª") || str.contains("2023") || str.contains("2024") || str.contains("2025") || str.contains("2026") || str.contains("2027"))) {
                    String urlComp = API + "/parlamentares/composicaomesa/?mesa_diretora=" + idMesa + "&format=json";
                    coleta.obterOuVazio(urlComp, "composicao_mesa/" + idMesa + ".json", JSON, Politica.SEMANAL);
                }
            }
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqMandatos = Coleta.metadados(ctx, nome(), "mandatos.json");
        if (arqMandatos == null || !Files.exists(Coleta.caminho(ctx, arqMandatos))) {
            System.out.println("alero: mandatos.json não encontrado");
            return;
        }

        Prov provMandatos = Prov.de(FONTE_DEPUTADOS, API + "/parlamentares/mandato/?legislatura=" + ID_LEGISLATURA_11, arqMandatos);

        Path pastaBlocos = ctx.normalizado().resolve("alero/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alero: erro ao criar pasta de blocos", e);
        }

        // Carrega mapas de mesas, cargos e tipos de matéria
        Map<Integer, MesaInfo> mesasPorId = carregarMesas(ctx);
        Map<Integer, String> cargosMesaPorId = carregarCargosMesa(ctx);
        Map<Integer, List<OrgaoOuFrente>> orgaosPorParlamentar = carregarMesaDiretora(ctx, mesasPorId, cargosMesaPorId);
        Map<String, String[]> tiposMateria = SaplMateria.carregarTipos(ctx, nome());
        List<Integer> anosLeg = SaplMateria.anosLegislatura(INICIO_LEG_11, FIM_LEG_11, ctx.hoje());

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
            String urlOrigemParl = API + "/parlamentares/parlamentar/" + pid + "/";
            Prov provParl = Prov.de(FONTE_PARLAMENTARES, urlOrigemParl, arqParl);

            String nomeParlamentar = null;
            String nomeCompleto = null;
            String biografia = null;
            String fotoUrl = null;
            boolean ativo = true;

            if (arqParl != null && Files.exists(Coleta.caminho(ctx, arqParl))) {
                JsonNode parlNode = Json.ler(Coleta.caminho(ctx, arqParl));
                nomeParlamentar = Json.texto(parlNode, "nome_parlamentar");
                nomeCompleto = Json.texto(parlNode, "nome_completo");
                ativo = parlNode.hasNonNull("ativo") ? parlNode.get("ativo").asBoolean() : true;

                String bioRaw = Json.texto(parlNode, "biografia");
                if (bioRaw != null && !bioRaw.isBlank()) {
                    String bioLimpa = bioRaw.replaceAll("<[^>]+>", " ")
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

            if (nomeParlamentar != null) {
                nomeParlamentar = limparNomeParlamentar(nomeParlamentar);
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

            Pessoa pessoa = new Pessoa("alero", idStr, nomeParlamentar, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, situacao, emExercicio, links);
            pessoas.add(pessoa);

            List<Mandato> mandatosFormatados = new ArrayList<>();
            for (MandatoInfo mi : listaMandatos) {
                LocalDate ini = mi.inicio() != null ? mi.inicio() : INICIO_LEG_11;
                LocalDate fim = mi.fim() != null ? mi.fim() : FIM_LEG_11;
                String titularidade = mi.titular() ? "Titular" : "Suplente";
                mandatosFormatados.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                        ini, fim, situacao, titularidade, NUMERO_LEGISLATURA, 2022,
                        "Deputado Estadual na 11ª Legislatura (2023–2027)", provMandatos));
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
                throw new RuntimeException("alero: erro ao gravar bloco de " + idStr, e);
            }
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        try {
            Json.gravar(ctx.normalizado().resolve("alero/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
            if (!nomesCivisPorId.isEmpty()) {
                Json.gravar(ctx.normalizado().resolve("alero/nomes-civis.json"), Json.SAIDA.valueToTree(nomesCivisPorId));
            }
        } catch (Exception e) {
            throw new RuntimeException("alero: erro ao gravar pessoas.json", e);
        }
    }

    public static List<MandatoInfo> parseMandatos(JsonNode root) {
        List<MandatoInfo> resultado = new ArrayList<>();
        for (JsonNode m : resultados(root)) {
            int id = m.path("id").asInt();
            int parlamentarId = m.path("parlamentar").asInt();
            boolean titular = m.path("titular").asBoolean(true);
            LocalDate inicio = m.hasNonNull("data_inicio_mandato") ? LocalDate.parse(m.get("data_inicio_mandato").stringValue()) : null;
            LocalDate fim = m.hasNonNull("data_fim_mandato") ? LocalDate.parse(m.get("data_fim_mandato").stringValue()) : null;
            String causaFim = Json.texto(m, "tipo_causa_fim_mandato");
            Integer votos = m.hasNonNull("votos_recebidos") ? m.get("votos_recebidos").asInt() : null;
            resultado.add(new MandatoInfo(id, parlamentarId, titular, inicio, fim, causaFim, votos));
        }
        return resultado;
    }

    public static String limparNomeParlamentar(String nome) {
        if (nome == null || nome.isBlank()) {
            return nome;
        }
        String n = nome.trim();
        if (n.toUpperCase(Locale.ROOT).startsWith("DEPUTADO ")) {
            n = n.substring("DEPUTADO ".length()).trim();
        } else if (n.toUpperCase(Locale.ROOT).startsWith("DEPUTADA ")) {
            n = n.substring("DEPUTADA ".length()).trim();
        }
        return n;
    }

    public static String formatarUrlFoto(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String limpa = url.trim();
        if (limpa.startsWith("http://sapl.al.ro.leg.br/")) {
            limpa = "https://sapl.al.ro.leg.br/" + limpa.substring("http://sapl.al.ro.leg.br/".length());
        } else if (limpa.startsWith("/")) {
            limpa = SITE + limpa;
        } else if (!limpa.startsWith("http://") && !limpa.startsWith("https://")) {
            limpa = SITE + "/" + limpa;
        }
        return limpa;
    }

    private String extrairPartido(Contexto ctx, int pid) {
        Coleta.Arquivo arqFil = Coleta.metadados(ctx, nome(), "filiacoes/" + pid + ".json");
        if (arqFil == null || !Files.exists(Coleta.caminho(ctx, arqFil))) {
            return "S/P";
        }
        try {
            JsonNode filNode = Json.ler(Coleta.caminho(ctx, arqFil));
            List<JsonNode> filiacoes = resultados(filNode);
            // Procura filiação sem data_desfiliacao ou com data mais recente
            for (JsonNode f : filiacoes) {
                if (!f.hasNonNull("data_desfiliacao")) {
                    String str = Json.texto(f, "__str__");
                    if (str != null) {
                        String[] partes = str.split(" - ");
                        if (partes.length >= 2) {
                            return partes[1].trim();
                        }
                    }
                }
            }
            if (!filiacoes.isEmpty()) {
                JsonNode f = filiacoes.get(0);
                String str = Json.texto(f, "__str__");
                if (str != null) {
                    String[] partes = str.split(" - ");
                    if (partes.length >= 2) {
                        return partes[1].trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("alero: aviso ao ler filiação de " + pid + ": " + e.getMessage());
        }
        return "S/P";
    }

    private Map<Integer, MesaInfo> carregarMesas(Contexto ctx) {
        Map<Integer, MesaInfo> mapa = new HashMap<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "mesas.json");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return mapa;
        }
        try {
            JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
            for (JsonNode m : resultados(root)) {
                int id = m.path("id").asInt();
                String desc = Json.texto(m, "descricao");
                String str = Json.texto(m, "__str__");
                LocalDate ini = m.hasNonNull("data_inicio") ? LocalDate.parse(m.get("data_inicio").stringValue()) : null;
                LocalDate fim = m.hasNonNull("data_fim") ? LocalDate.parse(m.get("data_fim").stringValue()) : null;

                // Estima datas pela sessão caso data_inicio seja nula
                if (ini == null && str != null) {
                    if (str.contains("2023")) {
                        ini = LocalDate.of(2023, 2, 1);
                        fim = LocalDate.of(2025, 1, 31);
                    } else if (str.contains("2024")) {
                        ini = LocalDate.of(2023, 2, 1);
                        fim = LocalDate.of(2025, 1, 31);
                    } else if (str.contains("2025")) {
                        ini = LocalDate.of(2025, 2, 1);
                        fim = LocalDate.of(2027, 1, 31);
                    } else if (str.contains("2026") || str.contains("2027")) {
                        ini = LocalDate.of(2025, 2, 1);
                        fim = LocalDate.of(2027, 1, 31);
                    }
                }
                mapa.put(id, new MesaInfo(id, desc != null ? desc : str, ini, fim));
            }
        } catch (Exception e) {
            System.err.println("alero: erro ao carregar mesas: " + e.getMessage());
        }
        return mapa;
    }

    private Map<Integer, String> carregarCargosMesa(Contexto ctx) {
        Map<Integer, String> mapa = new HashMap<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "cargos_mesa.json");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return mapa;
        }
        try {
            JsonNode root = Json.ler(Coleta.caminho(ctx, arq));
            for (JsonNode c : resultados(root)) {
                int id = c.path("id").asInt();
                String desc = Json.texto(c, "descricao");
                if (desc != null) {
                    mapa.put(id, desc.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("alero: erro ao carregar cargos mesa: " + e.getMessage());
        }
        return mapa;
    }

    private Map<Integer, List<OrgaoOuFrente>> carregarMesaDiretora(Contexto ctx, Map<Integer, MesaInfo> mesas,
                                                                   Map<Integer, String> cargosMesa) {
        Map<Integer, List<OrgaoOuFrente>> resultado = new HashMap<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "composicao_mesa.json");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return resultado;
        }

        Prov provMesa = Prov.de(FONTE_MESA_DIRETORA, API + "/parlamentares/composicaomesa/", arq);

        List<JsonNode> todasComposicoes = new ArrayList<>();
        try {
            todasComposicoes.addAll(resultados(Json.ler(Coleta.caminho(ctx, arq))));
        } catch (Exception e) {
            System.err.println("alero: erro ao ler composicao_mesa.json: " + e.getMessage());
        }

        // Também carrega composições salvas individualmente sob composicao_mesa/{idMesa}.json
        for (int idMesa : mesas.keySet()) {
            Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), "composicao_mesa/" + idMesa + ".json");
            if (arqMesa != null && Files.exists(Coleta.caminho(ctx, arqMesa))) {
                try {
                    todasComposicoes.addAll(resultados(Json.ler(Coleta.caminho(ctx, arqMesa))));
                } catch (Exception e) {
                    // ignora
                }
            }
        }

        Set<String> jaVistos = new HashSet<>();
        for (JsonNode comp : todasComposicoes) {
            int pid = comp.path("parlamentar").asInt();
            int mesaId = comp.path("mesa_diretora").asInt();
            int cargoId = comp.path("cargo").asInt();

            if (pid <= 0) {
                continue;
            }

            String chaveVisto = pid + ":" + mesaId + ":" + cargoId;
            if (!jaVistos.add(chaveVisto)) {
                continue;
            }

            String cargo = cargosMesa.get(cargoId);
            if (cargo == null) {
                String str = Json.texto(comp, "__str__");
                if (str != null && str.contains(" - ")) {
                    String[] partes = str.split(" - ");
                    if (partes.length >= 2) {
                        cargo = partes[1].trim();
                    }
                }
            }
            if (cargo == null || cargo.isBlank()) {
                cargo = "Membro da Mesa";
            }

            MesaInfo mesa = mesas.get(mesaId);
            LocalDate inicio = mesa != null && mesa.inicio() != null ? mesa.inicio() : INICIO_LEG_11;
            LocalDate fim = mesa != null && mesa.fim() != null ? mesa.fim() : FIM_LEG_11;

            OrgaoOuFrente orgao = new OrgaoOuFrente("orgao", "Mesa Diretora", null, cargo, inicio, fim, provMesa);
            resultado.computeIfAbsent(pid, k -> new ArrayList<>()).add(orgao);
        }

        // Ordena órgãos por data de início
        for (List<OrgaoOuFrente> lista : resultado.values()) {
            lista.sort(Comparator.comparing(OrgaoOuFrente::inicio, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        return resultado;
    }

    private static List<JsonNode> resultados(JsonNode root) {
        List<JsonNode> lista = new ArrayList<>();
        if (root == null) {
            return lista;
        }
        if (root.isArray()) {
            root.forEach(lista::add);
        } else if (root.has("results") && root.get("results").isArray()) {
            root.get("results").forEach(lista::add);
        }
        return lista;
    }
}
