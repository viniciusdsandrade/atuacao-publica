package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
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

class AlepeTest {

    @Test
    void formataNomeComPreposicoes() {
        assertEquals("Abimael Santos", Alepe.formatarNome("ABIMAEL SANTOS"));
        assertEquals("Álvaro Porto", Alepe.formatarNome("ÁLVARO PORTO"));
        assertEquals("Maria do Carmo", Alepe.formatarNome("MARIA DO CARMO"));
        assertEquals("Claudiano Martins Filho", Alepe.formatarNome("Claudiano Martins Filho"));
    }

    @Test
    void normalizaUrlFotoEPartido() {
        assertEquals("https://www.alepe.pe.gov.br/wp-content/foto.webp",
                Alepe.normalizarUrlFoto("/wp-content/foto.webp"));
        assertEquals("https://www.alepe.pe.gov.br/wp-content/foto%20perfil.webp",
                Alepe.normalizarUrlFoto("https://www.alepe.pe.gov.br/wp-content/foto perfil.webp"));
        assertEquals("https://www.alepe.pe.gov.br/img.php?src=teste&scale=1",
                Alepe.normalizarUrlFoto("https://www.alepe.pe.gov.br/img.php?src=teste&amp;scale=1"));

        assertEquals("PL", Alepe.normalizarPartido("PL"));
        assertEquals("PODE", Alepe.normalizarPartido("Podemos"));
        assertEquals("UNIÃO", Alepe.normalizarPartido("União"));
        assertEquals("REPUBLICANOS", Alepe.normalizarPartido("Republicanos"));
        assertEquals("NOVO", Alepe.normalizarPartido("Novo"));
    }

    @Test
    void extraiMunicipioEUfDeNaturalidade() {
        String[] r1 = Alepe.extrairMunicipioUf("Recife - PE");
        assertEquals("Recife", r1[0]);
        assertEquals("PE", r1[1]);

        String[] r2 = Alepe.extrairMunicipioUf("Canhotinho (PE)");
        assertEquals("Canhotinho", r2[0]);
        assertEquals("PE", r2[1]);

        String[] r3 = Alepe.extrairMunicipioUf("Caruaru");
        assertEquals("Caruaru", r3[0]);
        assertEquals(null, r3[1]);
    }

    @Test
    void extraiDeputadosDoHtml() {
        String html = """
                <html>
                <body>
                  <select name="field-deputados" id="field-deputados">
                    <option value="/parlamentar/abimael-santos">Abimael Santos</option>
                    <option value="/parlamentar/dani-portela">Dani Portela</option>
                  </select>
                  <ul class="parlamentares-list-ul">
                    <li>
                      <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                        <figure class="figure figure-avatar">
                          <img src="https://www.alepe.pe.gov.br/wp-content/abimael.webp" />
                        </figure>
                        <div class="text">
                          <h2 class="title parlamentares-nome">Abimael Santos</h2>
                          <span class="subtitle parlamentares-partido">PL</span>
                        </div>
                      </a>
                    </li>
                    <li>
                      <a href='/parlamentar/dani-portela' class="parlamentares-list-a">
                        <figure class="figure figure-avatar">
                          <img src="https://www.alepe.pe.gov.br/wp-content/dani.webp" />
                        </figure>
                        <div class="text">
                          <h2 class="title parlamentares-nome">Dani Portela</h2>
                          <span class="subtitle parlamentares-partido">PT</span>
                        </div>
                      </a>
                    </li>
                  </ul>
                </body>
                </html>
                """;

        List<Alepe.DeputadoBruto> deputados = Alepe.extrairDeputadosDeHtml(html);
        assertEquals(2, deputados.size());

        Alepe.DeputadoBruto d1 = deputados.get(0);
        assertEquals("abimael-santos", d1.slug());
        assertEquals("Abimael Santos", d1.nome());
        assertEquals("PL", d1.partido());
        assertEquals("https://www.alepe.pe.gov.br/wp-content/abimael.webp", d1.fotoUrl());

        Alepe.DeputadoBruto d2 = deputados.get(1);
        assertEquals("dani-portela", d2.slug());
        assertEquals("Dani Portela", d2.nome());
        assertEquals("PT", d2.partido());
    }

