package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import etl.Json;

/** Despesa do Executivo do RS (ADR-0082). Fixture minimizada do arquivo de julho de 2026. */
class ExecutivoRsTest {

    @Test
    void somaSoOPagamentoDoExecutivoPeloMesDaData() throws Exception {
        String csv = "Exercicio;Mes;FaseGasto;Favorecido;Poder;Orgao;Data;Valor\n"
                + "2026;7;Pagamento;Fulano;Poder Executivo;Secretaria da Saude;15/07/2026;1.000,50\n"
                + "2026;7;Pagamento;Beltrano;Poder Executivo;Secretaria da Saude;20/07/2026;-0,50\n"
                + "2026;7;Liquidação;Fulano;Poder Executivo;Secretaria da Saude;15/07/2026;999,00\n"
                + "2026;7;Pagamento;Fulano;Poder Judiciario;Tribunal de Justica;15/07/2026;5,00\n"
                + "2025;7;Pagamento;Ciclano;Poder Executivo;Secretaria da Educacao;02/07/2026;10,00\n"
                + "2022;1;Pagamento;Ciclano;PODER EXECUTIVO;SECRETARIA DA SAUDE;10/01/2022;4,00\n"
                // Ponto e vírgula a mais antes da data: a linha desalinha e fica de fora.
                + "2026;7;Pagamento;Empresa; Ltda;Poder Executivo;Secretaria da Saude;15/07/2026;3,00\n";
        Map<String, BigDecimal> valor = new TreeMap<>();
        Map<String, Integer> pagamentos = new TreeMap<>();

        int descartadas = ExecutivoRs.somar(new ByteArrayInputStream(csv.getBytes(StandardCharsets.ISO_8859_1)), valor, pagamentos);


        assertEquals(Map.of("2026|07|Secretaria da Saude", new BigDecimal("1000.00"), "2026|07|Secretaria da Educacao", new BigDecimal("10.00"),
                "2022|01|SECRETARIA DA SAUDE", new BigDecimal("4.00")), valor);
        assertEquals(2, pagamentos.get("2026|07|Secretaria da Saude"));
        assertEquals(0, descartadas);
    }

    @Test
    void mesPeloNomeDoArquivoEPacotesPorAno() {
        assertEquals(YearMonth.of(2023, 9), ExecutivoRs.mes("https://dados.rs.gov.br/x/download/gasto-rs-202309.zip"));
        assertNull(ExecutivoRs.mes("https://dados.rs.gov.br/x/download/dicionario.pdf"));
        List<String> pacotes = ExecutivoRs.pacotes(Json.FONTE.readTree("""
                {"result": {"results": [{"name": "2026-despesa-do-estado", "title": "2026 - Despesa do Estado"},
                  {"name": "despesas-do-estado-2022", "title": "2022 - Despesas do Estado"},
                  {"name": "despesas-do-estado-em-2021", "title": "2021 - Despesas do Estado"},
                  {"name": "diarias-do-estado", "title": "Diárias do Estado"}]}}
                """));
        assertEquals(List.of("2026-despesa-do-estado", "despesas-do-estado-2022"), pacotes);
        assertEquals(new BigDecimal("-776.76"), ExecutivoRs.decimal("-776,76"));
        // O "Abril" que baixa o arquivo de maio fica de fora: vale o "Maio".
        assertEquals(List.of("https://dados.rs.gov.br/dataset/x/resource/b/download/gasto-rs-202205.zip"),
                ExecutivoRs.arquivosMensais(Json.FONTE.readTree("""
                        {"result": {"resources": [
                          {"name": "Abril", "url": "https://dados.rs.gov.br/dataset/x/resource/a/download/gasto-rs-202205.zip"},
                          {"name": "Maio", "url": "https://dados.rs.gov.br/dataset/x/resource/b/download/gasto-rs-202205.zip"}]}}
                        """)));
    }
}
