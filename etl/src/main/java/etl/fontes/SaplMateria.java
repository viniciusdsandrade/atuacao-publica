package etl.fontes;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Json;
import etl.Registros.FonteUsada;
import etl.Registros.Proposicao;
import etl.Registros.Prov;

/**
 * Utilitário compartilhado para coleta e normalização de proposições legislativas
 * em casas legislativas que utilizam a API REST do SAPL 3 (ALPB, ALEAM, ALEAL, ALERO, ALEPI, etc.).
 */
public final class SaplMateria {

    private static final String JSON = "application/json";
    private static final int PAGINA = 100;
    private static final int LIMITE_DE_PAGINAS = 50;

    private SaplMateria() {
    }

    public static List<Integer> anosLegislatura(LocalDate inicio, LocalDate fim, LocalDate hoje) {
        int anoIni = inicio.getYear();
        int anoLimite = Math.min(fim.getYear(), hoje.getYear());
        List<Integer> anos = new ArrayList<>();
        for (int a = anoIni; a <= anoLimite; a++) {
            anos.add(a);
        }
        return anos;
    }

    public static Politica politicaAnual(int ano, LocalDate hoje) {
        return Politica.porAnoSemanal(ano, hoje);
    }

    public static void fetchTipos(Coleta coleta, String apiUrl) throws IOException, InterruptedException {
        coleta.obterOuVazio(apiUrl + "/materia/tipomaterialegislativa/?page_size=" + PAGINA + "&format=json",
                "tipos-materia.json", JSON, Politica.SEMANAL);
    }

    public static void fetchAutorEMaterias(Contexto ctx, Coleta coleta, String apiUrl, int parlamentarId, List<Integer> anos)
            throws Exception {
        String urlAutor = apiUrl + "/base/autor/?object_id=" + parlamentarId + "&format=json";
        Coleta.Arquivo arqAutor = coleta.obterOuVazio(urlAutor, "autores/" + parlamentarId + ".json", JSON, Politica.SEMANAL);
        String autorId = null;
        if (arqAutor != null && Files.exists(Coleta.caminho(ctx, arqAutor))) {
            JsonNode raiz = Json.ler(Coleta.caminho(ctx, arqAutor));
            autorId = extrairAutorId(raiz, String.valueOf(parlamentarId));
        }

        if (autorId != null) {
            for (int ano : anos) {
                Politica politica = politicaAnual(ano, ctx.hoje());
                String caminhoBase = "materias/" + parlamentarId + "-" + ano;
                for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                    String url = apiUrl + "/materia/materialegislativa/?autores=" + autorId + "&ano=" + ano
                            + "&page_size=" + PAGINA + "&page=" + p + "&format=json";
                    Coleta.Arquivo arq = coleta.obterOuVazio(url, caminhoBase + "-p" + p + ".json", JSON, politica);
                    if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                        break;
                    }
                    JsonNode pagNode = Json.ler(Coleta.caminho(ctx, arq));
                    if (!pagNode.hasNonNull("next")) {
                        break;
                    }
                }
            }
        }
    }

    public static Map<String, String[]> carregarTipos(Contexto ctx, String fonte) {
        Coleta.Arquivo arqTipos = Coleta.metadados(ctx, fonte, "tipos-materia.json");
        Map<String, String[]> tipos = new HashMap<>();
        if (arqTipos != null && Files.exists(Coleta.caminho(ctx, arqTipos))) {
            JsonNode raiz = Json.ler(Coleta.caminho(ctx, arqTipos));
            for (JsonNode t : resultados(raiz)) {
                String id = Json.texto(t, "id");
                if (id != null) {
                    tipos.put(id, new String[] { Json.texto(t, "sigla"), Json.texto(t, "descricao") });
                }
            }
        }
        return tipos;
    }

    public static String extrairAutorId(JsonNode raiz, String parlamentarId) {
        if (raiz == null) {
            return null;
        }
        for (JsonNode a : resultados(raiz)) {
            if (parlamentarId.equals(Json.texto(a, "object_id"))) {
                return Json.texto(a, "id");
            }
        }
        return null;
    }

    public static List<Proposicao> normalizarProposicoes(Contexto ctx, String fonte, String fonteProposicoesId,
                                                         String siteUrl, int parlamentarId, List<Integer> anos,
                                                         Map<String, String[]> tipos,
                                                         Map<String, List<FonteUsada>> fontesUsadas) {
        List<Proposicao> proposicoes = new ArrayList<>();
        List<FonteUsada> usadas = new ArrayList<>();

        for (int ano : anos) {
            String caminhoBase = "materias/" + parlamentarId + "-" + ano;
            for (int p = 1; p <= LIMITE_DE_PAGINAS; p++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, fonte, caminhoBase + "-p" + p + ".json");
                if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                    break;
                }
                JsonNode pagNode = Json.ler(Coleta.caminho(ctx, arq));
                List<JsonNode> materias = resultados(pagNode);
                for (JsonNode m : materias) {
                    String idMateria = Json.texto(m, "id");
                    if (idMateria == null) {
                        continue;
                    }
                    String[] tipo = tipos.get(Json.texto(m, "tipo"));
                    String siglaTipo = tipo != null ? tipo[0] : Json.texto(m, "tipo_descricao");
                    String descTipo = tipo != null ? tipo[1] : (siglaTipo != null ? siglaTipo : "Matéria");
                    String numeroStr = Json.texto(m, "numero");
                    String anoStr = Json.texto(m, "ano");
                    String identificacao = primeiro(Json.texto(m, "__str__"),
                            descTipo + " nº " + numeroStr + " de " + anoStr);

                    Boolean emTramitacao = m.path("em_tramitacao").isBoolean() ? m.path("em_tramitacao").asBoolean() : null;
                    String situacao = primeiro(Json.texto(m, "resultado"),
                            emTramitacao == null ? null : (emTramitacao ? "Em tramitação" : "Tramitação encerrada"));

                    String dataApresentacao = Json.texto(m, "data_apresentacao");
                    String urlTexto = urlValida(Json.texto(m, "texto_original"));

                    String urlMateria = siteUrl + "/materia/" + idMateria;
                    Prov prov = Prov.de(fonteProposicoesId, urlMateria, arq);

                    proposicoes.add(new Proposicao(idMateria, siglaTipo, Json.inteiro(m, "numero"),
                            Json.inteiro(m, "ano"), identificacao, Json.texto(m, "ementa"), situacao, List.of(),
                            dataApresentacao, null, null, urlTexto, prov));
                }
                usadas.add(FonteUsada.de(fonteProposicoesId, arq));
                if (!pagNode.hasNonNull("next")) {
                    break;
                }
            }
        }

        if (!usadas.isEmpty() && fontesUsadas != null) {
            proposicoes.sort(Comparator.comparing((Proposicao pr) -> pr.dataApresentacao() == null ? "" : pr.dataApresentacao()).reversed());
            fontesUsadas.put("proposicoes", usadas);
        }

        return proposicoes;
    }

    public static List<JsonNode> resultados(JsonNode raiz) {
        if (raiz == null) {
            return List.of();
        }
        JsonNode r = raiz.get("results");
        return r != null && r.isArray() ? Json.lista(r) : List.of();
    }

    private static String urlValida(String texto) {
        return texto != null && texto.startsWith("http") ? texto : null;
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
