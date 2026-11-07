package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TseTest {

    @Test
    void eleicoesCobertasNaoEncolhemComOTempo() {
        assertEquals(List.of(2018, 2020, 2022, 2024, 2026), Tse.eleicoes(2026));
        assertEquals(List.of(2018, 2020, 2022, 2024, 2026), Tse.eleicoes(2027));
        assertEquals(List.of(2018, 2020, 2022, 2024, 2026, 2028, 2030), Tse.eleicoes(2030));
    }

    @Test
    void aceitaDeputadoEstadualParaUfsCobertas() {
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "BA")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "RJ")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "RS")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "CE")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "SC")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "GO")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "ES")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "PA")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "PE")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO DISTRITAL", "SG_UF", "DF")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "PR")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "SP")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "MG")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "AC")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO FEDERAL", "SG_UF", "AC")));
    }

    @Test
    void cadaMunicipioComecaNaSuaPrimeiraEleicao() {
        // O piloto municipal entra desde 2020. A capital só desde 2024: em 2020 o eleito prefeito não completou o mandato.
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "62910", "ANO_ELEICAO", "2020")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "PREFEITO", "SG_UE", "62910", "ANO_ELEICAO", "2024")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072", "ANO_ELEICAO", "2020")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "PREFEITO", "SG_UE", "71072", "ANO_ELEICAO", "2020")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072", "ANO_ELEICAO", "2024")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "VICE-PREFEITO", "SG_UE", "71072", "ANO_ELEICAO", "2024")));
    }

    @Test
    void municipioForaDaCoberturaOuSemDadosNaoEntra() {
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "99999", "ANO_ELEICAO", "2024")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "ANO_ELEICAO", "2024")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072", "ANO_ELEICAO", "x")));
    }

    @Test
    void tituloLevaOCpfDeOutraEleicaoSoQuandoACorrespondenciaEInequivoca() {
        // Dados sintéticos. Em 2024 o CPF vem mascarado e o título vem completo.
        Map<String, Tse.Chave> chaves = new HashMap<>();
        Map<String, String> titulos = new HashMap<>();
        // Mesma pessoa em 2022 e 2024, com o nome civil grafado de outro jeito: o título faz a ponte.
        chaves.put("sq-a-2022", new Tse.Chave("12345678909", "FULANA DE TAL", "SP", "1970-01-01", "DEPUTADO FEDERAL"));
        chaves.put("sq-a-2024", new Tse.Chave(null, "FULANA DE TAL SOBRENOME", "SP", "1970-01-01", "VEREADOR"));
        titulos.put("sq-a-2022", "100000000001");
        titulos.put("sq-a-2024", "100000000001");
        // O mesmo título com dois CPFs (erro de digitação na fonte): ninguém herda CPF.
        chaves.put("sq-b-2022", new Tse.Chave("11111111111", "BELTRANO", "RJ", "1980-02-02", "DEPUTADO FEDERAL"));
        chaves.put("sq-b-2026", new Tse.Chave("22222222222", "BELTRANO", "RJ", "1980-02-02", "DEPUTADO FEDERAL"));
        chaves.put("sq-b-2024", new Tse.Chave(null, "BELTRANO", "RJ", "1980-02-02", "VEREADOR"));
        titulos.put("sq-b-2022", "200000000002");
        titulos.put("sq-b-2026", "200000000002");
        titulos.put("sq-b-2024", "200000000002");
        // O mesmo CPF com dois títulos: também fica de fora.
        chaves.put("sq-c-2018", new Tse.Chave("33333333333", "CICRANO", "MG", "1990-03-03", "SENADOR"));
        chaves.put("sq-c-2022", new Tse.Chave("33333333333", "CICRANO", "MG", "1990-03-03", "DEPUTADO FEDERAL"));
        chaves.put("sq-c-2024", new Tse.Chave(null, "CICRANO", "MG", "1990-03-03", "VEREADOR"));
        titulos.put("sq-c-2018", "300000000003");
        titulos.put("sq-c-2022", "300000000033");
        titulos.put("sq-c-2024", "300000000003");
        // Título igual com data de nascimento diferente: fica de fora.
        chaves.put("sq-d-2022", new Tse.Chave("44444444444", "DEUTRANO", "BA", "1960-04-04", "DEPUTADO FEDERAL"));
        chaves.put("sq-d-2024", new Tse.Chave(null, "DEUTRANO", "BA", "1961-04-04", "VEREADOR"));
        titulos.put("sq-d-2022", "400000000004");
        titulos.put("sq-d-2024", "400000000004");
        // Pessoa só com a eleição mascarada: segue sem CPF e não conta como ambígua.
        chaves.put("sq-e-2024", new Tse.Chave(null, "ESSETRANO", "SP", "1950-05-05", "VEREADOR"));
        titulos.put("sq-e-2024", "500000000005");

        Tse.PeloTitulo resultado = Tse.completarCpfPeloTitulo(chaves, titulos);

        assertEquals("12345678909", resultado.chaves().get("sq-a-2024").cpf());
        assertEquals("FULANA DE TAL SOBRENOME", resultado.chaves().get("sq-a-2024").nomeCivil());
        assertNull(resultado.chaves().get("sq-b-2024").cpf());
        assertNull(resultado.chaves().get("sq-c-2024").cpf());
        assertNull(resultado.chaves().get("sq-d-2024").cpf());
        assertNull(resultado.chaves().get("sq-e-2024").cpf());
        assertEquals("11111111111", resultado.chaves().get("sq-b-2022").cpf(), "candidatura com CPF próprio não muda");
        assertEquals(1, resultado.ligadas());
        assertEquals(3, resultado.ambiguas());
    }

    @Test
    void ueMunicipalTrazOCodigoIbgeDoEnte() {
        assertEquals("3509502", Tse.UES_MUNICIPAIS.get("62910").ibge());
        assertEquals("3550308", Tse.UES_MUNICIPAIS.get("71072").ibge());
    }
}
