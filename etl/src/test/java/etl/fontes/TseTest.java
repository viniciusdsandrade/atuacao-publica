package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TseTest {

    @Test
    void eleicoesCobertasNaoEncolhemComOTempo() {
        assertEquals(List.of(2018, 2022, 2024, 2026), Tse.eleicoes(2026));
        assertEquals(List.of(2018, 2022, 2024, 2026), Tse.eleicoes(2027));
        assertEquals(List.of(2018, 2022, 2024, 2026, 2028, 2030), Tse.eleicoes(2030));
    }
}
