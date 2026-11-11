package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Prov;

class AlmtTest {

    @Test
    void parseDeputadosExtraiCardsComIdNomePartidoEFoto() {
        String html = """
                <div class="row">
                    <div class="col-6 col-md-4 col-lg-3 col-xl-2">
                        <div class="card rounded-0 my-3">
                            <a href="/parlamento/deputados/401/perfil">
                                <img class="card-img-top rounded-0" src="https://storage.al.mt.gov.br/api/v1/download/image/570629?width=420px&amp;quality=80" alt="Beto Dois a Um" loading="lazy"/>
                            </a>
                            <div class="card-body">
                                PODE
                                </span>
                                <h6 class="card-title fw-semibold fs-14">BETO DOIS A UM</h6>
                                <div class="d-grid">
                                    <a href="/parlamento/deputados/401/perfil" class="btn btn-sm btn-primary">Veja mais</a>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-6 col-md-4 col-lg-3 col-xl-2">
                        <div class="card rounded-0 my-3">
                            <a href="/parlamento/deputados/32/perfil">
                                <img class="card-img-top rounded-0" src="https://storage.al.mt.gov.br/api/v1/download/image/558725?width=420px&amp;quality=80" alt="Wilson Santos" loading="lazy"/>
                            </a>
                            <div class="card-body">
                                PSD
                                </span>
                                <h6 class="card-title fw-semibold fs-14">WILSON SANTOS</h6>
                                <div class="d-grid">
                                    <a href="/parlamento/deputados/32/perfil" class="btn btn-sm btn-primary">Veja mais</a>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                """;

        List<Almt.DeputadoBruto> deputados = Almt.parseDeputados(html);

        assertEquals(2, deputados.size());
        assertEquals("401", deputados.get(0).id());
        assertEquals("Beto Dois a Um", deputados.get(0).nome());
        assertEquals("PODE", deputados.get(0).partido());
        assertEquals("https://storage.al.mt.gov.br/api/v1/download/image/570629?width=420px&quality=80", deputados.get(0).fotoUrl());
        assertEquals("/parlamento/deputados/401/perfil", deputados.get(0).href());

        assertEquals("32", deputados.get(1).id());
        assertEquals("Wilson Santos", deputados.get(1).nome());
        assertEquals("PSD", deputados.get(1).partido());
        assertEquals("https://storage.al.mt.gov.br/api/v1/download/image/558725?width=420px&quality=80", deputados.get(1).fotoUrl());
    }

    @Test
    void parsePerfilExtraiNomeCivilNomeParlamentarPartidoBiografiaERedes() {
        String html = """
                <div class="container">
                    <div class="row align-items-end">
                        <div class="col-md-3 mb-2">
                            <a href="/parlamento/deputados/401/perfil">
                                <img src="https://storage.al.mt.gov.br/api/v1/download/image/570629?width=420px&amp;quality=80" alt="Beto Dois a Um"/>
                            </a>
                        </div>
                        <div class="col-md-8">
                            <h1 class="fw-bold border-bottom border-3 d-md-inline-block pb-2 mb-2 fs-30">Beto Dois a Um</h1>
                            <ul class="list-group">
                                <li class="list-group-item border-0 px-0 py-1">
                                    <span class="fw-bold">Nome civil:</span>
                                    Alberto Machado
                                </li>
                                <li class="list-group-item border-0 px-0 py-1">
                                    <span class="fw-bold">Nome parlamentar:</span>
                                    Beto Dois a Um
                                </li>
                                <li class="list-group-item border-0 px-0 py-1">
                                    <span class="fw-bold">Filiação partidária:</span>
                                    PODE
                                </li>
                            </ul>
                            <ul class="nav mt-2">
                                <li class="nav-item">
                                    <a href="https://twitter.com/betodoisaum" class="btn-icon fs-16 text-black" target="_blank">
                                        <i class="bi bi-twitter-x"></i>
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a href="https://www.instagram.com/betodoisaum" class="btn-icon fs-16 text-black" target="_blank">
                                        <i class="bi bi-instagram"></i>
                                    </a>
                                </li>
                            </ul>
                        </div>
                    </div>
                    <div class="row" data-controller="parlamento--biografia">
                        <div class="col-12 mt-md-4">
                            <h3 class="fw-bold text-size-xl mb-3">Perfil</h3>
                            <div class="d-none d-lg-block" data-parlamento--biografia-target="perfilBiografia">
                                <p>Alberto Machado, o <strong>Beto Dois a Um</strong> nasceu em 1976. Foi eleito deputado estadual em 2022 com 26.462 votos.</p>
                            </div>
                        </div>
                    </div>
                </div>
                """;

        Almt.PerfilDeputado perfil = Almt.parsePerfil(html);

        assertEquals("Alberto Machado", perfil.nomeCivil());
        assertEquals("Beto Dois a Um", perfil.nomeParlamentar());
        assertEquals("PODE", perfil.partido());
        assertEquals("https://storage.al.mt.gov.br/api/v1/download/image/570629?width=420px&quality=80", perfil.fotoUrl());
        assertEquals("Alberto Machado, o Beto Dois a Um nasceu em 1976. Foi eleito deputado estadual em 2022 com 26.462 votos.",
                perfil.biografia());
        assertEquals(2, perfil.redes().size());
        assertTrue(perfil.redes().contains("https://twitter.com/betodoisaum"));
        assertTrue(perfil.redes().contains("https://www.instagram.com/betodoisaum"));
    }

