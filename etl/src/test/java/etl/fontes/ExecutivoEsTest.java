package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Registros.Prov;
import etl.fontes.ExecutivoSp.Compromisso;

/** Agenda do Governador do Espírito Santo em iCalendar (ADR-0077). Fixture minimizada do calendário de 2026-11-21. */
class ExecutivoEsTest {

    private static final Prov PROV = new Prov(ExecutivoEs.FONTE_AGENDA, ExecutivoEs.URL_AGENDA, Instant.parse("2026-11-21T12:00:00Z"));

    private static final String ICS = String.join("\r\n",
            "BEGIN:VCALENDAR",
            "X-WR-CALNAME:Agenda do Governador",
            "X-WR-TIMEZONE:America/Sao_Paulo",
            "BEGIN:VEVENT",
            "DTSTART:20260402T170000Z",
            "LOCATION:VITÓRIA",
            "SUMMARY:Leitura da Carta de Renúncia e Posse do Vice-Governador Ricardo Ferraço como Gov",
            " ernador\\, na Assembleia.",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20260415",
            "SUMMARY:Agendas Internas.",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART:20260403T100000Z",
            "SUMMARY:Feriado Nacional",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART:20211230T130000Z",
            "SUMMARY:Antes da cobertura",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART:20260410T130000Z",
            "STATUS:CANCELLED",
            "SUMMARY:Cancelado",
            "END:VEVENT",
            "END:VCALENDAR");

    @Test
    void compromissoComHoraNoFusoDoCalendarioELinhaDobrada() {
        List<Compromisso> lista = ExecutivoEs.compromissos(ICS, PROV);

        assertEquals(2, lista.size());
        Compromisso posse = lista.get(0);
        assertEquals("2026-04-02", posse.data());
        assertEquals("14:00", posse.hora());
        assertEquals("Leitura da Carta de Renúncia e Posse do Vice-Governador Ricardo Ferraço como Governador, na Assembleia.",
                posse.textoOficial());
        assertEquals("VITÓRIA", posse.local());
        assertEquals("ES", posse.ente());
        Compromisso diaInteiro = lista.get(1);
        assertEquals("2026-04-15", diaInteiro.data());
        assertNull(diaInteiro.hora());
    }

    @Test
    void semFusoDeclaradoAHoraEmUtcFicaDeFora() {
        List<Compromisso> lista = ExecutivoEs.compromissos(ICS.replace("X-WR-TIMEZONE:America/Sao_Paulo\r\n", ""), PROV);

        assertNull(lista.getFirst().hora());
    }
}
