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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;

class AleseTest {

    @Test
    void parseDeputadosExtraiCardsCorretamente() {
        String html = """
                <article class="qodef-portfolio-item mix portfolio_category_74">
                    <div class="qodef-item-image-holder">
                        <img src="https://al.se.leg.br/wp-content/uploads/2023/06/Adailton-Martins-1.jpg" class="attachment-full wp-post-image" />
                    </div>
                    <div class="qodef-item-text-holder">
                        <h5 class="qodef-item-title">
                            <a href="https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id=545" target="_blank">
                                Adailton Martins
                            </a>
                        </h5>
                        <div class="qodef-ptf-category-holder"><span>PSD</span></div>
                    </div>
                </article>
                <article class="qodef-portfolio-item mix portfolio_category_74">
                    <div class="qodef-item-image-holder">
                        <img src="https://al.se.leg.br/wp-content/uploads/2023/06/Josefa-Aurea.jpg" class="attachment-full wp-post-image" />
                    </div>
                    <div class="qodef-item-text-holder">
                        <h5 class="qodef-item-title">
                            <a href="https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id=562" target="_blank">
                                Áurea Ribeiro
                            </a>
                        </h5>
                        <div class="qodef-ptf-category-holder"><span>PP</span></div>
                    </div>
                </article>
                """;

        List<Alese.DeputadoCard> deputados = Alese.parseDeputados(html);
        assertEquals(2, deputados.size());

        Alese.DeputadoCard d1 = deputados.get(0);
        assertEquals(545, d1.id());
        assertEquals("Adailton Martins", d1.nome());
        assertEquals("PSD", d1.partido());
        assertEquals("https://al.se.leg.br/wp-content/uploads/2023/06/Adailton-Martins-1.jpg", d1.fotoUrl());
        assertEquals("https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id=545", d1.link());

        Alese.DeputadoCard d2 = deputados.get(1);
        assertEquals(562, d2.id());
        assertEquals("Áurea Ribeiro", d2.nome());
        assertEquals("PP", d2.partido());
    }

    @Test
    void parseNomeCivilExtraiNomeComSpan() {
        String html = """
                <div class='kt-widget__info' style='display: flow;'>
                    <span class='kt-widget__label'>Nome civil:</span><span class='kt-widget__data ml-2' style='word-break: break-word;'>Adailton Martins de Oliveira Filho</span>
                </div>
                """;
        assertEquals("Adailton Martins de Oliveira Filho", Alese.parseNomeCivil(html));
    }

    @Test
    void parseBiografiaHigienizaHtmlESanitizaDadosSensíveis() {
        String html = """
                <div id="texto_biografia" class="kt-section texto-biografia">
                    <p>Natural de Aracaju, exerceu cargo público. CPF: 123.456.789-00 e telefone (79) 99999-9999.</p>
                </div>
                """;
        String bio = Alese.parseBiografia(html);
        assertNotNull(bio);
        assertTrue(bio.contains("Natural de Aracaju, exerceu cargo público."));
        assertFalse(bio.contains("123.456.789-00"));
        assertFalse(bio.contains("(79) 99999-9999"));
    }

    @Test
    void parseMesaExtraiCargosCorretamente() {
        String html = """
                <table class="tb-body">
                    <tr>
                        <td><div align="right"><div align="left">Presidente:</div></div></td>
                        <td><b>Jeferson Andrade</b></td>
                    </tr>
                    <tr>
                        <td><div align="right"><div align="left">Vice-Presidente:</div></div></td>
                        <td><b>Garibalde Mendonça</b></td>
                    </tr>
                    <tr>
                        <td><div align="right"><div align="left">1&ordm; Secret&aacute;rio:</div></div></td>
                        <td><b>Luciano Bispo</b></td>
                    </tr>
                </table>
                """;

        List<Alese.MesaMembro> membros = Alese.parseMesa(html);
        assertEquals(3, membros.size());

        assertEquals("Presidente", membros.get(0).cargo());
        assertEquals("Jeferson Andrade", membros.get(0).nome());

        assertEquals("Vice-Presidente", membros.get(1).cargo());
        assertEquals("Garibalde Mendonça", membros.get(1).nome());

        assertEquals("1º Secretário", membros.get(2).cargo());
        assertEquals("Luciano Bispo", membros.get(2).nome());
    }

    @Test
    void normalizacaoCompletaGeraArquivosValidos(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlese = ctx.raw().resolve("alese");
        Files.createDirectories(rawAlese.resolve("parlamentares"));

        String htmlDeputados = """
                <article class="qodef-portfolio-item mix portfolio_category_74">
                    <div class="qodef-item-image-holder">
                        <img src="https://al.se.leg.br/wp-content/uploads/2023/06/Adailton-Martins-1.jpg" class="attachment-full wp-post-image" />
                    </div>
                    <div class="qodef-item-text-holder">
                        <h5 class="qodef-item-title">
                            <a href="https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id=545" target="_blank">
                                Adailton Martins
                            </a>
                        </h5>
                        <div class="qodef-ptf-category-holder"><span>PSD</span></div>
                    </div>
                </article>
                """;
        Files.writeString(rawAlese.resolve("deputados.html"), htmlDeputados);
        gravarMetadado(rawAlese, "deputados.html", Alese.URL_DEPUTADOS);

        String htmlMesa = """
                <table class="tb-body">
                    <tr>
                        <td><div align="right"><div align="left">Presidente:</div></div></td>
                        <td><b>Adailton Martins</b></td>
                    </tr>
                </table>
                """;
        Files.writeString(rawAlese.resolve("mesa_diretora.html"), htmlMesa);
        gravarMetadado(rawAlese, "mesa_diretora.html", Alese.URL_MESA);

        String htmlDetalhe = """
                <div class='kt-widget__info'>
                    <span class='kt-widget__label'>Nome civil:</span><span>Adailton Martins de Oliveira Filho</span>
                </div>
                <div id="texto_biografia" class="kt-section texto-biografia">
                    <p>Natural de Aracaju, engenheiro civil e deputado estadual. CPF 123.456.789-00.</p>
                </div>
                """;
        Files.writeString(rawAlese.resolve("parlamentares/545.html"), htmlDetalhe);
        gravarMetadado(rawAlese, "parlamentares/545.html", Alese.URL_PARLAMENTAR + "545");

        Alese alese = new Alese();
        alese.normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alese/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alese", p1.get("fonte").stringValue());
        assertEquals("545", p1.get("id_fonte").stringValue());
        assertEquals("Adailton Martins", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("SE", p1.get("uf").stringValue());
        assertEquals("PSD", p1.get("partido").stringValue());
        assertEquals("https://al.se.leg.br/wp-content/uploads/2023/06/Adailton-Martins-1.jpg", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alese/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("Adailton Martins de Oliveira Filho", nomesCivis.get("545").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alese/blocos/545.json");
        assertTrue(Files.exists(arqBloco), "bloco 545.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        // Biografia não deve conter CPF
        String bio = bloco.get("biografia").stringValue();
        assertFalse(bio.contains("123.456.789-00"), "CPF deve ter sido removido pela Guarda");
        assertTrue(bio.contains("Natural de Aracaju"));

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alese/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
