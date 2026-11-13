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

class AlepiTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 734,
                            "parlamentar": 324,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-02",
                            "data_fim_mandato": "2026-12-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 29600
                        },
                        {
                            "id": 735,
                            "parlamentar": 278,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 42000
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Alepi.MandatoInfo> mandatos = Alepi.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Alepi.MandatoInfo m1 = mandatos.get(0);
        assertEquals(734, m1.id());
        assertEquals(324, m1.parlamentarId());
        assertTrue(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 2), m1.inicio());
        assertEquals(LocalDate.of(2026, 12, 31), m1.fim());
        assertEquals(29600, m1.votos());

        Alepi.MandatoInfo m2 = mandatos.get(1);
        assertEquals(735, m2.id());
        assertEquals(278, m2.parlamentarId());
        assertTrue(m2.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m2.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m2.fim());
        assertEquals(42000, m2.votos());
    }

    @Test
    void limparNomeParlamentarRemovePrefixos() {
        assertEquals("SEVERO EULÁLIO", Alepi.limparNomeParlamentar("DEPUTADO SEVERO EULÁLIO"));
        assertEquals("ANA PAULA", Alepi.limparNomeParlamentar("DEPUTADA ANA PAULA"));
        assertEquals("Aldo Gil", Alepi.limparNomeParlamentar("Aldo Gil"));
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl.al.pi.leg.br/media/sapl/public/parlamentar/324/foto.jpg",
                Alepi.formatarUrlFoto("http://sapl.al.pi.leg.br/media/sapl/public/parlamentar/324/foto.jpg"));
        assertEquals("https://sapl.al.pi.leg.br/media/foto.jpg",
                Alepi.formatarUrlFoto("/media/foto.jpg"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlepi = ctx.raw().resolve("alepi");
        Files.createDirectories(rawAlepi.resolve("parlamentares"));
        Files.createDirectories(rawAlepi.resolve("filiacoes"));
        Files.createDirectories(rawAlepi.resolve("composicao_mesa"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 735,
                            "parlamentar": 278,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "votos_recebidos": 42000
                        }
                    ]
                }
                """;
        Files.writeString(rawAlepi.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAlepi, "mandatos.json", Alepi.API + "/parlamentares/mandato/?legislatura=" + Alepi.ID_LEGISLATURA_20);

        // 2. parlamentares/278.json
        String parlJson = """
                {
                    "id": 278,
                    "nome_completo": "Severo Maria Eulálio Neto",
                    "nome_parlamentar": "DEPUTADO SEVERO EULÁLIO",
                    "ativo": true,
                    "biografia": "<p>Presidente da Assembleia Legislativa. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl.al.pi.leg.br/media/sapl/public/parlamentar/278/severo.jpg",
                    "telefone": "3133-3000",
                    "email": "dep.severoeulalio@al.pi.leg.br"
                }
                """;
        Files.writeString(rawAlepi.resolve("parlamentares/278.json"), parlJson);
        gravarMetadado(rawAlepi, "parlamentares/278.json", Alepi.API + "/parlamentares/parlamentar/278/");

        // 3. filiacoes/278.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 348,
                            "__str__": "Severo Eulálio - MDB - Movimento Democrático Brasileiro",
                            "data_desfiliacao": null,
                            "parlamentar": 278
                        }
                    ]
                }
                """;
        Files.writeString(rawAlepi.resolve("filiacoes/278.json"), filiacaoJson);
        gravarMetadado(rawAlepi, "filiacoes/278.json", Alepi.API + "/parlamentares/filiacao/?parlamentar=278");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 57,
                            "descricao": "Mesa 2026",
                            "__str__": "Mesa da 4º (2026 - 2026) (Atual) sessao da 20ª (2023 - 2026) (Atual) Legislatura",
                            "data_inicio": "2025-02-01",
                            "data_fim": "2027-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlepi.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAlepi, "mesas.json", Alepi.API + "/parlamentares/mesadiretora/");

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
        Files.writeString(rawAlepi.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAlepi, "cargos_mesa.json", Alepi.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 250,
                            "__str__": "Severo Eulálio - Presidente da Mesa Diretora",
                            "parlamentar": 278,
                            "cargo": 1,
                            "mesa_diretora": 57
                        }
                    ]
                }
                """;
        Files.writeString(rawAlepi.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAlepi, "composicao_mesa.json", Alepi.API + "/parlamentares/composicaomesa/");

        // 7. tipos-materia.json e materias/278-2023-p1.json
        Files.createDirectories(rawAlepi.resolve("materias"));
        String tiposJson = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" }
                    ]
                }
                """;
        Files.writeString(rawAlepi.resolve("tipos-materia.json"), tiposJson);
        gravarMetadado(rawAlepi, "tipos-materia.json", Alepi.API + "/materia/tipomaterialegislativa/");

        String materiasJson = """
                {
                    "results": [
                        {
                            "id": 6401,
                            "tipo": 1,
                            "numero": 52,
                            "ano": 2023,
                            "ementa": "Institui o programa estadual de incentivo à energia solar no Piauí.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-09-05",
                            "texto_original": "https://sapl.al.pi.leg.br/media/texto.pdf",
                            "__str__": "Projeto de Lei nº 52 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlepi.resolve("materias/278-2023-p1.json"), materiasJson);
        gravarMetadado(rawAlepi, "materias/278-2023-p1.json", Alepi.API + "/materia/materialegislativa/?autores=278&ano=2023&page_size=100&page=1");

        // Executa normalização
        Alepi alepi = new Alepi();
        alepi.normalize(ctx);

        // Verificações
        Path arqPessoas = ctx.normalizado().resolve("alepi/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alepi", p1.get("fonte").stringValue());
        assertEquals("278", p1.get("id_fonte").stringValue());
        assertEquals("SEVERO EULÁLIO", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("PI", p1.get("uf").stringValue());
        assertEquals("MDB", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.pi.leg.br/media/sapl/public/parlamentar/278/severo.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alepi/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Severo Maria Eulálio Neto", nomesCivis.get("278").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alepi/blocos/278.json");
        assertTrue(Files.exists(arqBloco), "bloco 278.json deve existir");
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
        assertEquals("6401", prop.get("id_fonte").stringValue());
        assertEquals("PL", prop.get("tipo").stringValue());
        assertEquals(52, prop.get("numero").asInt());
        assertEquals("Aprovado", prop.get("situacao").stringValue());
        assertEquals("alepi-proposicoes", prop.get("fonte_id").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alepi/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
