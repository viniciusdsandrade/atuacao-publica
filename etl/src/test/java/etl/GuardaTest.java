package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Dados sintéticos: os números abaixo não pertencem a ninguém. */
class GuardaTest {

    @Test
    void bloqueiaCpfFormatadoESemFormato() {
        assertEquals(List.of("CPF"), Guarda.violacoes("documento 123.456.789-09 do fornecedor"));
        assertEquals(List.of("CPF"), Guarda.violacoes("FULANO DE TAL 12345678909"));
    }

    @Test
    void naoConfundeCnpjNemIdentificadorLongoComCpf() {
        assertTrue(Guarda.violacoes("CNPJ 12345678000195").isEmpty());
        assertTrue(Guarda.violacoes("SQ_CANDIDATO 250001799989").isEmpty());
        assertTrue(Guarda.violacoes("votação 2585316-23").isEmpty());
    }

    @Test
    void bloqueiaTelefoneComDddEEmail() {
        assertEquals(List.of("telefone"), Guarda.violacoes("gabinete (61) 3215-5000"));
        assertEquals(List.of("telefone"), Guarda.violacoes("celular 61 99999-0000"));
        assertEquals(List.of("e-mail"), Guarda.violacoes("escreva para pessoa.exemplo@exemplo.invalid"));
    }

    @Test
    void naoConfundeDataNemValorComTelefone() {
        assertTrue(Guarda.violacoes("sessão de 2026-10-28").isEmpty());
        assertTrue(Guarda.violacoes("valor de R$ 1.234,56").isEmpty());
    }

    @Test
    void identificadorDeVotacaoDoSenadoNaoEhTelefone() {
        var registro = Json.arvore(Map.of("votacao_id", "586477-7104", "descricao", "Votação nominal"));
        assertTrue(Guarda.violacoes(registro).isEmpty());
    }

    @Test
    void removeCpfDoNomeDeMei() {
        assertEquals("FULANO DE TAL", Guarda.semCpf("FULANO DE TAL 12345678909"));
        assertEquals("POSTO EXEMPLO LTDA", Guarda.semCpf("POSTO EXEMPLO LTDA"));
    }

    @Test
    void sanitizaCpfTelefoneEEmailDoTextoOficial() {
        assertEquals("PRESTADOR X", Guarda.sanitizar("PRESTADOR X 999.888.777-66"));
        assertEquals("EMPRESA EXEMPLO LTDA",
                Guarda.sanitizar("EMPRESA EXEMPLO LTDA (96) 3222-1000 pessoa.exemplo@exemplo.invalid"));
        assertEquals("POSTO EXEMPLO LTDA", Guarda.sanitizar("POSTO EXEMPLO LTDA"));
        assertEquals(null, Guarda.sanitizar("123.456.789-09"));
    }

    @Test
    void varreArvoreEIgnoraIdentificadoresEUrls() {
        var registro = Json.arvore(Map.of(
                "id_fonte", "12345678909",
                "url_origem", "https://exemplo.invalid/doc?id=12345678909",
                "fornecedor_nome", "FULANO 12345678909"));
        assertEquals(List.of("CPF em /fornecedor_nome"), Guarda.violacoes(registro));
    }
}
