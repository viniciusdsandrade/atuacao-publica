package etl.fontes;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.Despesa;
import etl.Registros.Discurso;
import etl.Registros.Evento;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Adaptador do SAPL (Sistema de Apoio ao Processo Legislativo) e do portal da transparência
 * da câmara municipal do piloto (ADR-0007). Cobre a Câmara Municipal de Campinas (IBGE 3509502).
 *
 * Regras da fonte que moldam a coleta (docs/FONTES.md):
 * - a API pagina com page_size de no máximo 100;
 * - o voto individual não traz data nem ordem do dia: a data vem do registro de votação,
 *   que aponta para a ordem do dia ou para o expediente, e esses trazem a data da sessão;
 * - os filtros de intervalo por chave (votacao__gte, sessao_plenaria__gte, ordem__gte) permitem
 *   coletar só a legislatura vigente;
 * - as listas de ordem do dia e de expediente vêm ordenadas por data, com empates instáveis entre páginas.
 *   Por isso são coletadas por sessão, que cabe numa página.
 */
public final class Sapl implements Fonte {

    public static final String FONTE_ID = "campinas-camara-sapl";
    public static final String TRANSPARENCIA_ID = "campinas-camara-transparencia";
    public static final String IBGE = "3509502";
    public static final String UF = "SP";
    public static final String API = "https://sapl.campinas.sp.leg.br/api";
    public static final String SITE = "https://sapl.campinas.sp.leg.br";
    public static final String TRANSPARENCIA = "https://transparencia.campinas.sp.leg.br/api/v1";
    private static final String JSON = "application/json";
    private static final int PAGINA = 100;
    private static final int LIMITE_DE_PAGINAS = 200;
    /** Sessão com mais de 60 dias: os itens votados vêm do cache. Antes disso, renovam toda semana. */
    private static final int DIAS_DE_REVISAO = 60;
    private static final Set<String> VOTOS_REGISTRADOS = Set.of("Sim", "Não", "Abstenção");

    private static final Pattern RESULTADO = Pattern.compile("Votação: (.+?) - Matéria: (.+)$");
    private static final Pattern ITEM = Pattern.compile("^\\d+ - (.+)$");

    public record Legislatura(int id, LocalDate inicio, LocalDate fim) {
    }

    public record MandatoSapl(String parlamentarId, LocalDate inicio, LocalDate fim, boolean titular,
                              String tipoAfastamento, String situacao, boolean emExercicio) {
    }

    /** Sessão plenária: a data de votos, presenças e falas vem daqui. */
    public record Sessao(int id, LocalDate data, String hora, String tipo, Integer numero, Integer legislatura) {
    }

    /** Item da ordem do dia ou do expediente: liga o registro de votação à data e à matéria. */
    public record Item(int id, LocalDate data, String materia) {
    }

    /** Registro de votação: liga o voto individual ao item votado. */
    public record RegistroVotacao(int id, Integer ordem, Integer expediente, String resultado, String materia) {
    }

    public record Filiacao(String partido, LocalDate inicio, LocalDate fim) {
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

        Coleta.Arquivo arqMandatos = coleta.obter(API + "/parlamentares/mandato/?legislatura=" + atual.id() + "&page_size=" + PAGINA,
                "mandatos/" + atual.id() + ".json", JSON, Politica.SEMPRE);
        Coleta.Arquivo arqAtivos = coleta.obter(API + "/parlamentares/parlamentar/?ativo=True&page_size=" + PAGINA,
                "parlamentares-ativos.json", JSON, Politica.SEMPRE);

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

        // Sessões, itens votados e registros de votação da legislatura vigente: dão data aos votos e às presenças.
        Coleta.Arquivo arqSessoes = coleta.obter(API + "/sessao/sessaoplenaria/", "sessoes.json", JSON, Politica.SEMANAL);
        coleta.obterOpcional(API + "/materia/tipomaterialegislativa/?page_size=" + PAGINA, "tipos-materia.json", JSON, Politica.SEMANAL);
        Map<Integer, Sessao> sessoes = sessoes(Json.ler(Coleta.caminho(ctx, arqSessoes)), atual);
        List<Sessao> realizadas = realizadas(sessoes, ctx.hoje());
        coleta.contar("sessoes", realizadas.size());
        for (Sessao sessao : realizadas) {
            Politica politica = politicaDaSessao(sessao, ctx.hoje());
            paginas(ctx, coleta, API + "/sessao/ordemdia/?sessao_plenaria=" + sessao.id(), "ordens/" + sessao.id(), politica);
            paginas(ctx, coleta, API + "/sessao/expedientemateria/?sessao_plenaria=" + sessao.id(), "expedientes/" + sessao.id(), politica);
        }

