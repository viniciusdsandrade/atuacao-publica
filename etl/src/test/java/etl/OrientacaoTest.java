package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Rótulos de bancada no formato visto nos arquivos da Câmara e do Senado em 2026. */
class OrientacaoTest {

    private static final Map<String, String> BANCADAS = new LinkedHashMap<>();

    static {
        BANCADAS.put("PL", "Não");
        BANCADAS.put("Fdr PT-PCdoB-PV", "Sim");
        BANCADAS.put("Fdr PSDB-CIDADAN", "Liberado");
        BANCADAS.put("Solidaried", "Sim");
        BANCADAS.put("Bl UniPpPsd...", "Sim");
        BANCADAS.put("Progressistas", "Obstrução");
        BANCADAS.put("Governo", "Sim");
    }

    @Test
    void partidoIgual() {
        assertEquals("Não", Orientacao.doPartido("PL", BANCADAS).orientacao());
    }

    @Test
    void partidoDentroDeFederacaoInclusiveComRotuloTruncado() {
        assertEquals("Sim", Orientacao.doPartido("PCdoB", BANCADAS).orientacao());
        assertEquals("Fdr PT-PCdoB-PV", Orientacao.doPartido("PT", BANCADAS).bancada());
        assertEquals("Liberado", Orientacao.doPartido("CIDADANIA", BANCADAS).orientacao());
    }

    @Test
    void rotuloTruncadoEApelido() {
        assertEquals("Sim", Orientacao.doPartido("SOLIDARIEDADE", BANCADAS).orientacao());
        assertEquals("Obstrução", Orientacao.doPartido("PP", BANCADAS).orientacao());
    }

    @Test
    void blocoComNomeTruncadoNaoDefinePartido() {
        assertNull(Orientacao.doPartido("PSD", BANCADAS));
        assertNull(Orientacao.doPartido("UNIÃO", BANCADAS));
    }

    @Test
    void siglaCurtaNaoCasaPorPrefixo() {
        Map<String, String> bancadas = Map.of("PSB", "Sim");
        assertNull(Orientacao.doPartido("PSBX", bancadas));
    }
}
