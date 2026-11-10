package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class AlepaTest {

    @Test
    void listaMensalTrazOsDetalhesSemRepeticao() {
        // Trecho no formato da lista de setembro de 2025: o link vem entre aspas simples e repete no botão de download.
        String html = "<div class='pauta-item'><a class='pauta-item-title' href='/Legislativo/DetalhesVotacaoNominal?IdProposicao=15090&IdVotacao=8559'>"
                + "30/09/2025 - PROJETO DE LEI COMPLEMENTAR N º 11/2025 - 1º TURNO</a>"
                + "<a class='download-button' href='/Legislativo/DetalhesVotacaoNominal?IdProposicao=15090&IdVotacao=8559&type=DOC'>DOC</a></div>"
                + "<div class='pauta-item'><a class='pauta-item-title' href='/Legislativo/DetalhesVotacaoNominal?IdProposicao=15090&IdVotacao=8567'>"
                + "30/09/2025 - PROJETO DE LEI COMPLEMENTAR N º 11/2025 - 2º TURNO</a></div>";

        var votacoes = Alepa.votacoesDaLista(html);

        assertEquals(2, votacoes.size());
        assertEquals("8559", votacoes.get(0).idVotacao());
        assertEquals("/Legislativo/DetalhesVotacaoNominal?IdProposicao=15090&IdVotacao=8559", votacoes.get(0).caminho());
        assertEquals("https://www.alepa.pa.gov.br/Legislativo/VotacoesNominais?ano=2025&mes=9", Alepa.urlDaLista(2025, 9));
    }

    @Test
    void detalheDaVotacaoDaOVotoDeCadaSecaoEQuemNaoVotou() {
        // Trecho no formato do detalhe da votação 8559, com nomes de exemplo.
        String html = """
                <table class="table-header">
                <tr><td><strong>Tipo:</strong> SESS&#195;O ORDIN&#193;RIA</td><td><strong>Número:</strong> 29</td></tr>
                <tr><td><strong>Projeto:</strong> PROJETO DE LEI COMPLEMENTAR N &#186; 11/2025</td><td><strong>Data:</strong> 30/09/2025</td></tr>
                <tr><td><strong>Autor:</strong> TRIBUNAL DE CONTAS DO ESTADO</td><td><strong>Votação:</strong> VOTAÇÃO NOMINAL</td></tr>
                <tr><td><strong>Turno:</strong> 1&#186; TURNO</td><td><strong>Resultado:</strong> APROVADA</td></tr>
                </table>
                <div class="section-title">SIM</div>
                <div class="votos-lista"><div class="voto-item"> ADRIANO EXEMPLO </div><div class="voto-item"> MDB </div>
                <div class="voto-item"> &#194;NGELO EXEMPLO </div><div class="voto-item"> FE UNI&#195;O PP </div></div>
                <p class="quantidade">Quantidade: 2</p>
                <div class="section-title">N&#195;O</div>
                <div class="votos-lista"><div class="voto-item"> BRAZ EXEMPLO </div><div class="voto-item"> PSD </div></div>
                <div class="section-title">N&#195;O VOTARAM</div>
                <div class="votos-lista"><div class="voto-item"> CILENE EXEMPLO </div><div class="voto-item"> MDB </div></div>
                """;

        var detalhe = Alepa.detalheDaVotacao(html);

        assertEquals("2025-09-30", detalhe.data());
        assertEquals("PROJETO DE LEI COMPLEMENTAR N º 11/2025", detalhe.projeto());
        assertEquals("1º TURNO", detalhe.turno());
        assertEquals("APROVADA", detalhe.resultado());
        assertEquals(4, detalhe.votos().size());
        assertEquals("ÂNGELO EXEMPLO", detalhe.votos().get(1).nome());
        assertEquals("FE UNIÃO PP", detalhe.votos().get(1).partido());
        assertEquals("Sim", detalhe.votos().get(1).voto());
        assertEquals("Não", detalhe.votos().get(2).voto());
        assertEquals(Alepa.NAO_VOTOU, detalhe.votos().get(3).voto());
        assertEquals(Alepa.chaveDoNome("Ângelo Exemplo"), Alepa.chaveDoNome("ÂNGELO EXEMPLO"));
    }

    @Test
    void participacaoContaQuemNaoVotouComoVotacaoSemVoto() {
        Prov prov = new Prov(Alepa.FONTE_VOTACOES, Alepa.URL_VOTACOES, Instant.parse("2026-11-09T12:00:00Z"));
        var votos = List.of(
                new etl.Registros.Voto("1", "2025-09-30", "PLEN", "PL 1/2025", null, "Sim", "MDB", null, null, null, prov),
                new etl.Registros.Voto("2", "2025-10-01", "PLEN", "PL 2/2025", null, Alepa.NAO_VOTOU, "MDB", null, null, null, prov),
                new etl.Registros.Voto("3", "2024-05-21", "PLEN", "PEC 8/2023", null, "Abstenção", "MDB", null, null, null, prov));

        var participacao = Alepa.participacao(votos, prov);

        assertEquals(2, participacao.size());
        assertEquals("2024", participacao.get(0).periodo());
        var de2025 = participacao.get(1);
        assertEquals(2, de2025.votacoesNominais());
        assertEquals(1, de2025.votosRegistrados());
        assertEquals(1, de2025.semRegistro());
        assertEquals(Map.of("Sim", 1, Alepa.NAO_VOTOU, 1), de2025.porTipo());
    }

    @Test
    void grafiaDaPaginaDeVotosCasaComONomeDoPerfil() {
        // Nome na página de votos e nome do perfil em exercício, como a ALEPA publica os dois.
        assertEquals(Alepa.chaveDoNome(Alepa.formatarNome("AVEILTON")), Alepa.chaveDoVotante("AVEILTON SOUZA"));
        assertEquals(Alepa.chaveDoNome(Alepa.formatarNome("DEPUTADO BORDALO")), Alepa.chaveDoVotante("CARLOS BORDALO"));
        assertEquals(Alepa.chaveDoNome(Alepa.formatarNome("RONIE")), Alepa.chaveDoVotante("RONIE SILVA"));
        assertEquals(Alepa.chaveDoNome(Alepa.formatarNome("TORRINHO")), Alepa.chaveDoVotante("TORRINHO TORRES"));
        // Fora da tabela, vale a chave do próprio nome.
        assertEquals(Alepa.chaveDoNome("Maria do Carmo"), Alepa.chaveDoVotante("MARIA DO CARMO"));
    }

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Adriano Coelho", Alepa.formatarNome("ADRIANO COELHO"));
        assertEquals("Ana Cunha", Alepa.formatarNome("ANA CUNHA"));
        assertEquals("Eraldo Pimenta", Alepa.formatarNome("ERALDO PIMENTA"));
        assertEquals("Maria do Carmo", Alepa.formatarNome("MARIA DO CARMO"));
    }

    @Test
    void decodificaHtmlENormalizaUrlFoto() {
        assertEquals("Comissões e Representações", Alepa.decodificarHtml("Comiss&#245;es e Representa&#231;&#245;es"));
        assertEquals("https://alepa.quartertec.com.br/Content/Portal/PerfilAgentePolitico/DEPUTADO_Adriano%20Coelho.png",
                Alepa.normalizarUrlFoto("https://alepa.quartertec.com.br\\Content\\Portal\\PerfilAgentePolitico\\DEPUTADO_Adriano Coelho.png"));
        assertEquals("https://www.alepa.pa.gov.br/Content/Images/foto.jpg",
                Alepa.normalizarUrlFoto("/Content/Images/foto.jpg"));
    }

    @Test
    void extraiDeputadosDoCardViewDevExpress() {
        String html = """
                <html>
                <body>
                  <table id="cardView">
                    <div id="cardView_DXDataCard0" class="breakpoints-card-deputado">
                      <div class='card-wrapper-deputado'>
                        <div class='card-container'>
                          <img class='deputado-image' src='https://alepa.quartertec.com.br\\Content\\Portal\\PerfilAgentePolitico\\DEPUTADO_Adriano Coelho.png' />
                        </div>
                        <div class='card-info'>
                          <span>Deputado</span>
                          <span>Adriano Coelho</span>
                          <p>MDB</p>
                        </div>
                      </div>
                    </div>
                    <div id="cardView_DXDataCard1" class="breakpoints-card-deputado">
                      <div class='card-wrapper-deputado'>
                        <div class='card-container'>
                          <img class='deputado-image' src='https://alepa.quartertec.com.br\\Content\\Portal\\PerfilAgentePolitico\\DEPUTADA_Ana Cunha.png' />
                        </div>
                        <div class='card-info'>
                          <span>Deputada</span>
                          <span>Ana Cunha</span>
                          <p>MDB</p>
                        </div>
                      </div>
                    </div>
                  </table>
                  <script>
                    ASPx.createControl(MVCxClientCardView,'cardView','',{'callBack':function(arg) { ; },'stateObject':{'keys':['1024','1011']}});
                  </script>
                </body>
                </html>
                """;

        List<Alepa.DeputadoBruto> deputados = Alepa.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        Alepa.DeputadoBruto d1 = deputados.get(0);
        assertEquals("1024", d1.id());
        assertEquals("Adriano Coelho", d1.nome());
        assertEquals("MDB", d1.partido());
        assertEquals("https://alepa.quartertec.com.br/Content/Portal/PerfilAgentePolitico/DEPUTADO_Adriano%20Coelho.png", d1.fotoUrl());

        Alepa.DeputadoBruto d2 = deputados.get(1);
        assertEquals("1011", d2.id());
        assertEquals("Ana Cunha", d2.nome());
        assertEquals("MDB", d2.partido());
    }

    @Test
    void extraiComissoesEPapeisComSucesso() {
        String html = """
                <div class="comissoes-item">
                  <div class="comissoes-header">
                    <h1>COMISSÃO DE CONSTITUIÇÃO E JUSTIÇA E DE REDAÇÃO FINAL - CCJRF</h1>
                  </div>
                  <div class="comissoes-content">
                    <p class="comissoes-descricao">Atribuições regimentais da comissão.</p>
                    <div class="comissoes-participantes">
                      <div class="comissoes-participantes-info">
                        <h3>TITULARES</h3>
                        <a href='/Institucional/Deputado/1016'><p class='funcao-importante'>Eraldo Pimenta - MDB (PRESIDENTE)</p></a>
                        <a href='/Institucional/Deputado/1034'><p class='funcao-importante'>Elias Santiago - PT (VICE-PRESIDENTE)</p></a>
                        <a href='/Institucional/Deputado/1011'><p>Ana Cunha - MDB</p></a>
                      </div>
                      <div class="comissoes-participantes-info">
                        <h3>SUPLENTES</h3>
                        <a href='/Institucional/Deputado/1031'><p>Andréia Xarão - MDB</p></a>
                      </div>
                    </div>
                  </div>
                </div>
                """;

        Prov prov = new Prov("alepa-deputados-detalhes", "https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes", Instant.parse("2026-11-06T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> mapa = Alepa.extrairComissoesDoHtml(html, prov);

        assertEquals(4, mapa.size());

        assertNotNull(mapa.get("1016"));
        assertEquals("Presidente", mapa.get("1016").get(0).papel());
        assertEquals("COMISSÃO DE CONSTITUIÇÃO E JUSTIÇA E DE REDAÇÃO FINAL - CCJRF", mapa.get("1016").get(0).nome());

        assertNotNull(mapa.get("1034"));
        assertEquals("Vice-Presidente", mapa.get("1034").get(0).papel());

        assertNotNull(mapa.get("1011"));
        assertEquals("Titular", mapa.get("1011").get(0).papel());

        assertNotNull(mapa.get("1031"));
        assertEquals("Suplente", mapa.get("1031").get(0).papel());
    }

    @Test
    void normalizaDeputadosCompletos(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-06T12:00:00Z"), null);

        Path rawAlepa = ctx.raw().resolve("alepa");
        Files.createDirectories(rawAlepa.resolve("perfis"));

        String listaHtml = """
                <table id="cardView">
                  <div id="cardView_DXDataCard0" class="breakpoints-card-deputado">
                    <div class='card-wrapper-deputado'>
                      <div class='card-info'>
                        <span>Deputado</span>
                        <span>Adriano Coelho</span>
                        <p>MDB</p>
                      </div>
                    </div>
                  </div>
                </table>
                <script>
                  ASPx.createControl(MVCxClientCardView,'cardView','',{'stateObject':{'keys':['1024']}});
                </script>
                """;
        Files.writeString(rawAlepa.resolve("deputados.html"), listaHtml);
        gravarMetadado(rawAlepa, "deputados.html", "https://www.alepa.pa.gov.br/Home/Page/Deputados");

        String perfilHtml = """
                <div class="card-politico-img-wrapper">
                  <img src="https://alepa.quartertec.com.br/Content/Portal/PerfilAgentePolitico/DEPUTADO_Adriano Coelho.png" />
                </div>
                <div class="card-politico-info-wrapper">
                  <span>Nome completo: <b>Marco Adriano Alburquerque Coelho</b></span>
                  <span>Nascimento: <b>04.01.1989</b></span>
                  <span>Partido Político: <b>MDB</b></span>
                </div>
                <div id="bioContent">
                  <p>Marco Adriano Albuquerque Coelho é administrador e deputado estadual reeleito no Pará.</p>
                </div>
                """;
        Files.writeString(rawAlepa.resolve("perfis/1024.html"), perfilHtml);
        gravarMetadado(rawAlepa, "perfis/1024.html", "https://www.alepa.pa.gov.br/Institucional/Deputado/1024");

        String comissoesHtml = """
                <div class="comissoes-item">
                  <div class="comissoes-header">
                    <h1>COMISSÃO DE CONSTITUIÇÃO E JUSTIÇA - CCJ</h1>
                  </div>
                  <div class="comissoes-content">
                    <div class="comissoes-participantes-info">
                      <h3>TITULARES</h3>
                      <a href='/Institucional/Deputado/1024'><p>Adriano Coelho - MDB</p></a>
                    </div>
                  </div>
                </div>
                """;
        Files.writeString(rawAlepa.resolve("comissoes.html"), comissoesHtml);
        gravarMetadado(rawAlepa, "comissoes.html", "https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes");

        Alepa alepa = new Alepa();
        alepa.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alepa/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("alepa", dep.get("fonte").stringValue());
        assertEquals("1024", dep.get("id_fonte").stringValue());
        assertEquals("Adriano Coelho", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("PA", dep.get("ente").stringValue());
        assertEquals("MDB", dep.get("partido").stringValue());
        assertEquals("https://alepa.quartertec.com.br/Content/Portal/PerfilAgentePolitico/DEPUTADO_Adriano%20Coelho.png", dep.get("foto_url").stringValue());

        Path blocoJson = ctx.normalizado().resolve("alepa/blocos/1024.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        JsonNode mandatos = bloco.get("mandatos");
        assertNotNull(mandatos);
        assertEquals(1, mandatos.size());

        JsonNode m1 = mandatos.get(0);
        assertEquals("deputado_estadual", m1.get("cargo").stringValue());
        assertEquals(60, m1.get("legislatura").asInt());
        assertEquals("Em exercício", m1.get("situacao").stringValue());

        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("COMISSÃO DE CONSTITUIÇÃO E JUSTIÇA - CCJ", orgaos.get(0).get("nome").stringValue());
        assertEquals("Titular", orgaos.get(0).get("papel").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alepa/" + arquivo, "abc", 100L, Instant.parse("2026-11-06T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
