package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etl.Json;
import tools.jackson.databind.JsonNode;

class SaplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 31);
    private static final Sapl.Legislatura LEG19 = new Sapl.Legislatura(19, LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31));

    @Test
    void parseDeLegislaturasIdentificaVigente() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"numero": 18, "data_inicio": "2021-01-01", "data_fim": "2024-12-31"},
                    {"numero": 19, "data_inicio": "2025-01-01", "data_fim": "2028-12-31"}
                  ]
                }
                """);
        List<Sapl.Legislatura> legs = Sapl.legislaturas(json);
        assertEquals(2, legs.size());
        assertEquals(19, Sapl.legislaturaVigente(legs, HOJE).id());
        assertEquals(18, Sapl.legislaturaVigente(legs, LocalDate.of(2022, 6, 1)).id());
    }

    @Test
    void extraiIdNumericoDeUrlOuNumero() {
        JsonNode node = Json.FONTE.readTree("""
                {"link": "https://sapl.campinas.sp.leg.br/api/parlamentares/parlamentar/101/", "direto": "202"}
                """);
        assertEquals("101", Sapl.extrairId(node, "link"));
        assertEquals("202", Sapl.extrairId(node, "direto"));
    }

    @Test
    void mandatosDiferenciamEmExercicioEQuemSaiu() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {
                      "parlamentar": "https://sapl.../parlamentar/10/",
                      "data_inicio_mandato": "2025-01-01",
                      "data_fim_mandato": "2028-12-31",
                      "titular": true,
                      "tipo_afastamento": null
                    },
                    {
                      "parlamentar": "20",
                      "data_inicio_mandato": "2025-01-01",
                      "data_fim_mandato": "2026-03-15",
                      "titular": false,
                      "tipo_afastamento": "Licença para assumir secretaria"
                    }
                  ]
                }
                """);
        Map<String, List<Sapl.MandatoSapl>> map = Sapl.mandatos(json, LEG19, HOJE);
        assertEquals(2, map.size());

        Sapl.MandatoSapl titular = map.get("10").getFirst();
        assertTrue(titular.emExercicio());
        assertEquals("Exercício", titular.situacao());
        assertTrue(titular.titular());

        Sapl.MandatoSapl suplente = map.get("20").getFirst();
        assertFalse(suplente.emExercicio());
        assertEquals("Fora de exercício (Licença para assumir secretaria)", suplente.situacao());
        assertFalse(suplente.titular());
    }

    @Test
    void filiacaoAtivaDeterminaPartido() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"sigla_partido": "ANTIGO", "data_filiacao": "2020-01-01", "data_desfiliacao": "2024-03-01"},
                    {"sigla_partido": "ATUAL", "data_filiacao": "2024-03-02", "data_desfiliacao": null}
                  ]
                }
                """);
        assertEquals("ATUAL", Sapl.extrairPartido(json, HOJE));
    }
}
