package etl.fontes;

import java.nio.file.Files;
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
 * Proposições da ALMT na 20ª legislatura, pela pesquisa de proposições do portal (ADR-0085). A lista é HTML, dez por
 * página, por tipo e ano, em ordem de número. Cada item traz a ementa, a linha "Projeto de lei nº 2/2025 Dep. Eduardo
 * Botelho - Protocolo nº 3/2025" e o PDF do texto. A data de apresentação e a situação não saem na lista.
 */
final class AlmtProposicoes {

    static final String FONTE = "almt-proposicoes";
    static final String SITE = "https://www.al.mt.gov.br";
    static final int POR_PAGINA = 10;
    static final int MAX_PAGINAS = 500;
    static final int ANO_INICIAL = 2023;

    /** Tipo no formulário de pesquisa, com a sigla publicada. */
    record Tipo(String sigla, int codigo) {
    }

    static final List<Tipo> TIPOS = List.of(new Tipo("PEC", 11), new Tipo("PLC", 2), new Tipo("PL", 1), new Tipo("PDL", 3),
            new Tipo("PR", 4));

    private static final Pattern ITEM = Pattern.compile("(?s)<h3 class=\"fs-16\">(.*?)</h3>\\s*<div class=\"text-muted mb-2\">(.*?)</div>"
            + "(.*?)(?=<h3 class=\"fs-16\">|$)");
    private static final Pattern LINHA = Pattern.compile("^(.+?) nº (\\d+)/(\\d{4})\\s+(.*?)\\s+-\\s+Protocolo nº");
    private static final Pattern DOCUMENTO = Pattern.compile("collapse-group-(\\d+)");
    private static final Pattern PDF = Pattern.compile("href=\"(/storage/webdisco/[^\"]+\\.pdf)\"");

    private AlmtProposicoes() {
    }

    /** Item da lista, antes da ligação com os deputados. */
    record Item(String documento, String ementa, String tipo, int numero, int ano, String autores, String pdf) {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        for (Tipo tipo : TIPOS) {
            for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear() && !coleta.orcamentoExaurido(); ano++) {
                // Em ordem de número, a lista do ano aberto cresce no fim: as páginas renovam juntas, uma vez por semana.
                Politica politica = Politica.porAnoSemanalEmLista(ano, ctx.hoje());
                for (int p = 1; p <= MAX_PAGINAS && !coleta.orcamentoExaurido(); p++) {
                    Coleta.Arquivo arq = coleta.obterOpcional(url(tipo, ano, p), caminho(tipo, ano, p), "text/html", politica);
                    if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))
                            || itens(Texto.lerArquivo(Coleta.caminho(ctx, arq))).size() < POR_PAGINA) {
                        break;
                    }
                }
            }
        }
    }

    static Map<String, List<Proposicao>> porDeputado(Contexto ctx, AutoriaPorNome autoria) {
        Map<String, List<Proposicao>> porDeputado = new LinkedHashMap<>();
        Set<String> vistas = new LinkedHashSet<>();
        Set<String> semPerfil = new LinkedHashSet<>();
        int autorias = 0;
        for (Tipo tipo : TIPOS) {
            for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear(); ano++) {
                for (int p = 1; p <= MAX_PAGINAS; p++) {
                    Coleta.Arquivo arq = Coleta.metadados(ctx, "almt", caminho(tipo, ano, p));
                    if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                        break;
                    }
                    List<Item> itens;
                    try {
                        itens = itens(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
                    } catch (Exception e) {
                        System.err.println("almt: erro ao ler " + caminho(tipo, ano, p) + ": " + e.getMessage());
                        break;
                    }
                    Prov prov = Prov.de(FONTE, arq.url(), arq);
                    for (Item item : itens) {
                        if (!vistas.add(item.documento())) {
                            continue;
                        }
                        List<String> autores = AlemaProposicoes.autores(grafiaDoPerfil(item.autores()), autoria);
                        for (int i = 0; i < autores.size(); i++) {
                            String id = autoria.id(autores.get(i));
                            if (id == null) {
                                semPerfil.add(autores.get(i));
                                continue;
                            }
                            porDeputado.computeIfAbsent(id, k -> new ArrayList<>()).add(new Proposicao(item.documento(), tipo.sigla(),
                                    item.numero(), item.ano(), tipo.sigla() + " " + item.numero() + "/" + item.ano(),
                                    item.ementa() == null ? null : Guarda.sanitizar(item.ementa()), null, null, null, i + 1, i == 0,
                                    item.pdf() == null ? SITE + "/proposicao/cpdoc/" + item.documento() + "/visualizar" : SITE + item.pdf(),
                                    prov));
                            autorias++;
                        }
                    }
                    if (itens.size() < POR_PAGINA) {
                        break;
                    }
                }
            }
        }
        porDeputado.values().forEach(l -> l.sort(Comparator.comparing((Proposicao p) -> p.ano() == null ? 0 : p.ano())
                .thenComparing(p -> p.numero() == null ? 0 : p.numero()).reversed()));
        System.out.println("almt: " + vistas.size() + " proposições e " + autorias + " autorias em " + porDeputado.size()
                + " deputados; " + semPerfil.size() + " nome(s) de autor sem perfil de deputado");
        return porDeputado;
    }

    /** A pesquisa escreve "Carlos Avalone"; o perfil, "Carlos Avallone". */
    private static final Map<String, String> GRAFIAS = Map.of("Dep. Carlos Avalone", "Dep. Carlos Avallone");

    static String grafiaDoPerfil(String autores) {
        return autores == null ? null : GRAFIAS.getOrDefault(autores.strip(), autores);
    }

    static List<Item> itens(String html) {
        List<Item> lista = new ArrayList<>();
        Matcher m = ITEM.matcher(html);
        while (m.find()) {
            String linha = Texto.semEntidades(m.group(2).replaceAll("(?s)<[^>]+>", " "));
            Matcher l = linha == null ? null : LINHA.matcher(linha);
            Matcher doc = DOCUMENTO.matcher(m.group(3));
            if (l == null || !l.find() || !doc.find()) {
                continue;
            }
            Matcher pdf = PDF.matcher(m.group(3));
            lista.add(new Item(doc.group(1), Texto.semEntidades(m.group(1).replaceAll("(?s)<[^>]+>", " ")), l.group(1),
                    Integer.parseInt(l.group(2)), Integer.parseInt(l.group(3)), l.group(4).strip(), pdf.find() ? pdf.group(1) : null));
        }
        return lista;
    }

    static String url(Tipo tipo, int ano, int pagina) {
        return SITE + "/proposicao?almt_form_proposicao_search_proposicao%5BtipoPropositura%5D=" + tipo.codigo()
                + "&almt_form_proposicao_search_proposicao%5Bano%5D=" + ano + "&page=" + pagina;
    }

    private static String caminho(Tipo tipo, int ano, int pagina) {
        return "proposicoes/" + tipo.sigla() + "-" + ano + "-" + pagina + ".html";
    }
}
