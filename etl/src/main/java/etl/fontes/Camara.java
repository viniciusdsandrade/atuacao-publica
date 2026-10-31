package etl.fontes;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Orientacao;
import etl.Registros.Despesa;
import etl.Registros.Discurso;
import etl.Registros.Evento;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/** Câmara dos Deputados: API v2, arquivos anuais e cota parlamentar (docs/FONTES.md). */
public final class Camara implements Fonte {

    static final String API = "https://dadosabertos.camara.leg.br/api/v2";
    static final String ARQUIVOS = "https://dadosabertos.camara.leg.br/arquivos";
    static final String COTAS = "https://www.camara.leg.br/cotas";
    private static final String JSON = "application/json";
    private static final List<String> ANUAIS = List.of("votacoes", "votacoesVotos", "votacoesOrientacoes",
            "votacoesObjetos", "proposicoes", "proposicoesAutores", "proposicoesTemas", "eventos", "eventosOrgaos",
            "eventosPresencaDeputados");
    /** A situação das proposições muda com a tramitação: esses arquivos são baixados de novo todo dia. */
    private static final Set<String> SEMPRE_ATUAIS = Set.of("proposicoes", "proposicoesTemas");
    private static final Set<String> VOTO_REGISTRADO = Set.of("Sim", "Não", "Abstenção", "Obstrução");

    record Legislatura(int id, LocalDate inicio, LocalDate fim) {
    }

    @Override
    public String nome() {
        return "camara";
    }

    // ---------------------------------------------------------------- fetch

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        Coleta.Arquivo arquivoLeg = coleta.obter(API + "/legislaturas?data=" + ctx.hoje(), "legislatura.json", JSON, Politica.SEMPRE);
        Legislatura leg = legislatura(Json.ler(Coleta.caminho(ctx, arquivoLeg)));
        Coleta.Arquivo lista = coleta.obter(API + "/deputados?itens=1000", "deputados.json", JSON, Politica.SEMPRE);
        List<String> ids = new ArrayList<>();
        for (JsonNode d : Json.ler(Coleta.caminho(ctx, lista)).path("dados")) {
            ids.add(Json.texto(d, "id"));
        }
        coleta.contar("deputados", ids.size());

        int anoAtual = ctx.hoje().getYear();
        for (int ano = leg.inicio().getYear(); ano <= anoAtual; ano++) {
            Politica doAno = Politica.porAno(ano, ctx.hoje());
            for (String nome : ANUAIS) {
                Politica politica = SEMPRE_ATUAIS.contains(nome) ? Politica.SEMPRE : doAno;
                coleta.obter(ARQUIVOS + "/" + nome + "/json/" + nome + "-" + ano + ".json",
                        "arquivos/" + nome + "-" + ano + ".json", JSON, politica);
            }
            coleta.obter(COTAS + "/Ano-" + ano + ".json.zip", "cotas/Ano-" + ano + ".json.zip", "application/zip", doAno);
        }
        coleta.obter(ARQUIVOS + "/orgaosDeputados/json/orgaosDeputados-L" + leg.id() + ".json",
                "arquivos/orgaosDeputados-L" + leg.id() + ".json", JSON, Politica.SEMPRE);

