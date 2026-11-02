package etl.fontes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
import etl.Csv;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Orientacao;
import etl.Registros.Despesa;
import etl.Registros.Discurso;
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

/** Senado Federal: dados abertos legislativos e CEAPS (docs/FONTES.md). */
public final class Senado implements Fonte {

    static final String API = "https://legis.senado.leg.br/dadosabertos";
    static final String CEAPS = "https://www.senado.leg.br/transparencia/LAI/verba";
    private static final String JSON = "application/json";
    private static final DateTimeFormatter COMPACTA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> VOTO_REGISTRADO = Set.of("Sim", "Não", "Abstenção", "Obstrução", "Votou");

    record Legislatura(int numero, LocalDate inicio, LocalDate fim) {
    }

    @Override
    public String nome() {
        return "senado";
    }

    // ---------------------------------------------------------------- fetch

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        Coleta.Arquivo lista = coleta.obter(API + "/senador/lista/atual", "senadores.json", JSON, Politica.SEMPRE);
        List<JsonNode> atuais = parlamentares(Json.ler(Coleta.caminho(ctx, lista)));
        Legislatura leg = legislatura(atuais, ctx.hoje());
        // Quem exerceu mandato desde a primeira legislatura coberta continua no arquivo (ADR-0006).
        List<JsonNode> porLegislatura = new ArrayList<>();
        for (int numero = Camara.PRIMEIRA_LEGISLATURA; numero <= leg.numero(); numero++) {
            Coleta.Arquivo arq = coleta.obter(API + "/senador/lista/legislatura/" + numero, "legislaturas/" + numero + ".json",
                    JSON, numero == leg.numero() ? Politica.SEMPRE : Politica.SE_AUSENTE);
            porLegislatura.addAll(daLegislatura(Json.ler(Coleta.caminho(ctx, arq))));
        }
        LocalDate inicio = inicioDaCobertura(porLegislatura);
        Map<String, JsonNode> membros = membros(atuais, porLegislatura, inicio, ctx.hoje());
        Set<String> emExercicio = codigos(atuais);
        coleta.contar("senadores", atuais.size());
        coleta.contar("senadores_fora_de_exercicio", membros.size() - atuais.size());

        YearMonth atual = YearMonth.from(ctx.hoje());
        for (YearMonth mes = YearMonth.from(inicio); !mes.isAfter(atual); mes = mes.plusMonths(1)) {
            Politica politica = mes.isBefore(atual.minusMonths(1)) ? Politica.SE_AUSENTE : Politica.SEMPRE;
            LocalDate de = mes.atDay(1).isBefore(inicio) ? inicio : mes.atDay(1);
            LocalDate ate = mes.equals(atual) ? ctx.hoje() : mes.atEndOfMonth();
            coleta.obter(API + "/votacao?dataInicio=" + de + "&dataFim=" + ate, "votacao/" + mes + ".json", JSON, politica);
            coleta.obter(API + "/plenario/votacao/orientacaoBancada/" + de.format(COMPACTA) + "/" + ate.format(COMPACTA),
                    "orientacao/" + mes + ".json", JSON, politica);
        }
        for (int ano = inicio.getYear(); ano <= atual.getYear(); ano++) {
            coleta.obter(CEAPS + "/despesa_ceaps_" + ano + ".csv", "ceaps/despesa_ceaps_" + ano + ".csv", "*/*",
                    Politica.porAno(ano, ctx.hoje()));
        }

