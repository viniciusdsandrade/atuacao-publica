package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Publicador.Perfil;

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

    private static final String SUCESSAO = """
            [{"politico_id": "tse-1", "esfera": "estadual", "cargo": "governador", "tipo": "mandato", "nivel_fonte": "T0",
              "entrada": {"tipo": "mandato", "data": "2026-04-03", "cargo": "governador", "inicio": "2026-04-03",
                "fim": "2027-01-06", "situacao": "Em exercício como governador desde 2026-04-03",
                "texto_oficial": "Termo de posse", "fonte_id": "ac-diario-oficial", "url_origem": "https://diario.ac.gov.br/x",
                "snapshot_url": "https://web.archive.org/web/2026/https://diario.ac.gov.br/x",
                "capturado_em": "2026-11-21T10:00:00Z", "contribuidor": "exemplo"}}]
            """;

    @Test
    void viceQueAssumiuOGovernoPassaAGovernadorEmExercicio(@TempDir Path tmp) throws IOException {
        Perfil durante = vice(tmp.resolve("a"), "2026-11-21T12:00:00Z");
        Perfil depois = vice(tmp.resolve("b"), "2027-01-07T12:00:00Z");

        assertEquals("governador", durante.cargo);
        assertTrue(durante.emExercicio);
        assertEquals("Em exercício como governador desde 2026-04-03", durante.situacao);
        assertEquals("AC", durante.uf);
        assertTrue(durante.cargos.contains("vice_governador"));
        // Passado o fim, o mandato assumido fica no histórico e o papel principal não muda.
        assertEquals("vice_governador", depois.cargo);
    }

    private static Perfil vice(Path tmp, String agora) throws IOException {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse(agora), null);
        Files.createDirectories(ctx.normalizado().resolve("contrib"));
        Files.writeString(ctx.normalizado().resolve("contrib").resolve("registros.json"), SUCESSAO);
        Perfil p = new Perfil();
        p.id = "tse-1";
        p.papel("vice_governador", "estadual", "AC", "AC", "AAA", "Mandato vigente (eleito em 2022), até 2027-01-06",
                Perfil.EM_EXERCICIO, LocalDate.of(2023, 1, 6));
        Contribuicoes.anexar(ctx, null, List.of(p), Map.of(), new ArrayList<>());
        return p;
    }

    @Test
    void renunciaComFonteT0SubstituiOMandatoDaRegra(@TempDir Path tmp) throws IOException {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse("2026-11-21T12:00:00Z"), null);
        Files.createDirectories(ctx.normalizado().resolve("contrib"));
        Files.writeString(ctx.normalizado().resolve("contrib").resolve("registros.json"), SUCESSAO
                .replace("\"inicio\": \"2026-04-03\"", "\"inicio\": \"2023-01-01\"")
                .replace("\"fim\": \"2027-01-06\"", "\"fim\": \"2026-04-03\"")
                .replace("Em exercício como governador desde 2026-04-03", "Renunciou em 2026-04-03"));
        Perfil p = new Perfil();
        p.id = "tse-1";
        p.papel("governador", "estadual", "AC", "AC", "AAA", "Deixou o cargo", Perfil.FORA_DE_EXERCICIO, LocalDate.of(2023, 1, 1));
        // Mandato da regra de contrib/mandatos.yaml, com o fim no prazo de renúncia (ADR-0033): também traz contribuidor.
        p.bloco("mandatos").add(Json.FONTE.readTree("""
                {"cargo": "governador", "inicio": "2023-01-01", "fim": "2026-04-04", "eleicao": 2022, "contribuidor": "exemplo"}
                """));
        p.bloco("mandatos").add(Json.FONTE.readTree("""
                {"cargo": "governador", "inicio": "2019-01-01", "fim": "2023-01-01", "eleicao": 2018, "contribuidor": "exemplo"}
                """));

        Contribuicoes.anexar(ctx, null, List.of(p), Map.of(), new ArrayList<>());

        assertEquals(2, p.bloco("mandatos").size());
        assertTrue(p.bloco("mandatos").stream().anyMatch(m -> "2026-04-03".equals(Json.texto(m, "fim"))));
        assertTrue(p.bloco("mandatos").stream().anyMatch(m -> "2023-01-01".equals(Json.texto(m, "fim"))));
        assertEquals("Renunciou em 2026-04-03", p.situacao);
        assertFalse(p.emExercicio);
    }

    @Test
    void sucessaoEncerradaNaoMudaQuemTomouPosseDoMesmoCargoPorEleicao(@TempDir Path tmp) throws IOException {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse("2027-01-07T12:00:00Z"), null);
        Files.createDirectories(ctx.normalizado().resolve("contrib"));
        Files.writeString(ctx.normalizado().resolve("contrib").resolve("registros.json"), SUCESSAO);
        Perfil p = new Perfil();
        p.id = "tse-1";
        p.papel("vice_governador", "estadual", "AC", "AC", "AAA", "Mandato encerrado em 2027-01-06", Perfil.FORA_DE_EXERCICIO,
                LocalDate.of(2023, 1, 1));
        p.papel("governador", "estadual", "AC", "AC", "AAA", "Mandato vigente (eleito em 2026)", Perfil.EM_EXERCICIO,
                LocalDate.of(2027, 1, 6));
        p.bloco("mandatos").add(Json.FONTE.readTree("""
                {"cargo": "governador", "inicio": "2027-01-06", "fim": "2031-01-06", "eleicao": 2026, "contribuidor": "exemplo"}
                """));

        Contribuicoes.anexar(ctx, null, List.of(p), Map.of(), new ArrayList<>());

        assertEquals("governador", p.cargo);
        assertTrue(p.emExercicio);
        assertEquals("Mandato vigente (eleito em 2026)", p.situacao);
        assertEquals(2, p.bloco("mandatos").size());
    }
}
