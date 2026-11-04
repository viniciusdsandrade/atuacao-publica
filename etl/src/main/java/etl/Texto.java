package etl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalização de texto vindo das fontes. */
public final class Texto {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private static final Pattern ENTIDADE = Pattern.compile("&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Map<String, String> ENTIDADES = Map.of(
            "nbsp", " ", "amp", "&", "quot", "\"", "apos", "'", "lt", "<", "gt", ">",
            "ordm", "º", "ordf", "ª", "ndash", "-", "mdash", "-");
    /** Entidade de letra acentuada (&atilde;, &ccedil;): letra base mais diacrítico combinante. */
    private static final Pattern LETRA_ACENTUADA = Pattern.compile("^([A-Za-z])(acute|grave|circ|tilde|uml|cedil|ring)$");
    private static final Map<String, String> DIACRITICOS = Map.of(
            "acute", "́", "grave", "̀", "circ", "̂", "tilde", "̃",
            "uml", "̈", "cedil", "̧", "ring", "̊");

    private Texto() {
    }

    /** Nome para junção entre fontes: sem acento, maiúsculo, espaços simples. */
    public static String normalizarNome(String nome) {
        if (nome == null) {
            return null;
        }
        String semAcento = MARCAS.matcher(Normalizer.normalize(nome, Normalizer.Form.NFD)).replaceAll("");
        return ESPACOS.matcher(semAcento.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** Troca entidades HTML do texto oficial pelo caractere. O conteúdo não muda. */
    public static String semEntidades(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = ENTIDADE.matcher(texto);
        StringBuilder saida = new StringBuilder();
        while (m.find()) {
            String nome = m.group(1);
            String troca;
            if (nome.startsWith("#x")) {
                troca = Character.toString(Integer.parseInt(nome.substring(2), 16));
            } else if (nome.startsWith("#")) {
                troca = Character.toString(Integer.parseInt(nome.substring(1)));
            } else if (LETRA_ACENTUADA.matcher(nome).matches()) {
                Matcher letra = LETRA_ACENTUADA.matcher(nome);
                letra.matches();
                troca = Normalizer.normalize(letra.group(1) + DIACRITICOS.get(letra.group(2)), Normalizer.Form.NFC);
            } else {
                troca = ENTIDADES.getOrDefault(nome, m.group());
            }
            m.appendReplacement(saida, Matcher.quoteReplacement(troca));
        }
        m.appendTail(saida);
        String limpo = ESPACOS.matcher(saida.toString().replace(' ', ' ')).replaceAll(" ").strip();
        return limpo.isEmpty() ? null : limpo;
    }

    public static String digitos(String texto) {
        return texto == null ? "" : texto.replaceAll("[^0-9]", "");
    }

    /** CNPJ com 14 dígitos. Documento com outro tamanho (CPF, mascarado) vira null. */
    public static String cnpj(String documento) {
        String d = digitos(documento);
        return d.length() == 14 ? d : null;
    }

    /** Valor em formato brasileiro ("1.234,56") ou com ponto decimal ("1234.56"). */
    public static BigDecimal valor(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String t = texto.strip();
        if (t.contains(",")) {
            t = t.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Converte "dd/mm/aaaa" para "aaaa-mm-dd". Outro formato vira null. */
    public static String dataBr(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})").matcher(texto.strip());
        return m.find() ? m.group(3) + "-" + m.group(2) + "-" + m.group(1) : null;
    }

    public static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /** Lê arquivo de texto UTF-8, descompactando se terminar com .gz. */
    public static String lerArquivo(java.nio.file.Path arquivo) throws java.io.IOException {
        if (arquivo.getFileName().toString().endsWith(".gz")) {
            try (var in = new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(arquivo), 1 << 16)) {
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return java.nio.file.Files.readString(arquivo);
    }
}
