package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Publicador.Perfil;
import etl.Registros.FonteUsada;

/** O perfil publicado cita as fontes e a data de cada bloco. O Vinculo lê um objeto com uma lista por bloco. */
class FontesPorBlocoTest {

    private static final String PESSOAS = """
            [{"fonte": "alerj", "id_fonte": "1", "nome": "Fulana de Tal", "cargo": "deputado_estadual",
              "esfera": "estadual", "ente": "RJ", "uf": "RJ", "partido": "AAA", "situacao": "Em exercício",
              "em_exercicio": true, "links": []}]
            """;

    private static final String REGISTROS = """
            "mandatos": [
              {"cargo": "deputado_estadual", "fonte_id": "alerj-deputados-detalhes",
               "url_origem": "https://exemplo.gov.br/perfil/1", "capturado_em": "2026-11-05T10:00:00Z"}
            ],
            "orgaos_e_frentes": [
              {"nome": "Comissão A", "papel": "Titular", "fonte_id": "alerj-deputados-detalhes",
               "url_origem": "https://exemplo.gov.br/comissoes/a", "capturado_em": "2026-11-05T11:00:00Z"},
              {"nome": "Comissão A", "papel": "Relator", "fonte_id": "alerj-deputados-detalhes",
               "url_origem": "https://exemplo.gov.br/comissoes/a", "capturado_em": "2026-11-05T11:00:00Z"},
              {"nome": "Comissão B", "papel": "Suplente", "fonte_id": "alerj-deputados-detalhes",
               "url_origem": "https://exemplo.gov.br/comissoes/b", "capturado_em": "2026-11-05T12:00:00Z"}
            ],
            "despesas": []
            """;

    @Test
    void fontesDeCadaBlocoVemDosRegistrosSemRepeticao() {
        Map<String, List<FonteUsada>> fontes = Registros.fontesDosBlocos(Json.FONTE.readTree("{" + REGISTROS + "}"));

        assertEquals(List.of("mandatos", "orgaos_e_frentes"), List.copyOf(fontes.keySet()));
        assertEquals(List.of(new FonteUsada("alerj-deputados-detalhes", "https://exemplo.gov.br/perfil/1",
                Instant.parse("2026-11-05T10:00:00Z"))), fontes.get("mandatos"));
        // Dois registros da mesma página contam uma fonte só.
        assertEquals(2, fontes.get("orgaos_e_frentes").size());
        assertEquals("https://exemplo.gov.br/comissoes/b", fontes.get("orgaos_e_frentes").get(1).urlOrigem());
        assertFalse(fontes.containsKey("despesas"));
    }

    @Test
    void vinculoLeAsFontesDeCadaBloco(@TempDir Path tmp) throws IOException {
        Contexto ctx = casa(tmp, "{" + REGISTROS + ", \"fontes\": "
                + Json.SAIDA.writeValueAsString(Registros.fontesDosBlocos(Json.FONTE.readTree("{" + REGISTROS + "}"))) + "}");

        List<Perfil> perfis = Vinculo.legisladores(ctx, "alerj", "alerj", "alerj");

        assertEquals(1, perfis.size());
        assertEquals(1, perfis.getFirst().fontes.get("mandatos").size());
        assertEquals(2, perfis.getFirst().fontes.get("orgaos_e_frentes").size());
    }

    @Test
    void fontesEmListaInterrompemOVinculo(@TempDir Path tmp) throws IOException {
        Contexto ctx = casa(tmp, "{" + REGISTROS + ", \"fontes\": [{\"fonte_id\": \"alerj-deputados\","
                + " \"url_origem\": \"https://exemplo.gov.br/deputados\", \"capturado_em\": \"2026-11-05T09:00:00Z\"}]}");

        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> Vinculo.legisladores(ctx, "alerj", "alerj", "alerj"));
        assertTrue(erro.getMessage().contains("alerj/blocos/1.json"));
    }

    private static Contexto casa(Path tmp, String blocos) throws IOException {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse("2026-11-06T12:00:00Z"), null);
        Path pasta = ctx.normalizado().resolve("alerj");
        Files.createDirectories(pasta.resolve("blocos"));
        Files.writeString(pasta.resolve("pessoas.json"), PESSOAS);
        Files.writeString(pasta.resolve("blocos").resolve("1.json"), blocos);
        return ctx;
    }
}
