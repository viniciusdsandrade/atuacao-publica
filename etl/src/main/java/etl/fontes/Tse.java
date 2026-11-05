package etl.fontes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Csv;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.Candidatura;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/** TSE: candidaturas, resultado, bens declarados e propostas de governo (docs/FONTES.md). */
public final class Tse implements Fonte {

    static final String CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele";
    static final Set<String> CARGOS_GERAIS = Set.of("PRESIDENTE", "VICE-PRESIDENTE", "GOVERNADOR", "VICE-GOVERNADOR",
            "SENADOR", "1º SUPLENTE", "2º SUPLENTE", "DEPUTADO FEDERAL");
    static final Set<String> UFS_ESTADUAIS = Set.of("SP", "MG", "PR", "BA", "RJ", "RS", "CE");
    static final Set<String> CARGOS_MUNICIPAIS = Set.of("PREFEITO", "VICE-PREFEITO", "VEREADOR");
    static final Set<String> CARGOS = Set.of("PRESIDENTE", "VICE-PRESIDENTE", "GOVERNADOR", "VICE-GOVERNADOR",
            "SENADOR", "1º SUPLENTE", "2º SUPLENTE", "DEPUTADO FEDERAL", "DEPUTADO ESTADUAL", "PREFEITO", "VICE-PREFEITO", "VEREADOR");
    static final Set<String> EXECUTIVO = Set.of("PRESIDENTE", "GOVERNADOR", "PREFEITO");
    static final Set<String> ELEITO = Set.of("ELEITO", "ELEITO POR QP", "ELEITO POR MÉDIA");
    /** Unidades eleitorais municipais cobertas (piloto municipal ADR-0007). */
    static final Set<String> UES_MUNICIPAIS = Set.of("62910"); // Campinas - SP
    static final List<String> UFS = List.of("BR", "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS",
            "MT", "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");

    static boolean cargoValido(Map<String, String> linha) {
        String cargo = linha.get("DS_CARGO");
        if (CARGOS_GERAIS.contains(cargo)) {
            return true;
        }
        if ("DEPUTADO ESTADUAL".equals(cargo)) {
            String uf = linha.get("SG_UF");
            return uf != null && UFS_ESTADUAIS.contains(uf);
        }
        if (CARGOS_MUNICIPAIS.contains(cargo)) {
            String ue = linha.get("SG_UE");
            return ue != null && UES_MUNICIPAIS.contains(ue);
        }
        return false;
    }

    /** Candidatura normalizada, sem CPF, título, e-mail nem data de nascimento. */
    public record Linha(String sq, int ano, String cargo, String uf, String nomeUrna, String partido, boolean eleito,
                        Candidatura registro) {
    }

    @Override
    public String nome() {
        return "tse";
    }

    /**
     * Primeira eleição coberta (ADR-0006). Os senadores em exercício no início da 57ª legislatura, em 2023,
     * foram eleitos em 2018, para mandato de oito anos. A cobertura não encolhe com o passar dos anos.
     */
    static final int PRIMEIRA_ELEICAO = 2018;
    /** Presidente e governadores: a cobertura começa nos mandatos iniciados em 2023. */
    static final int PRIMEIRA_ELEICAO_EXECUTIVA = 2022;
    /** Piloto municipal (ADR-0007 e ADR-0009): a eleição de 2020 elegeu quem exerceu de 2021 a 2024. */
    static final int PRIMEIRA_ELEICAO_MUNICIPAL = 2020;

