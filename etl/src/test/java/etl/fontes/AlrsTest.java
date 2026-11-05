package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import tools.jackson.databind.JsonNode;

class AlrsTest {

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Adão Pretto Filho", Alrs.formatarNome("ADÃO PRETTO FILHO"));
        assertEquals("Adolfo Brito", Alrs.formatarNome("ADOLFO BRITO"));
        assertEquals("Adriana Lara", Alrs.formatarNome("ADRIANA LARA"));
        assertEquals("Beto Fantinel", Alrs.formatarNome("BETO FANTINEL"));
        assertEquals("Valdeci Oliveira", Alrs.formatarNome("VALDECI OLIVEIRA"));
        assertEquals("Zé Nunes", Alrs.formatarNome("ZÉ NUNES"));
    }

    @Test
    void extraiDeputadosDeFragmentoJson() throws Exception {
        String json = """
                {
                  "lista": [
                    {
                      "codigoPro": 2557,
                      "idDeputado": 2144,
                      "nomeDeputado": "Adão Pretto Filho",
                      "emailDeputado": "adao.prettofilho@al.rs.gov.br",
                      "telefoneDeputado": null,
                      "siglaPartido": "PT",
                      "nomePartido": "Partido dos Trabalhadores",
                      "codStatus": 1,
                      "fotoGrandeDeputado": "https://ww2.al.rs.gov.br/filerepository/fotografias/2023/3/24/360297_G.jpg"
                    },
                    {
                      "codigoPro": 13,
                      "idDeputado": 13,
                      "nomeDeputado": "Adolfo Brito",
                      "emailDeputado": "ajbrito@al.rs.gov.br",
                      "telefoneDeputado": "(51) 32102110",
                      "siglaPartido": "PP",
                      "nomePartido": "Progressistas",
                      "codStatus": 1,
                      "fotoGrandeDeputado": "https://ww2.al.rs.gov.br/filerepository/fotografias/2023/3/24/360299_G.jpg"
                    }
                  ]
                }
                """;

        JsonNode root = Json.SAIDA.readTree(json);
        List<Alrs.DeputadoBruto> deputados = Alrs.extrairDeputadosDoJson(root);
        assertEquals(2, deputados.size());

        Alrs.DeputadoBruto d1 = deputados.get(0);
        assertEquals("2144", d1.id());
        assertEquals("Adão Pretto Filho", d1.nome());
        assertEquals("PT", d1.partido());
        assertEquals("https://ww2.al.rs.gov.br/filerepository/fotografias/2023/3/24/360297_G.jpg", d1.fotoUrl());

        Alrs.DeputadoBruto d2 = deputados.get(1);
        assertEquals("13", d2.id());
        assertEquals("Adolfo Brito", d2.nome());
        assertEquals("PP", d2.partido());
        assertEquals("https://ww2.al.rs.gov.br/filerepository/fotografias/2023/3/24/360299_G.jpg", d2.fotoUrl());
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-05T12:00:00Z"), null);

        Path rawAlrs = ctx.raw().resolve("alrs");
        Files.createDirectories(rawAlrs.resolve("perfis"));

        String json = """
                {
                  "lista": [
                    {
                      "codigoPro": 2557,
                      "idDeputado": 2144,
                      "nomeDeputado": "Adão Pretto Filho",
                      "emailDeputado": "adao.prettofilho@al.rs.gov.br",
                      "telefoneDeputado": null,
                      "siglaPartido": "PT",
                      "nomePartido": "Partido dos Trabalhadores",
                      "codStatus": 1,
                      "fotoGrandeDeputado": "https://ww2.al.rs.gov.br/filerepository/fotografias/2023/3/24/360297_G.jpg"
                    }
                  ]
                }
                """;

        Files.writeString(rawAlrs.resolve("deputados.json"), json);
        gravarMetadado(rawAlrs, "deputados.json", "https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados");

        Alrs alrs = new Alrs();
        alrs.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alrs/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertEquals(1, pessoasNode.size());

        JsonNode p1 = pessoasNode.get(0);
        assertEquals("alrs", p1.get("fonte").stringValue());
        assertEquals("2144", p1.get("id_fonte").stringValue());
        assertEquals("Adão Pretto Filho", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("RS", p1.get("uf").stringValue());
        assertEquals("PT", p1.get("partido").stringValue());
        assertEquals("https://ww2.al.rs.gov.br/filerepository/fotografias/2023/3/24/360297_G.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").booleanValue());

        Path bloco2144 = ctx.normalizado().resolve("alrs/blocos/2144.json");
        assertTrue(Files.exists(bloco2144));
        JsonNode b2144 = Json.ler(bloco2144);
        assertNotNull(b2144.get("mandatos"));
        assertEquals(1, b2144.get("mandatos").size());

        JsonNode m1 = b2144.get("mandatos").get(0);
        assertEquals("2023-02-01", m1.get("inicio").stringValue());
        assertEquals("2027-01-31", m1.get("fim").stringValue());
        assertEquals(56, m1.get("legislatura").asInt());
    }

    private static void gravarMetadado(Path pastaFonte, String relativo, String url) throws IOException {
        Path arquivo = pastaFonte.resolve(relativo);
        Path meta = arquivo.resolveSibling(arquivo.getFileName() + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alrs/" + relativo, "hash-teste", 100, Instant.now());
        Json.gravar(meta, arq, true);
    }
}
