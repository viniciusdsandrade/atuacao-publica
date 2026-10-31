package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Json;
import etl.Registros.Despesa;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

/** Regras de dados da Câmara. Registros sintéticos no formato do arquivo anual de cotas. */
class CamaraTest {

    private static final Prov PROV = new Prov("camara-cotas", "https://www.camara.leg.br/cotas/Ano-2026.json.zip",
            Instant.parse("2026-10-30T03:00:00Z"));

    private static JsonNode cota(String fornecedor, String documento, String valor, String parcela) {
        return Json.FONTE.readTree("""
                {"idDeputado": 900001, "cpf": "12345678909", "passageiro": "PESSOA PASSAGEIRA", "ano": 2026, "mes": 3,
                 "descricao": "COMBUSTÍVEIS E LUBRIFICANTES.", "fornecedor": "%s", "cnpjCPF": "%s",
                 "numero": "288", "dataEmissao": "2026-03-26T00:00:00", "valorLiquido": "%s", "parcela": %s,
                 "idDocumento": 9000001, "numeroSubCota": 3, "urlDocumento": ""}
                """.formatted(fornecedor, documento, valor, parcela));
    }

    @Test
    void despesaDescartaCpfDoDeputadoDoFornecedorEDoNomeDeMei() {
        Despesa d = Camara.despesa(cota("FULANO DE TAL 12345678909", "123.456.789-09", "954.3", "0"), PROV);
        assertEquals("FULANO DE TAL", d.fornecedorNome());
        assertNull(d.fornecedorCnpj(), "CPF de fornecedor pessoa física não é publicado");
        assertNull(d.documentoUrl(), "URL vazia na fonte vira null");
        assertEquals(LocalDate.of(2026, 3, 26), d.data());
        String json = Json.SAIDA.writeValueAsString(d);
        assertFalse(json.contains("12345678909"));
        assertFalse(json.contains("PASSAGEIRA"));
    }

    @Test
    void despesaGuardaCnpjComCatorzeDigitos() {
        assertEquals("12345678000195", Camara.despesa(cota("POSTO EXEMPLO LTDA", "12.345.678/0001-95 ", "100", "0"), PROV).fornecedorCnpj());
    }

    @Test
    void deduplicaLancamentoRepetidoEMantemParcelasDiferentes() {
        JsonNode a = cota("POSTO EXEMPLO LTDA", "12.345.678/0001-95", "100", "0");
        JsonNode copia = cota("POSTO EXEMPLO LTDA", "12.345.678/0001-95", "100", "0");
        JsonNode outraParcela = cota("POSTO EXEMPLO LTDA", "12.345.678/0001-95", "100", "1");
        assertEquals(Camara.chaveDespesa(a), Camara.chaveDespesa(copia));
        assertNotEquals(Camara.chaveDespesa(a), Camara.chaveDespesa(outraParcela));
    }

    @Test
    void valorNegativoDeCompensacaoEntra() {
        assertEquals(-120, Camara.despesa(cota("CIA AEREA", "12.345.678/0001-95", "-120.00", "0"), PROV).valor().intValue());
    }

    @Test
    void intervalosDeExercicioIgnoramRegistroSemSituacaoEOutraLegislatura() {
        JsonNode historico = Json.FONTE.readTree("""
                [
                  {"dataHora": "2023-02-01T00:00", "situacao": null, "idLegislatura": 57},
                  {"dataHora": "2023-02-01T12:05", "situacao": "Exercício", "idLegislatura": 57},
                  {"dataHora": "2024-03-01T10:00", "situacao": "Licença", "idLegislatura": 57},
                  {"dataHora": "2024-07-01T10:00", "situacao": "Exercício", "idLegislatura": 57},
                  {"dataHora": "2019-02-01T11:45", "situacao": "Exercício", "idLegislatura": 56}
                ]
                """);
        List<Camara.Intervalo> intervalos = Camara.intervalos(historico,
                new Camara.Legislatura(57, LocalDate.of(2023, 2, 1), LocalDate.of(2027, 1, 31)));
        assertEquals(2, intervalos.size());
        assertTrue(intervalos.get(0).contem(LocalDateTime.of(2023, 6, 1, 15, 0)));
        assertFalse(intervalos.get(0).contem(LocalDateTime.of(2024, 5, 1, 15, 0)), "licença fica fora");
        assertTrue(intervalos.get(1).contem(LocalDateTime.of(2026, 10, 1, 15, 0)), "último intervalo fica aberto");
    }

    @Test
    void quemSaiuTemDataEDescricaoEQuemFicouAteOFimTemMandatoEncerrado() {
        Camara.Legislatura leg57 = new Camara.Legislatura(57, LocalDate.of(2023, 2, 1), LocalDate.of(2027, 1, 31));
        JsonNode historico = Json.FONTE.readTree("""
                [
                  {"dataHora": "2023-02-01T12:05", "situacao": "Exercício", "idLegislatura": 57},
                  {"dataHora": "2024-03-10T10:00", "situacao": "Afastado", "idLegislatura": 57,
                   "descricaoStatus": "Saída - Afastamento para investidura em cargo"}
                ]
                """);
        Camara.Intervalo saiu = Camara.intervalos(historico, leg57).getLast();
        assertEquals("Fora de exercício desde 2024-03-10 (Saída - Afastamento para investidura em cargo)",
                Camara.situacaoNaLegislatura(historico, leg57, saiu, LocalDate.of(2026, 10, 31)));

        Camara.Intervalo ateOFim = new Camara.Intervalo(LocalDateTime.of(2023, 2, 1, 12, 5), null);
        assertEquals("Mandato encerrado em 2027-01-31",
                Camara.situacaoNaLegislatura(historico, leg57, ateOFim, LocalDate.of(2027, 2, 2)));
        assertEquals("Fora de exercício", Camara.situacaoNaLegislatura(historico, leg57, ateOFim, LocalDate.of(2026, 10, 31)));
    }
}
