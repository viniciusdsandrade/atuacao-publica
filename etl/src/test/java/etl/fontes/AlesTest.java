package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

class AlesTest {

    @Test
    void nomeCivilDoPerfilComEntidadeHtmlCasaComODaCota() {
        assertEquals(Ales.chaveDoNomeCivil("Alc&#226;ntaro Filho "), Ales.chaveDoNomeCivil("Alcântaro Filho"));
        assertEquals("ALCANTARO FILHO", Ales.chaveDoNomeCivil("Alc&#226;ntaro Filho"));
    }

    @Test
    void gabinetesSaemDoSeletorDeSetorSemAOpcaoSelecione() {
        String html = """
                <select id="cboDeputado"><option value="305">Adilson Espindula</option></select>
                <select class="form-control" id="cboSetor" name="cboSetor">
                <option selected="selected" value="0">Selecione</option>
                <option value="319">Gab. Dep. Adilson Espindula</option>
                <option value="346">Gab. Dep. Alc&#226;ntaro Filho</option>
                </select>
                """;

        var gabinetes = Ales.gabinetesDaPagina(html);

        assertEquals(2, gabinetes.size());
        assertEquals("319", gabinetes.get(0).id());
        assertEquals("Gab. Dep. Alcântaro Filho", gabinetes.get(1).nome());
        assertEquals("https://www.al.es.gov.br/Transparencia/Api/CotasParlamentaresNovoTable/6/6/2024/319/false",
                Ales.urlDaCota("319", 2024, 6));
    }

    @Test
    void cotaDoMesTrazOsProdutosComValorENaoLeContato() {
        // Fragmento no formato da página de junho de 2024 do gabinete 319, com três produtos e a devolução de diária.
        String html = """
                <style>.x { color: red; }</style>
                <div class="deputado"><div><label class="fonte-titulo-pesquisa">Nome Civil: </label><span> Adilson Espindula</span></div>
                <div><label class="fonte-titulo-pesquisa">Telefone: </label><span> (27) 3382-0000 </span><label> E-mail: </label><span> gabinete@example.org</span></div></div>
                <table><tr><td>Produto:</td><td>C&#243;pias reprogr&#225;ficas</td></tr><tr><td>Quantidade:</td><td> 0 </td></tr><tr><td>Valor Solicitado:</td><td> R$ 0,00 </td></tr>
                <tr><td>Produto:</td><td>Peri&#243;dicos</td></tr><tr><td>Quantidade:</td><td> 1 </td></tr><tr><td>Valor Solicitado:</td><td> R$ 89,90 </td></tr>
                <tr><td>Produto:</td><td>Telefone</td></tr><tr><td>Quantidade:</td><td> 2 </td></tr><tr><td>Valor Solicitado:</td><td> R$ 636,00 </td></tr>
                <tr><td>Produto:</td><td>Devolu&#231;&#227;o de di&#225;ria</td></tr><tr><td>Quantidade:</td><td> R$ 12,50 </td></tr><tr><td>Valor Solicitado:</td><td> - </td></tr>
                <tr><td>Total:</td><td> R$ 738,40 </td></tr></table>
                <table><tr><th>Produto</th><th>Quantidade</th><th>Valor Solicitado</th></tr><tr><td>Telefone</td><td>2</td><td>R$ 636,00</td></tr></table>
                """;

        var cota = Ales.cotaDoMes(html);

        assertEquals("Adilson Espindula", cota.nomeCivil());
        assertEquals(3, cota.produtos().size(), "o produto de valor zero fica fora");
        assertEquals("Periódicos", cota.produtos().get(0).produto());
        assertEquals(new java.math.BigDecimal("636.00"), cota.produtos().get(1).valor());
        assertEquals("Devolução de diária", cota.produtos().get(2).produto());
        assertEquals(new java.math.BigDecimal("12.50"), cota.produtos().get(2).valor(), "o valor está na coluna da quantidade");
        assertEquals(new java.math.BigDecimal("738.40"), cota.totalDeclarado());
        assertEquals(0, Ales.cotaDoMes("<div>Nenhuma despesa encontrada para os par&#226;metros de busca informados!</div>").produtos().size());
    }

