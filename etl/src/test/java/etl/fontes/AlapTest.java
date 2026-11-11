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
        Files.writeString(rawAlap.resolve("legislatura.html"), htmlLeg);
        gravarMetadado(rawAlap, "legislatura.html", Alap.URL_LEGISLATURA);

        String htmlMesa = """
                <div class="box-foto-deputados"><a href="pagina.php?pg=exibir_parlamentar&iddeputado=75"><img src="foto.jpg"></a></div>
                <p class="ls-title-5">Alliny Serrão <br> <strong>Presidente</strong> </p>
                """;
        Files.writeString(rawAlap.resolve("mesa_diretora.html"), htmlMesa);
        gravarMetadado(rawAlap, "mesa_diretora.html", Alap.URL_MESA_DIRETORA);

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
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alap/" + arquivo, "abc", 100L, Instant.parse("2026-11-11T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
