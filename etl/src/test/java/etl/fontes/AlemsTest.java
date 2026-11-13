package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class AlemsTest {

    @Test
    void parseDeputadosExtraiBlocosPartidariosComIdNomePartidoEFoto() {
        String html = """
                <div class="partys">
                    <div class="party" >
                        <div class="party-name" title="Partido Liberal">PL</div>
                        <div class="party-deputies">
                            <a class="party-deputy" href="/Deputados/Visualizar/6">
                                <img src="/upload/Deputy/2023_02_10_11_02_23_4bbaa544-b402-47d5-9138-85901dd1cbe2.jpg" alt="" />
                                <div class="">Coronel David </div>
                            </a>
                            <a class="party-deputy" href="/Deputados/Visualizar/23">
                                <img src="/upload/Deputy/2023_02_10_09_42_52_deda7c9f-9caf-4a36-81df-a0b53b4c4e2a.jpg" alt="" />
                                <div class="">Paulo Corr&#xEA;a</div>
                            </a>
                        </div>
                    </div>
                    <div class="party" >
                        <div class="party-name" title="Partido Progressista">PP</div>
                        <div class="party-deputies">
                            <a class="party-deputy" href="/Deputados/Visualizar/35">
                                <img src="/upload/Deputy/2025_02_13_08_42_23_41382d6a-cf14-40b5-9cf6-036eb701f302.jpg" alt="" />
                                <div class="">Gerson Claro</div>
                            </a>
                        </div>
                    </div>
                </div>
                """;

        List<Alems.DeputadoBruto> deps = Alems.parseDeputados(html);
        assertEquals(3, deps.size());

        Alems.DeputadoBruto d1 = deps.get(0);
        assertEquals("6", d1.id());
        assertEquals("Coronel David", d1.nome());
        assertEquals("PL", d1.partido());
        assertEquals("https://al.ms.gov.br/upload/Deputy/2023_02_10_11_02_23_4bbaa544-b402-47d5-9138-85901dd1cbe2.jpg", d1.fotoUrl());
        assertEquals("/Deputados/Visualizar/6", d1.href());

        Alems.DeputadoBruto d2 = deps.get(1);
        assertEquals("23", d2.id());
        assertEquals("Paulo Corrêa", d2.nome());
        assertEquals("PL", d2.partido());

        Alems.DeputadoBruto d3 = deps.get(2);
        assertEquals("35", d3.id());
        assertEquals("Gerson Claro", d3.nome());
        assertEquals("PP", d3.partido());
    }

    @Test
    void parsePerfilExtraiNomePartidoEFoto() {
        String html = """
                <section id="content">
                    <div class="container">
                        <div class="col-lg-12">
                            <h5 class="widgetheading">Gerson Claro - PP</h5>
                            <div class="col-lg-5">
                                <div class="bt-radio">
                                    <a href="mailto:depgersonclaro@al.ms.gov.br"><i class="fa fa-envelope-o"></i> depgersonclaro@al.ms.gov.br</a>
                                </div>
                                <div class="bt-radio"><a href="tel:3389-6418"><i class="fa fa-phone"></i> 3389-6418</a></div>
                                <img src="/upload/Deputy/2025_02_13_08_42_23_41382d6a-cf14-40b5-9cf6-036eb701f302.jpg" class="img-responsive" />
                            </div>
                            <div class="col-lg-7 text-justify">
                                <p>Gerson Claro nasceu em Itapor&atilde;, interior de Mato Grosso do Sul.</p>
                                <p>Come&ccedil;ou sua carreira como educador e advogado com CPF 123.456.789-00.</p>
                            </div>
                        </div>
                    </div>
                </section>
                <footer id="footer_content">
                    <a href="https://www.facebook.com/assembleiams">ALEMS Facebook</a>
                </footer>
                """;

        Alems.PerfilDeputado perfil = Alems.parsePerfil(html);
        assertEquals("Gerson Claro", perfil.nomeParlamentar());
        assertEquals("PP", perfil.partido());
        assertEquals("https://al.ms.gov.br/upload/Deputy/2025_02_13_08_42_23_41382d6a-cf14-40b5-9cf6-036eb701f302.jpg", perfil.fotoUrl());
        assertTrue(perfil.redes().isEmpty()); // redes institucionais da ALEMS no rodapé ignoradas
    }

    @Test
    void parseMesaDiretoraExtraiCargosComDatas() {
        String html = """
                <strong>Mesa Diretora</strong><br />
                <br />
                &nbsp;Fevereiro de 2023 a Janeiro de 2025<br />
                &nbsp;
                <table align="center" border="1" cellpadding="0" cellspacing="1">
                    <tbody>
                        <tr>
                            <td><strong>Nome</strong></td>
                            <td><strong>Cargo</strong></td>
                        </tr>
                        <tr>
                            <td>Gerson Claro (PP)</td>
                            <td>presidente</td>
                        </tr>
                        <tr>
                            <td>Paulo Corr&ecirc;a (PSDB)</td>
                            <td>1&ordm; secret&aacute;rio</td>
                        </tr>
                        <tr>
                            <td>Renato C&acirc;mara (MDB)</td>
                            <td>1&ordm; vice-presidente</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Prov prov = new Prov("alems-mesa-diretora", "https://al.ms.gov.br/Paginas/783/12-legislatura", Instant.now());
        Map<String, List<OrgaoOuFrente>> mesa = Alems.parseMesaDiretora(html, prov);

        assertEquals(3, mesa.size());

        List<OrgaoOuFrente> orgaosGerson = mesa.get("GERSON CLARO");
        assertNotNull(orgaosGerson);
        assertEquals(1, orgaosGerson.size());
        assertEquals("Presidente", orgaosGerson.get(0).papel());
        assertEquals("Mesa Diretora", orgaosGerson.get(0).nome());
        assertEquals(LocalDate.of(2023, 2, 1), orgaosGerson.get(0).inicio());
        assertEquals(LocalDate.of(2025, 1, 31), orgaosGerson.get(0).fim());

        List<OrgaoOuFrente> orgaosPaulo = mesa.get("PAULO CORREA");
        assertNotNull(orgaosPaulo);
        assertEquals("1º Secretário", orgaosPaulo.get(0).papel());

        List<OrgaoOuFrente> orgaosRenato = mesa.get("RENATO CAMARA");
        assertNotNull(orgaosRenato);
        assertEquals("1º Vice-Presidente", orgaosRenato.get(0).papel());
    }

    @Test
    void normalizacaoCompletaEmContextoGeraArquivosEsperados(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlems = ctx.raw().resolve("alems");
        Files.createDirectories(rawAlems.resolve("perfis"));

        String deputadosHtml = """
                <div class="partys">
                    <div class="party" >
                        <div class="party-name">PP</div>
                        <div class="party-deputies">
                            <a class="party-deputy" href="/Deputados/Visualizar/35">
                                <img src="/upload/Deputy/35.jpg" alt="" />
                                <div class="">Gerson Claro</div>
                            </a>
                        </div>
                    </div>
                </div>
                """;
        Files.writeString(rawAlems.resolve("deputados.html"), deputadosHtml);
        gravarMetadado(rawAlems, "deputados.html", Alems.URL_DEPUTADOS);

        String perfilHtml = """
                <h5 class="widgetheading">Gerson Claro - PP</h5>
                <img src="/upload/Deputy/35.jpg" class="img-responsive" />
                <div class="col-lg-7 text-justify"><p>Historiador e advogado.</p></div>
                """;
        Files.writeString(rawAlems.resolve("perfis/35.html"), perfilHtml);
        gravarMetadado(rawAlems, "perfis/35.html", Alems.SITE + "/Deputados/Visualizar/35");

        String mesaHtml = """
                <strong>Mesa Diretora</strong>
                <table>
                    <tr><td>Gerson Claro (PP)</td><td>presidente</td></tr>
                </table>
                """;
        Files.writeString(rawAlems.resolve("mesa-diretora.html"), mesaHtml);
        gravarMetadado(rawAlems, "mesa-diretora.html", Alems.URL_MESA_DIRETORA);

        Alems alems = new Alems();
        alems.normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alems/pessoas.json");
        assertTrue(Files.exists(arqPessoas));
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());
        assertEquals("Gerson Claro", pessoas.get(0).get("nome").stringValue());
        assertEquals("35", pessoas.get(0).get("id_fonte").stringValue());
        assertEquals("PP", pessoas.get(0).get("partido").stringValue());
        assertEquals("deputado_estadual", pessoas.get(0).get("cargo").stringValue());
        assertEquals("MS", pessoas.get(0).get("uf").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alems/blocos/35.json");
        assertTrue(Files.exists(arqBloco));
        JsonNode bloco = Json.ler(arqBloco);
        assertNull(bloco.get("biografia"), "A biografia não é guardada no bloco normalizado");
        assertEquals(1, bloco.get("orgaos_e_frentes").size());
        assertEquals("Presidente", bloco.get("orgaos_e_frentes").get(0).get("papel").stringValue());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alems/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
