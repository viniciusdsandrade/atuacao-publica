package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Coleta;
import etl.Json;
import etl.Registros.Prov;
import etl.fontes.ExecutivoSp.Agregado;
import etl.fontes.ExecutivoSp.Ato;

/** Decretos e execução orçamentária de Goiás (ADR-0076). Fixtures minimizadas das respostas de 2026-11-21. */
class ExecutivoGoTest {

    @Test
    void decretoNumeradoSemPontoDeMilharNoIdentificador() {
        String pagina = """
                {"total_resultados": 2, "resultados": [
                  {"id": 113696, "ano": 2026, "numero": "10.891", "ementa": "Altera o Decreto nº 10.218.",
                   "data_legislacao": "31/03/2026", "tipo_legislacao": {"id": 3, "nome": "Decreto Numerado"}},
                  {"id": 1, "ano": 2026, "numero": "5", "ementa": "Orçamentário.", "data_legislacao": "31/03/2026",
                   "tipo_legislacao": {"id": 5, "nome": "Decreto Orçamentário"}}
                ]}
                """;
        Coleta.Arquivo arq = new Coleta.Arquivo(ExecutivoGo.urlDecretos(2026, 1), "raw/executivogo/decretos/2026-1.json", "abc", 10L,
                Instant.parse("2026-11-21T12:00:00Z"));

        List<Ato> atos = ExecutivoGo.decretos(Json.FONTE.readTree(pagina), arq);

        assertEquals(1, atos.size());
        assertEquals("go-dec-10891", atos.getFirst().idFonte());
        assertEquals("2026-03-31", atos.getFirst().data());
        assertEquals("Decreto nº 10.891", atos.getFirst().nome());
        assertEquals("GO", atos.getFirst().ente());
    }

    @Test
    void recursosDoPacoteDistinguemAnoFechadoEMes() {
        String pacote = """
                {"result": {"resources": [
                  {"name": "Execução Orçamentária Natureza Despesa - Março/2026", "url": "https://dadosabertos.go.gov.br/x/202603.csv"},
                  {"name": "Execução Orçamentária Natureza Despesa - 2025", "url": "https://dadosabertos.go.gov.br/x/2025.zip"},
                  {"name": "Execução Orçamentária Natureza Despesa - 2021", "url": "https://dadosabertos.go.gov.br/x/2021.zip"},
                  {"name": "Execucao Orcamentaria Natureza Despesa 2003 - 2025", "url": "https://dadosabertos.go.gov.br/x/todos.zip"},
                  {"name": "Dicionário de Dados", "url": "https://dadosabertos.go.gov.br/x/dic.pdf"}
                ]}}
                """;

        List<ExecutivoGo.Recurso> recursos = ExecutivoGo.recursos(Json.FONTE.readTree(pacote));

        // O ZIP de 2003 a 2025 também termina em "- 2025", mas somaria 2025 duas vezes: fica de fora.
        assertEquals(List.of(
                new ExecutivoGo.Recurso(2025, 0, "https://dadosabertos.go.gov.br/x/2025.zip"),
                new ExecutivoGo.Recurso(2026, 3, "https://dadosabertos.go.gov.br/x/202603.csv")), recursos);
    }

    @Test
    void somaOValorPagoPorMesEOrgaoSemLinhaZerada() {
        String csv = "﻿CODG_CATEGORIA_ECONOMICA;NUMR_ANO_MES;NOME_ORGAO_REDUZIDO_ATUAL;VALOR_EMPENHO;VALOR_PAGO\n"
                + "3;202603;polícia militar;10.00;100.50\n"
                + "3;202603;polícia militar;0.00;20.25\n"
                + "3;202603;detran;5.00;0.00\n"
                + "3;202602;detran;0.00;7.00\n";
        ExecutivoGo.Somas somas = new ExecutivoGo.Somas();
        somas.ler(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                new Prov(ExecutivoGo.FONTE_DESPESA, "https://dadosabertos.go.gov.br/x/202603.csv", Instant.parse("2026-11-21T12:00:00Z")));

        List<Agregado> agregados = somas.agregados();

        assertEquals(2, agregados.size());
        assertEquals(2, agregados.get(0).mes());
        assertEquals("detran", agregados.get(0).categoria());
        assertEquals(new BigDecimal("120.75"), agregados.get(1).valor());
        assertEquals(2, agregados.get(1).quantidade());
    }
}
