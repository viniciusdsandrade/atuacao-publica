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
        List<Vinculo.Posse> posses = List.of(new Vinculo.Posse(LocalDate.of(2031, 2, 1), 2030),
                new Vinculo.Posse(LocalDate.of(2027, 2, 1), 2026), new Vinculo.Posse(LocalDate.of(2023, 2, 1), 2022));

        assertEquals(LocalDate.of(2027, 2, 1), Vinculo.posseSeguinte(posses, LocalDate.of(2026, 12, 31)));
        assertNull(Vinculo.posseSeguinte(null, LocalDate.of(2026, 12, 31)));
        assertNull(Vinculo.posseSeguinte(List.of(new Vinculo.Posse(LocalDate.of(2027, 1, 31), 2026)), LocalDate.of(2027, 1, 31)));
    }

    @Test
    void aPosseDosSucessoresEncerraOMandatoAntesDoFimDaFonte(@TempDir Path tmp) throws IOException {
        // Como na CLDF: a fonte encerra a legislatura em 2027-01-31, e a posse dos eleitos em 2026 é em 2027-01-06.
        Files.createDirectories(tmp.resolve("contrib"));
        Files.writeString(tmp.resolve("contrib").resolve("mandatos.yaml"), """
                - cargo: deputado_estadual
                  ente: MS
                  eleicao: 2026
                  inicio: 2027-01-06
                  fim: 2031-01-06
                """);

        Perfil naVespera = perfil(tmp.resolve("v"), "2027-01-05T12:00:00Z", BLOCOS, tmp.resolve("contrib"));
        Perfil naPosse = perfil(tmp.resolve("p"), "2027-01-06T12:00:00Z", BLOCOS, tmp.resolve("contrib"));

        assertTrue(naVespera.emExercicio);
        assertFalse(naPosse.emExercicio);
        assertEquals("Mandato encerrado em 2027-01-06", naPosse.situacao);
    }

    @Test
    void posseDosSucessoresSoContaEleicaoPosteriorAosMandatos() {
        List<Vinculo.Posse> posses = List.of(new Vinculo.Posse(LocalDate.of(2023, 1, 6), 2022),
                new Vinculo.Posse(LocalDate.of(2027, 1, 6), 2026));
        LocalDate hoje = LocalDate.of(2027, 1, 10);

        assertEquals(LocalDate.of(2027, 1, 6), Vinculo.posseDosSucessores(posses,
                Json.FONTE.readTree("[{\"eleicao\": 2022, \"fim\": \"2027-01-31\"}]"), hoje));
        // O mandato da eleição de 2026, já coberto pela fonte, não se encerra com a posse da própria eleição.
        assertNull(Vinculo.posseDosSucessores(posses,
                Json.FONTE.readTree("[{\"eleicao\": 2022}, {\"eleicao\": 2026}]"), hoje));
        assertNull(Vinculo.posseDosSucessores(posses, Json.FONTE.readTree("[{\"fim\": \"2027-01-31\"}]"), hoje));
        assertNull(Vinculo.posseDosSucessores(null, Json.FONTE.readTree("[{\"eleicao\": 2022}]"), hoje));
        assertNull(Vinculo.posseDosSucessores(posses, Json.FONTE.readTree("[{\"eleicao\": 2022}]"), LocalDate.of(2027, 1, 5)));
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
