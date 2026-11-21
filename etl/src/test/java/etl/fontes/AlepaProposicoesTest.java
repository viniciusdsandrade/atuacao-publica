package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Painel de proposições da ALEPA (ADR-0087). Fixture minimizada da resposta de 2026-11-21. */
class AlepaProposicoesTest {

    private static final String HTML = """
            /*DX*/({'id':0,'result':{'stateObject':{'keys':['15501','15500'],'callbackState':'dXfe28lc+abc/=='},'html':'
            Página 2 de 89 (882 itens)
            <div onclick='onCardClick("/Legislativo/DetalhesProposicao?IdProposicao=15501&tipo=PROJETO%20DE%20LEI%20ORDIN%C3%81RIA&situacao=0&decisao=0&veto=-1")' class='card-proposicao'><h3 style="font-weight: 700; font-size: 1.25rem">DEP. FÁBIO FREITAS</h3><span>PROJETO DE LEI ORDINÁRIA Nº 873/2025, DE 16/12/2025</span><p>Declara e reconhece como de Utilidade Pública a ASPEGRAT.(Parecer FAVORÁVEL da Comissão de JUSTIÇA)</p></div>
            <div onclick='onCardClick("/Legislativo/DetalhesProposicao?IdProposicao=15511&tipo=PROJETO%20DE%20LEI%20ORDIN%C3%81RIA&situacao=0&decisao=0&veto=-1")' class='card-proposicao'><h3 style="font-weight: 700">PODER EXECUTIVO</h3><span>PROJETO DE LEI ORDINÁRIA Nº 882/2025, DE 18/12/2025</span><p>Cria cargos.</p></div>
            '}})
            """;

    @Test
    void cartaoComAutorNumeroDataEDetalhe() {
        List<AlepaProposicoes.Cartao> cartoes = AlepaProposicoes.cartoes(HTML);

        assertEquals(2, cartoes.size());
        AlepaProposicoes.Cartao c = cartoes.getFirst();
        assertEquals("15501", c.id());
        assertEquals("DEP. FÁBIO FREITAS", c.autores());
        assertEquals(873, c.numero());
        assertEquals(2025, c.ano());
        assertEquals("2025-12-16", c.data());
        assertEquals("/Legislativo/DetalhesProposicao?IdProposicao=15501&tipo=PROJETO%20DE%20LEI%20ORDIN%C3%81RIA&situacao=0&decisao=0&veto=-1",
                c.detalhe());
        assertEquals(89, AlepaProposicoes.paginas(HTML));
        assertEquals(88, AlepaProposicoes.paginas("<b class=\"dxp-lead dxp-summary\">P&#225;gina 1 de 88 (880 itens)</b>"));
    }

    @Test
    void estadoDaListaEmJson() {
        assertEquals("{\"keys\":[\"15501\",\"15500\"],\"callbackState\":\"dXfe28lc+abc/==\"}", AlepaProposicoes.estado(HTML));
        assertNull(AlepaProposicoes.estado("sem estado"));
    }

    @Test
    void argumentoDaPaginaComOsTamanhosDoProtocolo() {
        assertEquals("c0:GB|20;12|PAGERONCLICK3|PN1;", AlepaProposicoes.argumentoDaPagina(2));
        assertEquals("c0:GB|21;12|PAGERONCLICK4|PN10;", AlepaProposicoes.argumentoDaPagina(11));
        assertEquals("c0:GB|22;12|PAGERONCLICK5|PN100;", AlepaProposicoes.argumentoDaPagina(101));
    }

    @Test
    void grafiaDoPerfilComoNosVotos() {
        assertEquals("DEPUTADO BORDALO", AlepaProposicoes.grafiaDoPerfil("DEP. CARLOS BORDALO"));
        assertEquals("DEP. LÍVIA DUARTE", AlepaProposicoes.grafiaDoPerfil("DEP. LÍVIA DUARTE"));
    }
}