    @Test
    void extraiComissoesEMembros() {
        String htmlComissao = """
                <h1 class="title news-title">
                  <img src='/wp-content/icone.png' />
                  Constituição, Legislação e Justiça
                </h1>
                <ul class="parlamentares-list-ul">
                  <li>
                    <a href='/parlamentar/coronel-alberto-feitosa' class="parlamentares-list-a">
                      <div class="text">
                        <h2 class="title parlamentares-nome">Coronel Alberto Feitosa</h2>
                        <span class="subtitle parlamentares-partido">PRESIDENTE</span>
                      </div>
                    </a>
                  </li>
                  <li>
                    <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                      <div class="text">
                        <h2 class="title parlamentares-nome">Abimael Santos</h2>
                        <span class="subtitle parlamentares-partido">VICE-PRESIDENTE</span>
                      </div>
                    </a>
                  </li>
                  <li>
                    <a href='/parlamentar/dani-portela' class="parlamentares-list-a">
                      <div class="text">
                        <h2 class="title parlamentares-nome">Dani Portela</h2>
                        <span class="subtitle parlamentares-partido"></span>
                      </div>
                    </a>
                  </li>
                </ul>
                """;

        Prov prov = new Prov("alepe-deputados-detalhes", "https://www.alepe.pe.gov.br/comissao/ccj/", Instant.parse("2026-11-06T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        Alepe.extrairMembrosComissao(htmlComissao, "https://www.alepe.pe.gov.br/comissao/ccj/", prov, mapa);

        assertEquals(3, mapa.size());
        assertEquals("Presidente", mapa.get("coronel-alberto-feitosa").get(0).papel());
        assertEquals("Vice-Presidente", mapa.get("abimael-santos").get(0).papel());
        assertEquals("Membro", mapa.get("dani-portela").get(0).papel());
        assertEquals("Constituição, Legislação e Justiça", mapa.get("abimael-santos").get(0).nome());
    }

    @Test
    void normalizaDeputadoComPerfilEComissoes(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-06T12:00:00Z"), null);

        Path rawAlepe = ctx.raw().resolve("alepe");
        Files.createDirectories(rawAlepe.resolve("perfis"));
        Files.createDirectories(rawAlepe.resolve("comissoes"));

        String listaHtml = """
                <select name="field-deputados" id="field-deputados">
                  <option value="/parlamentar/abimael-santos">Abimael Santos</option>
                </select>
                <select name="field-comissoes" id="field-comissoes">
                  <option value="/comissao/constituicao-legislacao-e-justica">Constituição, Legislação e Justiça</option>
                </select>
                <ul class="parlamentares-list-ul">
                  <li>
                    <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                      <figure class="figure figure-avatar">
                        <img src="https://www.alepe.pe.gov.br/wp-content/abimael.webp" />
                      </figure>
                      <div class="text">
                        <h2 class="title parlamentares-nome">Abimael Santos</h2>
                        <span class="subtitle parlamentares-partido">PL</span>
                      </div>
                    </a>
                  </li>
                </ul>
                """;
        Files.writeString(rawAlepe.resolve("deputados.html"), listaHtml);
        gravarMetadado(rawAlepe, "deputados.html", "https://www.alepe.pe.gov.br/parlamentares/");

        String perfilHtml = """
                <link rel='shortlink' href='https://www.alepe.pe.gov.br/?p=481457' />
                <h1 class="title">Abimael Santos</h1>
                <div class="resume">
                  <p>Abimael Santos é deputado estadual de Pernambuco eleito para o primeiro mandato.</p>
                </div>
                <div class="parlamentares-view-info">
                  <dl>
                    <dt>Nome civil:</dt>
                    <dd>Abimael dos Santos Pereira</dd>
                    <dt>Naturalidade:</dt>
                    <dd>Recife - PE</dd>
                    <dt>Profissão:</dt>
                    <dd>Eletricista</dd>
                    <dt>Redes sociais:</dt>
                    <dd>
                      <a href="https://www.instagram.com/abimaelsantos.pernambuco">Instagram</a>
                    </dd>
                  </dl>
                </div>
                """;
        Files.writeString(rawAlepe.resolve("perfis/abimael-santos.html"), perfilHtml);
        gravarMetadado(rawAlepe, "perfis/abimael-santos.html", "https://www.alepe.pe.gov.br/parlamentar/abimael-santos/");

        String comissaoHtml = """
                <h1 class="title news-title">Constituição, Legislação e Justiça</h1>
                <ul class="parlamentares-list-ul">
                  <li>
                    <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                      <h2 class="title parlamentares-nome">Abimael Santos</h2>
                      <span class="subtitle parlamentares-partido">VICE-PRESIDENTE</span>
                    </a>
                  </li>
                </ul>
                """;
        Files.writeString(rawAlepe.resolve("comissoes/constituicao-legislacao-e-justica.html"), comissaoHtml);
        gravarMetadado(rawAlepe, "comissoes/constituicao-legislacao-e-justica.html", "https://www.alepe.pe.gov.br/comissao/constituicao-legislacao-e-justica/");

        Alepe alepe = new Alepe();
        alepe.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alepe/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("alepe", dep.get("fonte").stringValue());
        assertEquals("abimael-santos", dep.get("id_fonte").stringValue());
        assertEquals("Abimael Santos", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("PE", dep.get("ente").stringValue());
        assertEquals("PL", dep.get("partido").stringValue());

        Path blocoJson = ctx.normalizado().resolve("alepe/blocos/abimael-santos.json");
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
        assertEquals("Constituição, Legislação e Justiça", orgaos.get(0).get("nome").stringValue());
        assertEquals("Vice-Presidente", orgaos.get(0).get("papel").stringValue());

        assertEquals("Abimael dos Santos Pereira", bloco.get("nome_civil").stringValue());
        assertTrue(bloco.get("biografia").stringValue().contains("Abimael Santos é deputado estadual"));
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alepe/" + arquivo, "abc", 100L, Instant.parse("2026-11-06T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
