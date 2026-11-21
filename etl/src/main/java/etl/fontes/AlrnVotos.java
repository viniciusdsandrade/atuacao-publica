package etl.fontes;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Json;
import etl.Registros.Participacao;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Votos da ALRN pela API de transparência do e-Legis (ADR-0081). A lista "últimas votações" traz cada processo com a
 * votação mais recente; o detalhamento da votação traz o voto de cada deputado. Os códigos vêm do próprio portal de
 * transparência: voto 0 é Não, 1 é Sim, 2 é Abstenção; votação S é secreta e X é simbólica, sem voto por deputado.
 */
final class AlrnVotos {

    static final String FONTE = AlrnProposicoes.FONTE;
    static final String API = AlrnProposicoes.API;
    static final int POR_PAGINA = 200;
    static final int MAX_PAGINAS = 100;
    static final Map<Integer, String> VOTOS = Map.of(0, "Não", 1, "Sim", 2, "Abstenção");
    static final Set<String> SEM_VOTO_NOMINAL = Set.of("S", "X");

    private AlrnVotos() {
    }

    /** Votação de um processo numa reunião. */
    record Votacao(long processo, long reuniao, long votacao) {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        Coleta.Arquivo primeira = coleta.obterOpcional(url(0), caminhoDaLista(0), "application/json", Politica.SEMANAL_EM_LISTA);
        if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
            return;
        }
        int total = Json.ler(Coleta.caminho(ctx, primeira)).path("total").asInt();
        int paginas = Math.min(MAX_PAGINAS, (total + POR_PAGINA - 1) / POR_PAGINA);
        for (int p = 1; p < paginas && !coleta.orcamentoExaurido(); p++) {
            coleta.obterOpcional(url(p), caminhoDaLista(p), "application/json", Politica.SEMANAL_EM_LISTA);
        }
        // A votação encerrada não muda: o detalhamento vem do cache depois da primeira coleta.
        for (Votacao v : votacoes(ctx)) {
            if (coleta.orcamentoExaurido()) {
                break;
            }
            coleta.obterOpcional(API + "processo/" + v.processo() + "/detalhamento-votacao/" + v.reuniao(), caminhoDoDetalhe(v),
                    "application/json", Politica.SE_AUSENTE);
        }
    }

    /** Votações nominais das listas em cache, da mais nova para a mais velha. */
    static List<Votacao> votacoes(Contexto ctx) {
        Map<Long, Votacao> porId = new LinkedHashMap<>();
        for (int p = 0; p < MAX_PAGINAS; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alrn", caminhoDaLista(p));
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            for (JsonNode item : Json.ler(Coleta.caminho(ctx, arq)).path("dados")) {
                String tipo = Json.texto(item, "tipoVotacao");
                if (tipo != null && SEM_VOTO_NOMINAL.contains(tipo) || !item.hasNonNull("id")) {
                    continue;
                }
                for (JsonNode rp : item.path("reuniaoProcesso")) {
                    if (rp.hasNonNull("id") && rp.path("reuniao").hasNonNull("id")) {
                        porId.putIfAbsent(rp.get("id").asLong(),
                                new Votacao(item.get("id").asLong(), rp.path("reuniao").get("id").asLong(), rp.get("id").asLong()));
                    }
                }
            }
        }
        return new ArrayList<>(porId.values());
    }

    /** Resultado da leitura: votos por deputado e participação em plenário por ano. */
    record Resultado(Map<String, List<Voto>> votos, Map<String, List<Participacao>> participacao) {
    }

    static Resultado porDeputado(Contexto ctx, AutoriaPorNome autoria) {
        Map<Long, JsonNode> processos = new LinkedHashMap<>();
        for (int p = 0; p < MAX_PAGINAS; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alrn", caminhoDaLista(p));
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            for (JsonNode item : Json.ler(Coleta.caminho(ctx, arq)).path("dados")) {
                if (item.hasNonNull("id")) {
                    processos.putIfAbsent(item.get("id").asLong(), item);
                }
            }
        }
        Map<String, List<Voto>> votos = new LinkedHashMap<>();
        // Votações em plenário por ano, para a participação: no plenário todos os deputados votam.
        Map<String, Set<Long>> plenarioPorAno = new TreeMap<>();
        Map<String, Map<String, Set<Long>>> votouNoPlenario = new LinkedHashMap<>();
        Map<String, Prov> provDoAno = new TreeMap<>();
        Set<String> semPerfil = new LinkedHashSet<>();
        int lidas = 0;
        for (Votacao v : votacoes(ctx)) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alrn", caminhoDoDetalhe(v));
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            JsonNode detalhe = Json.ler(Coleta.caminho(ctx, arq));
            JsonNode processo = processos.get(v.processo());
            String data = data(processo, v.votacao());
            if (data == null) {
                continue;
            }
            lidas++;
            Prov prov = Prov.de(FONTE, arq.url(), arq);
            JsonNode reuniao = detalhe.path("reuniao");
            boolean plenario = reuniao.path("tipoReuniao").path("sessao").asBoolean(false);
            String orgao = orgao(reuniao);
            String proposicao = proposicao(processo);
            String descricao = processo == null ? null : Texto.semEntidades(Json.texto(processo, "ementa"));
            String ano = data.substring(0, 4);
            // A maior parte das votações em plenário não lista voto nenhum: é simbólica, mesmo marcada como aberta. Só a
            // que lista mais de um voto conta como votação nominal na participação.
            boolean nominal = plenario && detalhe.path("votacao").path("dados").size() > 1;
            if (nominal) {
                plenarioPorAno.computeIfAbsent(ano, k -> new LinkedHashSet<>()).add(v.votacao());
                provDoAno.putIfAbsent(ano, prov);
            }
            for (JsonNode voto : detalhe.path("votacao").path("dados")) {
                JsonNode parlamentar = voto.path("parlamentar");
                String nome = Json.texto(parlamentar, "nomeParlamentar");
                String id = nome == null ? null : autoria.id(nome);
                if (id == null) {
                    if (nome != null) {
                        semPerfil.add(nome);
                    }
                    continue;
                }
                String valor = valor(voto.get("voto"));
                votos.computeIfAbsent(id, k -> new ArrayList<>()).add(new Voto(String.valueOf(v.votacao()), data, orgao, proposicao,
                        descricao, valor, Json.texto(parlamentar.path("partido"), "nome"), null, null, null, prov));
                if (nominal) {
                    votouNoPlenario.computeIfAbsent(id, k -> new TreeMap<>()).computeIfAbsent(ano, k -> new LinkedHashSet<>()).add(v.votacao());
                }
            }
        }
        votos.values().forEach(l -> l.sort(Comparator.comparing(Voto::data).reversed().thenComparing(Voto::votacaoId)));
        Map<String, List<Participacao>> participacao = new LinkedHashMap<>();
        for (String id : votos.keySet()) {
            List<Participacao> lista = new ArrayList<>();
            for (var ano : plenarioPorAno.entrySet()) {
                int nominais = ano.getValue().size();
                int registrados = votouNoPlenario.getOrDefault(id, Map.of()).getOrDefault(ano.getKey(), Set.of()).size();
                Map<String, Integer> porTipo = new TreeMap<>();
                for (Voto voto : votos.get(id)) {
                    if (voto.data().startsWith(ano.getKey()) && ano.getValue().contains(Long.valueOf(voto.votacaoId()))) {
                        porTipo.merge(voto.voto() == null ? "(vazio na fonte)" : voto.voto(), 1, Integer::sum);
                    }
                }
                lista.add(new Participacao(ano.getKey(), nominais, registrados, nominais - registrados, porTipo, provDoAno.get(ano.getKey())));
            }
            participacao.put(id, lista);
        }
        System.out.println("alrn: " + lidas + " votações lidas, " + votos.values().stream().mapToInt(List::size).sum()
                + " votos em " + votos.size() + " deputados; " + semPerfil.size() + " nome(s) de votante sem perfil");
        return new Resultado(votos, participacao);
    }

    /** Voto pelo código do portal (0, 1, 2); texto que a fonte já escreve ("votou", em votação secreta) fica como está. */
    static String valor(JsonNode voto) {
        if (voto == null || voto.isNull()) {
            return null;
        }
        if (voto.isNumber() || voto.isTextual() && voto.asString().strip().matches("\\d+")) {
            int codigo = Integer.parseInt(voto.asString().strip());
            return VOTOS.getOrDefault(codigo, String.valueOf(codigo));
        }
        return Texto.semEntidades(voto.asString());
    }

    /** Data da abertura da votação, pelo registro da reunião no processo. */
    private static String data(JsonNode processo, long votacao) {
        if (processo == null) {
            return null;
        }
        for (JsonNode rp : processo.path("reuniaoProcesso")) {
            if (rp.path("id").asLong() == votacao) {
                String abertura = Json.texto(rp, "dataSituacaoAbertura");
                return abertura == null || abertura.length() < 10 ? null : abertura.substring(0, 10);
            }
        }
        return null;
    }

    private static String orgao(JsonNode reuniao) {
        JsonNode comissoes = reuniao.path("reuniaoComissao");
        if (comissoes.isArray() && !comissoes.isEmpty() && comissoes.get(0).path("comissao").hasNonNull("nome")) {
            return Texto.semEntidades(comissoes.get(0).path("comissao").get("nome").asString());
        }
        return Texto.semEntidades(Json.texto(reuniao.path("tipoReuniao"), "nome"));
    }

    private static String proposicao(JsonNode processo) {
        if (processo == null) {
            return null;
        }
        JsonNode p = processo.path("propositura");
        String sigla = Json.texto(p.path("tipo"), "sigla");
        if (sigla == null) {
            return null;
        }
        return sigla + " " + (p.hasNonNull("numero") ? p.get("numero").asInt() : "s/n") + "/" + (p.hasNonNull("ano") ? p.get("ano").asInt() : "");
    }

    static String url(int pagina) {
        return API + "processo/ultimas-votacoes?pagina=" + pagina + "&tamanhoPagina=" + POR_PAGINA;
    }

    private static String caminhoDaLista(int pagina) {
        return "votacoes/lista-" + pagina + ".json";
    }

    private static String caminhoDoDetalhe(Votacao v) {
        return "votacoes/detalhe/" + v.processo() + "-" + v.reuniao() + ".json";
    }
}
