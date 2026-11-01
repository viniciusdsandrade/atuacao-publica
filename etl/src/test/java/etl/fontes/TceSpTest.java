package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Coleta.Politica;
import etl.Json;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class TceSpTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 1);

    @Test
    void mesesDoMandatoVaoDaPosseAteHoje() {
        List<YearMonth> meses = TceSp.meses(LocalDate.of(2025, 1, 1), LocalDate.of(2029, 1, 1), HOJE);
        assertEquals(YearMonth.of(2025, 1), meses.getFirst());
        assertEquals(YearMonth.of(2026, 11), meses.getLast());
        assertEquals(23, meses.size());
    }

    @Test
    void mandatoEncerradoParaNoUltimoMesDoMandato() {
        List<YearMonth> meses = TceSp.meses(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 1, 1), HOJE);
        assertEquals(YearMonth.of(2024, 12), meses.getLast());
        assertEquals(List.of(), TceSp.meses(LocalDate.of(2029, 1, 1), LocalDate.of(2033, 1, 1), HOJE));
    }

    @Test
    void mesesRecentesRenovamEAntigosFicamNoCache() {
        assertEquals(Politica.SEMANAL, TceSp.politica(YearMonth.of(2026, 9), HOJE));
        assertEquals(Politica.SE_AUSENTE, TceSp.politica(YearMonth.of(2025, 12), HOJE));
    }

    @Test
    void agregaPorOrgaoEEventoSemGuardarFornecedor() {
        JsonNode json = Json.FONTE.readTree("""
                [
                  {"orgao": "PREFEITURA MUNICIPAL DE X", "evento": "Valor Pago", "id_fornecedor": "PESSOA FÍSICA - 000001",
                   "nm_fornecedor": "Fulano", "vl_despesa": "1.050,99"},
                  {"orgao": "PREFEITURA MUNICIPAL DE X", "evento": "Valor Pago", "id_fornecedor": "CNPJ - 1", "vl_despesa": "49,01"},
                  {"orgao": "PREFEITURA MUNICIPAL DE X", "evento": "Empenhado", "id_fornecedor": "CNPJ - 1", "vl_despesa": "10,00"},
                  {"orgao": "CÂMARA MUNICIPAL DE X", "evento": "Valor Pago", "id_fornecedor": "CNPJ - 2", "vl_despesa": "5,00"},
                  {"orgao": "PREFEITURA MUNICIPAL DE X", "evento": "Valor Pago", "vl_despesa": "abc"}
                ]
                """);
        Prov prov = new Prov(TceSp.FONTE_ID, "https://exemplo.invalid/despesas/x/2026/06", Instant.parse("2026-11-01T00:00:00Z"));
        List<TceSp.Agregado> agregados = TceSp.agregar(json, "3509502", YearMonth.of(2026, 6), prov);
        assertEquals(3, agregados.size());
        TceSp.Agregado pago = agregados.stream()
                .filter(a -> a.orgao().startsWith("PREFEITURA") && a.evento().equals("Valor Pago")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1100.00"), pago.valor());
        assertEquals(2, pago.quantidade());
        assertEquals(6, pago.mes());
        assertEquals(-1, Json.arvore(agregados).toString().indexOf("Fulano"));
    }
}
