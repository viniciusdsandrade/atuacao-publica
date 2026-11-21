package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Ligação do autor ao deputado pelo nome (ADR-0078). */
class AutoriaPorNomeTest {

    private final AutoriaPorNome autoria = new AutoriaPorNome(Map.of(
            "1", "Dr. Pedro Ricardo", "2", "Prof Josemar", "3", "Dr. Ana Souza", "4", "Delegada Ana Souza",
            "5", "Andreia M. Rezende", "6", "Dr. Yglésio"));

    @Test
    void nomeExatoSemAcentoESemPontuacao() {
        assertEquals("2", autoria.id("PROF. JOSEMAR"));
        assertEquals("6", autoria.id("Dr. Yglesio "));
    }

    @Test
    void semTratamentoSoQuandoUmUnicoDeputadoTemONome() {
        assertEquals("1", autoria.id("PEDRO RICARDO"));
        assertNull(autoria.id("ANA SOUZA"));
        assertEquals("1", autoria.id("DEPUTADO DR. PEDRO RICARDO"));
        assertEquals("2", autoria.id("DEPUTADO PROF JOSEMAR"));
    }

    @Test
    void palavraDoMeioAbreviada() {
        assertEquals("5", autoria.id("Andreia Martins Rezende"));
        assertTrue(AutoriaPorNome.abreviado("ANDREIA MARTINS REZENDE", "ANDREIA M REZENDE"));
        assertFalse(AutoriaPorNome.abreviado("ANDREIA MARTINS REZENDE", "ANDREIA MARTINS REZENDE"));
        assertFalse(AutoriaPorNome.abreviado("ANDREIA MARTINS REZENDE", "ANDREIA L REZENDE"));
        assertNull(autoria.id("Rafael"));
    }
}
