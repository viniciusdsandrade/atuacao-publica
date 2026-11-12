package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;
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
import etl.Registros.Despesa;
import etl.Registros.Prov;

class AlapTest {

    @Test
    void parseDeputadosExtraiCardsETooltipsCorretamente() {
        String html = """
                <div class="ls-box col-md-2 col-xs-5 ls-no-radius" style="margin: 10px; max-height: 310px">
                    <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=74" onmouseover="Tip('<b>Dep.</b> Aldilene Souza<br><b>Nome Completo:</b> ALDILENE MATOS DE SOUZA<br><b>Partido:</b> PDT<br><b>Profissão:</b> Administradora', WIDTH, 200, PADDING, 8, BGCOLOR, '#FFFFCC')"><img class="foto-deputado" src="fotodeputado/230828122115Aldilene Souza.jpg"></a></div>
                    <p class="ls-title-5">Deputada Aldilene Souza </p>
                </div>
                <div class="ls-box col-md-2 col-xs-5 ls-no-radius" style="margin: 10px; max-height: 310px">
                    <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=75" onmouseover="Tip('<b>Dep.</b> Alliny Serrão<br><b>Nome Completo:</b> ALLINY SOUSA DA ROCHA SERRÃO<br><b>Partido:</b> UNIÃO BRASIL (UNIÃO)<br><b>Profissão:</b> Empresária ', WIDTH, 200, PADDING, 8, BGCOLOR, '#FFFFCC')"><img class="foto-deputado" src="fotodeputado/foto_presidente_alliny.jpg"></a></div>
                    <p class="ls-title-5">Deputada Alliny Serrão </p>
                </div>
                """;

        List<Alap.DeputadoCard> deputados = Alap.parseDeputados(html);
        assertEquals(2, deputados.size());

        Alap.DeputadoCard d1 = deputados.get(0);
        assertEquals("74", d1.id());
        assertEquals("Aldilene Souza", d1.nomeParlamentar());
        assertEquals("ALDILENE MATOS DE SOUZA", d1.nomeCompleto());
        assertEquals("PDT", d1.partido());
        assertEquals("https://www.al.ap.leg.br/fotodeputado/230828122115Aldilene%20Souza.jpg", d1.fotoUrl());
        assertEquals("Administradora", d1.profissao());

        Alap.DeputadoCard d2 = deputados.get(1);
        assertEquals("75", d2.id());
        assertEquals("Alliny Serrão", d2.nomeParlamentar());
        assertEquals("ALLINY SOUSA DA ROCHA SERRÃO", d2.nomeCompleto());
        assertEquals("UNIÃO", d2.partido());
        assertEquals("Empresária", d2.profissao());
    }

    @Test
    void parseMesaExtraiCargosCorretamente() {
        String html = """
                <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=75"><img src="foto.jpg"></a></div>
                <p class="ls-title-5">Alliny Serrão <br> <strong>Presidente</strong> </p>
                <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=57"><img src="foto2.jpg"></a></div>
                <p class="ls-title-5">Jaime Perez <br> <strong>1º Vice-Presidente</strong> </p>
                """;

        List<Alap.MesaMembro> mesa = Alap.parseMesa(html);
        assertEquals(2, mesa.size());

        assertEquals("75", mesa.get(0).id());
        assertEquals("Presidente", mesa.get(0).cargo());
        assertEquals("Alliny Serrão", mesa.get(0).nome());

        assertEquals("57", mesa.get(1).id());
        assertEquals("1º Vice-Presidente", mesa.get(1).cargo());
    }

