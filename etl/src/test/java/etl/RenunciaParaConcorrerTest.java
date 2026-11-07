package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etl.fontes.Tse;
import tools.jackson.databind.JsonNode;

/** Chefe do Executivo que concorre a outro cargo renuncia até seis meses antes do pleito (ADR-0033). Dados sintéticos. */
class RenunciaParaConcorrerTest {

    @Test
    void prazoDeRenunciaESeisMesesAntesDoPrimeiroDomingoDeOutubro() {
        assertEquals(LocalDate.of(2026, 4, 4), Vinculo.prazoDeRenuncia(2026));
        assertEquals(LocalDate.of(2022, 4, 2), Vinculo.prazoDeRenuncia(2022));
    }

    @Test
    void candidaturaAOutroCargoEDaMesmaPessoaEReeleicaoNaoConta() {
        JsonNode eleito = linha("1", 2022, "GOVERNADOR");
        Map<String, Tse.Chave> chaves = Map.of(
                "1", new Tse.Chave("00000000001", "FULANO DE TAL", "XX", "1970-01-01", "GOVERNADOR"),
                "2", new Tse.Chave("00000000002", "BELTRANO", "XX", "1971-01-01", "SENADOR"),
                "3", new Tse.Chave("00000000001", "FULANO DE TAL", "XX", "1970-01-01", "SENADOR"),
                "4", new Tse.Chave(null, "FULANO DE TAL", "XX", "1970-01-01", "GOVERNADOR"));

        // Outra pessoa concorrendo a senador não conta; a mesma pessoa, pelo CPF, conta.
        List<JsonNode> comSenado = List.of(eleito, linha("2", 2026, "SENADOR"), linha("3", 2026, "SENADOR"));
        assertEquals("3", Vinculo.candidaturaAOutroCargo(eleito, 2026, comSenado, chaves).get("sq").stringValue());

        // Reeleição para governador, mesmo sem CPF (pelo nome civil e nascimento), não encerra o mandato.
        List<JsonNode> reeleicao = List.of(eleito, linha("4", 2026, "GOVERNADOR"));
        assertNull(Vinculo.candidaturaAOutroCargo(eleito, 2026, reeleicao, chaves));
    }

    private static JsonNode linha(String sq, int ano, String cargo) {
        return Json.FONTE.readTree("{\"sq\": \"" + sq + "\", \"ano\": " + ano + ", \"cargo\": \"" + cargo + "\"}");
    }
}
