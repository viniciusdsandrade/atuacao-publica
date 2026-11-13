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

class AlpbTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 317,
                            "parlamentar": 89,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 58329
                        },
                        {
                            "id": 357,
                            "parlamentar": 192,
                            "titular": false,
                            "data_inicio_mandato": "2023-12-12",
                            "data_fim_mandato": "2024-02-26",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 0
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Alpb.MandatoInfo> mandatos = Alpb.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Alpb.MandatoInfo m1 = mandatos.get(0);
        assertEquals(317, m1.id());
        assertEquals(89, m1.parlamentarId());
        assertTrue(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m1.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m1.fim());
        assertEquals(58329, m1.votos());

        Alpb.MandatoInfo m2 = mandatos.get(1);
        assertEquals(357, m2.id());
        assertEquals(192, m2.parlamentarId());
        assertFalse(m2.titular());
        assertEquals(LocalDate.of(2023, 12, 12), m2.inicio());
        assertEquals(LocalDate.of(2024, 2, 26), m2.fim());
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl3.al.pb.leg.br/media/sapl/public/parlamentar/89/foto.jpg",
                Alpb.formatarUrlFoto("http://sapl3.al.pb.leg.br/media/sapl/public/parlamentar/89/foto.jpg"));
        assertEquals("https://sapl3.al.pb.leg.br/media/foto.jpg",
                Alpb.formatarUrlFoto("/media/foto.jpg"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlpb = ctx.raw().resolve("alpb");
        Files.createDirectories(rawAlpb.resolve("parlamentares"));
        Files.createDirectories(rawAlpb.resolve("filiacoes"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 317,
                            "parlamentar": 89,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "votos_recebidos": 58329
                        }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAlpb, "mandatos.json", Alpb.API + "/parlamentares/mandato/?legislatura=20");

        // 2. parlamentares/89.json
        String parlJson = """
                {
                    "id": 89,
                    "nome_completo": "Adriano Cézar Galdino de Araújo",
                    "nome_parlamentar": "Adriano Galdino",
                    "ativo": true,
                    "biografia": "<p>Graduado em Engenharia Civil e Direito. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl3.al.pb.leg.br/media/parlamentar/89/foto.jpg",
                    "telefone": "3214-4638",
                    "email": "presidencia@al.pb.leg.br"
                }
                """;
        Files.writeString(rawAlpb.resolve("parlamentares/89.json"), parlJson);
        gravarMetadado(rawAlpb, "parlamentares/89.json", Alpb.API + "/parlamentares/parlamentar/89/");

        // 3. filiacoes/89.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 191,
                            "__str__": "Adriano Galdino - REPUBLICANOS - REPUBLICANOS",
                            "data_desfiliacao": null,
                            "parlamentar": 89
                        }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("filiacoes/89.json"), filiacaoJson);
        gravarMetadado(rawAlpb, "filiacoes/89.json", Alpb.API + "/parlamentares/filiacao/?parlamentar=89");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 12,
                            "descricao": "Mesa 2023-2025",
                            "data_inicio": "2023-02-01",
                            "data_fim": "2025-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAlpb, "mesas.json", Alpb.API + "/parlamentares/mesadiretora/");

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
        Files.writeString(rawAlpb.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAlpb, "cargos_mesa.json", Alpb.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "parlamentar": 89,
                            "mesa_diretora": 12,
                            "cargo": 1,
                            "__str__": "Adriano Galdino - Presidente"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAlpb, "composicao_mesa.json", Alpb.API + "/parlamentares/composicaomesa/");

        // 7. tipos-materia.json e materias/89-2023-p1.json
        Files.createDirectories(rawAlpb.resolve("materias"));
        String tiposJson = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("tipos-materia.json"), tiposJson);
        gravarMetadado(rawAlpb, "tipos-materia.json", Alpb.API + "/materia/tipomaterialegislativa/");

        String materiasJson = """
                {
                    "results": [
                        {
                            "id": 5001,
                            "tipo": 1,
                            "numero": 10,
                            "ano": 2023,
                            "ementa": "Institui diretrizes para a infraestrutura estadual.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-04-10",
                            "texto_original": "https://sapl3.al.pb.leg.br/media/texto.pdf",
                            "__str__": "Projeto de Lei nº 10 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlpb.resolve("materias/89-2023-p1.json"), materiasJson);
        gravarMetadado(rawAlpb, "materias/89-2023-p1.json", Alpb.API + "/materia/materialegislativa/?autores=89&ano=2023&page_size=100&page=1");

        Alpb alpb = new Alpb();
        alpb.normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alpb/pessoas.json");
        assertTrue(Files.exists(arqPessoas));
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());
        assertEquals("alpb", pessoas.get(0).get("fonte").stringValue());
        assertEquals("89", pessoas.get(0).get("id_fonte").stringValue());
        assertEquals("Adriano Galdino", pessoas.get(0).get("nome").stringValue());
        assertEquals("deputado_estadual", pessoas.get(0).get("cargo").stringValue());
        assertEquals("PB", pessoas.get(0).get("uf").stringValue());
        assertEquals("REPUBLICANOS", pessoas.get(0).get("partido").stringValue());
        assertEquals("https://sapl3.al.pb.leg.br/media/parlamentar/89/foto.jpg", pessoas.get(0).get("foto_url").stringValue());

        Path arqNomesCivis = ctx.normalizado().resolve("alpb/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis));
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Adriano Cézar Galdino de Araújo", nomesCivis.get("89").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alpb/blocos/89.json");
        assertTrue(Files.exists(arqBloco));
        JsonNode bloco = Json.ler(arqBloco);
        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");
        assertEquals(1, bloco.get("orgaos_e_frentes").size());
        assertEquals("Presidente", bloco.get("orgaos_e_frentes").get(0).get("papel").stringValue());

        // Proposições
        assertEquals(1, bloco.get("proposicoes").size());
        JsonNode prop = bloco.get("proposicoes").get(0);
        assertEquals("5001", prop.get("id_fonte").stringValue());
        assertEquals("PL", prop.get("tipo").stringValue());
        assertEquals(10, prop.get("numero").asInt());
        assertEquals("Aprovado", prop.get("situacao").stringValue());
        assertEquals("alpb-proposicoes", prop.get("fonte_id").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alpb/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