    /** Eleições cobertas até o ano corrente: gerais (de 4 em 4 anos desde 2018) e municipais (desde 2020). */
    static List<Integer> eleicoes(int anoAtual) {
        Set<Integer> anos = new java.util.TreeSet<>();
        for (int ano = PRIMEIRA_ELEICAO; ano <= anoAtual; ano += 4) {
            anos.add(ano);
        }
        for (int ano = PRIMEIRA_ELEICAO_MUNICIPAL; ano <= anoAtual; ano += 4) {
            anos.add(ano);
        }
        return new ArrayList<>(anos);
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        List<Integer> anos = eleicoes(ctx.hoje().getYear());
        int ultima = anos.getLast();
        for (int ano : anos) {
            Politica politica = ano == ultima ? Politica.SEMPRE : Politica.SE_AUSENTE;
            coleta.obter(CDN + "/consulta_cand/consulta_cand_" + ano + ".zip", "consulta_cand_" + ano + ".zip", "application/zip", politica);
            coleta.obter(CDN + "/bem_candidato/bem_candidato_" + ano + ".zip", "bem_candidato_" + ano + ".zip", "application/zip", politica);
        }
        // Propostas de governo: todas as eleições de mandatos executivos cobertos.
        for (int ano : anos) {
            if (ano < PRIMEIRA_ELEICAO_EXECUTIVA) {
                continue;
            }
            List<String> ufs = (ano % 4 == 0) ? List.of("SP") : UFS;
            for (String uf : ufs) {
                coleta.listarZip(CDN + "/proposta_governo/proposta_governo_" + ano + "_" + uf + ".zip",
                        "propostas/" + ano + "_" + uf + ".json", Politica.SE_AUSENTE);
            }
        }
        coleta.contar("eleicoes", anos.size());
        coleta.periodo(new Meta.Periodo(java.time.LocalDate.of(anos.getFirst(), 1, 1), ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) {
        List<Linha> linhas = new ArrayList<>();
        for (int ano : eleicoes(ctx.hoje().getYear())) {
            Coleta.Arquivo arqCand = arquivo(ctx, "consulta_cand_" + ano + ".zip");
            Map<String, Map<String, String>> porSq = new LinkedHashMap<>();
            Map<String, Integer> turnos = new HashMap<>();
            lerBrasil(Coleta.caminho(ctx, arqCand), linha -> {
                if (!cargoValido(linha)) {
                    return;
                }
                String sq = linha.get("SQ_CANDIDATO");
                int turno = inteiro(linha.get("NR_TURNO"), 1);
                if (turno >= turnos.getOrDefault(sq, 0)) {
                    turnos.put(sq, turno);
                    porSq.put(sq, linha);
                }
            });
            Map<String, BigDecimal> bens = new HashMap<>();
            Coleta.Arquivo arqBens = arquivo(ctx, "bem_candidato_" + ano + ".zip");
            lerBrasil(Coleta.caminho(ctx, arqBens), linha -> {
                String sq = linha.get("SQ_CANDIDATO");
                BigDecimal valor = Texto.valor(linha.get("VR_BEM_CANDIDATO"));
                if (porSq.containsKey(sq) && valor != null) {
                    bens.merge(sq, valor, BigDecimal::add);
                }
            });
            // Nome do PDF: {UF}/{ANO}{UF}{SQ}.pdf em 2022 e {UF}/{ANO}{UF}{SQ}_01.pdf em 2026. Vale o primeiro arquivo do SQ.
            Map<String, String> planoPorSq = new HashMap<>();
            Map<String, String> urlPropostas = new HashMap<>();
            List<String> ufs = (ano % 4 == 0) ? List.of("SP") : UFS;
            for (String uf : ufs) {
                Coleta.Arquivo lista = Coleta.metadados(ctx, "tse", "propostas/" + ano + "_" + uf + ".json");
                if (lista == null) {
                    continue;
                }
                urlPropostas.put(uf, lista.url());
                var padrao = java.util.regex.Pattern.compile("^" + uf + "/" + ano + uf + "([0-9]+)(_[0-9]+)?\\.pdf$");
                List<String> nomes = new ArrayList<>();
                Json.ler(Coleta.caminho(ctx, lista)).forEach(n -> nomes.add(n.stringValue()));
                nomes.sort(null);
                for (String nome : nomes) {
                    var m = padrao.matcher(nome);
                    if (m.matches()) {
                        planoPorSq.putIfAbsent(uf + ":" + m.group(1), nome);
                    }
                }
            }
            Prov prov = Prov.de("tse-cdn", arqCand.url(), arqCand);
            for (var item : porSq.entrySet()) {
                Map<String, String> l = item.getValue();
                String sq = item.getKey();
                String uf = l.get("SG_UF");
                String cargo = l.get("DS_CARGO");
                String ente = CARGOS_MUNICIPAIS.contains(cargo) ? Sapl.IBGE : uf;
                String resultado = semNulo(l.get("DS_SIT_TOT_TURNO"));
                String planoUrl = null;
                String planoArquivo = null;
                if (EXECUTIVO.contains(cargo) && planoPorSq.containsKey(uf + ":" + sq)) {
                    planoUrl = urlPropostas.get(uf);
                    planoArquivo = planoPorSq.get(uf + ":" + sq);
                }
                Candidatura registro = new Candidatura(ano, turnos.get(sq), cargo, ente, semNulo(l.get("NM_URNA_CANDIDATO")),
                        semNulo(l.get("SG_PARTIDO")), semNulo(l.get("NR_CANDIDATO")), semNulo(l.get("DS_SITUACAO_CANDIDATURA")),
                        resultado, bens.get(sq), planoUrl, planoArquivo, prov);
                linhas.add(new Linha(sq, ano, cargo, uf, registro.nomeUrna(), registro.partido(),
                        resultado != null && ELEITO.contains(resultado), registro));
            }
        }
        Json.gravar(ctx.normalizado().resolve("tse").resolve("candidaturas.json"), linhas);
        System.out.println("tse: " + linhas.size() + " candidaturas normalizadas");
    }

    /** Lê o CSV com todas as UFs (_BRASIL.csv) dentro do ZIP do TSE. */
    static void lerBrasil(Path zip, Consumer<Map<String, String>> acao) {
        try (ZipFile arquivo = new ZipFile(zip.toFile())) {
            ZipEntry brasil = arquivo.stream().filter(e -> e.getName().endsWith("_BRASIL.csv")).findFirst()
                    .orElseThrow(() -> new IOException("sem _BRASIL.csv em " + zip.getFileName()));
            Csv.paraCada(arquivo.getInputStream(brasil), StandardCharsets.ISO_8859_1, 0, acao);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Chaves sensíveis por SQ_CANDIDATO, só em memória (ADR-0003). Nunca gravar nem imprimir. */
    public record Chave(String cpf, String nomeCivil, String uf, String nascimento, String cargo) {
    }

    public static Map<String, Chave> chaves(Contexto ctx) {
        Map<String, Chave> chaves = new HashMap<>();
        for (int ano : eleicoes(ctx.hoje().getYear())) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "tse", "consulta_cand_" + ano + ".zip");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            lerBrasil(Coleta.caminho(ctx, arq), l -> {
                if (cargoValido(l)) {
                    String cpf = Texto.digitos(l.get("NR_CPF_CANDIDATO"));
                    chaves.put(l.get("SQ_CANDIDATO"), new Chave(cpf.length() == 11 ? cpf : null,
                            Texto.normalizarNome(l.get("NM_CANDIDATO")), l.get("SG_UF"), Texto.dataBr(l.get("DT_NASCIMENTO")),
                            l.get("DS_CARGO")));
                }
            });
        }
        return chaves;
    }

    private static String semNulo(String valor) {
        String v = Texto.vazioParaNulo(valor);
        return v == null || v.startsWith("#NULO") || v.equals("#NE") ? null : v;
    }

    private static int inteiro(String texto, int padrao) {
        try {
            return Integer.parseInt(texto.strip());
        } catch (RuntimeException e) {
            return padrao;
        }
    }

    static Coleta.Arquivo arquivo(Contexto ctx, String relativo) {
        Coleta.Arquivo arquivo = Coleta.metadados(ctx, "tse", relativo);
        if (arquivo == null) {
            throw new IllegalStateException("arquivo bruto ausente: raw/tse/" + relativo + " (rode fetch tse)");
        }
        return arquivo;
    }

    /** Linhas normalizadas, ou lista vazia se o TSE ainda não foi normalizado. */
    public static List<JsonNode> linhas(Contexto ctx) {
        Path arquivo = ctx.normalizado().resolve("tse").resolve("candidaturas.json");
        List<JsonNode> linhas = new ArrayList<>();
        if (Files.exists(arquivo)) {
            Json.ler(arquivo).forEach(linhas::add);
        }
        return linhas;
    }
}
