package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Publicador.Perfil;

/** Mandato da casa já encerrado não deixa o perfil em exercício, mesmo com a casa listando a pessoa como ativa (ADR-0071). */
class LegislaturaEncerradaTest {

    private static final String PESSOAS = """
            [{"fonte": "alems", "id_fonte": "1", "nome": "Fulana de Tal", "cargo": "deputado_estadual",
              "esfera": "estadual", "ente": "MS", "uf": "MS", "partido": "AAA", "situacao": "Em exercício",
              "em_exercicio": true, "links": []}]
            """;

    private static final String BLOCOS = """
            {"mandatos": [
              {"cargo": "deputado_estadual", "esfera": "estadual", "ente": "MS", "uf": "MS", "inicio": "2023-02-01",
               "fim": "2027-01-31", "situacao": "Em exercício", "legislatura": 12, "eleicao": 2022,
               "fonte_id": "alems-deputados", "url_origem": "https://exemplo.gov.br/deputados",
               "capturado_em": "2026-11-05T10:00:00Z"}
            ]}
            """;

    @Test
    void antesDoFimSegueEmExercicio(@TempDir Path tmp) throws IOException {
        Perfil p = perfil(tmp, "2027-01-31T12:00:00Z");

        assertTrue(p.emExercicio);
        assertEquals("Em exercício", p.situacao);
    }

    @Test
    void depoisDoFimSaiDeEmExercicio(@TempDir Path tmp) throws IOException {
        Perfil p = perfil(tmp, "2027-02-02T12:00:00Z");

        assertFalse(p.emExercicio);
        assertEquals("Mandato encerrado em 2027-01-31", p.situacao);
    }

    @Test
    void naAssembleiaEsperaAPosseDosSucessores(@TempDir Path tmp) throws IOException {
        // A fonte encerra o mandato em 2026-12-31, mas a posse seguinte na UF é em 2027-02-01.
        Files.createDirectories(tmp.resolve("contrib"));
        Files.writeString(tmp.resolve("contrib").resolve("mandatos.yaml"), """
                - cargo: deputado_estadual
                  ente: MS
                  eleicao: 2026
                  inicio: 2027-02-01
                  fim: 2031-02-01
                """);
        String blocos = BLOCOS.replace("2027-01-31", "2026-12-31");

        Perfil emJaneiro = perfil(tmp.resolve("jan"), "2027-01-20T12:00:00Z", blocos, tmp.resolve("contrib"));
        Perfil depoisDaPosse = perfil(tmp.resolve("fev"), "2027-02-01T12:00:00Z", blocos, tmp.resolve("contrib"));

        assertTrue(emJaneiro.emExercicio);
        assertFalse(depoisDaPosse.emExercicio);
        assertEquals("Mandato encerrado em 2026-12-31", depoisDaPosse.situacao);
    }

    @Test
    void posseSeguinteEAPrimeiraDepoisDoFim() {
        List<LocalDate> posses = List.of(LocalDate.of(2031, 2, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2023, 2, 1));

        assertEquals(LocalDate.of(2027, 2, 1), Vinculo.posseSeguinte(posses, LocalDate.of(2026, 12, 31)));
        assertNull(Vinculo.posseSeguinte(null, LocalDate.of(2026, 12, 31)));
        assertNull(Vinculo.posseSeguinte(List.of(LocalDate.of(2027, 1, 31)), LocalDate.of(2027, 1, 31)));
    }

    @Test
    void mandatoSemFimOuListaVaziaNaoDecidem() {
        LocalDate hoje = LocalDate.of(2027, 2, 2);

        assertNull(Vinculo.fimSeTodosEncerrados(Json.FONTE.readTree("[]"), hoje));
        assertNull(Vinculo.fimSeTodosEncerrados(Json.FONTE.readTree(
                "[{\"fim\": \"2023-01-31\"}, {\"inicio\": \"2023-02-01\"}]"), hoje));
        assertNull(Vinculo.fimSeTodosEncerrados(Json.FONTE.readTree(
                "[{\"fim\": \"2023-01-31\"}, {\"fim\": \"2031-01-31\"}]"), hoje));
        assertEquals(LocalDate.of(2027, 1, 31), Vinculo.fimSeTodosEncerrados(Json.FONTE.readTree(
                "[{\"fim\": \"2023-01-31\"}, {\"fim\": \"2027-01-31\"}]"), hoje));
    }

    private static Perfil perfil(Path tmp, String agora) throws IOException {
        return perfil(tmp, agora, BLOCOS, null);
    }

    private static Perfil perfil(Path tmp, String agora, String blocos, Path contrib) throws IOException {
        if (contrib != null) {
            Files.createDirectories(tmp);
            Files.createSymbolicLink(tmp.resolve("contrib"), contrib);
        }
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse(agora), null);
        Path pasta = ctx.normalizado().resolve("alems");
        Files.createDirectories(pasta.resolve("blocos"));
        Files.writeString(pasta.resolve("pessoas.json"), PESSOAS);
        Files.writeString(pasta.resolve("blocos").resolve("1.json"), blocos);
        List<Perfil> perfis = Vinculo.legisladores(ctx, "alems", "alems", "alems");
        assertEquals(1, perfis.size());
        return perfis.getFirst();
    }
}
