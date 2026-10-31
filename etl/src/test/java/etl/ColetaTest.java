package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    @Test
    void copiaSemanalRenovaUmaVezEmSeteDias() {
        Instant capturada = Instant.parse("2026-10-01T06:00:00Z");
        for (String caminho : List.of("deputados/1001.json", "deputados/1002.json", "frentes/1003.json")) {
            int renovacoes = 0;
            for (int dia = 0; dia < 7; dia++) {
                if (Coleta.vencida(caminho, capturada, capturada.plus(Duration.ofDays(dia)).plusSeconds(60))) {
                    renovacoes++;
                }
            }
            assertEquals(1, renovacoes, caminho);
        }
    }

    @Test
    void copiaSemanalComMaisDeUmaSemanaVence() {
        Instant agora = Instant.parse("2026-10-30T06:00:00Z");
        assertTrue(Coleta.vencida("deputados/1001.json", agora.minus(Duration.ofDays(8)), agora));
    }
}
