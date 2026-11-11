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

class AleacTest {

    @Test
    void parseMandatosExtraiInformacoesCorretamente() {
        String json = """
                {
                    "results": [
                        {
                            "id": 426,
                            "parlamentar": 248,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 8798
                        },
                        {
                            "id": 427,
                            "parlamentar": 249,
                            "titular": false,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "tipo_causa_fim_mandato": null,
                            "votos_recebidos": 5400
                        }
                    ]
                }
                """;

        JsonNode root = Json.FONTE.readTree(json);
        List<Aleac.MandatoInfo> mandatos = Aleac.parseMandatos(root);

        assertEquals(2, mandatos.size());

        Aleac.MandatoInfo m1 = mandatos.get(0);
        assertEquals(426, m1.id());
        assertEquals(248, m1.parlamentarId());
        assertTrue(m1.titular());
        assertEquals(LocalDate.of(2023, 2, 1), m1.inicio());
        assertEquals(LocalDate.of(2027, 1, 31), m1.fim());
        assertEquals(8798, m1.votos());

        Aleac.MandatoInfo m2 = mandatos.get(1);
        assertEquals(427, m2.id());
        assertEquals(249, m2.parlamentarId());
        assertFalse(m2.titular());
    }

    @Test
    void limparNomeParlamentarRemovePrefixos() {
        assertEquals("ADAILTON CRUZ", Aleac.limparNomeParlamentar("DEPUTADO ADAILTON CRUZ"));
        assertEquals("MICHELE MELO", Aleac.limparNomeParlamentar("DEPUTADA MICHELE MELO"));
        assertEquals("Afonso Fernandes", Aleac.limparNomeParlamentar("Afonso Fernandes"));
    }

