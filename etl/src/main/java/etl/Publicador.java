package etl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** publish: junta as fontes normalizadas num perfil por político, valida e grava data/publicado. */
public final class Publicador {

    /** Mesmos blocos para todos os políticos do mesmo cargo (política editorial). */
    static final Map<String, List<String>> BLOCOS = Map.of(
            "deputado_federal", List.of("mandatos", "participacao_votacoes", "votos", "despesas", "proposicoes",
                    "discursos", "eventos", "orgaos_e_frentes", "candidaturas", "posicionamentos_declarados"),
            "senador", List.of("mandatos", "participacao_votacoes", "votos", "despesas", "proposicoes",
                    "discursos", "eventos", "orgaos_e_frentes", "candidaturas", "posicionamentos_declarados"),
            "presidente", List.of("mandatos", "candidaturas", "atos_normativos", "agenda_oficial", "posicionamentos_declarados"),
            "governador", List.of("mandatos", "candidaturas", "posicionamentos_declarados"));

    /** Blocos com muitos registros: o perfil traz contagens e os mais recentes; o detalhe vai por ano. */
    private static final Map<String, String> AMOSTRA = Map.of(
            "votos", "voto", "proposicoes", "tipo", "discursos", "tipo", "eventos", "tipo",
            "atos_normativos", "tipo", "agenda_oficial", "");
    private static final Map<String, String> CAMPO_DATA = Map.of(
            "votos", "data", "proposicoes", "data_apresentacao", "discursos", "data", "eventos", "data",
            "atos_normativos", "data", "agenda_oficial", "data", "despesas", "data");
    /** Fonte da CLI de onde vem cada bloco, para marcar bloco desatualizado quando a coleta falha. */
    private static final Map<String, String> ORIGEM_DO_BLOCO = Map.of("candidaturas", "tse", "atos_normativos", "executivo");
    static final int RECENTES = 20;
    static final List<String> FONTES_CLI = List.of("camara", "senado", "tse", "executivo", "contrib");

    /** Perfil em construção, antes da validação. */
    static final class Perfil {
        String id;
        String nome;
        String cargo;
        String esfera;
        String ente;
        String uf;
        String partido;
        String situacao;
        String foto;
        /** Identificador externo principal ("camara:204554"), usado para reaproveitar o id publicado. */
        String chaveExterna;
        /** Fonte da CLI que originou o perfil (camara, senado, tse). */
        String fonteCli;
        final Map<String, String> ids = new LinkedHashMap<>();
        final Map<String, String> tse = new TreeMap<>();
        final List<JsonNode> links = new ArrayList<>();
        final Map<String, List<JsonNode>> registros = new LinkedHashMap<>();
        final Map<String, List<JsonNode>> fontes = new LinkedHashMap<>();

        List<JsonNode> bloco(String nome) {
            return registros.computeIfAbsent(nome, k -> new ArrayList<>());
        }

        /** Registros manuais (posicionamentos, agenda) não dependem de coleta e nunca ficam desatualizados. */
        String origemDoBloco(String nome) {
            if (nome.equals("posicionamentos_declarados") || nome.equals("agenda_oficial")) {
                return null;
            }
            return ORIGEM_DO_BLOCO.getOrDefault(nome, fonteCli);
        }
    }

    private final Contexto ctx;
    private final List<Meta.Rejeitado> rejeitados = new ArrayList<>();
    private final Map<String, Meta.Cursor> cursores = new HashMap<>();

    public Publicador(Contexto ctx) {
        this.ctx = ctx;
        for (String fonte : FONTES_CLI) {
            cursores.put(fonte, Meta.cursor(ctx, fonte));
        }
    }

