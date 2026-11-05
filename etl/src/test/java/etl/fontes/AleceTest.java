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

class AleceTest {

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Agenor Neto", Alece.formatarNome("AGENOR NETO"));
        assertEquals("Alcides Fernandes", Alece.formatarNome("ALCIDES FERNANDES"));
        assertEquals("Davi de Raimundão", Alece.formatarNome("DAVI DE RAIMUNDÃO"));
        assertEquals("De Assis Diniz", Alece.formatarNome("DE ASSIS DINIZ"));
        assertEquals("Guilherme Landim", Alece.formatarNome("GUILHERME LANDIM"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <div class="main_page deputado_page container">
                    <div
                        class="deputado_card "
                        style="max-width: 300px"
                    >
                        <img
                            width="100%"
                            height="230"
                            style="object-fit: cover"
                            src="https://www.al.ce.gov.br/image/389109"
                            alt="Foto de Agenor Neto"
                        >

                        <p class="deputado_card--nome s-font-1"><a href="https://www.al.ce.gov.br/deputados/agenor-neto">                            Agenor Neto </a></p>
                        <p class="deputado_card--partido s-font-1"> MDB </p>
                    </div>
                    <div
                        class="deputado_card licenciado"
                        style="max-width: 300px"
                    >
                        <img
                            width="100%"
                            height="230"
                            style="object-fit: cover"
                            src="https://www.al.ce.gov.br/image/392721"
                            alt="Foto de Alcides Fernandes"
                        >

                        <p class="deputado_card--nome s-font-1"><a href="https://www.al.ce.gov.br/deputados/alcides-fernandes">                            Alcides Fernandes </a></p>
                        <p class="deputado_card--partido s-font-1"> PL </p>
                    </div>
                </div>
                """;

        List<Alece.DeputadoBruto> deputados = Alece.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        Alece.DeputadoBruto d1 = deputados.get(0);
        assertEquals("agenor-neto", d1.slug());
        assertEquals("Agenor Neto", d1.nome());
        assertEquals("MDB", d1.partido());
        assertEquals("https://www.al.ce.gov.br/image/389109", d1.fotoUrl());

        Alece.DeputadoBruto d2 = deputados.get(1);
        assertEquals("alcides-fernandes", d2.slug());
        assertEquals("Alcides Fernandes", d2.nome());
        assertEquals("PL", d2.partido());
        assertEquals("https://www.al.ce.gov.br/image/392721", d2.fotoUrl());
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-05T12:00:00Z"), null);

        Path rawAlece = ctx.raw().resolve("alece");
        Files.createDirectories(rawAlece.resolve("perfis"));

        String html = """
                <div class="main_page deputado_page container">
                    <div
                        class="deputado_card "
                        style="max-width: 300px"
                    >
                        <img
                            width="100%"
                            height="230"
                            style="object-fit: cover"
                            src="https://www.al.ce.gov.br/image/389109"
                            alt="Foto de Agenor Neto"
                        >

                        <p class="deputado_card--nome s-font-1"><a href="https://www.al.ce.gov.br/deputados/agenor-neto">Agenor Neto</a></p>
                        <p class="deputado_card--partido s-font-1"> MDB </p>
                    </div>
                </div>
                """;

        Files.writeString(rawAlece.resolve("deputados.html"), html);
        gravarMetadado(rawAlece, "deputados.html", "https://www.al.ce.gov.br/deputados");

        Alece alece = new Alece();
        alece.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alece/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertEquals(1, pessoasNode.size());

        JsonNode p1 = pessoasNode.get(0);
        assertEquals("alece", p1.get("fonte").stringValue());
        assertEquals("agenor-neto", p1.get("id_fonte").stringValue());
        assertEquals("Agenor Neto", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("CE", p1.get("uf").stringValue());
        assertEquals("MDB", p1.get("partido").stringValue());
        assertEquals("https://www.al.ce.gov.br/image/389109", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").booleanValue());

        Path blocoAgenor = ctx.normalizado().resolve("alece/blocos/agenor-neto.json");
        assertTrue(Files.exists(blocoAgenor));
        JsonNode bAgenor = Json.ler(blocoAgenor);
        assertNotNull(bAgenor.get("mandatos"));
        assertEquals(1, bAgenor.get("mandatos").size());

        JsonNode m1 = bAgenor.get("mandatos").get(0);
        assertEquals("2023-02-01", m1.get("inicio").stringValue());
        assertEquals("2027-01-31", m1.get("fim").stringValue());
        assertEquals(31, m1.get("legislatura").asInt());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String html = """
                <h1>Composição - Agropecuária</h1>
                <table>
                    <tbody>
                        <tr>
                            <td>&nbsp;</td>
                            <td colspan="2"><p><span><strong>PRESIDENTE</strong></span></p></td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <p><span><strong>Titular:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/missias-dias">Missias Dias, PT</a>
                            </td>
                            <td>
                                <p><span><strong>Suplente:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/bruno-pedrosa">Bruno Pedrosa, PT</a>
                            </td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td colspan="2"><p><span><strong>VICE-PRESIDENTE</strong></span></p></td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <p><span><strong>Titular:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/agenor-neto">Agenor Neto, MDB</a>
                            </td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td colspan="2"><p><span><strong>MEMBROS</strong></span></p></td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <p><span><strong>Titular:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/salmito">Salmito, PSB</a>
                            </td>
                            <td>
                                <p><span><strong>Suplente:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/simao-pedro">Simão Pedro, PSD</a>
                            </td>
                            <td>&nbsp;</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Prov prov = new Prov("alece-deputados-detalhes", "https://www.al.ce.gov.br/comissoes-permanentes/agropecuaria/composicao", Instant.parse("2026-11-05T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> resultado = Alece.extrairComissoesDoHtml(html, "agropecuaria", prov);

        assertEquals(5, resultado.size());
        assertEquals("Presidente (Titular)", resultado.get("missias-dias").get(0).papel());
        assertEquals("Presidente (Suplente)", resultado.get("bruno-pedrosa").get(0).papel());
        assertEquals("Vice-Presidente (Titular)", resultado.get("agenor-neto").get(0).papel());
        assertEquals("Membro", resultado.get("salmito").get(0).papel());
        assertEquals("Suplente", resultado.get("simao-pedro").get(0).papel());
        assertEquals("Comissão de Agropecuária", resultado.get("missias-dias").get(0).nome());
        assertEquals("comissao", resultado.get("missias-dias").get(0).tipo());
    }

    private static void gravarMetadado(Path pastaFonte, String relativo, String url) throws IOException {
        Path arquivo = pastaFonte.resolve(relativo);
        Path meta = arquivo.resolveSibling(arquivo.getFileName() + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alece/" + relativo, "hash-teste", 100, Instant.now());
        Json.gravar(meta, arq, true);
    }
}
