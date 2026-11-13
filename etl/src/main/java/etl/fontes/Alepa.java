package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Registros;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Pará (ALEPA, ADR-0024).
 * Coleta os 41 deputados estaduais em exercício na 60ª legislatura (2023–2027),
 * dados de mandato, comissões permanentes e representações, fotografias e links institucionais.
 */
public final class Alepa implements Fonte {

    public static final String FONTE_DEPUTADOS = "alepa-deputados";
    public static final String FONTE_DETALHES = "alepa-deputados-detalhes";
    public static final String FONTE_PORTAL = "alepa-portal";
    public static final String FONTE_VOTACOES = "alepa-votacoes";

    public static final String URL_VOTACOES = "https://www.alepa.pa.gov.br/Legislativo/VotacoesNominais";
    static final int PRIMEIRO_ANO_VOTACOES = 2022;
    private static final Pattern ITEM_DA_LISTA = Pattern.compile(
            "class='pauta-item-title' href='(/Legislativo/DetalhesVotacaoNominal\\?IdProposicao=([0-9]+)&(?:amp;)?IdVotacao=([0-9]+))'");
    private static final Pattern CAMPO_DO_CABECALHO = Pattern.compile("<strong>([^<:]+):</strong>\\s*([^<]*?)\\s*</td>");
    private static final Pattern SECAO_DE_VOTO = Pattern.compile("<div class=\"section-title\">([^<]+)</div>");
    private static final Pattern ITEM_DE_VOTO = Pattern.compile("<div class=\"voto-item\">\\s*([^<]*?)\\s*</div>");
    /** A seção de quem não votou: entra na participação como votação sem voto registrado. */
    static final String NAO_VOTOU = "Não votou";

    public static final String SITE = "https://www.alepa.pa.gov.br";
    public static final String URL_DEPUTADOS = "https://www.alepa.pa.gov.br/Home/Page/Deputados";
    public static final String URL_COMISSOES = "https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes";
    public static final String UF = "PA";

    public static final LocalDate INICIO_LEG_60 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_60 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_60 = 60;

    public static final Pattern KEYS_PATTERN = Pattern.compile(
            "'stateObject':\\s*\\{[^}]*'keys':\\s*\\[([^\\]]+)\\]",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern CARD_PATTERN = Pattern.compile(
            "<div id=[\"']cardView_DXDataCard([0-9]+)[\"'][^>]*>(.*?)</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_CARD_PATTERN = Pattern.compile(
            "<img class=[\"']deputado-image[\"'][^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern INFO_CARD_PATTERN = Pattern.compile(
            "<div class=[\"']card-info[\"']>\\s*<span>(?:Deputado|Deputada)</span>\\s*<span>([^<]+)</span>\\s*<p>([^<]+)</p>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<span>Nome completo:\\s*<b>([^<]+)</b></span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NASCIMENTO_PATTERN = Pattern.compile(
            "<span>Nascimento:\\s*<b>([0-9]{2}\\.[0-9]{2}\\.[0-9]{4})</b></span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTIDO_PERFIL_PATTERN = Pattern.compile(
            "<span>Partido Político:\\s*<b>([^<]+)</b></span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PERFIL_PATTERN = Pattern.compile(
            "<div class=[\"']card-politico-img-wrapper[\"']>.*?<img src=[\"']([^\"']+)[\"']",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);


    public static final Pattern COMISSAO_ITEM_PATTERN = Pattern.compile(
            "<div class=[\"']comissoes-item[\"']>.*?<div class=[\"']comissoes-header[\"']><h1>([^<]+)</h1></div>.*?<div class=[\"']comissoes-content[\"']>(.*?)</div>\\s*</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTICIPANTE_PATTERN = Pattern.compile(
            "<a href=[\"']/Institucional/Deputado/([0-9]+)[\"'][^>]*>\\s*<p[^>]*>([^<]+)</p>\\s*</a>",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "alepa";
    }

    public record DeputadoBruto(String id, String nome, String partido, String fotoUrl) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("alepa: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Institucional/Deputado/" + d.id();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_COMISSOES, "comissoes.html", "text/html", Politica.SEMANAL);

        // Votações nominais: uma lista por mês, sem paginação, e um detalhe por votação. Lista vazia é comum (recesso)
        // e detalhe sem seção de voto não deveria existir: os dois são confirmados quando baixados. O que veio do cache
        // já passou pela confirmação na execução que o baixou, e o CI roda todo dia.
        for (int ano = PRIMEIRO_ANO_VOTACOES; ano <= ctx.hoje().getYear(); ano++) {
            Politica politica = Politica.porAnoSemanal(ano, ctx.hoje());
            int ultimoMes = ano == ctx.hoje().getYear() ? ctx.hoje().getMonthValue() : 12;
            for (int mes = 1; mes <= ultimoMes; mes++) {
                String url = urlDaLista(ano, mes);
                String caminho = arquivoDaLista(ano, mes);
                Coleta.Arquivo lista = Confirmacao.confirmandoVazio(
                        nova -> coleta.obterOpcional(url, caminho, "text/html", nova), politica,
                        arq -> baixadoAgora(ctx, arq) && semTexto(ctx, arq, "pauta-item-title' href"),
                        coleta.obterOpcional(url, caminho, "text/html", politica), Confirmacao.TENTATIVAS, Confirmacao.PAUSA);
                if (lista == null || !Files.exists(Coleta.caminho(ctx, lista))) {
                    continue;
                }
                for (VotacaoListada v : votacoesDaLista(Texto.lerArquivo(Coleta.caminho(ctx, lista)))) {
                    String urlDetalhe = SITE + v.caminho();
                    String arquivo = arquivoDoDetalhe(v.idVotacao());
                    Confirmacao.confirmandoVazio(nova -> coleta.obterOpcional(urlDetalhe, arquivo, "text/html", nova), politica,
                            arq -> baixadoAgora(ctx, arq) && semTexto(ctx, arq, "section-title"),
                            coleta.obterOpcional(urlDetalhe, arquivo, "text/html", politica), Confirmacao.TENTATIVAS, Confirmacao.PAUSA);
                }
            }
        }
    }