        List<String> falhas = paraCadaDeputado(ids, id -> coletarDeputado(ctx, coleta, id, leg));
        coleta.contar("deputados_com_falha", falhas.size());
        if (!falhas.isEmpty()) {
            throw new IllegalStateException("coleta incompleta para " + falhas.size() + " deputado(s): " + falhas.getFirst());
        }
        coleta.periodo(new Meta.Periodo(leg.inicio(), ctx.hoje()));
    }

    private static void coletarDeputado(Contexto ctx, Coleta coleta, String id, Legislatura leg) throws Exception {
        coleta.obter(API + "/deputados/" + id, "deputados/" + id + ".json", JSON, Politica.SEMPRE);
        coleta.obter(API + "/deputados/" + id + "/historico", "historico/" + id + ".json", JSON, Politica.SEMPRE);
        coleta.obterOpcional(API + "/deputados/" + id + "/frentes", "frentes/" + id + ".json", JSON, Politica.SEMPRE);
        int anoAtual = ctx.hoje().getYear();
        for (int ano = leg.inicio().getYear(); ano <= anoAtual; ano++) {
            LocalDate de = ano == leg.inicio().getYear() ? leg.inicio() : LocalDate.of(ano, 1, 1);
            LocalDate ate = ano == anoAtual ? ctx.hoje() : LocalDate.of(ano, 12, 31);
            Politica politica = Politica.porAno(ano, ctx.hoje());
            String url = API + "/deputados/" + id + "/discursos?dataInicio=" + de + "&dataFim=" + ate + "&itens=100";
            for (int pagina = 1; url != null; pagina++) {
                Coleta.Arquivo arquivo = coleta.obter(url, discursos(id, ano, pagina), JSON, politica);
                url = proxima(Json.ler(Coleta.caminho(ctx, arquivo)));
            }
        }
    }

    interface PorDeputado {
        void executar(String id) throws Exception;
    }

    /** Roda em threads virtuais. A concorrência real fica limitada pelo cliente HTTP. */
    static List<String> paraCadaDeputado(List<String> ids, PorDeputado tarefa) throws InterruptedException {
        List<String> falhas = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<?>> futuros = new LinkedHashMap<>();
            for (String id : ids) {
                futuros.put(id, executor.submit(() -> {
                    tarefa.executar(id);
                    return null;
                }));
            }
            for (var item : futuros.entrySet()) {
                try {
                    item.getValue().get();
                } catch (java.util.concurrent.ExecutionException e) {
                    falhas.add(item.getKey() + ": " + e.getCause().getMessage());
                }
            }
        }
        return falhas;
    }

    private static String proxima(JsonNode pagina) {
        for (JsonNode link : pagina.path("links")) {
            if ("next".equals(Json.texto(link, "rel"))) {
                return Json.texto(link, "href");
            }
        }
        return null;
    }

    private static String discursos(String id, int ano, int pagina) {
        return "discursos/" + id + "-" + ano + "-p" + pagina + ".json";
    }

    private static Legislatura legislatura(JsonNode resposta) {
        JsonNode dados = resposta.path("dados").get(0);
        return new Legislatura(Json.inteiro(dados, "id"), LocalDate.parse(Json.texto(dados, "dataInicio")),
                LocalDate.parse(Json.texto(dados, "dataFim")));
    }

    // ------------------------------------------------------------ normalize

    /** Blocos de um deputado, antes do vínculo com outras fontes. */
    public record Blocos(List<Mandato> mandatos, List<Participacao> participacaoVotacoes, List<Voto> votos,
                         List<Despesa> despesas, List<Proposicao> proposicoes, List<Discurso> discursos,
                         List<Evento> eventos, List<OrgaoOuFrente> orgaosEFrentes,
                         Map<String, List<FonteUsada>> fontes) {
    }

    /** Estado mutável de um deputado durante a normalização. */
    private static final class Acumulado {
        final List<Voto> votos = new ArrayList<>();
        final List<Despesa> despesas = new ArrayList<>();
        final Set<String> chavesDespesa = new HashSet<>();
        final List<Proposicao> proposicoes = new ArrayList<>();
        final List<Evento> eventos = new ArrayList<>();
        final List<OrgaoOuFrente> orgaos = new ArrayList<>();
        final Map<String, int[]> participacao = new TreeMap<>();
        final Map<String, Map<String, Integer>> porTipo = new TreeMap<>();
    }

    private record Votacao(String orgao, String dataHora, String descricao) {
    }

    @Override
    public void normalize(Contexto ctx) {
        Legislatura leg = legislatura(Json.ler(caminho(ctx, "legislatura.json")));
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, List<Intervalo>> exercicio = new HashMap<>();
        Map<String, Mandato> mandatos = new HashMap<>();
        Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();

        for (JsonNode d : Json.ler(caminho(ctx, "deputados.json")).path("dados")) {
            String id = Json.texto(d, "id");
            acumulados.put(id, new Acumulado());
            Coleta.Arquivo detalheArq = arquivo(ctx, "deputados/" + id + ".json");
            JsonNode detalhe = Json.ler(Coleta.caminho(ctx, detalheArq)).path("dados");
            JsonNode status = detalhe.path("ultimoStatus");
            Prov prov = Prov.de("camara-api", API + "/deputados/" + id, detalheArq);
            List<LinkOficial> links = new ArrayList<>();
            links.add(new LinkOficial("Página na Câmara dos Deputados", "https://www.camara.leg.br/deputados/" + id, prov));
            String site = Json.texto(detalhe, "urlWebsite");
            if (site != null && site.matches("^https?://\\S+$")) {
                links.add(new LinkOficial("Site informado à Câmara", site, prov));
            }
            pessoas.add(new Pessoa("camara", id, primeiro(Json.texto(status, "nomeEleitoral"), Json.texto(status, "nome")),
                    "deputado_federal", "federal", "BR", Json.texto(status, "siglaUf"), Json.texto(status, "siglaPartido"),
                    Json.texto(status, "urlFoto"), Json.texto(status, "situacao"), links));

            Coleta.Arquivo historicoArq = arquivo(ctx, "historico/" + id + ".json");
            List<Intervalo> intervalos = intervalos(Json.ler(Coleta.caminho(ctx, historicoArq)).path("dados"), leg);
            exercicio.put(id, intervalos);
            LocalDate inicioExercicio = intervalos.isEmpty() ? null : intervalos.getFirst().inicio().toLocalDate();
            mandatos.put(id, new Mandato("deputado_federal", "federal", "BR", Json.texto(status, "siglaUf"),
                    inicioExercicio, leg.fim(), Json.texto(status, "situacao"), Json.texto(status, "condicaoEleitoral"),
                    leg.id(), null, null, Prov.de("camara-api", API + "/deputados/" + id + "/historico", historicoArq)));
        }
        fontes.put("mandatos", List.of(FonteUsada.de("camara-api", arquivo(ctx, "deputados.json"))));

        votos(ctx, leg, acumulados, exercicio, fontes);
        despesas(ctx, leg, acumulados, fontes);
        proposicoes(ctx, leg, acumulados, fontes);
        eventos(ctx, leg, acumulados, fontes);
        orgaos(ctx, leg, acumulados, fontes);

        Path saida = ctx.normalizado().resolve("camara");
        for (Pessoa pessoa : pessoas) {
            String id = pessoa.idFonte();
            Acumulado a = acumulados.get(id);
            a.orgaos.addAll(frentes(ctx, id, leg));
            List<Participacao> participacao = new ArrayList<>();
            for (var ano : a.participacao.entrySet()) {
                int[] n = ano.getValue();
                Coleta.Arquivo arq = arquivo(ctx, "arquivos/votacoesVotos-" + ano.getKey() + ".json");
                participacao.add(new Participacao(ano.getKey(), n[0], n[1], n[2], a.porTipo.get(ano.getKey()),
                        Prov.de("camara-arquivos", arq.url(), arq)));
            }
            Map<String, List<FonteUsada>> fontesDoDeputado = new LinkedHashMap<>(fontes);
            Coleta.Arquivo arqFrentes = Coleta.metadados(ctx, "camara", "frentes/" + id + ".json");
            fontesDoDeputado.put("orgaos_e_frentes", arqFrentes == null ? fontes.get("orgaos_e_frentes")
                    : List.of(fontes.get("orgaos_e_frentes").getFirst(), FonteUsada.de("camara-api", arqFrentes)));
            List<Discurso> discursos = discursos(ctx, id, leg, fontesDoDeputado);
            Json.gravar(saida.resolve("blocos").resolve(id + ".json"), new Blocos(List.of(mandatos.get(id)), participacao,
                    a.votos, a.despesas, a.proposicoes, discursos, a.eventos, a.orgaos, fontesDoDeputado));
        }
        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        System.out.println("camara: " + pessoas.size() + " deputados normalizados");
    }

    record Intervalo(LocalDateTime inicio, LocalDateTime fim) {
        boolean contem(LocalDateTime instante) {
            return !instante.isBefore(inicio) && (fim == null || instante.isBefore(fim));
        }
    }

    /** Intervalos com situação "Exercício" na legislatura. Registro de situação nula não muda o intervalo. */
    static List<Intervalo> intervalos(JsonNode historico, Legislatura leg) {
        List<JsonNode> mudancas = new ArrayList<>();
        for (JsonNode h : historico) {
            if (leg.id() == Json.inteiro(h, "idLegislatura") && Json.texto(h, "situacao") != null) {
                mudancas.add(h);
            }
        }
        mudancas.sort(Comparator.comparing(h -> Json.texto(h, "dataHora")));
        List<Intervalo> intervalos = new ArrayList<>();
        for (int i = 0; i < mudancas.size(); i++) {
            if ("Exercício".equals(Json.texto(mudancas.get(i), "situacao"))) {
                LocalDateTime inicio = LocalDateTime.parse(Json.texto(mudancas.get(i), "dataHora"));
                LocalDateTime fim = i + 1 < mudancas.size() ? LocalDateTime.parse(Json.texto(mudancas.get(i + 1), "dataHora")) : null;
                intervalos.add(new Intervalo(inicio, fim));
            }
        }
        return intervalos;
    }

    private static void votos(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados,
                              Map<String, List<Intervalo>> exercicio, Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Map<String, Votacao> votacoes = new HashMap<>();
            Coleta.Arquivo arqVotacoes = arquivo(ctx, "arquivos/votacoes-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqVotacoes), "dados", v -> votacoes.put(Json.texto(v, "id"),
                    new Votacao(Json.texto(v, "siglaOrgao"), primeiro(Json.texto(v, "dataHoraRegistro"), Json.texto(v, "data")),
                            Texto.semEntidades(Json.texto(v, "descricao")))));
            Map<String, String> objetos = new HashMap<>();
            Coleta.Arquivo arqObjetos = arquivo(ctx, "arquivos/votacoesObjetos-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqObjetos), "dados", o -> objetos.putIfAbsent(Json.texto(o, "idVotacao"),
                    identificacao(o.path("proposicao_"))));
            Map<String, Map<String, String>> orientacoes = new HashMap<>();
            Coleta.Arquivo arqOrientacoes = arquivo(ctx, "arquivos/votacoesOrientacoes-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqOrientacoes), "dados", o -> orientacoes
                    .computeIfAbsent(Json.texto(o, "idVotacao"), k -> new LinkedHashMap<>())
                    .put(Json.texto(o, "siglaBancada"), primeiro(Json.texto(o, "orientacao"), "")));

            Map<String, Map<String, String>> votoPorDeputado = new HashMap<>();
            Set<String> nominais = new HashSet<>();
            Coleta.Arquivo arqVotos = arquivo(ctx, "arquivos/votacoesVotos-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqVotos), "dados", registro -> {
                String idVotacao = Json.texto(registro, "idVotacao");
                nominais.add(idVotacao);
                JsonNode deputado = registro.path("deputado_");
                Acumulado a = acumulados.get(Json.texto(deputado, "id"));
                if (a == null) {
                    return;
                }
                Votacao votacao = votacoes.get(idVotacao);
                String voto = Json.texto(registro, "voto");
                String partido = Json.texto(deputado, "siglaPartido");
                Map<String, String> orientacao = orientacoes.getOrDefault(idVotacao, Map.of());
                Orientacao.Casamento doPartido = Orientacao.doPartido(partido, orientacao);
                a.votos.add(new Voto(idVotacao, primeiro(Json.texto(registro, "dataHoraVoto"), votacao == null ? null : votacao.dataHora()),
                        votacao == null ? null : votacao.orgao(), objetos.get(idVotacao), votacao == null ? null : votacao.descricao(),
                        voto, partido, doPartido == null ? null : doPartido.orientacao(),
                        doPartido == null ? null : doPartido.bancada(), Texto.vazioParaNulo(orientacao.get("Governo")),
                        Prov.de("camara-arquivos", Json.texto(registro, "uriVotacao"), arqVotos)));
                votoPorDeputado.computeIfAbsent(Json.texto(deputado, "id"), k -> new HashMap<>())
                        .put(idVotacao, voto == null ? "(vazio na fonte)" : voto);
            });

            String periodo = String.valueOf(ano);
            for (var item : acumulados.entrySet()) {
                Map<String, String> dele = votoPorDeputado.getOrDefault(item.getKey(), Map.of());
                int[] contagem = new int[3];
                Map<String, Integer> porTipo = new TreeMap<>();
                for (String idVotacao : nominais) {
                    Votacao votacao = votacoes.get(idVotacao);
                    if (votacao == null || !"PLEN".equals(votacao.orgao()) || votacao.dataHora() == null) {
                        continue;
                    }
                    LocalDateTime instante = instante(votacao.dataHora());
                    boolean emExercicio = exercicio.get(item.getKey()).stream().anyMatch(i -> i.contem(instante));
                    if (!emExercicio) {
                        continue;
                    }
                    contagem[0]++;
                    String voto = dele.get(idVotacao);
                    if (voto == null) {
                        contagem[2]++;
                    } else {
                        porTipo.merge(voto, 1, Integer::sum);
                        if (VOTO_REGISTRADO.contains(voto)) {
                            contagem[1]++;
                        }
                    }
                }
                if (contagem[0] > 0) {
                    item.getValue().participacao.put(periodo, contagem);
                    item.getValue().porTipo.put(periodo, porTipo);
                }
            }
            usadas.add(FonteUsada.de("camara-arquivos", arqVotos));
            usadas.add(FonteUsada.de("camara-arquivos", arqVotacoes));
            usadas.add(FonteUsada.de("camara-arquivos", arqOrientacoes));
        }
        fontes.put("votos", usadas);
        fontes.put("participacao_votacoes", usadas);
    }

    private static void despesas(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados,
                                 Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = arquivo(ctx, "cotas/Ano-" + ano + ".json.zip");
            Prov prov = Prov.de("camara-cotas", arq.url(), arq);
            Json.paraCadaNoZip(Coleta.caminho(ctx, arq), "dados", d -> {
                Acumulado a = acumulados.get(Json.texto(d, "idDeputado"));
                if (a == null) {
                    return;
                }
                Despesa despesa = despesa(d, prov);
                if (despesa != null && a.chavesDespesa.add(chaveDespesa(d))) {
                    a.despesas.add(despesa);
                }
            });
            usadas.add(FonteUsada.de("camara-cotas", arq));
        }
        fontes.put("despesas", usadas);
    }

    /** Registro da cota já sem CPF do deputado, de fornecedor pessoa física e de passageiro. */
    static Despesa despesa(JsonNode d, Prov prov) {
        Integer ano = Json.inteiro(d, "ano");
        Integer mes = Json.inteiro(d, "mes");
        BigDecimal valor = Texto.valor(Json.texto(d, "valorLiquido"));
        if (ano == null || mes == null || mes < 1 || mes > 12 || valor == null) {
            return null;
        }
        String emissao = Json.texto(d, "dataEmissao");
        return new Despesa(emissao == null || emissao.length() < 10 ? null : LocalDate.parse(emissao.substring(0, 10)),
                ano, mes, primeiro(Json.texto(d, "descricao"), "sem categoria na fonte"),
                Guarda.semCpf(Json.texto(d, "fornecedor")), Texto.cnpj(Json.texto(d, "cnpjCPF")), valor,
                Json.texto(d, "numero"), Json.texto(d, "urlDocumento"), prov);
    }

    /** Dois registros iguais nestes campos são o mesmo lançamento (docs/METODOLOGIA.md). */
    static String chaveDespesa(JsonNode d) {
        StringBuilder chave = new StringBuilder();
        for (String campo : List.of("idDeputado", "idDocumento", "parcela", "numero", "dataEmissao", "valorLiquido",
                "fornecedor", "cnpjCPF", "mes", "ano", "numeroSubCota")) {
            chave.append(Json.texto(d, campo)).append('|');
        }
        return chave.toString();
    }

    private static void proposicoes(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados,
                                    Map<String, List<FonteUsada>> fontes) {
        record Autoria(String deputado, String proposicao, Integer ordem, Boolean proponente) {
        }
        List<Autoria> autorias = new ArrayList<>();
        Set<String> necessarias = new HashSet<>();
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = arquivo(ctx, "arquivos/proposicoesAutores-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arq), "dados", a -> {
                String deputado = Json.texto(a, "idDeputadoAutor");
                if (deputado != null && acumulados.containsKey(deputado)) {
                    String proposicao = Json.texto(a, "idProposicao");
                    String proponente = Json.texto(a, "proponente");
                    autorias.add(new Autoria(deputado, proposicao, Json.inteiro(a, "ordemAssinatura"),
                            proponente == null ? null : "1".equals(proponente)));
                    necessarias.add(proposicao);
                }
            });
            usadas.add(FonteUsada.de("camara-arquivos", arq));
        }
        Map<String, JsonNode> dados = new HashMap<>();
        Map<String, List<String>> temas = new HashMap<>();
        Map<String, Coleta.Arquivo> origem = new HashMap<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = arquivo(ctx, "arquivos/proposicoes-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arq), "dados", p -> {
                String id = Json.texto(p, "id");
                if (necessarias.contains(id)) {
                    dados.put(id, p);
                    origem.put(id, arq);
                }
            });
            Coleta.Arquivo arqTemas = arquivo(ctx, "arquivos/proposicoesTemas-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqTemas), "dados", t -> {
                String uri = Json.texto(t, "uriProposicao");
                String id = uri == null ? null : uri.substring(uri.lastIndexOf('/') + 1);
                if (necessarias.contains(id) && Json.texto(t, "tema") != null) {
                    List<String> lista = temas.computeIfAbsent(id, k -> new ArrayList<>());
                    if (!lista.contains(Json.texto(t, "tema"))) {
                        lista.add(Json.texto(t, "tema"));
                    }
                }
            });
            usadas.add(FonteUsada.de("camara-arquivos", arq));
            usadas.add(FonteUsada.de("camara-arquivos", arqTemas));
        }
        for (Autoria autoria : autorias) {
            JsonNode p = dados.get(autoria.proposicao());
            if (p == null) {
                continue;
            }
            String tipo = Json.texto(p, "siglaTipo");
            Integer numero = Json.inteiro(p, "numero");
            Integer ano = Json.inteiro(p, "ano");
            acumulados.get(autoria.deputado()).proposicoes.add(new Proposicao(autoria.proposicao(), tipo, numero, ano,
                    tipo + " " + numero + "/" + ano, Texto.semEntidades(Json.texto(p, "ementa")),
                    Json.texto(p.path("ultimoStatus"), "descricaoSituacao"), temas.getOrDefault(autoria.proposicao(), List.of()),
                    Json.texto(p, "dataApresentacao"), autoria.ordem(), autoria.proponente(),
                    Json.texto(p, "urlInteiroTeor"),
                    Prov.de("camara-arquivos", API + "/proposicoes/" + autoria.proposicao(), origem.get(autoria.proposicao()))));
        }
        fontes.put("proposicoes", usadas);
    }

    private static void eventos(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados,
                                Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Map<String, JsonNode> eventos = new HashMap<>();
            Coleta.Arquivo arqEventos = arquivo(ctx, "arquivos/eventos-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqEventos), "dados", e -> eventos.put(Json.texto(e, "id"), e));
            Map<String, String> orgaos = new HashMap<>();
            Coleta.Arquivo arqOrgaos = arquivo(ctx, "arquivos/eventosOrgaos-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqOrgaos), "dados", o -> orgaos.putIfAbsent(Json.texto(o, "idEvento"), Json.texto(o, "siglaOrgao")));
            Coleta.Arquivo arqPresenca = arquivo(ctx, "arquivos/eventosPresencaDeputados-" + ano + ".json");
            Json.paraCada(Coleta.caminho(ctx, arqPresenca), "dados", p -> {
                Acumulado a = acumulados.get(Json.texto(p, "idDeputado"));
                String idEvento = Json.texto(p, "idEvento");
                if (a == null) {
                    return;
                }
                JsonNode e = eventos.get(idEvento);
                a.eventos.add(new Evento(idEvento, primeiro(Json.texto(p, "dataHoraInicio"), e == null ? null : Json.texto(e, "dataHoraInicio")),
                        e == null ? null : Json.texto(e, "descricaoTipo"), e == null ? null : Texto.semEntidades(Json.texto(e, "descricao")),
                        orgaos.get(idEvento), e == null ? null : Json.texto(e, "situacao"), "registrada",
                        Prov.de("camara-arquivos", API + "/eventos/" + idEvento, arqPresenca)));
            });
            usadas.add(FonteUsada.de("camara-arquivos", arqPresenca));
            usadas.add(FonteUsada.de("camara-arquivos", arqEventos));
        }
        fontes.put("eventos", usadas);
    }

    private static void orgaos(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados,
                               Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "arquivos/orgaosDeputados-L" + leg.id() + ".json");
        Json.paraCada(Coleta.caminho(ctx, arq), "dados", o -> {
            String uri = Json.texto(o, "uriDeputado");
            Acumulado a = uri == null ? null : acumulados.get(uri.substring(uri.lastIndexOf('/') + 1));
            if (a == null) {
                return;
            }
            a.orgaos.add(new OrgaoOuFrente("orgao", primeiro(Json.texto(o, "nomeOrgao"), Json.texto(o, "siglaOrgao")),
                    Json.texto(o, "siglaOrgao"), Json.texto(o, "cargo"), data(Json.texto(o, "dataInicio")),
                    data(Json.texto(o, "dataFim")), Prov.de("camara-arquivos", Json.texto(o, "uriOrgao"), arq)));
        });
        fontes.put("orgaos_e_frentes", List.of(FonteUsada.de("camara-arquivos", arq)));
    }

    /** Frentes são dado complementar: sem coleta do deputado, a lista fica vazia (coleta parcial). */
    private static List<OrgaoOuFrente> frentes(Contexto ctx, String id, Legislatura leg) {
        List<OrgaoOuFrente> frentes = new ArrayList<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, "camara", "frentes/" + id + ".json");
        if (arq == null) {
            return frentes;
        }
        for (JsonNode f : Json.ler(Coleta.caminho(ctx, arq)).path("dados")) {
            if (leg.id() == Json.inteiro(f, "idLegislatura")) {
                frentes.add(new OrgaoOuFrente("frente", Json.texto(f, "titulo"), null, null, null, null,
                        Prov.de("camara-api", API + "/frentes/" + Json.texto(f, "id"), arq)));
            }
        }
        return frentes;
    }

    private static List<Discurso> discursos(Contexto ctx, String id, Legislatura leg, Map<String, List<FonteUsada>> fontes) {
        List<Discurso> discursos = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
            for (int pagina = 1; ; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "camara", discursos(id, ano, pagina));
                if (arq == null) {
                    break;
                }
                JsonNode conteudo = Json.ler(Coleta.caminho(ctx, arq));
                for (JsonNode d : conteudo.path("dados")) {
                    String inicio = Json.texto(d, "dataHoraInicio");
                    String dia = inicio == null ? null : inicio.substring(0, 10);
                    discursos.add(new Discurso(inicio, Json.texto(d, "tipoDiscurso"), Json.texto(d.path("faseEvento"), "titulo"),
                            Texto.semEntidades(Json.texto(d, "sumario")), Json.texto(d, "urlTexto"),
                            Prov.de("camara-api", API + "/deputados/" + id + "/discursos?dataInicio=" + dia + "&dataFim=" + dia, arq)));
                }
                if (pagina == 1) {
                    usadas.add(FonteUsada.de("camara-api", arq));
                }
                if (proxima(conteudo) == null) {
                    break;
                }
            }
        }
        fontes.put("discursos", usadas);
        return discursos;
    }

    private static String identificacao(JsonNode proposicao) {
        String titulo = Json.texto(proposicao, "titulo");
        if (titulo != null) {
            return titulo;
        }
        String sigla = Json.texto(proposicao, "siglaTipo");
        return sigla == null ? null : sigla + " " + Json.texto(proposicao, "numero") + "/" + Json.texto(proposicao, "ano");
    }

    private static LocalDateTime instante(String dataHora) {
        return dataHora.length() == 10 ? LocalDate.parse(dataHora).atStartOfDay() : LocalDateTime.parse(dataHora);
    }

    private static LocalDate data(String texto) {
        return texto == null || texto.length() < 10 ? null : LocalDate.parse(texto.substring(0, 10));
    }

    private static Path caminho(Contexto ctx, String relativo) {
        return Coleta.caminho(ctx, arquivo(ctx, relativo));
    }

    private static Coleta.Arquivo arquivo(Contexto ctx, String relativo) {
        Coleta.Arquivo arquivo = Coleta.metadados(ctx, "camara", relativo);
        if (arquivo == null) {
            throw new IllegalStateException("arquivo bruto ausente: raw/camara/" + relativo + " (rode fetch camara)");
        }
        return arquivo;
    }

    private static String primeiro(String a, String b) {
        return a != null ? a : b;
    }
}
