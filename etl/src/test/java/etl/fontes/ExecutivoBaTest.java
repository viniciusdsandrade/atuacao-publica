package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/** Despesa do Executivo da BA pelo painel de despesas (ADR-0083). Fixture minimizada do arquivo de 2026-11-21. */
class ExecutivoBaTest {

    @Test
    void somaOPagoDoExecutivoPorMesEOrgaoDesde2022() throws Exception {
        String csv = "﻿\"MES_EXERCICIO\";\"ANO_EXERCICIO\";\"NOM_PODER_ORCAMENTO\";\"NOM_ORGAO_ORCAMENTO\";\"VAL_EMPENHADO_TOTAL\";\"VAL_PAGO\"\n"
                + "\"3\";\"2026\";\"Executivo\";\"Secretaria da Saúde\";\"10,00\";\"1000,50\"\n"
                + "\"3\";\"2026\";\"Executivo\";\"Secretaria da Saúde\";\"10,00\";\"20,25\"\n"
                + "\"3\";\"2026\";\"Executivo\";\"Secretaria da Saúde\";\"10,00\";\"0,00\"\n"
                + "\"3\";\"2026\";\"Legislativo\";\"Assembleia Legislativa da Bahia\";\"10,00\";\"5,00\"\n"
                + "\"1\";\"2021\";\"Executivo\";\"Secretaria da Saúde\";\"10,00\";\"7,00\"\n";
        Map<String, BigDecimal> valor = new TreeMap<>();
        Map<String, Integer> linhas = new TreeMap<>();

        ExecutivoBa.somar(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), valor, linhas);

        assertEquals(Map.of("2026|03|Secretaria da Saúde", new BigDecimal("1020.75")), valor);
        assertEquals(2, linhas.get("2026|03|Secretaria da Saúde"));
        assertEquals(List.of("1", "2013", "Legislativo"), ExecutivoBa.campos("\"1\";\"2013\";\"Legislativo\""));
    }
}
