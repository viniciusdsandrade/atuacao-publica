package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;

class AlemaTest {

    @Test
    void parseDeputadosExtraiCardsCorretamente() {
        String html = """
                <div class="news-card">
                    <div class="news-card-image">
                        <img src="https://www.al.ma.leg.br/sitealema/wp-content/uploads/2024/12/Abigail-Cunha.jpg" alt="Abigail Cunha">
                    </div>
                    <div class="news-card-content">
                        <h3 class="news-card-title"><a href="https://www.al.ma.leg.br/sitealema/deputado/abigail-cunha/">Abigail Cunha</a></h3>
                        <p class="news-card-chapeu">MDB</p>
                        <p class="fs-6 m-0" style="height: 50px;">Empresária e bacharel em Direito</p>
                        <span class="news-card-divider"></span>
                    </div>
                </div>
                <div class="news-card">
                    <div class="news-card-image">
                        <img src="https://www.al.ma.leg.br/sitealema/wp-content/uploads/2024/01/carlos-lula-1-350x233.jpg" alt="Carlos Lula">
                    </div>
                    <div class="news-card-content">
                        <h3 class="news-card-title"><a href="https://www.al.ma.leg.br/sitealema/deputado/carlos-lula/">Carlos Lula</a></h3>
                        <p class="news-card-chapeu">PSB</p>
                        <p class="fs-6 m-0" style="height: 50px;">Advogado e professor universitário</p>
                        <span class="news-card-divider"></span>
                    </div>
                </div>
                """;

        List<Alema.DeputadoCard> deputados = Alema.parseDeputados(html);
        assertEquals(2, deputados.size());

        Alema.DeputadoCard d1 = deputados.get(0);
        assertEquals("abigail-cunha", d1.slug());
        assertEquals("Abigail Cunha", d1.nome());
        assertEquals("MDB", d1.partido());
        assertEquals("https://www.al.ma.leg.br/sitealema/wp-content/uploads/2024/12/Abigail-Cunha.jpg", d1.fotoUrl());
        assertEquals("https://www.al.ma.leg.br/sitealema/deputado/abigail-cunha/", d1.link());

        Alema.DeputadoCard d2 = deputados.get(1);
        assertEquals("carlos-lula", d2.slug());
        assertEquals("Carlos Lula", d2.nome());
        assertEquals("PSB", d2.partido());
    }

    @Test
    void normalizacaoCompletaGeraArquivosValidos(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlema = ctx.raw().resolve("alema");
        Files.createDirectories(rawAlema.resolve("deputados"));

        String htmlDeputados = """
                <div class="news-card">
                    <div class="news-card-image">
                        <img src="https://www.al.ma.leg.br/sitealema/wp-content/uploads/2024/01/image-2-1-350x233.png" alt="Iracema Vale">
                    </div>
                    <div class="news-card-content">
                        <h3 class="news-card-title"><a href="https://www.al.ma.leg.br/sitealema/deputado/iracema-vale/">Iracema Vale</a></h3>
                        <p class="news-card-chapeu">MDB</p>
                        <p class="fs-6 m-0" style="height: 50px;">Enfermeira e servidora pública</p>
                        <span class="news-card-divider"></span>
                    </div>
                </div>
                """;
        gravarCompactado(rawAlema, "deputados.html", Alema.URL_DEPUTADOS, htmlDeputados);

        String htmlDetalhe = """
                <div class="entry-content">
                    <p>Iracema Cristina Vale Lima é presidente da ALEMA. CPF 123.456.789-00.</p>
                </div>
                """;
        gravarCompactado(rawAlema, "deputados/iracema-vale.html", Alema.URL_PARLAMENTAR + "iracema-vale/", htmlDetalhe);

        Alema alema = new Alema();
        alema.normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alema/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alema", p1.get("fonte").stringValue());
        assertEquals("iracema-vale", p1.get("id_fonte").stringValue());
        assertEquals("Iracema Vale", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("MA", p1.get("uf").stringValue());
        assertEquals("MDB", p1.get("partido").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alema/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Iracema Vale", nomesCivis.get("iracema-vale").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alema/blocos/iracema-vale.json");
        assertTrue(Files.exists(arqBloco), "bloco iracema-vale.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
    }

    private static void gravarCompactado(Path dir, String arquivo, String url, String conteudo) throws IOException {
        Path gz = dir.resolve(arquivo + ".gz");
        try (OutputStream out = Files.newOutputStream(gz); GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        }
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alema/" + arquivo + ".gz", "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
