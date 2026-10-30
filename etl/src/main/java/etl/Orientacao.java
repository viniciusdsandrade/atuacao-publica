package etl;

import java.util.Locale;
import java.util.Map;

/**
 * Casa o partido do parlamentar com a bancada que orientou o voto (docs/METODOLOGIA.md).
 * Vale partido igual, partido dentro do nome de federação ("Fdr PT-PCdoB-PV") e rótulo truncado pela fonte.
 */
public final class Orientacao {

    /** Rótulos da fonte que designam o mesmo partido com outro nome. */
    private static final Map<String, String> APELIDOS = Map.of("PROGRESSISTAS", "PP");
    private static final int PREFIXO_MINIMO = 6;

    public record Casamento(String bancada, String orientacao) {
    }

    private Orientacao() {
    }

    /** Orientação da bancada do partido, ou null quando nenhuma bancada corresponde ao partido. */
    public static Casamento doPartido(String partido, Map<String, String> orientacoesPorBancada) {
        if (partido == null) {
            return null;
        }
        String alvo = chave(partido);
        for (var item : orientacoesPorBancada.entrySet()) {
            String bancada = item.getKey();
            boolean casa;
            if (bancada.toUpperCase(Locale.ROOT).startsWith("FDR ")) {
                casa = false;
                for (String componente : bancada.substring(4).split("-")) {
                    casa |= mesmoPartido(chave(componente), alvo);
                }
            } else {
                casa = mesmoPartido(chave(bancada), alvo);
            }
            if (casa) {
                return new Casamento(bancada, Texto.vazioParaNulo(item.getValue()));
            }
        }
        return null;
    }

    private static boolean mesmoPartido(String rotulo, String partido) {
        return rotulo.equals(partido) || (rotulo.length() >= PREFIXO_MINIMO && partido.startsWith(rotulo));
    }

    private static String chave(String nome) {
        String limpo = Texto.normalizarNome(nome).replaceAll("[^A-Z0-9]", "");
        return APELIDOS.getOrDefault(limpo, limpo);
    }
}
