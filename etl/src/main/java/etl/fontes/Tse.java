package etl.fontes;

import java.io.IOException;
import java.io.InputStream;
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
import etl.Registros.RedeDeclarada;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/** TSE: candidaturas, resultado, bens declarados e propostas de governo (docs/FONTES.md). */
public final class Tse implements Fonte {

    static final String CDN = "https://cdn.tse.jus.br/estatistica/sead/odsele";
    static final Set<String> CARGOS_GERAIS = Set.of("PRESIDENTE", "VICE-PRESIDENTE", "GOVERNADOR", "VICE-GOVERNADOR",
            "SENADOR", "1º SUPLENTE", "2º SUPLENTE", "DEPUTADO FEDERAL");
    static final Set<String> UFS_ESTADUAIS = Set.of("SP", "MG", "PR", "BA", "RJ", "RS", "CE", "SC", "GO", "ES", "PA", "PE", "DF", "MT", "MS", "PB", "AM", "AL", "RO", "PI", "RN", "TO", "SE", "MA", "AC");
    static final Set<String> CARGOS_MUNICIPAIS = Set.of("PREFEITO", "VICE-PREFEITO", "VEREADOR");
    static final Set<String> CARGOS = Set.of("PRESIDENTE", "VICE-PRESIDENTE", "GOVERNADOR", "VICE-GOVERNADOR",
            "SENADOR", "1º SUPLENTE", "2º SUPLENTE", "DEPUTADO FEDERAL", "DEPUTADO ESTADUAL", "DEPUTADO DISTRITAL", "PREFEITO", "VICE-PREFEITO", "VEREADOR");
    static final Set<String> EXECUTIVO = Set.of("PRESIDENTE", "GOVERNADOR", "PREFEITO");
    static final Set<String> ELEITO = Set.of("ELEITO", "ELEITO POR QP", "ELEITO POR MÉDIA");
    /** Município coberto: código IBGE do ente e primeira eleição que entra (a regra de mandato supõe que o eleito exerce os quatro anos). */
    record Municipio(String ibge, int primeiraEleicao) {
    }

    /** Unidades eleitorais municipais cobertas, por código da UE no TSE (ADR-0007 e ADR-0027). */
    static final Map<String, Municipio> UES_MUNICIPAIS = Map.of(
            "62910", new Municipio(Sapl.IBGE, 2020), // Campinas - SP
            "71072", new Municipio(Cmsp.IBGE, 2024)); // São Paulo - SP
    static final List<String> UFS = List.of("BR", "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS",
            "MT", "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");

    static boolean cargoValido(Map<String, String> linha) {
        String cargo = linha.get("DS_CARGO");
        if (CARGOS_GERAIS.contains(cargo)) {
            return true;
        }
        if ("DEPUTADO ESTADUAL".equals(cargo) || "DEPUTADO DISTRITAL".equals(cargo)) {
            String uf = linha.get("SG_UF");
            return uf != null && UFS_ESTADUAIS.contains(uf);
        }
        if (CARGOS_MUNICIPAIS.contains(cargo)) {
            String ue = linha.get("SG_UE");
            Municipio municipio = ue == null ? null : UES_MUNICIPAIS.get(ue);
            return municipio != null && anoDaEleicao(linha) >= municipio.primeiraEleicao();
        }
        return false;
    }