    @Test
    void normalizacaoCompletaGeraArquivosValidos(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-11T12:00:00Z"), null);

        Path rawAlap = ctx.raw().resolve("alap");
        Files.createDirectories(rawAlap.resolve("parlamentares"));

        String htmlLeg = """
                <div class="ls-box col-md-2 col-xs-5 ls-no-radius" style="margin: 10px; max-height: 310px">
                    <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=75" onmouseover="Tip('<b>Dep.</b> Alliny Serrão<br><b>Nome Completo:</b> ALLINY SOUSA DA ROCHA SERRÃO<br><b>Partido:</b> UNIÃO BRASIL (UNIÃO)<br><b>Profissão:</b> Empresária e líder comunitária. CPF 123.456.789-00.', WIDTH, 200, PADDING, 8, BGCOLOR, '#FFFFCC')"><img class="foto-deputado" src="fotodeputado/foto_presidente_alliny.jpg"></a></div>
                    <p class="ls-title-5">Deputada Alliny Serrão </p>
                </div>
                """;
        gravarCompactado(rawAlap, "legislatura.html", Alap.URL_LEGISLATURA, htmlLeg);

        String htmlMesa = """
                <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=75"><img src="foto.jpg"></a></div>
                <p class="ls-title-5">Alliny Serrão <br> <strong>Presidente</strong> </p>
                """;
        gravarCompactado(rawAlap, "mesa_diretora.html", Alap.URL_MESA_DIRETORA, htmlMesa);

        Alap alap = new Alap();
        alap.normalize(ctx);

        Path arqPessoas = ctx.normalizado().resolve("alap/pessoas.json");
        assertTrue(Files.exists(arqPessoas), "pessoas.json deve existir");
        JsonNode pessoas = Json.ler(arqPessoas);
        assertEquals(1, pessoas.size());

        JsonNode p1 = pessoas.get(0);
        assertEquals("alap", p1.get("fonte").stringValue());
        assertEquals("75", p1.get("id_fonte").stringValue());
        assertEquals("Alliny Serrão", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("AP", p1.get("uf").stringValue());
        assertEquals("UNIÃO", p1.get("partido").stringValue());
        assertTrue(p1.get("em_exercicio").asBoolean());

        Path arqNomesCivis = ctx.normalizado().resolve("alap/nomes-civis.json");
        assertTrue(Files.exists(arqNomesCivis), "nomes-civis.json deve existir");
        JsonNode nomesCivis = Json.ler(arqNomesCivis);
        assertEquals("ALLINY SOUSA DA ROCHA SERRÃO", nomesCivis.get("75").stringValue());

        Path arqBloco = ctx.normalizado().resolve("alap/blocos/75.json");
        assertTrue(Files.exists(arqBloco), "bloco 75.json deve existir");
        JsonNode bloco = Json.ler(arqBloco);

        // Biografia não deve conter CPF
        String bio = bloco.get("biografia").stringValue();
        assertFalse(bio.contains("123.456.789-00"), "CPF deve ter sido removido pela Guarda");
        assertTrue(bio.contains("Empresária e líder comunitária"));

        // Órgãos (Mesa Diretora)
        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Mesa Diretora", orgaos.get(0).get("nome").stringValue());
        assertEquals("Presidente", orgaos.get(0).get("papel").stringValue());

        assertEquals(0, bloco.get("despesas").size());
    }

    @Test
    void parsersCeapTratamMojibakeSubelementosEDespesas() {
        String utf8EmLatin1 = new String("LOCAÇÃO DE VEÍCULOS".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        assertEquals("Nº LOCAÇÃO DE VEÍCULOS", Alap.limparTextoPortal("N\u00ba " + utf8EmLatin1));
        assertEquals("Vigilância & Segurança", Alap.limparTextoPortal("Vigil&acirc;ncia &amp; Seguran&ccedil;a"));

        List<Integer> meses = Alap.parseMesesCeap(
                "<option value=''>--</option><option value='02'>Fev</option><option value='2'>Fev</option><option value='11'>Nov</option>");
        assertEquals(List.of(2, 11), meses);

        String depMojibake = new String("ROBERTO GÓES -UNIÃO".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        byte[] bytesDuploAlliny = "ALLINY SERRÃO - UNIÃO".getBytes(StandardCharsets.UTF_8);
        String duploEmLatin1 = new String(
                new String(bytesDuploAlliny, StandardCharsets.ISO_8859_1).getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        List<Alap.GabineteCeap> gabs = Alap.parseGabinetesCeap(
                "<option value='12'>" + depMojibake + "</option>"
                        + "<option value='55'>" + duploEmLatin1 + "</option>"
                        + "<option value='12'>DUPLICADO</option>");
        assertEquals(2, gabs.size());
        assertEquals("12", gabs.get(0).id());
        assertEquals("ROBERTO GÓES -UNIÃO", gabs.get(0).nome());
        assertEquals("55", gabs.get(1).id());
        assertEquals("ALLINY SERRÃO - UNIÃO", gabs.get(1).nome());

        String htmlBusca = """
                <tr>
                  <td><a href="ceap_exibir_detalhado.php?idgabineteB=12&nome_gabineteB=ROBERTO GÓES -UNIÃO&mes_verbaB=03&ano_verbaB=2025&cod_despesa=33&idsubelemento=55&cod_subelemento=55&nome_subelemento=LOCA&Ccedil;&Atilde;O">Locação</a></td>
                  <td><a href="ceap_exibir_detalhado.php?idgabineteB=12&nome_gabineteB=ROBERTO GÓES -UNIÃO&mes_verbaB=03&ano_verbaB=2025&cod_despesa=33&idsubelemento=55&cod_subelemento=55&nome_subelemento=LOCA&Ccedil;&Atilde;O">R$ 5.000,00</a></td>
                </tr>
                """;
        List<Alap.SubelementoCeap> subs = Alap.parseSubelementosCeap(htmlBusca);
        assertEquals(1, subs.size());
        assertEquals("55", subs.get(0).idSubelemento());
        assertEquals("LOCAÇÃO", subs.get(0).categoria());
        URI uri = URI.create(subs.get(0).urlDetalhada());
        assertTrue(uri.toASCIIString().contains("idsubelemento=55"));
        assertFalse(subs.get(0).urlDetalhada().contains(" "));

        String empMojibake = new String("12.345.678/0001-99 - AUTO LOCAÇÃO AMAPÁ LTDA".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        String catMojibake = new String("3390.39.12 - LOCAÇÃO DE VEÍCULOS".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        String htmlDetalhe = "<h4>" + catMojibake + "</h4><table>"
                + "<tr><td>Empresa</td><td>Objeto</td><td>Nº NF</td><td>Valor (R$)</td></tr>"
                + "<tr><td>" + empMojibake + "</td><td>Frota gabinete</td><td>NF 1042</td><td>1.500,50</td></tr>"
                + "<tr><td>111.222.333-44 - PRESTADOR X 999.888.777-66</td><td>Serviço</td><td>S/N</td><td>800,00</td></tr>"
                + "<tr><td>TOTAL</td><td></td><td></td><td>2.300,50</td></tr>"
                + "</table>";
        Prov prov = new Prov(Alap.FONTE_CEAP, Alap.URL_CEAP_DETALHE, Instant.parse("2025-03-10T00:00:00Z"));
        List<Despesa> despesas = Alap.parseDetalheCeap(htmlDetalhe, 2025, 3, "FALLBACK", Alap.URL_CEAP_DETALHE, prov);
        assertEquals(2, despesas.size());

        Despesa pj = despesas.get(0);
        assertEquals(2025, pj.ano());
        assertEquals(3, pj.mes());
        assertEquals("LOCAÇÃO DE VEÍCULOS", pj.categoria());
        assertEquals("12345678000199", pj.fornecedorCnpj());
        assertEquals("AUTO LOCAÇÃO AMAPÁ LTDA", pj.fornecedorNome());
        assertEquals("NF 1042", pj.documentoNumero());
        assertEquals(new BigDecimal("1500.50"), pj.valor());

        Despesa pf = despesas.get(1);
        assertNull(pf.fornecedorCnpj());
        assertNull(pf.documentoNumero());
        assertFalse(pf.fornecedorNome().contains("999.888.777-66"));
        assertTrue(pf.fornecedorNome().contains("PRESTADOR X"));
        assertEquals(new BigDecimal("800.00"), pf.valor());
    }

    @Test
    void resolverGabineteDesambiguaERejeitaAmbiguidade() {
        List<Alap.DeputadoCard> deputados = List.of(
                new Alap.DeputadoCard("61", "Pastor Oliveira", "CARLOS ANTONIO OLIVEIRA SANTOS", "REPUBLICANOS", null, null, null),
                new Alap.DeputadoCard("66", "Roberto Góes", "ROBERTO CARLOS FERREIRA GÓES", "UNIÃO", null, null, null),
                new Alap.DeputadoCard("90", "Silva Junior", "JOÃO SILVA SANTOS", "PDT", null, null, null),
                new Alap.DeputadoCard("91", "Maria Silva", "MARIA SILVA SANTOS", "PSD", null, null, null));

        assertEquals("66", Alap.resolverGabinete(new Alap.GabineteCeap("1", "ROBERTO GOES -UNIÃO"), deputados).id());
        assertEquals("61", Alap.resolverGabinete(new Alap.GabineteCeap("2", "OLIVEIRA SANTOS - REPUBLICANOS"), deputados).id());
        assertNull(Alap.resolverGabinete(new Alap.GabineteCeap("3", "SILVA SANTOS - PDT"), deputados));
    }

    @Test
    void normalizacaoComCeapEmIsoIncluiDespesasEFontes(@TempDir Path tempDir) throws Exception {
        Contexto ctx = new Contexto(tempDir, tempDir.resolve("data"), null, null,
                Instant.parse("2026-11-11T12:00:00Z"), null);
        Path rawAlap = ctx.raw().resolve("alap");
        Files.createDirectories(rawAlap.resolve("parlamentares"));

        String htmlLeg = """
                <div class="ls-box col-md-2 col-xs-5 ls-no-radius">
                    <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=75" onmouseover="Tip('<b>Dep.</b> Alliny Serrão<br><b>Nome Completo:</b> ALLINY SOUSA DA ROCHA SERRÃO<br><b>Partido:</b> UNIÃO BRASIL (UNIÃO)<br><b>Profissão:</b> Empresária', WIDTH, 200)"><img class="foto-deputado" src="fotodeputado/a.jpg"></a></div>
                    <p class="ls-title-5">Deputada Alliny Serrão </p>
                </div>
                """;
        gravarCompactado(rawAlap, "legislatura.html", Alap.URL_LEGISLATURA, htmlLeg);
        gravarCompactadoCharset(rawAlap, "ceap/gabinetes/2025-03.html", Alap.URL_CEAP_GABINETES,
                "<option value='10'>ALLINY SERRÃO - UNIÃO</option>", StandardCharsets.ISO_8859_1);
        gravarCompactadoCharset(rawAlap, "ceap/buscar/2025-03/10.html", Alap.URL_CEAP_BUSCAR,
                "<a href=\"ceap_exibir_detalhado.php?idgabineteB=10&idsubelemento=31&nome_subelemento=COMBUST%C3%8DVEL\">Ver</a>",
                StandardCharsets.ISO_8859_1);
        String linhaMojibake = new String("98.765.432/0001-10 - POSTO SOL NASCENTE LTDA".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        String htmlDetalhe = "<div>3390.30.01 - COMBUSTÍVEIS</div><table>"
                + "<tr><td>" + linhaMojibake + "</td><td>Abastecimento</td><td>123</td><td>2.450,00</td></tr>"
                + "</table>";
        gravarCompactadoCharset(rawAlap, "ceap/detalhe/2025-03/10_31.html",
                Alap.URL_CEAP_DETALHE + "?idgabineteB=10&idsubelemento=31", htmlDetalhe, StandardCharsets.ISO_8859_1);

        new Alap().normalize(ctx);

        JsonNode bloco = Json.ler(ctx.normalizado().resolve("alap/blocos/75.json"));
        JsonNode despesas = bloco.get("despesas");
        assertEquals(1, despesas.size());
        assertEquals(2025, despesas.get(0).get("ano").asInt());
        assertEquals(3, despesas.get(0).get("mes").asInt());
        assertEquals("COMBUSTÍVEIS", despesas.get(0).get("categoria").stringValue());
        assertEquals("POSTO SOL NASCENTE LTDA", despesas.get(0).get("fornecedor_nome").stringValue());
        assertEquals("98765432000110", despesas.get(0).get("fornecedor_cnpj").stringValue());
        assertEquals(2450.0, despesas.get(0).get("valor").asDouble());

        boolean temFonteCeap = false;
        for (JsonNode f : bloco.get("fontes").get("despesas")) {
            if (Alap.FONTE_CEAP.equals(f.get("fonte_id").stringValue())) {
                temFonteCeap = true;
            }
        }
        assertTrue(temFonteCeap, "fontes.despesas do bloco deve incluir alap-ceap");
    }

    private static void gravarCompactado(Path dir, String arquivo, String url, String conteudo) throws IOException {
        gravarCompactadoCharset(dir, arquivo, url, conteudo, StandardCharsets.UTF_8);
    }

    private static void gravarCompactadoCharset(Path dir, String arquivo, String url, String conteudo, Charset charset)
            throws IOException {
        Path gz = dir.resolve(arquivo + ".gz");
        Files.createDirectories(gz.getParent());
        try (OutputStream out = Files.newOutputStream(gz); GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(conteudo.getBytes(charset));
        }
        Path meta = dir.resolve(arquivo + ".meta.json");
        Files.createDirectories(meta.getParent());
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alap/" + arquivo + ".gz", "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
