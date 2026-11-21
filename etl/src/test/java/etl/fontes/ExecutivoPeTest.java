package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import etl.fontes.ExecutivoSp.Ato;

/** Decretos do Executivo de Pernambuco na lista da Alepe (ADR-0089). Fixture minimizada da página de 2026-11-21. */
class ExecutivoPeTest {

    @Test
    void decretoComNumeroDataDePublicacaoEEmenta() {
        String html = """
                <tr><td class="td-nome-norma col-md-3">
                    <span class="nome-norma"><a href='texto.aspx?id=87316&tipo='''>Decreto do Executivo n° 60.155</a></span>
                    <span class="publicacao">Publicada no DOE 31/12/2025</span>
                </td><td class="textos"><ul><li><a href='texto.aspx?id=87316&tipo=TEXTOORIGINAL'></a></li></ul></td>
                <td class="col-md-7"><div class="fLeft">
                    Abre ao Orçamento Fiscal do Estado crédito suplementar.
                </div></td></tr>
                """;

        List<Ato> atos = ExecutivoPe.decretos(html, null);

        assertEquals(1, atos.size());
        assertEquals("pe-dec-60155", atos.getFirst().idFonte());
        assertEquals("2025-12-31", atos.getFirst().data());
        assertEquals("Decreto nº 60.155", atos.getFirst().nome());
        assertEquals("Abre ao Orçamento Fiscal do Estado crédito suplementar.", atos.getFirst().ementa());
    }
}
