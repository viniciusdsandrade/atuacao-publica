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

class AleroTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 484,
                            "parlamentar": 243,
                            "titular": false,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 9800
                        },
                        {
                            "id": 461,
                            "parlamentar": 256,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 19500
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Alero.MandatoInfo> mandatos = Alero.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Alero.MandatoInfo m1 = mandatos.get(0);
        assertEquals(484, m1.id());
        assertEquals(243, m1.parlamentarId());
        assertFalse(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m1.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m1.fim());
        assertEquals(9800, m1.votos());

        Alero.MandatoInfo m2 = mandatos.get(1);
        assertEquals(461, m2.id());
        assertEquals(256, m2.parlamentarId());
        assertTrue(m2.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m2.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m2.fim());
        assertEquals(19500, m2.votos());
    }

    @Test
    void limparNomeParlamentarRemovePrefixos() {
        assertEquals("ALEX REDANO", Alero.limparNomeParlamentar("DEPUTADO ALEX REDANO"));
        assertEquals("IEDA CHAVES", Alero.limparNomeParlamentar("DEPUTADA IEDA CHAVES"));
        assertEquals("Cirone Deiró", Alero.limparNomeParlamentar("Cirone Deiró"));
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl.al.ro.leg.br/media/sapl/public/parlamentar/256/foto.jpg",
                Alero.formatarUrlFoto("http://sapl.al.ro.leg.br/media/sapl/public/parlamentar/256/foto.jpg"));
        assertEquals("https://sapl.al.ro.leg.br/media/foto.jpg",
                Alero.formatarUrlFoto("/media/foto.jpg"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlero = ctx.raw().resolve("alero");
        Files.createDirectories(rawAlero.resolve("parlamentares"));
        Files.createDirectories(rawAlero.resolve("filiacoes"));
        Files.createDirectories(rawAlero.resolve("composicao_mesa"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 461,
                            "parlamentar": 256,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "votos_recebidos": 19500
                        }
                    ]
                }
                """;
        Files.writeString(rawAlero.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAlero, "mandatos.json", Alero.API + "/parlamentares/mandato/?legislatura=" + Alero.ID_LEGISLATURA_11);

        // 2. parlamentares/256.json
        String parlJson = """
                {
                    "id": 256,
                    "nome_completo": "Alex Mendonça Alves",
                    "nome_parlamentar": "DEPUTADO ALEX REDANO",
                    "ativo": true,
                    "biografia": "<p>Presidente da Assembleia Legislativa. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl.al.ro.leg.br/media/sapl/public/parlamentar/256/alex.jpg",
                    "telefone": "3214-5000",
                    "email": "dep.alexredano@al.ro.leg.br"
                }
                """;
        Files.writeString(rawAlero.resolve("parlamentares/256.json"), parlJson);
        gravarMetadado(rawAlero, "parlamentares/256.json", Alero.API + "/parlamentares/parlamentar/256/");

        // 3. filiacoes/256.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 205,
                            "__str__": "ALEX REDANO - REPUBLICANOS - REPUBLICANOS",
                            "data_desfiliacao": null,
                            "parlamentar": 256
                        }
                    ]
                }
                """;
        Files.writeString(rawAlero.resolve("filiacoes/256.json"), filiacaoJson);
        gravarMetadado(rawAlero, "filiacoes/256.json", Alero.API + "/parlamentares/filiacao/?parlamentar=256");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 47,
                            "descricao": "Mesa 2025-2026",
                            "__str__": "Mesa da 4º (2026 - 2027) (Atual) sessao da 11ª (2023 - 2027) (Atual) Legislatura",
                            "data_inicio": "2025-02-01",
                            "data_fim": "2027-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAlero.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAlero, "mesas.json", Alero.API + "/parlamentares/mesadiretora/");

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
        Files.writeString(rawAlero.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAlero, "cargos_mesa.json", Alero.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 376,
                            "__str__": "ALEX REDANO - Presidente",
                            "parlamentar": 256,
                            "cargo": 1,
                            "mesa_diretora": 47
                        }
                    ]
                }
                """;
        Files.writeString(rawAlero.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAlero, "composicao_mesa.json", Alero.API + "/parlamentares/composicaomesa/");

        // Executa normalização
        Alero alero = new Alero();
        alero.normalize(ctx);

        // Verificações
        Path arqPessoas = ctx.normalizado().resolve("alero/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alero", p1.get("fonte").stringValue());
        assertEquals("256", p1.get("id_fonte").stringValue());
        assertEquals("ALEX REDANO", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("RO", p1.get("uf").stringValue());
        assertEquals("REPUBLICANOS", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.ro.leg.br/media/sapl/public/parlamentar/256/alex.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alero/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Alex Mendonça Alves", nomesCivis.get("256").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alero/blocos/256.json");
        assertTrue(Files.exists(arqBloco), "bloco 256.json deve existir");
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
        assertEquals("2025-02-01", orgaos.get(0).get("inicio").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alero/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
