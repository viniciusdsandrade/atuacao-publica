package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TextoTest {

    @Test
    void normalizaNomeParaJuncaoEntreFontes() {
        assertEquals("VENEZIANO VITAL DO REGO", Texto.normalizarNome("Veneziano  Vital do Rêgo "));
        assertEquals(Texto.normalizarNome("JOSÉ DA SILVA"), Texto.normalizarNome("José da Silva"));
        assertEquals("ACACIO FAVACHO", Texto.normalizarNome("Acácio\tFavacho"));
    }

    @Test
    void trocaEntidadesHtmlSemMudarConteudo() {
        assertEquals("orientou a bancada na votação", Texto.semEntidades("orientou a&nbsp;bancada na vota&#231;&atilde;o"));
        assertNull(Texto.semEntidades("&nbsp;"));
        assertEquals("Lei nº 1, art. 5º, § 2º: \u201Cprazo\u201D de 30° em 5 m³", Texto.semEntidades("Lei n&ordm; 1, art. 5&ordm;, &sect; 2&ordm;: &ldquo;prazo&rdquo; de 30&deg; em 5 m&sup3;&lrm;"));
    }

    @Test
    void lerValorBrasileiroEComPonto() {
        assertEquals(new BigDecimal("1234.56"), Texto.valor("1.234,56"));
        assertEquals(new BigDecimal("954.3"), Texto.valor("954.3"));
        assertNull(Texto.valor("N/D"));
    }

    @Test
    void cnpjSoComCatorzeDigitos() {
        assertEquals("12345678000195", Texto.cnpj("12.345.678/0001-95 "));
        assertNull(Texto.cnpj("123.456.789-09"));
    }

    @Test
    void converteDataBrasileira() {
        assertEquals("2026-01-20", Texto.dataBr("20/01/2026"));
        assertNull(Texto.dataBr(""));
    }
}
