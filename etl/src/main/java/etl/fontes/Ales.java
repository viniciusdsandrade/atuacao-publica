package etl.fontes;

import java.math.BigDecimal;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Registros;
import etl.Registros.Despesa;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Assembleia Legislativa do Estado do Espírito Santo (ALES, ADR-0023).
 * Coleta os 30 deputados estaduais em exercício na 20ª legislatura (2023–2027),
 * dados de mandato, comissões permanentes, fotografias oficiais em alta resolução e links institucionais.
 */
public final class Ales implements Fonte {

    public static final String FONTE_DEPUTADOS = "ales-deputados";
    public static final String FONTE_DETALHES = "ales-deputados-detalhes";
    public static final String FONTE_PORTAL = "ales-portal";
    public static final String FONTE_PROPOSICOES = "ales-proposicoes";
    public static final String FONTE_COTAS = "ales-cotas";

    public static final String URL_COTAS = "https://www.al.es.gov.br/Transparencia/CotasParlamentares";
    static final String URL_TABELA_DE_COTAS = "https://www.al.es.gov.br/Transparencia/Api/CotasParlamentaresNovoTable/";
    static final int PRIMEIRO_ANO_COTAS = 2022;
    private static final String SEM_DESPESA = "Nenhuma despesa encontrada";
    private static final Pattern OPCAO_DE_GABINETE = Pattern.compile("<option[^>]*value=\"([0-9]+)\"[^>]*>([^<]*)");
    private static final Pattern PRODUTO_DA_COTA = Pattern.compile(
            "Produto:\\s*(.+?)\\s+Quantidade:\\s*(.+?)\\s+Valor Solicitado:\\s*(.+?)(?=\\s+Produto:|\\s+Total:|$)");
    private static final Pattern TOTAL_DA_COTA = Pattern.compile("Total:\\s*R\\$\\s*([0-9.]+,[0-9]{2})");
    private static final Pattern NOME_CIVIL_DA_COTA = Pattern.compile("Nome Civil:\\s*</label>\\s*<span>\\s*([^<]+?)\\s*</span>");

    public static final String API_PROPOSICOES = "https://www3.al.es.gov.br/api/publico/proposicao/";
    static final int PRIMEIRO_ANO_PROPOSICOES = 2022;
    /** Tipos de autoria parlamentar. A consulta sem tipo, em página grande, quebra no servidor. */
    static final List<String> SIGLAS_DE_PROPOSICAO = List.of("PL", "PLC", "PEC", "PR", "PDL", "IND", "REQ");
    /**
     * Cada item traz a tramitação inteira, e o tempo do servidor cresce com o número de itens: 200 levam cerca de 20 s.
     * Página pequena limita o refazer item a item quando um item com defeito derruba a página.
     */
    static final int TAMANHO_DA_PAGINA = 20;

    public static final String SITE = "https://www.al.es.gov.br";
    public static final String URL_DEPUTADOS = "https://www.al.es.gov.br/Deputado/Lista";
    public static final String URL_COMISSOES = "https://www.al.es.gov.br/Comissao/ListaComissao?tipoComissao=permanente";
    public static final String UF = "ES";

