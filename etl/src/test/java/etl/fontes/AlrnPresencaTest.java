package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.Evento;

/** Presença nas sessões deliberativas da ALRN (ADR-0084). Fixture minimizada das respostas de 2026-11-21. */
class AlrnPresencaTest {

    @Test
    void presencaEAusenciaJustificadaSemInventarFalta(@TempDir Path tmp) throws Exception {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse("2023-06-01T12:00:00Z"), null);
        grava("reunioes/2023-0.json", tmp, """
                {"total": 2, "dados": [
                  {"id": 3068, "numero": 108, "dataInicio": "2023-05-19T10:30:00.000Z", "situacao": "R",
                   "tipoReuniao": {"id": 1, "nome": "Sessão Plenária Ordinária"}},
                  {"id": 3070, "numero": 5, "dataInicio": "2023-05-20T10:30:00.000Z", "situacao": "R",
                   "tipoReuniao": {"id": 14, "nome": "Sessão Solene"}}
                ]}
                """);
        grava("reunioes/presenca/3068.json", tmp, """
                {"dados": [
                  {"nomeParlamentar": "DEPUTADA CRISTIANE DANTAS", "evento": {"id": 8, "chave": "presenca"}},
                  {"nomeParlamentar": "DEPUTADA DIVANEIDE BASÍLIO", "evento": {"id": 9, "chave": "ausencia-justificada"}},
                  {"nomeParlamentar": "DEPUTADA EUDIANE MACEDO", "evento": null}
                ]}
                """);

        Map<String, java.util.List<Evento>> r = AlrnPresenca.porDeputado(ctx,
                new AutoriaPorNome(Map.of("8", "Cristiane Dantas", "56", "Divaneide Basílio", "10", "Eudiane Macedo")));

        assertEquals(1, AlrnPresenca.sessoes(ctx).size());
        Evento cristiane = r.get("8").getFirst();
        assertEquals("registrada", cristiane.presenca());
        assertEquals("2023-05-19", cristiane.data());
        assertEquals("Sessão Plenária Ordinária nº 108", cristiane.titulo());
        assertEquals("ausencia_justificada", r.get("56").getFirst().presenca());
        assertNull(r.get("10").getFirst().presenca());
    }

    private static void grava(String caminho, Path tmp, String json) throws Exception {
        Path arquivo = tmp.resolve("data/raw/alrn").resolve(caminho);
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, json);
        Json.gravar(arquivo.resolveSibling(arquivo.getFileName() + ".meta.json"),
                new Coleta.Arquivo("x", "raw/alrn/" + caminho, "abc", json.length(), Instant.parse("2026-11-21T12:00:00Z")), true);
    }
}
