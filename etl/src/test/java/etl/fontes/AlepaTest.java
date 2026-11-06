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
