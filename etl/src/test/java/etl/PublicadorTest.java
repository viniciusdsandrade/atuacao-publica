package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Identificadores sintéticos. */
class PublicadorTest {

    private static Publicador.Perfil perfil(String id, String chave, Map<String, String> tse) {
        Publicador.Perfil p = new Publicador.Perfil();
        p.id = id;
        p.chaveExterna = chave;
        p.tse.putAll(tse);
        return p;
    }

    @Test
    void fontesDoBlocoSaemSemRepeticaoEVemDosRegistrosQuandoFaltam() {
        JsonNode fonte = Json.FONTE.readTree("{\"fonte_id\": \"x\", \"url_origem\": \"https://exemplo.invalid/a\", \"capturado_em\": \"2026-11-07T00:00:00Z\"}");
        JsonNode registro = Json.FONTE.readTree("{\"valor\": 1, \"fonte_id\": \"y\", \"url_origem\": \"https://exemplo.invalid/b\","
                + " \"capturado_em\": \"2026-11-06T00:00:00Z\"}");
        JsonNode semProveniencia = Json.FONTE.readTree("{\"valor\": 2}");

        // A mesma fonte declarada três vezes fica uma.
        assertEquals(1, Publicador.fontesDoBloco(List.of(fonte, fonte, fonte), List.of(registro)).size());
        // Sem fonte declarada, o bloco cita a proveniência dos registros, sem repetir.
        List<JsonNode> dosRegistros = Publicador.fontesDoBloco(List.of(), List.of(registro, registro, semProveniencia));
        assertEquals(1, dosRegistros.size());
        assertEquals("https://exemplo.invalid/b", dosRegistros.getFirst().get("url_origem").stringValue());
        assertTrue(Publicador.fontesDoBloco(List.of(), List.of(semProveniencia)).isEmpty());
    }

    @Test
    void ausenciaJustificadaContaAParteDoTipoDeEvento() {
        JsonNode presente = Json.FONTE.readTree("{\"tipo\": \"Sessão Ordinária\", \"presenca\": \"registrada\"}");
        JsonNode ausente = Json.FONTE.readTree("{\"tipo\": \"Sessão Ordinária\", \"presenca\": \"ausencia_justificada\"}");

        assertEquals("Sessão Ordinária", Publicador.tipoNaContagem("eventos", presente, "Sessão Ordinária"));
        assertEquals("Sessão Ordinária · ausência justificada", Publicador.tipoNaContagem("eventos", ausente, "Sessão Ordinária"));
        JsonNode falta = Json.FONTE.readTree("{\"tipo\": \"Sessão Ordinária\", \"presenca\": \"falta\"}");
        assertEquals("Sessão Ordinária · falta", Publicador.tipoNaContagem("eventos", falta, "Sessão Ordinária"));
        // Fora do bloco de eventos, o tipo não muda.
        assertEquals("Sessão Ordinária", Publicador.tipoNaContagem("discursos", ausente, "Sessão Ordinária"));
    }

    @Test
    void todoCargoDoSchemaTemBlocos() {
        // Cargo fora da tabela de blocos sai publicado sem bloco nenhum, nem mandato.
        Path comum = Contexto.acharRaiz(Path.of("").toAbsolutePath()).resolve("schemas/comum.schema.json");
        JsonNode cargos = Json.ler(comum).path("$defs").path("cargo").path("enum");
        assertTrue(cargos.size() > 0);
        for (JsonNode cargo : cargos) {
            assertTrue(Publicador.BLOCOS.containsKey(cargo.stringValue()), "cargo sem blocos: " + cargo.stringValue());
            assertTrue(Publicador.BLOCOS.get(cargo.stringValue()).contains("mandatos"), "cargo sem mandatos: " + cargo.stringValue());
        }
    }

    @Test
    void eleitoQueTomaPosseMantemOIdPublicadoAntesDaPosse() {
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of("tse:900", "tse-900"), Set.of("tse-900"), Map.of());
        Publicador.Perfil deputado = perfil("cam-1", "camara:1", Map.of("2026", "900"));
        deputado.ids.put("camara", "1");

