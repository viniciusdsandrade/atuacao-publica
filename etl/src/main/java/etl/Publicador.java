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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
            "deputado_estadual", List.of("mandatos", "participacao_votacoes", "votos", "despesas", "proposicoes",
                    "discursos", "eventos", "orgaos_e_frentes", "candidaturas", "posicionamentos_declarados"),
            "vereador", List.of("mandatos", "participacao_votacoes", "votos", "despesas", "proposicoes",
                    "discursos", "eventos", "orgaos_e_frentes", "candidaturas", "posicionamentos_declarados"),
            "presidente", List.of("mandatos", "candidaturas", "despesas_executivo", "atos_normativos", "agenda_oficial",
                    "posicionamentos_declarados"),
            "governador", List.of("mandatos", "candidaturas", "despesas_executivo", "atos_normativos", "agenda_oficial",
                    "posicionamentos_declarados"),
            "prefeito", List.of("mandatos", "candidaturas", "despesas_executivo", "atos_normativos", "agenda_oficial",
                    "posicionamentos_declarados"),
            "vice_prefeito", List.of("mandatos", "candidaturas", "posicionamentos_declarados"));

    /** Ordem dos blocos no perfil. Quem ocupou mais de um cargo recebe os blocos de todos eles (ADR-0006). */
    private static final List<String> ORDEM_DOS_BLOCOS = List.of("mandatos", "participacao_votacoes", "votos", "despesas",
            "proposicoes", "discursos", "eventos", "orgaos_e_frentes", "candidaturas", "despesas_executivo", "atos_normativos",
            "agenda_oficial", "posicionamentos_declarados");

    static List<String> blocosDoPerfil(Perfil perfil) {
        Set<String> cargos = perfil.cargos.isEmpty() ? Set.of(perfil.cargo) : perfil.cargos;
        List<String> blocos = new ArrayList<>();
        for (String bloco : ORDEM_DOS_BLOCOS) {
            for (String cargo : cargos) {
                List<String> permitidos = BLOCOS.get(cargo);
                if (permitidos != null && permitidos.contains(bloco)) {
                    blocos.add(bloco);
                    break;
                }
            }
        }
        return blocos;
    }

    /** Blocos com muitos registros: o perfil traz contagens e os mais recentes; o detalhe vai por ano. */
    private static final Map<String, String> AMOSTRA = Map.of(
            "votos", "voto", "proposicoes", "tipo", "discursos", "tipo", "eventos", "tipo",
            "atos_normativos", "tipo", "agenda_oficial", "");
    private static final Map<String, String> CAMPO_DATA = Map.of(
            "votos", "data", "proposicoes", "data_apresentacao", "discursos", "data", "eventos", "data",
            "atos_normativos", "data", "agenda_oficial", "data", "despesas", "data");
    /** Fonte da CLI de onde vem cada bloco, para marcar bloco desatualizado quando a coleta falha. */
    private static final Map<String, String> ORIGEM_DO_BLOCO = Map.of("candidaturas", "tse", "atos_normativos", "executivo",
            "despesas_executivo", "tcesp");
    static final int RECENTES = 20;
    static final List<String> FONTES_CLI = List.of("camara", "senado", "tse", "executivo", "sapl", "tcesp", "cgu", "agenda", "contrib", "alesp", "executivosp");

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
        /** Blocos normalizados de cada casa legislativa da pessoa, lidos na publicação do perfil. */
        final List<Path> arquivosDeBlocos = new ArrayList<>();
        final Map<String, List<JsonNode>> fontes = new LinkedHashMap<>();
        /** Ids que redirecionam para este perfil (ADR-0002, item 6). */
        final Set<String> aliases = new LinkedHashSet<>();
        /** Todos os cargos da pessoa na cobertura: o perfil traz os blocos de cada um (ADR-0006). */
        final Set<String> cargos = new LinkedHashSet<>();
        boolean emExercicio;
        /** Prioridade do papel principal: em exercício, depois posse prevista, depois fora de exercício. */
        int prioridade;
        java.time.LocalDate inicioDoPapel;

        static final int FORA_DE_EXERCICIO = 1;
        static final int POSSE_PREVISTA = 2;
        static final int EM_EXERCICIO = 3;

        /** Registra um papel. O principal define cargo, situação e local do perfil. */
        void papel(String cargo, String esfera, String ente, String uf, String partido, String situacao, int prioridade,
                   java.time.LocalDate inicio) {
            cargos.add(cargo);
            boolean principal = this.cargo == null || prioridade > this.prioridade || prioridade == this.prioridade
                    && inicio != null && (inicioDoPapel == null || inicio.isAfter(inicioDoPapel));
            if (principal) {
                this.cargo = cargo;
                this.esfera = esfera;
                this.ente = ente;
                this.uf = uf;
                this.partido = partido;
                this.situacao = situacao;
                this.prioridade = prioridade;
                this.inicioDoPapel = inicio;
                this.emExercicio = prioridade == EM_EXERCICIO;
            }
        }

        List<JsonNode> bloco(String nome) {
            return registros.computeIfAbsent(nome, k -> new ArrayList<>());
        }

        /** Registros manuais (posicionamentos, agenda) não dependem de coleta e nunca ficam desatualizados. */
        String origemDoBloco(String nome) {
            if (nome.equals("posicionamentos_declarados")) {
                return null;
            }
            if (nome.equals("agenda_oficial")) {
                return cargos.contains("presidente") ? "agenda" : (cargos.contains("governador") ? "executivosp" : null);
            }
            if (nome.equals("atos_normativos") && cargos.contains("prefeito") && !cargos.contains("presidente")) {
                return "sapl";
            }
            if (nome.equals("despesas_executivo")) {
                return cargos.contains("presidente") ? "cgu" : (cargos.contains("governador") ? "executivosp" : "tcesp");
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
        Anterior anterior = anterior();
        List<Perfil> perfis = new ArrayList<>(Vinculo.perfis(ctx, catalogo));
        Map<String, String> aliases = identificadores(perfis, anterior);
        Contribuicoes.anexar(ctx, catalogo, perfis, aliases, rejeitados);

        // Monta numa pasta nova e só troca pela publicada se nenhum perfil anterior sumir (ADR-0006).
        Path pasta = ctx.publicado().resolve("politicos");
        Path nova = ctx.publicado().resolve("politicos.novo");
        apagar(nova);
        ArrayNode indice = Json.SAIDA.createArrayNode();
        Set<String> publicados = new HashSet<>();
        perfis.sort(Comparator.comparing(p -> Texto.normalizarNome(p.nome)));
        for (Perfil perfil : perfis) {
            ObjectNode documento = documento(perfil, nova);
            List<String> erros = ctx.validador().validar("politico", documento);
            String caminho = "politicos/" + perfil.id + ".json";
            if (!erros.isEmpty()) {
                System.err.println("publish: rejeitado: " + caminho + ": " + String.join("; ", erros));
                rejeitados.add(new Meta.Rejeitado(caminho, "schema: " + String.join("; ", erros.subList(0, Math.min(3, erros.size())))));
                continue;
            }
            Json.gravar(nova.resolve(perfil.id + ".json"), documento);
            indice.add(entradaDoIndice(documento));
            publicados.add(perfil.id);
        }
        aliases.values().removeIf(destino -> !publicados.contains(destino));
        List<String> sumiram = sumiram(anterior, publicados, aliases, permitidos());
        if (!sumiram.isEmpty()) {
            apagar(nova);
            System.err.println("publish: bloqueado: " + sumiram.size() + " perfil(is) da publicação anterior sumiriam: "
                    + String.join(", ", sumiram.subList(0, Math.min(20, sumiram.size()))));
            System.err.println("publish: nada foi gravado; o site continua com a publicação anterior (ADR-0006)");
            return 1;
        }
        apagar(pasta);
        mover(nova, pasta);
        ObjectNode mapa = Json.SAIDA.createObjectNode();
        aliases.forEach(mapa::put);
        gravarValidado("indice", ctx.publicado().resolve("index.json"), Json.SAIDA.createObjectNode()
                .put("gerado_em", ctx.agora().toString())
                .put("total", indice.size())
                .<ObjectNode>set("politicos", indice)
                .set("aliases", mapa));
        gravarValidado("fontes", ctx.publicado().resolve("fontes.json"), Json.arvore(catalogo.itens()));

        List<Meta.StatusFonte> status = new ArrayList<>();
        for (String fonte : FONTES_CLI) {
            Meta.Cursor c = cursores.get(fonte);
            status.add(new Meta.StatusFonte(fonte, c.status(), c.ultimaColetaOk(), c.erros().isEmpty() ? null : c.erros().getFirst()));
        }
        Meta.Execucao execucao = new Meta.Execucao(new Meta.Projeto(Projeto.NOME, Projeto.VERSAO, ctx.repositorioUrl()),
                inicio, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS), status, publicados.size(), rejeitados);
        gravarValidado("execucao", Meta.pasta(ctx).resolve("execucao.json"), Json.arvore(execucao));
        System.out.println("publish: " + publicados.size() + " perfis publicados, " + aliases.size() + " alias(es), " + rejeitados.size() + " rejeição(ões)");
        rejeitados.stream().limit(10).forEach(r -> System.out.println("publish: rejeitado " + r.arquivo() + ": " + r.motivo()));
        return publicados.isEmpty() ? 1 : 0;
    }

    private ObjectNode documento(Perfil perfil, Path pasta) {
        ObjectNode doc = Json.SAIDA.createObjectNode();
        doc.put("id", perfil.id).put("nome", perfil.nome).put("cargo_atual", perfil.cargo).put("esfera", perfil.esfera)
                .put("ente", perfil.ente).put("uf", perfil.uf).put("partido_atual", perfil.partido)
                .put("situacao", perfil.situacao).put("em_exercicio", perfil.emExercicio).put("foto_url", perfil.foto);
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
        Map<String, List<JsonNode>> todos = registrosDoPerfil(perfil);
        for (String bloco : blocosDoPerfil(perfil)) {
            List<JsonNode> registros = seguros(perfil, bloco, todos.getOrDefault(bloco, List.of()));
            blocos.set(bloco, bloco(perfil, bloco, registros, pasta));
        }
        return doc;
    }

    /** Registros em memória mais os blocos da casa legislativa lidos do normalizado agora, só para este perfil. */
    private static Map<String, List<JsonNode>> registrosDoPerfil(Perfil perfil) {
        Map<String, List<JsonNode>> registros = new LinkedHashMap<>();
        perfil.registros.forEach((bloco, lista) -> registros.put(bloco, new ArrayList<>(lista)));
        for (Path arquivo : perfil.arquivosDeBlocos) {
            JsonNode blocos = Json.ler(arquivo);
            for (var bloco : blocos.properties()) {
                if (!bloco.getKey().equals("fontes") && !bloco.getKey().equals("mandatos")) {
                    List<JsonNode> lista = registros.computeIfAbsent(bloco.getKey(), k -> new ArrayList<>());
                    bloco.getValue().forEach(lista::add);
                }
            }
        }
        return registros;
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
        } else if (bloco.equals("despesas_executivo")) {
            despesasAgregadas(registros, no);
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

    /**
     * Despesas que já chegam somadas por mês e órgão (ADR-0008): o total conta os pagamentos da fonte,
     * e não há arquivo de detalhe, porque nenhum lançamento individual é publicado.
     */
    private static void despesasAgregadas(List<JsonNode> registros, ObjectNode no) {
        Map<String, BigDecimal[]> porMes = new TreeMap<>();
        Map<String, BigDecimal[]> porCategoria = new TreeMap<>();
        Map<Integer, JsonNode> provPorAno = new HashMap<>();
        Map<String, JsonNode> provPorMes = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        int pagamentos = 0;
        for (JsonNode r : registros) {
            BigDecimal valor = r.get("valor").decimalValue();
            int ano = r.get("ano").asInt();
            int quantidade = r.get("quantidade").asInt();
            String chaveMes = ano + "|" + String.format("%02d", r.get("mes").asInt());
            total = total.add(valor);
            pagamentos += quantidade;
            somar(porMes, chaveMes, valor, quantidade);
            somar(porCategoria, ano + "|" + r.get("categoria").stringValue(), valor, quantidade);
            provPorAno.putIfAbsent(ano, r);
            provPorMes.putIfAbsent(chaveMes, r);
        }
        no.put("total", pagamentos);
        no.put("valor_total", total);
        ArrayNode mensal = no.putArray("por_mes");
        porMes.forEach((chave, soma) -> {
            String[] partes = chave.split("\\|", 2);
            mensal.add(agregado(provPorMes.get(chave), Integer.parseInt(partes[0]), Integer.parseInt(partes[1]), null, soma));
        });
        ArrayNode categorias = no.putArray("por_categoria");
        porCategoria.forEach((chave, soma) -> {
            String[] partes = chave.split("\\|", 2);
            categorias.add(agregado(provPorAno.get(Integer.parseInt(partes[0])), Integer.parseInt(partes[0]), null, partes[1], soma));
        });
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
        somar(mapa, chave, valor, 1);
    }

    private static void somar(Map<String, BigDecimal[]> mapa, String chave, BigDecimal valor, int quantidade) {
        BigDecimal[] atual = mapa.computeIfAbsent(chave, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        atual[0] = atual[0].add(valor);
        atual[1] = atual[1].add(BigDecimal.valueOf(quantidade));
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
        for (var item : porAno.entrySet()) {
            String caminho = "politicos/" + perfil.id + "/" + bloco + "-" + item.getKey() + ".json.gz";
            ObjectNode detalhe = Json.SAIDA.createObjectNode().put("politico_id", perfil.id).put("tipo", bloco)
                    .put("ano", item.getKey()).put("gerado_em", ctx.agora().toString());
            ArrayNode lista = detalhe.putArray("registros");
            item.getValue().forEach(lista::add);
            List<String> erros = ctx.validador().validar("detalhe", detalhe);
            if (!erros.isEmpty()) {
                System.err.println("publish: rejeitado: " + caminho + ": " + String.join("; ", erros));
                rejeitados.add(new Meta.Rejeitado(caminho, "schema: " + String.join("; ", erros.subList(0, Math.min(3, erros.size())))));
                continue;
            }
            // Detalhe anual compactado: repete chaves e textos em centenas de registros e cai a cerca de 1/10.
            Json.gravarGz(pasta.resolve(perfil.id).resolve(bloco + "-" + item.getKey() + ".json.gz"), detalhe);
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
        e.set("situacao", doc.get("situacao"));
        e.set("em_exercicio", doc.get("em_exercicio"));
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

    /** Publicação anterior: id canônico por identificador externo, ids publicados e aliases. */
    record Anterior(Map<String, String> porChave, Set<String> ids, Map<String, String> aliases) {
    }

    private Anterior anterior() {
        Path indice = ctx.publicado().resolve("index.json");
        Map<String, String> porChave = new HashMap<>();
        Set<String> ids = new HashSet<>();
        Map<String, String> aliases = new HashMap<>();
        if (!Files.exists(indice)) {
            return new Anterior(porChave, ids, aliases);
        }
        JsonNode documento = Json.ler(indice);
        for (JsonNode p : documento.path("politicos")) {
            String id = p.get("id").stringValue();
            ids.add(id);
            JsonNode externos = p.path("ids_externos");
            if (externos.hasNonNull("camara")) {
                porChave.put("camara:" + externos.get("camara").stringValue(), id);
            }
            if (externos.hasNonNull("senado")) {
                porChave.put("senado:" + externos.get("senado").stringValue(), id);
            }
            if (externos.hasNonNull("alesp")) {
                porChave.put("alesp:" + externos.get("alesp").stringValue(), id);
            }
            if (externos.hasNonNull("sapl")) {
                porChave.put("sapl:" + externos.get("sapl").stringValue(), id);
            }
            for (var tse : externos.path("tse").properties()) {
                porChave.put("tse:" + tse.getValue().stringValue(), id);
            }
        }
        for (var alias : documento.path("aliases").properties()) {
            aliases.put(alias.getKey(), alias.getValue().stringValue());
        }
        return new Anterior(porChave, ids, aliases);
    }

    /**
     * Identificador estável (ADR-0002, itens 5 e 6; ADR-0006): o perfil mantém o id já publicado para qualquer um
     * dos seus identificadores externos. O id que deixou de ser canônico vira alias, e alias publicado continua valendo.
     */
    static Map<String, String> identificadores(List<Perfil> perfis, Anterior anterior) {
        Set<String> tomados = new HashSet<>();
        Map<Perfil, String> derivados = new HashMap<>();
        // Dono de cada id derivado. Um perfil que mantém o id derivado não entra em `tomados`, então sem isto outro perfil
        // da mesma pessoa, ainda não fundido, levaria o id dele pela chave da casa ou do TSE e os dois sairiam com o mesmo id.
        Map<String, Perfil> donos = new HashMap<>();
        for (Perfil perfil : perfis) {
            derivados.put(perfil, perfil.id);
            donos.putIfAbsent(perfil.id, perfil);
        }
        // Primeiro as chaves das casas (a principal e os ids externos): o id publicado de um perfil é dele.
        // Só depois as chaves do TSE, que podem apontar para o id de outro perfil da mesma pessoa ainda não fundido.
        for (Perfil perfil : perfis) {
            for (String chave : chavesDasCasas(perfil)) {
                String publicado = anterior.porChave().get(chave);
                if (publicado != null && adotavel(publicado, perfil, tomados, donos)) {
                    perfil.id = publicado;
                    tomados.add(publicado);
                    break;
                }
            }
        }
        for (Perfil perfil : perfis) {
            if (!perfil.id.equals(derivados.get(perfil)) || tomados.contains(perfil.id)) {
                continue;
            }
            for (String chave : chavesDoTse(perfil)) {
                String publicado = anterior.porChave().get(chave);
                if (publicado != null && adotavel(publicado, perfil, tomados, donos)) {
                    perfil.id = publicado;
                    tomados.add(publicado);
                    break;
                }
            }
        }
        tomados.clear();
        for (Perfil perfil : perfis) {
            String derivado = derivados.get(perfil);
            if (!tomados.add(perfil.id)) {
                throw new IllegalStateException("dois perfis com o identificador " + perfil.id);
            }
            perfil.aliases.add(derivado);
            // Perfis que a publicação anterior tinha em separado e agora são a mesma pessoa: o id antigo vira alias.
            for (String chave : chaves(perfil)) {
                String publicado = anterior.porChave().get(chave);
                if (publicado != null && !publicado.equals(perfil.id)) {
                    perfil.aliases.add(publicado);
                }
            }
        }
        Map<String, String> destinoAtual = new HashMap<>();
        for (Perfil perfil : perfis) {
            perfil.aliases.forEach(a -> destinoAtual.putIfAbsent(a, perfil.id));
            destinoAtual.put(perfil.id, perfil.id);
        }
        Map<String, String> aliases = new TreeMap<>();
        anterior.aliases().forEach((alias, destino) -> {
            String atual = destinoAtual.get(destino);
            if (atual != null) {
                aliases.put(alias, atual);
            }
        });
        for (Perfil perfil : perfis) {
            perfil.aliases.forEach(a -> aliases.putIfAbsent(a, perfil.id));
        }
        aliases.keySet().removeIf(tomados::contains);
        return aliases;
    }

    /** Chaves de busca na publicação anterior: a principal, as das casas e as do TSE, da eleição mais recente para a mais antiga. */
    /** O id publicado serve a este perfil se ninguém o tomou e se não é o id derivado de outro perfil. */
    private static boolean adotavel(String publicado, Perfil perfil, Set<String> tomados, Map<String, Perfil> donos) {
        Perfil dono = donos.get(publicado);
        return !tomados.contains(publicado) && (dono == null || dono == perfil);
    }

    private static List<String> chaves(Perfil perfil) {
        List<String> chaves = new ArrayList<>(chavesDasCasas(perfil));
        chaves.addAll(chavesDoTse(perfil));
        return chaves;
    }

    private static List<String> chavesDasCasas(Perfil perfil) {
        List<String> chaves = new ArrayList<>();
        chaves.add(perfil.chaveExterna);
        perfil.ids.forEach((fonte, id) -> chaves.add(fonte + ":" + id));
        return chaves;
    }

    private static List<String> chavesDoTse(Perfil perfil) {
        List<String> chaves = new ArrayList<>();
        new TreeMap<>(perfil.tse).descendingMap().values().forEach(sq -> chaves.add("tse:" + sq));
        return chaves;
    }

    /** Ids e aliases da publicação anterior que não aparecem na nova, fora os liberados em ETL_PERMITIR_REMOCAO. */
    static List<String> sumiram(Anterior anterior, Set<String> publicados, Map<String, String> aliases, Set<String> permitidos) {
        Set<String> antes = new TreeSet<>(anterior.ids());
        antes.addAll(anterior.aliases().keySet());
        List<String> sumiram = new ArrayList<>();
        for (String id : antes) {
            if (!publicados.contains(id) && !aliases.containsKey(id) && !permitidos.contains(id)) {
                sumiram.add(id);
            }
        }
        return sumiram;
    }

    private static Set<String> permitidos() {
        String lista = System.getenv("ETL_PERMITIR_REMOCAO");
        Set<String> ids = new HashSet<>();
        if (lista != null) {
            for (String id : lista.split(",")) {
                if (!id.isBlank()) {
                    ids.add(id.strip());
                }
            }
        }
        return ids;
    }

    private static void mover(Path origem, Path destino) {
        try {
            Files.move(origem, destino, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
