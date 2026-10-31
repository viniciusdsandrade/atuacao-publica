package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Registros.Voto;
import tools.jackson.databind.JsonNode;

/**
 * Adaptador do SAPL (Sistema de Apoio ao Processo Legislativo) para o piloto municipal (ADR-0007).
 * Cobre a Câmara Municipal de Campinas (IBGE 3509502).
 */
public final class Sapl implements Fonte {

    public static final String FONTE_ID = "campinas-camara-sapl";
    public static final String IBGE = "3509502";
    public static final String UF = "SP";
    public static final String API = "https://sapl.campinas.sp.leg.br/api";
    private static final String JSON = "application/json";

    public record Legislatura(int id, LocalDate inicio, LocalDate fim) {
    }

    public record MandatoSapl(String parlamentarId, LocalDate inicio, LocalDate fim, boolean titular,
                              String tipoAfastamento, String situacao, boolean emExercicio) {
    }

    @Override
    public String nome() {
        return "sapl";
    }

    // ---------------------------------------------------------------- fetch

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        Coleta.Arquivo arqLeg = coleta.obter(API + "/parlamentares/legislatura/", "legislaturas.json", JSON, Politica.SEMPRE);
        List<Legislatura> legislaturas = legislaturas(Json.ler(Coleta.caminho(ctx, arqLeg)));
        Legislatura atual = legislaturaVigente(legislaturas, ctx.hoje());

        Coleta.Arquivo arqMandatos = coleta.obter(API + "/parlamentares/mandato/?legislatura=" + atual.id() + "&page_size=100",
                "mandatos/" + atual.id() + ".json", JSON, Politica.SEMPRE);
        Coleta.Arquivo arqAtivos = coleta.obter(API + "/parlamentares/parlamentar/?ativo=True&page_size=100", "parlamentares-ativos.json",
                JSON, Politica.SEMPRE);

        Set<String> ids = new HashSet<>();
        for (JsonNode m : resultados(Json.ler(Coleta.caminho(ctx, arqMandatos)))) {
            String id = extrairId(m, "parlamentar");
            if (id != null) {
                ids.add(id);
            }
        }
        for (JsonNode a : resultados(Json.ler(Coleta.caminho(ctx, arqAtivos)))) {
            String id = Json.texto(a, "id");
            if (id != null) {
                ids.add(id);
            }
        }

        coleta.contar("vereadores", ids.size());

        for (String id : ids) {
            coleta.obter(API + "/parlamentares/parlamentar/" + id + "/", "parlamentares/" + id + ".json", JSON, Politica.SEMANAL);
            coleta.obterOpcional(API + "/parlamentares/filiacao/?parlamentar=" + id, "filiacoes/" + id + ".json", JSON, Politica.SEMANAL);
            coleta.obterOpcional(API + "/sessao/votoparlamentar/?parlamentar=" + id, "votos/" + id + ".json", JSON, Politica.SEMANAL);
        }