    @Test
    void proposicaoFicaComODeputadoAutorPeloNomeSemAcento() {
        // Dado sintético no formato da API NoPaper, sem a tramitação.
        JsonNode pagina = Json.FONTE.readTree("""
                {"total": 3, "message": "success", "Data": [
                  {"id": 1001, "sigla": "PL", "tipo": "Projeto de Lei", "numero": "690", "ano": "2024",
                   "assunto": "Dispõe sobre a capacitação\\r\\n de comissários de bordo", "data": "31/12/2024 19:28:04",
                   "situacao": "Tramitando", "arquivo": "https://www3.al.es.gov.br/Arquivo/Documents/PL/1001.pdf",
                   "AutorRequerenteDados": {"nomeRazao": "Sérgio Exemplo", "setor": "Gab. Dep. Sergio Exemplo"}, "CoAutores": null},
                  {"id": 1002, "sigla": "REQ", "tipo": "Requerimento                    ", "numero": "12", "ano": "2024",
                   "assunto": "Requer informações", "data": "02/02/2024 10:00:00", "situacao": "Arquivado", "arquivo": "",
                   "AutorRequerenteDados": {"nomeRazao": "Outra Pessoa", "setor": "Gab. Dep. Sergio Exemplo"}, "CoAutores": null},
                  {"id": 1003, "sigla": "PL", "tipo": "Projeto de Lei", "numero": "5", "ano": "2024", "assunto": "Mensagem",
                   "data": "05/01/2024 09:00:00", "situacao": "Tramitando", "arquivo": null,
                   "AutorRequerenteDados": {"nomeRazao": "GOVERNADOR DO ESTADO", "setor": "Secretaria de Estado de Governo"}}
                ]}
                """);
        Map<String, String> idPorChave = Map.of(Ales.chaveDeAutoria("Sergio Exemplo"), "999");
        Prov prov = new Prov(Ales.FONTE_PROPOSICOES, Ales.urlDaPagina("PL", 2024, 1, 20), Instant.parse("2026-11-07T12:00:00Z"));

        Map<String, List<Proposicao>> porId = Ales.proposicoesDaPagina(pagina, idPorChave, prov);

        assertEquals(1, porId.size());
        List<Proposicao> doDeputado = porId.get("999");
        assertEquals(1, doDeputado.size(), "o gabinete não liga a proposição de outro autor ao titular");
        Proposicao pl = doDeputado.getFirst();
        assertEquals("1001", pl.idFonte());
        assertEquals("PL 690/2024", pl.identificacao());
        assertEquals("Dispõe sobre a capacitação de comissários de bordo", pl.ementa());
        assertEquals("Tramitando", pl.situacao());
        assertEquals("2024-12-31", pl.dataApresentacao());
        assertEquals("https://www3.al.es.gov.br/Arquivo/Documents/PL/1001.pdf", pl.urlTexto());
        assertEquals(1, pl.ordemAssinatura());
        assertEquals(Ales.chaveDeAutoria("Engenheiro José Esmeraldo"), Ales.chaveDeAutoria("Engenherio José Esmeraldo"));
        assertEquals(Ales.chaveDeAutoria("Fábio Duarte"), Ales.chaveDeAutoria("Fábio Duarte de Almeida"));
    }

    @Test
    void paginaComItemDefeituosoEhRefeitaItemAItem() throws Exception {
        // 45 itens: a página 2 volta com erro por causa do item 24, como o PL 124 de 2022 na validação.
        java.util.List<String> pedidos = new java.util.ArrayList<>();
        Ales.Leitor leitor = (url, caminho) -> {
            pedidos.add(caminho);
            boolean erro = caminho.endsWith("-2.json") || caminho.endsWith("-item-24.json");
            String corpo = erro ? "{\"erro\":true,\"mensagem\":\"Nullable object must have a value.\"}"
                    : "{\"total\":45,\"Data\":[]}";
            return new Ales.Resposta(Json.FONTE.readTree(corpo), new Coleta.Arquivo(url, caminho, "x", 10, Instant.EPOCH));
        };

        List<Ales.Resposta> validas = Ales.respostasDoAno("PL", 2022, leitor);

        assertEquals(3, Ales.paginas(45));
        assertEquals(2 + 19, validas.size(), "páginas 1 e 3, e os itens 21 a 40 menos o 24");
        assertEquals(1 + 1 + 20 + 1, pedidos.size());
        assertTrue(pedidos.contains("proposicoes/PL-2022-item-21.json"));
        assertTrue(pedidos.contains("proposicoes/PL-2022-item-40.json"));
        assertTrue(pedidos.contains("proposicoes/PL-2022-3.json"));
        assertEquals("https://www3.al.es.gov.br/api/publico/proposicao/?pag=24&qtd=1&ano=2022&sigla=PL",
                Ales.urlDaPagina("PL", 2022, 24, 1));
    }

    @Test
    void geraSlugSemAcentosEEspacos() {
        assertEquals("AdilsonEspindula", Ales.gerarSlug("Adilson Espindula"));
        assertEquals("SergioMeneguelli", Ales.gerarSlug("Sérgio Meneguelli"));
        assertEquals("MarceloSantos", Ales.gerarSlug("Marcelo Santos"));
        assertEquals("JoaoCoser", Ales.gerarSlug("João Coser"));
        assertEquals("DaryPaghunzan", Ales.gerarSlug("Dary Paghunzan"));
    }

