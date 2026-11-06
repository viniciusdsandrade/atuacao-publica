package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class CmspTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 5);

    /**
     * Registros sintéticos: dois em exercício (o segundo sucede o primeiro como 1º Vice, sem o primeiro ter data de fim);
     * um com mandato vigente mas sem gabinete (saiu em 2025); e um homônimo antigo, sem mandato vigente.
     */
    private static final String VEREADORES = """
            [
              {
                "chave": 2266,
                "nome": "FULANO DE TAL",
                "cargos": [
                  {"chave": "P", "nome": "Presidente", "inicio": "2025-03-01T00:00:00",
                   "ente": {"chave": 56, "nome": "Comissão - COMISSÃO DE CONSTITUIÇÃO, JUSTIÇA E LEGISLAÇÃO PARTICIPATIVA", "tipo": null}},
                  {"chave": "M", "nome": "Membro", "inicio": "2021-03-01T00:00:00", "fim": "2024-12-31T00:00:00",
                   "ente": {"chave": 57, "nome": "Comissão - COMISSÃO DE FINANÇAS E ORÇAMENTO", "tipo": null}},
                  {"chave": "2", "nome": "1º Vice", "inicio": "2025-01-01T00:00:00",
                   "ente": {"chave": 1, "nome": "MESA DA CAMARA MUNICIPAL DE SAO PAULO", "tipo": null}}
                ],
                "filiacoes": [],
                "mandatos": [
                  {"inicio": "2021-01-01T00:00:00", "fim": "2024-12-31T00:00:00",
                   "partido": {"chave": 1, "nome": "Partido A", "sigla": "AAA"}, "coligacao": null},
                  {"inicio": "2025-01-01T00:00:00", "fim": "2028-12-31T00:00:00",
                   "partido": {"chave": 2, "nome": "Partido B", "sigla": "BBB"}, "coligacao": null}
                ]
              },
              {
                "chave": 2270,
                "nome": "CICRANO SOUZA",
                "cargos": [
                  {"chave": "2", "nome": "1º Vice", "inicio": "2026-03-13T00:00:00",
                   "ente": {"chave": 1, "nome": "MESA DA CAMARA MUNICIPAL DE SAO PAULO", "tipo": null}},
                  {"chave": "5", "nome": "2º Secretário", "inicio": "2026-01-01T00:00:00", "fim": "2026-12-31T00:00:00",
                   "ente": {"chave": 1, "nome": "MESA DA CAMARA MUNICIPAL DE SAO PAULO", "tipo": null}},
                  {"chave": "M", "nome": "Membro", "inicio": "2025-03-01T00:00:00",
                   "ente": {"chave": 56, "nome": "Comissão - COMISSÃO DE CONSTITUIÇÃO, JUSTIÇA E LEGISLAÇÃO PARTICIPATIVA", "tipo": null}}
                ],
                "filiacoes": [],
                "mandatos": [
                  {"inicio": "2025-01-01T00:00:00", "fim": "2028-12-31T00:00:00",
                   "partido": {"chave": 2, "nome": "Partido B", "sigla": "BBB"}, "coligacao": null}
                ]
              },
              {
                "chave": 2255,
                "nome": "BELTRANA SILVA",
                "cargos": [],
                "filiacoes": [],
                "mandatos": [
                  {"inicio": "2025-01-01T00:00:00", "fim": "2028-12-31T00:00:00",
                   "partido": {"chave": 2, "nome": "Partido B", "sigla": "BBB"}, "coligacao": null}
                ]
              },
              {
                "chave": 83,
                "nome": "FULANO DE TAL",
                "cargos": [],
                "filiacoes": [],
                "mandatos": [
                  {"inicio": "1997-01-01T00:00:00", "fim": "2000-12-31T00:00:00",
                   "partido": {"chave": 3, "nome": "Partido C", "sigla": "CCC"}, "coligacao": null}
                ]
              }
            ]
            """;

    private static final String GABINETES = """
            [
              {"legislatura": 18, "gabinete": 1, "inicio": "2021-01-01T00:00:00", "fim": "2024-12-31T00:00:00", "codigo": 83, "vereador": "FULANO DE TAL"},
              {"legislatura": 19, "gabinete": 1, "inicio": "2025-01-01T00:00:00", "codigo": 2266, "vereador": "FULANO DE TAL"},
              {"legislatura": 19, "gabinete": 2, "inicio": "2025-01-01T00:00:00", "fim": "2025-06-30T00:00:00", "codigo": 2255, "vereador": "BELTRANA SILVA"},
              {"legislatura": 19, "gabinete": 3, "inicio": "2025-01-01T00:00:00", "codigo": 2270, "vereador": "CICRANO SOUZA"}
            ]
            """;

    private static final String COMISSOES = """
            [
              {"chave": 56, "nome": "COMISSÃO DE CONSTITUIÇÃO, JUSTIÇA E LEGISLAÇÃO PARTICIPATIVA", "sigla": "CCJ"},
              {"chave": 57, "nome": "COMISSÃO DE FINANÇAS E ORÇAMENTO", "sigla": "FIN"}
            ]
            """;

    @Test
    void emExercicioExigeGabineteVigenteEMandatoVigente() throws Exception {
        List<Cmsp.Vereador> lista = Cmsp.emExercicio(Json.FONTE.readTree(VEREADORES), Json.FONTE.readTree(GABINETES), HOJE);

        assertEquals(2, lista.size());
        Cmsp.Vereador v = lista.get(0);
        assertEquals("2266", v.chave());
        assertEquals("FULANO DE TAL", v.nome());
        assertEquals("BBB", v.partido());
        assertEquals(LocalDate.of(2025, 1, 1), v.inicio());
        assertEquals(LocalDate.of(2028, 12, 31), v.fim());
        assertEquals(19, v.legislatura());
        assertEquals("2270", lista.get(1).chave());
    }

    @Test
    void orgaosTrazSoCargosVigentesComSiglaDaComissao() throws Exception {
        List<Cmsp.Vereador> lista = Cmsp.emExercicio(Json.FONTE.readTree(VEREADORES), Json.FONTE.readTree(GABINETES), HOJE);
        Prov prov = new Prov("cmsp-splegisws", Cmsp.URL_VEREADORES, Instant.parse("2026-11-05T12:00:00Z"));
        Map<Integer, String> siglas = Cmsp.siglasDasComissoes(Json.FONTE.readTree(COMISSOES));
        Map<String, LocalDate> maisRecentes = Cmsp.inicioMaisRecenteDosExclusivos(lista, HOJE);

        // O membro de comissão encerrado em 2024 não entra. O 1º Vice de 2025, sem fim, foi sucedido em 2026.
        List<OrgaoOuFrente> primeiro = Cmsp.orgaos(lista.get(0).cargos(), siglas, maisRecentes, HOJE, prov);
        assertEquals(1, primeiro.size());
        OrgaoOuFrente comissao = primeiro.get(0);
        assertEquals("comissao", comissao.tipo());
        assertEquals("Comissão de Constituição, Justiça e Legislação Participativa", comissao.nome());
        assertEquals("CCJ", comissao.sigla());
        assertEquals("Presidente", comissao.papel());
        assertNull(comissao.fim());

        // O sucessor fica com o 1º Vice. O 2º Secretário tem fim futuro explícito e nenhum sucessor, então vale.
        List<OrgaoOuFrente> segundo = Cmsp.orgaos(lista.get(1).cargos(), siglas, maisRecentes, HOJE, prov);
        assertEquals(3, segundo.size());
        assertEquals("Membro", segundo.get(0).papel());
        OrgaoOuFrente vice = segundo.get(1);
        assertEquals("orgao", vice.tipo());
        assertEquals("Mesa da Câmara Municipal de São Paulo", vice.nome());
        assertEquals("1º Vice", vice.papel());
        assertNull(vice.fim());
        OrgaoOuFrente secretario = segundo.get(2);
        assertEquals("2º Secretário", secretario.papel());
        assertEquals(LocalDate.of(2026, 12, 31), secretario.fim());
    }

    @Test
    void membroDeComissaoNaoEhCargoExclusivo() throws Exception {
        JsonNode membro = Json.FONTE.readTree("""
                {"chave": "M", "nome": "Membro", "inicio": "2025-03-01T00:00:00",
                 "ente": {"chave": 56, "nome": "Comissão - COMISSÃO DE CONSTITUIÇÃO, JUSTIÇA E LEGISLAÇÃO PARTICIPATIVA"}}""");
        JsonNode presidente = Json.FONTE.readTree("""
                {"chave": "P", "nome": "Presidente", "inicio": "2025-03-01T00:00:00",
                 "ente": {"chave": 56, "nome": "Comissão - COMISSÃO DE CONSTITUIÇÃO, JUSTIÇA E LEGISLAÇÃO PARTICIPATIVA"}}""");
        JsonNode mesa = Json.FONTE.readTree("""
                {"chave": "2", "nome": "1º Vice", "inicio": "2025-01-01T00:00:00",
                 "ente": {"chave": 1, "nome": "MESA DA CAMARA MUNICIPAL DE SAO PAULO"}}""");

        assertEquals(false, Cmsp.exclusivo(membro));
        assertEquals(true, Cmsp.exclusivo(presidente));
        assertEquals(true, Cmsp.exclusivo(mesa));
    }

    @Test
    void formataTituloDaCaixaAltaDoServico() {
        assertEquals("Fulano de Tal", Cmsp.titulo("FULANO DE TAL"));
        assertEquals("Dr. Fulano da Silva", Cmsp.titulo("DR. FULANO DA SILVA"));
        assertEquals("Comissão de Finanças e Orçamento", Cmsp.titulo("COMISSÃO DE FINANÇAS E ORÇAMENTO"));
    }

    @Test
    void papelDeComissaoVemComoLetra() {
        assertEquals("Presidente", Cmsp.papel("P", "Presidente", true));
        assertEquals("Vice-Presidente", Cmsp.papel("V", "Vice-presidente", true));
        assertEquals("Membro", Cmsp.papel("M", "Membro", true));
        assertEquals("Relator", Cmsp.papel("R", "Relator", true));
        assertEquals("2º Secretário", Cmsp.papel("5", "2º Secretário", false));
    }

    @Test
    void normalizaVereadoresComTempDir(@TempDir Path tempDir) throws Exception {
        Contexto ctx = new Contexto(tempDir, tempDir.resolve("data"), null, null, Instant.parse("2026-11-05T12:00:00Z"), null);
        Path raw = ctx.raw().resolve("cmsp");
        Files.createDirectories(raw);
        Files.writeString(raw.resolve("vereadores.json"), VEREADORES);
        gravarMetadado(raw, "vereadores.json", Cmsp.URL_VEREADORES);
        Files.writeString(raw.resolve("gabinetes.json"), GABINETES);
        gravarMetadado(raw, "gabinetes.json", Cmsp.URL_GABINETES);
        Files.writeString(raw.resolve("comissoes.json"), COMISSOES);
        gravarMetadado(raw, "comissoes.json", Cmsp.URL_COMISSOES);
        Files.writeString(raw.resolve("membros.html"), "<html></html>");
        gravarMetadado(raw, "membros.html", Cmsp.URL_MEMBROS);

        new Cmsp().normalize(ctx);

        JsonNode pessoas = Json.ler(ctx.normalizado().resolve("cmsp/pessoas.json"));
        assertEquals(2, pessoas.size());
        assertEquals("Cicrano Souza", pessoas.get(0).get("nome").stringValue());
        JsonNode p = pessoas.get(1);
        assertEquals("cmsp", p.get("fonte").stringValue());
        assertEquals("2266", p.get("id_fonte").stringValue());
        assertEquals("Fulano de Tal", p.get("nome").stringValue());
        assertEquals("vereador", p.get("cargo").stringValue());
        assertEquals("municipal", p.get("esfera").stringValue());
        assertEquals("3550308", p.get("ente").stringValue());
        assertEquals("SP", p.get("uf").stringValue());
        assertEquals("BBB", p.get("partido").stringValue());
        assertTrue(p.get("em_exercicio").asBoolean());
        assertEquals(1, p.get("links").size());

        JsonNode bloco = Json.ler(ctx.normalizado().resolve("cmsp/blocos/2266.json"));
        JsonNode mandato = bloco.get("mandatos").get(0);
        assertEquals("vereador", mandato.get("cargo").stringValue());
        assertEquals(19, mandato.get("legislatura").asInt());
        assertEquals(2024, mandato.get("eleicao").asInt());
        assertEquals(1, bloco.get("orgaos_e_frentes").size());
        // O Vinculo e o Publicador leem as fontes por bloco: um objeto, não uma lista.
        assertTrue(bloco.get("fontes").isObject());
        assertEquals(2, bloco.get("fontes").get("mandatos").size());
        assertEquals(2, bloco.get("fontes").get("orgaos_e_frentes").size());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/cmsp/" + arquivo, "abc", 100L, Instant.parse("2026-11-05T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