    private static int anoDaEleicao(Map<String, String> linha) {
        try {
            return Integer.parseInt(linha.get("ANO_ELEICAO"));
        } catch (NumberFormatException e) {
            return 0;
        }
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
            // Redes e sites declarados (ADR-0031): dado complementar; a falha deixa a fonte parcial, sem derrubar candidaturas.
            coleta.obterOpcional(CDN + "/consulta_cand/rede_social_candidato_" + ano + ".zip", "rede_social_candidato_" + ano + ".zip",
                    "application/zip", politica);
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
        // Prestação de contas (ADR-0032): só receitas e despesas contratadas, lidas por Range do ZIP de centenas de MB,
        // e só as linhas das candidaturas cobertas. A eleição mais recente renova por semana: as contas chegam depois do pleito.
        for (int ano : anos) {
            Coleta.Arquivo candidaturas = Coleta.metadados(ctx, "tse", "consulta_cand_" + ano + ".zip");
            if (candidaturas == null || !Files.exists(Coleta.caminho(ctx, candidaturas))) {
                continue;
            }
            Set<String> cobertas = new HashSet<>();
            lerBrasil(Coleta.caminho(ctx, candidaturas), l -> {
                if (cargoValido(l)) {
                    cobertas.add(l.get("SQ_CANDIDATO"));
                }
            });
            Politica politicaDasContas = ano == ultima ? Politica.SEMANAL : Politica.SE_AUSENTE;
            List<String> entradas = entradasDeContas(ano);
            coleta.extrairDoZip(zipDeContas(ano), entradas.get(0), recorteDeContas(entradas.get(0)), politicaDasContas,
                    (dados, destino) -> recortarCandidaturas(dados, destino, cobertas, COLUNAS_DE_RECEITA));
            coleta.extrairDoZip(zipDeContas(ano), entradas.get(1), recorteDeContas(entradas.get(1)), politicaDasContas,
                    (dados, destino) -> recortarCandidaturas(dados, destino, cobertas, COLUNAS_DE_DESPESA));
        }
        coleta.contar("eleicoes", anos.size());
        coleta.periodo(new Meta.Periodo(java.time.LocalDate.of(anos.getFirst(), 1, 1), ctx.hoje()));
    }

    /** Todos os municípios cobertos ficam em SP (UES_MUNICIPAIS): a eleição municipal lê só o CSV dessa UF. */
    static final String UF_DOS_MUNICIPIOS = "SP";
    static final String RECEITAS = "receitas";
    static final String DESPESAS = "despesas contratadas";
    /** Maiores fornecedores com CNPJ por candidatura, pelo valor contratado. */
    static final int MAIORES_FORNECEDORES = 10;

    static String zipDeContas(int ano) {
        return CDN + "/prestacao_contas/prestacao_de_contas_eleitorais_candidatos_" + ano + ".zip";
    }

    /** Eleição geral: o CSV _BRASIL. Eleição municipal (ano múltiplo de 4): o CSV da UF dos municípios cobertos. */
    static List<String> entradasDeContas(int ano) {
        String recorte = ano % 4 == 0 ? UF_DOS_MUNICIPIOS : "BRASIL";
        return List.of("receitas_candidatos_" + ano + "_" + recorte + ".csv",
                "despesas_contratadas_candidatos_" + ano + "_" + recorte + ".csv");
    }

    /** Colunas que o normalize usa. As demais (CPF e nome de doador, CPF do candidato e do vice, gênero, cor) não entram no cache. */
    static final List<String> COLUNAS_DE_RECEITA = List.of("SQ_CANDIDATO", "SQ_RECEITA", "DS_FONTE_RECEITA", "DS_ORIGEM_RECEITA",
            "VR_RECEITA");
    static final List<String> COLUNAS_DE_DESPESA = List.of("SQ_CANDIDATO", "SQ_DESPESA", "DS_ORIGEM_DESPESA",
            "NR_CPF_CNPJ_FORNECEDOR", "NM_FORNECEDOR", "NM_FORNECEDOR_RFB", "VR_DESPESA_CONTRATADA");

    static String recorteDeContas(String entrada) {
        return "prestacao_contas/" + entrada + ".gz";
    }

