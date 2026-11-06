package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