    public int publicar() {
        Instant inicio = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Catalogo catalogo = Catalogo.carregar(ctx);
        Map<String, String> idsAnteriores = idsAnteriores();
        List<Perfil> perfis = new ArrayList<>(Vinculo.perfis(ctx, catalogo));
        for (Perfil perfil : perfis) {
            perfil.id = idsAnteriores.getOrDefault(perfil.chaveExterna, perfil.id);
        }
        Contribuicoes.anexar(ctx, catalogo, perfis, rejeitados);

        Path pasta = ctx.publicado().resolve("politicos");
        apagar(pasta);
        ArrayNode indice = Json.SAIDA.createArrayNode();
        int publicados = 0;
        perfis.sort(Comparator.comparing(p -> Texto.normalizarNome(p.nome)));
        for (Perfil perfil : perfis) {
            ObjectNode documento = documento(perfil, pasta);
            List<String> erros = ctx.validador().validar("politico", documento);
            String caminho = "politicos/" + perfil.id + ".json";
            if (!erros.isEmpty()) {
                rejeitados.add(new Meta.Rejeitado(caminho, "schema: " + String.join("; ", erros.subList(0, Math.min(3, erros.size())))));
                continue;
            }
            Json.gravar(pasta.resolve(perfil.id + ".json"), documento);
            indice.add(entradaDoIndice(documento));
            publicados++;
        }
        gravarValidado("indice", ctx.publicado().resolve("index.json"), Json.SAIDA.createObjectNode()
                .put("gerado_em", ctx.agora().toString())
                .put("total", indice.size())
                .<ObjectNode>set("politicos", indice)
                .set("aliases", Json.SAIDA.createObjectNode()));
        gravarValidado("fontes", ctx.publicado().resolve("fontes.json"), Json.arvore(catalogo.itens()));

        List<Meta.StatusFonte> status = new ArrayList<>();
        for (String fonte : FONTES_CLI) {
            Meta.Cursor c = cursores.get(fonte);
            status.add(new Meta.StatusFonte(fonte, c.status(), c.ultimaColetaOk(), c.erros().isEmpty() ? null : c.erros().getFirst()));
        }
        Meta.Execucao execucao = new Meta.Execucao(new Meta.Projeto(Projeto.NOME, Projeto.VERSAO, ctx.repositorioUrl()),
                inicio, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), status, publicados, rejeitados);
        gravarValidado("execucao", Meta.pasta(ctx).resolve("execucao.json"), Json.arvore(execucao));
        System.out.println("publish: " + publicados + " perfis publicados, " + rejeitados.size() + " rejeição(ões)");
        rejeitados.stream().limit(10).forEach(r -> System.out.println("publish: rejeitado " + r.arquivo() + ": " + r.motivo()));
        return publicados > 0 ? 0 : 1;
    }

    private ObjectNode documento(Perfil perfil, Path pasta) {
        ObjectNode doc = Json.SAIDA.createObjectNode();
        doc.put("id", perfil.id).put("nome", perfil.nome).put("cargo_atual", perfil.cargo).put("esfera", perfil.esfera)
                .put("ente", perfil.ente).put("uf", perfil.uf).put("partido_atual", perfil.partido)
                .put("situacao", perfil.situacao).put("foto_url", perfil.foto);
        ObjectNode ids = doc.putObject("ids_externos");
        perfil.ids.forEach(ids::put);
        if (!perfil.tse.isEmpty()) {
            ObjectNode tse = ids.putObject("tse");
            perfil.tse.forEach(tse::put);
        }
        ArrayNode links = doc.putArray("links_oficiais");
        perfil.links.forEach(links::add);
        doc.put("atualizado_em", ctx.agora().toString());
        ObjectNode blocos = doc.putObject("blocos");
        for (String bloco : BLOCOS.get(perfil.cargo)) {
            List<JsonNode> registros = seguros(perfil, bloco, perfil.registros.getOrDefault(bloco, List.of()));
            blocos.set(bloco, bloco(perfil, bloco, registros, pasta));
        }
        return doc;
    }

    /** Remove registro com CPF, telefone ou e-mail. O motivo vai para o relatório, sem o trecho. */
    private List<JsonNode> seguros(Perfil perfil, String bloco, List<JsonNode> registros) {
        List<JsonNode> seguros = new ArrayList<>();
        for (JsonNode registro : registros) {
            List<String> violacoes = Guarda.violacoes(registro);
            if (violacoes.isEmpty()) {
                seguros.add(registro);
            } else {
                rejeitados.add(new Meta.Rejeitado("politicos/" + perfil.id + ".json",
                        "registro removido de " + bloco + ": " + violacoes.getFirst()));
            }
        }
        return seguros;
    }

    private ObjectNode bloco(Perfil perfil, String bloco, List<JsonNode> registros, Path pasta) {
        ObjectNode no = Json.SAIDA.createObjectNode();
        List<JsonNode> fontes = perfil.fontes.getOrDefault(bloco, List.of());
        ArrayNode listaFontes = no.putArray("fontes");
        Instant atualizado = null;
        for (JsonNode f : fontes) {
            listaFontes.add(f);
            Instant capturado = Instant.parse(f.get("capturado_em").stringValue());
            atualizado = atualizado == null || capturado.isAfter(atualizado) ? capturado : atualizado;
        }
        no.put("atualizado_em", atualizado == null ? null : atualizado.toString());
        String origem = perfil.origemDoBloco(bloco);
        no.put("desatualizado", origem != null && cursores.containsKey(origem) && cursores.get(origem).desatualizado());
        no.put("total", registros.size());

        if (bloco.equals("despesas")) {
            despesas(perfil, registros, no, pasta);
        } else if (AMOSTRA.containsKey(bloco)) {
            amostra(perfil, bloco, registros, no, pasta);
        } else {
            ArrayNode lista = no.putArray("registros");
            registros.forEach(lista::add);
        }
        return no;
    }

    private void amostra(Perfil perfil, String bloco, List<JsonNode> registros, ObjectNode no, Path pasta) {
        String campoTipo = AMOSTRA.get(bloco);
        String campoData = CAMPO_DATA.get(bloco);
        List<JsonNode> ordenados = new ArrayList<>(registros);
        ordenados.sort(Comparator.comparing((JsonNode r) -> textoOuVazio(r, campoData)).reversed());
        if (!campoTipo.isEmpty()) {
            Map<String, Integer> porTipo = new TreeMap<>();
            for (JsonNode r : registros) {
                porTipo.merge(textoOuPadrao(r, campoTipo, "(vazio na fonte)"), 1, Integer::sum);
            }
            ObjectNode tipos = no.putObject("por_tipo");
            porTipo.forEach(tipos::put);
        }
        no.set("arquivos", detalhes(perfil, bloco, ordenados, pasta));
        ArrayNode recentes = no.putArray("recentes");
        ordenados.stream().limit(RECENTES).forEach(recentes::add);
    }

    private void despesas(Perfil perfil, List<JsonNode> registros, ObjectNode no, Path pasta) {
        Map<String, BigDecimal[]> porMes = new TreeMap<>();
        Map<String, BigDecimal[]> porCategoria = new TreeMap<>();
        Map<Integer, JsonNode> provPorAno = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode r : registros) {
            BigDecimal valor = r.get("valor").decimalValue();
            int ano = r.get("ano").asInt();
            total = total.add(valor);
            somar(porMes, ano + "|" + String.format("%02d", r.get("mes").asInt()), valor);
            somar(porCategoria, ano + "|" + r.get("categoria").stringValue(), valor);
            provPorAno.putIfAbsent(ano, r);
        }
        no.put("valor_total", total);
        ArrayNode mensal = no.putArray("por_mes");
        porMes.forEach((chave, soma) -> {
            String[] partes = chave.split("\\|", 2);
            mensal.add(agregado(provPorAno.get(Integer.parseInt(partes[0])), Integer.parseInt(partes[0]),
                    Integer.parseInt(partes[1]), null, soma));
        });
        ArrayNode categorias = no.putArray("por_categoria");
        porCategoria.forEach((chave, soma) -> {
            String[] partes = chave.split("\\|", 2);
            categorias.add(agregado(provPorAno.get(Integer.parseInt(partes[0])), Integer.parseInt(partes[0]), null, partes[1], soma));
        });
        List<JsonNode> ordenados = new ArrayList<>(registros);
        ordenados.sort(Comparator.comparing((JsonNode r) -> r.get("ano").asInt() * 100 + r.get("mes").asInt()).reversed());
        no.set("arquivos", detalhes(perfil, "despesas", ordenados, pasta));
    }

    private static ObjectNode agregado(JsonNode exemplo, int ano, Integer mes, String categoria, BigDecimal[] soma) {
        ObjectNode a = Json.SAIDA.createObjectNode();
        a.put("ano", ano);
        a.put("mes", mes);
        a.put("categoria", categoria);
        a.put("valor", soma[0]);
        a.put("quantidade", soma[1].intValue());
        a.set("fonte_id", exemplo.get("fonte_id"));
        a.set("url_origem", exemplo.get("url_origem"));
        a.set("capturado_em", exemplo.get("capturado_em"));
        return a;
    }

    private static void somar(Map<String, BigDecimal[]> mapa, String chave, BigDecimal valor) {
        BigDecimal[] atual = mapa.computeIfAbsent(chave, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        atual[0] = atual[0].add(valor);
        atual[1] = atual[1].add(BigDecimal.ONE);
    }

    /** Grava politicos/{id}/{bloco}-{ano}.json e devolve a lista de arquivos para o perfil. */
    private ArrayNode detalhes(Perfil perfil, String bloco, List<JsonNode> ordenados, Path pasta) {
        Map<Integer, List<JsonNode>> porAno = new TreeMap<>(Comparator.reverseOrder());
        for (JsonNode r : ordenados) {
            Integer ano = ano(r, bloco);
            if (ano != null) {
                porAno.computeIfAbsent(ano, k -> new ArrayList<>()).add(r);
            }
        }
        ArrayNode arquivos = Json.SAIDA.createArrayNode();
        if (bloco.equals("agenda_oficial")) {
            return arquivos;
        }
        for (var item : porAno.entrySet()) {
            String caminho = "politicos/" + perfil.id + "/" + bloco + "-" + item.getKey() + ".json";
            ObjectNode detalhe = Json.SAIDA.createObjectNode().put("politico_id", perfil.id).put("tipo", bloco)
                    .put("ano", item.getKey()).put("gerado_em", ctx.agora().toString());
            ArrayNode lista = detalhe.putArray("registros");
            item.getValue().forEach(lista::add);
            List<String> erros = ctx.validador().validar("detalhe", detalhe);
            if (!erros.isEmpty()) {
                rejeitados.add(new Meta.Rejeitado(caminho, "schema: " + String.join("; ", erros.subList(0, Math.min(3, erros.size())))));
                continue;
            }
            Json.gravar(pasta.resolve(perfil.id).resolve(bloco + "-" + item.getKey() + ".json"), detalhe);
            arquivos.add(Json.SAIDA.createObjectNode().put("ano", item.getKey()).put("caminho", caminho)
                    .put("total", item.getValue().size()));
        }
        return arquivos;
    }

    private static Integer ano(JsonNode registro, String bloco) {
        if (registro.hasNonNull("ano") && bloco.equals("despesas")) {
            return registro.get("ano").asInt();
        }
        String data = textoOuVazio(registro, CAMPO_DATA.get(bloco));
        return data.length() >= 4 && data.substring(0, 4).matches("[0-9]{4}") ? Integer.valueOf(data.substring(0, 4)) : null;
    }

    private static ObjectNode entradaDoIndice(ObjectNode doc) {
        ObjectNode e = Json.SAIDA.createObjectNode();
        e.set("id", doc.get("id"));
        e.set("nome", doc.get("nome"));
        e.set("cargo", doc.get("cargo_atual"));
        e.set("esfera", doc.get("esfera"));
        e.set("ente", doc.get("ente"));
        e.set("uf", doc.get("uf"));
        e.set("partido", doc.get("partido_atual"));
        e.set("foto_url", doc.get("foto_url"));
        e.set("ids_externos", doc.get("ids_externos"));
        return e;
    }

    private void gravarValidado(String schema, Path arquivo, JsonNode documento) {
        List<String> erros = ctx.validador().validar(schema, documento);
        if (!erros.isEmpty()) {
            throw new IllegalStateException(arquivo.getFileName() + " inválido: " + String.join("; ", erros.subList(0, Math.min(3, erros.size()))));
        }
        Json.gravar(arquivo, documento);
    }

    /** Identificador canônico já publicado para cada identificador externo (ADR-0002, item 5). */
    private Map<String, String> idsAnteriores() {
        Path indice = ctx.publicado().resolve("index.json");
        Map<String, String> ids = new HashMap<>();
        if (!Files.exists(indice)) {
            return ids;
        }
        for (JsonNode p : Json.ler(indice).path("politicos")) {
            String id = p.get("id").stringValue();
            JsonNode externos = p.path("ids_externos");
            if (externos.hasNonNull("camara")) {
                ids.put("camara:" + externos.get("camara").stringValue(), id);
            }
            if (externos.hasNonNull("senado")) {
                ids.put("senado:" + externos.get("senado").stringValue(), id);
            }
            for (var tse : externos.path("tse").properties()) {
                ids.put("tse:" + tse.getValue().stringValue(), id);
            }
        }
        return ids;
    }

    private static String textoOuVazio(JsonNode registro, String campo) {
        JsonNode valor = registro.get(campo);
        return valor == null || valor.isNull() ? "" : valor.asString();
    }

    private static String textoOuPadrao(JsonNode registro, String campo, String padrao) {
        String texto = textoOuVazio(registro, campo);
        return texto.isEmpty() ? padrao : texto;
    }

    private static void apagar(Path pasta) {
        if (!Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            for (Path p : caminhos.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Set<String> blocosDe(String cargo) {
        return Set.copyOf(BLOCOS.get(cargo));
    }
}
