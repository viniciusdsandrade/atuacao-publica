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

    /** Nome oficial por extenso no TSE, indexado pela chave da sigla canônica (ADR-0064). */
    private static final Map<String, String> NOMES_COMPLETOS = Map.ofEntries(
            Map.entry("AGIR", "Agir"),
            Map.entry("AVANTE", "Avante"),
            Map.entry("CIDADANIA", "Cidadania"),
            Map.entry("DC", "Democracia Cristã"),
            Map.entry("DEM", "Democratas"),
            Map.entry("DEMOCRATA", "Democrata"),
            Map.entry("MDB", "Movimento Democrático Brasileiro"),
            Map.entry("MISSAO", "Partido Missão"),
            Map.entry("MOBILIZA", "Mobilização Nacional"),
            Map.entry("NOVO", "Partido Novo"),
            Map.entry("PATRIOTA", "Patriota"),
            Map.entry("PCB", "Partido Comunista Brasileiro"),
            Map.entry("PCDOB", "Partido Comunista do Brasil"),
            Map.entry("PCO", "Partido da Causa Operária"),
            Map.entry("PDT", "Partido Democrático Trabalhista"),
            Map.entry("PHS", "Partido Humanista da Solidariedade"),
            Map.entry("PL", "Partido Liberal"),
            Map.entry("PMB", "Partido da Mulher Brasileira"),
            Map.entry("PMN", "Partido da Mobilização Nacional"),
            Map.entry("PODE", "Podemos"),
            Map.entry("PP", "Progressistas"),
            Map.entry("PPL", "Partido Pátria Livre"),
            Map.entry("PPS", "Partido Popular Socialista"),
            Map.entry("PR", "Partido da República"),
            Map.entry("PRB", "Partido Republicano Brasileiro"),
            Map.entry("PRD", "Partido Renovação Democrática"),
            Map.entry("PROS", "Partido Republicano da Ordem Social"),
            Map.entry("PRP", "Partido Republicano Progressista"),
            Map.entry("PRTB", "Partido Renovador Trabalhista Brasileiro"),
            Map.entry("PSB", "Partido Socialista Brasileiro"),
            Map.entry("PSC", "Partido Social Cristão"),
            Map.entry("PSD", "Partido Social Democrático"),
            Map.entry("PSDB", "Partido da Social Democracia Brasileira"),
            Map.entry("PSL", "Partido Social Liberal"),
            Map.entry("PSOL", "Partido Socialismo e Liberdade"),
            Map.entry("PSTU", "Partido Socialista dos Trabalhadores Unificado"),
            Map.entry("PT", "Partido dos Trabalhadores"),
            Map.entry("PTB", "Partido Trabalhista Brasileiro"),
            Map.entry("PTC", "Partido Trabalhista Cristão"),
            Map.entry("PV", "Partido Verde"),
            Map.entry("REDE", "Rede Sustentabilidade"),
            Map.entry("REPUBLICANOS", "Republicanos"),
            Map.entry("SOLIDARIEDADE", "Solidariedade"),
            Map.entry("UNIAO", "União Brasil"),
            Map.entry("UP", "Unidade Popular"));

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

    /** Nome oficial completo do partido a partir da sigla ou apelido conhecido, ou nulo quando desconhecido. */
    public static String nomeCompleto(String original) {
        String unificada = unificar(original, Map.of());
        if (unificada == null) {
            return null;
        }
        if (SEM_PARTIDO.equals(unificada)) {
            return SEM_PARTIDO;
        }
        String chave = chave(unificada);
        return chave == null ? null : NOMES_COMPLETOS.get(chave);
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