        List<Integer> anos = anos(atual, ctx.hoje());
        Map<Integer, Item> ordens = itens(ctx, "ordens", realizadas);
        Map<Integer, Item> expedientes = itens(ctx, "expedientes", realizadas);
        Integer minOrdem = minimo(ordens.keySet());
        Integer minExpediente = minimo(expedientes.keySet());
        if (minOrdem != null) {
            paginas(ctx, coleta, API + "/sessao/registrovotacao/?ordem__gte=" + minOrdem, "registros/ordem", Politica.SEMANAL);
        }
        if (minExpediente != null) {
            paginas(ctx, coleta, API + "/sessao/registrovotacao/?expediente__gte=" + minExpediente, "registros/expediente",
                    Politica.SEMANAL);
        }
        Integer minRegistro = minimo(registros(ctx).keySet());
        Integer minSessao = minimo(sessoes.keySet());

        // Portal da transparência: ponte idSAPL -> id do portal, e viagens por ano.
        coleta.obterOpcional(TRANSPARENCIA + "/portal/vereadores/filtro_vereador_ativo_inativo/?status=True",
                "transparencia/vereadores-ativos.json", JSON, Politica.SEMANAL);
        coleta.obterOpcional(TRANSPARENCIA + "/portal/vereadores/filtro_vereador_ativo_inativo/?status=False",
                "transparencia/vereadores-inativos.json", JSON, Politica.SEMANAL);
        for (int ano : anos) {
            coleta.obterOpcional(TRANSPARENCIA + "/despesas-parlamentares-e-administrativas/hospedagens-e-viagens-parlamentares/por-ano/?ano=" + ano,
                    "viagens/" + ano + ".json", JSON, politicaAnual(ano, ctx.hoje()));
        }
        Map<String, String> idsTransparencia = idsTransparencia(ctx);

        for (String id : ids) {
            coleta.obter(API + "/parlamentares/parlamentar/" + id + "/", "parlamentares/" + id + ".json", JSON, Politica.SEMANAL);
            coleta.obterOpcional(API + "/parlamentares/filiacao/?parlamentar=" + id, "filiacoes/" + id + ".json", JSON, Politica.SEMANAL);
            if (minRegistro != null) {
                paginas(ctx, coleta, API + "/sessao/votoparlamentar/?parlamentar=" + id + "&votacao__gte=" + minRegistro,
                        "votos/" + id, Politica.SEMANAL);
            }
            if (minSessao != null) {
                paginas(ctx, coleta, API + "/sessao/sessaoplenariapresenca/?parlamentar=" + id + "&sessao_plenaria__gte=" + minSessao,
                        "presencas/" + id, Politica.SEMANAL);
                paginas(ctx, coleta, API + "/sessao/oradorexpediente/?parlamentar=" + id + "&sessao_plenaria__gte=" + minSessao,
                        "oradores-expediente/" + id, Politica.SEMANAL);
                paginas(ctx, coleta, API + "/sessao/orador/?parlamentar=" + id + "&sessao_plenaria__gte=" + minSessao,
                        "oradores-explicacoes/" + id, Politica.SEMANAL);
            }
            Coleta.Arquivo arqAutor = coleta.obterOpcional(API + "/base/autor/?object_id=" + id, "autores/" + id + ".json", JSON,
                    Politica.SEMANAL);
            String autorId = arqAutor == null ? null : autorId(Json.ler(Coleta.caminho(ctx, arqAutor)), id);
            if (autorId != null) {
                for (int ano : anos) {
                    paginas(ctx, coleta, API + "/materia/materialegislativa/?autores=" + autorId + "&ano=" + ano,
                            "materias/" + id + "-" + ano, politicaAnual(ano, ctx.hoje()));
                }
            }
            String idTransparencia = idsTransparencia.get(id);
            if (idTransparencia != null) {
                for (int ano : anos) {
                    Politica politica = politicaAnual(ano, ctx.hoje());
                    coleta.obterOpcional(TRANSPARENCIA + "/despesas-parlamentares-e-administrativas/despesas-com-combustivel-por-vereador/filtro-por-vereador-ano/?vereador="
                            + idTransparencia + "&ano=" + ano, "despesas/" + id + "-" + ano + "-combustivel.json", JSON, politica);
                    coleta.obterOpcional(TRANSPARENCIA + "/despesas-parlamentares-e-administrativas/despesas-com-material-de-escritorio-por-vereador/filtro-por-vereador-ano/?vereador="
                            + idTransparencia + "&ano=" + ano, "despesas/" + id + "-" + ano + "-material.json", JSON, politica);
                }
            }
        }

