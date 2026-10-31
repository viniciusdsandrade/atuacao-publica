package etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import etl.Publicador.Perfil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Anexa os registros manuais de contrib/ aos perfis, com a precedência da política editorial. */
final class Contribuicoes {

    static final String ROTULO_T1 = "declaração do próprio político";
    private static final Set<String> PROVENIENCIA = Set.of("fonte_id", "url_origem", "snapshot_url", "capturado_em", "contribuidor");

    private Contribuicoes() {
    }

    static void anexar(Contexto ctx, Catalogo catalogo, List<Perfil> perfis, Map<String, String> aliases,
                       List<Meta.Rejeitado> rejeitados) {
        Path arquivo = ctx.normalizado().resolve("contrib").resolve("registros.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        Map<String, Perfil> porId = new HashMap<>();
        perfis.forEach(p -> porId.put(p.id, p));
        for (JsonNode registro : Json.ler(arquivo)) {
            String id = registro.get("politico_id").stringValue();
            String tipo = registro.get("tipo").stringValue();
            JsonNode entrada = registro.get("entrada");
            Perfil perfil = porId.containsKey(id) ? porId.get(id) : porId.get(aliases.get(id));
            String origem = "contrib/" + registro.get("esfera").stringValue() + "/" + registro.get("cargo").stringValue()
                    + "/" + id + "/" + tipo + ".yaml";
            if (perfil == null) {
                rejeitados.add(new Meta.Rejeitado(origem, "perfil " + id + " não existe no conjunto publicado"));
                continue;
            }
            switch (tipo) {
                case "posicionamento_declarado" -> {
                    ObjectNode r = copiar(entrada, "categoria", "data", "texto_oficial", "resumo_factual");
                    String nivel = registro.get("nivel_fonte").stringValue();
                    r.put("nivel_fonte", nivel);
                    r.put("rotulo", "T1".equals(nivel) ? ROTULO_T1 : null);
                    perfil.bloco("posicionamentos_declarados").add(proveniencia(r, entrada));
                }
                case "agenda_oficial" -> perfil.bloco("agenda_oficial").add(proveniencia(
                        copiar(entrada, "data", "hora", "texto_oficial", "resumo_factual", "local"), entrada));
                case "link_oficial" -> {
                    ObjectNode r = copiar(entrada, "rotulo");
                    r.set("url", entrada.get("url_origem"));
                    perfil.links.add(proveniencia(r, entrada));
                }
                case "mandato" -> {
                    // Correção de mandato só entra com fonte T0. Ela vence o dado automático do mesmo cargo no mesmo
                    // período; os outros mandatos da pessoa ficam (ADR-0006).
                    String cargo = entrada.get("cargo").stringValue();
                    String inicio = Json.texto(entrada, "inicio");
                    String fim = Json.texto(entrada, "fim");
                    JsonNode substituido = null;
                    for (JsonNode m : List.copyOf(perfil.bloco("mandatos"))) {
                        if (cargo.equals(m.path("cargo").asString()) && !m.has("contribuidor")
                                && sobrepoe(inicio, fim, Json.texto(m, "inicio"), Json.texto(m, "fim"))) {
                            substituido = substituido == null ? m : substituido;
                            perfil.bloco("mandatos").remove(m);
                        }
                    }
                    ObjectNode r = copiar(entrada, "cargo");
                    r.put("esfera", substituido == null ? perfil.esfera : Json.texto(substituido, "esfera"))
                            .put("ente", substituido == null ? perfil.ente : Json.texto(substituido, "ente"))
                            .put("uf", substituido == null ? perfil.uf : Json.texto(substituido, "uf"));
                    r.set("inicio", entrada.get("inicio"));
                    r.set("fim", entrada.get("fim"));
                    r.set("situacao", entrada.get("situacao"));
                    r.set("texto_oficial", entrada.get("texto_oficial"));
                    perfil.bloco("mandatos").add(proveniencia(r, entrada));
                    if (cargo.equals(perfil.cargo)) {
                        String hoje = ctx.hoje().toString();
                        perfil.situacao = Json.texto(entrada, "situacao");
                        perfil.emExercicio = (inicio == null || inicio.compareTo(hoje) <= 0) && (fim == null || fim.compareTo(hoje) > 0);
                    }
                }
                default -> rejeitados.add(new Meta.Rejeitado(origem, "tipo desconhecido: " + tipo));
            }
        }
    }

    /** Períodos [inicio, fim) em ISO 8601 que se sobrepõem. Data ausente deixa o período aberto daquele lado. */
    static boolean sobrepoe(String inicioA, String fimA, String inicioB, String fimB) {
        boolean aAntesDoFimDeB = inicioA == null || fimB == null || inicioA.compareTo(fimB) < 0;
        boolean bAntesDoFimDeA = inicioB == null || fimA == null || inicioB.compareTo(fimA) < 0;
        return aAntesDoFimDeB && bAntesDoFimDeA;
    }

    private static ObjectNode copiar(JsonNode entrada, String... campos) {
        ObjectNode r = Json.SAIDA.createObjectNode();
        for (String campo : campos) {
            if (entrada.has(campo)) {
                r.set(campo, entrada.get(campo));
            }
        }
        return r;
    }

    private static ObjectNode proveniencia(ObjectNode r, JsonNode entrada) {
        for (String campo : PROVENIENCIA) {
            r.set(campo, entrada.get(campo));
        }
        return r;
    }
}
