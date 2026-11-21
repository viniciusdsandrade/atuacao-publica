package etl.fontes;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Json;
import etl.Registros.Evento;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

/**
 * Presença dos deputados da ALRN nas sessões deliberativas do plenário, pela API do e-Legis (ADR-0084). A fonte registra
 * presença ou ausência justificada; sem registro, a presença fica nula, sem virar falta.
 */
final class AlrnPresenca {

    static final String FONTE = AlrnProposicoes.FONTE;
    static final String API = AlrnProposicoes.API;
    static final int POR_PAGINA = 200;
    static final int MAX_PAGINAS = 20;
    static final int ANO_INICIAL = 2023;
    /** Sessão plenária ordinária, extraordinária e extraordinária de vetos. Solene e audiência não são deliberativas. */
    static final Set<Integer> TIPOS = Set.of(1, 2, 12);

    private AlrnPresenca() {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear() && !coleta.orcamentoExaurido(); ano++) {
            Politica politica = Politica.porAnoSemanalEmLista(ano, ctx.hoje());
            // O campo total da resposta é o tamanho da página: a coleta pagina até vir uma página incompleta.
            for (int p = 0; p < MAX_PAGINAS; p++) {
                Coleta.Arquivo arq = coleta.obterOpcional(url(ano, p, ctx), caminhoDaLista(ano, p), "application/json", politica);
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))
                        || Json.ler(Coleta.caminho(ctx, arq)).path("dados").size() < POR_PAGINA) {
                    break;
                }
            }
        }
        // Sessão realizada não muda: a presença vem uma vez e fica no cache. Da mais nova para a mais velha: as sessões de
        // 2023 respondem HTTP 500, e o limite de falhas da coleta não pode impedir as dos anos seguintes.
        List<JsonNode> ordem = new ArrayList<>(sessoes(ctx));
        ordem.sort(Comparator.comparing((JsonNode s) -> Json.texto(s, "dataInicio") == null ? "" : Json.texto(s, "dataInicio")).reversed());
        for (JsonNode sessao : ordem) {
            if (coleta.orcamentoExaurido()) {
                break;
            }
            long id = sessao.get("id").asLong();
            coleta.obterOpcional(API + "reuniao/presenca/" + id, caminhoDaPresenca(id), "application/json", Politica.SE_AUSENTE);
        }
    }

    /** Sessões deliberativas realizadas, das listas em cache. */
    static List<JsonNode> sessoes(Contexto ctx) {
        Map<Long, JsonNode> porId = new LinkedHashMap<>();
        for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear(); ano++) {
            for (int p = 0; p < MAX_PAGINAS; p++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "alrn", caminhoDaLista(ano, p));
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                for (JsonNode s : Json.ler(Coleta.caminho(ctx, arq)).path("dados")) {
                    if (s.hasNonNull("id") && TIPOS.contains(s.path("tipoReuniao").path("id").asInt(-1))) {
                        porId.putIfAbsent(s.get("id").asLong(), s);
                    }
                }
            }
        }
        return new ArrayList<>(porId.values());
    }

    static Map<String, List<Evento>> porDeputado(Contexto ctx, AutoriaPorNome autoria) {
        Map<String, List<Evento>> eventos = new LinkedHashMap<>();
        Set<String> semPerfil = new LinkedHashSet<>();
        int sessoes = 0;
        for (JsonNode sessao : sessoes(ctx)) {
            long id = sessao.get("id").asLong();
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alrn", caminhoDaPresenca(id));
            String data = Json.texto(sessao, "dataInicio");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq)) || data == null || data.length() < 10) {
                continue;
            }
            sessoes++;
            Prov prov = Prov.de(FONTE, arq.url(), arq);
            String tipo = Json.texto(sessao.path("tipoReuniao"), "nome");
            String titulo = tipo + (sessao.hasNonNull("numero") ? " nº " + sessao.get("numero").asInt() : "");
            for (JsonNode d : Json.ler(Coleta.caminho(ctx, arq)).path("dados")) {
                String nome = Json.texto(d, "nomeParlamentar");
                String deputado = nome == null ? null : autoria.id(nome);
                if (deputado == null) {
                    if (nome != null) {
                        semPerfil.add(nome);
                    }
                    continue;
                }
                eventos.computeIfAbsent(deputado, k -> new ArrayList<>()).add(new Evento(String.valueOf(id), data.substring(0, 10),
                        tipo, titulo, "Plenário", "Realizada", presenca(Json.texto(d.path("evento"), "chave")), prov));
            }
        }
        eventos.values().forEach(l -> l.sort(Comparator.comparing(Evento::data).reversed().thenComparing(Evento::idFonte)));
        System.out.println("alrn: presença em " + sessoes + " sessões deliberativas, " + eventos.size() + " deputados; "
                + semPerfil.size() + " nome(s) sem perfil");
        return eventos;
    }

    /** Valor do schema: registrada, ausencia_justificada, ou nulo quando a fonte não registra nada. */
    static String presenca(String chave) {
        if ("presenca".equals(chave)) {
            return "registrada";
        }
        if ("ausencia-justificada".equals(chave)) {
            return "ausencia_justificada";
        }
        return null;
    }

    static String url(int ano, int pagina, Contexto ctx) {
        String fim = ano == ctx.hoje().getYear() ? ctx.hoje().toString() : ano + "-12-31";
        return API + "reuniao?situacao=R&dataInicio=" + ano + "-01-01&dataFim=" + fim + "&pagina=" + pagina + "&tamanhoPagina=" + POR_PAGINA;
    }

    private static String caminhoDaLista(int ano, int pagina) {
        return "reunioes/" + ano + "-" + pagina + ".json";
    }

    private static String caminhoDaPresenca(long id) {
        return "reunioes/presenca/" + id + ".json";
    }
}
