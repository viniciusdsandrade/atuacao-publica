package etl.fontes;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * Proposições da ALERJ na 13ª legislatura, pela pesquisa das visões do sistema de processo legislativo (ADR-0078).
 * O código da proposição é AAAATTNNNNN: ano, tipo e o número, que corre pela legislatura inteira.
 * A pesquisa devolve no máximo 1.000 linhas: a consulta que chega ao limite se divide pelo dígito seguinte do código.
 */
final class AlerjProposicoes {

    static final String FONTE = "alerj-proposicoes";
    static final String BASE = "https://alerjln1.alerj.rj.gov.br/scpro2327.nsf/";
    static final int LIMITE_DA_PESQUISA = 1000;
    static final int ANO_INICIAL = 2023;

    /** Tipo, com o código de dois dígitos no código da proposição e a visão do sistema. */
    record Tipo(String sigla, String codigo, String visao) {
    }

    static final List<Tipo> TIPOS = List.of(
            new Tipo("PEC", "01", "e156baa31cae7158832566ec0018d83c"),
            new Tipo("PLC", "02", "34c4e2da9b17c0d3832566ec0018d830"),
            new Tipo("PL", "03", "18c1dd68f96be3e7832566ec0018d833"),
            new Tipo("PDL", "04", "b3156862637ed3b7832566ec0018d82d"),
            new Tipo("PR", "05", "10d6d451b00fd42b832566ec0018d836"));

    private static final Pattern LINHA = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELULA = Pattern.compile("(?is)<td[^>]*>(.*?)</td>");
    private static final Pattern LINK = Pattern.compile("(?i)href=\"/scpro2327\\.nsf/([0-9a-f]{32})/([0-9a-f]{32})\\?OpenDocument\"[^>]*>(\\d{11})</a>");
    private static final Pattern DATA_US = Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");

    private AlerjProposicoes() {
    }

    /** Linha da pesquisa, antes da ligação com os deputados. */
    record Linha(String codigo, String visao, String documento, String ementa, String data, List<String> autores) {
    }

    static void coletar(Contexto ctx, Coleta coleta) throws Exception {
        int anoAtual = ctx.hoje().getYear();
        for (Tipo tipo : TIPOS) {
            for (int ano = ANO_INICIAL; ano <= anoAtual && !coleta.orcamentoExaurido(); ano++) {
                buscar(ctx, coleta, tipo, ano + tipo.codigo(), Politica.porAnoSemanal(ano, ctx.hoje()));
            }
        }
    }

