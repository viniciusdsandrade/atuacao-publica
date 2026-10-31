package etl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContribuicoesTest {

    @Test
    void correcaoDeRenunciaSubstituiSoOMandatoDoMesmoPeriodo() {
        assertTrue(Contribuicoes.sobrepoe("2023-01-01", "2025-04-02", "2023-01-01", "2027-01-06"));
        assertFalse(Contribuicoes.sobrepoe("2023-01-01", "2025-04-02", "2027-01-06", "2031-01-06"));
    }

    @Test
    void mandatoSemFimFicaAberto() {
        assertTrue(Contribuicoes.sobrepoe("2027-02-01", null, "2027-02-01", "2031-02-01"));
    }
}
