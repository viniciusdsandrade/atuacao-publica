package etl.fontes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.fontes.ExecutivoSp.Agregado;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Poder Executivo do Estado da Bahia (ADR-0083): despesa paga por mês e órgão, a partir do painel de despesas do portal
 * de dados abertos. O arquivo é um ZIP único, de cerca de 230 MB, com todos os anos desde 2013 já somados por mês,
 * órgão, função e elemento, sem favorecido. A coleta soma o arquivo assim que baixa e guarda só a soma.
 */
public final class ExecutivoBa implements Fonte {

    public static final String FONTE_DESPESA = "ba-despesas";
    public static final String URL_PACOTE = "https://dados.ba.gov.br/api/3/action/package_show?id=despesas";
    public static final String UF = "BA";
    static final int ANO_INICIAL = 2022;

    @Override
    public String nome() {
        return "executivoba";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        Coleta.Arquivo pacote = coleta.obter(URL_PACOTE, "despesa/pacote.json", "application/json", Politica.SEMANAL);
        String url = arquivo(Json.ler(Coleta.caminho(ctx, pacote)));
        if (url == null) {
            throw new IOException("executivoba: o pacote de despesas não traz o arquivo ZIP");
        }
        coleta.obterDerivado(url, "despesa/somas.json", "application/zip", Politica.SEMANAL, ExecutivoBa::somar);
        coleta.periodo(new Meta.Periodo(LocalDate.of(ANO_INICIAL, 1, 1), ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path saida = ctx.normalizado().resolve(nome());
        Files.createDirectories(saida);
        List<Agregado> despesas = new ArrayList<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "despesa/somas.json");
        if (arq != null && Files.exists(Coleta.caminho(ctx, arq))) {
            Prov prov = Prov.de(FONTE_DESPESA, arq.url(), arq);
            for (JsonNode s : Json.ler(Coleta.caminho(ctx, arq))) {
                despesas.add(new Agregado(UF, s.get("ano").asInt(), s.get("mes").asInt(), Json.texto(s, "orgao"), Json.texto(s, "orgao"),
                        s.get("valor").decimalValue(), s.get("quantidade").asInt(), prov));
            }
        }
        Json.gravar(saida.resolve("despesas.json"), Json.SAIDA.valueToTree(despesas));
        Json.gravar(saida.resolve("atos.json"), Json.SAIDA.createArrayNode());
        Json.gravar(saida.resolve("compromissos.json"), Json.SAIDA.createArrayNode());
        System.out.println("executivoba: " + despesas.size() + " agregados de despesa normalizados");
    }

    static String arquivo(JsonNode pacote) {
        for (JsonNode r : pacote.path("result").path("resources")) {
            String url = Json.texto(r, "url");
            if (url != null && url.toLowerCase().endsWith(".zip")) {
                return url;
            }
        }
        return null;
    }

    /** Derivação: soma o valor pago do Poder Executivo por mês e órgão, de 2022 em diante. */
    static void somar(Path zip, Path destino) throws IOException {
        Map<String, BigDecimal> valor = new TreeMap<>();
        Map<String, Integer> linhas = new TreeMap<>();
        try (InputStream in = Files.newInputStream(zip); ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                // O ZIP traz outras visões, como a de empenhos, com o credor e o CPF ou CNPJ: só o painel é lido.
                if (e.getName().toUpperCase().endsWith("PAINEL_DESPESA.CSV")) {
                    somar(z, valor, linhas);
                }
            }
        }
        ArrayNode lista = Json.SAIDA.createArrayNode();
        valor.forEach((chave, v) -> {
            String[] partes = chave.split("\\|", 3);
            ObjectNode s = lista.addObject();
            s.put("ano", Integer.parseInt(partes[0])).put("mes", Integer.parseInt(partes[1])).put("orgao", partes[2])
                    .put("valor", v).put("quantidade", linhas.get(chave));
        });
        Json.gravar(destino, lista);
    }

    /**
     * CSV com ponto e vírgula e todos os campos entre aspas, UTF-8 com BOM. Cada linha já é uma soma (mês, órgão,
     * função, elemento, fonte): a quantidade conta as linhas com valor pago, não pagamentos.
     */
    static void somar(InputStream csv, Map<String, BigDecimal> valor, Map<String, Integer> linhas) throws IOException {
        BufferedReader leitor = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8));
        String cabecalho = leitor.readLine();
        if (cabecalho == null) {
            return;
        }
        List<String> colunas = campos(cabecalho.replace("﻿", ""));
        int mes = colunas.indexOf("MES_EXERCICIO");
        int ano = colunas.indexOf("ANO_EXERCICIO");
        int poder = colunas.indexOf("NOM_PODER_ORCAMENTO");
        int orgao = colunas.indexOf("NOM_ORGAO_ORCAMENTO");
        int pago = colunas.indexOf("VAL_PAGO");
        if (mes < 0 || ano < 0 || poder < 0 || orgao < 0 || pago < 0) {
            throw new IOException("colunas esperadas ausentes no cabeçalho: " + cabecalho);
        }
        String linha;
        while ((linha = leitor.readLine()) != null) {
            List<String> c = campos(linha);
            if (c.size() != colunas.size() || !"Executivo".equals(c.get(poder))) {
                continue;
            }
            BigDecimal v = ExecutivoRs.decimal(c.get(pago));
            if (v == null || v.signum() == 0 || !c.get(ano).matches("\\d{4}") || !c.get(mes).matches("\\d{1,2}")
                    || Integer.parseInt(c.get(ano)) < ANO_INICIAL) {
                continue;
            }
            String chave = c.get(ano) + "|" + String.format("%02d", Integer.parseInt(c.get(mes))) + "|" + c.get(orgao).strip();
            valor.merge(chave, v, BigDecimal::add);
            linhas.merge(chave, 1, Integer::sum);
        }
    }

    /** Campos de uma linha com todos os valores entre aspas ("1";"2013";"Legislativo"). */
    static List<String> campos(String linha) {
        String t = linha.strip();
        if (t.startsWith("\"")) {
            t = t.substring(1);
        }
        if (t.endsWith("\"")) {
            t = t.substring(0, t.length() - 1);
        }
        return List.of(t.split("\";\"", -1));
    }
}
