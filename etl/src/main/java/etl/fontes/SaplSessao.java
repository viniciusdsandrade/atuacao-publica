package etl.fontes;

import java.io.IOException;
import java.io.OutputStream;
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
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Json;
import etl.Registros.Evento;
import etl.Registros.Participacao;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Coleta e normalização de sessões plenárias, presenças e votações nominais para assembleias em SAPL (ADR-0063).
 * Remove os campos {@code ip} e {@code user} de {@code votoparlamentar} antes de gravar no cache bruto e mantém
 * {@code sem_registro = null} porque a API registra apenas votos individuais e presenças efetivas.
 */
public final class SaplSessao {

    private static final String JSON = "application/json";
    private static final int PAGINA = 100;
    private static final int LIMITE_DE_PAGINAS = 300;
    private static final int LIMITE_DE_TIPOS = 10;
    private static final Set<String> VOTOS_REGISTRADOS = Set.of("Sim", "Não", "Abstenção", "Obstrução");

    private static final Pattern INSTANTE = Pattern.compile("^([0-9]{4}-[0-9]{2}-[0-9]{2}(?:T[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)");
    private static final Pattern PREFIXO_ITEM_VOTO = Pattern.compile(
            "^(?:Votação:\\s*)?(?:(?:Ordem:\\s*Ordem do Dia/Expediente|Ordem do Dia|Ordem|Expediente):\\s*)?(?:\\d+\\s*-\\s*)?");
    private static final Pattern SUFIXO_SESSAO_NA_MATERIA = Pattern.compile(
            "\\s+em\\s+(?:\\d+[ªº]\\s+.+|(?:Sessão|Reunião|Ordinária|Extraordinária|Solene|Especial|Deliberativa|Preparatória).*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TIPO_DA_SESSAO_STR = Pattern.compile(
            "^\\d+[ªº]\\s+(.+?)\\s+da\\s+\\d+[ªº]\\s+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private SaplSessao() {
    }

    public record SessaoInfo(int id, LocalDate data, String hora, String tipoNome, String titulo) {
    }

    public record DadosNormalizados(
            Map<Integer, List<Voto>> votosPorParlamentar,
            Map<Integer, List<Participacao>> participacoesPorParlamentar,
            Map<Integer, List<Evento>> eventosPorParlamentar,
            Map<Integer, String> partidosPorParlamentar) {

        public List<Voto> votos(int pid) {
            return votosPorParlamentar.getOrDefault(pid, List.of());
        }

        public List<Participacao> participacoes(int pid) {
            return participacoesPorParlamentar.getOrDefault(pid, List.of());
        }

        public List<Evento> eventos(int pid) {
            return eventosPorParlamentar.getOrDefault(pid, List.of());
        }

        public String partido(int pid) {
            return partidosPorParlamentar.getOrDefault(pid, "S/P");
        }
    }

