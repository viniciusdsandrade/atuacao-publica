package etl.fontes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
        List<JsonNode> senadores = parlamentares(Json.ler(Coleta.caminho(ctx, lista)));
        Legislatura leg = legislatura(senadores, ctx.hoje());
        coleta.contar("senadores", senadores.size());

        YearMonth atual = YearMonth.from(ctx.hoje());
        for (YearMonth mes = YearMonth.from(leg.inicio()); !mes.isAfter(atual); mes = mes.plusMonths(1)) {
            Politica politica = mes.isBefore(atual.minusMonths(1)) ? Politica.SE_AUSENTE : Politica.SEMPRE;
            LocalDate de = mes.atDay(1).isBefore(leg.inicio()) ? leg.inicio() : mes.atDay(1);
            LocalDate ate = mes.equals(atual) ? ctx.hoje() : mes.atEndOfMonth();
            coleta.obter(API + "/votacao?dataInicio=" + de + "&dataFim=" + ate, "votacao/" + mes + ".json", JSON, politica);
            coleta.obter(API + "/plenario/votacao/orientacaoBancada/" + de.format(COMPACTA) + "/" + ate.format(COMPACTA),
                    "orientacao/" + mes + ".json", JSON, politica);
        }
        for (int ano = leg.inicio().getYear(); ano <= atual.getYear(); ano++) {
            coleta.obter(CEAPS + "/despesa_ceaps_" + ano + ".csv", "ceaps/despesa_ceaps_" + ano + ".csv", "*/*",
                    Politica.porAno(ano, ctx.hoje()));
        }

        List<String> codigos = new ArrayList<>();
        senadores.forEach(s -> codigos.add(Json.texto(s.path("IdentificacaoParlamentar"), "CodigoParlamentar")));
        List<String> falhas = Camara.paraCadaDeputado(codigos, codigo -> {
            coleta.obter(API + "/senador/" + codigo, "senador/" + codigo + ".json", JSON, Politica.SEMPRE);
            coleta.obter(API + "/senador/" + codigo + "/mandatos", "mandatos/" + codigo + ".json", JSON, Politica.SEMPRE);
            coleta.obter(API + "/senador/" + codigo + "/comissoes", "comissoes/" + codigo + ".json", JSON, Politica.SEMPRE);
            coleta.obter(API + "/processo?codigoParlamentarAutor=" + codigo + "&tramitouLegislaturaAtual=S",
                    "processos/" + codigo + ".json", JSON, Politica.SEMPRE);
            for (int ano = leg.inicio().getYear(); ano <= atual.getYear(); ano++) {
                LocalDate de = ano == leg.inicio().getYear() ? leg.inicio() : LocalDate.of(ano, 1, 1);
                LocalDate ate = ano == atual.getYear() ? ctx.hoje() : LocalDate.of(ano, 12, 31);
                coleta.obter(API + "/senador/" + codigo + "/discursos?dataInicio=" + de.format(COMPACTA) + "&dataFim=" + ate.format(COMPACTA),
                        "discursos/" + codigo + "-" + ano + ".json", JSON, Politica.porAno(ano, ctx.hoje()));
            }
        });
        coleta.contar("senadores_com_falha", falhas.size());
        if (!falhas.isEmpty()) {
            throw new IllegalStateException("coleta incompleta para " + falhas.size() + " senador(es): " + falhas.getFirst());
        }
        coleta.periodo(new Meta.Periodo(leg.inicio(), ctx.hoje()));
    }

    static List<JsonNode> parlamentares(JsonNode lista) {
        return Json.lista(lista.path("ListaParlamentarEmExercicio").path("Parlamentares").path("Parlamentar"));
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
        List<JsonNode> senadores = parlamentares(Json.ler(Coleta.caminho(ctx, arqLista)));
        Legislatura leg = legislatura(senadores, ctx.hoje());
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        Map<String, String> porNome = new HashMap<>();
        List<Pessoa> pessoas = new ArrayList<>();
        for (JsonNode s : senadores) {
            JsonNode id = s.path("IdentificacaoParlamentar");
            String codigo = Json.texto(id, "CodigoParlamentar");
            acumulados.put(codigo, new Acumulado());
            porNome.put(Texto.normalizarNome(Json.texto(id, "NomeParlamentar")), codigo);
            Prov prov = Prov.de("senado-api", API + "/senador/" + codigo, arqLista);
            List<LinkOficial> links = new ArrayList<>();
            String pagina = https(Json.texto(id, "UrlPaginaParlamentar"));
            if (pagina != null) {
                links.add(new LinkOficial("Página no Senado Federal", pagina, prov));
            }
            String particular = Json.texto(id, "UrlPaginaParticular");
            if (particular != null && particular.matches("^https?://\\S+$")) {
                links.add(new LinkOficial("Site informado ao Senado", particular, prov));
            }
            pessoas.add(new Pessoa("senado", codigo, Json.texto(id, "NomeParlamentar"), "senador", "federal", "BR",
                    primeiro(Json.texto(id, "UfParlamentar"), Json.texto(s.path("Mandato"), "UfParlamentar")),
                    Json.texto(id, "SiglaPartidoParlamentar"), https(Json.texto(id, "UrlFotoParlamentar")), "Exercício", links));
        }

        Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
        votos(ctx, leg, acumulados, fontes);
        despesas(ctx, leg, acumulados, porNome, fontes);

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
                    mandatos(ctx, codigo, leg, pessoa.uf(), fontesDoSenador), participacao, a.votos, a.despesas,
                    proposicoes(ctx, codigo, leg, fontesDoSenador), discursos(ctx, codigo, leg, fontesDoSenador), List.of(),
                    comissoes(ctx, codigo, leg, fontesDoSenador), fontesDoSenador));
        }
        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        System.out.println("senado: " + pessoas.size() + " senadores normalizados");
    }

    private static void votos(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados, Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        YearMonth atual = YearMonth.from(ctx.hoje());
        for (YearMonth mes = YearMonth.from(leg.inicio()); !mes.isAfter(atual); mes = mes.plusMonths(1)) {
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

    private static void despesas(Contexto ctx, Legislatura leg, Map<String, Acumulado> acumulados, Map<String, String> porNome,
                                 Map<String, List<FonteUsada>> fontes) {
        List<FonteUsada> usadas = new ArrayList<>();
        Set<String> semCorrespondencia = new HashSet<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
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

    private static List<Mandato> mandatos(Contexto ctx, String codigo, Legislatura leg, String uf, Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "mandatos/" + codigo + ".json");
        List<Mandato> mandatos = new ArrayList<>();
        for (JsonNode m : Json.lista(Json.ler(Coleta.caminho(ctx, arq)).path("MandatoParlamentar").path("Parlamentar").path("Mandatos").path("Mandato"))) {
            String inicio = Json.texto(m.path("PrimeiraLegislaturaDoMandato"), "DataInicio");
            String fim = primeiro(Json.texto(m.path("SegundaLegislaturaDoMandato"), "DataFim"), Json.texto(m.path("PrimeiraLegislaturaDoMandato"), "DataFim"));
            if (fim == null || LocalDate.parse(fim).isBefore(leg.inicio())) {
                continue;
            }
            mandatos.add(new Mandato("senador", "federal", "BR", primeiro(Json.texto(m, "UfParlamentar"), uf),
                    inicio == null ? null : LocalDate.parse(inicio), LocalDate.parse(fim), "Exercício",
                    Json.texto(m, "DescricaoParticipacao"), leg.numero(), null, null,
                    Prov.de("senado-api", API + "/senador/" + codigo + "/mandatos", arq)));
        }
        fontes.put("mandatos", List.of(FonteUsada.de("senado-api", arq)));
        return mandatos;
    }

    private static List<Proposicao> proposicoes(Contexto ctx, String codigo, Legislatura leg, Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "processos/" + codigo + ".json");
        List<Proposicao> lista = new ArrayList<>();
        for (JsonNode p : Json.lista(Json.ler(Coleta.caminho(ctx, arq)))) {
            String apresentacao = Json.texto(p, "dataApresentacao");
            if (apresentacao == null || LocalDate.parse(apresentacao.substring(0, 10)).isBefore(leg.inicio())) {
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

    private static List<Discurso> discursos(Contexto ctx, String codigo, Legislatura leg, Map<String, List<FonteUsada>> fontes) {
        List<Discurso> lista = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();
        for (int ano = leg.inicio().getYear(); ano <= ctx.hoje().getYear(); ano++) {
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

    private static List<OrgaoOuFrente> comissoes(Contexto ctx, String codigo, Legislatura leg, Map<String, List<FonteUsada>> fontes) {
        Coleta.Arquivo arq = arquivo(ctx, "comissoes/" + codigo + ".json");
        List<OrgaoOuFrente> lista = new ArrayList<>();
        JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq)).path("MembroComissaoParlamentar").path("Parlamentar");
        for (JsonNode c : Json.lista(raiz.path("MembroComissoes").path("Comissao"))) {
            String fim = Json.texto(c, "DataFim");
            if (fim != null && LocalDate.parse(fim.substring(0, 10)).isBefore(leg.inicio())) {
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
