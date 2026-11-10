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
    void requisicaoQueJaEsperavaNoClienteNaoSaiDepoisDoOrcamento() {
        Http http = new Http("teste/0", 1);
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), http, null, Instant.now(), null);
        new Coleta(ctx, "teste", Duration.ZERO);
        // Requisição que passou por obter antes do fim do orçamento e esperava vaga: desiste sem ir à rede.
        IOException erro = assertThrows(IOException.class,
                () -> http.baixar("https://exemplo.invalid/a", tmp.resolve("a.json"), "application/json"));
        assertTrue(erro instanceof Http.PrazoEsgotado);
        assertEquals("nenhuma", http.tentativasFalhas());
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
    void marcadorVazioDeRecursoInexistenteValeComoCache() throws Exception {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), new Http("teste/0", 1), null, Instant.now(), null);
        Path arquivo = ctx.raw().resolve("teste").resolve("dias").resolve("2026-01-01.html");
        java.nio.file.Files.createDirectories(arquivo.getParent());
        java.nio.file.Files.write(arquivo, new byte[0]);
        Coleta.Arquivo vazio = new Coleta.Arquivo("https://exemplo.invalid/dias/2026-01-01", "raw/teste/dias/2026-01-01.html", "", 0,
                Instant.now());
        Json.gravar(arquivo.resolveSibling("2026-01-01.html.meta.json"), vazio, true);
        Coleta coleta = new Coleta(ctx, "teste", Duration.ZERO);
        // Dia sem agenda já conhecido: não volta à rede nem conta como falha.
        assertEquals(vazio, coleta.obterOuVazio("https://exemplo.invalid/dias/2026-01-01", "dias/2026-01-01.html", "text/html",
                Politica.SE_AUSENTE));
        assertTrue(coleta.reservasUsadas().isEmpty());
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
    void paginasDaMesmaListaRenovamNoMesmoDia() throws Exception {
        // Lista que desloca: com um dia por página, o item que muda de página entre duas renovações some.
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), new Http("teste/0", 1), null, Instant.parse("2026-10-01T06:00:00Z"), null);
        List<String> paginas = List.of("proposicoes/PL-2026-1.json", "proposicoes/PL-2026-2.json", "proposicoes/IND-2026-82.json");
        for (int dia = 0; dia < 7; dia++) {
            Instant agora = Instant.parse("2026-10-01T06:00:00Z").plus(Duration.ofDays(dia)).plusSeconds(60);
            java.util.Set<Boolean> vencidas = new java.util.HashSet<>();
            for (String pagina : paginas) {
                vencidas.add(Coleta.vencida(Coleta.pasta(pagina), Instant.parse("2026-10-01T06:00:00Z"), agora));
            }
            assertEquals(1, vencidas.size(), "dia " + dia);
        }
        assertEquals(Politica.SEMANAL_EM_LISTA, Politica.porAnoSemanalEmLista(2026, LocalDate.of(2026, 11, 10)));
        assertEquals(Politica.SE_AUSENTE, Politica.porAnoSemanalEmLista(2024, LocalDate.of(2026, 11, 10)));
    }

    @Test
    void copiaSemanalComMaisDeUmaSemanaVence() {
        Instant agora = Instant.parse("2026-10-30T06:00:00Z");
        assertTrue(Coleta.vencida("deputados/1001.json", agora.minus(Duration.ofDays(8)), agora));
    }
}