    public static void fetch(
            Contexto ctx, Coleta coleta, String apiUrl, LocalDate inicioLegislatura, List<Integer> anos)
            throws IOException, InterruptedException {
        for (int p = 1; p <= LIMITE_DE_TIPOS; p++) {
            String url = apiUrl + "/sessao/tiposessaoplenaria/?page_size=" + PAGINA + "&page=" + p + "&format=json";
            Coleta.Arquivo arq = coleta.obterOuVazio(url, "sessoes/tipos-p" + p + ".json", JSON, Politica.SEMANAL);
            if (coleta.orcamentoExaurido() || arq == null || arq.bytes() == 0
                    || proximaPagina(ctx, arq) == null) {
                break;
            }
        }

        int minSessaoId = Integer.MAX_VALUE;
        for (int ano : anos) {
            Politica politica = Politica.porAnoSemanalEmLista(ano, ctx.hoje());
            for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                String caminho = "sessoes/" + ano + "-p" + p + ".json";
                String url = apiUrl + "/sessao/sessaoplenaria/?data_inicio__year=" + ano
                        + "&page_size=" + PAGINA + "&page=" + p + "&format=json";
                Coleta.Arquivo arq = coleta.obterOpcional(url, caminho, JSON, politica);
                if (arq != null && Files.exists(Coleta.caminho(ctx, arq))) {
                    JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
                    for (JsonNode s : resultados(raiz)) {
                        int id = s.path("id").asInt();
                        LocalDate data = Sapl.data(Json.texto(s, "data_inicio"));
                        if (id > 0 && data != null && !data.isBefore(inicioLegislatura) && !data.isAfter(ctx.hoje())
                                && id < minSessaoId) {
                            minSessaoId = id;
                        }
                    }
                    if (coleta.orcamentoExaurido() || Sapl.proxima(raiz) == null) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        if (minSessaoId > 0 && minSessaoId < Integer.MAX_VALUE && !coleta.orcamentoExaurido()) {
            for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                String caminho = "presencas/p" + p + ".json";
                String url = apiUrl + "/sessao/sessaoplenariapresenca/?sessao_plenaria__gte=" + minSessaoId
                        + "&page_size=" + PAGINA + "&page=" + p + "&format=json";
                Coleta.Arquivo arq = coleta.obterOpcional(url, caminho, JSON, Politica.SEMANAL_EM_LISTA);
                if (coleta.orcamentoExaurido() || arq == null || proximaPagina(ctx, arq) == null) {
                    break;
                }
            }
        }

        for (int ano : anos) {
            if (coleta.orcamentoExaurido()) {
                break;
            }
            Politica politica = Politica.porAnoSemanalEmLista(ano, ctx.hoje());
            for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                String caminho = "votos/" + ano + "-p" + p + ".json";
                String url = apiUrl + "/sessao/votoparlamentar/?data_hora__year=" + ano
                        + "&page_size=" + PAGINA + "&page=" + p + "&format=json";
                Coleta.Arquivo arq = coleta.obterDerivado(url, caminho, JSON, politica, SaplSessao::derivarSemIp);
                if (coleta.orcamentoExaurido() || arq == null || proximaPagina(ctx, arq) == null) {
                    break;
                }
            }
        }
    }

    static void derivarSemIp(Path baixado, Path destino) throws IOException {
        JsonNode raiz = Json.ler(baixado);
        if (raiz instanceof ObjectNode && raiz.path("results") instanceof ArrayNode resultados) {
            for (JsonNode item : resultados) {
                if (item instanceof ObjectNode registro) {
                    registro.remove("ip");
                    registro.remove("user");
                }
            }
        }
        try (OutputStream out = Files.newOutputStream(destino)) {
            Json.SAIDA.writeValue(out, raiz);
        }
    }

    public static List<Sapl.Filiacao> filiacoesDoParlamentar(Contexto ctx, String fonte, int pid) {
        Coleta.Arquivo arqFil = Coleta.metadados(ctx, fonte, "filiacoes/" + pid + ".json");
        if (arqFil == null || !Files.exists(Coleta.caminho(ctx, arqFil))) {
            return List.of();
        }
        try {
            return Sapl.filiacoes(Json.ler(Coleta.caminho(ctx, arqFil)));
        } catch (Exception e) {
            return List.of();
        }
    }

    public static DadosNormalizados normalize(
            Contexto ctx,
            String fonte,
            String apiUrl,
            String fonteVotacoes,
            String fontePresenca,
            LocalDate inicioLegislatura,
            List<Integer> anos,
            Set<Integer> parlamentarIds,
            IntFunction<String> resolvedorPartido) {
        Map<Integer, List<Sapl.Filiacao>> filiacoes = new HashMap<>();
        Map<Integer, String> partidos = new HashMap<>();
        for (int pid : parlamentarIds) {
            filiacoes.put(pid, filiacoesDoParlamentar(ctx, fonte, pid));
            partidos.put(pid, resolvedorPartido.apply(pid));
        }
        return normalize(ctx, fonte, apiUrl, fonteVotacoes, fontePresenca, inicioLegislatura, anos, filiacoes, partidos);
    }