    @Test
    void parseMesaDiretoraExtraiCargosComDatasCorretas() {
        String html = """
                <h6>
                    1ª Sessão Legislativa ordinaria da 20ª Legislatura
                    <br>
                    01/02/2023 - 12/01/2024
                </h6>
                <div class="well well-sm mb-0 p-2 text-black">
                    <h6>
                        <b>Presidente</b>
                        <br>
                        Eduardo Botelho
                    </h6>
                </div>
                <div class="well well-sm mb-0 p-2 text-black">
                    <h6>
                        <b>1º Vice Presidente</b>
                        <br>
                        Janaina Riva
                    </h6>
                </div>
                <h6>
                    3ª Sessão Legislativa ordinaria da 20ª Legislatura
                    <br>
                    01/02/2025 - 30/01/2026
                </h6>
                <div class="well well-sm mb-0 p-2 text-black">
                    <h6>
                        <b>Presidente</b>
                        <br>
                        Max Russi
                    </h6>
                </div>
                """;

        Prov prov = new Prov(Almt.FONTE_MESA_DIRETORA, Almt.URL_MESA_DIRETORA, Instant.parse("2026-11-11T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> mesa = Almt.parseMesaDiretora(html, prov);

        assertEquals(3, mesa.size());

        List<OrgaoOuFrente> doBotelho = mesa.get(Almt.normalizarChaveNome("Eduardo Botelho"));
        assertNotNull(doBotelho);
        assertEquals(1, doBotelho.size());
        assertEquals("orgao", doBotelho.get(0).tipo());
        assertEquals("Mesa Diretora", doBotelho.get(0).nome());
        assertEquals("Presidente", doBotelho.get(0).papel());
        assertEquals(LocalDate.of(2023, 2, 1), doBotelho.get(0).inicio());
        assertEquals(LocalDate.of(2024, 1, 12), doBotelho.get(0).fim());

        List<OrgaoOuFrente> daJanaina = mesa.get(Almt.normalizarChaveNome("Janaina Riva"));
        assertNotNull(daJanaina);
        assertEquals("1º Vice Presidente", daJanaina.get(0).papel());

        List<OrgaoOuFrente> doMax = mesa.get(Almt.normalizarChaveNome("Max Russi"));
        assertNotNull(doMax);
        assertEquals("Presidente", doMax.get(0).papel());
        assertEquals(LocalDate.of(2025, 2, 1), doMax.get(0).inicio());
        assertEquals(LocalDate.of(2026, 1, 30), doMax.get(0).fim());
    }

    @Test
    void normalizacaoCompletaEmContextoGeraArquivosEsperados(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlmt = ctx.raw().resolve("almt");
        Files.createDirectories(rawAlmt.resolve("perfis"));

        String deputadosHtml = """
                <div class="card rounded-0 my-3">
                    <a href="/parlamento/deputados/401/perfil">
                        <img class="card-img-top rounded-0" src="https://storage.al.mt.gov.br/api/v1/download/image/570629?width=420px&amp;quality=80" alt="Beto Dois a Um"/>
                    </a>
                    <div class="card-body">
                        PODE
                        </span>
                        <h6 class="card-title fw-semibold fs-14">BETO DOIS A UM</h6>
                    </div>
                </div>
                """;
        Files.writeString(rawAlmt.resolve("deputados.html"), deputadosHtml);
        gravarMetadado(rawAlmt, "deputados.html", Almt.URL_DEPUTADOS);

        String perfilHtml = """
                <span class="fw-bold">Nome civil:</span> Alberto Machado
                <span class="fw-bold">Nome parlamentar:</span> Beto Dois a Um
                <span class="fw-bold">Filiação partidária:</span> PODE
                <div data-parlamento--biografia-target="perfilBiografia"><p>Biografia do Beto.</p></div>
                """;
        Files.writeString(rawAlmt.resolve("perfis/401.html"), perfilHtml);
        gravarMetadado(rawAlmt, "perfis/401.html", Almt.SITE + "/parlamento/deputados/401/perfil");

        String mesaHtml = """
                <h6>1ª Sessão Legislativa ordinaria da 20ª Legislatura<br>01/02/2023 - 12/01/2024</h6>
                <h6><b>Presidente</b><br>Beto Dois a Um</h6>
                """;
        Files.writeString(rawAlmt.resolve("mesa-diretora.html"), mesaHtml);
        gravarMetadado(rawAlmt, "mesa-diretora.html", Almt.URL_MESA_DIRETORA);

        Almt almt = new Almt();
        almt.normalize(ctx);

        Path pessoasPath = ctx.normalizado().resolve("almt/pessoas.json");
        assertTrue(Files.exists(pessoasPath));
        JsonNode pessoas = Json.ler(pessoasPath);
        assertEquals(1, pessoas.size());
        assertEquals("almt", pessoas.get(0).get("fonte").stringValue());
        assertEquals("401", pessoas.get(0).get("id_fonte").stringValue());
        assertEquals("Beto Dois a Um", pessoas.get(0).get("nome").stringValue());
        assertEquals("deputado_estadual", pessoas.get(0).get("cargo").stringValue());
        assertEquals("MT", pessoas.get(0).get("uf").stringValue());

        Path nomesCivisPath = ctx.normalizado().resolve("almt/nomes-civis.json");
        assertTrue(Files.exists(nomesCivisPath));
        JsonNode nomesCivis = Json.ler(nomesCivisPath);
        assertEquals("Alberto Machado", nomesCivis.get("401").stringValue());

        Path blocoPath = ctx.normalizado().resolve("almt/blocos/401.json");
        assertTrue(Files.exists(blocoPath));
        JsonNode bloco = Json.ler(blocoPath);
        assertTrue(bloco.has("mandatos"));
        assertTrue(bloco.has("orgaos_e_frentes"));
        assertEquals("Biografia do Beto.", bloco.get("biografia").stringValue());
        assertEquals(1, bloco.get("orgaos_e_frentes").size());
        assertEquals("Presidente", bloco.get("orgaos_e_frentes").get(0).get("papel").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/almt/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
