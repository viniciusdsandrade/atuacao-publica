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
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Prov;
import etl.Registros.Voto;
import tools.jackson.databind.JsonNode;

class AlrsTest {

    @Test
    void votosDaPaginaAnualComDuasVotacoesDaMesmaProposicaoNoDia() {
        // Duas linhas oficiais da página de 2025 do deputado 2144, com o escape do atributo como a fonte publica.
        String html = "<tr data-item='&#x7B;&quot;nomeDeputado&quot;&#x3A;&quot;Ad&#x5C;u00e3o&#x20;Pretto&#x20;Filho&quot;,"
                + "&quot;dataVotacao&quot;&#x3A;&quot;16&#x5C;&#x2F;12&#x5C;&#x2F;2025&#x20;00&#x3A;00&quot;,&quot;tipoProjeto&quot;&#x3A;"
                + "&quot;PL&#x20;&quot;,&quot;numProposicao&quot;&#x3A;319,&quot;anoProposicao&quot;&#x3A;2025,&quot;materia&quot;&#x3A;"
                + "&quot;Altera&#x20;o&#x20;Anexo&#x20;da&#x20;lei&#x20;16.005,&#x20;de&#x20;20&#x20;de&#x20;outubro&#x20;de&#x20;2023,"
                + "&#x20;que&#x20;disp&#x5C;u00f5e&#x20;sobre&#x20;o&#x20;Plano&#x20;Plurianual&#x20;para&#x20;o&#x20;quadri&#x5C;u00eanio"
                + "&#x20;2024-2027,&#x20;e&#x20;d&#x5C;u00e1&#x20;outras&#x20;provid&#x5C;u00eancias.&quot;,&quot;voto&quot;&#x3A;"
                + "&quot;Sim&quot;,&quot;resultadoVotacao&quot;&#x3A;&quot;Aprovado&quot;&#x7D;'></tr>\n"
                + "<tr data-item='&#x7B;&quot;nomeDeputado&quot;&#x3A;&quot;Ad&#x5C;u00e3o&#x20;Pretto&#x20;Filho&quot;,"
                + "&quot;dataVotacao&quot;&#x3A;&quot;16&#x5C;&#x2F;12&#x5C;&#x2F;2025&#x20;00&#x3A;00&quot;,&quot;tipoProjeto&quot;&#x3A;"
                + "&quot;PL&#x20;&quot;,&quot;numProposicao&quot;&#x3A;319,&quot;anoProposicao&quot;&#x3A;2025,&quot;materia&quot;&#x3A;"
                + "&quot;Requer,&#x20;com&#x20;base&#x20;nos&#x20;arts.&#x20;138,&#x20;&#x5C;u00a7&#x20;3&#x5C;u00ba&#x20;e&#x20;194,"
                + "&#x20;VIII,&#x20;do&#x20;Regimento&#x20;Interno&#x20;da&#x20;Assembleia&#x20;Legislativa&#x20;do&#x20;Estado,&#x20;a"
                + "&#x20;prefer&#x5C;u00eancia&#x20;para&#x20;a&#x20;vota&#x5C;u00e7&#x5C;u00e3o&#x20;&#x20;do&#x20;texto&#x20;do&#x20;Projeto"
                + "&#x20;de&#x20;Lei&#x20;n&#x5C;u00ba&#x20;&#x20;319&#x5C;&#x2F;2025.&quot;,&quot;voto&quot;&#x3A;&quot;N&#x5C;u00e3o&quot;,"
                + "&quot;resultadoVotacao&quot;&#x3A;&quot;Aprovado&quot;&#x7D;'></tr>";
        Prov prov = new Prov(Alrs.FONTE_VOTOS, Alrs.urlVotos("2144", 2025), Instant.parse("2026-11-07T12:00:00Z"));

        List<Voto> votos = Alrs.votosDaPagina(html, prov);

        assertEquals(2, votos.size());
        Voto primeiro = votos.get(0);
        assertEquals("PL-319-2025-2025-12-16", primeiro.votacaoId());
        assertEquals("2025-12-16", primeiro.data());
        assertEquals("PL 319/2025", primeiro.proposicao());
        assertEquals("Sim", primeiro.voto());
        assertTrue(primeiro.descricao().startsWith("Altera o Anexo da lei 16.005"));
        Voto segundo = votos.get(1);
        assertEquals("PL-319-2025-2025-12-16-2", segundo.votacaoId(), "segunda votação da proposição no dia");
        assertEquals("Não", segundo.voto());
        assertTrue(segundo.descricao().contains("preferência para a votação do texto"));
        assertEquals("https://transparencia.al.rs.gov.br/parlamentares/votos-plenario/pesquisa?solicitante=2144&ano=2025",
                Alrs.urlVotos("2144", 2025));
    }

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

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String html = """
                <div class="bloco-teste">
                    <script type="application/json" data-drupal-selector="drupal-settings-json">
                    {
                      "alergs_deputados": {
                        "comissoes": [
                          {
                            "idComissao": 3,
                            "tipoComissao": "Comissões Permanentes",
                            "nomeComissao": "Comissão de Agricultura, Pecuária, Pesca e Cooperativismo",
                            "situacaoComissao": "NAO"
                          },
                          {
                            "idComissao": 501,
                            "tipoComissao": "Comissões Especiais",
                            "nomeComissao": "Comissão Especial da Apicultura",
                            "situacaoComissao": "SIM"
                          }
                        ]
                      }
                    }
                    </script>
                </div>
                """;

        Prov prov = new Prov("alrs-deputados-detalhes", "https://ww4.al.rs.gov.br/deputados/13/comissoes", Instant.parse("2026-11-05T12:00:00Z"));
        List<OrgaoOuFrente> comissoes = Alrs.extrairComissoesDoHtml(html, prov);
        assertEquals(2, comissoes.size());

        OrgaoOuFrente c1 = comissoes.get(0);
        assertEquals("comissao", c1.tipo());
        assertEquals("Comissão de Agricultura, Pecuária, Pesca e Cooperativismo", c1.nome());
        assertEquals("Membro", c1.papel());
        assertEquals(Alrs.INICIO_LEG_56, c1.inicio());
        assertEquals(null, c1.fim());

        OrgaoOuFrente c2 = comissoes.get(1);
        assertEquals("comissao", c2.tipo());
        assertEquals("Comissão Especial da Apicultura", c2.nome());
        assertEquals("Membro", c2.papel());
        assertEquals(Alrs.INICIO_LEG_56, c2.inicio());
        assertEquals(Alrs.FIM_LEG_56, c2.fim());
    }

    private static void gravarMetadado(Path pastaFonte, String relativo, String url) throws IOException {
        Path arquivo = pastaFonte.resolve(relativo);
        Path meta = arquivo.resolveSibling(arquivo.getFileName() + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alrs/" + relativo, "hash-teste", 100, Instant.now());
        Json.gravar(meta, arq, true);
    }
}
