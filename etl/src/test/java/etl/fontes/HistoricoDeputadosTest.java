package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Contexto;
import etl.Registros.Prov;

class HistoricoDeputadosTest {

    private static final Instant T1 = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant T3 = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    void preservaQuemSaiDaListagemAtivaEMarcaRetornoQuandoVolta(@TempDir Path tempDir) {
        Contexto c1 = new Contexto(tempDir, tempDir.resolve("data"), null, null, T1, null);
        Prov prov1 = new Prov("teste-deputados", "https://exemplo.leg.br/deputados", T1);
        List<HistoricoDeputados.Snapshot> ativos1 = List.of(
                HistoricoDeputados.Snapshot.de("ana", "Ana Silva", "PSOL", "https://exemplo.leg.br/ana.jpg", "ana", prov1),
                HistoricoDeputados.Snapshot.de("bruno", "Bruno Souza", "MDB", "https://exemplo.leg.br/bruno.jpg", "bruno", prov1)
        );

        List<HistoricoDeputados.Entrada> res1 = HistoricoDeputados.mesclar(c1, "alesc", ativos1, prov1);
        assertEquals(2, res1.size());
        assertTrue(res1.stream().allMatch(HistoricoDeputados.Entrada::emExercicio));
        assertTrue(Files.exists(c1.raw().resolve("alesc").resolve(HistoricoDeputados.ARQUIVO)));

        // Semana 2: Ana sai da listagem ativa e Carlos assume a vaga.
        Contexto c2 = new Contexto(tempDir, tempDir.resolve("data"), null, null, T2, null);
        Prov prov2 = new Prov("teste-deputados", "https://exemplo.leg.br/deputados", T2);
        List<HistoricoDeputados.Snapshot> ativos2 = List.of(
                HistoricoDeputados.Snapshot.de("bruno", "Bruno Souza", "MDB", "https://exemplo.leg.br/bruno.jpg", "bruno", prov2),
                HistoricoDeputados.Snapshot.de("carlos", "Carlos Lima", "PT", "https://exemplo.leg.br/carlos.jpg", "carlos", prov2)
        );
        List<HistoricoDeputados.Entrada> res2 = HistoricoDeputados.mesclar(c2, "alesc", ativos2, prov2);
        Map<String, HistoricoDeputados.Entrada> mapa2 = res2.stream()
                .collect(Collectors.toMap(HistoricoDeputados.Entrada::id, e -> e));

        assertEquals(3, mapa2.size());
        assertFalse(mapa2.get("ana").emExercicio());
        assertEquals("Fora de exercício", mapa2.get("ana").situacao("Em exercício"));
        assertEquals("PSOL", mapa2.get("ana").partido());
        assertEquals(T1, mapa2.get("ana").provOu(prov2).capturadoEm());
        assertEquals(LocalDate.of(2026, 11, 1), mapa2.get("ana").ultimoVistoEm());
        assertTrue(mapa2.get("carlos").emExercicio());

        // Semana 3: Ana retorna ao exercício.
        Contexto c3 = new Contexto(tempDir, tempDir.resolve("data"), null, null, T3, null);
        Prov prov3 = new Prov("teste-deputados", "https://exemplo.leg.br/deputados", T3);
        List<HistoricoDeputados.Entrada> res3 = HistoricoDeputados.mesclar(c3, "alesc", List.of(
                HistoricoDeputados.Snapshot.de("ana", "Ana Silva", "PSOL", "https://exemplo.leg.br/ana.jpg", "ana", prov3),
                HistoricoDeputados.Snapshot.de("bruno", "Bruno Souza", "MDB", null, "bruno", prov3)
        ), prov3);
        Map<String, HistoricoDeputados.Entrada> mapa3 = res3.stream()
                .collect(Collectors.toMap(HistoricoDeputados.Entrada::id, e -> e));

        assertTrue(mapa3.get("ana").emExercicio());
        assertEquals("Em exercício", mapa3.get("ana").situacao("Em exercício"));
        assertEquals(LocalDate.of(2026, 11, 1), mapa3.get("ana").primeiroVistoEm());
        assertEquals(LocalDate.of(2026, 11, 21), mapa3.get("ana").ultimoVistoEm());
        // Mantém a foto anterior se uma renovação vier sem URL de foto.
        assertEquals("https://exemplo.leg.br/bruno.jpg", mapa3.get("bruno").fotoUrl());
        assertFalse(mapa3.get("carlos").emExercicio());
    }

    @Test
    void suplementaresEntramForaDeExercicioENaoApagamPartidoSalvo(@TempDir Path tempDir) {
        Contexto c1 = new Contexto(tempDir, tempDir.resolve("data"), null, null, T1, null);
        Prov provLista = new Prov("alesc-deputados", "https://www.alesc.sc.gov.br/deputados/", T1);
        HistoricoDeputados.registrar(c1, "alesc", List.of(
                HistoricoDeputados.Snapshot.de("carol", "Carol Exemplo", "PSOL", "https://exemplo.jpg", "carol", provLista)
        ), provLista);

        // Na execução seguinte, Carol saiu da lista ativa e o perfil do sitemap vem sem o crachá de partido.
        Contexto c2 = new Contexto(tempDir, tempDir.resolve("data"), null, null, T2, null);
        Prov provDetalhe = new Prov("alesc-deputados-detalhes", "https://www.alesc.sc.gov.br/deputado/carol/", T2);
        List<HistoricoDeputados.Entrada> mesclado = HistoricoDeputados.mesclar(
                c2, "alesc",
                List.of(HistoricoDeputados.Snapshot.de("titular", "Titular Exemplo", "PL", null, "titular", provLista)),
                provLista,
                List.of(
                        HistoricoDeputados.Snapshot.de("carol", "Carol Exemplo", null, null, "carol", provDetalhe),
                        HistoricoDeputados.Snapshot.de("outro-suplente", "Outro Suplente", null, null, "outro-suplente", provDetalhe)
                ));

        Map<String, HistoricoDeputados.Entrada> porId = mesclado.stream()
                .collect(Collectors.toMap(HistoricoDeputados.Entrada::id, e -> e));

        assertEquals(3, porId.size());
        assertTrue(porId.get("titular").emExercicio());
        assertFalse(porId.get("carol").emExercicio());
        assertEquals("PSOL", porId.get("carol").partido());
        assertEquals("https://exemplo.jpg", porId.get("carol").fotoUrl());
        assertFalse(porId.get("outro-suplente").emExercicio());
        assertNull(porId.get("outro-suplente").ultimoVistoEm());
        assertEquals(T2, porId.get("outro-suplente").provOu(provLista).capturadoEm());
    }
}