    /** Linhas da consulta. No limite, divide pelo dígito seguinte; os números são contínuos, e o primeiro vazio depois de um cheio encerra. */
    private static int buscar(Contexto ctx, Coleta coleta, Tipo tipo, String prefixo, Politica politica) throws Exception {
        if (coleta.orcamentoExaurido()) {
            return 0;
        }
        Coleta.Arquivo arq = coleta.obterOpcional(url(tipo, prefixo), caminho(tipo, prefixo), "text/html", politica);
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return 0;
        }
        int linhas = linhas(Texto.lerArquivo(Coleta.caminho(ctx, arq))).size();
        if (linhas >= LIMITE_DA_PESQUISA && prefixo.length() < 11) {
            boolean achou = false;
            for (int d = 0; d <= 9; d++) {
                int n = buscar(ctx, coleta, tipo, prefixo + d, politica);
                if (n == 0 && achou) {
                    break;
                }
                achou |= n > 0;
            }
        }
        return linhas;
    }

    /** Proposições por id de deputado, pelo nome do autor como a ALERJ escreve (mapa de idsPorNome). Autor que não é deputado fica de fora. */
    static Map<String, List<Proposicao>> porDeputado(Contexto ctx, Map<String, String> idPorNome) {
        Map<String, Linha> porCodigo = new LinkedHashMap<>();
        Map<String, Prov> provs = new HashMap<>();
        int anoAtual = ctx.hoje().getYear();
        for (Tipo tipo : TIPOS) {
            for (int ano = ANO_INICIAL; ano <= anoAtual; ano++) {
                ler(ctx, tipo, ano + tipo.codigo(), porCodigo, provs);
            }
        }
        Map<String, List<Proposicao>> porDeputado = new LinkedHashMap<>();
        Set<String> semPerfil = new LinkedHashSet<>();
        int autorias = 0;
        for (Linha l : porCodigo.values()) {
            Tipo tipo = TIPOS.stream().filter(t -> t.codigo().equals(l.codigo().substring(4, 6))).findFirst().orElse(null);
            if (tipo == null) {
                continue;
            }
            int ano = Integer.parseInt(l.codigo().substring(0, 4));
            int numero = Integer.parseInt(l.codigo().substring(6));
            for (int i = 0; i < l.autores().size(); i++) {
                String id = idDoAutor(idPorNome, l.autores().get(i));
                if (id == null) {
                    semPerfil.add(l.autores().get(i));
                    continue;
                }
                porDeputado.computeIfAbsent(id, k -> new ArrayList<>()).add(new Proposicao(l.codigo(), tipo.sigla(), numero, ano,
                        tipo.sigla() + " " + numero + "/" + ano, l.ementa(), null, null, l.data(), i + 1, i == 0,
                        BASE + l.visao() + "/" + l.documento() + "?OpenDocument", provs.get(l.codigo())));
                autorias++;
            }
        }
        porDeputado.values().forEach(lista -> lista.sort(Comparator.comparing(
                (Proposicao p) -> p.dataApresentacao() == null ? "" : p.dataApresentacao()).reversed().thenComparing(Proposicao::idFonte)));
        System.out.println("alerj: " + porCodigo.size() + " proposições e " + autorias + " autorias em " + porDeputado.size()
                + " deputados; " + semPerfil.size() + " nome(s) de autor sem perfil de deputado");
        return porDeputado;
    }

    private static void ler(Contexto ctx, Tipo tipo, String prefixo, Map<String, Linha> porCodigo, Map<String, Prov> provs) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, "alerj", caminho(tipo, prefixo));
        if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
            return;
        }
        List<Linha> linhas;
        try {
            linhas = linhas(Texto.lerArquivo(Coleta.caminho(ctx, arq)));
        } catch (Exception e) {
            System.err.println("alerj: erro ao ler " + caminho(tipo, prefixo) + ": " + e.getMessage());
            return;
        }
        Prov prov = Prov.de(FONTE, url(tipo, prefixo), arq);
        for (Linha l : linhas) {
            if (porCodigo.putIfAbsent(l.codigo(), l) == null) {
                provs.put(l.codigo(), prov);
            }
        }
        if (linhas.size() >= LIMITE_DA_PESQUISA && prefixo.length() < 11) {
            for (int d = 0; d <= 9; d++) {
                ler(ctx, tipo, prefixo + d, porCodigo, provs);
            }
        }
    }

    /** Linhas da página de resultado: código com link, ementa (antes de "=>"), data em MM/DD/AAAA e autores separados por vírgula. */
    static List<Linha> linhas(String html) {
        List<Linha> lista = new ArrayList<>();
        Matcher linha = LINHA.matcher(html);
        while (linha.find()) {
            Matcher link = LINK.matcher(linha.group(1));
            if (!link.find()) {
                continue;
            }
            List<String> celulas = new ArrayList<>();
            Matcher celula = CELULA.matcher(linha.group(1));
            while (celula.find()) {
                celulas.add(celula.group(1));
            }
            if (celulas.size() < 6) {
                continue;
            }
            String ementa = limpar(celulas.get(3));
            if (ementa != null && ementa.contains("=>")) {
                ementa = ementa.substring(0, ementa.indexOf("=>")).strip();
            }
            Matcher data = DATA_US.matcher(limpar(celulas.get(4)) == null ? "" : limpar(celulas.get(4)));
            String iso = data.find() ? data.group(3) + "-" + data.group(1) + "-" + data.group(2) : null;
            List<String> autores = new ArrayList<>();
            String nomes = limpar(celulas.get(5));
            if (nomes != null) {
                for (String nome : nomes.split(",")) {
                    String n = nome.strip();
                    if (!n.isEmpty()) {
                        autores.add(n);
                    }
                }
            }
            lista.add(new Linha(link.group(3), link.group(1), link.group(2),
                    ementa == null || ementa.isEmpty() ? null : Guarda.sanitizar(ementa), iso, autores));
        }
        return lista;
    }

    /** Tratamento antes do nome ("Dr.", "Prof."): a ALERJ escreve o autor com ou sem ele. */
    private static final Pattern TRATAMENTO = Pattern.compile(
            "^(DR|DRA|PROF|PROFA|PROFESSOR|PROFESSORA|DELEGADO|DELEGADA|PASTOR|PASTORA|CORONEL|CAPITAO|SARGENTO|TENENTE) ");

    /** Nome do autor ou do deputado como chave: sem acento e sem pontuação, em maiúsculas, com um espaço entre as palavras. */
    static String chave(String nome) {
        String n = Texto.normalizarNome(Texto.semEntidades(nome));
        return n == null ? null : n.replaceAll("[.,;]", " ").replaceAll("\\s+", " ").strip();
    }

    /** Chave sem o tratamento, para a segunda tentativa de ligação. */
    static String semTratamento(String chave) {
        return chave == null ? null : TRATAMENTO.matcher(chave).replaceFirst("");
    }

    /** Mapa de nome para id com as duas chaves. A chave sem tratamento só entra se não for de mais de um deputado. */
    static Map<String, String> idsPorNome(Map<String, String> nomePorId) {
        Map<String, String> exatos = new HashMap<>();
        Map<String, String> semTratamento = new HashMap<>();
        Set<String> repetidas = new java.util.HashSet<>();
        nomePorId.forEach((id, nome) -> {
            String c = chave(nome);
            if (c == null) {
                return;
            }
            exatos.putIfAbsent(c, id);
            String s = semTratamento(c);
            if (semTratamento.containsKey(s) && !semTratamento.get(s).equals(id)) {
                repetidas.add(s);
            }
            semTratamento.putIfAbsent(s, id);
        });
        repetidas.forEach(semTratamento::remove);
        Map<String, String> mapa = new HashMap<>(exatos);
        semTratamento.forEach((s, id) -> mapa.putIfAbsent("~" + s, id));
        return mapa;
    }

    private static String idDoAutor(Map<String, String> idPorNome, String autor) {
        String c = chave(autor);
        String id = idPorNome.get(c);
        return id != null ? id : idPorNome.get("~" + semTratamento(c));
    }

    private static String limpar(String html) {
        return Texto.semEntidades(html.replaceAll("(?is)<br\\s*/?>", " ").replaceAll("(?s)<[^>]+>", " "));
    }

    static String url(Tipo tipo, String prefixo) {
        return BASE + tipo.visao() + "?SearchView&Query=FIELD%20prop_cod_prop%20CONTAINS%20" + prefixo + "*&SearchMax=0&SearchOrder=4";
    }

    private static String caminho(Tipo tipo, String prefixo) {
        return "proposicoes/" + tipo.sigla() + "-" + prefixo + ".html";
    }
}