        Map<String, String> aliases = Publicador.identificadores(List.of(deputado), anterior);

        assertEquals("tse-900", deputado.id);
        assertEquals(Map.of("cam-1", "tse-900"), aliases);
        assertTrue(Publicador.sumiram(anterior, Set.of("tse-900"), aliases, Set.of()).isEmpty());
    }

    @Test
    void chaveDaCasaVenceChaveDoTseQuandoApontamParaOMesmoIdPublicado() {
        // Vereador publicado como sapl-306 com a candidatura X; um ex-deputado novo no arquivo tem a mesma candidatura X
        // (mesma pessoa, ainda não fundida): o id publicado fica com quem tem a chave da casa, e o outro segue com o seu.
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of("sapl:306", "sapl-3509502-306", "tse:X", "sapl-3509502-306"),
                Set.of("sapl-3509502-306"), Map.of());
        Publicador.Perfil deputado = perfil("cam-1", "camara:1", Map.of("2024", "X"));
        deputado.ids.put("camara", "1");
        Publicador.Perfil vereador = perfil("sapl-3509502-306", "sapl:306", Map.of("2024", "X"));
        vereador.ids.put("sapl", "306");

        Map<String, String> aliases = Publicador.identificadores(List.of(deputado, vereador), anterior);

        assertEquals("cam-1", deputado.id);
        assertEquals("sapl-3509502-306", vereador.id);
        assertTrue(aliases.isEmpty());
    }

    @Test
    void idPublicadoNaoPassaParaPerfilQueJaTemEsseIdComoDerivado() {
        // Pessoa publicada como sapl-306, fundida com a Câmara (camara:152610) e com o TSE (tse:X). Numa coleta incompleta
        // o vínculo não funde: o deputado e o vereador voltam em separado, e o índice anterior aponta as chaves do deputado
        // para sapl-306. O id publicado fica com o vereador, que já o tem como derivado; o deputado segue com o seu.
        // O resultado não pode depender da ordem dos perfis.
        Publicador.Anterior anterior = new Publicador.Anterior(
                Map.of("camara:152610", "sapl-3509502-306", "tse:X", "sapl-3509502-306"), Set.of("sapl-3509502-306"), Map.of());
        for (boolean deputadoPrimeiro : new boolean[] {true, false}) {
            Publicador.Perfil deputado = perfil("cam-152610", "camara:152610", Map.of("2022", "X"));
            deputado.ids.put("camara", "152610");
            Publicador.Perfil vereador = perfil("sapl-3509502-306", "sapl:306", Map.of());
            vereador.ids.put("sapl", "306");
            List<Publicador.Perfil> perfis = deputadoPrimeiro ? List.of(deputado, vereador) : List.of(vereador, deputado);

            Map<String, String> aliases = Publicador.identificadores(perfis, anterior);

            assertEquals("cam-152610", deputado.id);
            assertEquals("sapl-3509502-306", vereador.id);
            assertTrue(aliases.isEmpty());
            assertTrue(Publicador.sumiram(anterior, Set.of("cam-152610", "sapl-3509502-306"), aliases, Set.of()).isEmpty());
        }
    }

    @Test
    void aliasPublicadoContinuaValendo() {
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of("camara:1", "tse-900"), Set.of("tse-900"),
                Map.of("cam-1", "tse-900"));
        Publicador.Perfil deputado = perfil("cam-1", "camara:1", Map.of());

        Map<String, String> aliases = Publicador.identificadores(List.of(deputado), anterior);

        assertEquals(Map.of("cam-1", "tse-900"), aliases);
    }

    @Test
    void perfilQueSomeBloqueiaOPublishSalvoSeLiberado() {
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of(), Set.of("cam-1", "cam-2"), Map.of());

        assertEquals(List.of("cam-2"), Publicador.sumiram(anterior, Set.of("cam-1"), Map.of(), Set.of()));
        assertTrue(Publicador.sumiram(anterior, Set.of("cam-1"), Map.of(), Set.of("cam-2")).isEmpty());
    }

    @Test
    void proposicaoSemDataVaiParaOAnoDaIdentificacaoEVotoSemDataSegueFora() {
        JsonNode semData = Json.FONTE.readTree("{\"identificacao\":\"RAP 1/2026\",\"ano\":2026,\"data_apresentacao\":null}");
        JsonNode comData = Json.FONTE.readTree("{\"identificacao\":\"PL 9/2025\",\"ano\":2025,\"data_apresentacao\":\"2024-12-30\"}");
        JsonNode votoSemData = Json.FONTE.readTree("{\"votacao_id\":\"x\",\"ano\":2026,\"data\":null}");

        assertEquals(2026, Publicador.ano(semData, "proposicoes"));
        assertEquals(2024, Publicador.ano(comData, "proposicoes"), "a data prevalece sobre o ano da identificação");
        assertEquals(null, Publicador.ano(votoSemData, "votos"), "só proposição usa o ano da identificação");
    }

    @Test
    void escaparCsvTrataAspasEVirgulas() {
        assertEquals("", Publicador.escaparCsv(null));
        assertEquals("normal", Publicador.escaparCsv("normal"));
        assertEquals("\"com, virgula\"", Publicador.escaparCsv("com, virgula"));
        assertEquals("\"com \"\"aspas\"\"\"", Publicador.escaparCsv("com \"aspas\""));
    }

    @Test
    void gerarDumpsGeraCsvEJsonManifestComConsolidadoEPorUf() throws java.io.IOException {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("dumps-test");
        try {
            tools.jackson.databind.node.ArrayNode indice = Json.SAIDA.createArrayNode();
            indice.add(Json.SAIDA.createObjectNode()
                    .put("id", "cam-1")
                    .put("nome", "Deputado A")
                    .put("cargo", "deputado_federal")
                    .put("esfera", "federal")
                    .put("ente", "BR")
                    .put("uf", "SP")
                    .put("partido", "PL")
                    .put("situacao", "Exercício")
                    .put("em_exercicio", true)
                    .put("foto_url", "https://exemplo.org/foto.jpg"));

            indice.add(Json.SAIDA.createObjectNode()
                    .put("id", "alep-2")
                    .put("nome", "Deputado B")
                    .put("cargo", "deputado_estadual")
                    .put("esfera", "estadual")
                    .put("ente", "PR")
                    .put("uf", "PR")
                    .put("partido", "PT")
                    .put("situacao", "Exercício")
                    .put("em_exercicio", true)
                    .put("foto_url", ""));

            java.nio.file.Path pastaDumps = tempDir.resolve("dumps");
            Publicador.gerarDumps(indice, pastaDumps, java.time.Instant.parse("2026-11-06T15:00:00Z"));

            assertTrue(java.nio.file.Files.exists(pastaDumps.resolve("politicos.csv")));
            assertTrue(java.nio.file.Files.exists(pastaDumps.resolve("politicos-federal.csv")));
            assertTrue(java.nio.file.Files.exists(pastaDumps.resolve("politicos-SP.csv")));
            assertTrue(java.nio.file.Files.exists(pastaDumps.resolve("politicos-PR.csv")));
            assertTrue(java.nio.file.Files.exists(pastaDumps.resolve("manifest.json")));

            List<String> linhasGeral = java.nio.file.Files.readAllLines(pastaDumps.resolve("politicos.csv"));
            assertEquals(3, linhasGeral.size()); // cabeçalho + 2 políticos

            tools.jackson.databind.JsonNode manifest = Json.ler(pastaDumps.resolve("manifest.json"));
            assertEquals(4, manifest.get("total_arquivos").asInt());
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        java.nio.file.Files.delete(p);
                    } catch (java.io.IOException ignored) {
                    }
                });
            }
        }
    }
}
