package etl.fontes;

import java.nio.file.Files;
import java.time.LocalDate;
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
import etl.Guarda;
import etl.Json;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Proposições da ALRN na 63ª legislatura, pela API de transparência do e-Legis (ADR-0080).
 * Cada processo traz a propositura (tipo, número e ano), a ementa, a data de entrada e a iniciativa, com o deputado autor.
 * O endpoint de parlamentar da API traz CPF e data de nascimento: a coleta não o chama.
 */
final class AlrnProposicoes {

    static final String FONTE = "alrn-elegis";
    static final String API = "https://api-transparencialegislativa.al.rn.leg.br/elegis-api-transp-legislativa/";
    static final int POR_PAGINA = 200;
    static final int MAX_PAGINAS = 400;
    static final LocalDate INICIO = LocalDate.of(2023, 2, 1);

    /** Tipos legislativos: emenda constitucional, leis e resoluções (inclusive a de título honorífico). */
    static final Set<String> TIPOS = Set.of("PEC", "PLC", "PL", "PDL", "PRES", "PRTH");

    private AlrnProposicoes() {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        // A lista vem da entrada mais nova para a mais velha: um processo novo desloca as páginas, que renovam juntas.
        Coleta.Arquivo primeira = coleta.obterOpcional(url(ctx, 0), caminho(0), "application/json", Politica.SEMANAL_EM_LISTA);
        if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
            return;
        }
        int total = Json.ler(Coleta.caminho(ctx, primeira)).path("total").asInt();
        int paginas = Math.min(MAX_PAGINAS, (total + POR_PAGINA - 1) / POR_PAGINA);
        for (int p = 1; p < paginas && !coleta.orcamentoExaurido(); p++) {
            coleta.obterOpcional(url(ctx, p), caminho(p), "application/json", Politica.SEMANAL_EM_LISTA);
        }
    }

    static Map<String, List<Proposicao>> porDeputado(Contexto ctx, AutoriaPorNome autoria) {
        Map<String, List<Proposicao>> porDeputado = new LinkedHashMap<>();
        Set<String> vistas = new LinkedHashSet<>();
        Set<String> semPerfil = new LinkedHashSet<>();
        int autorias = 0;
        for (int p = 0; p < MAX_PAGINAS; p++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alrn", caminho(p));
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            Prov prov = Prov.de(FONTE, arq.url(), arq);
            for (JsonNode item : Json.ler(Coleta.caminho(ctx, arq)).path("dados")) {
                Proposicao base = proposicao(item, prov);
                if (base == null || !vistas.add(base.idFonte())) {
                    continue;
                }
                JsonNode parlamentar = item.path("iniciativa").path("parlamentar");
                String nome = Json.texto(parlamentar, "nomeParlamentar");
                if (nome == null) {
                    continue;
                }
                String id = autoria.id(nome);
                if (id == null) {
                    semPerfil.add(nome);
                    continue;
                }
                porDeputado.computeIfAbsent(id, k -> new ArrayList<>()).add(base);
                autorias++;
            }
        }
        porDeputado.values().forEach(lista -> lista.sort(Comparator.comparing(
                (Proposicao p) -> p.dataApresentacao() == null ? "" : p.dataApresentacao()).reversed().thenComparing(Proposicao::idFonte)));
        System.out.println("alrn: " + vistas.size() + " proposições e " + autorias + " autorias em " + porDeputado.size()
                + " deputados; " + semPerfil.size() + " nome(s) de autor sem perfil de deputado");
        return porDeputado;
    }

    /** Proposição de tipo legislativo, com o deputado da iniciativa como único autor; nula para os outros tipos. */
    static Proposicao proposicao(JsonNode item, Prov prov) {
        JsonNode propositura = item.path("propositura");
        String sigla = Json.texto(propositura.path("tipo"), "sigla");
        if (sigla == null || !TIPOS.contains(sigla.strip()) || !item.hasNonNull("id")) {
            return null;
        }
        Integer numero = propositura.hasNonNull("numero") ? propositura.get("numero").asInt() : null;
        Integer ano = propositura.hasNonNull("ano") ? propositura.get("ano").asInt() : null;
        String ementa = Texto.semEntidades(Json.texto(item, "ementa"));
        String data = Json.texto(item, "dataEntrada");
        String texto = item.path("documento").hasNonNull("id") ? API + "documento/" + item.path("documento").get("id").asLong() + "/conteudo" : null;
        return new Proposicao(String.valueOf(item.get("id").asLong()), sigla.strip(), numero, ano,
                sigla.strip() + " " + (numero == null ? "s/n" : numero) + "/" + (ano == null ? "" : ano),
                ementa == null ? null : Guarda.sanitizar(ementa), null, null,
                data == null || data.length() < 10 ? null : data.substring(0, 10), 1, true, texto, prov);
    }

    static String url(Contexto ctx, int pagina) {
        return API + "processo?pagina=" + pagina + "&tamanhoPagina=" + POR_PAGINA + "&dataInicio=" + INICIO + "&dataFinal=" + ctx.hoje();
    }

    private static String caminho(int pagina) {
        return "proposicoes/pagina-" + pagina + ".json";
    }
}
