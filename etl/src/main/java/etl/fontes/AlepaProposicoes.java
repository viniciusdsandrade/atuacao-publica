package etl.fontes;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Guarda;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;

/**
 * Proposições da ALEPA na 60ª legislatura, pelo painel DevExpress da página de proposições (ADR-0087). A primeira
 * página sai do callback do painel, com o filtro de tipo e ano; as seguintes, do callback da lista de cartões, com o
 * estado que a página anterior devolve e o cookie de sessão que o primeiro callback cria. Cada tipo e ano é uma cadeia
 * única, coletada inteira na mesma sessão.
 */
final class AlepaProposicoes {

    static final String FONTE = "alepa-proposicoes";
    static final String SITE = "https://www.alepa.pa.gov.br";
    static final String PAINEL = SITE + "/Legislativo/CallbackPanelProposicoes";
    static final String CARTOES = SITE + "/Legislativo/CardViewProposicoes";
    static final int ANO_INICIAL = 2023;
    static final int MAX_PAGINAS = 400;

    /** Tipo no filtro do painel (/Legislativo/GetTipoProposicoes), com a sigla publicada. */
    record Tipo(String sigla, int codigo) {
    }

    static final List<Tipo> TIPOS = List.of(new Tipo("PEC", 2), new Tipo("PLC", 4), new Tipo("PL", 3), new Tipo("PDL", 1),
            new Tipo("PR", 5));

    private static final Pattern CARTAO = Pattern.compile("(?s)onCardClick\\(\"(/Legislativo/DetalhesProposicao\\?IdProposicao=(\\d+)[^\"]*)\"\\)"
            + "[^>]*>\\s*<h3[^>]*>(.*?)</h3>\\s*<span>(.*?)</span>\\s*<p>(.*?)</p>");
    private static final Pattern IDENTIFICACAO = Pattern.compile("^(.+?) Nº (\\d+)/(\\d{4})(?:, DE (\\d{2})/(\\d{2})/(\\d{4}))?");
    /** "Página 1 de 88 (880 itens)"; o acento vem como entidade HTML ("P&#225;gina"). */
    private static final Pattern PAGINAS = Pattern.compile("P(?:á|&#225;|&aacute;)gina \\d+ de (\\d+)");
    private static final Pattern ESTADO = Pattern.compile("'stateObject':(\\{.*?'callbackState':'[^']*'[^}]*\\})");

    private AlepaProposicoes() {
    }

