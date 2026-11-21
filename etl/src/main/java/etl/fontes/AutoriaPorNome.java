package etl.fontes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import etl.Texto;

/**
 * Ligação do autor de uma proposição ao deputado quando a fonte só traz o nome (ADR-0078). Três tentativas, em ordem:
 * o nome sem acento e sem pontuação; o nome sem o tratamento ("Dr.", "Prof."); e o nome com as palavras do meio
 * abreviadas ("Andreia M. Rezende" e "Andreia Martins Rezende"). As duas últimas só ligam quando um único deputado
 * tem aquele nome.
 */
final class AutoriaPorNome {

    private static final Pattern TRATAMENTO = Pattern.compile(
            "^(DR|DRA|PROF|PROFA|PROFESSOR|PROFESSORA|DELEGADO|DELEGADA|PASTOR|PASTORA|CORONEL|CAPITAO|SARGENTO|TENENTE) ");

    private final Map<String, String> exatos = new HashMap<>();
    private final Map<String, String> semTratamento = new HashMap<>();
    private final Map<String, String> idsComNome = new HashMap<>();

    AutoriaPorNome(Map<String, String> nomePorId) {
        Set<String> repetidas = new HashSet<>();
        nomePorId.forEach((id, nome) -> {
            String c = chave(nome);
            if (c == null) {
                return;
            }
            exatos.putIfAbsent(c, id);
            String s = semTratamento(c);
            if (semTratamento.containsKey(s) && !semTratamento.get(s).equals(id)) {
                repetidas.add(s);
            }
            semTratamento.putIfAbsent(s, id);
            idsComNome.put(id, s);
        });
        repetidas.forEach(semTratamento::remove);
    }

    /** Id do deputado, ou nulo se o nome não se liga a um único deputado. */
    String id(String autor) {
        String c = chave(autor);
        if (c == null) {
            return null;
        }
        String id = exatos.get(c);
        if (id == null) {
            id = semTratamento.get(semTratamento(c));
        }
        if (id == null) {
            String s = semTratamento(c);
            String unico = null;
            for (var e : idsComNome.entrySet()) {
                if (abreviado(s, e.getValue())) {
                    if (unico != null && !unico.equals(e.getKey())) {
                        return null;
                    }
                    unico = e.getKey();
                }
            }
            id = unico;
        }
        return id;
    }

    /** Nome como chave: sem acento e sem pontuação, em maiúsculas, com um espaço entre as palavras. */
    static String chave(String nome) {
        String n = Texto.normalizarNome(Texto.semEntidades(nome));
        if (n == null) {
            return null;
        }
        String limpo = n.replaceAll("[.,;]", " ").replaceAll("\\s+", " ").strip();
        return limpo.isEmpty() ? null : limpo;
    }

    static String semTratamento(String chave) {
        return chave == null ? null : TRATAMENTO.matcher(chave).replaceFirst("");
    }

    /** Mesmo número de palavras, a primeira e a última iguais, e as do meio iguais ou uma a inicial da outra. */
    static boolean abreviado(String a, String b) {
        String[] x = a.split(" ");
        String[] y = b.split(" ");
        if (x.length != y.length || x.length < 3 || !x[0].equals(y[0]) || !x[x.length - 1].equals(y[y.length - 1])) {
            return false;
        }
        boolean algumaAbreviada = false;
        for (int i = 1; i < x.length - 1; i++) {
            if (x[i].equals(y[i])) {
                continue;
            }
            boolean inicial = x[i].length() == 1 && y[i].startsWith(x[i]) || y[i].length() == 1 && x[i].startsWith(y[i]);
            if (!inicial) {
                return false;
            }
            algumaAbreviada = true;
        }
        return algumaAbreviada;
    }
}
