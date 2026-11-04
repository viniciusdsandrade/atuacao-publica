package etl.fontes;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Registros.Despesa;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado de Minas Gerais (ALMG) (ADR-0015).
 * Coleta deputados estaduais da 19ª (2019-2023, cobrindo 2022) e 20ª (2023-2027, cobrindo 2023-2026) legislaturas,
 * detalhes biográficos para vínculo com o TSE, verbas indenizatórias e comissões.
 */
public final class Almg implements Fonte {

    public static final String FONTE_DEPUTADOS = "almg-deputados";
    public static final String FONTE_DETALHES = "almg-deputados-detalhes";
    public static final String FONTE_DESPESAS = "almg-despesas";
    public static final String FONTE_PORTAL = "almg-portal";

    public static final String UF = "MG";
    public static final String API = "https://dadosabertos.almg.gov.br/api/v2";
    public static final String SITE = "https://www.almg.gov.br";

    private static final int LEGISLATURA_19 = 19;
    private static final int LEGISLATURA_20 = 20;

    private static final LocalDate INICIO_LEG_19 = LocalDate.of(2019, 2, 1);
    private static final LocalDate FIM_LEG_19 = LocalDate.of(2023, 1, 31);
    private static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    private static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);

    private static final LocalDate INICIO_COBERTURA = LocalDate.of(2022, 1, 1);
    private static final LocalDate FIM_COBERTURA = LocalDate.of(2026, 12, 31);

    private static final DateTimeFormatter[] FORMATOS_NASCIMENTO = {
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    @Override
    public String nome() {
        return "almg";
    }

    record DeputadoBasico(int id, String nome, String partido, boolean temDetalhe, int codSituacao, int legislatura) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        // Baixa a pesquisa de deputados das legislaturas 19 e 20
        coleta.obter(API + "/legislaturas/pesquisa_deputados?leg=" + LEGISLATURA_19 + "&formato=json",
                "pesquisa-leg-19.json", "application/json", Politica.SEMANAL);
        coleta.obter(API + "/legislaturas/pesquisa_deputados?leg=" + LEGISLATURA_20 + "&formato=json",
                "pesquisa-leg-20.json", "application/json", Politica.SEMANAL);

        Map<Integer, DeputadoBasico> deputados = extrairDeputadosBasicos(ctx);
        for (DeputadoBasico dep : deputados.values()) {
            int id = dep.id();
            // Detalhes biográficos (só se temDetalhe for verdadeiro)
            if (dep.temDetalhe()) {
                coleta.obterOuVazio(API + "/deputados/" + id + "?formato=json",
                        "deputados/" + id + ".json", "application/json", Politica.SEMANAL);
            }

            // Participação em comissões
            coleta.obterOuVazio(API + "/deputados/" + id + "/participacoes_comissoes?formato=json",
                    "comissoes/" + id + ".json", "application/json", Politica.SEMANAL);

            // Datas de fechamento de verbas indenizatórias
            coleta.obterOuVazio(API + "/prestacao_contas/verbas_indenizatorias/deputados/" + id + "/datas?formato=json",
                    "verbas/" + id + "-datas.json", "application/json", Politica.SEMANAL);

            // Coleta meses de verba indenizatória dentro da cobertura (2022 a 2026)
            Coleta.Arquivo arqDatas = Coleta.metadados(ctx, "almg", "verbas/" + id + "-datas.json");
            if (arqDatas != null && Files.exists(Coleta.caminho(ctx, arqDatas)) && arqDatas.bytes() > 0) {
                JsonNode raizDatas = Json.ler(Coleta.caminho(ctx, arqDatas));
                JsonNode listaFechamento = raizDatas.path("listaFechamentoVerba");
                if (listaFechamento.isArray()) {
                    for (JsonNode item : listaFechamento) {
                        String dataRef = extrairData(item.path("dataReferencia"));
                        if (dataRef != null && dataRef.length() >= 7) {
                            int ano = Integer.parseInt(dataRef.substring(0, 4));
                            int mes = Integer.parseInt(dataRef.substring(5, 7));
                            if (ano >= 2022 && ano <= 2026) {
                                String mesFormatado = String.format("%02d", mes);
                                coleta.obterOuVazio(API + "/prestacao_contas/verbas_indenizatorias/deputados/" + id + "/" + ano + "/" + mesFormatado + "?formato=json",
                                        "verbas/" + id + "-" + ano + "-" + mesFormatado + ".json", "application/json", Politica.porAnoSemanal(ano, ctx.hoje()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path pastaRaw = ctx.raw().resolve("almg");
        if (!Files.exists(pastaRaw)) {
            System.out.println("almg: sem dados coletados para normalizar");
            return;
        }

        Map<Integer, DeputadoBasico> deputados = extrairDeputadosBasicos(ctx);
        if (deputados.isEmpty()) {
            System.out.println("almg: nenhum deputado identificado para normalização");
            return;
        }

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();

        Coleta.Arquivo arqLeg20 = Coleta.metadados(ctx, "almg", "pesquisa-leg-20.json");
        Coleta.Arquivo arqLeg19 = Coleta.metadados(ctx, "almg", "pesquisa-leg-19.json");
        Coleta.Arquivo arqBase = arqLeg20 != null ? arqLeg20 : arqLeg19;

        for (DeputadoBasico dep : deputados.values()) {
            int id = dep.id();
            String idStr = String.valueOf(id);
            Acumulado ac = new Acumulado();
            acumulados.put(idStr, ac);

            Coleta.Arquivo arqDet = Coleta.metadados(ctx, "almg", "deputados/" + id + ".json");
            JsonNode det = null;
            if (arqDet != null && Files.exists(Coleta.caminho(ctx, arqDet)) && arqDet.bytes() > 0) {
                det = Json.ler(Coleta.caminho(ctx, arqDet)).path("deputado");
            }

            String nome = det != null && det.hasNonNull("nome") ? det.get("nome").asText() : dep.nome();
            String partido = det != null && det.hasNonNull("partido") ? det.get("partido").asText() : dep.partido();
            int codSituacao = det != null && det.hasNonNull("codigoSituacao") ? det.get("codigoSituacao").asInt() : dep.codSituacao();
            boolean emExercicio = codSituacao == 1;
            String situacao = emExercicio ? "em exercício" : (det != null && det.hasNonNull("situacao") ? det.get("situacao").asText().toLowerCase() : "mandato encerrado");

            String fotoUrl = SITE + "/export/sites/portal/a-assembleia/deputados/fotos/" + id + ".jpg";
            Prov provDep = Prov.de(FONTE_DEPUTADOS, API + "/deputados/" + id, arqDet != null && arqDet.bytes() > 0 ? arqDet : arqBase);

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALMG", SITE + "/deputados/conheca-os-deputados/deputado?idDeputado=" + id, provDep)
            );

            pessoas.add(new Pessoa("almg", idStr, nome, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, situacao, emExercicio, links));

            // Mandatos
            montarMandatos(id, dep, det, ac, provDep);
            ac.fontes.computeIfAbsent("mandatos", k -> new ArrayList<>()).add(FonteUsada.de(FONTE_DEPUTADOS, arqDet != null && arqDet.bytes() > 0 ? arqDet : arqBase));

            // Comissões
            processarComissoes(ctx, id, ac);

            // Verbas indenizatórias
            processarVerbas(ctx, id, ac);
        }

        // Ordenar pessoas por nome
        pessoas.sort(Comparator.comparing(Pessoa::nome));

        // Gravação
        Path saida = ctx.normalizado().resolve("almg");
        Path pastaBlocos = saida.resolve("blocos");
        Files.createDirectories(pastaBlocos);
        Json.gravar(saida.resolve("pessoas.json"), Json.SAIDA.valueToTree(pessoas));

        for (var e : acumulados.entrySet()) {
            String idStr = e.getKey();
            Acumulado ac = e.getValue();
            Blocos blocos = new Blocos(ac.mandatos, ac.despesas, ac.orgaos, ac.fontes);
            Json.gravar(pastaBlocos.resolve(idStr + ".json"), Json.SAIDA.valueToTree(blocos));
        }

        System.out.println("almg: " + pessoas.size() + " deputados estaduais normalizados");
    }

    private Map<Integer, DeputadoBasico> extrairDeputadosBasicos(Contexto ctx) {
        Map<Integer, DeputadoBasico> map = new LinkedHashMap<>();
        for (String arqNome : List.of("pesquisa-leg-20.json", "pesquisa-leg-19.json")) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "almg", arqNome);
            if (arq != null && Files.exists(Coleta.caminho(ctx, arq)) && arq.bytes() > 0) {
                try {
                    JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
                    JsonNode resultado = raiz.path("listaResultadoPesquisaDeputado");
                    if (resultado.isArray()) {
                        for (JsonNode bloco : resultado) {
                            int codSit = bloco.path("codSituacao").asInt(0);
                            int numLeg = bloco.path("numLegislatura").asInt(20);
                            JsonNode lista = bloco.path("lista");
                            if (lista.isArray()) {
                                for (JsonNode d : lista) {
                                    if (d.hasNonNull("id")) {
                                        int id = d.get("id").asInt();
                                        String nome = d.path("nome").asText("Deputado " + id);
                                        String partido = d.path("partido").asText(null);
                                        boolean temDetalhe = d.path("temDetalhe").asBoolean(true);
                                        map.putIfAbsent(id, new DeputadoBasico(id, nome, partido, temDetalhe, codSit, numLeg));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("almg: erro ao ler " + arqNome + ": " + e.getMessage());
                }
            }
        }
        return map;
    }

    private void montarMandatos(int id, DeputadoBasico dep, JsonNode det, Acumulado ac, Prov provDep) {
        if (det != null && det.hasNonNull("legislaturas") && det.path("legislaturas").isArray()) {
            JsonNode legList = det.path("legislaturas");
            for (JsonNode leg : legList) {
                int numLeg = leg.path("numeroLegislatura").asInt();
                if (numLeg != LEGISLATURA_19 && numLeg != LEGISLATURA_20) {
                    continue;
                }
                LocalDate padraoInicio = numLeg == LEGISLATURA_19 ? INICIO_LEG_19 : INICIO_LEG_20;
                LocalDate padraoFim = numLeg == LEGISLATURA_19 ? FIM_LEG_19 : FIM_LEG_20;

                String iniStr = extrairData(leg.path("inicioLegislatura"));
                LocalDate inicio = iniStr != null ? parseDataOu(iniStr, padraoInicio) : padraoInicio;

                String fimStr = extrairData(leg.path("terminoLegislatura"));
                LocalDate fim = fimStr != null ? parseDataOu(fimStr, padraoFim) : padraoFim;

                String condicao = leg.path("tipoMandato").asText(null);
                String situacao = "mandato encerrado";
                if (numLeg == LEGISLATURA_20) {
                    int codSit = det.path("codigoSituacao").asInt(0);
                    situacao = codSit == 1 ? "em exercício" : det.path("situacao").asText("mandato encerrado").toLowerCase();
                }

                int eleicao = numLeg == LEGISLATURA_19 ? 2018 : 2022;

                ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, inicio, fim,
                        situacao, condicao, numLeg, eleicao, null, provDep));
            }
        }
        if (ac.mandatos.isEmpty()) {
            // Mandato sintetizado a partir da pesquisa
            int numLeg = dep.legislatura();
            LocalDate inicio = numLeg == LEGISLATURA_19 ? INICIO_LEG_19 : INICIO_LEG_20;
            LocalDate fim = numLeg == LEGISLATURA_19 ? FIM_LEG_19 : FIM_LEG_20;
            String situacao = dep.codSituacao() == 1 ? "em exercício" : "mandato encerrado";
            int eleicao = numLeg == LEGISLATURA_19 ? 2018 : 2022;
            ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, inicio, fim,
                    situacao, "Efetivo", numLeg, eleicao, null, provDep));
        }
    }

    private void processarComissoes(Contexto ctx, int id, Acumulado ac) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "almg", "comissoes/" + id + ".json");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return;
        }
        try {
            JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
            JsonNode lista = raiz.path("list");
            if (!lista.isArray()) {
                return;
            }
            Prov provComissao = Prov.de(FONTE_DETALHES, API + "/deputados/" + id + "/participacoes_comissoes", arq);
            for (JsonNode item : lista) {
                JsonNode comissao = item.path("comissao");
                String nomeComissao = comissao.path("nome").asText(null);
                if (nomeComissao == null || nomeComissao.isBlank()) {
                    continue;
                }
                String papel = item.path("participacao").asText("Membro");

                String iniStr = extrairData(item.path("dataInicio"));
                LocalDate inicio = iniStr != null ? parseDataOu(iniStr, null) : null;

                String fimStr = extrairData(item.path("dataTermino"));
                LocalDate fim = fimStr != null ? parseDataOu(fimStr, null) : null;

                // Filtrar se fora do período de cobertura
                if (fim != null && fim.isBefore(INICIO_COBERTURA)) {
                    continue;
                }
                if (inicio != null && inicio.isAfter(FIM_COBERTURA)) {
                    continue;
                }

                ac.orgaos.add(new OrgaoOuFrente("comissao", nomeComissao, null, papel, inicio, fim, provComissao));
            }
            if (!ac.orgaos.isEmpty()) {
                ac.fontes.computeIfAbsent("orgaos_e_frentes", k -> new ArrayList<>())
                        .add(FonteUsada.de(FONTE_DETALHES, arq));
            }
        } catch (Exception e) {
            System.err.println("almg: erro ao processar comissões do deputado " + id + ": " + e.getMessage());
        }
    }

    private void processarVerbas(Contexto ctx, int id, Acumulado ac) {
        Path pastaVerbas = ctx.raw().resolve("almg").resolve("verbas");
        if (!Files.exists(pastaVerbas)) {
            return;
        }
        for (int ano = 2022; ano <= 2026; ano++) {
            for (int mes = 1; mes <= 12; mes++) {
                String mesFormatado = String.format("%02d", mes);
                String arqRelativo = "verbas/" + id + "-" + ano + "-" + mesFormatado + ".json";
                Coleta.Arquivo arq = Coleta.metadados(ctx, "almg", arqRelativo);
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    continue;
                }
                try {
                    JsonNode raiz = Json.ler(Coleta.caminho(ctx, arq));
                    JsonNode lista = raiz.path("list");
                    if (!lista.isArray()) {
                        continue;
                    }
                    Prov provVerba = Prov.de(FONTE_DESPESAS,
                            API + "/prestacao_contas/verbas_indenizatorias/deputados/" + id + "/" + ano + "/" + mesFormatado, arq);
                    boolean temDespesa = false;
                    for (JsonNode grupo : lista) {
                        String categoria = grupo.path("descTipoDespesa").asText("Outros");
                        JsonNode detalhes = grupo.path("listaDetalheVerba");
                        if (!detalhes.isArray()) {
                            continue;
                        }
                        for (JsonNode item : detalhes) {
                            String dataStr = extrairData(item.path("dataEmissao"));
                            if (dataStr == null || dataStr.isBlank()) {
                                dataStr = extrairData(item.path("dataReferencia"));
                            }
                            LocalDate data = dataStr != null ? parseDataOu(dataStr, LocalDate.of(ano, mes, 1)) : LocalDate.of(ano, mes, 1);

                            BigDecimal valor = BigDecimal.valueOf(item.path("valorDespesa").asDouble(0.0));
                            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                                valor = BigDecimal.valueOf(item.path("valorReembolsado").asDouble(0.0));
                            }
                            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                                continue;
                            }

                            String emitente = Guarda.semCpf(item.path("nomeEmitente").asText(null));
                            String cpfCnpjRaw = item.path("cpfCnpj").asText(null);
                            String cnpjCpf = Texto.cnpj(cpfCnpjRaw); // retém apenas CNPJ válido de 14 dígitos

                            String numDoc = item.path("descDocumento").asText(null);

                            ac.despesas.add(new Despesa(data, ano, mes, categoria, emitente, cnpjCpf, valor, numDoc, null, provVerba));
                            temDespesa = true;
                        }
                    }
                    if (temDespesa) {
                        ac.fontes.computeIfAbsent("despesas", k -> new ArrayList<>())
                                .add(FonteUsada.de(FONTE_DESPESAS, arq));
                    }
                } catch (Exception e) {
                    System.err.println("almg: erro ao ler verba " + arqRelativo + ": " + e.getMessage());
                }
            }
        }
        // Ordenar despesas decrescente por data
        ac.despesas.sort(Comparator.comparing(Despesa::data, Comparator.reverseOrder()));
    }

    public static String extrairData(JsonNode no) {
        if (no == null || no.isNull() || no.isMissingNode()) {
            return null;
        }
        if (no.isTextual()) {
            return no.asText();
        }
        if (no.has("$")) {
            return no.get("$").asText();
        }
        return null;
    }

    public static LocalDate parseDataOu(String texto, LocalDate padrao) {
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        String limpo = texto.trim().split("T")[0]; // remove hora se presente
        for (DateTimeFormatter fmt : FORMATOS_NASCIMENTO) {
            try {
                return LocalDate.parse(limpo, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return padrao;
    }

    public static String normalizarDataIso(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        LocalDate ld = parseDataOu(texto, null);
        return ld != null ? ld.toString() : null;
    }

    private static final class Acumulado {
        final List<Mandato> mandatos = new ArrayList<>();
        final List<Despesa> despesas = new ArrayList<>();
        final List<OrgaoOuFrente> orgaos = new ArrayList<>();
        final Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
    }

    public record Blocos(List<Mandato> mandatos,
                         List<Despesa> despesas,
                         List<OrgaoOuFrente> orgaos_e_frentes,
                         Map<String, List<FonteUsada>> fontes) {
    }
}