    record Cartao(String id, String detalhe, String autores, String tipo, int numero, int ano, String data, String ementa) {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        ctx.http().manterSessao("www.alepa.pa.gov.br");
        for (Tipo tipo : TIPOS) {
            for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear() && !coleta.orcamentoExaurido(); ano++) {
                if (!precisaRenovar(ctx, tipo, ano)) {
                    continue;
                }
                cadeia(ctx, coleta, tipo, ano);
            }
        }
    }

    /**
     * Ano fechado com a cadeia completa fica no cache. O ano aberto renova uma vez por semana. A cadeia é inteira ou
     * nada: o estado de cada página vale só na sessão em que foi criado.
     */
    private static boolean precisaRenovar(Contexto ctx, Tipo tipo, int ano) throws Exception {
        Coleta.Arquivo primeira = Coleta.metadados(ctx, "alepa", caminho(tipo, ano, 1));
        if (primeira == null || !Files.exists(Coleta.caminho(ctx, primeira))) {
            return true;
        }
        int paginas = paginas(Texto.lerArquivo(Coleta.caminho(ctx, primeira)));
        Coleta.Arquivo ultima = Coleta.metadados(ctx, "alepa", caminho(tipo, ano, Math.max(1, paginas)));
        boolean completa = ultima != null && Files.exists(Coleta.caminho(ctx, ultima)) && !ultima.capturadoEm().isBefore(primeira.capturadoEm());
        if (!completa) {
            return true;
        }
        boolean aberto = ano >= ctx.hoje().getYear() || ano == ctx.hoje().getYear() - 1 && ctx.hoje().getMonthValue() <= 3;
        return aberto && primeira.capturadoEm().isBefore(ctx.agora().minus(Duration.ofDays(7)));
    }

    private static void cadeia(Contexto ctx, Coleta coleta, Tipo tipo, int ano) throws Exception {
        Coleta.Arquivo arq = coleta.obterFormularioOpcional(PAINEL, Map.of("DXCallbackName", "cbpProposicoes",
                "__DXCallbackArgument", "c0:", "model[IdTipoProposicao]", String.valueOf(tipo.codigo()), "model[Ano]", String.valueOf(ano)),
                caminho(tipo, ano, 1), "text/html", Politica.SEMPRE);
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return;
        }
        String html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
        int paginas = Math.min(MAX_PAGINAS, paginas(html));
        for (int p = 2; p <= paginas && !coleta.orcamentoExaurido(); p++) {
            String estado = estado(html);
            if (estado == null) {
                return;
            }
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("DXCallbackName", "cardViewProposicoes");
            campos.put("__DXCallbackArgument", argumentoDaPagina(p));
            campos.put("cardViewProposicoes", estado);
            arq = coleta.obterFormularioOpcional(CARTOES, campos, caminho(tipo, ano, p), "text/html", Politica.SEMPRE);
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                return;
            }
            html = Texto.lerArquivo(Coleta.caminho(ctx, arq));
        }
    }

    static Map<String, List<Proposicao>> porDeputado(Contexto ctx, AutoriaPorNome autoria) {
        Map<String, List<Proposicao>> porDeputado = new LinkedHashMap<>();
        Set<String> vistas = new LinkedHashSet<>();
        Set<String> semPerfil = new LinkedHashSet<>();
        int autorias = 0;
        for (Tipo tipo : TIPOS) {
            for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear(); ano++) {
                Instant inicioDaCadeia = null;
                for (int p = 1; p <= MAX_PAGINAS; p++) {
                    Coleta.Arquivo arq = Coleta.metadados(ctx, "alepa", caminho(tipo, ano, p));
                    // Página de uma cadeia mais velha que a primeira é de outra coleta: a lista pode ter deslocado.
                    if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))
                            || inicioDaCadeia != null && arq.capturadoEm().isBefore(inicioDaCadeia)) {
                        break;
                    }
                    if (p == 1) {
                        inicioDaCadeia = arq.capturadoEm();
                    }
                    List<Cartao> cartoes;
                    try {
                        cartoes = cartoes(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                    } catch (Exception e) {
                        System.err.println("alepa: erro ao ler " + caminho(tipo, ano, p) + ": " + e.getMessage());
                        break;
                    }
                    Prov prov = Prov.de(FONTE, SITE + "/Legislativo/Proposicoes", arq);
                    for (Cartao c : cartoes) {
                        if (!vistas.add(tipo.sigla() + "|" + c.id())) {
                            continue;
                        }
                        List<String> autores = AlemaProposicoes.autores(c.autores(), autoria).stream().map(AlepaProposicoes::grafiaDoPerfil).toList();
                        for (int i = 0; i < autores.size(); i++) {
                            String id = autoria.id(autores.get(i));
                            if (id == null) {
                                semPerfil.add(autores.get(i));
                                continue;
                            }
                            porDeputado.computeIfAbsent(id, k -> new ArrayList<>()).add(new Proposicao(tipo.sigla() + "-" + c.id(),
                                    tipo.sigla(), c.numero(), c.ano(), tipo.sigla() + " " + c.numero() + "/" + c.ano(),
                                    c.ementa() == null ? null : Guarda.sanitizar(c.ementa()), null, null, c.data(), i + 1, i == 0,
                                    SITE + c.detalhe(), prov));
                            autorias++;
                        }
                    }
                }
            }
        }
        porDeputado.values().forEach(l -> l.sort(Comparator.comparing(
                (Proposicao p) -> p.dataApresentacao() == null ? "" : p.dataApresentacao()).reversed().thenComparing(Proposicao::idFonte)));
        System.out.println("alepa: " + vistas.size() + " proposições e " + autorias + " autorias em " + porDeputado.size()
                + " deputados; " + semPerfil.size() + " nome(s) de autor sem perfil de deputado");
        return porDeputado;
    }

    /** Mesma grafia dos votos (Alepa.GRAFIAS_DAS_VOTACOES): "DEP. CARLOS BORDALO" é o perfil "Deputado Bordalo". */
    static String grafiaDoPerfil(String autor) {
        String chave = Texto.normalizarNome(autor == null ? null : autor.replaceFirst("(?i)^DEP\\.?\\s+", ""));
        String perfil = chave == null ? null : Alepa.GRAFIAS_DAS_VOTACOES.get(chave);
        return perfil != null ? perfil : autor;
    }

    static List<Cartao> cartoes(String html) {
        List<Cartao> lista = new ArrayList<>();
        Matcher m = CARTAO.matcher(html);
        while (m.find()) {
            String linha = Texto.semEntidades(m.group(4).replaceAll("(?s)<[^>]+>", " "));
            Matcher id = linha == null ? null : IDENTIFICACAO.matcher(linha);
            if (id == null || !id.find()) {
                continue;
            }
            String data = id.group(4) == null ? null : id.group(6) + "-" + id.group(5) + "-" + id.group(4);
            lista.add(new Cartao(m.group(2), Texto.semEntidades(m.group(1)), Texto.semEntidades(m.group(3).replaceAll("(?s)<[^>]+>", " ")),
                    id.group(1), Integer.parseInt(id.group(2)), Integer.parseInt(id.group(3)), data,
                    Texto.semEntidades(m.group(5).replaceAll("(?s)<[^>]+>", " "))));
        }
        return lista;
    }

    /**
     * Argumento do callback para ir à página p (1 = primeira). O protocolo do DevExpress prefixa cada parte pelo tamanho:
     * "GB|20;12|PAGERONCLICK3|PN1;" pede a página 2; da página 11 em diante, "PN10" tem quatro letras e os tamanhos mudam.
     */
    static String argumentoDaPagina(int p) {
        String pagina = "PN" + (p - 1);
        String corpo = "12|PAGERONCLICK" + pagina.length() + "|" + pagina;
        return "c0:GB|" + corpo.length() + ";" + corpo + ";";
    }

    static int paginas(String html) {
        Matcher m = PAGINAS.matcher(html);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** Estado da lista de cartões, em JSON, para pedir a página seguinte. */
    static String estado(String html) {
        Matcher m = ESTADO.matcher(html);
        return m.find() ? m.group(1).replace('\'', '"') : null;
    }

    private static String caminho(Tipo tipo, int ano, int pagina) {
        return "proposicoes/" + tipo.sigla() + "-" + ano + "-" + pagina + ".html";
    }
}
