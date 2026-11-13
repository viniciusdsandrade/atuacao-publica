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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;

class AletoTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 43,
                            "parlamentar": 3,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 22921
                        },
                        {
                            "id": 47,
                            "parlamentar": 11,
                            "titular": false,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2024-12-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": null
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Aleto.MandatoInfo> mandatos = Aleto.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Aleto.MandatoInfo m1 = mandatos.get(0);
        assertEquals(43, m1.id());
        assertEquals(3, m1.parlamentarId());
        assertTrue(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m1.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m1.fim());
        assertEquals(22921, m1.votos());

        Aleto.MandatoInfo m2 = mandatos.get(1);
        assertEquals(47, m2.id());
        assertEquals(11, m2.parlamentarId());
        assertFalse(m2.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m2.inicio());
        assertEquals(LocalDate.of(2024, 12, 31), m2.fim());
    }

    @Test
    void formatacaoDeFotoESiglaPartido() {
        assertEquals("https://sapl.al.to.leg.br/media/foto.jpg",
                Aleto.formatarUrlFoto("http://sapl.al.to.leg.br/media/foto.jpg"));
        assertEquals("https://sapl.al.to.leg.br/media/foto.jpg",
                Aleto.formatarUrlFoto("/media/foto.jpg"));

        assertEquals("REPUBLICANOS", Aleto.formatarSiglaPartido("10 - REPUBLICANOS"));
        assertEquals("PL", Aleto.formatarSiglaPartido("Partido Liberal (PL)"));
        assertEquals("MDB", Aleto.formatarSiglaPartido("MDB"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAleto = ctx.raw().resolve("aleto");
        Files.createDirectories(rawAleto.resolve("parlamentares"));
        Files.createDirectories(rawAleto.resolve("filiacoes"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 43,
                            "parlamentar": 3,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 22921
                        }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAleto, "mandatos.json", Aleto.API + "/parlamentares/mandato/?legislatura=11");

        // 2. parlamentares/3.json
        String parlJson = """
                {
                    "id": 3,
                    "nome_parlamentar": "AMÉLIO CAYRES",
                    "nome_completo": "Amélio Cayres de Almeida",
                    "ativo": true,
                    "biografia": "<p>Eleito deputado estadual. CPF 123.456.789-00. Presidente da Assembleia.</p>",
                    "fotografia": "http://sapl.al.to.leg.br/media/sapl/public/parlamentar/3/foto_amelio.jpg"
                }
                """;
        Files.writeString(rawAleto.resolve("parlamentares/3.json"), parlJson);
        gravarMetadado(rawAleto, "parlamentares/3.json", Aleto.API + "/parlamentares/parlamentar/3/");

        // 3. filiacoes/3.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "parlamentar": 3,
                            "partido": "10 - REPUBLICANOS",
                            "data_filiacao": "2022-04-01",
                            "data_desfiliacao": null
                        }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("filiacoes/3.json"), filiacaoJson);
        gravarMetadado(rawAleto, "filiacoes/3.json", Aleto.API + "/parlamentares/filiacao/?parlamentar=3");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 7,
                            "descricao": "Mesa da 3ª sessão da 10ª Legislatura",
                            "data_inicio": "2025-02-01",
                            "data_fim": "2027-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAleto, "mesas.json", Aleto.API + "/parlamentares/mesadiretora/");

        // 5. cargos_mesa.json
        String cargosJson = """
                {
                    "results": [
                        {
                            "id": 1,
                            "descricao": "Presidente"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAleto, "cargos_mesa.json", Aleto.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 48,
                            "parlamentar": 3,
                            "cargo": 1,
                            "mesa_diretora": 7,
                            "__str__": "AMÉLIO CAYRES - Presidente"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAleto, "composicao_mesa.json", Aleto.API + "/parlamentares/composicaomesa/");

        // 7. tipos-materia.json e materias/3-2023-p1.json
        Files.createDirectories(rawAleto.resolve("materias"));
        String tiposJson = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("tipos-materia.json"), tiposJson);
        gravarMetadado(rawAleto, "tipos-materia.json", Aleto.API + "/materia/tipomaterialegislativa/");

        String materiasJson = """
                {
                    "results": [
                        {
                            "id": 3101,
                            "tipo": 1,
                            "numero": 15,
                            "ano": 2023,
                            "ementa": "Dispõe sobre o apoio à agropecuária familiar no Tocantins.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-03-22",
                            "texto_original": "https://sapl.al.to.leg.br/media/texto.pdf",
                            "__str__": "Projeto de Lei nº 15 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleto.resolve("materias/3-2023-p1.json"), materiasJson);
        gravarMetadado(rawAleto, "materias/3-2023-p1.json", Aleto.API + "/materia/materialegislativa/?autores=3&ano=2023&page_size=100&page=1");

        // Executa normalização
        Aleto aleto = new Aleto();
        aleto.normalize(ctx);

        // Verificações
        Path arqPessoas = ctx.normalizado().resolve("aleto/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("aleto", p1.get("fonte").stringValue());
        assertEquals("3", p1.get("id_fonte").stringValue());
        assertEquals("AMÉLIO CAYRES", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("TO", p1.get("uf").stringValue());
        assertEquals("REPUBLICANOS", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.to.leg.br/media/sapl/public/parlamentar/3/foto_amelio.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("aleto/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Amélio Cayres de Almeida", nomesCivis.get("3").stringValue());

        Path arqBloco = ctx.normalizado().resolve("aleto/blocos/3.json");
        assertTrue(Files.exists(arqBloco), "bloco 3.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
        assertEquals("2025-02-01", orgaos.get(0).get("inicio").stringValue());

        // Proposições
        assertEquals(1, bloco.get("proposicoes").size());
        JsonNode prop = bloco.get("proposicoes").get(0);
        assertEquals("3101", prop.get("id_fonte").stringValue());
        assertEquals("PL", prop.get("tipo").stringValue());
        assertEquals(15, prop.get("numero").asInt());
        assertEquals("Aprovado", prop.get("situacao").stringValue());
        assertEquals("aleto-proposicoes", prop.get("fonte_id").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/aleto/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