    @Test
    void formatarUrlFotoConverteHttpParaHttps() {
        assertEquals("https://sapl.al.ac.leg.br/media/sapl/public/parlamentar/248/foto.jpg",
                Aleac.formatarUrlFoto("http://sapl.al.ac.leg.br/media/sapl/public/parlamentar/248/foto.jpg"));
        assertEquals("https://sapl.al.ac.leg.br/media/foto.jpg",
                Aleac.formatarUrlFoto("/media/foto.jpg"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAleac = ctx.raw().resolve("aleac");
        Files.createDirectories(rawAleac.resolve("parlamentares"));
        Files.createDirectories(rawAleac.resolve("filiacoes"));
        Files.createDirectories(rawAleac.resolve("composicao_mesa"));

        // 1. mandatos.json
        String mandatosJson = """
                {
                    "results": [
                        {
                            "id": 426,
                            "parlamentar": 248,
                            "titular": true,
                            "data_inicio_mandato": "2023-02-01",
                            "data_fim_mandato": "2027-01-31",
                            "votos_recebidos": 8798
                        }
                    ]
                }
                """;
        Files.writeString(rawAleac.resolve("mandatos.json"), mandatosJson);
        gravarMetadado(rawAleac, "mandatos.json", Aleac.API + "/parlamentares/mandato/?legislatura=" + Aleac.ID_LEGISLATURA_16);

        // 2. parlamentares/248.json
        String parlJson = """
                {
                    "id": 248,
                    "nome_completo": "Adailton Cruz de Oliveira",
                    "nome_parlamentar": "DEPUTADO ADAILTON CRUZ",
                    "ativo": true,
                    "biografia": "<p>Enfermeiro e líder comunitário no Acre. CPF 123.456.789-00.</p>",
                    "fotografia": "http://sapl.al.ac.leg.br/media/sapl/public/parlamentar/248/adailton.jpg",
                    "telefone": "3214-5000",
                    "email": "dep.adailtoncruz@al.ac.leg.br"
                }
                """;
        Files.writeString(rawAleac.resolve("parlamentares/248.json"), parlJson);
        gravarMetadado(rawAleac, "parlamentares/248.json", Aleac.API + "/parlamentares/parlamentar/248/");

        // 3. filiacoes/248.json
        String filiacaoJson = """
                {
                    "results": [
                        {
                            "id": 141,
                            "__str__": "DEPUTADO ADAILTON CRUZ - PSB - Partido Socialista Brasileiro",
                            "data_desfiliacao": null,
                            "parlamentar": 248
                        }
                    ]
                }
                """;
        Files.writeString(rawAleac.resolve("filiacoes/248.json"), filiacaoJson);
        gravarMetadado(rawAleac, "filiacoes/248.json", Aleac.API + "/parlamentares/filiacao/?parlamentar=248");

        // 4. mesas.json
        String mesasJson = """
                {
                    "results": [
                        {
                            "id": 35,
                            "descricao": "Mesa 2023-2024",
                            "__str__": "Mesa da 1º (2023 - 2024) sessao da 16ª (2023 - 2027) (Atual) Legislatura",
                            "data_inicio": "2023-02-01",
                            "data_fim": "2025-01-31"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleac.resolve("mesas.json"), mesasJson);
        gravarMetadado(rawAleac, "mesas.json", Aleac.API + "/parlamentares/mesadiretora/");

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
        Files.writeString(rawAleac.resolve("cargos_mesa.json"), cargosJson);
        gravarMetadado(rawAleac, "cargos_mesa.json", Aleac.API + "/parlamentares/cargomesa/");

        // 6. composicao_mesa.json
        String compJson = """
                {
                    "results": [
                        {
                            "id": 183,
                            "parlamentar": 248,
                            "cargo": 1,
                            "mesa_diretora": 35,
                            "__str__": "DEPUTADO ADAILTON CRUZ - Presidente"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleac.resolve("composicao_mesa.json"), compJson);
        gravarMetadado(rawAleac, "composicao_mesa.json", Aleac.API + "/parlamentares/composicaomesa/");

        // 7. tipos-materia.json e materias/248-2023-p1.json
        Files.createDirectories(rawAleac.resolve("materias"));
        String tiposJson = """
                {
                    "results": [
                        { "id": 1, "sigla": "PL", "descricao": "Projeto de Lei" }
                    ]
                }
                """;
        Files.writeString(rawAleac.resolve("tipos-materia.json"), tiposJson);
        gravarMetadado(rawAleac, "tipos-materia.json", Aleac.API + "/materia/tipomaterialegislativa/");

        String materiasJson = """
                {
                    "results": [
                        {
                            "id": 8101,
                            "tipo": 1,
                            "numero": 45,
                            "ano": 2023,
                            "ementa": "Dispõe sobre a valorização da saúde pública no Acre.",
                            "resultado": "Aprovado",
                            "em_tramitacao": false,
                            "data_apresentacao": "2023-05-10",
                            "texto_original": "https://sapl.al.ac.leg.br/media/texto.pdf",
                            "__str__": "Projeto de Lei nº 45 de 2023"
                        }
                    ]
                }
                """;
        Files.writeString(rawAleac.resolve("materias/248-2023-p1.json"), materiasJson);
        gravarMetadado(rawAleac, "materias/248-2023-p1.json", Aleac.API + "/materia/materialegislativa/?autores=248&ano=2023&page_size=100&page=1");

        new Aleac().normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("aleac/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("aleac", p1.get("fonte").stringValue());
        assertEquals("248", p1.get("id_fonte").stringValue());
        assertEquals("ADAILTON CRUZ", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("AC", p1.get("uf").stringValue());
        assertEquals("PSB", p1.get("partido").stringValue());
        assertEquals("https://sapl.al.ac.leg.br/media/sapl/public/parlamentar/248/adailton.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("aleac/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Adailton Cruz de Oliveira", nomesCivis.get("248").stringValue());

        Path arqBloco = ctx.normalizado().resolve("aleac/blocos/248.json");
        assertTrue(Files.exists(arqBloco), "bloco 248.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        // Biografia não deve conter CPF
        String bio = bloco.get("biografia").stringValue();
        assertFalse(bio.contains("123.456.789-00"), "CPF deve ter sido removido pela Guarda");
        assertTrue(bio.contains("Enfermeiro e líder comunitário no Acre"));

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
        assertEquals("2023-02-01", orgaos.get(0).get("inicio").stringValue());

        // Proposições
        assertEquals(1, bloco.get("proposicoes").size());
        JsonNode prop = bloco.get("proposicoes").get(0);
        assertEquals("8101", prop.get("id_fonte").stringValue());
        assertEquals("PL", prop.get("tipo").stringValue());
        assertEquals(45, prop.get("numero").asInt());
        assertEquals("Aprovado", prop.get("situacao").stringValue());
        assertEquals("aleac-proposicoes", prop.get("fonte_id").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/aleac/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