        coleta.periodo(new Meta.Periodo(atual.inicio(), ctx.hoje()));
    }

    // ------------------------------------------------------------ normalize

    @Override
    public void normalize(Contexto ctx) {
        Path pasta = ctx.raw().resolve("sapl");
        if (!Files.exists(pasta.resolve("legislaturas.json.gz")) && !Files.exists(pasta.resolve("legislaturas.json"))) {
            System.out.println("sapl: sem dados coletados para normalizar");
            return;
        }

        Coleta.Arquivo arqLeg = Coleta.metadados(ctx, "sapl", "legislaturas.json");
        List<Legislatura> legislaturas = legislaturas(Json.ler(Coleta.caminho(ctx, arqLeg)));
        Legislatura atual = legislaturaVigente(legislaturas, ctx.hoje());

        Coleta.Arquivo arqMandatos = Coleta.metadados(ctx, "sapl", "mandatos/" + atual.id() + ".json");
        JsonNode dadosMandatos = arqMandatos == null ? null : Json.ler(Coleta.caminho(ctx, arqMandatos));
        Map<String, List<MandatoSapl>> porParlamentar = mandatos(dadosMandatos, atual, ctx.hoje());

        Coleta.Arquivo arqAtivos = Coleta.metadados(ctx, "sapl", "parlamentares-ativos.json");
        Set<String> ativosIds = new HashSet<>();
        if (arqAtivos != null) {
            for (JsonNode a : resultados(Json.ler(Coleta.caminho(ctx, arqAtivos)))) {
                String id = Json.texto(a, "id");
                if (id != null) {
                    ativosIds.add(id);
                }
            }
        }

        Set<String> todosIds = new HashSet<>(porParlamentar.keySet());
        todosIds.addAll(ativosIds);

        List<Pessoa> pessoas = new ArrayList<>();
        Path saida = ctx.normalizado().resolve("sapl");
        Path pastaBlocos = saida.resolve("blocos");

        for (String id : todosIds) {
            Coleta.Arquivo arqP = Coleta.metadados(ctx, "sapl", "parlamentares/" + id + ".json");
            if (arqP == null) {
                continue;
            }
            JsonNode pNode = Json.ler(Coleta.caminho(ctx, arqP));
            String nome = primeiro(Json.texto(pNode, "nome_parlamentar"), Json.texto(pNode, "nome_completo"));
            if (nome == null) {
                continue;
            }
            String fotoUrl = Json.texto(pNode, "fotografia");

            Coleta.Arquivo arqFil = Coleta.metadados(ctx, "sapl", "filiacoes/" + id + ".json");
            String partido = arqFil == null ? null : extrairPartido(Json.ler(Coleta.caminho(ctx, arqFil)), ctx.hoje());

            List<MandatoSapl> listaMandatos = porParlamentar.getOrDefault(id, List.of());
            boolean emExercicio = listaMandatos.stream().anyMatch(MandatoSapl::emExercicio) || ativosIds.contains(id);
            String situacao = listaMandatos.isEmpty() ? (emExercicio ? "Exercício" : "Sem registro de exercício")
                    : listaMandatos.getFirst().situacao();

            List<LinkOficial> links = List.of(new LinkOficial("Página no SAPL",
                    "https://sapl.campinas.sp.leg.br/parlamentar/" + id,
                    Prov.de(FONTE_ID, API + "/parlamentares/parlamentar/" + id + "/", arqP)));

            Pessoa pessoa = new Pessoa("sapl", id, nome, "vereador", "municipal", IBGE, UF, partido, fotoUrl, situacao,
                    emExercicio, links);
            pessoas.add(pessoa);

            // Blocos
            List<Mandato> mandatosBloco = new ArrayList<>();
            for (MandatoSapl ms : listaMandatos) {
                mandatosBloco.add(new Mandato("vereador", "municipal", IBGE, UF, ms.inicio(), ms.fim(), ms.situacao(),
                        ms.titular() ? "Titular" : "Suplente", atual.id(), null, null,
                        Prov.de(FONTE_ID, API + "/parlamentares/mandato/?legislatura=" + atual.id(), arqMandatos)));
            }

            // Votos
            Coleta.Arquivo arqVotos = Coleta.metadados(ctx, "sapl", "votos/" + id + ".json");
            List<Voto> votos = new ArrayList<>();
            Map<String, int[]> participacaoPorAno = new TreeMap<>();
            Map<String, Map<String, Integer>> votosPorTipo = new TreeMap<>();
            if (arqVotos != null) {
                for (JsonNode v : resultados(Json.ler(Coleta.caminho(ctx, arqVotos)))) {
                    String dataHora = formatarDataHora(Json.texto(v, "data_hora"));
                    String votoTxt = Json.texto(v, "voto");
                    String regId = extrairId(v, "registro_votacao");
                    if (dataHora != null && votoTxt != null) {
                        String ano = dataHora.substring(0, 4);
                        votos.add(new Voto(regId == null ? id : regId, dataHora, "PLEN", "Matéria legislativa",
                                null, votoTxt, partido, null, null, null,
                                Prov.de(FONTE_ID, API + "/sessao/votoparlamentar/?parlamentar=" + id, arqVotos)));
                        participacaoPorAno.computeIfAbsent(ano, k -> new int[2]);
                        participacaoPorAno.get(ano)[0]++;
                        participacaoPorAno.get(ano)[1]++;
                        votosPorTipo.computeIfAbsent(ano, k -> new TreeMap<>())
                                .merge(votoTxt, 1, Integer::sum);
                    }
                }
            }

            List<Participacao> participacoes = new ArrayList<>();
            for (var entry : participacaoPorAno.entrySet()) {
                int[] n = entry.getValue();
                participacoes.add(new Participacao(entry.getKey(), n[0], n[1], null, votosPorTipo.get(entry.getKey()),
                        Prov.de(FONTE_ID, API + "/sessao/votoparlamentar/?parlamentar=" + id, arqVotos)));
            }

            Map<String, List<FonteUsada>> fontesDoVereador = new LinkedHashMap<>();
            if (arqMandatos != null) {
                fontesDoVereador.put("mandatos", List.of(FonteUsada.de(FONTE_ID, arqMandatos)));
            }
            if (arqVotos != null && !votos.isEmpty()) {
                fontesDoVereador.put("votos", List.of(FonteUsada.de(FONTE_ID, arqVotos)));
                fontesDoVereador.put("participacao_votacoes", List.of(FonteUsada.de(FONTE_ID, arqVotos)));
            }

            Camara.Blocos blocos = new Camara.Blocos(mandatosBloco, participacoes, votos, List.of(), List.of(), List.of(),
                    List.of(), List.of(), fontesDoVereador);
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocos);
        }

        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        System.out.println("sapl: " + pessoas.size() + " vereadores normalizados");
    }

    // ------------------------------------------------------------ helpers

    static List<JsonNode> resultados(JsonNode raiz) {
        if (raiz == null) {
            return List.of();
        }
        JsonNode r = raiz.get("results");
        return r != null && r.isArray() ? Json.lista(r) : Json.lista(raiz);
    }

    static String formatarDataHora(String dataHora) {
        if (dataHora == null || dataHora.isBlank()) {
            return null;
        }
        int ponto = dataHora.indexOf('.');
        if (ponto > 0) {
            dataHora = dataHora.substring(0, ponto);
        } else {
            int mais = dataHora.indexOf('+');
            int menos = dataHora.indexOf('-', 10);
            int corte = mais > 0 ? mais : (menos > 0 ? menos : -1);
            if (corte > 0) {
                dataHora = dataHora.substring(0, corte);
            }
        }
        return dataHora.length() >= 19 ? dataHora.substring(0, 19) : (dataHora.length() >= 10 ? dataHora.substring(0, 10) : null);
    }

    static String extrairId(JsonNode no, String campo) {
        String val = Json.texto(no, campo);
        if (val == null) {
            return null;
        }
        if (val.contains("/")) {
            String[] partes = val.split("/");
            for (int i = partes.length - 1; i >= 0; i--) {
                if (!partes[i].isBlank() && partes[i].matches("^[0-9]+$")) {
                    return partes[i];
                }
            }
        }
        return val.matches("^[0-9]+$") ? val : null;
    }

    static List<Legislatura> legislaturas(JsonNode raiz) {
        List<Legislatura> lista = new ArrayList<>();
        for (JsonNode l : resultados(raiz)) {
            Integer numero = Json.inteiro(l, "numero");
            if (numero == null) {
                numero = Json.inteiro(l, "id");
            }
            String ini = Json.texto(l, "data_inicio");
            String fim = Json.texto(l, "data_fim");
            if (numero != null && ini != null && fim != null) {
                lista.add(new Legislatura(numero, LocalDate.parse(ini), LocalDate.parse(fim)));
            }
        }
        lista.sort(Comparator.comparing(Legislatura::id));
        return lista;
    }

    static Legislatura legislaturaVigente(List<Legislatura> legislaturas, LocalDate hoje) {
        for (Legislatura l : legislaturas) {
            if (!hoje.isBefore(l.inicio()) && !hoje.isAfter(l.fim())) {
                return l;
            }
        }
        return legislaturas.isEmpty() ? new Legislatura(19, LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31))
                : legislaturas.getLast();
    }

    static Map<String, List<MandatoSapl>> mandatos(JsonNode raiz, Legislatura leg, LocalDate hoje) {
        Map<String, List<MandatoSapl>> map = new LinkedHashMap<>();
        if (raiz == null) {
            return map;
        }
        for (JsonNode m : resultados(raiz)) {
            String pId = extrairId(m, "parlamentar");
            if (pId == null) {
                continue;
            }
            String ini = Json.texto(m, "data_inicio_mandato");
            String fim = Json.texto(m, "data_fim_mandato");
            LocalDate dataInicio = ini == null ? leg.inicio() : LocalDate.parse(ini);
            LocalDate dataFim = fim == null ? leg.fim() : LocalDate.parse(fim);
            boolean titular = m.path("titular").asBoolean(true);
            String tipoAfastamento = Json.texto(m, "tipo_afastamento");

            boolean emExercicio = !hoje.isBefore(dataInicio) && !hoje.isAfter(dataFim) && tipoAfastamento == null;
            String situacao = emExercicio ? "Exercício" : (tipoAfastamento != null ? "Fora de exercício (" + tipoAfastamento + ")"
                    : (hoje.isAfter(dataFim) ? "Mandato encerrado em " + dataFim : "Mandato previsto"));

            map.computeIfAbsent(pId, k -> new ArrayList<>())
                    .add(new MandatoSapl(pId, dataInicio, dataFim, titular, tipoAfastamento, situacao, emExercicio));
        }
        return map;
    }

    static String extrairPartido(JsonNode raiz, LocalDate hoje) {
        if (raiz == null) {
            return null;
        }
        for (JsonNode f : resultados(raiz)) {
            String desf = Json.texto(f, "data_desfiliacao");
            if (desf == null || LocalDate.parse(desf).isAfter(hoje)) {
                String str = Json.texto(f, "__str__");
                if (str != null && str.contains(" - ")) {
                    String[] partes = str.split(" - ");
                    if (partes.length >= 2 && !partes[1].isBlank()) {
                        return partes[1].strip();
                    }
                }
                String sigla = Json.texto(f, "sigla_partido");
                if (sigla != null) {
                    return sigla;
                }
            }
        }
        return null;
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