        List<String> falhas = Camara.paraCadaDeputado(new ArrayList<>(membros.keySet()), codigo -> {
            // Dados de quem saiu mudam pouco: renovação semanal.
            Politica dados = emExercicio.contains(codigo) ? Politica.SEMPRE : Politica.SEMANAL;
            coleta.obter(API + "/senador/" + codigo, "senador/" + codigo + ".json", JSON, dados);
            coleta.obter(API + "/senador/" + codigo + "/mandatos", "mandatos/" + codigo + ".json", JSON, dados);
            coleta.obter(API + "/senador/" + codigo + "/comissoes", "comissoes/" + codigo + ".json", JSON, dados);
            // Sem tramitouLegislaturaAtual: o filtro perde autorias do período e, numa legislatura nova, as da anterior.
            coleta.obter(API + "/processo?codigoParlamentarAutor=" + codigo, "processos/" + codigo + ".json", JSON, dados);
            for (int ano = inicio.getYear(); ano <= atual.getYear(); ano++) {
                LocalDate de = ano == inicio.getYear() ? inicio : LocalDate.of(ano, 1, 1);
                LocalDate ate = ano == atual.getYear() ? ctx.hoje() : LocalDate.of(ano, 12, 31);
                coleta.obter(API + "/senador/" + codigo + "/discursos?dataInicio=" + de.format(COMPACTA) + "&dataFim=" + ate.format(COMPACTA),
                        "discursos/" + codigo + "-" + ano + ".json", JSON, Politica.porAno(ano, ctx.hoje()));
            }
        });
        coleta.contar("senadores_com_falha", falhas.size());
        if (!falhas.isEmpty()) {
            throw new IllegalStateException("coleta incompleta para " + falhas.size() + " senador(es): " + falhas.getFirst());
        }
        coleta.periodo(new Meta.Periodo(inicio, ctx.hoje()));
    }

    static List<JsonNode> parlamentares(JsonNode lista) {
        return Json.lista(lista.path("ListaParlamentarEmExercicio").path("Parlamentares").path("Parlamentar"));
    }

    static List<JsonNode> daLegislatura(JsonNode lista) {
        return Json.lista(lista.path("ListaParlamentarLegislatura").path("Parlamentares").path("Parlamentar"));
    }

    private static Set<String> codigos(List<JsonNode> parlamentares) {
        Set<String> codigos = new HashSet<>();
        parlamentares.forEach(s -> codigos.add(Json.texto(s.path("IdentificacaoParlamentar"), "CodigoParlamentar")));
        return codigos;
    }

    /**
     * Início da cobertura: o da primeira legislatura coberta, lido dos mandatos da lista daquela legislatura,
     * nunca antes de Camara.INICIO_DA_COBERTURA (ADR-0009).
     */
    static LocalDate inicioDaCobertura(List<JsonNode> porLegislatura) {
        for (JsonNode p : porLegislatura) {
            for (JsonNode m : Json.lista(p.path("Mandatos").path("Mandato"))) {
                for (String campo : List.of("PrimeiraLegislaturaDoMandato", "SegundaLegislaturaDoMandato")) {
                    JsonNode l = m.path(campo);
                    Integer numero = Json.inteiro(l, "NumeroLegislatura");
                    if (numero != null && numero == Camara.PRIMEIRA_LEGISLATURA && Json.texto(l, "DataInicio") != null) {
                        LocalDate inicio = LocalDate.parse(Json.texto(l, "DataInicio"));
                        return inicio.isBefore(Camara.INICIO_DA_COBERTURA) ? Camara.INICIO_DA_COBERTURA : inicio;
                    }
                }
            }
        }
        throw new IllegalStateException("início da legislatura " + Camara.PRIMEIRA_LEGISLATURA + " não encontrado nas listas do Senado");
    }

    /**
     * Senadores em exercício e quem exerceu mandato desde o início da cobertura, inclusive suplente que assumiu.
     * Suplente que nunca assumiu fica de fora. Para quem saiu, vale o registro da legislatura mais recente.
     */
    static Map<String, JsonNode> membros(List<JsonNode> atuais, List<JsonNode> porLegislatura, LocalDate inicio, LocalDate hoje) {
        Map<String, JsonNode> membros = new LinkedHashMap<>();
        for (JsonNode s : atuais) {
            membros.put(Json.texto(s.path("IdentificacaoParlamentar"), "CodigoParlamentar"), s);
        }
        Set<String> emExercicio = Set.copyOf(membros.keySet());
        for (JsonNode p : porLegislatura) {
            String codigo = Json.texto(p.path("IdentificacaoParlamentar"), "CodigoParlamentar");
            if (!emExercicio.contains(codigo) && !exercicios(p, inicio, hoje).isEmpty()) {
                membros.put(codigo, p);
            }
        }
        return membros;
    }

    /** Períodos de exercício que tocam o intervalo coberto, do mais antigo ao mais recente. */
    static List<JsonNode> exercicios(JsonNode parlamentar, LocalDate inicio, LocalDate hoje) {
        List<JsonNode> exercicios = new ArrayList<>();
        for (JsonNode m : Json.lista(parlamentar.path("Mandatos").path("Mandato"))) {
            for (JsonNode e : Json.lista(m.path("Exercicios").path("Exercicio"))) {
                String de = Json.texto(e, "DataInicio");
                String ate = Json.texto(e, "DataFim");
                if (de != null && !LocalDate.parse(de).isAfter(hoje) && (ate == null || !LocalDate.parse(ate).isBefore(inicio))) {
                    exercicios.add(e);
                }
            }
        }
        exercicios.sort(Comparator.comparing(e -> Json.texto(e, "DataInicio")));
        return exercicios;
    }

    /** Situação de quem saiu, pelo último período de exercício: data de saída e causa informadas pelo Senado. */
    static String situacaoDeQuemSaiu(JsonNode parlamentar, LocalDate inicio, LocalDate hoje) {
        List<JsonNode> exercicios = exercicios(parlamentar, inicio, hoje);
        JsonNode ultimo = exercicios.isEmpty() ? null : exercicios.getLast();
        String fim = ultimo == null ? null : Json.texto(ultimo, "DataFim");
        if (fim == null) {
            return "Fora de exercício";
        }
        String causa = Json.texto(ultimo, "DescricaoCausaAfastamento");
        return "Fora de exercício desde " + fim + (causa == null ? "" : " (" + causa + ")");
    }

    /** Legislatura que contém a data, lida dos mandatos dos senadores em exercício. */
    static Legislatura legislatura(List<JsonNode> senadores, LocalDate hoje) {
        for (JsonNode s : senadores) {
            for (String campo : List.of("PrimeiraLegislaturaDoMandato", "SegundaLegislaturaDoMandato")) {
                JsonNode l = s.path("Mandato").path(campo);
                String inicio = Json.texto(l, "DataInicio");
                String fim = Json.texto(l, "DataFim");
                if (inicio != null && fim != null && !hoje.isBefore(LocalDate.parse(inicio)) && !hoje.isAfter(LocalDate.parse(fim))) {
                    return new Legislatura(Json.inteiro(l, "NumeroLegislatura"), LocalDate.parse(inicio), LocalDate.parse(fim));
                }
            }
        }
        throw new IllegalStateException("legislatura vigente não encontrada na lista de senadores");
    }

    // ------------------------------------------------------------ normalize

    private static final class Acumulado {
        final List<Voto> votos = new ArrayList<>();
        final List<Despesa> despesas = new ArrayList<>();
        final Map<String, Map<String, Integer>> porTipo = new TreeMap<>();
        final Map<String, int[]> participacao = new TreeMap<>();
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqLista = arquivo(ctx, "senadores.json");
        List<JsonNode> atuais = parlamentares(Json.ler(Coleta.caminho(ctx, arqLista)));
        Legislatura leg = legislatura(atuais, ctx.hoje());
        List<JsonNode> porLegislatura = new ArrayList<>();
        Map<String, Coleta.Arquivo> arqDaLegislatura = new HashMap<>();
        for (int numero = Camara.PRIMEIRA_LEGISLATURA; numero <= leg.numero(); numero++) {
            Coleta.Arquivo arq = arquivo(ctx, "legislaturas/" + numero + ".json");
            for (JsonNode p : daLegislatura(Json.ler(Coleta.caminho(ctx, arq)))) {
                porLegislatura.add(p);
                arqDaLegislatura.put(Json.texto(p.path("IdentificacaoParlamentar"), "CodigoParlamentar"), arq);
            }
        }
        LocalDate inicio = inicioDaCobertura(porLegislatura);
        Map<String, JsonNode> membros = membros(atuais, porLegislatura, inicio, ctx.hoje());
        Set<String> emExercicio = codigos(atuais);
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        Map<String, String> porNome = new HashMap<>();
        List<Pessoa> pessoas = new ArrayList<>();
        for (var membro : membros.entrySet()) {
            String codigo = membro.getKey();
            JsonNode s = membro.getValue();
            JsonNode id = s.path("IdentificacaoParlamentar");
            boolean atual = emExercicio.contains(codigo);
            acumulados.put(codigo, new Acumulado());
            porNome.put(Texto.normalizarNome(Json.texto(id, "NomeParlamentar")), codigo);
            Prov prov = Prov.de("senado-api", API + "/senador/" + codigo, atual ? arqLista : arqDaLegislatura.get(codigo));
            List<LinkOficial> links = new ArrayList<>();
            String pagina = https(Json.texto(id, "UrlPaginaParlamentar"));
            if (pagina != null) {
                links.add(new LinkOficial("Página no Senado Federal", pagina, prov));
            }
            String particular = Json.texto(id, "UrlPaginaParticular");
            if (particular != null && particular.matches("^https?://\\S+$")) {
                links.add(new LinkOficial("Site informado ao Senado", particular, prov));
            }
            String ufDoMandato = atual ? Json.texto(s.path("Mandato"), "UfParlamentar")
                    : Json.lista(s.path("Mandatos").path("Mandato")).stream().map(m -> Json.texto(m, "UfParlamentar"))
                            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            pessoas.add(new Pessoa("senado", codigo, Json.texto(id, "NomeParlamentar"), "senador", "federal", "BR",
                    primeiro(Json.texto(id, "UfParlamentar"), ufDoMandato), Json.texto(id, "SiglaPartidoParlamentar"),
                    https(Json.texto(id, "UrlFotoParlamentar")), atual ? "Exercício" : situacaoDeQuemSaiu(s, inicio, ctx.hoje()),
                    atual, links));
        }

        Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
        votos(ctx, inicio, acumulados, fontes);
        despesas(ctx, inicio, acumulados, porNome, fontes);

        Path saida = ctx.normalizado().resolve("senado");
        for (Pessoa pessoa : pessoas) {
            String codigo = pessoa.idFonte();
            Acumulado a = acumulados.get(codigo);
            Map<String, List<FonteUsada>> fontesDoSenador = new LinkedHashMap<>(fontes);
            List<Participacao> participacao = new ArrayList<>();
            for (var ano : a.participacao.entrySet()) {
                int[] n = ano.getValue();
                participacao.add(new Participacao(ano.getKey(), n[0], n[1], null, a.porTipo.get(ano.getKey()),
                        new Prov("senado-api", API + "/votacao?dataInicio=" + ano.getKey() + "-01-01&dataFim=" + ano.getKey() + "-12-31",
                                fontes.get("votos").getLast().capturadoEm())));
            }
            Json.gravar(saida.resolve("blocos").resolve(codigo + ".json"), new Camara.Blocos(
                    mandatos(ctx, codigo, inicio, pessoa.uf(), pessoa.emExercicio() ? "Exercício" : pessoa.situacao(), fontesDoSenador), participacao, a.votos, a.despesas,
                    proposicoes(ctx, codigo, inicio, fontesDoSenador), discursos(ctx, codigo, inicio, fontesDoSenador), List.of(),
                    comissoes(ctx, codigo, inicio, fontesDoSenador), fontesDoSenador));
        }
        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        System.out.println("senado: " + pessoas.size() + " senadores normalizados");
    }

    private static void votos(Contexto ctx, LocalDate inicio, Map<String, Acumulado> acumulados, Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        YearMonth atual = YearMonth.from(ctx.hoje());
        for (YearMonth mes = YearMonth.from(inicio); !mes.isAfter(atual); mes = mes.plusMonths(1)) {
            Coleta.Arquivo arqVotacao = arquivo(ctx, "votacao/" + mes + ".json");
            Coleta.Arquivo arqOrientacao = arquivo(ctx, "orientacao/" + mes + ".json");
            Map<String, Map<String, String>> orientacoes = new HashMap<>();
            for (JsonNode v : Json.lista(Json.ler(Coleta.caminho(ctx, arqOrientacao)).path("votacoes"))) {
                Map<String, String> porPartido = new LinkedHashMap<>();
                for (JsonNode o : Json.lista(v.path("orientacoesLideranca"))) {
                    porPartido.put(Json.texto(o, "partido"), Json.texto(o, "voto"));
                }
                orientacoes.put(Json.texto(v, "sequencialVotacao"), porPartido);
            }
            for (JsonNode v : Json.lista(Json.ler(Coleta.caminho(ctx, arqVotacao)))) {
                String id = Json.texto(v, "codigoSessao") + "-" + Json.texto(v, "codigoSessaoVotacao");
                String data = Json.texto(v, "dataSessao");
                Map<String, String> orientacao = orientacoes.getOrDefault(Json.texto(v, "sequencialVotacao"), Map.of());
                String periodo = data == null ? null : data.substring(0, 4);
                for (JsonNode voto : Json.lista(v.path("votos"))) {
                    Acumulado a = acumulados.get(Json.texto(voto, "codigoParlamentar"));
                    if (a == null) {
                        continue;
                    }
                    String sigla = Json.texto(voto, "siglaVotoParlamentar");
                    String partido = Json.texto(voto, "siglaPartidoParlamentar");
                    Orientacao.Casamento doPartido = Orientacao.doPartido(partido, orientacao);
                    a.votos.add(new Voto(id, data, Json.texto(v, "casaSessao"), Json.texto(v, "identificacao"),
                            Texto.semEntidades(Json.texto(v, "descricaoVotacao")), sigla, partido,
                            doPartido == null ? null : doPartido.orientacao(), doPartido == null ? null : doPartido.bancada(),
                            orientacao.get("Governo"), Prov.de("senado-api", API + "/votacao?codigoSessao=" + Json.texto(v, "codigoSessao"), arqVotacao)));
                    if (periodo != null) {
                        int[] n = a.participacao.computeIfAbsent(periodo, k -> new int[2]);
                        n[0]++;
                        if (VOTO_REGISTRADO.contains(sigla)) {
                            n[1]++;
                        }
                        a.porTipo.computeIfAbsent(periodo, k -> new TreeMap<>()).merge(sigla == null ? "(vazio na fonte)" : sigla, 1, Integer::sum);
                    }
                }
            }
            usadas.add(FonteUsada.de("senado-api", arqVotacao));
        }
        fontes.put("votos", usadas);
        fontes.put("participacao_votacoes", usadas);
    }

    private static void despesas(Contexto ctx, LocalDate inicio, Map<String, Acumulado> acumulados, Map<String, String> porNome,
                                 Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        Set<String> semCorrespondencia = new HashSet<>();
        for (int ano = inicio.getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = arquivo(ctx, "ceaps/despesa_ceaps_" + ano + ".csv");
            Prov prov = Prov.de("senado-ceaps", arq.url(), arq);
            Set<Map<String, String>> vistas = new HashSet<>();
            try (var entrada = Json.abrir(Coleta.caminho(ctx, arq))) {
                Csv.paraCada(entrada, StandardCharsets.ISO_8859_1, 1, linha -> {
                    String nome = Texto.normalizarNome(linha.get("SENADOR"));
                    String codigo = porNome.get(nome);
                    if (codigo == null) {
                        semCorrespondencia.add(nome);
                        return;
                    }
                    if (!vistas.add(linha)) {
                        return;
                    }
                    Despesa d = despesa(linha, prov);
                    if (d != null) {
                        acumulados.get(codigo).despesas.add(d);
                    }
                });
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            usadas.add(FonteUsada.de("senado-ceaps", arq));
        }
        fontes.put("despesas", usadas);
        System.out.println("senado: " + semCorrespondencia.size() + " nome(s) do CEAPS sem senador em exercício (ex-senadores ou suplentes fora de exercício)");
    }

    static Despesa despesa(Map<String, String> linha, Prov prov) {
        Integer ano = inteiro(linha.get("ANO"));
        Integer mes = inteiro(linha.get("MES"));
        BigDecimal valor = Texto.valor(linha.get("VALOR_REEMBOLSADO"));
        if (ano == null || mes == null || mes < 1 || mes > 12 || valor == null) {
            return null;
        }
        String data = Texto.dataBr(linha.get("DATA"));
        return new Despesa(data == null ? null : LocalDate.parse(data), ano, mes,
                primeiro(Texto.vazioParaNulo(linha.get("TIPO_DESPESA")), "sem categoria na fonte"),
                Guarda.semCpf(linha.get("FORNECEDOR")), Texto.cnpj(linha.get("CNPJ_CPF")), valor,
                Texto.vazioParaNulo(linha.get("DOCUMENTO")), null, prov);
    }

    /** Mandatos que chegam ao período coberto. A legislatura é a primeira do mandato. */
    private static List<Mandato> mandatos(Contexto ctx, String codigo, LocalDate coberturaInicio, String uf, String situacao,
                                          Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "mandatos/" + codigo + ".json");
        List<Mandato> mandatos = new ArrayList<>();
        for (JsonNode m : Json.lista(Json.ler(Coleta.caminho(ctx, arq)).path("MandatoParlamentar").path("Parlamentar").path("Mandatos").path("Mandato"))) {
            String inicio = Json.texto(m.path("PrimeiraLegislaturaDoMandato"), "DataInicio");
            String fim = primeiro(Json.texto(m.path("SegundaLegislaturaDoMandato"), "DataFim"), Json.texto(m.path("PrimeiraLegislaturaDoMandato"), "DataFim"));
            if (fim == null || LocalDate.parse(fim).isBefore(coberturaInicio)) {
                continue;
            }
            mandatos.add(new Mandato("senador", "federal", "BR", primeiro(Json.texto(m, "UfParlamentar"), uf),
                    inicio == null ? null : LocalDate.parse(inicio), LocalDate.parse(fim), situacao,
                    Json.texto(m, "DescricaoParticipacao"), Json.inteiro(m.path("PrimeiraLegislaturaDoMandato"), "NumeroLegislatura"),
                    null, null, Prov.de("senado-api", API + "/senador/" + codigo + "/mandatos", arq)));
        }
        fontes.put("mandatos", List.of(FonteUsada.de("senado-api", arq)));
        return mandatos;
    }

    private static List<Proposicao> proposicoes(Contexto ctx, String codigo, LocalDate inicio, Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "processos/" + codigo + ".json");
        List<Proposicao> lista = new ArrayList<>();
        for (JsonNode p : Json.lista(Json.ler(Coleta.caminho(ctx, arq)))) {
            String apresentacao = Json.texto(p, "dataApresentacao");
            if (apresentacao == null || LocalDate.parse(apresentacao.substring(0, 10)).isBefore(inicio)) {
                continue;
            }
            String identificacao = Json.texto(p, "identificacao");
            String[] partes = identificacao == null ? new String[0] : identificacao.split("[ /]");
            lista.add(new Proposicao(Json.texto(p, "id"), partes.length > 0 ? partes[0] : null,
                    partes.length > 1 ? inteiro(partes[1]) : null, partes.length > 2 ? inteiro(partes[2]) : null,
                    primeiro(identificacao, "processo " + Json.texto(p, "id")), Texto.semEntidades(Json.texto(p, "ementa")),
                    Json.texto(p, "situacaoAtual"), null, apresentacao, null, null, Json.texto(p, "urlDocumento"),
                    Prov.de("senado-api", API + "/processo/" + Json.texto(p, "id"), arq)));
        }
        fontes.put("proposicoes", List.of(FonteUsada.de("senado-api", arq)));
        return lista;
    }

    private static List<Discurso> discursos(Contexto ctx, String codigo, LocalDate inicio, Map<String, List<FonteUsada>> fontes) {
        List<Discurso> lista = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = inicio.getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = arquivo(ctx, "discursos/" + codigo + "-" + ano + ".json");
            JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq)).path("DiscursosParlamentar").path("Parlamentar");
            for (JsonNode d : Json.lista(raiz.path("Pronunciamentos").path("Pronunciamento"))) {
                String data = Json.texto(d, "DataPronunciamento");
                String compacta = data == null ? "" : data.replace("-", "");
                lista.add(new Discurso(data, Json.texto(d.path("TipoUsoPalavra"), "Descricao"), null,
                        Texto.semEntidades(Json.texto(d, "TextoResumo")), Json.texto(d, "UrlTexto"),
                        Prov.de("senado-api", API + "/senador/" + codigo + "/discursos?dataInicio=" + compacta + "&dataFim=" + compacta, arq)));
            }
            usadas.add(FonteUsada.de("senado-api", arq));
        }
        fontes.put("discursos", usadas);
        return lista;
    }

    private static List<OrgaoOuFrente> comissoes(Contexto ctx, String codigo, LocalDate coberturaInicio, Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "comissoes/" + codigo + ".json");
        List<OrgaoOuFrente> lista = new ArrayList<>();
        JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq)).path("MembroComissaoParlamentar").path("Parlamentar");
        for (JsonNode c : Json.lista(raiz.path("MembroComissoes").path("Comissao"))) {
            String fim = Json.texto(c, "DataFim");
            if (fim != null && LocalDate.parse(fim.substring(0, 10)).isBefore(coberturaInicio)) {
                continue;
            }
            JsonNode id = c.path("IdentificacaoComissao");
            String inicio = Json.texto(c, "DataInicio");
            lista.add(new OrgaoOuFrente("comissao", primeiro(Json.texto(id, "NomeComissao"), Json.texto(id, "SiglaComissao")),
                    Json.texto(id, "SiglaComissao"), Json.texto(c, "DescricaoParticipacao"),
                    inicio == null ? null : LocalDate.parse(inicio.substring(0, 10)), fim == null ? null : LocalDate.parse(fim.substring(0, 10)),
                    Prov.de("senado-api", API + "/senador/" + codigo + "/comissoes", arq)));
        }
        fontes.put("orgaos_e_frentes", List.of(FonteUsada.de("senado-api", arq)));
        return lista;
    }

    /** As URLs de foto e de página vêm em http; o mesmo endereço responde em https (redirecionamento oficial). */
    static String https(String url) {
        return url == null ? null : url.replaceFirst("^http://", "https://");
    }

    private static Integer inteiro(String texto) {
        try {
            return texto == null ? null : Integer.valueOf(texto.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Coleta.Arquivo arquivo(Contexto ctx, String relativo) {
        Coleta.Arquivo arquivo = Coleta.metadados(ctx, "senado", relativo);
        if (arquivo == null) {
            throw new IllegalStateException("arquivo bruto ausente: raw/senado/" + relativo + " (rode fetch senado)");
        }
        return arquivo;
    }

    private static String primeiro(String a, String b) {
        return a != null ? a : b;
    }
}
