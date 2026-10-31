package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etl.Json;
import tools.jackson.databind.JsonNode;

/** Códigos e datas sintéticos. */
class SenadoTest {

    private static final LocalDate INICIO = LocalDate.of(2023, 2, 1);
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 31);

    private static JsonNode parlamentar(String codigo, String exercicios) {
        return Json.FONTE.readTree("""
                {"IdentificacaoParlamentar": {"CodigoParlamentar": "%s"},
                 "Mandatos": {"Mandato": {"Exercicios": %s}}}
                """.formatted(codigo, exercicios));
    }

    @Test
    void arquivoTemQuemExerceuDesde2023ENaoTemSuplenteQueNuncaAssumiu() {
        JsonNode atual = Json.FONTE.readTree("""
                {"IdentificacaoParlamentar": {"CodigoParlamentar": "1"}}
                """);
        JsonNode saiu = parlamentar("2", """
                {"Exercicio": [{"DataInicio": "2023-02-01", "DataFim": "2024-02-20", "DescricaoCausaAfastamento": "Renúncia"}]}""");
        JsonNode nuncaAssumiu = parlamentar("3", "null");
        JsonNode saiuAntes = parlamentar("4", """
                {"Exercicio": {"DataInicio": "2019-02-01", "DataFim": "2022-12-31"}}""");

        Map<String, JsonNode> membros = Senado.membros(List.of(atual), List.of(saiu, nuncaAssumiu, saiuAntes), INICIO, HOJE);

        assertEquals(List.of("1", "2"), List.copyOf(membros.keySet()));
        assertEquals("Fora de exercício desde 2024-02-20 (Renúncia)", Senado.situacaoDeQuemSaiu(saiu, INICIO, HOJE));
    }
}