    /**
     * Do CSV da prestação de contas, só as linhas das candidaturas cobertas e só as colunas usadas, compactado.
     * Fornecedor pessoa física fica sem documento e sem nome já no cache: só o valor e o tipo da despesa contam.
     * Na eleição geral, quase todo candidato a deputado é coberto: sem o corte de colunas, o recorte passaria de 4 GB.
     */
    static void recortarCandidaturas(InputStream dados, Path destino, Set<String> cobertas, List<String> colunas) throws IOException {
        try (java.io.Writer saida = new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                new java.util.zip.GZIPOutputStream(Files.newOutputStream(destino), 65536), StandardCharsets.ISO_8859_1))) {
            Csv.escreverLinha(saida, colunas);
            Csv.paraCada(dados, StandardCharsets.ISO_8859_1, 0, linha -> {
                if (!cobertas.contains(linha.get("SQ_CANDIDATO"))) {
                    return;
                }
                boolean pessoaFisica = linha.containsKey("NR_CPF_CNPJ_FORNECEDOR") && Texto.cnpj(linha.get("NR_CPF_CNPJ_FORNECEDOR")) == null;
                List<String> valores = new ArrayList<>(colunas.size());
                for (String coluna : colunas) {
                    boolean identifica = pessoaFisica && (coluna.equals("NR_CPF_CNPJ_FORNECEDOR") || coluna.startsWith("NM_FORNECEDOR"));
                    valores.add(identifica ? "" : linha.get(coluna));
                }
                try {
                    Csv.escreverLinha(saida, valores);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Receitas e despesas contratadas de cada candidatura coberta, somadas por categoria (ADR-0032). Doador e fornecedor
     * pessoa física nunca saem por nome nem por CPF: entram só nas somas. Fornecedor com CNPJ sai pelo nome e pelo CNPJ.
     */
    static Map<String, List<Object>> financiamento(Contexto ctx, int ano, Map<String, Map<String, String>> candidaturas) {
        Map<String, List<Object>> porSq = new HashMap<>();
        List<String> entradas = entradasDeContas(ano);
        Coleta.Arquivo receitas = Coleta.metadados(ctx, "tse", recorteDeContas(entradas.get(0)));
        Coleta.Arquivo despesas = Coleta.metadados(ctx, "tse", recorteDeContas(entradas.get(1)));
        if (receitas != null && Files.exists(Coleta.caminho(ctx, receitas))) {
            Prov prov = Prov.de("tse-cdn", receitas.url(), receitas);
            Map<String, Somas> somas = new HashMap<>();
            lerCsv(Coleta.caminho(ctx, receitas), l -> {
                if (candidaturas.containsKey(l.get("SQ_CANDIDATO"))) {
                    somas.computeIfAbsent(l.get("SQ_CANDIDATO"), k -> new Somas()).receita(l);
                }
            });
            somas.forEach((sq, s) -> porSq.computeIfAbsent(sq, k -> new ArrayList<>())
                    .add(s.receitas(ano, candidaturas.get(sq).get("DS_CARGO"), prov)));
        }
        if (despesas != null && Files.exists(Coleta.caminho(ctx, despesas))) {
            Prov prov = Prov.de("tse-cdn", despesas.url(), despesas);
            Map<String, Somas> somas = new HashMap<>();
            lerCsv(Coleta.caminho(ctx, despesas), l -> {
                if (candidaturas.containsKey(l.get("SQ_CANDIDATO"))) {
                    somas.computeIfAbsent(l.get("SQ_CANDIDATO"), k -> new Somas()).despesa(l);
                }
            });
            somas.forEach((sq, s) -> porSq.computeIfAbsent(sq, k -> new ArrayList<>())
                    .add(s.despesas(ano, candidaturas.get(sq).get("DS_CARGO"), prov)));
        }
        return porSq;
    }

    private static void lerCsv(Path arquivo, Consumer<Map<String, String>> acao) {
        try (InputStream entrada = new java.util.zip.GZIPInputStream(Files.newInputStream(arquivo), 65536)) {
            Csv.paraCada(entrada, StandardCharsets.ISO_8859_1, 0, acao);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Somas de uma candidatura. Cada receita (SQ_RECEITA) e cada despesa (SQ_DESPESA) conta uma vez. */
    static final class Somas {
        private final Set<String> vistas = new HashSet<>();
        private BigDecimal total = BigDecimal.ZERO;
        private int quantidade;
        private final Map<String, BigDecimal[]> porFonte = new HashMap<>();
        private final Map<String, BigDecimal[]> porOrigem = new HashMap<>();
        private final Map<String, BigDecimal[]> porTipo = new HashMap<>();
        private final Map<String, BigDecimal[]> porFornecedor = new HashMap<>();
        private final Map<String, String> nomeDoFornecedor = new HashMap<>();

        void receita(Map<String, String> l) {
            BigDecimal valor = Texto.valor(l.get("VR_RECEITA"));
            if (valor == null || !primeira(l.get("SQ_RECEITA"))) {
                return;
            }
            total = total.add(valor);
            quantidade++;
            somar(porFonte, categoria(l.get("DS_FONTE_RECEITA")), valor);
            somar(porOrigem, categoria(l.get("DS_ORIGEM_RECEITA")), valor);
        }

        void despesa(Map<String, String> l) {
            BigDecimal valor = Texto.valor(l.get("VR_DESPESA_CONTRATADA"));
            if (valor == null || !primeira(l.get("SQ_DESPESA"))) {
                return;
            }
            total = total.add(valor);
            quantidade++;
            somar(porTipo, categoria(l.get("DS_ORIGEM_DESPESA")), valor);
            String cnpj = Texto.cnpj(l.get("NR_CPF_CNPJ_FORNECEDOR"));
            if (cnpj != null) {
                somar(porFornecedor, cnpj, valor);
                String nome = semNulo(l.get("NM_FORNECEDOR_RFB"));
                nomeDoFornecedor.putIfAbsent(cnpj, etl.Guarda.semCpf(nome != null ? nome : semNulo(l.get("NM_FORNECEDOR"))));
            }
        }

        /** Sem identificador na linha, a linha conta: só um identificador repetido indica a mesma receita ou despesa. */
        private boolean primeira(String id) {
            String chave = semNulo(id);
            return chave == null || vistas.add(chave);
        }

        etl.Registros.ReceitasDeCampanha receitas(int ano, String cargo, Prov prov) {
            return new etl.Registros.ReceitasDeCampanha(ano, cargo, RECEITAS, total, quantidade, lista(porFonte), lista(porOrigem), prov);
        }

        etl.Registros.DespesasDeCampanha despesas(int ano, String cargo, Prov prov) {
            List<etl.Registros.FornecedorDeCampanha> fornecedores = new ArrayList<>();
            porFornecedor.forEach((cnpj, s) -> fornecedores.add(
                    new etl.Registros.FornecedorDeCampanha(nomeDoFornecedor.get(cnpj), cnpj, s[0], s[1].intValue())));
            fornecedores.sort(java.util.Comparator.comparing(etl.Registros.FornecedorDeCampanha::valor).reversed()
                    .thenComparing(etl.Registros.FornecedorDeCampanha::cnpj));
            return new etl.Registros.DespesasDeCampanha(ano, cargo, DESPESAS, total, quantidade, lista(porTipo),
                    List.copyOf(fornecedores.subList(0, Math.min(MAIORES_FORNECEDORES, fornecedores.size()))), prov);
        }

        private static String categoria(String valor) {
            String v = semNulo(valor);
            return v == null ? "(vazio na fonte)" : v;
        }

        private static void somar(Map<String, BigDecimal[]> mapa, String chave, BigDecimal valor) {
            BigDecimal[] s = mapa.computeIfAbsent(chave, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(valor);
            s[1] = s[1].add(BigDecimal.ONE);
        }

        /** Categorias do maior valor para o menor; empate pelo nome. */
        private static List<etl.Registros.Soma> lista(Map<String, BigDecimal[]> mapa) {
            List<etl.Registros.Soma> lista = new ArrayList<>();
            mapa.forEach((categoria, s) -> lista.add(new etl.Registros.Soma(categoria, s[0], s[1].intValue())));
            lista.sort(java.util.Comparator.comparing(etl.Registros.Soma::valor).reversed().thenComparing(etl.Registros.Soma::categoria));
            return lista;
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<Linha> linhas = new ArrayList<>();
        ContagemDeRedes contagemDeRedes = new ContagemDeRedes();
        Map<String, List<Object>> financiamento = new java.util.TreeMap<>();
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
            Map<String, List<RedeDeclarada>> redes = redesDeclaradas(ctx, ano, porSq.keySet(), contagemDeRedes);
            financiamento.putAll(financiamento(ctx, ano, porSq));
            Prov prov = Prov.de("tse-cdn", arqCand.url(), arqCand);
            for (var item : porSq.entrySet()) {
                Map<String, String> l = item.getValue();
                String sq = item.getKey();
                String uf = l.get("SG_UF");
                String cargo = l.get("DS_CARGO");
                String ente = CARGOS_MUNICIPAIS.contains(cargo) ? UES_MUNICIPAIS.get(l.get("SG_UE")).ibge() : uf;
                String resultado = semNulo(l.get("DS_SIT_TOT_TURNO"));
                String planoUrl = null;
                String planoArquivo = null;
                if (EXECUTIVO.contains(cargo) && planoPorSq.containsKey(uf + ":" + sq)) {
                    planoUrl = urlPropostas.get(uf);
                    planoArquivo = planoPorSq.get(uf + ":" + sq);
                }
                Candidatura registro = new Candidatura(ano, turnos.get(sq), cargo, ente, semNulo(l.get("NM_URNA_CANDIDATO")),
                        semNulo(l.get("SG_PARTIDO")), semNulo(l.get("NR_CANDIDATO")), semNulo(l.get("DS_SITUACAO_CANDIDATURA")),
                        resultado, bens.get(sq), planoUrl, planoArquivo, redes.getOrDefault(sq, List.of()), prov);
                linhas.add(new Linha(sq, ano, cargo, uf, registro.nomeUrna(), registro.partido(),
                        resultado != null && ELEITO.contains(resultado), registro));
            }
        }
        Json.gravar(ctx.normalizado().resolve("tse").resolve("candidaturas.json"), linhas);
        Json.gravar(ctx.normalizado().resolve("tse").resolve("financiamento.json"), financiamento);
        System.out.println("tse: " + linhas.size() + " candidaturas normalizadas; " + financiamento.size()
                + " com prestação de contas na fonte");
        System.out.println("tse: " + contagemDeRedes.publicados + " endereço(s) de rede ou site declarado(s); "
                + contagemDeRedes.comDadoPessoal + " fora por telefone, e-mail ou CPF; " + contagemDeRedes.semEndereco
                + " fora por não ser endereço; " + contagemDeRedes.alemDoLimite + " além do limite de " + LIMITE_DE_REDES
                + " por candidatura");
    }

    /** O que entra e o que fica fora dos endereços declarados, para a saída do normalize. */
    static final class ContagemDeRedes {
        int publicados;
        int comDadoPessoal;
        int semEndereco;
        int alemDoLimite;
    }

    /** Redes e sites declarados de cada candidatura coberta, na ordem da fonte e sem repetir endereço (ADR-0031). */
    static Map<String, List<RedeDeclarada>> redesDeclaradas(Contexto ctx, int ano, Set<String> sqs, ContagemDeRedes contagem) {
        Map<String, List<RedeDeclarada>> redes = new HashMap<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, "tse", "rede_social_candidato_" + ano + ".zip");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return redes;
        }
        Prov prov = Prov.de("tse-cdn", arq.url(), arq);
        Map<String, List<Map<String, String>>> declarados = new HashMap<>();
        lerBrasil(Coleta.caminho(ctx, arq), linha -> {
            if (sqs.contains(linha.get("SQ_CANDIDATO"))) {
                declarados.computeIfAbsent(linha.get("SQ_CANDIDATO"), k -> new ArrayList<>()).add(linha);
            }
        });
        for (var item : declarados.entrySet()) {
            List<RedeDeclarada> lista = new ArrayList<>();
            Set<String> vistos = new HashSet<>();
            List<Map<String, String>> linhas = item.getValue();
            linhas.sort(java.util.Comparator.comparingInt(l -> inteiro(l.get("NR_ORDEM_REDE_SOCIAL"), Integer.MAX_VALUE)));
            for (Map<String, String> linha : linhas) {
                String bruto = linha.get("DS_URL");
                if (temDadoPessoal(bruto)) {
                    contagem.comDadoPessoal++;
                    continue;
                }
                String url = enderecoDeclarado(bruto);
                if (url == null) {
                    contagem.semEndereco++;
                } else if (vistos.add(url.toLowerCase(java.util.Locale.ROOT))) {
                    lista.add(new RedeDeclarada(rede(url), url, prov));
                    contagem.publicados++;
                }
            }
            if (lista.size() > LIMITE_DE_REDES) {
                contagem.alemDoLimite += lista.size() - LIMITE_DE_REDES;
                contagem.publicados -= lista.size() - LIMITE_DE_REDES;
                lista = new ArrayList<>(lista.subList(0, LIMITE_DE_REDES));
            }
            if (!lista.isEmpty()) {
                redes.put(item.getKey(), lista);
            }
        }
        return redes;
    }

    /**
     * Endereços por candidatura, na ordem declarada. Mediana de 4 e p99 de 15 em 2026-11-06; uma candidatura passava
     * de 90, a maioria convites de grupo. O limite protege o orçamento de tamanho do perfil (ADR-0001).
     */
    static final int LIMITE_DE_REDES = 20;

    /** Domínios das redes mais declaradas. O resto aparece como "Site". */
    private static final List<Map.Entry<String, List<String>>> REDES = List.of(
            Map.entry("Instagram", List.of("instagram.com")),
            Map.entry("Facebook", List.of("facebook.com", "fb.com", "fb.me")),
            Map.entry("X (Twitter)", List.of("x.com", "twitter.com")),
            Map.entry("YouTube", List.of("youtube.com", "youtu.be")),
            Map.entry("TikTok", List.of("tiktok.com")),
            Map.entry("LinkedIn", List.of("linkedin.com")),
            Map.entry("Telegram", List.of("t.me", "telegram.me", "telegram.org")),
            Map.entry("Kwai", List.of("kwai.com")),
            Map.entry("Threads", List.of("threads.net", "threads.com")),
            Map.entry("WhatsApp", List.of("whatsapp.com")));

    /** Endereço que leva a telefone ou e-mail: link de Telegram por número, tel:, mailto:, número com DDI. */
    private static final java.util.regex.Pattern NUMERO_DE_TELEFONE = java.util.regex.Pattern.compile(
            "(phone=|t\\.me/\\+|tel:|mailto:|(?<![0-9])\\+?55[0-9]{10,11}(?![0-9]))", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern WHATSAPP = java.util.regex.Pattern.compile(
            "(wa\\.me|wa\\.link|whatsapp\\.com)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern CANAL_DO_WHATSAPP = java.util.regex.Pattern.compile(
            "(^|[/.])whatsapp\\.com/channel/", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DOMINIO = java.util.regex.Pattern.compile(
            "^(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+(/|$)", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Endereço que leva a telefone ou e-mail. No WhatsApp, só o canal não leva: conversa, link curto e convite de grupo
     * mostram o número, porque quem entra num grupo vê o telefone de quem o administra.
     */
    static boolean temDadoPessoal(String bruto) {
        if (bruto == null) {
            return false;
        }
        if (WHATSAPP.matcher(bruto).find() && !CANAL_DO_WHATSAPP.matcher(bruto).find()) {
            return true;
        }
        return NUMERO_DE_TELEFONE.matcher(bruto).find() || !etl.Guarda.violacoes(bruto).isEmpty();
    }

    /**
     * Endereço como o candidato declarou, pronto para publicar, ou null. Sem esquema, ganha https://. Texto que não é
     * endereço (um "@perfil" solto, por exemplo) fica fora: o site não adivinha a rede. Esquema e domínio vão para
     * minúsculas, porque não diferenciam maiúsculas (RFC 3986): o arquivo de 2026 traz a maior parte dos endereços todo
     * em maiúsculas. O caminho fica como a fonte publica.
     */
    static String enderecoDeclarado(String bruto) {
        String t = Texto.vazioParaNulo(bruto);
        if (t == null || temDadoPessoal(t)) {
            return null;
        }
        t = t.strip();
        String minusculo = t.toLowerCase(java.util.Locale.ROOT);
        if (!minusculo.startsWith("http://") && !minusculo.startsWith("https://")) {
            if (!DOMINIO.matcher(t).find()) {
                return null;
            }
            t = "https://" + t;
        }
        if (t.length() > 2048 || t.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(t);
            String host = uri.getHost();
            if (host == null || !host.contains(".") || uri.getRawUserInfo() != null) {
                return null;
            }
            String depoisDoDominio = t.substring(t.indexOf("://") + 3 + uri.getRawAuthority().length());
            return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                    + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT) + depoisDoDominio;
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    /** Nome da rede pelo domínio do endereço. */
    static String rede(String url) {
        String host = java.net.URI.create(url).getHost().toLowerCase(java.util.Locale.ROOT);
        for (var rede : REDES) {
            for (String dominio : rede.getValue()) {
                if (host.equals(dominio) || host.endsWith("." + dominio)) {
                    return rede.getKey();
                }
            }
        }
        return "Site";
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
        Map<String, String> tituloPorSq = new HashMap<>();
        for (int ano : eleicoes(ctx.hoje().getYear())) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "tse", "consulta_cand_" + ano + ".zip");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            lerBrasil(Coleta.caminho(ctx, arq), l -> {
                if (cargoValido(l)) {
                    String sq = l.get("SQ_CANDIDATO");
                    String cpf = Texto.digitos(l.get("NR_CPF_CANDIDATO"));
                    String titulo = Texto.digitos(l.get("NR_TITULO_ELEITORAL_CANDIDATO"));
                    chaves.put(sq, new Chave(cpf.length() == 11 ? cpf : null,
                            Texto.normalizarNome(l.get("NM_CANDIDATO")), l.get("SG_UF"), Texto.dataBr(l.get("DT_NASCIMENTO")),
                            l.get("DS_CARGO")));
                    if (titulo.length() == 12) {
                        tituloPorSq.put(sq, titulo);
                    }
                }
            });
        }
        PeloTitulo peloTitulo = completarCpfPeloTitulo(chaves, tituloPorSq);
        if (peloTitulo.ligadas() > 0 || peloTitulo.ambiguas() > 0) {
            System.out.println("tse: " + peloTitulo.ligadas() + " candidatura(s) com CPF mascarado ligada(s) a outra eleição pelo"
                    + " título de eleitor; " + peloTitulo.ambiguas() + " com título ambíguo ficam pelo nome civil e nascimento");
        }
        return peloTitulo.chaves();
    }

    /** Chaves com o CPF completado pelo título, e quantas candidaturas ganharam CPF ou ficaram de fora por ambiguidade. */
    record PeloTitulo(Map<String, Chave> chaves, int ligadas, int ambiguas) {
    }

    /**
     * O TSE mascarou o CPF da eleição de 2024, mas manteve o título de eleitor completo. O título leva à candidatura
     * o CPF da mesma pessoa em outra eleição só quando a correspondência é inequívoca: o título aparece com um único
     * CPF, esse CPF com um único título, e a data de nascimento confere. Na dúvida, a candidatura segue sem CPF, e o
     * vínculo usa nome civil e data de nascimento. O título, como o CPF, fica só em memória (ADR-0003).
     */
    static PeloTitulo completarCpfPeloTitulo(Map<String, Chave> chaves, Map<String, String> tituloPorSq) {
        Map<String, Set<String>> cpfsPorTitulo = new HashMap<>();
        Map<String, Set<String>> titulosPorCpf = new HashMap<>();
        Map<String, Set<String>> nascimentosPorCpf = new HashMap<>();
        for (var entrada : chaves.entrySet()) {
            Chave chave = entrada.getValue();
            String titulo = tituloPorSq.get(entrada.getKey());
            if (chave.cpf() == null) {
                continue;
            }
            if (chave.nascimento() != null) {
                nascimentosPorCpf.computeIfAbsent(chave.cpf(), k -> new HashSet<>()).add(chave.nascimento());
            }
            if (titulo != null) {
                cpfsPorTitulo.computeIfAbsent(titulo, k -> new HashSet<>()).add(chave.cpf());
                titulosPorCpf.computeIfAbsent(chave.cpf(), k -> new HashSet<>()).add(titulo);
            }
        }
        Map<String, Chave> completas = new HashMap<>(chaves);
        int ligadas = 0;
        int ambiguas = 0;
        for (var entrada : chaves.entrySet()) {
            Chave chave = entrada.getValue();
            String titulo = tituloPorSq.get(entrada.getKey());
            if (chave.cpf() != null || titulo == null || chave.nascimento() == null || !cpfsPorTitulo.containsKey(titulo)) {
                continue;
            }
            Set<String> cpfs = cpfsPorTitulo.get(titulo);
            String cpf = cpfs.iterator().next();
            if (cpfs.size() != 1 || titulosPorCpf.get(cpf).size() != 1
                    || !nascimentosPorCpf.getOrDefault(cpf, Set.of()).contains(chave.nascimento())) {
                ambiguas++;
                continue;
            }
            completas.put(entrada.getKey(), new Chave(cpf, chave.nomeCivil(), chave.uf(), chave.nascimento(), chave.cargo()));
            ligadas++;
        }
        return new PeloTitulo(completas, ligadas, ambiguas);
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
