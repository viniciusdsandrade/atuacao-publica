package etl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Bloqueio de dado pessoal: CPF, telefone e e-mail nunca saem publicados.
 * Vale mesmo quando a fonte oficial expõe o dado (política editorial).
 */
public final class Guarda {

    static final Pattern CPF = Pattern.compile("(?<![0-9])[0-9]{3}\\.?[0-9]{3}\\.?[0-9]{3}-?[0-9]{2}(?![0-9])");
    static final Pattern TELEFONE = Pattern.compile(
            "(?<![0-9])(\\+55\\s?)?(\\(\\s?[0-9]{2}\\s?\\)|[0-9]{2})[\\s-]?9?[0-9]{4}-[0-9]{4}(?![0-9])");
    static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    private static final Map<String, Pattern> PADROES = Map.of("CPF", CPF, "telefone", TELEFONE, "e-mail", EMAIL);

    /** Campos com número de 11 dígitos que não é CPF (identificadores oficiais e URLs). */
    private static final List<String> CAMPOS_IGNORADOS = List.of(
            "id", "id_fonte", "votacao_id", "politico_id", "fornecedor_cnpj", "documento_numero", "numero", "caminho");

    private Guarda() {
    }

    /** Remove do nome do fornecedor a sequência de 11 dígitos (CPF no nome de MEI). */
    public static String semCpf(String texto) {
        if (texto == null) {
            return null;
        }
        return Texto.vazioParaNulo(CPF.matcher(texto).replaceAll("").replaceAll("\\s{2,}", " "));
    }

    /** Lista os campos de texto que casam com algum padrão bloqueado. Não devolve o trecho. */
    public static List<String> violacoes(JsonNode no) {
        List<String> achados = new ArrayList<>();
        varrer(no, "", achados);
        return achados;
    }

    public static List<String> violacoes(String texto) {
        List<String> achados = new ArrayList<>();
        PADROES.forEach((nome, padrao) -> {
            if (texto != null && padrao.matcher(texto).find()) {
                achados.add(nome);
            }
        });
        return achados;
    }

    private static void varrer(JsonNode no, String caminho, List<String> achados) {
        if (no.isObject()) {
            for (var campo : no.properties()) {
                varrer(campo.getValue(), caminho + "/" + campo.getKey(), achados);
            }
        } else if (no.isArray()) {
            int i = 0;
            for (JsonNode item : no) {
                varrer(item, caminho + "/" + i++, achados);
            }
        } else if (no.isString()) {
            String campo = caminho.substring(caminho.lastIndexOf('/') + 1);
            String valor = no.stringValue();
            boolean url = valor.startsWith("http://") || valor.startsWith("https://");
            for (String tipo : violacoes(valor)) {
                boolean identificador = tipo.equals("CPF") && (url || CAMPOS_IGNORADOS.contains(campo));
                if (!identificador && !(url && tipo.equals("telefone"))) {
                    achados.add(tipo + " em " + caminho);
                }
            }
        }
    }
}
