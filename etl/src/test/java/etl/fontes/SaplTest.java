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
    void registroDeVotacaoTrazResultadoEMateria() {
        JsonNode json = Json.FONTE.readTree("""
                {"id": 27012, "__str__": "Ordem: 1 - Emenda fls. 55 a 57 - Votação: Aprovado - Matéria: Emenda fls. 55 a 57",
                 "ordem": 15555, "expediente": null}
                """);
        Sapl.RegistroVotacao reg = Sapl.registro(json);
        assertEquals(27012, reg.id());
        assertEquals(15555, reg.ordem());
        assertEquals(null, reg.expediente());
        assertEquals("Aprovado", reg.resultado());
        assertEquals("Emenda fls. 55 a 57", reg.materia());
    }

    @Test
    void itemDaOrdemDoDiaDaDataEMateriaAoVoto() {
        JsonNode json = Json.FONTE.readTree("""
                {"id": 15555, "__str__": "1 - Projeto de Lei Ordinária nº 280 de 2009", "data_ordem": "2026-06-29"}
                """);
        Sapl.Item item = Sapl.item(json, LocalDate.of(2026, 6, 30));
        assertEquals(LocalDate.of(2026, 6, 29), item.data());
        assertEquals("Projeto de Lei Ordinária nº 280 de 2009", item.materia());
        JsonNode semData = Json.FONTE.readTree("{\"id\": 1, \"__str__\": \"2 - Moção nº 1 de 2026\", \"data_ordem\": null}");
        assertEquals(LocalDate.of(2026, 6, 30), Sapl.item(semData, LocalDate.of(2026, 6, 30)).data());
    }

    @Test
    void participacaoContaSoVotoRegistrado() {
        assertTrue(Sapl.registrado("Sim"));
        assertTrue(Sapl.registrado("Abstenção"));
        assertFalse(Sapl.registrado("Não Votou"));
        assertFalse(Sapl.registrado("Ausente"));
        assertFalse(Sapl.registrado(null));
    }

    @Test
    void sessoesFicamRestritasALegislaturaVigente() {
        JsonNode json = Json.FONTE.readTree("""
                [
                  {"id": 2106, "numero": 1, "data_inicio": "2025-01-01", "hora_inicio": "10:00", "tipo": "Solene", "legislatura_numero": 19},
                  {"id": 2000, "numero": 50, "data_inicio": "2024-12-10", "hora_inicio": "18:00", "tipo": "Ordinária", "legislatura_numero": 18}
                ]
                """);
        Map<Integer, Sapl.Sessao> sessoes = Sapl.sessoes(json, LEG19);
        assertEquals(1, sessoes.size());
        assertEquals(1, Sapl.realizadas(sessoes, HOJE).size());
        assertEquals(0, Sapl.realizadas(sessoes, LocalDate.of(2024, 12, 31)).size());
        assertEquals(etl.Coleta.Politica.SE_AUSENTE, Sapl.politicaDaSessao(sessoes.get(2106), HOJE));
        assertEquals(etl.Coleta.Politica.SEMANAL, Sapl.politicaDaSessao(sessoes.get(2106), LocalDate.of(2025, 2, 1)));
        assertEquals("2025-01-01T10:00", Sapl.instante(sessoes.get(2106)));
        assertEquals("1ª Sessão Solene", Sapl.tituloDaSessao(sessoes.get(2106)));
    }

    @Test
    void partidoNaDataSegueAsFiliacoes() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"__str__": "Nome - ANTIGO - Partido Antigo", "data": "2020-01-01", "data_desfiliacao": "2024-03-01"},
                    {"__str__": "Nome - PL - Partido Liberal", "data": "2024-03-02", "data_desfiliacao": null}
                  ]
                }
                """);
        List<Sapl.Filiacao> filiacoes = Sapl.filiacoes(json);
        assertEquals("ANTIGO", Sapl.partidoEm(filiacoes, LocalDate.of(2023, 6, 1)));
        assertEquals("PL", Sapl.partidoEm(filiacoes, LocalDate.of(2025, 6, 1)));
        assertEquals(null, Sapl.partidoEm(filiacoes, LocalDate.of(2019, 6, 1)));
    }

    @Test
    void filiacaoAtivaDeterminaPartido() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"__str__": "Nome - ANTIGO - Partido Antigo", "data": "2020-01-01", "data_desfiliacao": "2024-03-01"},
                    {"__str__": "Nome - PL - Partido Liberal", "data": "2024-03-02", "data_desfiliacao": null}
                  ]
                }
                """);
        assertEquals("PL", Sapl.extrairPartido(json, HOJE));
    }
}
