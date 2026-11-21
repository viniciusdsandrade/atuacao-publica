package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pesquisa de proposições da ALERJ (ADR-0078). Fixture minimizada da resposta de 2026-11-21. */
class AlerjProposicoesTest {

    private static final String HTML = """
            <table><tr><th>Código</th></tr>
            <tr valign="top"><td align="center"><img src="/icons/vwicnsr7.gif" alt="0%"></td><td><font size="1" face="Verdana"><a href="/scpro2327.nsf/18c1dd68f96be3e7832566ec0018d833/9624294de33c260403258a85004dd9c3?OpenDocument">20230302846</a></font></td><td><img src="/icons/vwicn112.gif"></td><td><font size="1" face="Verdana">AUTORIZA O PODER EXECUTIVO A EMPREGAR OS EGRESSOS DAS FORÇAS ARMADAS =&gt;20230302846 =&gt; {Constituição e Justiça Educação        }</font></td><td align="center"><font size="1" face="Verdana">12/15/2023</font></td><td><font size="1" face="Verdana">CLAUDIO CAIADO,RODRIGO  AMORIM</font></td></tr>
            <tr valign="top"><td></td><td><font><a href="/scpro2327.nsf/18c1dd68f96be3e7832566ec0018d833/543462cc2b1cf9ce03258a85004d17cc?OpenDocument">20230302554</a></font></td><td></td><td><font><br>
            ALTERA O ART 1º DA LEI 2562 =&gt;20230302554 =&gt; {Constituição e Justiça }</font></td><td><font>11/16/2023</font></td><td><font>LUIZ PAULO</font></td></tr>
            </table>
            """;

    @Test
    void linhaTemCodigoEmentaDataEAutoresNaOrdem() {
        List<AlerjProposicoes.Linha> linhas = AlerjProposicoes.linhas(HTML);

        assertEquals(2, linhas.size());
        AlerjProposicoes.Linha l = linhas.getFirst();
        assertEquals("20230302846", l.codigo());
        assertEquals("AUTORIZA O PODER EXECUTIVO A EMPREGAR OS EGRESSOS DAS FORÇAS ARMADAS", l.ementa());
        assertEquals("2023-12-15", l.data());
        assertEquals(List.of("CLAUDIO CAIADO", "RODRIGO AMORIM"), l.autores());
        assertEquals("9624294de33c260403258a85004dd9c3", l.documento());
        assertEquals("ALTERA O ART 1º DA LEI 2562", linhas.get(1).ementa());
    }

    @Test
    void nomeDoAutorEDoDeputadoTemAMesmaChave() {
        assertEquals(AlerjProposicoes.chave("Rodrigo Amorim"), AlerjProposicoes.chave("RODRIGO  AMORIM"));
        assertEquals(AlerjProposicoes.chave("Cláudio Caiado"), AlerjProposicoes.chave("CLAUDIO CAIADO"));
        assertEquals(AlerjProposicoes.chave("Dr. Deodalto"), AlerjProposicoes.chave("DR DEODALTO"));
        assertNull(AlerjProposicoes.chave(null));
    }

    @Test
    void consultaPeloPrefixoDoCodigo() {
        assertEquals("https://alerjln1.alerj.rj.gov.br/scpro2327.nsf/18c1dd68f96be3e7832566ec0018d833?SearchView&Query=FIELD%20prop_cod_prop"
                + "%20CONTAINS%20202303*&SearchMax=0&SearchOrder=4", AlerjProposicoes.url(AlerjProposicoes.TIPOS.get(2), "202303"));
    }
}