    public static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 2, 1);
    public static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);
    public static final int LEGISLATURA_20 = 20;

    public static final Pattern SELECT_DEPUTADOS_PATTERN = Pattern.compile(
            "<select[^>]*id=[\"']ddlistDeputado[\"'][^>]*>(.*?)</select>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern OPTION_DEPUTADO_PATTERN = Pattern.compile(
            "<option value=[\"']([0-9]+)[\"']>([^<]+)</option>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern FOTO_PATTERN = Pattern.compile(
            "<div id=[\"']divFotoDeputado[\"'][^>]*>.*?<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern PARTIDO_PATTERN = Pattern.compile(
            "<label><b>Partido:</b></label>\\s*<span>\\s*([^<]+)</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NOME_CIVIL_PATTERN = Pattern.compile(
            "<label>Nome Civil:\\s*</label>\\s*<span>\\s*([^<]+)</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern NASCIMENTO_PATTERN = Pattern.compile(
            "<label>Data de Nascimento:\\s*</label>\\s*<span>\\s*([0-9]{2}/[0-9]{2}/[0-9]{4})</span>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern TITULO_COMISSAO_PATTERN = Pattern.compile(
            "<div class=[\"']fonte-titulo-deputado[\"']>\\s*<span>([^<]+)</span>\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern PRESIDENTE_PATTERN = Pattern.compile(
            "<div>\\s*<label>Presidente:\\s*</label>\\s*<span>\\s*([^<]+)</span>\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern VICE_PATTERN = Pattern.compile(
            "<div>\\s*<label>Vice-Presidente:\\s*</label>\\s*<span>\\s*([^<]+)</span>\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern MEMBROS_EFETIVOS_PATTERN = Pattern.compile(
            "<div id=[\"']divTextoMembrosEfetivos[\"'][^>]*>.*?<div class=[\"']texto-conteudo-interno[\"'][^>]*>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final Pattern MEMBROS_SUPLENTES_PATTERN = Pattern.compile(
            "<div id=[\"']divTextoMembrosSuplentes[\"'][^>]*>.*?<div class=[\"']texto-conteudo-interno[\"'][^>]*>(.*?)</div>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final List<String> COMISSOES_SLUGS = List.of(
            "Agricultura",
            "AssistenciaSocial",
            "CienciaeTecnologia",
            "ConstituicaoeJustica",
            "Cooperativismo",
            "Cultura",
            "DefesadoConsumidor",
            "DireitosHumanos",
            "Educacao",
            "Financas",
            "Infraestrutura",
            "PoliticasobreDrogas",
            "ProtecaoaCriancaeaoAdolescente",
            "MeioAmbiente",
            "DosAnimais",
            "Saude",
            "Seguranca",
            "Turismo"
    );

    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "del", "e");

    @Override
    public String nome() {
        return "ales";
    }

    public record DeputadoBruto(String id, String nome, String slug) {
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("ales: coleta iniciada");
        coleta.obter(URL_DEPUTADOS, "deputados.html", "text/html", Politica.SEMANAL);

        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        for (DeputadoBruto d : deputados) {
            String urlPerfil = SITE + "/Deputado/" + d.slug();
            coleta.obterOuVazio(urlPerfil, "perfis/" + d.id() + ".html", "text/html", Politica.SEMANAL);
        }

        coleta.obterOuVazio(URL_COMISSOES, "comissoes.html", "text/html", Politica.SEMANAL);
        for (String comSlug : COMISSOES_SLUGS) {
            String urlCom = SITE + "/Comissao/" + comSlug;
            coleta.obterOuVazio(urlCom, "comissoes/" + comSlug + ".html", "text/html", Politica.SEMANAL);
        }

        // Proposições: API NoPaper, por tipo e ano.
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            Politica politica = Politica.porAnoSemanal(ano, ctx.hoje());
            for (String sigla : SIGLAS_DE_PROPOSICAO) {
                respostasDoAno(sigla, ano, (url, caminho) -> {
                    Coleta.Arquivo arq = coleta.obterOpcional(url, caminho, "application/json", politica);
                    return arq == null || !Files.exists(Coleta.caminho(ctx, arq)) ? null
                            : new Resposta(Json.ler(Coleta.caminho(ctx, arq)), arq);
                });
            }
        }

        // Cotas parlamentares: uma página por gabinete e mês, com os gabinetes do seletor da página de cotas.
        Coleta.Arquivo paginaDeCotas = coleta.obterOpcional(URL_COTAS, "cotas/pagina.html", "text/html", Politica.SEMANAL);
        if (paginaDeCotas != null && Files.exists(Coleta.caminho(ctx, paginaDeCotas))) {
            List<String> ids = new ArrayList<>();
            gabinetesDaPagina(Texto.lerArquivo(Coleta.caminho(ctx, paginaDeCotas))).forEach(g -> ids.add(g.id()));
            for (String falha : Camara.paraCadaDeputado(ids, gabinete -> coletarCotas(ctx, coleta, gabinete))) {
                coleta.falhaParcial("cotas", falha);
            }
        }
    }

    /** Gabinete da lista de cotas: o id do setor não é o id do deputado. */
    record Gabinete(String id, String nome) {
    }

    /** Gabinetes do seletor "cboSetor" da página de cotas, sem a opção "Selecione". */
    static List<Gabinete> gabinetesDaPagina(String html) {
        int inicio = html.indexOf("id=\"cboSetor\"");
        int fim = inicio < 0 ? -1 : html.indexOf("</select>", inicio);
        if (inicio < 0 || fim < 0) {
            return List.of();
        }
        List<Gabinete> gabinetes = new ArrayList<>();
        Matcher m = OPCAO_DE_GABINETE.matcher(html.substring(inicio, fim));
        while (m.find()) {
            String nome = Texto.semEntidades(m.group(2));
            if (!m.group(1).equals("0") && nome != null) {
                gabinetes.add(new Gabinete(m.group(1), nome));
            }
        }
        return gabinetes;
    }

    static String urlDaCota(String gabinete, int ano, int mes) {
        return URL_TABELA_DE_COTAS + mes + "/" + mes + "/" + ano + "/" + gabinete + "/false";
    }

    private static String arquivoDaCota(String gabinete, int ano, int mes) {
        return String.format(Locale.ROOT, "cotas/%s-%d-%02d.html", gabinete, ano, mes);
    }

    private static boolean semDespesa(Contexto ctx, Coleta.Arquivo arq) {
        try {
            return arq != null && Files.exists(Coleta.caminho(ctx, arq)) && Texto.lerArquivo(Coleta.caminho(ctx, arq)).contains(SEM_DESPESA);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Cota de um gabinete num mês: uma página por gabinete e mês. A página responde "Nenhuma despesa encontrada" para o
     * mês sem registro, e esse texto também pode vir por engano, por isso o ETL confirma antes de aceitar.
     */
    private static void coletarCotas(Contexto ctx, Coleta coleta, String gabinete) throws Exception {
        for (int ano = PRIMEIRO_ANO_COTAS; ano <= ctx.hoje().getYear(); ano++) {
            Politica politica = Politica.porAnoSemanal(ano, ctx.hoje());
            int ultimoMes = ano == ctx.hoje().getYear() ? ctx.hoje().getMonthValue() : 12;
            for (int mes = 1; mes <= ultimoMes; mes++) {
                String url = urlDaCota(gabinete, ano, mes);
                String caminho = arquivoDaCota(gabinete, ano, mes);
                Coleta.Arquivo primeira = coleta.obterOpcional(url, caminho, "text/html", politica);
                Confirmacao.confirmandoVazio(nova -> coleta.obterOpcional(url, caminho, "text/html", nova), politica,
                        arq -> semDespesa(ctx, arq), primeira, Confirmacao.TENTATIVAS, Confirmacao.PAUSA);
            }
        }
    }

    /** Produto da cota e o valor que o gabinete solicitou no mês. */
    record ProdutoDaCota(String produto, BigDecimal valor) {
    }

    /** Cota do mês: o nome civil do deputado do gabinete, os produtos com valor e o total que a página declara. */
    record CotaDoMes(String nomeCivil, List<ProdutoDaCota> produtos, BigDecimal totalDeclarado) {
    }

    /**
     * Produtos da cota de um mês, com valor diferente de zero. A página lista todos os produtos, também os de valor
     * zero. "Devolução de diária" traz o valor na coluna da quantidade e "-" na do valor: vale o campo com "R$".
     * O valor é o solicitado pelo gabinete. Telefone e e-mail do deputado, que a página traz, não são lidos.
     */
    static CotaDoMes cotaDoMes(String html) {
        String semEstilo = html.replaceAll("(?s)<(style|script)[^>]*>.*?</\\1>", "");
        Matcher civil = NOME_CIVIL_DA_COTA.matcher(semEstilo);
        String nomeCivil = civil.find() ? Texto.semEntidades(civil.group(1)) : null;
        String texto = Texto.semEntidades(semEstilo.replaceAll("(?s)<[^>]+>", " "));
        if (texto == null || texto.contains(SEM_DESPESA)) {
            return new CotaDoMes(nomeCivil, List.of(), null);
        }
        List<ProdutoDaCota> produtos = new ArrayList<>();
        Matcher m = PRODUTO_DA_COTA.matcher(texto);
        while (m.find()) {
            String campo = m.group(3).contains("R$") ? m.group(3) : m.group(2).contains("R$") ? m.group(2) : null;
            BigDecimal valor = campo == null ? null : Texto.valor(campo.replace("R$", "").strip());
            if (valor != null && valor.signum() != 0) {
                produtos.add(new ProdutoDaCota(m.group(1).strip(), valor));
            }
        }
        Matcher total = TOTAL_DA_COTA.matcher(texto);
        return new CotaDoMes(nomeCivil, produtos, total.find() ? Texto.valor(total.group(1)) : null);
    }

    /** Chave do nome civil: o perfil o traz com entidades HTML ("Alc&#226;ntaro") e a cota já decodificado. */
    static String chaveDoNomeCivil(String nome) {
        String decodificado = nome == null ? null : Texto.semEntidades(nome);
        return decodificado == null ? null : Texto.normalizarNome(decodificado);
    }

    /** Nome civil de cada deputado, sem acento e em maiúsculas, pelo perfil em cache; repetido entre dois deputados fica fora. */
    private Map<String, String> idPorNomeCivil(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, List<String>> ids = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            Coleta.Arquivo perfil = Coleta.metadados(ctx, nome(), "perfis/" + d.id() + ".html");
            if (perfil == null || !Files.exists(Coleta.caminho(ctx, perfil))) {
                continue;
            }
            try {
                Matcher civil = NOME_CIVIL_PATTERN.matcher(Texto.lerArquivo(Coleta.caminho(ctx, perfil)));
                if (civil.find()) {
                    String chave = chaveDoNomeCivil(civil.group(1));
                    if (chave != null) {
                        ids.computeIfAbsent(chave, k -> new ArrayList<>()).add(d.id());
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("ales: aviso ao ler perfil " + d.id() + ": " + e.getMessage());
            }
        }
        Map<String, String> unicos = new HashMap<>();
        ids.forEach((nome, lista) -> {
            if (lista.size() == 1) {
                unicos.put(nome, lista.getFirst());
            }
        });
        return unicos;
    }

    /**
     * Cotas de cada deputado, pelas páginas em cache. O gabinete liga ao deputado pelo nome civil que a própria página
     * traz, igual ao do perfil, e não pelo nome do gabinete. Um gabinete que muda de ocupante fica certo mês a mês.
     */
    private Map<String, List<Despesa>> cotasPorDeputado(Contexto ctx, List<DeputadoBruto> deputados) {
        Map<String, List<Despesa>> porDeputado = new HashMap<>();
        Coleta.Arquivo pagina = Coleta.metadados(ctx, nome(), "cotas/pagina.html");
        if (pagina == null || !Files.exists(Coleta.caminho(ctx, pagina))) {
            return porDeputado;
        }
        List<Gabinete> gabinetes;
        try {
            gabinetes = gabinetesDaPagina(Texto.lerArquivo(Coleta.caminho(ctx, pagina)));
        } catch (java.io.IOException e) {
            return porDeputado;
        }
        Map<String, String> idPorNomeCivil = idPorNomeCivil(ctx, deputados);
        int lancamentos = 0;
        int semDeputado = 0;
        int totaisDiferentes = 0;
        for (Gabinete g : gabinetes) {
            for (int ano = PRIMEIRO_ANO_COTAS; ano <= ctx.hoje().getYear(); ano++) {
                for (int mes = 1; mes <= 12; mes++) {
                    Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaCota(g.id(), ano, mes));
                    if (arq == null || arq.bytes() == 0 || !Files.exists(Coleta.caminho(ctx, arq))) {
                        continue;
                    }
                    try {
                        CotaDoMes cota = cotaDoMes(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                        if (cota.nomeCivil() == null || cota.produtos().isEmpty()) {
                            continue;
                        }
                        String deputado = idPorNomeCivil.get(Texto.normalizarNome(cota.nomeCivil()));
                        if (deputado == null) {
                            semDeputado++;
                            continue;
                        }
                        BigDecimal soma = BigDecimal.ZERO;
                        for (ProdutoDaCota produto : cota.produtos()) {
                            soma = soma.add(produto.valor());
                            porDeputado.computeIfAbsent(deputado, k -> new ArrayList<>()).add(new Despesa(null, ano, mes,
                                    produto.produto(), null, null, produto.valor(), null, null, Prov.de(FONTE_COTAS, arq.url(), arq)));
                            lancamentos++;
                        }
                        if (cota.totalDeclarado() != null && soma.compareTo(cota.totalDeclarado()) != 0) {
                            totaisDiferentes++;
                        }
                    } catch (java.io.IOException e) {
                        System.err.println("ales: erro ao ler cota " + g.id() + " " + ano + "-" + mes + ": " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("ales: " + lancamentos + " lançamentos de cota por produto e mês em " + porDeputado.size() + " deputados; "
                + semDeputado + " meses de gabinete sem deputado com esse nome civil; " + totaisDiferentes
                + " meses com total diferente da soma dos produtos");
        return porDeputado;
    }

    /** Resposta da API em cache, com o arquivo que dá a proveniência. */
    record Resposta(JsonNode corpo, Coleta.Arquivo arquivo) {
    }

    /** Busca de uma consulta: na coleta, baixa ou usa o cache; na normalização, só lê o cache. Sem cópia, null. */
    interface Leitor {
        Resposta ler(String url, String caminho) throws Exception;
    }

    /**
     * Respostas válidas de um tipo no ano, página a página. Quando um item tem campo nulo, a API responde 200 com
     * {@code {"erro": true}} para a página inteira: essa página é refeita item a item, e só o item com defeito fica
     * fora. O número de páginas sai do total da primeira resposta válida.
     */
    static List<Resposta> respostasDoAno(String sigla, int ano, Leitor leitor) throws Exception {
        List<Resposta> validas = new ArrayList<>();
        int total = -1;
        for (int pagina = 1; pagina <= (total < 0 ? 1 : paginas(total)); pagina++) {
            Resposta resposta = leitor.ler(urlDaPagina(sigla, ano, pagina, TAMANHO_DA_PAGINA), caminhoDaPagina(sigla, ano, pagina));
            if (resposta == null) {
                continue;
            }
            if (valida(resposta)) {
                validas.add(resposta);
                total = total < 0 ? resposta.corpo().path("total").asInt(0) : total;
                continue;
            }
            int primeiro = (pagina - 1) * TAMANHO_DA_PAGINA + 1;
            for (int item = primeiro; item < primeiro + TAMANHO_DA_PAGINA && (total < 0 || item <= total); item++) {
                Resposta doItem = leitor.ler(urlDaPagina(sigla, ano, item, 1), caminhoDoItem(sigla, ano, item));
                if (doItem != null && valida(doItem)) {
                    validas.add(doItem);
                    total = total < 0 ? doItem.corpo().path("total").asInt(0) : total;
                }
            }
        }
        return validas;
    }

    static boolean valida(Resposta resposta) {
        return resposta.corpo().path("Data").isArray() && !resposta.corpo().path("erro").asBoolean(false);
    }

    static String urlDaPagina(String sigla, int ano, int pagina, int quantidade) {
        return API_PROPOSICOES + "?pag=" + pagina + "&qtd=" + quantidade + "&ano=" + ano + "&sigla=" + sigla;
    }

    private static String caminhoDaPagina(String sigla, int ano, int pagina) {
        return "proposicoes/" + sigla + "-" + ano + "-" + pagina + ".json";
    }

    private static String caminhoDoItem(String sigla, int ano, int item) {
        return "proposicoes/" + sigla + "-" + ano + "-item-" + item + ".json";
    }

    static int paginas(int total) {
        return (total + TAMANHO_DA_PAGINA - 1) / TAMANHO_DA_PAGINA;
    }

    /** Chave de vínculo da autoria: nome sem acento e sem pontuação, em maiúsculas. Serve ao autor e ao perfil. */
    static String chaveDeAutoria(String nome) {
        String chave = Texto.normalizarNome(nome.replaceAll("[^\\p{L}\\s]", " "));
        return GRAFIAS_DE_AUTORIA.getOrDefault(chave, chave);
    }

    /**
     * Autoria grafada de outro jeito que o perfil, com um só deputado possível na lista da ALES: um erro de digitação e
     * um nome completo, registrado no gabinete do próprio deputado.
     */
    private static final Map<String, String> GRAFIAS_DE_AUTORIA = Map.of(
            "ENGENHERIO JOSE ESMERALDO", "ENGENHEIRO JOSE ESMERALDO",
            "FABIO DUARTE DE ALMEIDA", "FABIO DUARTE");

    /** Proposições de cada deputado em exercício, pelas páginas em cache. */
    private static Map<String, List<Proposicao>> proposicoesPorId(Contexto ctx, Map<String, String> idPorChave) {
        Map<String, List<Proposicao>> porId = new HashMap<>();
        int total = 0;
        Leitor doCache = (url, caminho) -> {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "ales", caminho);
            // Cópia de outra consulta no mesmo caminho (outro tamanho de página) não vale.
            if (arq == null || arq.bytes() == 0 || !url.equals(arq.url()) || !Files.exists(Coleta.caminho(ctx, arq))) {
                return null;
            }
            return new Resposta(Json.ler(Coleta.caminho(ctx, arq)), arq);
        };
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            for (String sigla : SIGLAS_DE_PROPOSICAO) {
                // Página de outra coleta pode repetir item: o id decide.
                Set<String> vistos = new HashSet<>();
                List<Resposta> respostas;
                try {
                    respostas = respostasDoAno(sigla, ano, doCache);
                } catch (Exception e) {
                    throw new IllegalStateException("ales: falha ao ler proposições " + sigla + " de " + ano, e);
                }
                for (Resposta r : respostas) {
                    Prov prov = Prov.de(FONTE_PROPOSICOES, r.arquivo().url(), r.arquivo());
                    for (var e : proposicoesDaPagina(r.corpo(), idPorChave, prov).entrySet()) {
                        for (Proposicao p : e.getValue()) {
                            if (vistos.add(e.getKey() + "|" + p.idFonte())) {
                                porId.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(p);
                                total++;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("ales: " + total + " autorias de proposições em " + porId.size() + " deputados");
        return porId;
    }

    /**
     * Proposições de uma página da API, por id do deputado. O autor vem pelo nome ({@code AutorRequerenteDados}); só
     * entra o nome igual ao de um deputado em exercício, sem acento e sem pontuação. O gabinete não serve de vínculo:
     * há proposição cujo autor não é o titular do gabinete.
     */
    static Map<String, List<Proposicao>> proposicoesDaPagina(JsonNode resposta, Map<String, String> idPorChave, Prov prov) {
        Map<String, List<Proposicao>> porId = new HashMap<>();
        for (JsonNode p : resposta.path("Data")) {
            String id = Json.texto(p, "id");
            String sigla = Json.texto(p, "sigla");
            Integer numero = Json.inteiro(p, "numero");
            Integer ano = Json.inteiro(p, "ano");
            if (id == null || sigla == null || numero == null || ano == null) {
                continue;
            }
            List<String> autores = new ArrayList<>();
            autores.add(Json.texto(p.path("AutorRequerenteDados"), "nomeRazao"));
            for (JsonNode coautor : p.path("CoAutores")) {
                autores.add(coautor.isObject() ? Json.texto(coautor, "nomeRazao") : coautor.isString() ? coautor.stringValue() : null);
            }
            String ementa = Json.texto(p, "assunto");
            String arquivo = Json.texto(p, "arquivo");
            Set<String> jaContados = new HashSet<>();
            for (int i = 0; i < autores.size(); i++) {
                String deputado = autores.get(i) == null ? null : idPorChave.get(chaveDeAutoria(autores.get(i)));
                if (deputado == null || !jaContados.add(deputado)) {
                    continue;
                }
                porId.computeIfAbsent(deputado, k -> new ArrayList<>()).add(new Proposicao(id, Json.texto(p, "tipo"), numero, ano,
                        sigla + " " + numero + "/" + ano, ementa == null ? null : Guarda.semCpf(ementa.replaceAll("\\s+", " ")),
                        Json.texto(p, "situacao"), null, Texto.dataBr(Json.texto(p, "data")), i + 1, null,
                        arquivo != null && arquivo.startsWith("https://") ? arquivo : null, prov));
            }
        }
        return porId;
    }

    @Override
    public void normalize(Contexto ctx) {
        List<DeputadoBruto> deputados = extrairDeputados(ctx);
        if (deputados.isEmpty()) {
            System.out.println("ales: nenhum deputado para normalizar");
            return;
        }

        Coleta.Arquivo arqDeputados = Coleta.metadados(ctx, nome(), "deputados.html");
        Prov provDeputados = Prov.de(FONTE_DEPUTADOS, URL_DEPUTADOS, arqDeputados);

        Path pastaBlocos = ctx.normalizado().resolve("ales/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("ales: erro ao criar pasta de blocos", e);
        }

        Map<String, String> idPorNomeNorm = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            idPorNomeNorm.put(Texto.normalizarNome(d.nome()), d.id());
            idPorNomeNorm.put(Texto.normalizarNome(formatarNome(d.nome())), d.id());
        }

        Map<String, List<OrgaoOuFrente>> comissoesPorDeputado = extrairComissoes(ctx, idPorNomeNorm);
        Map<String, String> idPorChaveDeAutoria = new HashMap<>();
        for (DeputadoBruto d : deputados) {
            idPorChaveDeAutoria.put(chaveDeAutoria(formatarNome(d.nome())), d.id());
        }
        Map<String, List<Proposicao>> proposicoesPorDeputado = proposicoesPorId(ctx, idPorChaveDeAutoria);
        Map<String, List<Despesa>> cotasPorDeputado = cotasPorDeputado(ctx, deputados);

        List<Pessoa> pessoas = new ArrayList<>();

        for (DeputadoBruto d : deputados) {
            String id = d.id();
            String nomeFormatado = formatarNome(d.nome());
            String slug = d.slug();

            String urlPerfil = SITE + "/Deputado/" + slug;
            Coleta.Arquivo arqPerfil = Coleta.metadados(ctx, nome(), "perfis/" + id + ".html");
            Prov provPerfil = arqPerfil != null ? Prov.de(FONTE_DETALHES, urlPerfil, arqPerfil) : provDeputados;

            String fotoUrl = null;
            String nomeCivil = null;
            String partido = null;

            if (arqPerfil != null && Files.exists(Coleta.caminho(ctx, arqPerfil))) {
                try {
                    String htmlPerfil = Texto.lerArquivo(Coleta.caminho(ctx, arqPerfil));
                    Matcher mFoto = FOTO_PATTERN.matcher(htmlPerfil);
                    if (mFoto.find()) {
                        String f = mFoto.group(1).trim();
                        fotoUrl = f.startsWith("http") ? f : SITE + (f.startsWith("/") ? f : "/" + f);
                    }
                    Matcher mCivil = NOME_CIVIL_PATTERN.matcher(htmlPerfil);
                    if (mCivil.find()) {
                        nomeCivil = formatarNome(mCivil.group(1).trim());
                    }
                    Matcher mPartido = PARTIDO_PATTERN.matcher(htmlPerfil);
                    if (mPartido.find()) {
                        partido = mPartido.group(1).trim().toUpperCase(Locale.ROOT);
                    }
                } catch (Exception e) {
                    System.err.println("ales: aviso ao ler perfil " + id + ": " + e.getMessage());
                }
            }

            if (partido == null || partido.isBlank()) {
                partido = "S/PARTIDO";
            }

            List<Mandato> mandatos = new ArrayList<>();
            mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF,
                    INICIO_LEG_20, FIM_LEG_20, "Em exercício", "Titular", LEGISLATURA_20, 2022,
                    "Deputado Estadual na 20ª Legislatura (2023–2027)", provDeputados));

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALES", urlPerfil, provPerfil));

            Pessoa pessoa = new Pessoa(
                    "ales", id, nomeFormatado, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, "Em exercício", true, links);
            pessoas.add(pessoa);

            List<OrgaoOuFrente> orgaos = comissoesPorDeputado.getOrDefault(id, List.of());

            var blocosNode = Json.SAIDA.createObjectNode();
            blocosNode.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocosNode.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocosNode.set("despesas", Json.SAIDA.valueToTree(cotasPorDeputado.getOrDefault(id, List.of())));
            blocosNode.set("proposicoes", Json.SAIDA.valueToTree(proposicoesPorDeputado.getOrDefault(id, List.of())));
            blocosNode.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocosNode)));
            Json.gravar(pastaBlocos.resolve(id + ".json"), blocosNode);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("ales/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("ales: " + pessoas.size() + " deputados estaduais normalizados");
    }

    public static List<DeputadoBruto> extrairDeputados(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "ales", "deputados.html");
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return List.of();
        }
        try {
            String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
            return extrairDeputadosDoHtml(html);
        } catch (Exception e) {
            System.err.println("ales: erro ao extrair deputados: " + e.getMessage());
            return List.of();
        }
    }

    public static List<DeputadoBruto> extrairDeputadosDoHtml(String html) {
        List<DeputadoBruto> lista = new ArrayList<>();
        Matcher mSelect = SELECT_DEPUTADOS_PATTERN.matcher(html);
        if (!mSelect.find()) {
            return List.of();
        }

        String selectHtml = mSelect.group(1);
        Matcher mOpt = OPTION_DEPUTADO_PATTERN.matcher(selectHtml);
        Set<String> idsVistos = new HashSet<>();

        while (mOpt.find()) {
            String id = mOpt.group(1).trim();
            if ("0".equals(id)) {
                continue;
            }
            String nomeRaw = decodificarHtml(mOpt.group(2).trim());
            String slug = gerarSlug(nomeRaw);
            if (idsVistos.add(id)) {
                lista.add(new DeputadoBruto(id, nomeRaw, slug));
            }
        }
        return lista;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoes(Contexto ctx, Map<String, String> idPorNomeNorm) {
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        for (String comSlug : COMISSOES_SLUGS) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, "ales", "comissoes/" + comSlug + ".html");
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            try {
                String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
                String urlOrigem = SITE + "/Comissao/" + comSlug;
                Prov prov = Prov.de(FONTE_DETALHES, urlOrigem, arq);
                Map<String, List<OrgaoOuFrente>> doHtml = extrairComissoesDoHtml(html, comSlug, prov, idPorNomeNorm);
                for (var entry : doHtml.entrySet()) {
                    mapa.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("ales: erro ao extrair comissao " + comSlug + ": " + e.getMessage());
            }
        }
        return mapa;
    }

    public static Map<String, List<OrgaoOuFrente>> extrairComissoesDoHtml(
            String html, String comSlug, Prov prov, Map<String, String> idPorNomeNorm) {
        Map<String, List<OrgaoOuFrente>> resultado = new HashMap<>();

        String titulo = null;
        Matcher mTitulo = TITULO_COMISSAO_PATTERN.matcher(html);
        if (mTitulo.find()) {
            String bruto = decodificarHtml(mTitulo.group(1).trim());
            titulo = bruto.toLowerCase(Locale.ROOT).startsWith("comiss") ? bruto : "Comissão de " + bruto;
        } else {
            titulo = "Comissão de " + comSlug;
        }

        // Presidente
        Matcher mPres = PRESIDENTE_PATTERN.matcher(html);
        if (mPres.find()) {
            String nomePres = decodificarHtml(mPres.group(1).trim());
            String idPres = idPorNomeNorm.get(Texto.normalizarNome(nomePres));
            if (idPres != null) {
                resultado.computeIfAbsent(idPres, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", titulo, null, "Presidente", INICIO_LEG_20, null, prov));
            }
        }

        // Vice-Presidente
        Matcher mVice = VICE_PATTERN.matcher(html);
        if (mVice.find()) {
            String nomeVice = decodificarHtml(mVice.group(1).trim());
            String idVice = idPorNomeNorm.get(Texto.normalizarNome(nomeVice));
            if (idVice != null) {
                resultado.computeIfAbsent(idVice, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", titulo, null, "Vice-Presidente", INICIO_LEG_20, null, prov));
            }
        }

        // Membros Efetivos
        Matcher mMembros = MEMBROS_EFETIVOS_PATTERN.matcher(html);
        if (mMembros.find()) {
            String bloco = mMembros.group(1);
            String[] linhas = bloco.split("<br\\s*/?>|\\r?\\n");
            for (String linha : linhas) {
                String nome = decodificarHtml(linha.replaceAll("<[^>]+>", "").trim());
                if (!nome.isBlank()) {
                    String id = idPorNomeNorm.get(Texto.normalizarNome(nome));
                    if (id != null) {
                        resultado.computeIfAbsent(id, k -> new ArrayList<>())
                                .add(new OrgaoOuFrente("comissao", titulo, null, "Membro Efetivo", INICIO_LEG_20, null, prov));
                    }
                }
            }
        }

        // Membros Suplentes
        Matcher mSuplentes = MEMBROS_SUPLENTES_PATTERN.matcher(html);
        if (mSuplentes.find()) {
            String bloco = mSuplentes.group(1);
            String[] linhas = bloco.split("<br\\s*/?>|\\r?\\n");
            for (String linha : linhas) {
                String nome = decodificarHtml(linha.replaceAll("<[^>]+>", "").trim());
                if (!nome.isBlank()) {
                    String id = idPorNomeNorm.get(Texto.normalizarNome(nome));
                    if (id != null) {
                        resultado.computeIfAbsent(id, k -> new ArrayList<>())
                                .add(new OrgaoOuFrente("comissao", titulo, null, "Membro Suplente", INICIO_LEG_20, null, prov));
                    }
                }
            }
        }

        return resultado;
    }

    public static String gerarSlug(String nomeBruto) {
        if (nomeBruto == null || nomeBruto.isBlank()) {
            return "";
        }
        String semAcentos = Normalizer.normalize(nomeBruto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos.replaceAll("[^a-zA-Z0-9]", "");
    }

    public static String decodificarHtml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&#227;", "ã")
                .replace("&#225;", "á")
                .replace("&#233;", "é")
                .replace("&#237;", "í")
                .replace("&#243;", "ó")
                .replace("&#250;", "ú")
                .replace("&#226;", "â")
                .replace("&#234;", "ê")
                .replace("&#244;", "ô")
                .replace("&#231;", "ç")
                .replace("&#193;", "Á")
                .replace("&#201;", "É")
                .replace("&#205;", "Í")
                .replace("&#211;", "Ó")
                .replace("&#218;", "Ú")
                .replace("&#194;", "Â")
                .replace("&#202;", "Ê")
                .replace("&#212;", "Ô")
                .replace("&#195;", "Ã")
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
