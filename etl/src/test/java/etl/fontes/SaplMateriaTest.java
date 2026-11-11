package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.FonteUsada;
import etl.Registros.Proposicao;

class SaplMateriaTest {

    @Test
    void anosLegislaturaCalculaIntervaloCorreto() {
        LocalDate inicio = LocalDate.of(2023, 2, 1);
        LocalDate fim = LocalDate.of(2027, 1, 31);
        LocalDate hoje = LocalDate.of(2025, 6, 1);

        List<Integer> anos = SaplMateria.anosLegislatura(inicio, fim, hoje);
        assertEquals(List.of(2023, 2024, 2025), anos);
    }

    @Test
    void extrairAutorIdRetornaIdQuandoPresente() {
        String json = """
                {
                    "count": 1,
                    "results": [
                        {
                            "id": 19,
                            "tipo": 1,
                            "object_id": "16",
                            "nome": "Cabo Bebeto"
                        }
                    ]
                }
                """;
        JsonNode raiz = Json.FONTE.readTree(json);
        assertEquals("19", SaplMateria.extrairAutorId(raiz, "16"));
        assertNull(SaplMateria.extrairAutorId(raiz, "99"));
    }

    @Test
    void carregarTiposENormalizarProposicoes(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlpb = ctx.raw().resolve("alpb");
        Files.createDirectories(rawAlpb.resolve("materias"));

        // 1. tipos-materia.json
        String jsonTipos = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" },
                        { "id": 2, "sigla": "REQ", "descricao": "Requerimento" }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("tipos-materia.json"), jsonTipos);
        gravarMetadado(rawAlpb, "tipos-materia.json", "https://sapl3.al.pb.leg.br/api/materia/tipomaterialegislativa/");

        Map<String, String[]> tipos = SaplMateria.carregarTipos(ctx, "alpb");
        assertEquals(2, tipos.size());
        assertEquals("PL", tipos.get("1")[0]);
        assertEquals("Projeto de Lei", tipos.get("1")[1]);

        // 2. materias/16-2023-p1.json
        String jsonMaterias2023 = """
                {
                    "count": 2,
                    "next": null,
                    "results": [
                        {
                            "id": 101,
                            "tipo": 1,
                            "numero": 12,
                            "ano": 2023,
                            "ementa": "Institui o programa estadual de saúde preventiva.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-03-15",
                            "texto_original": "https://sapl3.al.pb.leg.br/media/sapl/public/materialegislativa/2023/101/texto.pdf",
                            "__str__": "Projeto de Lei nº 12 de 2023"
                        },
                        {
                            "id": 102,
                            "tipo": 2,
                            "numero": 45,
                            "ano": 2023,
                            "ementa": "Requer sessão solene em homenagem aos bombeiros.",
                            "resultado": null,
                            "em_tramitacao": true,
                            "data_apresentacao": "2023-08-20",
                            "texto_original": null,
                            "__str__": "Requerimento nº 45 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("materias/16-2023-p1.json"), jsonMaterias2023);
        gravarMetadado(rawAlpb, "materias/16-2023-p1.json", "https://sapl3.al.pb.leg.br/api/materia/materialegislativa/?autores=19&ano=2023&page_size=100&page=1");

        Map<String, List<FonteUsada>> fontesUsadas = new LinkedHashMap<>();
        List<Proposicao> proposicoes = SaplMateria.normalizarProposicoes(
                ctx, "alpb", "alpb-proposicoes", "https://sapl3.al.pb.leg.br", 16, List.of(2023), tipos, fontesUsadas);

        assertEquals(2, proposicoes.size());

        // Deve vir ordenado por data_apresentacao decrescente (2023-08-20 antes de 2023-03-15)
        Proposicao p1 = proposicoes.get(0);
        assertEquals("102", p1.idFonte());
        assertEquals("REQ", p1.tipo());
        assertEquals(45, p1.numero());
        assertEquals(2023, p1.ano());
        assertEquals("Requerimento nº 45 de 2023", p1.identificacao());
        assertEquals("Requer sessão solene em homenagem aos bombeiros.", p1.ementa());
        assertEquals("Em tramitação", p1.situacao());
        assertEquals("2023-08-20", p1.dataApresentacao());
        assertNull(p1.urlTexto());
        assertNotNull(p1.prov());
        assertEquals("alpb-proposicoes", p1.prov().fonteId());

        Proposicao p2 = proposicoes.get(1);
        assertEquals("101", p2.idFonte());
        assertEquals("PL", p2.tipo());
        assertEquals(12, p2.numero());
        assertEquals("Aprovado", p2.situacao());
        assertEquals("2023-03-15", p2.dataApresentacao());
        assertEquals("https://sapl3.al.pb.leg.br/media/sapl/public/materialegislativa/2023/101/texto.pdf", p2.urlTexto());

        assertTrue(fontesUsadas.containsKey("proposicoes"));
        assertEquals(1, fontesUsadas.get("proposicoes").size());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alpb/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
