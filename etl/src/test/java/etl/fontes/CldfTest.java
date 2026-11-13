package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class CldfTest {

    @Test
    void paginasSaemDoTotalPorqueOCampoLastVemSempreFalso() {
        assertEquals(0, Cldf.paginas(0));
        assertEquals(1, Cldf.paginas(2000));
        assertEquals(3, Cldf.paginas(4931));
        assertEquals("https://ple.cl.df.gov.br/pleservico/api/public/proposicao/filter?page=2&size=2000&sort=id,asc",
                Cldf.urlDaPagina(2));
    }

    @Test
    void proposicaoFicaComCadaDeputadoDaAutoria() {
        // Dado sintético no formato da API do PLe.
        JsonNode pagina = Json.FONTE.readTree("""
                {"totalElements": 3, "last": false, "content": [
                  {"id": 101, "tipoProposicao": "Indicação", "siglaNumeroAno": "IND 10/2024",
                   "ementa": "Sugere ao Poder Executivo a reforma\\nda escola.\\n\\n", "autoria": "Deputada Doutora Jane, Comissão de Economia, Orçamento e Finanças, Deputado João Cardoso , Deputada Doutora Jane",
                   "etapa": "Apresentação", "temaNome": "#Educação#Urbanismo#", "dataLeitura": "2024-03-05"},
                  {"id": 102, "tipoProposicao": "Projeto de Lei", "siglaNumeroAno": "PL 20/2024", "ementa": "Institui o dia estadual de exemplo.",
                   "autoria": "Deputado Ex Distrital", "etapa": "Comissões", "temaNome": "#Outro#", "dataLeitura": "2024-04-01"},
                  {"id": 103, "tipoProposicao": "Proc", "siglaNumeroAno": "PROC 3/2024", "ementa": "Prestação de contas.",
                   "autoria": "Poder Executivo", "etapa": "Apresentação", "temaNome": "#Outro#", "dataLeitura": "2024-05-01"}
                ]}
                """);
        Set<String> chaves = Set.of(Cldf.normalizarChaveNome("Doutora Jane"), Cldf.normalizarChaveNome("João Cardoso"));
        Prov prov = new Prov(Cldf.FONTE_PROPOSICOES, Cldf.urlDaPagina(0), Instant.parse("2026-11-07T12:00:00Z"));

        Map<String, List<Proposicao>> porChave = Cldf.proposicoesDaPagina(pagina, chaves, prov);

        assertEquals(2, porChave.size(), "ex-deputado e Poder Executivo ficam fora");
        List<Proposicao> daJane = porChave.get(Cldf.normalizarChaveNome("Doutora Jane"));
        assertEquals(1, daJane.size(), "autor repetido na autoria conta uma vez");
        Proposicao ind = daJane.getFirst();
        assertEquals("101", ind.idFonte());
        assertEquals("IND 10/2024", ind.identificacao());
        assertEquals(10, ind.numero());
        assertEquals(2024, ind.ano());
        assertEquals("Sugere ao Poder Executivo a reforma da escola.", ind.ementa());
        assertEquals("Apresentação", ind.situacao());
        assertEquals(List.of("Educação", "Urbanismo"), ind.temas());
        assertEquals("2024-03-05", ind.dataApresentacao());
        assertEquals("https://ple.cl.df.gov.br/#/proposicao/101/consultar", ind.urlTexto());
        assertEquals(1, ind.ordemAssinatura());
        assertEquals(4, porChave.get(Cldf.normalizarChaveNome("João Cardoso")).getFirst().ordemAssinatura(),
                "a ordem conta a posição na autoria, com a vírgula do nome da comissão");
    }

    @Test
    void normalizaPartidoEUrlFoto() {
        assertEquals("PT", Cldf.normalizarPartido("Partido dos Trabalhadores (PT)"));
        assertEquals("MDB", Cldf.normalizarPartido("Movimento Democrático Brasileiro (MDB)"));
        assertEquals("PSOL", Cldf.normalizarPartido("Partido Socialismo e Liberdade (PSOL)"));
        assertEquals("PODE", Cldf.normalizarPartido("Podemos (Podemos)"));
        assertEquals("REPUBLICANOS", Cldf.normalizarPartido("Republicanos"));
        assertEquals("UNIÃO", Cldf.normalizarPartido("União Brasil"));
        assertEquals("PP", Cldf.normalizarPartido("Progressistas (PP)"));

        assertEquals("https://www.cl.df.gov.br/documents/foto.jpg",
                Cldf.normalizarUrlFoto("/documents/foto.jpg"));
        assertEquals("https://www.cl.df.gov.br/documents/foto%20perfil.jpg",
                Cldf.normalizarUrlFoto("https://www.cl.df.gov.br/documents/foto perfil.jpg"));
    }

    @Test
    void limparNomeDeputadoENormalizarChave() {
        assertEquals("Chico Vigilante", Cldf.limparNomeDeputado("Deputado Chico Vigilante"));
        assertEquals("Paula Belmonte", Cldf.limparNomeDeputado("Deputada Paula Belmonte"));
        assertEquals("Fábio Felix", Cldf.limparNomeDeputado("Fábio Felix"));

        assertEquals("chico vigilante", Cldf.normalizarChaveNome("Deputado Chico Vigilante"));
        assertEquals("fabio felix", Cldf.normalizarChaveNome("Deputado Fábio Felix"));
        assertEquals("paula belmonte", Cldf.normalizarChaveNome("Deputada Paula Belmonte"));
    }

    @Test
    void decodificaUnicode() {
        assertEquals("Comissão de Transporte e Mobilidade Urbana",
                Cldf.decodificarUnicode("Comiss\\u00e3o de Transporte e Mobilidade Urbana"));
        assertEquals("Fábio Felix",
                Cldf.decodificarUnicode("F\\u00e1bio Felix"));
    }

    @Test
    void extraiDeputadosDoHtml() {
        String html = """
                <html>
                <body>
                  <div class="col-md-3 mb-4">
                    <a class="card card-image card-interactive card-interactive-primary card-type-template template-card h-100" href="/web/guest/chico-vigilante-2019-2022">
                      <img src="https://www.cl.df.gov.br/documents/chico.jpg" class="w-100" />
                      <span class="card-body">
                        <span class="card-title">Chico Vigilante</span>
                        <span class="card-text">Partido dos Trabalhadores (PT)</span>
                      </span>
                    </a>
                  </div>
                  <div class="col-md-3 mb-4">
                    <a class="card card-image card-interactive card-interactive-primary card-type-template template-card h-100" href="/paula-belmonte">
                      <img src="https://www.cl.df.gov.br/documents/paula.jpg" class="w-100" />
                      <span class="card-body">
                        <span class="card-title">Paula Belmonte</span>
                        <span class="card-text">Partido da Social Democracia Brasileira (PSDB)</span>
                      </span>
                    </a>
                  </div>
                </body>
                </html>
                """;

        List<Cldf.DeputadoBruto> deputados = Cldf.extrairDeputadosDeHtml(html);
        assertEquals(2, deputados.size());

        Cldf.DeputadoBruto d1 = deputados.get(0);
        assertEquals("chico-vigilante-2019-2022", d1.slug());
        assertEquals("Chico Vigilante", d1.nome());
        assertEquals("PT", d1.partido());
        assertEquals("https://www.cl.df.gov.br/documents/chico.jpg", d1.fotoUrl());

        Cldf.DeputadoBruto d2 = deputados.get(1);
        assertEquals("paula-belmonte", d2.slug());
        assertEquals("Paula Belmonte", d2.nome());
        assertEquals("PSDB", d2.partido());
    }

    @Test
    void extraiComissoesEMembrosDoHtml() {
        String html = """
                <script>
                var comissoes = [
                  {comissaoNome:"Comiss\\u00e3o de Transporte e Mobilidade Urbana",comissaoSigla:"CTMU",dataInicio:"2025-01-06",dataFim:"",membros:[
                    {tipoMembro:{nome:"Presidente"},autor:{id:"1",autorId:"10",nome:"Deputado Max Maciel"}},
                    {tipoMembro:{nome:"Vice-Presidente"},autor:{id:"2",autorId:"20",nome:"Deputada Paula Belmonte"}},
                    {tipoMembro:{nome:"Membro"},autor:{id:"3",autorId:"30",nome:"Deputado F\\u00e1bio Felix"}}
                  ],
                  suplentes:[
                    {autorSuplente:{id:"4",autorId:"40",nome:"Deputado Chico Vigilante"}}
                  ]}
                ];
                </script>
                """;

        Prov prov = new Prov("cldf-deputados-detalhes", "https://www.cl.df.gov.br/comissoes-permanentes", Instant.parse("2026-11-06T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> mapa = Cldf.extrairComissoesDeHtml(html, prov);

        assertEquals(4, mapa.size());

        List<OrgaoOuFrente> max = mapa.get("max maciel");
        assertNotNull(max);
        assertEquals(1, max.size());
        assertEquals("Presidente", max.get(0).papel());
        assertEquals("CTMU", max.get(0).sigla());

        List<OrgaoOuFrente> paula = mapa.get("paula belmonte");
        assertNotNull(paula);
        assertEquals("Vice-Presidente", paula.get(0).papel());

        List<OrgaoOuFrente> fabio = mapa.get("fabio felix");
        assertNotNull(fabio);
        assertEquals("Membro", fabio.get(0).papel());

        List<OrgaoOuFrente> chico = mapa.get("chico vigilante");
        assertNotNull(chico);
        assertEquals("Suplente", chico.get(0).papel());
    }

    @Test
    void normalizaDeputadoComPerfilEComissoes(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-06T12:00:00Z"), null);

        Path rawCldf = ctx.raw().resolve("cldf");
        Files.createDirectories(rawCldf.resolve("perfis"));

        String listaHtml = """
                <a class="card template-card" href="/web/guest/chico-vigilante-2019-2022">
                  <img src="https://www.cl.df.gov.br/documents/chico.jpg" />
                  <span class="card-title">Chico Vigilante</span>
                  <span class="card-text">Partido dos Trabalhadores (PT)</span>
                </a>
                """;
        Files.writeString(rawCldf.resolve("deputados.html"), listaHtml);
        gravarMetadado(rawCldf, "deputados.html", "https://www.cl.df.gov.br/deputados-2023-2026");

        String perfilHtml = """
                <html>
                <body>
                  <p>Quem poderia imaginar que um cidadão viria a construir uma belíssima história na capital?</p>
                  <p>Eleito com quase 45 mil votos para a sua quinta legislatura na Câmara Legislativa do DF.</p>
                  <p><a href="https://www.instagram.com/chicovigilanteoficial">Instagram</a></p>
                </body>
                </html>
                """;
        Files.writeString(rawCldf.resolve("perfis/chico-vigilante-2019-2022.html"), perfilHtml);
        gravarMetadado(rawCldf, "perfis/chico-vigilante-2019-2022.html", "https://www.cl.df.gov.br/web/guest/chico-vigilante-2019-2022");

        String comissaoHtml = """
                comissaoNome:"Comissão de Transporte e Mobilidade Urbana",comissaoSigla:"CTMU",membros:[
                  {tipoMembro:{nome:"Presidente"},autor:{nome:"Deputado Chico Vigilante"}}
                ]
                """;
        Files.writeString(rawCldf.resolve("comissoes.html"), comissaoHtml);
        gravarMetadado(rawCldf, "comissoes.html", "https://www.cl.df.gov.br/comissoes-permanentes");

        Cldf cldf = new Cldf();
        cldf.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("cldf/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("cldf", dep.get("fonte").stringValue());
        assertEquals("chico-vigilante-2019-2022", dep.get("id_fonte").stringValue());
        assertEquals("Chico Vigilante", dep.get("nome").stringValue());
        assertEquals("deputado_distrital", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("DF", dep.get("ente").stringValue());
        assertEquals("PT", dep.get("partido").stringValue());

        Path blocoJson = ctx.normalizado().resolve("cldf/blocos/chico-vigilante-2019-2022.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        JsonNode mandatos = bloco.get("mandatos");
        assertNotNull(mandatos);
        assertEquals(1, mandatos.size());

        JsonNode m1 = mandatos.get(0);
        assertEquals("deputado_distrital", m1.get("cargo").stringValue());
        assertEquals(9, m1.get("legislatura").asInt());
        assertEquals("Em exercício", m1.get("situacao").stringValue());

        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Comissão de Transporte e Mobilidade Urbana", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());

        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/cldf/" + arquivo, "abc", 100L, Instant.parse("2026-11-06T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