        coleta.periodo(new Meta.Periodo(atual.inicio(), ctx.hoje()));
    }

    /** Baixa as páginas de uma listagem até a fonte não indicar próxima página. A URL já contém "?". */
    private static void paginas(Contexto ctx, Coleta coleta, String url, String caminho, Politica politica) throws Exception {
        for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
            Coleta.Arquivo arq = coleta.obterOpcional(url + "&page_size=" + PAGINA + "&page=" + p, caminho + "-p" + p + ".json", JSON, politica);
            if (arq == null || proxima(Json.ler(Coleta.caminho(ctx, arq))) == null) {
                return;
            }
        }
    }

    /** Lê as páginas já coletadas de uma listagem, na ordem, até a última. */
    private static List<JsonNode> paginados(Contexto ctx, String caminho) {
        List<JsonNode> itens = new ArrayList<>();
        for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "sapl", caminho + "-p" + p + ".json");
            if (arq == null) {
                return itens;
            }
            JsonNode pagina = Json.ler(Coleta.caminho(ctx, arq));
            itens.addAll(resultados(pagina));
            if (proxima(pagina) == null) {
                return itens;
            }
        }
        return itens;
    }

    private static Coleta.Arquivo primeiraPagina(Contexto ctx, String caminho) {
        return Coleta.metadados(ctx, "sapl", caminho + "-p1.json");
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
        List<Integer> anos = anos(atual, ctx.hoje());

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

        Coleta.Arquivo arqSessoes = Coleta.metadados(ctx, "sapl", "sessoes.json");
        Map<Integer, Sessao> sessoes = arqSessoes == null ? Map.of() : sessoes(Json.ler(Coleta.caminho(ctx, arqSessoes)), atual);
        List<Sessao> realizadas = realizadas(sessoes, ctx.hoje());
        Map<Integer, Item> ordens = itens(ctx, "ordens", realizadas);
        Map<Integer, Item> expedientes = itens(ctx, "expedientes", realizadas);
        Map<Integer, RegistroVotacao> registros = registros(ctx);
        Coleta.Arquivo arqTipos = Coleta.metadados(ctx, "sapl", "tipos-materia.json");
        Map<String, String[]> tipos = arqTipos == null ? Map.of() : tiposDeMateria(Json.ler(Coleta.caminho(ctx, arqTipos)));
        Map<String, String> idsTransparencia = idsTransparencia(ctx);

        Set<String> todosIds = new HashSet<>(porParlamentar.keySet());
        todosIds.addAll(ativosIds);

        List<Pessoa> pessoas = new ArrayList<>();
        Path saida = ctx.normalizado().resolve("sapl");
        Path pastaBlocos = saida.resolve("blocos");
        int votosSemData = 0;

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
            List<Filiacao> filiacoes = arqFil == null ? List.of() : filiacoes(Json.ler(Coleta.caminho(ctx, arqFil)));
            String partido = partidoEm(filiacoes, ctx.hoje());

            List<MandatoSapl> listaMandatos = porParlamentar.getOrDefault(id, List.of());
            boolean emExercicio = listaMandatos.stream().anyMatch(MandatoSapl::emExercicio) || ativosIds.contains(id);
            String situacao = listaMandatos.isEmpty() ? (emExercicio ? "Exercício" : "Sem registro de exercício")
                    : listaMandatos.getFirst().situacao();

            List<LinkOficial> links = List.of(new LinkOficial("Página no SAPL", SITE + "/parlamentar/" + id,
                    Prov.de(FONTE_ID, API + "/parlamentares/parlamentar/" + id + "/", arqP)));

            pessoas.add(new Pessoa("sapl", id, nome, "vereador", "municipal", IBGE, UF, partido, fotoUrl, situacao,
                    emExercicio, links));

            Map<String, List<FonteUsada>> fontesDoVereador = new LinkedHashMap<>();

            // Mandatos
            List<Mandato> mandatosBloco = new ArrayList<>();
            for (MandatoSapl ms : listaMandatos) {
                mandatosBloco.add(new Mandato("vereador", "municipal", IBGE, UF, ms.inicio(), ms.fim(), ms.situacao(),
                        ms.titular() ? "Titular" : "Suplente", atual.id(), null, null,
                        Prov.de(FONTE_ID, API + "/parlamentares/mandato/?legislatura=" + atual.id(), arqMandatos)));
            }
            if (arqMandatos != null) {
                fontesDoVereador.put("mandatos", List.of(FonteUsada.de(FONTE_ID, arqMandatos)));
            }

            // Votos e participação: a data vem do item votado (ordem do dia ou expediente).
            Coleta.Arquivo arqVotos = primeiraPagina(ctx, "votos/" + id);
            List<Voto> votos = new ArrayList<>();
            List<Participacao> participacoes = new ArrayList<>();
            Map<String, int[]> participacaoPorAno = new TreeMap<>();
            Map<String, Map<String, Integer>> votosPorTipo = new TreeMap<>();
            if (arqVotos != null) {
                String urlVotos = API + "/sessao/votoparlamentar/?parlamentar=" + id;
                Prov provVotos = Prov.de(FONTE_ID, urlVotos, arqVotos);
                for (JsonNode v : paginados(ctx, "votos/" + id)) {
                    Integer regId = Json.inteiro(v, "votacao");
                    RegistroVotacao reg = regId == null ? null : registros.get(regId);
                    Item item = reg == null ? null : (reg.ordem() != null ? ordens.get(reg.ordem()) : expedientes.get(reg.expediente()));
                    if (item == null) {
                        votosSemData++;
                        continue;
                    }
                    String votoTxt = Json.texto(v, "voto");
                    String ano = String.valueOf(item.data().getYear());
                    votos.add(new Voto(String.valueOf(regId), item.data().toString(), "PLEN", primeiro(reg.materia(), item.materia()),
                            reg.resultado() == null ? null : "Resultado: " + reg.resultado(), votoTxt,
                            partidoEm(filiacoes, item.data()), null, null, null, provVotos));
                    int[] n = participacaoPorAno.computeIfAbsent(ano, k -> new int[2]);
                    n[0]++;
                    if (registrado(votoTxt)) {
                        n[1]++;
                    }
                    votosPorTipo.computeIfAbsent(ano, k -> new TreeMap<>()).merge(votoTxt == null ? "" : votoTxt, 1, Integer::sum);
                }
                for (var entry : participacaoPorAno.entrySet()) {
                    int[] n = entry.getValue();
                    participacoes.add(new Participacao(entry.getKey(), n[0], n[1], null, votosPorTipo.get(entry.getKey()), provVotos));
                }
                fontesDoVereador.put("votos", List.of(FonteUsada.de(FONTE_ID, arqVotos)));
                fontesDoVereador.put("participacao_votacoes", List.of(FonteUsada.de(FONTE_ID, arqVotos)));
                votos.sort(Comparator.comparing(Voto::data).reversed());
            }

            List<Despesa> despesas = despesas(ctx, id, anos, idsTransparencia.get(id), fontesDoVereador);
            List<Proposicao> proposicoes = proposicoes(ctx, id, anos, tipos, fontesDoVereador);
            List<Discurso> discursos = discursos(ctx, id, sessoes, fontesDoVereador);
            List<Evento> eventos = presencas(ctx, id, sessoes, ctx.hoje(), fontesDoVereador);
            Camara.Blocos blocos = new Camara.Blocos(mandatosBloco, participacoes, votos, despesas, proposicoes, discursos,
                    eventos, List.of(), fontesDoVereador);
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocos);
        }

        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        System.out.println("sapl: " + pessoas.size() + " vereadores normalizados"
                + (votosSemData > 0 ? ", " + votosSemData + " votos sem item votado na coleta" : ""));
    }

    /** Presença em sessão plenária da legislatura vigente, até hoje. A fonte lista só presentes. */
    private static List<Evento> presencas(Contexto ctx, String id, Map<Integer, Sessao> sessoes, LocalDate hoje,
                                          Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = primeiraPagina(ctx, "presencas/" + id);
        if (arq == null) {
            return List.of();
        }
        Prov prov = Prov.de(FONTE_ID, API + "/sessao/sessaoplenariapresenca/?parlamentar=" + id, arq);
        List<Evento> eventos = new ArrayList<>();
        for (JsonNode p : paginados(ctx, "presencas/" + id)) {
            Sessao sessao = sessoes.get(Json.inteiro(p, "sessao_plenaria"));
            if (sessao == null || sessao.data().isAfter(hoje)) {
                continue;
            }
            eventos.add(new Evento("sessao-" + sessao.id(), instante(sessao), "Sessão " + sessao.tipo(), tituloDaSessao(sessao),
                    "Plenário", null, "registrada", prov));
        }
        eventos.sort(Comparator.comparing(Evento::data).reversed());
        fontes.put("eventos", List.of(FonteUsada.de(FONTE_ID, arq)));
        return eventos;
    }

    /** Falas registradas como orador. A fonte traz ordem e tempo, não o texto. */
    private static List<Discurso> discursos(Contexto ctx, String id, Map<Integer, Sessao> sessoes,
                                            Map<String, List<FonteUsada>> fontes) {
        List<Discurso> discursos = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();
        String[][] listas = {
                {"oradores-expediente/" + id, "Orador do Expediente", "/sessao/oradorexpediente/?parlamentar=" + id},
                {"oradores-explicacoes/" + id, "Orador das Explicações Pessoais", "/sessao/orador/?parlamentar=" + id}};
        for (String[] lista : listas) {
            Coleta.Arquivo arq = primeiraPagina(ctx, lista[0]);
            if (arq == null) {
                continue;
            }
            Prov prov = Prov.de(FONTE_ID, API + lista[2], arq);
            for (JsonNode o : paginados(ctx, lista[0])) {
                Sessao sessao = sessoes.get(Json.inteiro(o, "sessao_plenaria"));
                if (sessao == null) {
                    continue;
                }
                Integer ordem = Json.inteiro(o, "numero_ordem");
                String hora = Json.texto(o, "hora_inicio");
                String data = hora != null && hora.matches("^\\d{2}:\\d{2}$") ? sessao.data() + "T" + hora : instante(sessao);
                discursos.add(new Discurso(data, lista[1], ordem == null ? null : ordem + "º orador na " + tituloDaSessao(sessao),
                        Json.texto(o, "observacao"), url(Json.texto(o, "url_discurso")), prov));
            }
            usadas.add(FonteUsada.de(FONTE_ID, arq));
        }
        if (!usadas.isEmpty()) {
            discursos.sort(Comparator.comparing(Discurso::data).reversed());
            fontes.put("discursos", usadas);
        }
        return discursos;
    }

    /** Matérias legislativas de autoria do vereador, por ano da legislatura. */
    private static List<Proposicao> proposicoes(Contexto ctx, String id, List<Integer> anos, Map<String, String[]> tipos,
                                                Map<String, List<FonteUsada>> fontes) {
        List<Proposicao> proposicoes = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano : anos) {
            String caminho = "materias/" + id + "-" + ano;
            Coleta.Arquivo arq = primeiraPagina(ctx, caminho);
            if (arq == null) {
                continue;
            }
            for (JsonNode m : paginados(ctx, caminho)) {
                String idMateria = Json.texto(m, "id");
                if (idMateria == null) {
                    continue;
                }
                String[] tipo = tipos.get(Json.texto(m, "tipo"));
                String identificacao = primeiro(Json.texto(m, "__str__"),
                        (tipo == null ? "Matéria" : tipo[1]) + " nº " + Json.texto(m, "numero") + " de " + Json.texto(m, "ano"));
                Boolean emTramitacao = m.path("em_tramitacao").isBoolean() ? m.path("em_tramitacao").asBoolean() : null;
                String situacao = primeiro(Json.texto(m, "resultado"),
                        emTramitacao == null ? null : (emTramitacao ? "Em tramitação" : "Tramitação encerrada"));
                proposicoes.add(new Proposicao(idMateria, tipo == null ? null : tipo[0], Json.inteiro(m, "numero"), Json.inteiro(m, "ano"),
                        identificacao, Json.texto(m, "ementa"), situacao, List.of(), Json.texto(m, "data_apresentacao"), null, null,
                        url(Json.texto(m, "texto_original")), Prov.de(FONTE_ID, SITE + "/materia/" + idMateria, arq)));
            }
            usadas.add(FonteUsada.de(FONTE_ID, arq));
        }
        if (!usadas.isEmpty()) {
            proposicoes.sort(Comparator.comparing((Proposicao p) -> p.dataApresentacao() == null ? "" : p.dataApresentacao()).reversed());
            fontes.put("proposicoes", usadas);
        }
        return proposicoes;
    }

    /** Despesas do portal da transparência: combustível, material de escritório, hospedagem e passagens. */
    private static List<Despesa> despesas(Contexto ctx, String id, List<Integer> anos, String idTransparencia,
                                          Map<String, List<FonteUsada>> fontes) {
        List<Despesa> despesas = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano : anos) {
            if (idTransparencia != null) {
                despesasPorItem(ctx, "despesas/" + id + "-" + ano + "-combustivel.json", "Combustível", despesas, usadas);
                despesasPorItem(ctx, "despesas/" + id + "-" + ano + "-material.json", "Material de escritório", despesas, usadas);
            }
            Coleta.Arquivo arqViagens = Coleta.metadados(ctx, "sapl", "viagens/" + ano + ".json");
            if (arqViagens == null) {
                continue;
            }
            Prov prov = Prov.de(TRANSPARENCIA_ID, arqViagens.url(), arqViagens);
            boolean usou = false;
            for (JsonNode v : Json.lista(Json.ler(Coleta.caminho(ctx, arqViagens)))) {
                if (!id.equals(Json.texto(v.path("vereador"), "idSAPL"))) {
                    continue;
                }
                usou = true;
                LocalDate data = data(Json.texto(v, "data_inicio"));
                Integer mes = Json.inteiro(v, "mes");
                Integer anoViagem = Json.inteiro(v, "ano");
                if (mes == null || anoViagem == null) {
                    continue;
                }
                BigDecimal hospedagem = Texto.valor(Json.texto(v, "valor_hospedagem"));
                BigDecimal passagens = Texto.valor(Json.texto(v, "valor_passagens"));
                if (hospedagem != null && hospedagem.signum() != 0) {
                    despesas.add(new Despesa(data, anoViagem, mes, "Hospedagem", null, null, hospedagem, null, null, prov));
                }
                if (passagens != null && passagens.signum() != 0) {
                    despesas.add(new Despesa(data, anoViagem, mes, "Passagens", null, null, passagens, null, null, prov));
                }
            }
            if (usou) {
                usadas.add(FonteUsada.de(TRANSPARENCIA_ID, arqViagens));
            }
        }
        if (!usadas.isEmpty()) {
            fontes.put("despesas", usadas);
        }
        return despesas;
    }

    private static void despesasPorItem(Contexto ctx, String caminho, String categoria, List<Despesa> despesas, List<FonteUsada> usadas) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "sapl", caminho);
        if (arq == null) {
            return;
        }
        Prov prov = Prov.de(TRANSPARENCIA_ID, arq.url(), arq);
        for (JsonNode d : Json.lista(Json.ler(Coleta.caminho(ctx, arq)))) {
            BigDecimal valor = Texto.valor(Json.texto(d, "custo_total"));
            Integer mes = Json.inteiro(d, "mes");
            Integer ano = Json.inteiro(d, "ano");
            if (valor == null || mes == null || ano == null) {
                continue;
            }
            despesas.add(new Despesa(data(Json.texto(d, "data")), ano, mes, categoria, null, null, valor, null, null, prov));
        }
        usadas.add(FonteUsada.de(TRANSPARENCIA_ID, arq));
    }

    // ------------------------------------------------------------ helpers

    static List<JsonNode> resultados(JsonNode raiz) {
        if (raiz == null) {
            return List.of();
        }
        JsonNode r = raiz.get("results");
        return r != null && r.isArray() ? Json.lista(r) : Json.lista(raiz);
    }

    static String proxima(JsonNode pagina) {
        return pagina == null ? null : Json.texto(pagina.path("pagination").path("links"), "next");
    }

    /** Anos da legislatura vigente até hoje. */
    static List<Integer> anos(Legislatura leg, LocalDate hoje) {
        List<Integer> anos = new ArrayList<>();
        int fim = Math.min(leg.fim().getYear(), hoje.getYear());
        for (int ano = leg.inicio().getYear(); ano <= fim; ano++) {
            anos.add(ano);
        }
        return anos;
    }

    /** Listagem anual: ano fechado vem do cache; ano aberto renova uma vez por semana, em rodízio. */
    static Politica politicaAnual(int ano, LocalDate hoje) {
        return Politica.porAno(ano, hoje) == Politica.SEMPRE ? Politica.SEMANAL : Politica.SE_AUSENTE;
    }

    static Integer minimo(Set<Integer> ids) {
        Integer min = null;
        for (Integer id : ids) {
            if (min == null || id < min) {
                min = id;
            }
        }
        return min;
    }

    static boolean registrado(String voto) {
        return voto != null && VOTOS_REGISTRADOS.contains(voto);
    }

    /** Sessões plenárias da legislatura. A listagem da fonte ignora paginação e devolve tudo. */
    static Map<Integer, Sessao> sessoes(JsonNode raiz, Legislatura leg) {
        Map<Integer, Sessao> map = new HashMap<>();
        for (JsonNode s : resultados(raiz)) {
            Integer id = Json.inteiro(s, "id");
            Integer legislatura = Json.inteiro(s, "legislatura_numero");
            LocalDate data = data(Json.texto(s, "data_inicio"));
            if (id == null || data == null || legislatura == null || legislatura != leg.id()) {
                continue;
            }
            map.put(id, new Sessao(id, data, Json.texto(s, "hora_inicio"), primeiro(Json.texto(s, "tipo"), "Plenária"),
                    Json.inteiro(s, "numero"), legislatura));
        }
        return map;
    }

    static String instante(Sessao sessao) {
        String hora = sessao.hora();
        return hora != null && hora.matches("^\\d{2}:\\d{2}$") ? sessao.data() + "T" + hora : sessao.data().toString();
    }

    static String tituloDaSessao(Sessao sessao) {
        return (sessao.numero() == null ? "" : sessao.numero() + "ª ") + "Sessão " + sessao.tipo();
    }

    /** Sessões da legislatura já realizadas, da mais antiga para a mais recente. */
    static List<Sessao> realizadas(Map<Integer, Sessao> sessoes, LocalDate hoje) {
        List<Sessao> lista = new ArrayList<>();
        for (Sessao s : sessoes.values()) {
            if (!s.data().isAfter(hoje)) {
                lista.add(s);
            }
        }
        lista.sort(Comparator.comparing(Sessao::data).thenComparing(Sessao::id));
        return lista;
    }

    static Politica politicaDaSessao(Sessao sessao, LocalDate hoje) {
        return sessao.data().isBefore(hoje.minusDays(DIAS_DE_REVISAO)) ? Politica.SE_AUSENTE : Politica.SEMANAL;
    }

    private static Map<Integer, Item> itens(Contexto ctx, String pasta, List<Sessao> sessoes) {
        Map<Integer, Item> map = new HashMap<>();
        for (Sessao sessao : sessoes) {
            for (JsonNode i : paginados(ctx, pasta + "/" + sessao.id())) {
                Item item = item(i, sessao.data());
                if (item != null) {
                    map.put(item.id(), item);
                }
            }
        }
        return map;
    }

    /** Item votado. A data é a do item; sem ela, vale a da sessão. */
    static Item item(JsonNode no, LocalDate dataDaSessao) {
        Integer id = Json.inteiro(no, "id");
        LocalDate data = data(Json.texto(no, "data_ordem"));
        if (data == null) {
            data = dataDaSessao;
        }
        if (id == null || data == null) {
            return null;
        }
        String str = Json.texto(no, "__str__");
        Matcher m = str == null ? null : ITEM.matcher(str);
        return new Item(id, data, m != null && m.matches() ? m.group(1) : str);
    }

    private static Map<Integer, RegistroVotacao> registros(Contexto ctx) {
        Map<Integer, RegistroVotacao> map = new HashMap<>();
        for (String lista : List.of("registros/ordem", "registros/expediente")) {
            for (JsonNode r : paginados(ctx, lista)) {
                RegistroVotacao reg = registro(r);
                if (reg != null) {
                    map.put(reg.id(), reg);
                }
            }
        }
        return map;
    }

    static RegistroVotacao registro(JsonNode no) {
        Integer id = Json.inteiro(no, "id");
        if (id == null) {
            return null;
        }
        String str = Json.texto(no, "__str__");
        Matcher m = str == null ? null : RESULTADO.matcher(str);
        boolean achou = m != null && m.find();
        return new RegistroVotacao(id, Json.inteiro(no, "ordem"), Json.inteiro(no, "expediente"),
                achou ? m.group(1).strip() : null, achou ? m.group(2).strip() : null);
    }

    /** id -> {sigla, descrição} dos tipos de matéria. */
    static Map<String, String[]> tiposDeMateria(JsonNode raiz) {
        Map<String, String[]> map = new HashMap<>();
        for (JsonNode t : resultados(raiz)) {
            String id = Json.texto(t, "id");
            if (id != null) {
                map.put(id, new String[] {Json.texto(t, "sigla"), Json.texto(t, "descricao")});
            }
        }
        return map;
    }

    /** O autor em base/autor que aponta para o parlamentar (object_id). */
    static String autorId(JsonNode raiz, String parlamentarId) {
        for (JsonNode a : resultados(raiz)) {
            if (parlamentarId.equals(Json.texto(a, "object_id"))) {
                return Json.texto(a, "id");
            }
        }
        return null;
    }

    /** idSAPL -> id do vereador no portal da transparência. */
    private static Map<String, String> idsTransparencia(Contexto ctx) {
        Map<String, String> map = new HashMap<>();
        for (String arquivo : List.of("transparencia/vereadores-ativos.json", "transparencia/vereadores-inativos.json")) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "sapl", arquivo);
            if (arq == null) {
                continue;
            }
            for (JsonNode v : Json.lista(Json.ler(Coleta.caminho(ctx, arq)))) {
                String idSapl = Json.texto(v, "idSAPL");
                String id = Json.texto(v, "id");
                if (idSapl != null && id != null) {
                    map.putIfAbsent(idSapl, id);
                }
            }
        }
        return map;
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

    /** Filiações partidárias, da mais recente para a mais antiga. A sigla vem de "Nome - SIGLA - Partido". */
    static List<Filiacao> filiacoes(JsonNode raiz) {
        List<Filiacao> lista = new ArrayList<>();
        for (JsonNode f : resultados(raiz)) {
            String sigla = Json.texto(f, "sigla_partido");
            String str = Json.texto(f, "__str__");
            if (sigla == null && str != null && str.contains(" - ")) {
                String[] partes = str.split(" - ");
                if (partes.length >= 2 && !partes[1].isBlank()) {
                    sigla = partes[1].strip();
                }
            }
            if (sigla != null) {
                lista.add(new Filiacao(sigla, data(Json.texto(f, "data")), data(Json.texto(f, "data_desfiliacao"))));
            }
        }
        lista.sort(Comparator.comparing((Filiacao f) -> f.inicio() == null ? LocalDate.MIN : f.inicio()).reversed());
        return lista;
    }

    /** Partido vigente na data: filiação iniciada até a data e sem desfiliação até ela. */
    static String partidoEm(List<Filiacao> filiacoes, LocalDate data) {
        for (Filiacao f : filiacoes) {
            boolean comecou = f.inicio() == null || !f.inicio().isAfter(data);
            boolean vigente = f.fim() == null || f.fim().isAfter(data);
            if (comecou && vigente) {
                return f.partido();
            }
        }
        return null;
    }

    static String extrairPartido(JsonNode raiz, LocalDate hoje) {
        return partidoEm(filiacoes(raiz), hoje);
    }

    static LocalDate data(String texto) {
        if (texto == null || texto.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(texto.substring(0, 10));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String url(String texto) {
        return texto != null && texto.startsWith("http") ? texto : null;
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
