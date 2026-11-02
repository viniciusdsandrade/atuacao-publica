package etl.fontes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Despesas dos municípios paulistas publicadas pelo Tribunal de Contas do Estado de São Paulo (ADR-0008).
 * Um mês por chamada, sem paginação. O ETL agrega por mês, órgão e evento e não guarda lançamento individual
 * no normalizado: a fonte traz nome de fornecedor pessoa física com parte do CPF.
 */
public final class TceSp implements Fonte {

    public static final String FONTE_ID = "tce-sp-transparencia-api";
    public static final String API = "https://transparencia.tce.sp.gov.br/api/json";
    /** Código IBGE -> nome do município na API (lista `municipios`). */
    static final Map<String, String> MUNICIPIOS = Map.of(Sapl.IBGE, "campinas");
    /** Meses renovados toda semana: a fonte publica com defasagem e revisa os meses recentes. */
    static final int MESES_ABERTOS = 6;

    /** Soma de um mês, por órgão e evento contábil. */
    public record Agregado(String ente, int ano, int mes, String orgao, String evento, BigDecimal valor, int quantidade,
                           @JsonUnwrapped Prov prov) {
    }

    @Override
    public String nome() {
        return "tcesp";
    }

    /** Meses de mandato de prefeito já iniciados, por ente, a partir de contrib/mandatos.yaml. */
    static Map<String, List<YearMonth>> mesesPorEnte(Contexto ctx) {
        Map<String, List<YearMonth>> meses = new TreeMap<>();
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            if (!"prefeito".equals(Json.texto(regra, "cargo"))) {
                continue;
            }
            String ente = regra.hasNonNull("ente") ? Json.texto(regra, "ente") : Sapl.IBGE;
            if (!MUNICIPIOS.containsKey(ente)) {
                continue;
            }
            // Mais de um mandato no mesmo ente (prefeito anterior e atual): os meses se somam, sem repetição.
            List<YearMonth> doEnte = meses.computeIfAbsent(ente, k -> new ArrayList<>());
            for (YearMonth mes : meses(LocalDate.parse(Json.texto(regra, "inicio")), LocalDate.parse(Json.texto(regra, "fim")), ctx.hoje())) {
                if (!doEnte.contains(mes)) {
                    doEnte.add(mes);
                }
            }
        }
        meses.values().forEach(lista -> lista.sort(null));
        return meses;
    }

    /** Meses do mandato já iniciados, nunca antes do início da cobertura (ADR-0009). */
    static List<YearMonth> meses(LocalDate inicio, LocalDate fim, LocalDate hoje) {
        List<YearMonth> lista = new ArrayList<>();
        if (inicio.isBefore(Camara.INICIO_DA_COBERTURA)) {
            inicio = Camara.INICIO_DA_COBERTURA;
        }
        if (hoje.isBefore(inicio) || !fim.isAfter(inicio)) {
            return lista;
        }
        YearMonth ultimo = YearMonth.from(fim.isBefore(hoje) ? fim.minusDays(1) : hoje);
        for (YearMonth m = YearMonth.from(inicio); !m.isAfter(ultimo); m = m.plusMonths(1)) {
            lista.add(m);
        }
        return lista;
    }

    static Politica politica(YearMonth mes, LocalDate hoje) {
        return mes.isBefore(YearMonth.from(hoje).minusMonths(MESES_ABERTOS)) ? Politica.SE_AUSENTE : Politica.SEMANAL;
    }

    static String url(String ente, YearMonth mes) {
        return API + "/despesas/" + MUNICIPIOS.get(ente) + "/" + mes.getYear() + "/" + String.format("%02d", mes.getMonthValue());
    }

    static String caminho(String ente, YearMonth mes) {
        return "despesas/" + ente + "-" + mes + ".json";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        LocalDate inicio = null;
        int meses = 0;
        for (var entrada : mesesPorEnte(ctx).entrySet()) {
            for (YearMonth mes : entrada.getValue()) {
                coleta.obterOpcional(url(entrada.getKey(), mes), caminho(entrada.getKey(), mes), "application/json",
                        politica(mes, ctx.hoje()));
                meses++;
                LocalDate primeiro = mes.atDay(1);
                inicio = inicio == null || primeiro.isBefore(inicio) ? primeiro : inicio;
            }
        }
        coleta.contar("meses", meses);
        if (inicio != null) {
            coleta.periodo(new Meta.Periodo(inicio, ctx.hoje()));
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<Agregado> agregados = new ArrayList<>();
        int mesesSemDado = 0;
        for (var entrada : mesesPorEnte(ctx).entrySet()) {
            String ente = entrada.getKey();
            for (YearMonth mes : entrada.getValue()) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "tcesp", caminho(ente, mes));
                if (arq == null) {
                    mesesSemDado++;
                    continue;
                }
                List<Agregado> doMes = agregar(Json.ler(Coleta.caminho(ctx, arq)), ente, mes, Prov.de(FONTE_ID, arq.url(), arq));
                if (doMes.isEmpty()) {
                    mesesSemDado++;
                }
                agregados.addAll(doMes);
            }
        }
        Json.gravar(ctx.normalizado().resolve("tcesp").resolve("despesas.json"), agregados);
        System.out.println("tcesp: " + agregados.size() + " agregados de despesa normalizados"
                + (mesesSemDado > 0 ? ", " + mesesSemDado + " mês(es) sem dado na fonte" : ""));
    }

    /** Soma os lançamentos do mês por órgão e evento. Lançamento sem valor numérico fica fora. */
    static List<Agregado> agregar(JsonNode lancamentos, String ente, YearMonth mes, Prov prov) {
        Map<String, BigDecimal> somas = new TreeMap<>();
        Map<String, Integer> contagens = new TreeMap<>();
        for (JsonNode l : Json.lista(lancamentos)) {
            String orgao = Json.texto(l, "orgao");
            String evento = Json.texto(l, "evento");
            BigDecimal valor = Texto.valor(Json.texto(l, "vl_despesa"));
            if (orgao == null || evento == null || valor == null) {
                continue;
            }
            String chave = orgao + "|" + evento;
            somas.merge(chave, valor, BigDecimal::add);
            contagens.merge(chave, 1, Integer::sum);
        }
        List<Agregado> lista = new ArrayList<>();
        for (var soma : somas.entrySet()) {
            String[] partes = soma.getKey().split("\\|", 2);
            lista.add(new Agregado(ente, mes.getYear(), mes.getMonthValue(), partes[0], partes[1], soma.getValue(),
                    contagens.get(soma.getKey()), prov));
        }
        return lista;
    }
}
