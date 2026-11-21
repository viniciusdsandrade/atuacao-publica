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
 * Proposições da ALEMA na 20ª legislatura, pela API pública do sistema legislativo (alemalegis), ADR-0079.
 * A lista já traz tipo, número, ano, ementa, situação, data de apresentação, autores e o arquivo do texto.
 * Os endpoints de parlamentar da API trazem CPF, RG e endereço: a coleta não os chama.
 */
final class AlemaProposicoes {

    static final String FONTE = "alema-legis";
    static final String API = "https://alemalegis.al.ma.leg.br/api/v1/public/";
    static final int POR_PAGINA = 200;
    static final int MAX_PAGINAS = 400;
    static final LocalDate INICIO = LocalDate.of(2023, 2, 1);

    /** Tipos legislativos, como nas outras casas: emenda constitucional, leis, decreto legislativo e resolução. */
    static final Set<String> TIPOS = Set.of("PEC", "PLO", "PLC", "PDL", "PRE");

    private AlemaProposicoes() {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        // Ordenada pelo id, a lista só cresce no fim: as páginas renovam juntas, uma vez por semana.
        Coleta.Arquivo primeira = coleta.obterOpcional(url(ctx, 0), caminho(0), "application/json", Politica.SEMANAL_EM_LISTA);
        if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
            return;
        }
        int paginas = Math.min(MAX_PAGINAS, Json.ler(Coleta.caminho(ctx, primeira)).path("totalPages").asInt());
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
            Coleta.Arquivo arq = Coleta.metadados(ctx, "alema", caminho(p));
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                break;
            }
            Prov prov = Prov.de(FONTE, arq.url(), arq);
            for (JsonNode item : Json.ler(Coleta.caminho(ctx, arq)).path("content")) {
                Proposicao base = proposicao(item, prov);
                if (base == null || !vistas.add(base.idFonte())) {
                    continue;
                }
                List<String> autores = autores(Json.texto(item, "authorNames"), autoria);
                for (int i = 0; i < autores.size(); i++) {
                    String id = autoria.id(autores.get(i));
                    if (id == null) {
                        semPerfil.add(autores.get(i).strip());
                        continue;
                    }
                    porDeputado.computeIfAbsent(id, k -> new ArrayList<>()).add(new Proposicao(base.idFonte(), base.tipo(),
                            base.numero(), base.ano(), base.identificacao(), base.ementa(), base.situacao(), null,
                            base.dataApresentacao(), i + 1, i == 0, base.urlTexto(), prov));
                    autorias++;
                }
            }
        }
        porDeputado.values().forEach(lista -> lista.sort(Comparator.comparing(
                (Proposicao p) -> p.dataApresentacao() == null ? "" : p.dataApresentacao()).reversed().thenComparing(Proposicao::idFonte)));
        System.out.println("alema: " + vistas.size() + " proposições e " + autorias + " autorias em " + porDeputado.size()
                + " deputados; " + semPerfil.size() + " nome(s) de autor sem perfil de deputado");
        return porDeputado;
    }

    /** Proposição de tipo legislativo, sem autor; nula para os outros tipos (indicação, requerimento, moção, mensagem). */
    static Proposicao proposicao(JsonNode item, Prov prov) {
        String sigla = Json.texto(item, "matterTypeSigla");
        if (sigla == null || !TIPOS.contains(sigla.strip()) || !item.hasNonNull("id")) {
            return null;
        }
        Integer numero = inteiro(Json.texto(item, "number"));
        Integer ano = inteiro(Json.texto(item, "year"));
        String ementa = Texto.semEntidades(Json.texto(item, "ement"));
        String texto = null;
        JsonNode arquivos = item.path("files");
        if (arquivos.isArray() && !arquivos.isEmpty() && arquivos.get(0).hasNonNull("id")) {
            texto = API + "legislative-matter/download/" + arquivos.get(0).get("id").asLong();
        }
        String data = Json.texto(item, "presentationDate");
        return new Proposicao(String.valueOf(item.get("id").asLong()), sigla.strip(), numero, ano,
                sigla.strip() + " " + (numero == null ? "s/n" : numero) + "/" + (ano == null ? "" : ano),
                ementa == null ? null : Guarda.sanitizar(ementa), Texto.semEntidades(Json.texto(item, "situation")), null,
                data == null || data.length() < 10 ? null : data.substring(0, 10), null, null, texto, prov);
    }

    /**
     * Autores do campo authorNames. Em geral é um nome só. Nome de comissão tem vírgula ("Comissão de Constituição,
     * Justiça e Cidadania"): a divisão por vírgula só vale quando alguma parte é um deputado.
     */
    static List<String> autores(String nomes, AutoriaPorNome autoria) {
        if (nomes == null || nomes.isBlank()) {
            return List.of();
        }
        if (autoria.id(nomes) != null || !nomes.contains(",")) {
            return List.of(nomes.strip());
        }
        List<String> partes = new ArrayList<>();
        for (String parte : nomes.split(",")) {
            if (!parte.isBlank()) {
                partes.add(parte.strip());
            }
        }
        return partes.stream().anyMatch(p -> autoria.id(p) != null) ? partes : List.of(nomes.strip());
    }

    private static Integer inteiro(String texto) {
        if (texto == null || !texto.strip().matches("\\d{1,9}")) {
            return null;
        }
        return Integer.valueOf(texto.strip());
    }

    static String url(Contexto ctx, int pagina) {
        return API + "legislative-matter/filter/access?page=" + pagina + "&size=" + POR_PAGINA
                + "&initialPresentationPeriod=" + INICIO + "T00:00:00&finalPresentationPeriod=" + ctx.hoje() + "T23:59:59&sort=id,asc";
    }

    private static String caminho(int pagina) {
        return "proposicoes/pagina-" + pagina + ".json";
    }
}
