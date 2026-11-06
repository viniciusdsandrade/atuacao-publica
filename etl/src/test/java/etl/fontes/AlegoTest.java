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
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

class AlegoTest {

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Alessandro Moreira dos Santos", Alego.formatarNome("ALESSANDRO MOREIRA DOS SANTOS"));
        assertEquals("Bruno Regiany Peixoto Pimenta", Alego.formatarNome("BRUNO REGIANY PEIXOTO PIMENTA"));
        assertEquals("Amauri Ribeiro", Alego.formatarNome("AMAURI RIBEIRO"));
        assertEquals("André do Premium", Alego.formatarNome("ANDRÉ DO PREMIUM"));
    }

    @Test
    void extraiSiglaPartidoCorretamente() {
        assertEquals("PRD", Alego.extrairSiglaPartido("PARTIDO DA RENOVAÇÃO DEMOCRÁTICA (PRD)"));
        assertEquals("PL", Alego.extrairSiglaPartido("PL (PL)"));
        assertEquals("MDB", Alego.extrairSiglaPartido("MOVIMENTO DEMOCRÁTICO BRASILEIRO (MDB)"));
        assertEquals("UB", Alego.extrairSiglaPartido("UNIÃO BRASIL (UB)"));
        assertEquals("PT", Alego.extrairSiglaPartido("PT"));
    }

    @Test
    void parseDataNascimentoFormatosExtensoEEstruturado() {
        assertEquals(LocalDate.of(1974, 5, 16), Alego.parseDataNascimento("16 de maio de 1974"));
        assertEquals(LocalDate.of(1972, 9, 1), Alego.parseDataNascimento("01 de setembro de 1972"));
        assertEquals(LocalDate.of(1980, 1, 25), Alego.parseDataNascimento("25/01/1980"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <tbody>
                        <tr data-target="search.filterable" data-filter-key='alessandro moreira partido da renovação democrática prd'>
                            <td data-title="Nome">
                                <a class="link" href=/deputados/perfil/808>Alessandro Moreira</a>
                            </td>
                            <td data-title="Partido">PARTIDO DA RENOVAÇÃO DEMOCRÁTICA (PRD)</td>
                            <td data-title="Telefones">
                                <span class="texto-nowrap"><a class='link' href='tel:6232213308'>(62) 3221-3308</a></span>
                            </td>
                        </tr>
                        <tr data-target="search.filterable" data-filter-key='bruno peixoto união brasil ub'>
                            <td data-title="Nome">
                                <a class="link" href=/deputados/perfil/51>Bruno Peixoto</a>
                            </td>
                            <td data-title="Partido">UNIÃO BRASIL (UB)</td>
                            <td data-title="Telefones">
                                <span class="texto-nowrap"><a class='link' href='tel:6232213200'>(62) 3221-3200</a></span>
                            </td>
                        </tr>
                </tbody>
                """;

        List<Alego.DeputadoBruto> deputados = Alego.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        Alego.DeputadoBruto d1 = deputados.get(0);
        assertEquals("808", d1.id());
        assertEquals("Alessandro Moreira", d1.nome());
        assertEquals("PARTIDO DA RENOVAÇÃO DEMOCRÁTICA (PRD)", d1.partido());

        Alego.DeputadoBruto d2 = deputados.get(1);
        assertEquals("51", d2.id());
        assertEquals("Bruno Peixoto", d2.nome());
        assertEquals("UNIÃO BRASIL (UB)", d2.partido());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String html = """
                <header class="heading">
                  <h2 class="titulo">
                    <div class="ico-texto">
                      <span class="texto">Comissão de Constituição, Justiça e Redação</span>
                    </div>
                  </h2>
                </header>
                <section class="equipe">
                  <ul class="lista">
                    <li class="lista__titulo">Titulares</li>
                    <li><b>Presidente:</b>
                      <a class="link" href="/deputados/perfil/152">Wagner Camargo Neto (SD)</a>
                    </li>
                    <li class="lista__item">
                      <b>Vice-presidente: </b>
                      <a class="link" href="/deputados/perfil/68">Talles Barreto (UB)</a>
                    </li>
                    <li class="lista__item">
                      <a class="link" href="/deputados/perfil/40">Mauro Rubem (PT)</a>
                    </li>
                  </ul>
                  <ul class="lista ">
                    <li class="lista__titulo">Suplentes</li>
                    <li class="lista__item">
                      <a class="link" href="/deputados/perfil/63">Karlos Cabral (PSB)</a>
                    </li>
                  </ul>
                </section>
                """;

        Prov prov = new Prov("alego-deputados-detalhes", "https://portal.al.go.leg.br/comissoes/1", Instant.parse("2026-11-06T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> resultado = Alego.extrairComissoesDoHtml(html, "1", prov);

        assertEquals(4, resultado.size());
        assertEquals("Presidente", resultado.get("152").get(0).papel());
        assertEquals("Vice-Presidente", resultado.get("68").get(0).papel());
        assertEquals("Titular", resultado.get("40").get(0).papel());
        assertEquals("Suplente", resultado.get("63").get(0).papel());
        assertEquals("Comissão de Constituição, Justiça e Redação", resultado.get("152").get(0).nome());
        assertEquals("comissao", resultado.get("152").get(0).tipo());
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-06T12:00:00Z"), null);

        Path rawAlego = ctx.raw().resolve("alego");
        Files.createDirectories(rawAlego.resolve("perfis"));
        Files.createDirectories(rawAlego.resolve("biografias"));
        Files.createDirectories(rawAlego.resolve("comissoes"));

        String htmlLista = """
                <tbody>
                    <tr data-target="search.filterable">
                        <td data-title="Nome"><a class="link" href=/deputados/perfil/808>Alessandro Moreira</a></td>
                        <td data-title="Partido">PARTIDO DA RENOVAÇÃO DEMOCRÁTICA (PRD)</td>
                    </tr>
                </tbody>
                """;
        Files.writeString(rawAlego.resolve("deputados.html"), htmlLista);
        gravarMetadado(rawAlego, "deputados.html", "https://portal.al.go.leg.br/deputados/em-exercicio");

        String htmlPerfil = """
                <header class="deputados__header">
                    <h2 class="titulo">Alessandro Moreira</h2>
                </header>
                <section class="perfil">
                    <figure class="perfil__foto">
                        <img class="foto" src="https://saba.al.go.leg.br/v1/view/portal/public/foto808.jpg" alt="Foto">
                    </figure>
                    <ul class="perfil__texto">
                        <li class="item">
                            <p><b>Nome Civil:</b> <span>Alessandro Moreira dos Santos</span></p>
                        </li>
                    </ul>
                </section>
                """;
        Files.writeString(rawAlego.resolve("perfis/808.html"), htmlPerfil);
        gravarMetadado(rawAlego, "perfis/808.html", "https://portal.al.go.leg.br/deputados/perfil/808");

        String htmlBio = """
                <article class="perfil-biografico__texto">
                    <strong>Data de Nascimento</strong>: 01 de setembro de 1972<br />
                    <strong>Naturalidade:</strong> Anápolis – GO<br />
                </article>
                """;
        Files.writeString(rawAlego.resolve("biografias/808.html"), htmlBio);
        gravarMetadado(rawAlego, "biografias/808.html", "https://portal.al.go.leg.br/deputados/perfil-biografico/808");

        Alego alego = new Alego();
        alego.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alego/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("alego", dep.get("fonte").stringValue());
        assertEquals("808", dep.get("id_fonte").stringValue());
        assertEquals("Alessandro Moreira", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("GO", dep.get("ente").stringValue());
        assertEquals("PRD", dep.get("partido").stringValue());
        assertEquals("https://saba.al.go.leg.br/v1/view/portal/public/foto808.jpg", dep.get("foto_url").stringValue());

        Path blocoJson = ctx.normalizado().resolve("alego/blocos/808.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        JsonNode mandatos = bloco.get("mandatos");
        assertNotNull(mandatos);
        assertEquals(1, mandatos.size());

        JsonNode m1 = mandatos.get(0);
        assertEquals("deputado_estadual", m1.get("cargo").stringValue());
        assertEquals(20, m1.get("legislatura").asInt());
        assertEquals("Em exercício", m1.get("situacao").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alego/" + arquivo, "abc", 100L, Instant.parse("2026-11-06T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
