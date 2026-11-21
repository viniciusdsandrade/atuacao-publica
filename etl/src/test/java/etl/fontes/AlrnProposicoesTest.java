package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import etl.Json;
import etl.Registros.Proposicao;
import etl.Registros.Prov;

/** Proposições da ALRN pela API do e-Legis (ADR-0080). Fixture minimizada da resposta de 2026-11-21. */
class AlrnProposicoesTest {

    private static final Prov PROV = new Prov(AlrnProposicoes.FONTE, AlrnProposicoes.API, Instant.parse("2026-11-21T12:00:00Z"));

    @Test
    void projetoDeLeiPeloNumeroDaPropositura() {
        Proposicao p = AlrnProposicoes.proposicao(Json.FONTE.readTree("""
                {"id": 64440, "numero": 3476, "ano": 2026, "ementa": "Institui a Política Estadual de Proteção. ",
                 "dataEntrada": "2026-11-19T09:52:00.773Z",
                 "propositura": {"tipo": {"id": 1, "sigla": "PL", "descricao": "PROJETO DE LEI"}, "numero": 458, "ano": 2026},
                 "iniciativa": {"id": 10, "tipo": "PARLAMENTAR", "parlamentar": {"id": 16, "nomeParlamentar": "DEPUTADO GUSTAVO CARVALHO"}},
                 "documento": {"id": 307713}}
                """), PROV);

        assertEquals("64440", p.idFonte());
        assertEquals("PL 458/2026", p.identificacao());
        assertEquals("Institui a Política Estadual de Proteção.", p.ementa());
        assertEquals("2026-11-19", p.dataApresentacao());
        assertEquals(AlrnProposicoes.API + "documento/307713/conteudo", p.urlTexto());
        assertEquals(Boolean.TRUE, p.proponente());
    }

    @Test
    void requerimentoFicaDeFora() {
        assertNull(AlrnProposicoes.proposicao(Json.FONTE.readTree("""
                {"id": 1, "propositura": {"tipo": {"sigla": "REQ"}, "numero": 1, "ano": 2026}}
                """), PROV));
    }
}
