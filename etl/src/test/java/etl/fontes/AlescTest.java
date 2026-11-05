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
import etl.Texto;
import tools.jackson.databind.JsonNode;

class AlescTest {

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Alex Brasil", Alesc.formatarNome("ALEX BRASIL"));
        assertEquals("Altair Silva", Alesc.formatarNome("ALTAIR SILVA"));
        assertEquals("Ana Campagnolo", Alesc.formatarNome("ANA CAMPAGNOLO"));
        assertEquals("Fabiano da Luz", Alesc.formatarNome("FABIANO DA LUZ"));
        assertEquals("Mauro de Nadal", Alesc.formatarNome("MAURO DE NADAL"));
    }

    @Test
    void normalizaFotoUrlRemovendoSufixoDeMiniatura() {
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500.jpg",
                Alesc.normalizarFotoUrl("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg"));
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Ana-Campagnolo-PL-scaled-aspect-ratio-500-500.png",
                Alesc.normalizarFotoUrl("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Ana-Campagnolo-PL-scaled-aspect-ratio-500-500-150x150.png"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <article class="lab-card-team mt-5">
                    <div class="row align-items-center flex-xl-nowrap">
                        <div class="col-xl-4 mb-3 mb-xl-0">
                            <a href="https://www.alesc.sc.gov.br/deputado/alex-brasil/">
                                <div class="row align-items-center gx-3">
                                    <div class="col-auto">
                                        <img src="https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg" alt="Alex Brasil" class="lab-team-img" width="150" height="150" loading="lazy">
                                    </div>
                                    <div class="col">
                                        <h3 class="lab-title-news">Alex Brasil</h3>
                                        <span class="lab-button px-2 py-1 lab-text mt-2" style="background: #404040; color: #ffffff;">PL</span>
                                    </div>
                                </div>
                            </a>
                        </div>
                    </div>
                </article>
                <article class="lab-card-team mt-5">
                    <div class="row align-items-center flex-xl-nowrap">
                        <div class="col-xl-4 mb-3 mb-xl-0">
                            <a href="https://www.alesc.sc.gov.br/deputado/altair-silva/">
                                <div class="row align-items-center gx-3">
                                    <div class="col-auto">
                                        <img src="https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Altair-Silva-PP-scaled-aspect-ratio-500-500-150x150.jpg" alt="Altair Silva" class="lab-team-img" width="150" height="150" loading="lazy">
                                    </div>
                                    <div class="col">
                                        <h3 class="lab-title-news">Altair Silva</h3>
                                        <span class="lab-button px-2 py-1 lab-text mt-2" style="background: #404040; color: #ffffff;">PP</span>
                                    </div>
                                </div>
                            </a>
                        </div>
                    </div>
                </article>
                """;

        List<Alesc.DeputadoBruto> deputados = Alesc.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        Alesc.DeputadoBruto d1 = deputados.get(0);
        assertEquals("alex-brasil", d1.slug());
        assertEquals("Alex Brasil", d1.nome());
        assertEquals("PL", d1.partido());
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg", d1.fotoUrl());

        Alesc.DeputadoBruto d2 = deputados.get(1);
        assertEquals("altair-silva", d2.slug());
        assertEquals("Altair Silva", d2.nome());
        assertEquals("PP", d2.partido());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String modalHtml = """
                <dl class="mb-3">
                    <dt>Presidente</dt>
                    <dd>Pepê Collaço</dd>
                </dl>
                <dl class="mb-3">
                    <dt>Vice-Presidente</dt>
                    <dd>Rodrigo Minotto</dd>
                </dl>
                <dl class="mb-3">
                    <dt>Membro</dt>
                    <dd>Alex Brasil</dd>
                    <dd>Fabiano da Luz</dd>
                </dl>
                """;

        Map<String, String> slugPorNome = Map.of(
                Texto.normalizarNome("Pepê Collaço"), "pepe-collaco",
                Texto.normalizarNome("Rodrigo Minotto"), "rodrigo-minotto",
                Texto.normalizarNome("Alex Brasil"), "alex-brasil",
                Texto.normalizarNome("Fabiano da Luz"), "fabiano-da-luz"
        );

        Prov prov = new Prov("alesc-deputados-detalhes", "https://portalelegis.alesc.sc.gov.br/comissoes-permanentes/bzJD5", Instant.parse("2026-11-05T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> resultado = Alesc.extrairComissoesDoHtml(
                modalHtml, "Comissão de Constituição e Justiça", slugPorNome, prov);

        assertEquals(4, resultado.size());
        assertEquals("Presidente", resultado.get("pepe-collaco").get(0).papel());
        assertEquals("Vice-Presidente", resultado.get("rodrigo-minotto").get(0).papel());
        assertEquals("Membro", resultado.get("alex-brasil").get(0).papel());
        assertEquals("Comissão de Constituição e Justiça", resultado.get("alex-brasil").get(0).nome());
        assertEquals("comissao", resultado.get("alex-brasil").get(0).tipo());
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-05T12:00:00Z"), null);

        Path rawAlesc = ctx.raw().resolve("alesc");
        Files.createDirectories(rawAlesc.resolve("perfis"));
        Files.createDirectories(rawAlesc.resolve("comissoes"));

        String html = """
                <article class="lab-card-team mt-5">
                    <div class="row align-items-center flex-xl-nowrap">
                        <div class="col-xl-4 mb-3 mb-xl-0">
                            <a href="https://www.alesc.sc.gov.br/deputado/alex-brasil/">
                                <div class="row align-items-center gx-3">
                                    <div class="col-auto">
                                        <img src="https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg" alt="Alex Brasil" class="lab-team-img" width="150" height="150" loading="lazy">
                                    </div>
                                    <div class="col">
                                        <h3 class="lab-title-news">Alex Brasil</h3>
                                        <span class="lab-button px-2 py-1 lab-text mt-2" style="background: #404040; color: #ffffff;">PL</span>
                                    </div>
                                </div>
                            </a>
                        </div>
                    </div>
                </article>
                """;

        Files.writeString(rawAlesc.resolve("deputados.html"), html);
        gravarMetadado(rawAlesc, "deputados.html", "https://www.alesc.sc.gov.br/deputados/");

        Alesc alesc = new Alesc();
        alesc.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alesc/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("alesc", dep.get("fonte").stringValue());
        assertEquals("alex-brasil", dep.get("id_fonte").stringValue());
        assertEquals("Alex Brasil", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("SC", dep.get("ente").stringValue());
        assertEquals("SC", dep.get("uf").stringValue());
        assertEquals("PL", dep.get("partido").stringValue());
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500.jpg", dep.get("foto_url").stringValue());

        Path blocoJson = ctx.normalizado().resolve("alesc/blocos/alex-brasil.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        assertNotNull(bloco.get("mandatos"));
        assertEquals(1, bloco.get("mandatos").size());
        assertEquals("deputado_estadual", bloco.get("mandatos").get(0).get("cargo").stringValue());
        assertEquals("SC", bloco.get("mandatos").get(0).get("uf").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alesc/" + arquivo, "abc", 100L, Instant.parse("2026-11-05T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
