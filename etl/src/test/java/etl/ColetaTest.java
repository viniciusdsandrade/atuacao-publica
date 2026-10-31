package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import etl.Coleta.Politica;

class ColetaTest {

    @Test
    void anoAnteriorFicaAbertoAteMarco() {
        assertEquals(Politica.SEMPRE, Politica.porAno(2025, LocalDate.of(2026, 3, 31)));
        assertEquals(Politica.SE_AUSENTE, Politica.porAno(2025, LocalDate.of(2026, 4, 1)));
    }

    @Test
    void anoCorrenteFicaAbertoEAnosAntigosFicamFechados() {
        assertEquals(Politica.SEMPRE, Politica.porAno(2026, LocalDate.of(2026, 1, 1)));
        assertEquals(Politica.SE_AUSENTE, Politica.porAno(2024, LocalDate.of(2026, 1, 1)));
    }
}
