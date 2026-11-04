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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;

class AlmgTest {

    @Test
    void normalizaDataEmFormatosVariados() {
        assertEquals("1964-07-24", Almg.normalizarDataIso("24/7/1964"));
        assertEquals("1964-07-24", Almg.normalizarDataIso("24/07/1964"));
        assertEquals("1964-07-24", Almg.normalizarDataIso("1964-07-24"));
        assertEquals("1964-07-24", Almg.normalizarDataIso("1964-07-24T00:00:00"));
        assertNull(Almg.normalizarDataIso(null));
        assertNull(Almg.normalizarDataIso(""));
    }

    @Test
    void extraiDataDeObjetosSqlTimestampOuTexto() {
        JsonNode texto = Json.SAIDA.getNodeFactory().textNode("2024-05-30");
        assertEquals("2024-05-30", Almg.extrairData(texto));

        var obj = Json.SAIDA.createObjectNode();
        obj.put("@class", "sql-timestamp");
        obj.put("$", "2024-05-01");
        assertEquals("2024-05-01", Almg.extrairData(obj));

        assertNull(Almg.extrairData(null));
    }

    @Test
    void normalizaDeputadoComComissoesEDespesasProtegendoCpf(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-03T12:00:00Z"), null);

        Path rawAlmg = ctx.raw().resolve("almg");
        Files.createDirectories(rawAlmg.resolve("deputados"));
        Files.createDirectories(rawAlmg.resolve("comissoes"));
        Files.createDirectories(rawAlmg.resolve("verbas"));

        // 1. Pesquisa legislatura 20
        String pesquisaJson = """
                {
                  "listaResultadoPesquisaDeputado": [
                    {
                      "codSituacao": 1,
                      "numLegislatura": 20,
                      "lista": [
                        { "id": 26148, "nome": "Bruno Engler", "partido": "PL" }
                      ]
                    }
                  ]
                }
                """;
        Files.writeString(rawAlmg.resolve("pesquisa-leg-20.json"), pesquisaJson);
        gravarMetadado(rawAlmg, "pesquisa-leg-20.json", "https://dadosabertos.almg.gov.br/api/v2/legislaturas/pesquisa_deputados?leg=20&formato=json");

        // 2. Detalhe do deputado 26148
        String detalheJson = """
                {
                  "deputado": {
                    "id": 26148,
                    "nome": "Bruno Engler",
                    "partido": "PL",
                    "codigoSituacao": 1,
                    "situacao": "Em exercício",
                    "nomeServidor": "BRUNO ENGLER",
                    "dataNascimento": "17/6/1997",
                    "naturalidadeMunicipio": "Curitiba",
                    "naturalidadeUf": "PR",
                    "legislaturas": [
                      {
                        "numeroLegislatura": 19,
                        "inicioLegislatura": { "$": "2019-02-01" },
                        "terminoLegislatura": { "$": "2023-01-31" },
                        "tipoMandato": "Efetivo"
                      },
                      {
                        "numeroLegislatura": 20,
                        "inicioLegislatura": { "$": "2023-02-01" },
                        "terminoLegislatura": { "$": "2027-01-31" },
                        "tipoMandato": "Efetivo"
                      }
                    ]
                  }
                }
                """;
        Files.writeString(rawAlmg.resolve("deputados/26148.json"), detalheJson);
        gravarMetadado(rawAlmg, "deputados/26148.json", "https://dadosabertos.almg.gov.br/api/v2/deputados/26148?formato=json");

        // 3. Comissões
        String comissoesJson = """
                {
                  "list": [
                    {
                      "dataInicio": "2023-03-02",
                      "dataTermino": "2025-01-31",
                      "participacao": "Vice-Presidente",
                      "comissao": {
                        "id": 5,
                        "nome": "Constituição e Justiça",
                        "tipoComissao": { "descricao": "Comissão Permanente" }
                      }
                    }
                  ]
                }
                """;
        Files.writeString(rawAlmg.resolve("comissoes/26148.json"), comissoesJson);
        gravarMetadado(rawAlmg, "comissoes/26148.json", "https://dadosabertos.almg.gov.br/api/v2/deputados/26148/participacoes_comissoes?formato=json");

        // 4. Verbas de 2024-05 com um CNPJ válido e um CPF de pessoa física
        String verbaJson = """
                {
                  "list": [
                    {
                      "descTipoDespesa": "Combustível e lubrificante",
                      "listaDetalheVerba": [
                        {
                          "dataEmissao": { "$": "2024-05-30" },
                          "cpfCnpj": "18189993000157",
                          "valorDespesa": 210.06,
                          "nomeEmitente": "Auto Posto Cometa Ltda.",
                          "descDocumento": "311650"
                        },
                        {
                          "dataEmissao": { "$": "2024-05-20" },
                          "cpfCnpj": "12345678901",
                          "valorDespesa": 500.00,
                          "nomeEmitente": "Joao Prestador",
                          "descDocumento": "REC-01"
                        }
                      ]
                    }
                  ]
                }
                """;
        Files.writeString(rawAlmg.resolve("verbas/26148-2024-05.json"), verbaJson);
        gravarMetadado(rawAlmg, "verbas/26148-2024-05.json", "https://dadosabertos.almg.gov.br/api/v2/prestacao_contas/verbas_indenizatorias/deputados/26148/2024/05?formato=json");

        Almg almg = new Almg();
        almg.normalize(ctx);

        // Verifica pessoas.json
        Path pessoasPath = ctx.normalizado().resolve("almg/pessoas.json");
        assertTrue(Files.exists(pessoasPath));
        JsonNode pessoas = Json.ler(pessoasPath);
        assertEquals(1, pessoas.size());
        JsonNode p = pessoas.get(0);
        assertEquals("almg", p.path("fonte").asText());
        assertEquals("26148", p.path("id_fonte").asText());
        assertEquals("Bruno Engler", p.path("nome").asText());
        assertEquals("deputado_estadual", p.path("cargo").asText());
        assertEquals("MG", p.path("uf").asText());
        assertEquals("PL", p.path("partido").asText());
        assertTrue(p.path("em_exercicio").asBoolean());
        assertEquals("https://www.almg.gov.br/export/sites/portal/a-assembleia/deputados/fotos/26148.jpg", p.path("foto_url").asText());

        // Verifica blocos/26148.json
        Path blocosPath = ctx.normalizado().resolve("almg/blocos/26148.json");
        assertTrue(Files.exists(blocosPath));
        JsonNode blocos = Json.ler(blocosPath);

        // Mandatos
        JsonNode mandatos = blocos.path("mandatos");
        assertEquals(2, mandatos.size());

        // Órgãos
        JsonNode orgaos = blocos.path("orgaos_e_frentes");
        assertEquals(1, orgaos.size());
        assertEquals("Constituição e Justiça", orgaos.get(0).path("nome").asText());
        assertEquals("Vice-Presidente", orgaos.get(0).path("papel").asText());

        // Despesas: 2 despesas
        JsonNode despesas = blocos.path("despesas");
        assertEquals(2, despesas.size());

        // Primeira com CNPJ mantido
        JsonNode d1 = despesas.get(0);
        assertEquals("18189993000157", d1.path("fornecedor_cnpj").asText());
        assertEquals(210.06, d1.path("valor").asDouble());

        // Segunda com CPF descartado (nulo)
        JsonNode d2 = despesas.get(1);
        assertTrue(d2.path("fornecedor_cnpj").isNull(), "CPF de 11 dígitos deve virar null");
        assertEquals(500.0, d2.path("valor").asDouble());
    }

    private void gravarMetadado(Path pastaRaw, String arquivo, String url) throws IOException {
        Path meta = pastaRaw.resolve(arquivo + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/almg/" + arquivo, "hash", 100, Instant.now());
        Files.createDirectories(meta.getParent());
        Json.gravar(meta, arq);
    }
}