    static String urlDaLista(int ano, int mes) {
        return URL_VOTACOES + "?ano=" + ano + "&mes=" + mes;
    }

    private static String arquivoDaLista(int ano, int mes) {
        return String.format(Locale.ROOT, "votacoes/%d-%02d.html", ano, mes);
    }

    private static String arquivoDoDetalhe(String idVotacao) {
        return "votacoes/detalhes/" + idVotacao + ".html";
    }

    /** Arquivo baixado nesta execução: a coleta grava a hora da execução como hora da captura. */
    private static boolean baixadoAgora(Contexto ctx, Coleta.Arquivo arq) {
        return arq != null && ctx.agora().equals(arq.capturadoEm());
    }

    /** Verdadeiro quando o arquivo existe e não traz o trecho esperado. */
    private static boolean semTexto(Contexto ctx, Coleta.Arquivo arq, String trecho) {
        try {
            return arq != null && Files.exists(Coleta.caminho(ctx, arq)) && !Texto.lerArquivo(Coleta.caminho(ctx, arq)).contains(trecho);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Votação listada no mês: o caminho do detalhe e os ids da proposição e da votação. */
    record VotacaoListada(String caminho, String idProposicao, String idVotacao) {
    }

    /** Votações da lista mensal, na ordem da página, sem repetição. */
    static List<VotacaoListada> votacoesDaLista(String html) {
        Map<String, VotacaoListada> porId = new java.util.LinkedHashMap<>();
        Matcher m = ITEM_DA_LISTA.matcher(html);
        while (m.find()) {
            porId.putIfAbsent(m.group(3), new VotacaoListada(m.group(1).replace("&amp;", "&"), m.group(2), m.group(3)));
        }
        return new ArrayList<>(porId.values());
    }

    /** Voto de um parlamentar, como a seção da página o dá, com o partido da lista. */
    record VotoNaVotacao(String nome, String partido, String voto) {
    }

    /** Cabeçalho e votos do detalhe de uma votação nominal. */
    record DetalheDaVotacao(String data, String projeto, String turno, String resultado, List<VotoNaVotacao> votos) {
    }

    /**
     * Detalhe de uma votação: o cabeçalho (projeto, data, turno, resultado) e, por seção (SIM, NÃO, ABSTENÇÃO, NÃO
     * VOTARAM), os pares nome e partido. A seção de quem não votou vira "Não votou".
     */
    static DetalheDaVotacao detalheDaVotacao(String html) {
        Map<String, String> campos = new HashMap<>();
        Matcher c = CAMPO_DO_CABECALHO.matcher(html);
        while (c.find()) {
            String valor = Texto.semEntidades(c.group(2));
            if (valor != null) {
                campos.putIfAbsent(Texto.semEntidades(c.group(1)), valor);
            }
        }
        List<VotoNaVotacao> votos = new ArrayList<>();
        Matcher secoes = SECAO_DE_VOTO.matcher(html);
        List<int[]> limites = new ArrayList<>();
        List<String> nomesDasSecoes = new ArrayList<>();
        while (secoes.find()) {
            limites.add(new int[] {secoes.end(), 0});
            nomesDasSecoes.add(Texto.semEntidades(secoes.group(1)));
        }
        for (int i = 0; i < limites.size(); i++) {
            int fim = i + 1 < limites.size() ? html.lastIndexOf("<div class=\"section-title\">", limites.get(i + 1)[0]) : html.length();
            String voto = rotuloDoVoto(nomesDasSecoes.get(i));
            Matcher itens = ITEM_DE_VOTO.matcher(html.substring(limites.get(i)[0], fim));
            List<String> valores = new ArrayList<>();
            while (itens.find()) {
                valores.add(Texto.semEntidades(itens.group(1)));
            }
            for (int j = 0; j + 1 < valores.size(); j += 2) {
                if (valores.get(j) != null) {
                    votos.add(new VotoNaVotacao(valores.get(j), valores.get(j + 1), voto));
                }
            }
        }
        return new DetalheDaVotacao(Texto.dataBr(campos.get("Data")), campos.get("Projeto"), campos.get("Turno"),
                campos.get("Resultado"), votos);
    }

    /** "SIM" vira "Sim"; "NÃO VOTARAM", "Não votou". */
    static String rotuloDoVoto(String secao) {
        if (secao == null) {
            return null;
        }
        String texto = secao.strip();
        if (texto.equalsIgnoreCase("NÃO VOTARAM") || texto.equalsIgnoreCase("NÃO VOTOU")) {
            return NAO_VOTOU;
        }
        String minusculo = texto.toLowerCase(Locale.forLanguageTag("pt-BR"));
        return minusculo.isEmpty() ? null : Character.toUpperCase(minusculo.charAt(0)) + minusculo.substring(1);
    }

    /** Chave do nome do parlamentar: sem acento e sem pontuação, em maiúsculas. Serve à página de votos e ao perfil. */
    static String chaveDoNome(String nome) {
        return nome == null ? null : Texto.normalizarNome(nome.replaceAll("[^\\p{L}\\s]", " "));
    }

    /**
     * Nome da página de votos que difere do nome do perfil. Cada grafia foi conferida pelo nome completo do perfil, pelo
     * partido e pelo período dos votos, e corresponde a um único deputado da lista da ALEPA.
     */
    static final Map<String, String> GRAFIAS_DAS_VOTACOES = Map.of(
            "AVEILTON SOUZA", "AVEILTON",
            "CARLOS BORDALO", "DEPUTADO BORDALO",
            "RONIE SILVA", "RONIE",
            "TORRINHO TORRES", "TORRINHO");

    /** Chave do nome de quem votou, já com a grafia do perfil quando as duas diferem. */
    static String chaveDoVotante(String nome) {
        String chave = chaveDoNome(nome);
        return chave == null ? null : GRAFIAS_DAS_VOTACOES.getOrDefault(chave, chave);
    }

    /**
     * Participação por ano: cada votação em que o deputado aparece, em qualquer seção, conta como votação nominal; a
     * seção de quem não votou conta como votação sem voto registrado.
     */
    static List<Participacao> participacao(List<Voto> votos, Prov prov) {
        Map<String, int[]> contagem = new TreeMap<>();
        Map<String, Map<String, Integer>> porTipo = new TreeMap<>();
        for (Voto v : votos) {
            String ano = v.data() == null || v.data().length() < 4 ? null : v.data().substring(0, 4);
            if (ano == null) {
                continue;
            }
            int[] n = contagem.computeIfAbsent(ano, k -> new int[2]);
            n[0]++;
            if (!NAO_VOTOU.equals(v.voto())) {
                n[1]++;
            }
            porTipo.computeIfAbsent(ano, k -> new TreeMap<>()).merge(v.voto() == null ? "(vazio na fonte)" : v.voto(), 1, Integer::sum);
        }
        List<Participacao> lista = new ArrayList<>();
        for (var ano : contagem.entrySet()) {
            lista.add(new Participacao(ano.getKey(), ano.getValue()[0], ano.getValue()[1], ano.getValue()[0] - ano.getValue()[1],
                    porTipo.get(ano.getKey()), prov));
        }
        return lista;
    }

    /** Votos de cada deputado em exercício, pelas listas e detalhes em cache, ligados pelo nome do perfil. */
    private Map<String, List<Voto>> votosPorDeputado(Contexto ctx, List<DeputadoBruto> deputados, Map<String, Prov> provDaParticipacao) {
        Map<String, List<String>> idsPorChave = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            idsPorChave.computeIfAbsent(chaveDoNome(formatarNome(d.nome())), k -> new ArrayList<>()).add(d.id());
        }
        Map<String, List<Voto>> porDeputado = new HashMap<>();
        Map<String, Integer> semPerfil = new TreeMap<>();
        int votacoes = 0;
        Coleta.Arquivo ultimaLista = null;
        for (int ano = PRIMEIRO_ANO_VOTACOES; ano <= ctx.hoje().getYear(); ano++) {
            for (int mes = 1; mes <= 12; mes++) {
                Coleta.Arquivo lista = Coleta.metadados(ctx, nome(), arquivoDaLista(ano, mes));
                if (lista == null || lista.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, lista))) {
                    continue;
                }
                ultimaLista = lista;
                try {
                    for (VotacaoListada v : votacoesDaLista(Texto.lerArquivo(Coleta.caminho(ctx, lista)))) {
                        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDoDetalhe(v.idVotacao()));
                        if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                            continue;
                        }
                        DetalheDaVotacao detalhe = detalheDaVotacao(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                        if (detalhe.data() == null || detalhe.votos().isEmpty()) {
                            continue;
                        }
                        votacoes++;
                        Prov prov = Prov.de(FONTE_VOTACOES, arq.url(), arq);
                        String descricao = detalhe.turno() == null ? detalhe.resultado()
                                : detalhe.resultado() == null ? detalhe.turno() : detalhe.turno() + " (" + detalhe.resultado() + ")";
                        Set<String> jaContados = new HashSet<>();
                        for (VotoNaVotacao voto : detalhe.votos()) {
                            List<String> ids = idsPorChave.get(chaveDoVotante(voto.nome()));
                            if (ids == null || ids.size() != 1) {
                                semPerfil.merge(voto.nome(), 1, Integer::sum);
                                continue;
                            }
                            if (!jaContados.add(ids.getFirst())) {
                                continue;
                            }
                            porDeputado.computeIfAbsent(ids.getFirst(), k -> new ArrayList<>()).add(new Voto(v.idVotacao(),
                                    detalhe.data(), "PLEN", detalhe.projeto(), descricao, voto.voto(), voto.partido(), null, null, null, prov));
                        }
                    }
                } catch (java.io.IOException e) {
                    System.err.println("alepa: erro ao ler votações de " + ano + "-" + mes + ": " + e.getMessage());
                }
            }
        }
        if (ultimaLista != null) {
            provDaParticipacao.put("prov", Prov.de(FONTE_VOTACOES, URL_VOTACOES, ultimaLista));
        }
        int total = porDeputado.values().stream().mapToInt(List::size).sum();
        // Deputado em exercício sem nenhum voto costuma ser grafia nova na página de votos, a incluir na tabela acima.
        List<String> semVoto = new ArrayList<>();
        for (DeputadoBruto d : deputados) {
            if (votacoes > 0 && !porDeputado.containsKey(d.id())) {
                semVoto.add(formatarNome(d.nome()));
            }
        }
        System.out.println("alepa: " + votacoes + " votações nominais, " + total + " votos em " + porDeputado.size()
                + " deputados; em exercício sem voto: " + semVoto.size() + " " + semVoto
                + "; nomes sem perfil em exercício: " + semPerfil.size() + " " + semPerfil.keySet());
        return porDeputado;
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("alepa: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("alepa/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("alepa: erro ao criar pasta de blocos", e);
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairTodasComissoes(ctx);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, Prov> provDaParticipacao = new HashMap<>();
        Map<String, List<Voto>> votosPorDeputado = votosPorDeputado(ctx, deputados, provDaParticipacao);
        DateTimeFormatter fmtNasc = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nome = formatarNome(d.nome());
            String partido = d.partido();
            String fotoUrl = d.fotoUrl();
            String nomeCivil = null;
            LocalDate nascimento = null;

            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = Prov.de(FONTE_DETALHES, SITE + "/Institucional/Deputado/" + id, arqPerfil);

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));

                    Matcher mNomeCivil = NOME_CIVIL_PATTERN.matcher(htmlPerfil);
                    if (mNomeCivil.find()) {
                        nomeCivil = formatarNome(decodificarHtml(mNomeCivil.group(1).trim()));
                    }

                    Matcher mPart = PARTIDO_PERFIL_PATTERN.matcher(htmlPerfil);
                    if (mPart.find()) {
                        String p = mPart.group(1).trim();
                        if (!p.isBlank()) {
                            partido = p;
                        }
                    }

                    Matcher mFoto = FOTO_PERFIL_PATTERN.matcher(htmlPerfil);
                    if (mFoto.find()) {
                        String f = normalizarUrlFoto(mFoto.group(1).trim());
                        if (!f.isBlank()) {
                            fotoUrl = f;
                        }
                    }

                    Matcher mNasc = NASCIMENTO_PATTERN.matcher(htmlPerfil);
                    if (mNasc.find()) {
                        try {
                            nascimento = LocalDate.parse(mNasc.group(1).trim(), fmtNasc);
                        } catch (Exception ignored) {
                        }
                    }

                } catch (Exception e) {
                    System.err.println("alepa: erro ao ler perfil de " + id + ": " + e.getMessage());
                }
            }

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALEPA", SITE + "/Institucional/Deputado/" + id, provPerfil)
            );

            Pessoa pessoa = new Pessoa("alepa", id, nome, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<Mandato> mandatos = List.of(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_60, FIM_LEG_60, "Em exercício", "Titular", LEGISLATURA_60, 2022,
                    "Deputado Estadual na 60ª Legislatura (2023–2027)", provDeputados));

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(id, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.createArrayNode());
            List<Voto> votos = votosPorDeputado.getOrDefault(id, List.of());
            blocosNode.set("votos", Json.SAIDA.valueToTree(votos));
            blocosNode.set("participacao_votacoes", Json.SAIDA.valueToTree(
                    votos.isEmpty() ? List.of() : participacao(votos, provDaParticipacao.get("prov"))));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("alepa/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("alepa: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alepa", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("alepa: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        List<DeputadoBruto> lista = new ArrayList<>();

        Matcher mKeys = KEYS_PATTERN.matcher(html);
        if (!mKeys.find()) {
            return List.of();
        }
        String keysBruto = mKeys.group(1);
        String[] keys = keysBruto.replace("'", "").replace("\"", "").split(",");
        List<String> ids = new ArrayList<>();
        for (String k : keys) {
            String kTrim = k.trim();
            if (!kTrim.isBlank()) {
                ids.add(kTrim);
            }
        }

        Matcher mCard = CARD_PATTERN.matcher(html);
        int idx = 0;
        while (mCard.find() && idx < ids.size()) {
            String cardHtml = mCard.group(2);
            String id = ids.get(idx);

            String nome = "";
            String partido = "";
            Matcher mInfo = INFO_CARD_PATTERN.matcher(cardHtml);
            if (mInfo.find()) {
                nome = decodificarHtml(mInfo.group(1).trim());
                partido = mInfo.group(2).trim();
            }

            String fotoUrl = "";
            Matcher mFoto = FOTO_CARD_PATTERN.matcher(cardHtml);
            if (mFoto.find()) {
                fotoUrl = normalizarUrlFoto(mFoto.group(1).trim());
            }

            if (!nome.isBlank()) {
                lista.add(new DeputadoBruto(id, nome, partido, fotoUrl));
            }
            idx++;
        }

        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairTodasComissoes(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alepa", "comissoes.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return Map.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            Prov prov = Prov.de(FONTE_DETALHES, URL_COMISSOES, arq);
            return extrairComissoesDoHtml(html, prov);
        } catch (Exception e) {
            System.err.println("alepa: erro ao extrair comissoes: " + e.getMessage());
            return Map.of();
        }
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(String html, Prov prov) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        String[] blocos = html.split("<div[^>]+class=[\"']comissoes-item[\"'][^>]*>");
        Pattern pHeader = Pattern.compile("<div[^>]+class=[\"']comissoes-header[\"'][^>]*>\\s*<h1>([^<]+)</h1>", Pattern.CASE_INSENSITIVE);

        for (int i = 1; i < blocos.length; i++) {
            String bloco = blocos[i];
            Matcher mHeader = pHeader.matcher(bloco);
            String titulo = "Comissão";
            if (mHeader.find()) {
                titulo = formatarTituloComissao(decodificarHtml(mHeader.group(1).trim()));
            }

            // Divide em TITULARES e SUPLENTES se houver divisão
            String[] partes = bloco.split("SUPLENTES");
            String titularesHtml = partes[0];
            String suplentesHtml = partes.length > 1 ? partes[1] : "";

            // Titulares
            Matcher mTit = PARTICIPANTE_PATTERN.matcher(titularesHtml);
            while (mTit.find()) {
                String id = mTit.group(1);
                String texto = mTit.group(2);

                String papel = "Titular";
                if (texto.toUpperCase(Locale.ROOT).contains("(PRESIDENTE)")) {
                    papel = "Presidente";
                } else if (texto.toUpperCase(Locale.ROOT).contains("(VICE-PRESIDENTE)")) {
                    papel = "Vice-Presidente";
                }

                resultado.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", titulo, null, papel, INICIO_LEG_60, null, prov));
            }

            // Suplentes
            if (!suplentesHtml.isBlank()) {
                Matcher mSup = PARTICIPANTE_PATTERN.matcher(suplentesHtml);
                while (mSup.find()) {
                    String id = mSup.group(1);
                    resultado.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new OrgaoOuFrente("comissao", titulo, null, "Suplente", INICIO_LEG_60, null, prov));
                }
            }
        }

        return resultado;
    }

    public static String formatarTituloComissao(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return "Comissão";
        }
        String limpo = bruto.replaceAll("\\s+", " ").trim();
        // Capitaliza de forma agradável preservando siglas
        return limpo;
    }

    public static String normalizarUrlFoto(String urlBruta) {
        if (urlBruta == null || urlBruta.isBlank()) {
            return null;
        }
        String corrigida = urlBruta.replace("\\", "/").trim();
        if (corrigida.startsWith("//")) {
            corrigida = "https:" + corrigida;
        } else if (corrigida.startsWith("/")) {
            corrigida = SITE + corrigida;
        }
        return corrigida.replace(" ", "%20");
    }

    public static String decodificarHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&#227;", "ã")
                .replace("&#245;", "õ")
                .replace("&#225;", "á")
                .replace("&#233;", "é")
                .replace("&#237;", "í")
                .replace("&#243;", "ó")
                .replace("&#250;", "ú")
                .replace("&#224;", "à")
                .replace("&#226;", "â")
                .replace("&#234;", "ê")
                .replace("&#244;", "ô")
                .replace("&#231;", "ç")
                .replace("&#193;", "Á")
                .replace("&#201;", "É")
                .replace("&#205;", "Í")
                .replace("&#211;", "Ó")
                .replace("&#218;", "Ú")
                .replace("&#192;", "À")
                .replace("&#194;", "Â")
                .replace("&#202;", "Ê")
                .replace("&#212;", "Ô")
                .replace("&#195;", "Ã")
                .replace("&#213;", "Õ")
                .replace("&#199;", "Ç")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String formatarNome(String nomeBruto) {
        if (nomeBruto == null || nomeBruto.isBlank()) {
            return "";
        }
        String decodificado = decodificarHtml(nomeBruto);
        String[] partes = decodificado.replaceAll("\\s+", " ").split(" ");
        List<String> formatadas = new ArrayList<>();
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            String pLower = p.toLowerCase(Locale.ROOT);
            if (i > 0 && PREPOSICOES.contains(pLower)) {
                formatadas.add(pLower);
            } else if (p.length() > 0) {
                formatadas.add(p.substring(0, 1).toUpperCase(Locale.ROOT) + p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return String.join(" ", formatadas);
    }
}
