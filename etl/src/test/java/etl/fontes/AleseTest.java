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
        gravarCompactado(rawAlese, "deputados.html", Alese.URL_DEPUTADOS, htmlDeputados);

        String htmlMesa = """
                <table class="tb-body">
                    <tr>
                        <td><div align="right"><div align="left">Presidente:</div></div></td>
                        <td><b>Adailton Martins</b></td>
                    </tr>
                </table>
                """;
        gravarCompactado(rawAlese, "mesa_diretora.html", Alese.URL_MESA, htmlMesa);

        String htmlDetalhe = """
                <div class='kt-widget__info'>
                    <span class='kt-widget__label'>Nome civil:</span><span>Adailton Martins de Oliveira Filho</span>
                </div>
                <div id="texto_biografia" class="kt-section texto-biografia">
                    <p>Natural de Aracaju, engenheiro civil e deputado estadual. CPF 123.456.789-00.</p>
                </div>
                <div class='kt-widget1__item'>
                    <h3 class='kt-widget1__title'>Cargo: Membro (22/04/2026 a 31/01/2027)</h3>
                    <span class='kt-widget1__desc'>Permanente: <a href='comissoes.aspx?com=261#261'>COMISSÃO DE ECONOMIA - 2ª Formação</a></span>
                </div>
                <a href='https://aleselegis.al.se.leg.br/spl/consulta-producao.aspx?tipo=5&amp;autor=545' class='kt-widget31__username'>
                    Projeto de Lei <span class='kt-badge'>1</span>
                </a>
                """;
        gravarCompactado(rawAlese, "parlamentares/545.html", Alese.URL_PARLAMENTAR + "545", htmlDetalhe);

        String htmlProp = """
                <div class="kt-widget5__item d-block d-md-flex">
                    <a href="processo.aspx?id=118954&tipo=5&autor=545" class="kt-widget5__title">
                        Projeto de Lei n° 190/2026
                    </a>
                    <a href="processo.aspx?id=118954&tipo=5&autor=545" class="kt-widget5__desc">Ementa de teste.</a>
                    <div class="kt-widget5__info">
                        <span>Data:</span><span class="kt-font-info">09/09/2026 16:36:56</span>
                        <span>Situação:</span><span class="kt-font-info">Aprovado</span>
                    </div>
                </div>
                """;
        Files.createDirectories(rawAlese.resolve("producao/545_t5"));
        gravarCompactado(rawAlese, "producao/545_t5/p001.html",
                Alese.URL_PRODUCAO + "?tipo=5&autor=545", htmlProp);

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

        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(2, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());
        assertEquals("comissao", orgaos.get(1).get("tipo").stringValue());
        assertEquals("COMISSÃO DE ECONOMIA - 2ª Formação", orgaos.get(1).get("nome").stringValue());

        JsonNode proposicoes = bloco.get("proposicoes");
        assertNotNull(proposicoes);
        assertEquals(1, proposicoes.size());
        assertEquals("118954", proposicoes.get(0).get("id_fonte").stringValue());
        assertEquals("Projeto de Lei 190/2026", proposicoes.get(0).get("identificacao").stringValue());
        assertEquals("2026-09-09", proposicoes.get(0).get("data_apresentacao").stringValue());
        assertFalse(bloco.get("fontes").get("proposicoes").isEmpty());
    }

    @Test
    void normalizacaoSemFichaNoCacheMantemODeputadoDaLista(@TempDir Path tempDir) throws Exception {
        Contexto ctx = new Contexto(tempDir, tempDir.resolve("data"), null, null, Instant.parse("2026-11-11T12:00:00Z"), null);
        Path rawAlese = ctx.raw().resolve("alese");
        Files.createDirectories(rawAlese);
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
        gravarCompactado(rawAlese, "deputados.html", Alese.URL_DEPUTADOS, htmlDeputados);

        new Alese().normalize(ctx);

        JsonNode pessoas = Json.ler(ctx.normalizado().resolve("alese/pessoas.json"));
        assertEquals(1, pessoas.size());
        assertEquals(Alese.FONTE_DEPUTADOS, pessoas.get(0).get("links").get(0).get("fonte_id").stringValue());
        JsonNode bloco = Json.ler(ctx.normalizado().resolve("alese/blocos/545.json"));
        assertEquals(1, bloco.get("mandatos").size());
        assertTrue(bloco.get("proposicoes").isEmpty());
    }

    @Test
    void parseComissoesFiltraPorLegislaturaEClassificaConselhoComoOrgao() {
        var prov = new etl.Registros.Prov("alese-parlamentares", Alese.URL_PARLAMENTAR + "545",
                Instant.parse("2026-11-12T00:00:00Z"));
        String html = """
                <div class='kt-widget1__item'>
                    <h3 class='kt-widget1__title'>Cargo: Vice-Presidente (22/04/2026 a 31/01/2027)</h3>
                    <span class='kt-widget1__desc'>Permanente: <a href='comissoes.aspx?com=269#269'>CONSELHO DE &Eacute;TICA E DECORO - 2ª Formação</a></span>
                </div>
                <div class='kt-widget1__item'>
                    <h3 class='kt-widget1__title'>Cargo: Membro (01/02/2023 a 31/01/2025)</h3>
                    <span class='kt-widget1__desc'>Permanente-Finalizada: <a href='comissoes.aspx?com=225#225'>COMISSÃO DE SAÚDE - 1ª Formação</a></span>
                </div>
                <div class='kt-widget1__item'>
                    <h3 class='kt-widget1__title'>Cargo: Membro (01/02/2019 a 31/01/2023)</h3>
                    <span class='kt-widget1__desc'>Permanente-Finalizada: <a href='comissoes.aspx?com=100#100'>COMISSÃO ANTIGA</a></span>
                </div>
                """;
        var lista = Alese.parseComissoes(html, prov);
        assertEquals(2, lista.size());
        assertEquals("orgao", lista.get(0).tipo());
        assertEquals("CONSELHO DE ÉTICA E DECORO - 2ª Formação", lista.get(0).nome());
        assertEquals("Vice-Presidente", lista.get(0).papel());
        assertEquals("comissao", lista.get(1).tipo());
        assertEquals("Membro", lista.get(1).papel());
    }

    @Test
    void parseLinksEProposicoesHtmlExtraiESanitizaDados() {
        String htmlFicha = """
                <a href='https://aleselegis.al.se.leg.br/spl/consulta-producao.aspx?tipo=5&amp;autor=386' class='kt-widget31__username'>
                    Projeto de Lei <span class='kt-badge'>230</span>
                </a>
                <a href='consulta-producao.aspx?tipo=7&autor=386' class='kt-widget31__username'>
                    PEC <span class='kt-badge'>5</span>
                </a>
                """;
        var links = Alese.parseLinksProducao(htmlFicha);
        assertEquals(2, links.size());
        assertEquals(5, links.get(0).tipoId());
        assertEquals(386, links.get(0).autorId());
        assertEquals(230, links.get(0).contagem());
        assertEquals(7, links.get(1).tipoId());
        assertEquals(5, links.get(1).contagem());

        var prov = new etl.Registros.Prov("alese-proposicoes", links.get(0).url(), Instant.parse("2026-11-12T00:00:00Z"));
        String htmlLista = """
                <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="VS123" />
                <input type="hidden" name="__VIEWSTATEGENERATOR" id="__VIEWSTATEGENERATOR" value="GEN1" />
                <input type="hidden" name="__EVENTVALIDATION" id="__EVENTVALIDATION" value="EV9" />
                <div class="kt-widget5__item d-block d-md-flex">
                    <a href="processo.aspx?id=118954&tipo=5&autor=386" class="kt-widget5__title">
                        Projeto de Lei n° 190/2026
                    </a>
                    <a href="processo.aspx?id=118954&tipo=5&autor=386" class="kt-widget5__desc">
                        DISPÕE SOBRE TESTE. Contato (79) 99999-8888 e fulano@exemplo.invalid.
                    </a>
                    <div class="kt-widget5__info">
                        <span>Data:</span><span class="kt-font-info">09/09/2026 16:36:56</span>
                        <span>Situação:</span><span class="kt-font-info">Aprovado</span>
                    </div>
                </div>
                <div class="kt-widget5__item d-block d-md-flex">
                    <a href="processo.aspx?id=70001&tipo=5&autor=386" class="kt-widget5__title">
                        Projeto de Lei n° 10/2020
                    </a>
                    <a href="processo.aspx?id=70001&tipo=5&autor=386" class="kt-widget5__desc">Anterior</a>
                    <div class="kt-widget5__info">
                        <span>Data:</span><span class="kt-font-info">05/03/2020 10:00:00</span>
                    </div>
                </div>
                <a id="ContentPlaceHolder1_lbNext" class="btn" href="javascript:__doPostBack(&#39;ctl00$ContentPlaceHolder1$lbNext&#39;,&#39;&#39;)">Próxima</a>
                """;
        var props = Alese.parseProposicoesHtml(htmlLista, prov);
        assertEquals(2, props.size());
        assertEquals("Projeto de Lei 190/2026", props.get(0).identificacao());
        assertFalse(props.get(0).ementa().contains("99999-8888"));
        assertFalse(props.get(0).ementa().contains("@"));
        assertEquals("Aprovado", props.get(0).situacao());
        assertEquals("https://aleselegis.al.se.leg.br/spl/processo.aspx?id=118954", props.get(0).urlTexto());
        assertTrue(Alese.temProximaPagina(htmlLista));
        assertTrue(Alese.atingiuAnoAnterior(htmlLista, 2022));
        assertEquals("VS123", Alese.camposFormulario(htmlLista, "ctl00$ContentPlaceHolder1$lbNext").get("__VIEWSTATE"));
    }

    private static void gravarCompactado(Path dir, String arquivo, String url, String conteudo) throws IOException {
        Path gz = dir.resolve(arquivo + ".gz");
        try (OutputStream out = Files.newOutputStream(gz); GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        }
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alese/" + arquivo + ".gz", "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
