package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta.Politica;

class ColetaTest {

    @TempDir
    Path tmp;

    @Test
    void orcamentoEsgotadoNaoVaiARedeEDeixaAFonteParcial() throws Exception {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), new Http("teste/0", 1), null, Instant.now(), null);
        Coleta coleta = new Coleta(ctx, "teste", Duration.ZERO);
        assertTrue(coleta.orcamentoEsgotado());
        assertNull(coleta.obterOpcional("https://exemplo.invalid/a", "grupo/a.json", "application/json", Politica.SEMPRE));
        assertThrows(IOException.class,
                () -> coleta.obter("https://exemplo.invalid/b", "grupo/b.json", "application/json", Politica.SEMPRE));
        assertEquals(1, coleta.reservasUsadas().size());
        assertTrue(coleta.reservasUsadas().getFirst().contains("orçamento de tempo"));
    }

    @Test
    void copiaDeOutraUrlNoMesmoCaminhoNaoValeComoCache() throws Exception {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), new Http("teste/0", 1), null, Instant.now(), null);
        Path arquivo = ctx.raw().resolve("teste").resolve("grupo").resolve("a.json");
        java.nio.file.Files.createDirectories(arquivo.getParent());
        java.nio.file.Files.writeString(arquivo, "{}");
        Coleta.Arquivo copia = new Coleta.Arquivo("https://exemplo.invalid/a?legislatura=19", "raw/teste/grupo/a.json", "x", 2,
                Instant.now());
        Json.gravar(arquivo.resolveSibling("a.json.meta.json"), copia, true);
        Coleta coleta = new Coleta(ctx, "teste", Duration.ZERO);
        assertEquals(copia, coleta.obterOpcional("https://exemplo.invalid/a?legislatura=19", "grupo/a.json", "application/json",
                Politica.SE_AUSENTE));
        assertNull(coleta.obterOpcional("https://exemplo.invalid/a?legislatura=18", "grupo/a.json", "application/json",
                Politica.SE_AUSENTE));
    }

    @Test
    void semOrcamentoNadaMuda() {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), new Http("teste/0", 1), null, Instant.now(), null);
        Coleta coleta = new Coleta(ctx, "teste", null);
        assertTrue(!coleta.orcamentoEsgotado());
        coleta.falhaParcial("deputados", "123: HTTP 504");
        assertEquals(1, coleta.reservasUsadas().size());
        assertTrue(coleta.reservasUsadas().getFirst().startsWith("deputados: 1 arquivo(s)"));
    }

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
