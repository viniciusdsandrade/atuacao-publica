package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class AlrnTest {

    @Test
    void parseDeputadosExtraiCardsCorretamente() {
        String html = """
                <div class="container">
                    <div class="row">
                        <div class="col-12 col-lg-4">
                            <a href="https://www.al.rn.leg.br/deputado/72/ezequiel-ferreira" class="card-deputies card-deputies-inner">
                                <div class="image-deputies" style="background-image: url('https://www.al.rn.leg.br/storage/deputados/2023/ezequiel.png')"></div>
                                <strong class="name-deputies">Ezequiel Ferreira</strong>
                                <p class="party-deputies">Partido da Social Democracia Brasileira(PSDB)</p>
                            </a>
                        </div>
                        <div class="col-12 col-lg-4">
                            <a href="https://www.al.rn.leg.br/deputado/137/adjuto-dias" class="card-deputies card-deputies-inner">
                                <div class="image-deputies" style="background-image: url('https://www.al.rn.leg.br/storage/deputados/2023/adjuto.png')"></div>
                                <strong class="name-deputies">Adjuto Dias</strong>
                                <p class="party-deputies">Partido Liberal(PL)</p>
                            </a>
                        </div>
                    </div>
                </div>
                """;

        List<Alrn.DeputadoCard> deputados = Alrn.parseDeputados(html);
        assertEquals(2, deputados.size());

        Alrn.DeputadoCard d1 = deputados.get(0);
        assertEquals(72, d1.id());
        assertEquals("ezequiel-ferreira", d1.slug());
        assertEquals("Ezequiel Ferreira", d1.nome());
        assertEquals("Partido da Social Democracia Brasileira(PSDB)", d1.partido());
        assertEquals("https://www.al.rn.leg.br/storage/deputados/2023/ezequiel.png", d1.fotoUrl());

        Alrn.DeputadoCard d2 = deputados.get(1);
        assertEquals(137, d2.id());
        assertEquals("adjuto-dias", d2.slug());
        assertEquals("Adjuto Dias", d2.nome());
        assertEquals("Partido Liberal(PL)", d2.partido());
    }

    @Test
    void parseMesaExtraiComposicaoCorretamente() {
        String html = """
                <div class="row">
                    <a href="https://www.al.rn.leg.br/deputado/72/ezequiel-ferreira" class="card-deputies ">
                        <strong class="name-deputies">Ezequiel Ferreira</strong>
                        <p class="party-deputies">PRESIDENTE</p>
                    </a>
                    <a href="https://www.al.rn.leg.br/deputado/123/kleber-rodrigues" class="card-deputies ">
                        <strong class="name-deputies">Kleber Rodrigues</strong>
                        <p class="party-deputies">1º VICE-PRESIDENTE</p>
                    </a>
                </div>
                """;

        Map<Integer, Alrn.MesaMembro> mesa = Alrn.parseMesa(html);
        assertEquals(2, mesa.size());

        Alrn.MesaMembro m1 = mesa.get(72);
        assertNotNull(m1);
        assertEquals("Ezequiel Ferreira", m1.nome());
        assertEquals("PRESIDENTE", m1.papel());

        Alrn.MesaMembro m2 = mesa.get(123);
        assertNotNull(m2);
        assertEquals("Kleber Rodrigues", m2.nome());
        assertEquals("1º VICE-PRESIDENTE", m2.papel());
    }

    @Test
    void extrairSiglaPartidoEFormatarPapel() {
        assertEquals("PSDB", Alrn.extrairSiglaPartido("Partido da Social Democracia Brasileira(PSDB)"));
        assertEquals("PL", Alrn.extrairSiglaPartido("Partido Liberal(PL)"));
        assertEquals("PT", Alrn.extrairSiglaPartido("PT"));

        assertEquals("Presidente", Alrn.formatarPapelMesa("PRESIDENTE"));
        assertEquals("1º Vice-Presidente", Alrn.formatarPapelMesa("1º VICE-PRESIDENTE"));
        assertEquals("1º Secretário", Alrn.formatarPapelMesa("1º SECRETÁRIO(A)"));
    }

    @Test
    void normalizacaoGeraPessoasNomesCivisEBlocos(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlrn = ctx.raw().resolve("alrn");
        Files.createDirectories(rawAlrn.resolve("deputados"));

        String htmlDeputados = """
                <div class="row">
                    <a href="https://www.al.rn.leg.br/deputado/72/ezequiel-ferreira" class="card-deputies card-deputies-inner">
                        <div class="image-deputies" style="background-image: url('https://www.al.rn.leg.br/storage/deputados/2023/ezequiel.png')"></div>
                        <strong class="name-deputies">Ezequiel Ferreira</strong>
                        <p class="party-deputies">Partido da Social Democracia Brasileira(PSDB)</p>
                    </a>
                </div>
                """;
        Files.writeString(rawAlrn.resolve("deputados.html"), htmlDeputados);
        gravarMetadado(rawAlrn, "deputados.html", Alrn.URL_DEPUTADOS);

        String htmlMesa = """
                <div class="row">
                    <a href="https://www.al.rn.leg.br/deputado/72/ezequiel-ferreira" class="card-deputies ">
                        <strong class="name-deputies">Ezequiel Ferreira</strong>
                        <p class="party-deputies">PRESIDENTE</p>
                    </a>
                </div>
                """;
        Files.writeString(rawAlrn.resolve("mesa_diretora.html"), htmlMesa);
        gravarMetadado(rawAlrn, "mesa_diretora.html", Alrn.URL_MESA);

        String htmlDetalhe = """
                <div class="container">
                    <strong class="title-new">Ezequiel Ferreira</strong>
                    <div class="p-3">
                        <p>Natural de Natal, Ezequiel Galvão Ferreira de Souza é advogado e presidente da Assembleia Legislativa. CPF 123.456.789-00.</p>
                    </div>
                    <div class="outra-coisa"></div>
                </div>
                """;
        Files.writeString(rawAlrn.resolve("deputados/72.html"), htmlDetalhe);
        gravarMetadado(rawAlrn, "deputados/72.html", Alrn.SITE + "/deputado/72/ezequiel-ferreira");

        // Normalização
        Alrn alrn = new Alrn();
        alrn.normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alrn/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alrn", p1.get("fonte").stringValue());
        assertEquals("72", p1.get("id_fonte").stringValue());
        assertEquals("Ezequiel Ferreira", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("RN", p1.get("uf").stringValue());
        assertEquals("PSDB", p1.get("partido").stringValue());
        assertEquals("https://www.al.rn.leg.br/storage/deputados/2023/ezequiel.png", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alrn/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Ezequiel Galvão Ferreira de Souza", nomesCivis.get("72").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alrn/blocos/72.json");
        assertTrue(Files.exists(arqBloco), "bloco 72.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        // Biografia não deve conter CPF
        String bio = bloco.get("biografia").stringValue();
        assertFalse(bio.contains("123.456.789-00"), "CPF deve ter sido removido pela Guarda");
        assertTrue(bio.contains("Ezequiel Galvão Ferreira de Souza"));

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alrn/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
