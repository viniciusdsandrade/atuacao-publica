package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import etl.fontes.ExecutivoSp.Ato;

/**
 * Poder Executivo do Estado de Pernambuco (ADR-0089): decretos do Executivo no sistema de legislação da Alepe, uma
 * página por ano com todos os decretos. A data é a da publicação no Diário Oficial, que a lista traz.
 */
public final class ExecutivoPe implements Fonte {

    public static final String FONTE_DECRETOS = "alepe-legislacao";
    public static final String SITE = "https://legis.alepe.pe.gov.br/";
    public static final String UF = "PE";
    static final int ANO_INICIAL = 2022;

    private static final Pattern LINHA = Pattern.compile("(?s)<span class=\"nome-norma\"><a href='(texto\\.aspx\\?id=(\\d+)[^']*)'+>"
            + "\\s*Decreto do Executivo n[°º] ?([\\d.]+)\\s*</a></span>\\s*<span class=\"publicacao\">\\s*Publicada no DOE (\\d{2})/(\\d{2})/(\\d{4})"
            + ".*?<div class=\"fLeft\">(.*?)</div>");

    @Override
    public String nome() {
        return "executivope";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear() && !coleta.orcamentoExaurido(); ano++) {
            // Uma página por ano, com todos os decretos (cerca de 9 MB): o ano aberto renova uma vez por semana.
            coleta.obterOpcional(url(ano), caminho(ano), "text/html", Politica.porAnoSemanal(ano, ctx.hoje()));
        }
        coleta.periodo(new Meta.Periodo(LocalDate.of(ANO_INICIAL, 1, 1), ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path saida = ctx.normalizado().resolve(nome());
        Files.createDirectories(saida);
        Map<String, Ato> porId = new LinkedHashMap<>();
        for (int ano = ANO_INICIAL; ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), caminho(ano));
            if (arq == null || !Files.exists(Coleta.caminho(ctx, arq))) {
                continue;
            }
            for (Ato a : decretos(Texto.lerArquivo(Coleta.caminho(ctx, arq)), arq)) {
                porId.putIfAbsent(a.idFonte(), a);
            }
        }
        List<Ato> atos = new ArrayList<>(porId.values());
        // A lista tem data de publicação errada (um decreto de 2025 sai publicado em 2054): data depois de hoje fica de fora.
        atos.removeIf(a -> a.data().compareTo(ANO_INICIAL + "-01-01") < 0 || a.data().compareTo(ctx.hoje().toString()) > 0);
        atos.sort(Comparator.comparing(Ato::data).reversed().thenComparing(Ato::idFonte));
        Json.gravar(saida.resolve("atos.json"), Json.SAIDA.valueToTree(atos));
        Json.gravar(saida.resolve("despesas.json"), Json.SAIDA.createArrayNode());
        Json.gravar(saida.resolve("compromissos.json"), Json.SAIDA.createArrayNode());
        System.out.println("executivope: " + atos.size() + " decretos normalizados");
    }

    static List<Ato> decretos(String html, Coleta.Arquivo arq) {
        List<Ato> lista = new ArrayList<>();
        Matcher m = LINHA.matcher(html);
        while (m.find()) {
            String numero = m.group(3).replace(".", "");
            String data = m.group(6) + "-" + m.group(5) + "-" + m.group(4);
            String ementa = Texto.semEntidades(m.group(7).replaceAll("(?s)<[^>]+>", " "));
            Prov prov = arq == null ? null : Prov.de(FONTE_DECRETOS, SITE + m.group(1), arq);
            lista.add(new Ato(UF, "pe-dec-" + numero, "DEC", numero, data, "Decreto nº " + m.group(3),
                    ementa == null ? null : Guarda.sanitizar(ementa), prov));
        }
        return lista;
    }

    static String url(int ano) {
        return SITE + "lista.aspx?tiponorma=6&ano=" + ano;
    }

    private static String caminho(int ano) {
        return "decretos/" + ano + ".html";
    }
}