    public static DadosNormalizados normalize(
            Contexto ctx,
            String fonte,
            String apiUrl,
            String fonteVotacoes,
            String fontePresenca,
            LocalDate inicioLegislatura,
            List<Integer> anos,
            Map<Integer, List<Sapl.Filiacao>> filiacoesPorParlamentar,
            Map<Integer, String> partidoFallbackPorParlamentar) {
        Map<Integer, String> tipos = carregarTipos(ctx, fonte);
        Map<Integer, SessaoInfo> sessoes = carregarSessoes(ctx, fonte, inicioLegislatura, anos, tipos);
        Map<Integer, List<Evento>> eventos = carregarPresencas(ctx, fonte, apiUrl, fontePresenca, sessoes);

        Map<Integer, List<Voto>> votosPorParlamentar = new HashMap<>();
        Map<Integer, List<Participacao>> participacoesPorParlamentar = new HashMap<>();
        normalizarVotos(ctx, fonte, apiUrl, fonteVotacoes, inicioLegislatura, anos,
                filiacoesPorParlamentar, partidoFallbackPorParlamentar, votosPorParlamentar, participacoesPorParlamentar);

        return new DadosNormalizados(votosPorParlamentar, participacoesPorParlamentar, eventos, partidoFallbackPorParlamentar);
    }

