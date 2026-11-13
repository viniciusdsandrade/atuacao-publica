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

class AleamTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 103,
                            "parlamentar": 5,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 46287
                        },
                        {
                            "id": 120,
                            "parlamentar": 99,
                            "titular": false,
                            "data_inicio_mandato": "2023-10-01",
                            "data_fim_mandato": "2024-03-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": null
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Aleam.MandatoInfo> mandatos = Aleam.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Aleam.MandatoInfo m1 = mandatos.get(0);
        assertEquals(103, m1.id());
        assertEquals(5, m1.parlamentarId());
        assertTrue(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m1.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m1.fim());
        assertEquals(46287, m1.votos());

        Aleam.MandatoInfo m2 = mandatos.get(1);
        assertEquals(120, m2.id());
        assertEquals(99, m2.parlamentarId());
        assertFalse(m2.titular());
        assertEquals(LocalDate.of(2023, 10, 1), m2.inicio());
        assertEquals(LocalDate.of(2024, 3, 31), m2.fim());
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl.al.am.leg.br/media/sapl/public/parlamentar/5/abdala-fraxe.png",
                Aleam.formatarUrlFoto("http://sapl.al.am.leg.br/media/sapl/public/parlamentar/5/abdala-fraxe.png"));
        assertEquals("https://sapl.al.am.leg.br/media/foto.png",
                Aleam.formatarUrlFoto("/media/foto.png"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAleam = ctx.raw().resolve("aleam");
        Files.createDirectories(rawAleam.resolve("parlamentares"));
        Files.createDirectories(rawAleam.resolve("filiacoes"));
        Files.createDirectories(rawAleam.resolve("composicao_mesa"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 103,
                            "parlamentar": 5,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "votos_recebidos": 46287
                        }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAleam, "mandatos.json", Aleam.API + "/parlamentares/mandato/?legislatura=20");

        // 2. parlamentares/5.json
        String parlJson = """
                {
                    "id": 5,
                    "nome_completo": "ABDALA HABIB FRAXE JUNIOR",
                    "nome_parlamentar": "Abdala Fraxe",
                    "ativo": true,
                    "biografia": "<p>Economista formado pela UFAM. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl.al.am.leg.br/media/sapl/public/parlamentar/5/abdala.png",
                    "telefone": "3183-8000",
                    "email": "deputado.abdalafraxe@aleam.gov.br"
                }
                """;
        Files.writeString(rawAleam.resolve("parlamentares/5.json"), parlJson);
        gravarMetadado(rawAleam, "parlamentares/5.json", Aleam.API + "/parlamentares/parlamentar/5/");

        // 3. filiacoes/5.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 87,
                            "__str__": "Abdala Fraxe - AVANTE - Avante",
                            "data_desfiliacao": null,
                            "parlamentar": 5
                        }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("filiacoes/5.json"), filiacaoJson);
        gravarMetadado(rawAleam, "filiacoes/5.json", Aleam.API + "/parlamentares/filiacao/?parlamentar=5");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 13,
                            "descricao": "Mesa 2023",
                            "__str__": "Mesa da 1º (2023 - 2023) sessao da 20ª (2023 - 2027) (Atual) Legislatura",
                            "data_inicio": "2023-02-01",
                            "data_fim": "2024-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAleam, "mesas.json", Aleam.API + "/parlamentares/mesadiretora/");

        // 5. cargos_mesa.json
        String cargosJson = """
                {
                    "results": [
                        {
                            "id": 6,
                            "descricao": "1º Secretário"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAleam, "cargos_mesa.json", Aleam.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 113,
                            "parlamentar": 5,
                            "cargo": 6,
                            "mesa_diretora": 13,
                            "__str__": "Abdala Fraxe - 1º Secretário"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAleam, "composicao_mesa.json", Aleam.API + "/parlamentares/composicaomesa/");

        // 7. tipos-materia.json e materias/5-2023-p1.json
        Files.createDirectories(rawAleam.resolve("materias"));
        String tiposJson = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("tipos-materia.json"), tiposJson);
        gravarMetadado(rawAleam, "tipos-materia.json", Aleam.API + "/materia/tipomaterialegislativa/");

        String materiasJson = """
                {
                    "results": [
                        {
                            "id": 8801,
                            "tipo": 1,
                            "numero": 77,
                            "ano": 2023,
                            "ementa": "Institui o polo regional de bioeconomia no Amazonas.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-05-18",
                            "texto_original": "https://sapl.al.am.leg.br/media/texto.pdf",
                            "__str__": "Projeto de Lei nº 77 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleam.resolve("materias/5-2023-p1.json"), materiasJson);
        gravarMetadado(rawAleam, "materias/5-2023-p1.json", Aleam.API + "/materia/materialegislativa/?autores=5&ano=2023&page_size=100&page=1");

        new Aleam().normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("aleam/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("aleam", p1.get("fonte").stringValue());
        assertEquals("5", p1.get("id_fonte").stringValue());
        assertEquals("Abdala Fraxe", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("AM", p1.get("uf").stringValue());
        assertEquals("AVANTE", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.am.leg.br/media/sapl/public/parlamentar/5/abdala.png", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("aleam/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("ABDALA HABIB FRAXE JUNIOR", nomesCivis.get("5").stringValue());

        Path arqBloco = ctx.normalizado().resolve("aleam/blocos/5.json");
        assertTrue(Files.exists(arqBloco), "bloco 5.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("1º Secretário", orgaos.get(0).get("papel").stringValue());
        assertEquals("2023-02-01", orgaos.get(0).get("inicio").stringValue());

        // Proposições
        assertEquals(1, bloco.get("proposicoes").size());
        JsonNode prop = bloco.get("proposicoes").get(0);
        assertEquals("8801", prop.get("id_fonte").stringValue());
        assertEquals("PL", prop.get("tipo").stringValue());
        assertEquals(77, prop.get("numero").asInt());
        assertEquals("Aprovado", prop.get("situacao").stringValue());
        assertEquals("aleam-proposicoes", prop.get("fonte_id").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/aleam/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
