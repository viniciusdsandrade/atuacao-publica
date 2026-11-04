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

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Prov;

class AlbaTest {

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Adolfo Menezes", Alba.formatarNome("ADOLFO MENEZES"));
        assertEquals("Alex da Piatã", Alba.formatarNome("ALEX DA PIATÃ"));
        assertEquals("Angelo Coronel Filho", Alba.formatarNome("ANGELO CORONEL FILHO"));
        assertEquals("Maria del Carmen", Alba.formatarNome("MARIA DEL CARMEN"));
        assertEquals("Zé Raimundo Fontes", Alba.formatarNome("ZÉ RAIMUNDO FONTES"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <div class="col-md-3">
                    <li class="list-item">
                        <a href="/deputados/deputado-estadual/910629">
                            <div class="cnt-img-deputado">
                                <img src="/fserver/:imagensAlbanet:fotos:AdolfoMenezes.jpg/static:images:shared:avatar.jpg" class="deputado-img"/>
                            </div>
                        </a>
                    </li>
                    <div class="campo-dados">
                        <div class="deputado-nome">
                            <a href="/deputados/deputado-estadual/910629">
                                <span>Adolfo Menezes</span>
                            </a>
                        </div>
                        <div class="partido-nome">PSD</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <li class="list-item">
                        <a href="/deputados/deputado-estadual/921283">
                            <div class="cnt-img-deputado">
                                <img src="/fserver/:imagensAlbanet:fotos:Bobo.jpg/static:images:shared:avatar.jpg" class="deputado-img"/>
                            </div>
                        </a>
                    </li>
                    <div class="campo-dados">
                        <div class="deputado-nome">
                            <a href="/deputados/deputado-estadual/921283">
                                <span>Bobô</span>
                            </a>
                        </div>
                        <div class="partido-nome">PCdoB</div>
                    </div>
                </div>
                """;

        List<Alba.DeputadoBruto> deputados = Alba.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        assertEquals("910629", deputados.get(0).id());
        assertEquals("Adolfo Menezes", deputados.get(0).nome());
        assertEquals("PSD", deputados.get(0).partido());
        assertEquals("https://www.al.ba.gov.br/fserver/:imagensAlbanet:fotos:AdolfoMenezes.jpg/static:images:shared:avatar.jpg", deputados.get(0).fotoUrl());

        assertEquals("921283", deputados.get(1).id());
        assertEquals("Bobô", deputados.get(1).nome());
        assertEquals("PCdoB", deputados.get(1).partido());
    }

    @Test
    void extraiDetalhePerfilBiografico() {
        String html = """
                <div class="dados-deputado">
                    <p>
                        <strong>Nome: </strong><span>ADOLFO EMANUEL MONTEIRO DE MENEZES</span>
                    </p>
                    <p>
                        <strong>Nascimento: </strong><span>04/11/1958, Campo Formoso-BA</span>
                    </p>
                </div>
                """;

        Alba.DetalhePerfil det = Alba.extrairDetalhePerfilDoHtml(html);
        assertNotNull(det);
        assertEquals("ADOLFO EMANUEL MONTEIRO DE MENEZES", det.nomeCivil());
        assertEquals("1958-11-04", det.nascimento());
    }

    @Test
    void extraiMembrosDeComissaoDoHtml() {
        String html = """
                <div class="titulo-presidente">
                    <span>COMISSÃO DE CONSTITUIÇÃO E JUSTIÇA</span>
                </div>
                <div class="inter-title">PRESIDENTE</div>
                <div>
                    <span>ROBINSON ALMEIDA (PT)</span>
                </div>
                <div class="inter-title">VICE-PRESIDENTE</div>
                <div>
                    <span>LUCIANO RIBEIRO (UNIÃO)</span>
                </div>
                <table class="tabela-cab">
                    <tbody>
                        <tr class="table-itens">
                            <td><span>Robinson Almeida (PT)</span></td>
                            <td><span>Jurailton Santos (REPUBLICANOS)</span></td>
                        </tr>
                        <tr class="table-itens">
                            <td><span>Luciano Ribeiro (UNIÃO)</span></td>
                            <td><span></span></td>
                        </tr>
                        <tr class="table-itens">
                            <td><span>Euclides Fernandes (PT)</span></td>
                            <td><span></span></td>
                        </tr>
                    </tbody>
                </table>
                """;

        Map<String, String> idPorNomeNorm = Map.of(
                "ROBINSON ALMEIDA", "926905",
                "LUCIANO RIBEIRO", "936939",
                "EUCLIDES FERNANDES", "910635",
                "JURAILTON SANTOS", "926897"
        );

        var arq = new Coleta.Arquivo("https://exemplo", "raw/alba/comissoes/10.html", "hash", 100, Instant.now());
        Prov prov = Prov.de(Alba.FONTE_COMISSOES, "https://exemplo", arq);
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        Alba.extrairMembrosDeComissaoDoHtml(html, idPorNomeNorm, prov, mapa);

        assertTrue(mapa.containsKey("926905"));
        assertEquals("Presidente", mapa.get("926905").get(0).papel());
        assertEquals("Comissão de Constituição e Justiça", mapa.get("926905").get(0).nome());

        assertTrue(mapa.containsKey("936939"));
        assertEquals("Vice-Presidente", mapa.get("936939").get(0).papel());

        assertTrue(mapa.containsKey("910635"));
        assertEquals("Titular", mapa.get("910635").get(0).papel());

        assertTrue(mapa.containsKey("926897"));
        assertEquals("Suplente", mapa.get("926897").get(0).papel());
    }

    @Test
    void normalizaDeputadoEComissoes(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-04T12:00:00Z"), null);

        Path rawAlba = ctx.raw().resolve("alba");
        Files.createDirectories(rawAlba.resolve("deputados"));
        Files.createDirectories(rawAlba.resolve("perfil"));
        Files.createDirectories(rawAlba.resolve("comissoes"));

        String deputadosHtml = """
                <div class="col-md-3">
                    <li class="list-item">
                        <a href="/deputados/deputado-estadual/910629">
                            <div class="cnt-img-deputado">
                                <img src="/fserver/:imagensAlbanet:fotos:AdolfoMenezes.jpg/static:images:shared:avatar.jpg" class="deputado-img"/>
                            </div>
                        </a>
                    </li>
                    <div class="campo-dados">
                        <div class="deputado-nome">
                            <a href="/deputados/deputado-estadual/910629">
                                <span>Adolfo Menezes</span>
                            </a>
                        </div>
                        <div class="partido-nome">PSD</div>
                    </div>
                </div>
                """;
        Files.writeString(rawAlba.resolve("deputados/deputados-estaduais.html"), deputadosHtml);
        gravarMetadado(rawAlba, "deputados/deputados-estaduais.html", "https://www.al.ba.gov.br/deputados/deputados-estaduais");

        String perfilHtml = """
                <div class="dados-deputado">
                    <p><strong>Nome: </strong><span>ADOLFO EMANUEL MONTEIRO DE MENEZES</span></p>
                    <p><strong>Nascimento: </strong><span>04/11/1958, Campo Formoso-BA</span></p>
                </div>
                """;
        Files.writeString(rawAlba.resolve("perfil/910629.html"), perfilHtml);
        gravarMetadado(rawAlba, "perfil/910629.html", "https://www.al.ba.gov.br/deputados/deputado-estadual/910629");

        String comissoesCompHtml = """
                <li><a href="/comissoes/composicao/10" class="sub-title-com"><span>CCJ</span></a></li>
                """;
        Files.writeString(rawAlba.resolve("comissoes/composicao.html"), comissoesCompHtml);
        gravarMetadado(rawAlba, "comissoes/composicao.html", "https://www.al.ba.gov.br/comissoes/composicao");

        String comissao10Html = """
                <div class="titulo-presidente"><span>COMISSÃO DE CONSTITUIÇÃO E JUSTIÇA</span></div>
                <div class="inter-title">PRESIDENTE</div>
                <div><span>ADOLFO MENEZES (PSD)</span></div>
                <table class="tabela-cab">
                    <tbody>
                        <tr class="table-itens">
                            <td><span>Adolfo Menezes (PSD)</span></td>
                            <td><span></span></td>
                        </tr>
                    </tbody>
                </table>
                """;
        Files.writeString(rawAlba.resolve("comissoes/10.html"), comissao10Html);
        gravarMetadado(rawAlba, "comissoes/10.html", "https://www.al.ba.gov.br/comissoes/composicao/10");

        Alba alba = new Alba();
        alba.normalize(ctx);

        // Verifica pessoas.json
        Path pessoasPath = ctx.normalizado().resolve("alba/pessoas.json");
        assertTrue(Files.exists(pessoasPath));
        JsonNode pessoas = Json.ler(pessoasPath);
        assertEquals(1, pessoas.size());
        JsonNode p = pessoas.get(0);
        assertEquals("alba", p.path("fonte").asText());
        assertEquals("910629", p.path("id_fonte").asText());
        assertEquals("Adolfo Menezes", p.path("nome").asText());
        assertEquals("deputado_estadual", p.path("cargo").asText());
        assertEquals("BA", p.path("uf").asText());
        assertEquals("PSD", p.path("partido").asText());
        assertTrue(p.path("em_exercicio").asBoolean());

        // Verifica blocos/910629.json
        Path blocosPath = ctx.normalizado().resolve("alba/blocos/910629.json");
        assertTrue(Files.exists(blocosPath));
        JsonNode blocos = Json.ler(blocosPath);

        JsonNode mandatos = blocos.path("mandatos");
        assertEquals(1, mandatos.size());
        assertEquals("deputado_estadual", mandatos.get(0).path("cargo").asText());
        assertEquals("BA", mandatos.get(0).path("uf").asText());

        JsonNode orgaos = blocos.path("orgaos_e_frentes");
        assertEquals(1, orgaos.size());
        assertEquals("Presidente", orgaos.get(0).path("papel").asText());
        assertEquals("Comissão de Constituição e Justiça", orgaos.get(0).path("nome").asText());
    }

    private static void gravarMetadado(Path pastaFonte, String relativo, String url) throws IOException {
        Path arquivo = pastaFonte.resolve(relativo);
        Path meta = arquivo.resolveSibling(arquivo.getFileName() + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alba/" + relativo, "hash-teste", 100, Instant.now());
        Json.gravar(meta, arq, true);
    }
}
