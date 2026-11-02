package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Coleta.Politica;
import etl.Registros.Prov;

class AgendaTest {

    private static final Agenda.Regra VIGENTE = new Agenda.Regra(LocalDate.of(2023, 1, 1), LocalDate.of(2027, 1, 5),
            "https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica-lula/agenda-do-presidente-da-republica", false);
    private static final Agenda.Regra ANTERIOR = new Agenda.Regra(LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1),
            "https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica", true);
    private static final Prov PROV = new Prov(Agenda.FONTE_ID, "https://exemplo.invalid/dia", Instant.parse("2026-11-02T00:00:00Z"));

    @Test
    void leOsCompromissosDaPaginaDoDia() {
        String html = """
                <ul class="list-compromissos">
                <li class="item-compromisso-wrapper"><div class="item-compromisso">
                  <div class="horario"><time class="compromisso-inicio">09h30</time></div>
                  <div class="compromisso-dados"><h2 class="compromisso-titulo toggle closed">Ministro das Rela&ccedil;&otilde;es Exteriores, Mauro Vieira</h2>
                  <div class="compromisso-collapse"><div class="compromisso-participantes"><label>Participante(s)</label>
                  <ul><li> Assessor-Chefe, Embaixador Celso Amorim, </li><li> Chefe do Cerimonial, Fernando Igreja </li></ul></div></div>
                  <div class="compromisso-footer"><div class="compromisso-local">Pal&aacute;cio da Alvorada</div></div></div></div></li>
                <li class="item-compromisso-wrapper"><div class="item-compromisso">
                  <div class="horario"><time class="compromisso-inicio">14h30</time> - <time class="compromisso-fim">15h00</time></div>
                  <div class="compromisso-dados"><h2 class="compromisso-titulo">\u200bReuni&atilde;o com ministros</h2>
                  <div class="compromisso-footer"><div class="compromisso-local">Pal&aacute;cio do Planalto</div></div></div></div></li>
                </ul>
                """;
        List<Agenda.Compromisso> lista = Agenda.compromissos(html, LocalDate.of(2026, 10, 29), null, PROV);
        assertEquals(2, lista.size());
        Agenda.Compromisso primeiro = lista.getFirst();
        assertEquals("2026-10-29", primeiro.data());
        assertEquals("09:30", primeiro.hora());
        assertEquals("Ministro das Relações Exteriores, Mauro Vieira", primeiro.textoOficial());
        assertEquals("Palácio da Alvorada", primeiro.local());
        assertEquals(List.of("Assessor-Chefe, Embaixador Celso Amorim", "Chefe do Cerimonial, Fernando Igreja"), primeiro.participantes());
        assertNull(primeiro.snapshotUrl());
        assertEquals("14:30", lista.get(1).hora());
        assertEquals("Reunião com ministros", lista.get(1).textoOficial());
        assertEquals(List.of(), lista.get(1).participantes());
    }

    @Test
    void diasEEnderecosDoMandato() {
        LocalDate hoje = LocalDate.of(2026, 11, 2);
        List<LocalDate> dias = Agenda.dias(VIGENTE, hoje);
        assertEquals(LocalDate.of(2023, 1, 1), dias.getFirst());
        assertEquals(hoje, dias.getLast());
        List<LocalDate> anteriores = Agenda.dias(ANTERIOR, hoje);
        assertEquals(LocalDate.of(2022, 12, 31), anteriores.getLast());
        assertEquals(365, anteriores.size());
        assertEquals(VIGENTE.base() + "/2026-10-29", Agenda.urlDoDia(VIGENTE, LocalDate.of(2026, 10, 29)));
        assertEquals("https://web.archive.org/web/20220615120000id_/" + ANTERIOR.base() + "/2022-06-15",
                Agenda.urlNoArquivo(ANTERIOR, LocalDate.of(2022, 6, 15)));
        assertEquals(Politica.SE_AUSENTE, Agenda.politica(LocalDate.of(2026, 9, 1), hoje));
        assertEquals(Politica.SEMPRE, Agenda.politica(LocalDate.of(2026, 10, 25), hoje));
    }
}
