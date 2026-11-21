package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pesquisa de proposições da ALMT (ADR-0085). Fixture minimizada da página de 2026-11-21. */
class AlmtProposicoesTest {

    private static final String HTML = """
            <div>
                <h3 class="fs-16">ESTABELECE CRITÉRIOS PARA ENTREGA DOS RECURSOS AOS MUNICÍPIOS.</h3>
                <div class="text-muted mb-2">
                    Projeto de lei nº 2/2025 Dep. Eduardo Botelho
                    -
                    Protocolo nº 3/2025 -  Processo nº 3/2025
                </div>
                <div id="collapse-group-154139"><a href="/storage/webdisco/cp/20250106104923121100.pdf">PDF</a>
                <a href="/proposicao/cpdoc/154139/visualizar">Ver</a></div>
                <h3 class="fs-16">ALTERA A LEI Nº 11.488.</h3>
                <div class="text-muted mb-2">Projeto de lei nº 1/2025 Mesa Diretora - Protocolo nº 2/2025 - Processo nº 2/2025</div>
                <div id="collapse-group-154212"></div>
            </div>
            """;

    @Test
    void itemComNumeroAutorEPdf() {
        List<AlmtProposicoes.Item> itens = AlmtProposicoes.itens(HTML);

        assertEquals(2, itens.size());
        AlmtProposicoes.Item i = itens.getFirst();
        assertEquals("154139", i.documento());
        assertEquals("ESTABELECE CRITÉRIOS PARA ENTREGA DOS RECURSOS AOS MUNICÍPIOS.", i.ementa());
        assertEquals(2, i.numero());
        assertEquals(2025, i.ano());
        assertEquals("Dep. Eduardo Botelho", i.autores());
        assertEquals("/storage/webdisco/cp/20250106104923121100.pdf", i.pdf());
        assertEquals("Mesa Diretora", itens.get(1).autores());
    }

    @Test
    void autorComTratamentoDeDeputado() {
        AutoriaPorNome autoria = new AutoriaPorNome(java.util.Map.of("1", "Eduardo Botelho", "2", "Dr. Eugênio"));

        assertEquals("1", autoria.id("Dep. Eduardo Botelho"));
        assertEquals("2", autoria.id("Dep. Dr. Eugênio"));
    }
}