    private static Map<Integer, String> carregarTipos(Contexto ctx, String fonte) {
        Map<Integer, String> tipos = new HashMap<>();
        for (int p = 1; p <= LIMITE_DE_TIPOS; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, fonte, "sessoes/tipos-p" + p + ".json");
            if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
            for (JsonNode t : resultados(raiz)) {
                int id = t.path("id").asInt();
                String nome = Json.texto(t, "nome");
                if (id > 0 && nome != null && !nome.isBlank()) {
                    tipos.put(id, nome.strip());
                }
            }
            if (Sapl.proxima(raiz) == null) {
                break;
            }
        }
        return tipos;
    }

    private static Map<Integer, SessaoInfo> carregarSessoes(
            Contexto ctx, String fonte, LocalDate inicioLegislatura, List<Integer> anos, Map<Integer, String> tipos) {
        Map<Integer, SessaoInfo> sessoes = new HashMap<>();
        for (int ano : anos) {
            for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, fonte, "sessoes/" + ano + "-p" + p + ".json");
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
                for (JsonNode s : resultados(raiz)) {
                    int id = s.path("id").asInt();
                    LocalDate data = Sapl.data(Json.texto(s, "data_inicio"));
                    if (id <= 0 || data == null || data.isBefore(inicioLegislatura) || data.isAfter(ctx.hoje())) {
                        continue;
                    }
                    String horaRaw = Json.texto(s, "hora_inicio");
                    String hora = horaRaw != null && horaRaw.length() >= 5 && horaRaw.substring(0, 5).matches("\\d{2}:\\d{2}")
                            ? horaRaw.substring(0, 5) : null;
                    String str = Json.texto(s, "__str__");
                    Integer tipoId = Json.inteiro(s, "tipo");
                    String tipoNome = normalizarTipoSessao(tipoId != null ? tipos.get(tipoId) : null, str);
                    Integer numero = Json.inteiro(s, "numero");
                    String titulo = str != null && !str.isBlank()
                            ? str.strip() : (numero == null ? tipoNome : numero + "ª " + tipoNome);
                    sessoes.putIfAbsent(id, new SessaoInfo(id, data, hora, tipoNome, titulo));
                }
                if (Sapl.proxima(raiz) == null) {
                    break;
                }
            }
        }
        return sessoes;
    }

    private static Map<Integer, List<Evento>> carregarPresencas(
            Contexto ctx, String fonte, String apiUrl, String fontePresenca, Map<Integer, SessaoInfo> sessoes) {
        Map<Integer, List<Evento>> eventosPorParlamentar = new HashMap<>();
        if (sessoes.isEmpty()) {
            return eventosPorParlamentar;
        }
        int minSessaoId = sessoes.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
        if (minSessaoId <= 0) {
            return eventosPorParlamentar;
        }
        Coleta.Arquivo arqPrincipal = Coleta.metadados(ctx, fonte, "presencas/p1.json");
        if (arqPrincipal == null || !Files.exists(Coleta.caminho(ctx, arqPrincipal))) {
            return eventosPorParlamentar;
        }
        String urlPresenca = arqPrincipal.url() != null
                ? arqPrincipal.url()
                : apiUrl + "/sessao/sessaoplenariapresenca/?sessao_plenaria__gte=" + minSessaoId;
        Prov prov = Prov.de(fontePresenca, urlPresenca, arqPrincipal);
        Set<String> vistos = new HashSet<>();
        for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, fonte, "presencas/p" + p + ".json");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
            for (JsonNode pres : resultados(raiz)) {
                Integer pid = Json.inteiro(pres, "parlamentar");
                Integer sid = Json.inteiro(pres, "sessao_plenaria");
                if (pid == null || sid == null) {
                    continue;
                }
                SessaoInfo sessao = sessoes.get(sid);
                if (sessao == null || !vistos.add(pid + ":" + sid)) {
                    continue;
                }
                String instante = sessao.hora() != null ? sessao.data() + "T" + sessao.hora() : sessao.data().toString();
                eventosPorParlamentar.computeIfAbsent(pid, k -> new ArrayList<>())
                        .add(new Evento("sessao-" + sessao.id(), instante, sessao.tipoNome(), sessao.titulo(),
                                "Plenário", null, "registrada", prov));
            }
            if (Sapl.proxima(raiz) == null) {
                break;
            }
        }
        for (List<Evento> lista : eventosPorParlamentar.values()) {
            lista.sort(Comparator.comparing(Evento::data).reversed());
        }
        return eventosPorParlamentar;
    }

    private static void normalizarVotos(
            Contexto ctx,
            String fonte,
            String apiUrl,
            String fonteVotacoes,
            LocalDate inicioLegislatura,
            List<Integer> anos,
            Map<Integer, List<Sapl.Filiacao>> filiacoesPorParlamentar,
            Map<Integer, String> partidoFallbackPorParlamentar,
            Map<Integer, List<Voto>> votosPorParlamentar,
            Map<Integer, List<Participacao>> participacoesPorParlamentar) {
        // Deduplica por votacao_id por parlamentar e ano para evitar desvio entre páginas na coleta semanal.
        Map<Integer, Map<String, Voto>> votosPorPidEId = new HashMap<>();
        Map<Integer, Map<Integer, Prov>> provPorPidEAno = new HashMap<>();

        for (int ano : anos) {
            Coleta.Arquivo arqPrincipal = Coleta.metadados(ctx, fonte, "votos/" + ano + "-p1.json");
            if (arqPrincipal == null || !Files.exists(Coleta.caminho(ctx, arqPrincipal))) {
                continue;
            }
            Prov provAno = Prov.de(fonteVotacoes, apiUrl + "/sessao/votoparlamentar/?data_hora__year=" + ano, arqPrincipal);
            for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, fonte, "votos/" + ano + "-p" + p + ".json");
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
                for (JsonNode v : resultados(raiz)) {
                    Integer pid = Json.inteiro(v, "parlamentar");
                    String dataHora = instanteSemFuso(Json.texto(v, "data_hora"));
                    LocalDate dia = Sapl.data(dataHora);
                    if (pid == null || dataHora == null || dia == null
                            || dia.isBefore(inicioLegislatura) || dia.isAfter(ctx.hoje())) {
                        continue;
                    }
                    String votacaoId = v.hasNonNull("votacao")
                            ? String.valueOf(v.get("votacao").asInt()) : "voto-" + v.path("id").asInt();
                    String votoTexto = Texto.vazioParaNulo(Json.texto(v, "voto"));
                    String[] resumo = parseResumoVoto(Json.texto(v, "__str__"));
                    String partidoData = Sapl.partidoEm(filiacoesPorParlamentar.getOrDefault(pid, List.of()), dia);
                    if (partidoData == null) {
                        String fallback = partidoFallbackPorParlamentar.get(pid);
                        partidoData = fallback != null && !"S/P".equals(fallback) ? fallback : null;
                    }
                    Voto voto = new Voto(votacaoId, dataHora, "PLEN", resumo[0], resumo[1], votoTexto,
                            partidoData, null, null, null, provAno);
                    votosPorPidEId.computeIfAbsent(pid, k -> new LinkedHashMap<>()).put(votacaoId, voto);
                    provPorPidEAno.computeIfAbsent(pid, k -> new TreeMap<>()).putIfAbsent(dia.getYear(), provAno);
                }
                if (Sapl.proxima(raiz) == null) {
                    break;
                }
            }
        }

        for (var entry : votosPorPidEId.entrySet()) {
            int pid = entry.getKey();
            List<Voto> votos = new ArrayList<>(entry.getValue().values());
            votos.sort(Comparator.comparing(Voto::data).reversed());
            votosPorParlamentar.put(pid, votos);

            Map<Integer, int[]> totaisPorAno = new TreeMap<>(Comparator.reverseOrder());
            Map<Integer, Map<String, Integer>> porTipoAno = new HashMap<>();
            for (Voto voto : votos) {
                int ano = Integer.parseInt(voto.data().substring(0, 4));
                int[] n = totaisPorAno.computeIfAbsent(ano, k -> new int[2]);
                n[0]++;
                if (voto.voto() != null && VOTOS_REGISTRADOS.contains(voto.voto())) {
                    n[1]++;
                }
                String chaveTipo = voto.voto() != null ? voto.voto() : "Não informado";
                porTipoAno.computeIfAbsent(ano, k -> new TreeMap<>()).merge(chaveTipo, 1, Integer::sum);
            }
            List<Participacao> participacoes = new ArrayList<>();
            Map<Integer, Prov> provs = provPorPidEAno.getOrDefault(pid, Map.of());
            for (var item : totaisPorAno.entrySet()) {
                int ano = item.getKey();
                int[] n = item.getValue();
                Prov prov = provs.get(ano);
                if (prov != null) {
                    participacoes.add(new Participacao(String.valueOf(ano), n[0], n[1], null,
                            porTipoAno.get(ano), prov));
                }
            }
            participacoesPorParlamentar.put(pid, participacoes);
        }
    }

    static String instanteSemFuso(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = INSTANTE.matcher(texto.strip());
        return m.find() ? m.group(1) : null;
    }

    /** Extrai proposição [0] e resultado [1] da string padrão de votação do SAPL. */
    static String[] parseResumoVoto(String texto) {
        if (texto == null || texto.isBlank()) {
            return new String[] {null, null};
        }
        String s = texto.strip();
        int idxParl = s.lastIndexOf(" - Parlamentar:");
        if (idxParl > 0) {
            s = s.substring(0, idxParl).strip();
        }
        String materiaExplicita = null;
        int idxMat = s.lastIndexOf(" - Matéria:");
        if (idxMat > 0) {
            materiaExplicita = Texto.vazioParaNulo(s.substring(idxMat + " - Matéria:".length()));
            s = s.substring(0, idxMat).strip();
        }
        String resultado = null;
        int idxVot = s.lastIndexOf(" - Votação:");
        if (idxVot > 0) {
            resultado = Texto.vazioParaNulo(s.substring(idxVot + " - Votação:".length()));
            s = s.substring(0, idxVot).strip();
        }
        String proposicao = materiaExplicita;
        if (proposicao == null && !s.isBlank()) {
            String semPrefixo = PREFIXO_ITEM_VOTO.matcher(s).replaceFirst("").strip();
            String limpa = SUFIXO_SESSAO_NA_MATERIA.matcher(semPrefixo).replaceFirst("").strip();
            proposicao = Texto.vazioParaNulo(limpa);
        }
        String descricao = resultado != null ? "Resultado: " + resultado : Texto.vazioParaNulo(s);
        return new String[] {proposicao, descricao};
    }

    static String normalizarTipoSessao(String tipoDaTabela, String sessaoStr) {
        String bruto = Texto.vazioParaNulo(tipoDaTabela);
        if (bruto == null && sessaoStr != null) {
            Matcher m = TIPO_DA_SESSAO_STR.matcher(sessaoStr.strip());
            if (m.find()) {
                bruto = Texto.vazioParaNulo(m.group(1));
            }
        }
        if (bruto == null) {
            return "Sessão Plenária";
        }
        String minusculo = bruto.toLowerCase(Locale.ROOT);
        if (minusculo.startsWith("sessão") || minusculo.startsWith("reunião") || minusculo.startsWith("audiência")) {
            return bruto;
        }
        return "Sessão " + bruto;
    }

    private static String proximaPagina(Contexto ctx, Coleta.Arquivo arq) {
        if (!Files.exists(Coleta.caminho(ctx, arq))) {
            return null;
        }
        return Sapl.proxima(Json.ler(Coleta.caminho(ctx, arq)));
    }

    private static Iterable<JsonNode> resultados(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        if (root.isArray()) {
            return root;
        }
        if (root.has("results") && root.get("results").isArray()) {
            return root.get("results");
        }
        return List.of();
    }
}
