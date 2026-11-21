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

    @Test
    void decretoNumeradoDoLegislaBahia() {
        String html = "<tr><td><span> <a href=\"/documentos/decreto-no-23570-de-31-de-marco-de-2025\"><b class=\"clearfix\">"
                + "<a href=\"/documentos/decreto-no-23570-de-31-de-marco-de-2025\" hreflang=\"pt-br\">DECRETO Nº 23.570 DE 31 DE MARÇO DE 2025</a>"
                + "</b></a> Homologa o Decreto Municipal de &quot;Situação de Emergência” que indica. </span> </td></tr>"
                + "<tr><td><span><a href=\"/documentos/decreto-no-23001-de-1o-de-janeiro-de-2025\"><b class=\"clearfix\">"
                + "<a href=\"/documentos/decreto-no-23001-de-1o-de-janeiro-de-2025\" hreflang=\"pt-br\">DECRETO Nº 23.001 DE 1º DE JANEIRO DE 2025</a>"
                + "</b></a> Dispõe sobre o calendário. </span></td></tr>";

        List<ExecutivoSp.Ato> atos = ExecutivoBa.decretos(html, null);

        assertEquals(2, atos.size());
        assertEquals("ba-dec-23570", atos.get(0).idFonte());
        assertEquals("2025-03-31", atos.get(0).data());
        assertEquals("Decreto nº 23.570", atos.get(0).nome());
        assertEquals("Homologa o Decreto Municipal de \"Situação de Emergência” que indica.", atos.get(0).ementa());
        assertEquals("2025-01-01", atos.get(1).data());
    }
}
