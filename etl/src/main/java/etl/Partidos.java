package etl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Sigla de partido unificada pela grafia do TSE (ADR-0058). Cada casa grafa o partido de um jeito ("União", "UB",
 * "UNIÃO BRASIL"), e o filtro por partido do site partia o mesmo partido em vários. O perfil passa a mostrar a sigla
 * que o TSE usa no arquivo de candidaturas da eleição mais recente em que o partido aparece.
 *
 * A comparação ignora caixa, acento, espaço e pontuação. Nome por extenso e abreviação que não são a sigla entram por
 * uma tabela curta. Grafia que não casa com nada sai como a casa a publica. Sigla antiga de partido que mudou de nome
 * ou se fundiu (PPS, PRB, DEM) não vira a nova: era o partido da pessoa na data.
 */
public final class Partidos {

    /** A casa informa que o parlamentar não tem partido. Não é ausência de registro. */
    public static final String SEM_PARTIDO = "Sem partido";

    private static final Pattern MARCA_DE_SEM_PARTIDO = Pattern.compile("^(S[./]\\s*P(ART(IDO)?)?\\.?|SEM\\s+PARTIDO)$");

    /** Chave da grafia da casa para a sigla do TSE, na grafia de reserva usada quando o TSE ainda não foi normalizado. */
    private static final Map<String, String> APELIDOS = Map.ofEntries(
            Map.entry("CIDAD", "CIDADANIA"),
            Map.entry("PODEMOS", "PODE"),
            Map.entry("PROGRESSISTAS", "PP"),
            Map.entry("REP", "REPUBLICANOS"),
            Map.entry("REPUB", "REPUBLICANOS"),
            Map.entry("REPUBLICA", "REPUBLICANOS"),
            Map.entry("REPUBLICANO", "REPUBLICANOS"),
            Map.entry("SD", "SOLIDARIEDADE"),
            Map.entry("SDD", "SOLIDARIEDADE"),
            Map.entry("UB", "UNIÃO"),
            Map.entry("UNIAOBRASIL", "UNIÃO"));

    private Partidos() {
    }

    /** O que mudou e o que ficou como veio, para a saída do publish. */
    record Resultado(int unificados, Map<String, Integer> semCorrespondencia) {
    }

    /** Grafia do TSE por chave: a do arquivo de candidaturas mais recente em que a sigla aparece. */
    public static Map<String, String> grafiasDoTse(List<JsonNode> candidaturas) {
        Map<String, String> grafia = new HashMap<>();
        Map<String, Integer> ano = new HashMap<>();
        for (JsonNode c : candidaturas) {
            String partido = Json.texto(c, "partido");
            String chave = chave(partido);
            int eleicao = c.path("ano").asInt(0);
            if (chave != null && eleicao >= ano.getOrDefault(chave, 0)) {
                ano.put(chave, eleicao);
                grafia.put(chave, partido);
            }
        }
        return grafia;
    }

    /** A sigla unificada, ou a grafia da casa (sem entidade HTML) quando não há correspondência. Nulo continua nulo. */
    public static String unificar(String original, Map<String, String> grafiasDoTse) {
        String limpo = Texto.semEntidades(original);
        if (limpo == null) {
            return null;
        }
        if (MARCA_DE_SEM_PARTIDO.matcher(Texto.normalizarNome(limpo)).matches()) {
            return SEM_PARTIDO;
        }
        String chave = chave(limpo);
        String reserva = chave == null ? null : APELIDOS.get(chave);
        String doTse = grafiasDoTse.get(reserva == null ? chave : chave(reserva));
        if (doTse != null) {
            return doTse;
        }
        return reserva != null ? reserva : limpo;
    }

    /** A grafia tem correspondência: é a marca de sem partido, um apelido da tabela ou uma sigla do TSE. */
    static boolean conhecida(String original, Map<String, String> grafiasDoTse) {
        String limpo = Texto.semEntidades(original);
        if (limpo == null || MARCA_DE_SEM_PARTIDO.matcher(Texto.normalizarNome(limpo)).matches()) {
            return true;
        }
        String chave = chave(limpo);
        return chave != null && (APELIDOS.containsKey(chave) || grafiasDoTse.containsKey(chave));
    }

    /** Unifica o partido de cada perfil e conta o que mudou e o que não teve correspondência. */
    static Resultado unificarPerfis(List<Publicador.Perfil> perfis, Map<String, String> grafiasDoTse) {
        int unificados = 0;
        Map<String, Integer> semCorrespondencia = new TreeMap<>();
        for (Publicador.Perfil p : perfis) {
            if (p.partido == null) {
                continue;
            }
            if (!conhecida(p.partido, grafiasDoTse)) {
                semCorrespondencia.merge(p.partido, 1, Integer::sum);
            }
            String unificado = unificar(p.partido, grafiasDoTse);
            if (!p.partido.equals(unificado)) {
                unificados++;
                p.partido = unificado;
            }
        }
        return new Resultado(unificados, semCorrespondencia);
    }

    /** Sem acento, em maiúsculas, só letras e dígitos. Vazio vira nulo. */
    static String chave(String texto) {
        String normalizado = Texto.normalizarNome(Texto.semEntidades(texto));
        if (normalizado == null) {
            return null;
        }
        return Texto.vazioParaNulo(normalizado.replaceAll("[^A-Z0-9]", ""));
    }
}
