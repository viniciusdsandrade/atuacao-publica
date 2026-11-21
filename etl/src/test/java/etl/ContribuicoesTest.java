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
}
