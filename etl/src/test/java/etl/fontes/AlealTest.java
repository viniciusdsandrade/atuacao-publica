package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class AlealTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 232,
                            "parlamentar": 46,
                            "titular": false,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 26296
                        },
                        {
                            "id": 201,
                            "parlamentar": 16,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 51000
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Aleal.MandatoInfo> mandatos = Aleal.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Aleal.MandatoInfo m1 = mandatos.get(0);
        assertEquals(232, m1.id());
        assertEquals(46, m1.parlamentarId());
        assertFalse(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m1.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m1.fim());
        assertEquals(26296, m1.votos());

        Aleal.MandatoInfo m2 = mandatos.get(1);
        assertEquals(201, m2.id());
        assertEquals(16, m2.parlamentarId());
        assertTrue(m2.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m2.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m2.fim());
        assertEquals(51000, m2.votos());
    }

    @Test
    void limparNomeParlamentarRemovePrefixos() {
        assertEquals("MARCELO VICTOR", Aleal.limparNomeParlamentar("DEPUTADO MARCELO VICTOR"));
        assertEquals("CARLA DANTAS", Aleal.limparNomeParlamentar("DEPUTADA CARLA DANTAS"));
        assertEquals("Ângela Garrote", Aleal.limparNomeParlamentar("Ângela Garrote"));
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl.al.al.leg.br/media/sapl/public/parlamentar/46/foto.jpg",
                Aleal.formatarUrlFoto("http://sapl.al.al.leg.br/media/sapl/public/parlamentar/46/foto.jpg"));
        assertEquals("https://sapl.al.al.leg.br/media/foto.jpg",
                Aleal.formatarUrlFoto("/media/foto.jpg"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAleal = ctx.raw().resolve("aleal");
        Files.createDirectories(rawAleal.resolve("parlamentares"));
        Files.createDirectories(rawAleal.resolve("filiacoes"));
        Files.createDirectories(rawAleal.resolve("composicao_mesa"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 201,
                            "parlamentar": 16,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "votos_recebidos": 51000
                        }
                    ]
                }
                """;
        Files.writeString(rawAleal.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAleal, "mandatos.json", Aleal.API + "/parlamentares/mandato/?legislatura=" + Aleal.ID_LEGISLATURA_20);

        // 2. parlamentares/16.json
        String parlJson = """
                {
                    "id": 16,
                    "nome_completo": "Marcelo Victor Correia dos Santos",
                    "nome_parlamentar": "DEPUTADO MARCELO VICTOR",
                    "ativo": true,
                    "biografia": "<p>Presidente da Assembleia Legislativa. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl.al.al.leg.br/media/sapl/public/parlamentar/16/marcelo.jpg",
                    "telefone": "3214-5000",
                    "email": "dep.marcelovictor@al.al.leg.br"
                }
                """;
        Files.writeString(rawAleal.resolve("parlamentares/16.json"), parlJson);
        gravarMetadado(rawAleal, "parlamentares/16.json", Aleal.API + "/parlamentares/parlamentar/16/");

        // 3. filiacoes/16.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 141,
                            "__str__": "DEPUTADO MARCELO VICTOR - MDB - Movimento Democrático Brasileiro",
                            "data_desfiliacao": null,
                            "parlamentar": 16
                        }
                    ]
                }
                """;
        Files.writeString(rawAleal.resolve("filiacoes/16.json"), filiacaoJson);
        gravarMetadado(rawAleal, "filiacoes/16.json", Aleal.API + "/parlamentares/filiacao/?parlamentar=16");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 27,
                            "descricao": "Mesa 2023-2024",
                            "__str__": "Mesa da 1º (2023 - 2024) sessao da 20ª (2023 - 2027) (Atual) Legislatura",
                            "data_inicio": "2023-02-01",
                            "data_fim": "2025-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleal.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAleal, "mesas.json", Aleal.API + "/parlamentares/mesadiretora/");

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
        Files.writeString(rawAleal.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAleal, "cargos_mesa.json", Aleal.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 183,
                            "parlamentar": 16,
                            "cargo": 1,
                            "mesa_diretora": 27,
                            "__str__": "DEPUTADO MARCELO VICTOR - Presidente"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleal.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAleal, "composicao_mesa.json", Aleal.API + "/parlamentares/composicaomesa/");

        new Aleal().normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("aleal/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("aleal", p1.get("fonte").stringValue());
        assertEquals("16", p1.get("id_fonte").stringValue());
        assertEquals("MARCELO VICTOR", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("AL", p1.get("uf").stringValue());
        assertEquals("MDB", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.al.leg.br/media/sapl/public/parlamentar/16/marcelo.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("aleal/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Marcelo Victor Correia dos Santos", nomesCivis.get("16").stringValue());

        Path arqBloco = ctx.normalizado().resolve("aleal/blocos/16.json");
        assertTrue(Files.exists(arqBloco), "bloco 16.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        // Biografia não deve conter CPF
        String bio = bloco.get("biografia").stringValue();
        assertFalse(bio.contains("123.456.789-00"), "CPF deve ter sido removido pela Guarda");
        assertTrue(bio.contains("Presidente da Assembleia Legislativa"));

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
        assertEquals("2023-02-01", orgaos.get(0).get("inicio").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/aleal/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
