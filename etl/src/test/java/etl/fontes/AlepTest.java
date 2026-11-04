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

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;

class AlepTest {

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Renato Freitas", Alep.formatarNome("RENATO FREITAS"));
        assertEquals("Alexandre Curi", Alep.formatarNome("ALEXANDRE CURI"));
        assertEquals("Gilson de Souza", Alep.formatarNome("GILSON DE SOUZA"));
        assertEquals("Adão Litro", Alep.formatarNome("ADÃO LITRO"));
        assertEquals("Delegado Tito Barichello", Alep.formatarNome("DELEGADO TITO BARICHELLO"));
        assertEquals("Ana Júlia", Alep.formatarNome("ANA JÚLIA"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <div class="deputado-card">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas" class="relative">
                        <img src="https://storage2.assembleia.pr.leg.br/img/foto1.jpg" alt="Foto">
                        <h3>RENATO FREITAS</h3>
                        <p class="text-gray-500 text-sm italic">PT</p>
                    </a>
                </div>
                <div class="deputado-card">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/alexandre-curi" class="relative">
                        <img src="https://storage2.assembleia.pr.leg.br/img/foto2.jpg" alt="Foto">
                        <h3>ALEXANDRE CURI</h3>
                        <p class="text-gray-500 text-sm italic">REP</p>
                    </a>
                </div>
                """;

        List<Alep.DeputadoBruto> deputados = Alep.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        assertEquals("renato-freitas", deputados.get(0).slug());
        assertEquals("RENATO FREITAS", deputados.get(0).nome());
        assertEquals("PT", deputados.get(0).partido());
        assertEquals("https://storage2.assembleia.pr.leg.br/img/foto1.jpg", deputados.get(0).fotoUrl());

        assertEquals("alexandre-curi", deputados.get(1).slug());
        assertEquals("ALEXANDRE CURI", deputados.get(1).nome());
        assertEquals("REP", deputados.get(1).partido());
        assertEquals("https://storage2.assembleia.pr.leg.br/img/foto2.jpg", deputados.get(1).fotoUrl());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String html = """
                <button>
                    <span class="font-bold text-[#004080] text-sm uppercase text-left">
                        Comissão de Constituição e Justiça
                    </span>
                </button>
                <div x-show="openAccordion === 'perm-1'">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/ana-julia">
                        <h3>ANA JÚLIA</h3>
                        <p class="text-gray-500 dd-14 text-sm">Suplente: RENATO FREITAS</p>
                    </a>
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/alexandre-curi">
                        <h3>ALEXANDRE CURI</h3>
                    </a>
                </div>
                """;

        var arq = new Coleta.Arquivo("https://exemplo", "raw/alep/comissoes.html", "hash", 100, Instant.now());
        Map<String, List<OrgaoOuFrente>> comissoes = Alep.extrairComissoesDoHtml(html, arq);

        assertTrue(comissoes.containsKey("ana-julia"));
        assertEquals("Comissão de Constituição e Justiça", comissoes.get("ana-julia").get(0).nome());
        assertEquals("comissao", comissoes.get("ana-julia").get(0).tipo());
        assertEquals("Suplente: RENATO FREITAS", comissoes.get("ana-julia").get(0).papel());

        assertTrue(comissoes.containsKey("alexandre-curi"));
        assertEquals("Membro", comissoes.get("alexandre-curi").get(0).papel());
    }

    @Test
    void normalizaDeputadoEComissoes(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-03T12:00:00Z"), null);

        Path rawAlep = ctx.raw().resolve("alep");
        Files.createDirectories(rawAlep.resolve("deputados"));
        Files.createDirectories(rawAlep.resolve("perfil"));

        String conhecaHtml = """
                <div class="deputado-card">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas" class="relative">
                        <img src="https://storage2.assembleia.pr.leg.br/img/renato.jpg" alt="Foto">
                        <h3>RENATO FREITAS</h3>
                        <p class="text-gray-500 text-sm italic">PT</p>
                    </a>
                </div>
                """;
        Files.writeString(rawAlep.resolve("deputados/conheca.html"), conhecaHtml);
        gravarMetadado(rawAlep, "deputados/conheca.html", "https://www.assembleia.pr.leg.br/deputados/conheca");

        String perfilHtml = "<div class='biografia'><p>Renato Freitas nasceu em Sorocaba...</p></div>";
        Files.writeString(rawAlep.resolve("perfil/renato-freitas.html"), perfilHtml);
        gravarMetadado(rawAlep, "perfil/renato-freitas.html", "https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas");

        String comissoesHtml = """
                <button>
                    <span class="uppercase">Comissão de Educação</span>
                </button>
                <div x-show="openAccordion === 'perm-1'">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas">
                        <h3>RENATO FREITAS</h3>
                        <p class="dd-14">Presidente</p>
                    </a>
                </div>
                """;
        Files.writeString(rawAlep.resolve("comissoes.html"), comissoesHtml);
        gravarMetadado(rawAlep, "comissoes.html", "https://www.assembleia.pr.leg.br/atividade-parlamentar/comissoes-permanentes-e-temporarias");

        Alep alep = new Alep();
        alep.normalize(ctx);

        // Verifica pessoas.json
        Path pessoasPath = ctx.normalizado().resolve("alep/pessoas.json");
        assertTrue(Files.exists(pessoasPath));
        JsonNode pessoas = Json.ler(pessoasPath);
        assertEquals(1, pessoas.size());
        JsonNode p = pessoas.get(0);
        assertEquals("alep", p.path("fonte").asText());
        assertEquals("renato-freitas", p.path("id_fonte").asText());
        assertEquals("Renato Freitas", p.path("nome").asText());
        assertEquals("deputado_estadual", p.path("cargo").asText());
        assertEquals("PR", p.path("uf").asText());
        assertEquals("PT", p.path("partido").asText());
        assertTrue(p.path("em_exercicio").asBoolean());

        // Verifica blocos/renato-freitas.json
        Path blocosPath = ctx.normalizado().resolve("alep/blocos/renato-freitas.json");
        assertTrue(Files.exists(blocosPath));
        JsonNode blocos = Json.ler(blocosPath);

        JsonNode mandatos = blocos.path("mandatos");
        assertEquals(1, mandatos.size());
        assertEquals("deputado_estadual", mandatos.get(0).path("cargo").asText());
        assertEquals("PR", mandatos.get(0).path("uf").asText());
        assertEquals(20, mandatos.get(0).path("legislatura").asInt());

        JsonNode orgaos = blocos.path("orgaos_e_frentes");
        assertEquals(1, orgaos.size());
        assertEquals("comissao", orgaos.get(0).path("tipo").asText());
        assertEquals("Comissão de Educação", orgaos.get(0).path("nome").asText());
        assertEquals("Presidente", orgaos.get(0).path("papel").asText());
    }

    private void gravarMetadado(Path pastaRaw, String arquivo, String url) throws IOException {
        Path meta = pastaRaw.resolve(arquivo + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alep/" + arquivo, "hash", 100, Instant.now());
        Files.createDirectories(meta.getParent());
        Json.gravar(meta, arq);
    }
}
