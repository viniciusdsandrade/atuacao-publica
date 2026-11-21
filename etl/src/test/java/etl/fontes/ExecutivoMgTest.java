package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Coleta;
import etl.Json;
import etl.Registros.Prov;
import etl.fontes.ExecutivoSp.Ato;
import etl.fontes.ExecutivoSp.Compromisso;

/** Agenda do governador e decretos de Minas Gerais (ADR-0075). Fixtures minimizadas das respostas de 2026-11-21. */
class ExecutivoMgTest {

    private static final Prov PROV = new Prov(ExecutivoMg.FONTE_AGENDA, "https://www.agenciaminas.mg.gov.br/api/governorSchedule",
            Instant.parse("2026-11-21T12:00:00Z"));

    @Test
    void compromissoTemTituloDescricaoLocalEHoraPublicada() {
        String pagina = """
                {"results": [
                  {"id": 1, "title": "Reunião", "date": "2026-03-24T13:00:00.000Z", "publish": 1,
                   "body": "<p><sub>Participa de reuni&atilde;o com o presidente da Assembleia.</sub></p>\\r\\n<p><sub>Belo Horizonte (MG)</sub></p>\\r\\n<p><strong><sub>Fechado para a imprensa.</sub></strong></p>"},
                  {"id": 2, "title": "Reunião Solene - Posse", "date": "2023-01-01T09:00:00.000Z", "publish": 1,
                   "body": "<p>&nbsp;</p><p><strong>Local:</strong></p><p>Assembleia Legislativa de Minas Gerais (ALMG)</p><p>Belo Horizonte (MG)</p>"},
                  {"id": 3, "title": "Sem agenda oficial", "date": "2023-01-02T09:00:00.000Z", "publish": 1,
                   "body": "<p>Não há compromissos públicos confirmados até o momento.</p>"}
                ], "total": 7523}
                """;

        List<Compromisso> lista = ExecutivoMg.compromissos(Json.FONTE.readTree(pagina), PROV);

        assertEquals(2, lista.size());
        Compromisso reuniao = lista.get(0);
        assertEquals("MG", reuniao.ente());
        assertEquals("2026-03-24", reuniao.data());
        assertEquals("13:00", reuniao.hora());
        assertEquals("Reunião. Participa de reunião com o presidente da Assembleia.", reuniao.textoOficial());
        assertEquals("Belo Horizonte (MG)", reuniao.local());
        Compromisso posse = lista.get(1);
        assertEquals("Reunião Solene - Posse", posse.textoOficial());
        assertEquals("Assembleia Legislativa de Minas Gerais (ALMG), Belo Horizonte (MG)", posse.local());
    }

    @Test
    void decretoNumeradoDoExecutivoComLinkDaNorma() {
        String pagina = """
                {"resultado": {"noOcorrencias": 3, "listaItem": [
                  {"tipo": "DEC", "numero": "49320", "ano": "2026", "data": "20261114", "origem": "Executivo",
                   "ementa": "Altera o quantitativo e a distribuição de cargos de provimento em\\ncomissão."},
                  {"tipo": "DNE", "numero": null, "ano": "2026", "data": "20261114", "origem": "Executivo", "ementa": "Nomeia."},
                  {"tipo": "DEC", "numero": "48001", "ano": "2021", "data": "20211231", "origem": "Executivo", "ementa": "Antigo."}
                ]}}
                """;
        Coleta.Arquivo arq = new Coleta.Arquivo(ExecutivoMg.urlDecretos(2026, 1), "raw/executivomg/decretos/2026-1.json", "abc", 100L,
                Instant.parse("2026-11-21T12:00:00Z"));

        List<Ato> atos = ExecutivoMg.decretos(Json.FONTE.readTree(pagina), arq);

        assertEquals(1, atos.size());
        Ato ato = atos.getFirst();
        assertEquals("mg-dec-49320-2026", ato.idFonte());
        assertEquals("2026-11-14", ato.data());
        assertEquals("Decreto nº 49320", ato.nome());
        assertEquals("Altera o quantitativo e a distribuição de cargos de provimento em comissão.", ato.ementa());
        assertEquals("https://www.almg.gov.br/legislacao-mineira/texto/DEC/49320/2026/", ato.prov().urlOrigem());
        assertEquals(ExecutivoMg.FONTE_LEGISLACAO, ato.prov().fonteId());
    }

    @Test
    void consultaDaAgendaCobreOMesInteiro() {
        assertEquals("https://www.agenciaminas.mg.gov.br/api/governorSchedule?publish=1&page=2&limit=100&orderBy=asc"
                + "&orderByColumn=date&startDate=2024-02-01&endDate=2024-02-29", ExecutivoMg.urlAgenda(YearMonth.of(2024, 2), 2));
        assertNull(ExecutivoMg.limpar("<p>&nbsp;</p>"));
    }
}