    @Test
    void decodificaEntidadesHtmlEEspacos() {
        assertEquals("João Sérgio de Menezes", Ales.decodificarHtml("Jo&#227;o S&#233;rgio de Menezes"));
        assertEquals("Constituição e Justiça", Ales.decodificarHtml("Constitui&#231;&#227;o e Justi&#231;a"));
        assertEquals("Espírito Santo", Ales.decodificarHtml("Esp&#237;rito Santo"));
    }

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Marcelo Santos", Ales.formatarNome("MARCELO SANTOS"));
        assertEquals("João Coser", Ales.formatarNome("JOÃO COSER"));
        assertEquals("Sergio Meneguelli", Ales.formatarNome("SERGIO MENEGUELLI"));
        assertEquals("Adilson Espíndula", Ales.formatarNome("ADILSON ESPÍNDULA"));
        assertEquals("Janete de Sá", Ales.formatarNome("JANETE DE SÁ"));
    }

    @Test
    void extraiDeputadosDoSelectHtml() {
        String html = """
                <html>
                <body>
                  <select id="ddlistDeputado" name="ddlistDeputado">
                    <option value="0">Selecione o Deputado</option>
                    <option value="305">Adilson Espindula</option>
                    <option value="306">Alcântaro Filho</option>
                    <option value="330">Marcelo Santos</option>
                    <option value="340">Sérgio Meneguelli</option>
                  </select>
                </body>
                </html>
                """;

        var matcher = Ales.SELECT_DEPUTADOS_PATTERN.matcher(html);
        assertTrue(matcher.find());
        String optionsHtml = matcher.group(1);

        var optMatcher = Ales.OPTION_DEPUTADO_PATTERN.matcher(optionsHtml);
        List<Ales.DeputadoBruto> deputados = new java.util.ArrayList<>();
        while (optMatcher.find()) {
            String val = optMatcher.group(1);
            String nome = optMatcher.group(2).trim();
            if (!val.equals("0")) {
                deputados.add(new Ales.DeputadoBruto(val, Ales.formatarNome(nome), Ales.gerarSlug(nome)));
            }
        }

        assertEquals(4, deputados.size());
        assertEquals("305", deputados.get(0).id());
        assertEquals("Adilson Espindula", deputados.get(0).nome());
        assertEquals("AdilsonEspindula", deputados.get(0).slug());

        assertEquals("330", deputados.get(2).id());
        assertEquals("Marcelo Santos", deputados.get(2).nome());
        assertEquals("MarceloSantos", deputados.get(2).slug());
    }

    @Test
    void extraiDadosDoPerfilDeputado() {
        String html = """
                <div id="divFotoDeputado">
                  <img src="/appdata/anexos_internet/portal/deputados/marcelo-santos.jpg" />
                </div>
                <div>
                  <label>Nome Civil: </label>
                  <span>MARCELO SANTOS FERREIRA</span>
                </div>
                <div>
                  <label><b>Partido:</b></label>
                  <span>PODEMOS</span>
                </div>
                <div>
                  <label>Data de Nascimento: </label>
                  <span>25/01/1970</span>
                </div>
                """;

        var mFoto = Ales.FOTO_PATTERN.matcher(html);
        assertTrue(mFoto.find());
        assertEquals("/appdata/anexos_internet/portal/deputados/marcelo-santos.jpg", mFoto.group(1));

        var mNome = Ales.NOME_CIVIL_PATTERN.matcher(html);
        assertTrue(mNome.find());
        assertEquals("MARCELO SANTOS FERREIRA", mNome.group(1).trim());

        var mPart = Ales.PARTIDO_PATTERN.matcher(html);
        assertTrue(mPart.find());
        assertEquals("PODEMOS", mPart.group(1).trim());

        var mNasc = Ales.NASCIMENTO_PATTERN.matcher(html);
        assertTrue(mNasc.find());
        assertEquals("25/01/1970", mNasc.group(1).trim());
    }

    @Test
    void extraiComissaoDoHtml() {
        String html = """
                <div class="fonte-titulo-deputado">
                  <span>Constituição e Justiça</span>
                </div>
                <div>
                  <label>Presidente: </label>
                  <span>Mazinho dos Anjos</span>
                </div>
                <div>
                  <label>Vice-Presidente: </label>
                  <span>Fabrício Gandini</span>
                </div>
                <div id="divTextoMembrosEfetivos">
                  <div class="texto-conteudo-interno">
                    Lucas Scaramussa<br>
                    Marcelo Santos<br>
                    Denninho Silva
                  </div>
                </div>
                <div id="divTextoMembrosSuplentes">
                  <div class="texto-conteudo-interno">
                    João Coser<br>
                    Camila Valadão
                  </div>
                </div>
                """;

        Map<String, String> idPorNomeNorm = Map.of(
                Texto.normalizarNome("Mazinho dos Anjos"), "328",
                Texto.normalizarNome("Fabrício Gandini"), "318",
                Texto.normalizarNome("Lucas Scaramussa"), "326",
                Texto.normalizarNome("Marcelo Santos"), "330",
                Texto.normalizarNome("Denninho Silva"), "312",
                Texto.normalizarNome("João Coser"), "322",
                Texto.normalizarNome("Camila Valadão"), "310");

        Prov prov = new Prov("ales-deputados", "https://www.al.es.gov.br", Instant.parse("2026-11-06T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> mapa = Ales.extrairComissoesDoHtml(html, "ConstituicaoeJustica", prov, idPorNomeNorm);

        assertNotNull(mapa.get("328"));
        assertEquals("Presidente", mapa.get("328").get(0).papel());
        assertEquals("Comissão de Constituição e Justiça", mapa.get("328").get(0).nome());

        assertNotNull(mapa.get("318"));
        assertEquals("Vice-Presidente", mapa.get("318").get(0).papel());

        assertNotNull(mapa.get("330"));
        assertEquals("Membro Efetivo", mapa.get("330").get(0).papel());

        assertNotNull(mapa.get("322"));
        assertEquals("Membro Suplente", mapa.get("322").get(0).papel());
    }

    @Test
    void normalizaDeputadosCompletos(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-06T12:00:00Z"), null);

        Path rawAles = ctx.raw().resolve("ales");
        Files.createDirectories(rawAles.resolve("perfis"));
        Files.createDirectories(rawAles.resolve("comissoes"));

        String listaHtml = """
                <select id="ddlistDeputado">
                  <option value="330">Marcelo Santos</option>
                </select>
                """;
        Files.writeString(rawAles.resolve("deputados.html"), listaHtml);
        gravarMetadado(rawAles, "deputados.html", "https://www.al.es.gov.br/Deputado/Lista");

        String perfilHtml = """
                <div id="divFotoDeputado">
                  <img src="/appdata/anexos_internet/portal/deputados/marcelo-santos.jpg" />
                </div>
                <div>
                  <label>Nome Civil: </label>
                  <span>MARCELO SANTOS FERREIRA</span>
                </div>
                <div>
                  <label><b>Partido:</b></label>
                  <span>PODEMOS</span>
                </div>
                <div>
                  <label>Data de Nascimento: </label>
                  <span>25/01/1970</span>
                </div>
                """;
        Files.writeString(rawAles.resolve("perfis/330.html"), perfilHtml);
        gravarMetadado(rawAles, "perfis/330.html", "https://www.al.es.gov.br/Deputado/MarceloSantos");

        String comissaoHtml = """
                <div class="fonte-titulo-deputado">
                  <span>Constituição e Justiça</span>
                </div>
                <div id="divTextoMembrosEfetivos">
                  <div class="texto-conteudo-interno">
                    Marcelo Santos
                  </div>
                </div>
                """;
        Files.writeString(rawAles.resolve("comissoes/ConstituicaoeJustica.html"), comissaoHtml);
        gravarMetadado(rawAles, "comissoes/ConstituicaoeJustica.html", "https://www.al.es.gov.br/Comissao/ConstituicaoeJustica");

        Ales ales = new Ales();
        ales.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("ales/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("ales", dep.get("fonte").stringValue());
        assertEquals("330", dep.get("id_fonte").stringValue());
        assertEquals("Marcelo Santos", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("ES", dep.get("ente").stringValue());
        assertEquals("PODEMOS", dep.get("partido").stringValue());
        assertEquals("https://www.al.es.gov.br/appdata/anexos_internet/portal/deputados/marcelo-santos.jpg", dep.get("foto_url").stringValue());

        Path blocoJson = ctx.normalizado().resolve("ales/blocos/330.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        JsonNode mandatos = bloco.get("mandatos");
        assertNotNull(mandatos);
        assertEquals(1, mandatos.size());

        JsonNode m1 = mandatos.get(0);
        assertEquals("deputado_estadual", m1.get("cargo").stringValue());
        assertEquals(20, m1.get("legislatura").asInt());
        assertEquals("Em exercício", m1.get("situacao").stringValue());

        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Comissão de Constituição e Justiça", orgaos.get(0).get("nome").stringValue());
        assertEquals("Membro Efetivo", orgaos.get(0).get("papel").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/ales/" + arquivo, "abc", 100L, Instant.parse("2026-11-06T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
