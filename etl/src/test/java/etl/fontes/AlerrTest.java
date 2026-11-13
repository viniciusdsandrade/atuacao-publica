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

class AlerrTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 212,
                            "parlamentar": 1,
                            "titular": true,
                            "data_inicio_mandato": "2023-01-01",
                            "data_fim_mandato": "2026-12-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 4800
                        },
                        {
                            "id": 227,
                            "parlamentar": 110,
                            "titular": false,
                            "data_inicio_mandato": "2023-01-01",
                            "data_fim_mandato": "2026-12-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 3200
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Alerr.MandatoInfo> mandatos = Alerr.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Alerr.MandatoInfo m1 = mandatos.get(0);
        assertEquals(212, m1.id());
        assertEquals(1, m1.parlamentarId());
        assertTrue(m1.titular());
        assertEquals(LocalDate.of(2023, 1, 1), m1.inicio());
        assertEquals(LocalDate.of(2026, 12, 31), m1.fim());
        assertEquals(4800, m1.votos());

        Alerr.MandatoInfo m2 = mandatos.get(1);
        assertEquals(227, m2.id());
        assertEquals(110, m2.parlamentarId());
        assertFalse(m2.titular());
    }

    @Test
    void limparNomeParlamentarRemovePrefixos() {
        assertEquals("ANGELA ÁGUIDA", Alerr.limparNomeParlamentar("DEPUTADA ANGELA ÁGUIDA"));
        assertEquals("ARMANDO NETO", Alerr.limparNomeParlamentar("DEPUTADO ARMANDO NETO"));
        assertEquals("Aurelina Medeiros", Alerr.limparNomeParlamentar("Aurelina Medeiros"));
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl.al.rr.leg.br/media/sapl/public/parlamentar/1/foto.jpg",
                Alerr.formatarUrlFoto("http://sapl.al.rr.leg.br/media/sapl/public/parlamentar/1/foto.jpg"));
        assertEquals("https://sapl.al.rr.leg.br/media/foto.jpg",
                Alerr.formatarUrlFoto("/media/foto.jpg"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlerr = ctx.raw().resolve("alerr");
        Files.createDirectories(rawAlerr.resolve("parlamentares"));
        Files.createDirectories(rawAlerr.resolve("filiacoes"));
        Files.createDirectories(rawAlerr.resolve("composicao_mesa"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 212,
                            "parlamentar": 1,
                            "titular": true,
                            "data_inicio_mandato": "2023-01-01",
                            "data_fim_mandato": "2026-12-31",
                            "votos_recebidos": 4800
                        }
                    ]
                }
                """;
        Files.writeString(rawAlerr.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAlerr, "mandatos.json", Alerr.API + "/parlamentares/mandato/?legislatura=" + Alerr.ID_LEGISLATURA_9);

        // 2. parlamentares/1.json
        String parlJson = """
                {
                    "id": 1,
                    "nome_completo": "Angela Águida Portella",
                    "nome_parlamentar": "DEPUTADA ANGELA ÁGUIDA",
                    "ativo": true,
                    "biografia": "<p>Deputada estadual em Roraima. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl.al.rr.leg.br/media/sapl/public/parlamentar/1/angela.jpg",
                    "telefone": "3214-5000",
                    "email": "dep.angela@al.rr.leg.br"
                }
                """;
        Files.writeString(rawAlerr.resolve("parlamentares/1.json"), parlJson);
        gravarMetadado(rawAlerr, "parlamentares/1.json", Alerr.API + "/parlamentares/parlamentar/1/");

        // 3. filiacoes/1.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 141,
                            "__str__": "DEPUTADA ANGELA ÁGUIDA - PP - Progressistas",
                            "data_desfiliacao": null,
                            "parlamentar": 1
                        }
                    ]
                }
                """;
        Files.writeString(rawAlerr.resolve("filiacoes/1.json"), filiacaoJson);
        gravarMetadado(rawAlerr, "filiacoes/1.json", Alerr.API + "/parlamentares/filiacao/?parlamentar=1");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 2,
                            "descricao": "Mesa 2023-2024",
                            "__str__": "Mesa da 1º (2023 - 2024) sessao da 9ª (2023 - 2026) (Atual) Legislatura",
                            "data_inicio": "2023-01-01",
                            "data_fim": "2024-12-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlerr.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAlerr, "mesas.json", Alerr.API + "/parlamentares/mesadiretora/");

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
        Files.writeString(rawAlerr.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAlerr, "cargos_mesa.json", Alerr.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 183,
                            "parlamentar": 1,
                            "cargo": 1,
                            "mesa_diretora": 2,
                            "__str__": "DEPUTADA ANGELA ÁGUIDA - Presidente"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlerr.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAlerr, "composicao_mesa.json", Alerr.API + "/parlamentares/composicaomesa/");

        // 7. tipos-materia.json e materias/1-2023-p1.json
        Files.createDirectories(rawAlerr.resolve("materias"));
        String tiposJson = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" }
                    ]
                }
                """;
        Files.writeString(rawAlerr.resolve("tipos-materia.json"), tiposJson);
        gravarMetadado(rawAlerr, "tipos-materia.json", Alerr.API + "/materia/tipomaterialegislativa/");

        String materiasJson = """
                {
                    "results": [
                        {
                            "id": 9101,
                            "tipo": 1,
                            "numero": 12,
                            "ano": 2023,
                            "ementa": "Institui diretrizes para o desenvolvimento regional em Roraima.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-04-14",
                            "texto_original": "https://sapl.al.rr.leg.br/media/texto.pdf",
                            "__str__": "Projeto de Lei nº 12 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlerr.resolve("materias/1-2023-p1.json"), materiasJson);
        gravarMetadado(rawAlerr, "materias/1-2023-p1.json", Alerr.API + "/materia/materialegislativa/?autores=1&ano=2023&page_size=100&page=1");

        new Alerr().normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alerr/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alerr", p1.get("fonte").stringValue());
        assertEquals("1", p1.get("id_fonte").stringValue());
        assertEquals("ANGELA ÁGUIDA", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("RR", p1.get("uf").stringValue());
        assertEquals("PP", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.rr.leg.br/media/sapl/public/parlamentar/1/angela.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alerr/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Angela Águida Portella", nomesCivis.get("1").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alerr/blocos/1.json");
        assertTrue(Files.exists(arqBloco), "bloco 1.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
        assertEquals("2023-01-01", orgaos.get(0).get("inicio").stringValue());

        // Proposições
        assertEquals(1, bloco.get("proposicoes").size());
        JsonNode prop = bloco.get("proposicoes").get(0);
        assertEquals("9101", prop.get("id_fonte").stringValue());
        assertEquals("PL", prop.get("tipo").stringValue());
        assertEquals(12, prop.get("numero").asInt());
        assertEquals("Aprovado", prop.get("situacao").stringValue());
        assertEquals("alerr-proposicoes", prop.get("fonte_id").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alerr/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
