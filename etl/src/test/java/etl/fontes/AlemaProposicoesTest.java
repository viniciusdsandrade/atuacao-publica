package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etl.Json;
import etl.Registros.Proposicao;
import etl.Registros.Prov;

/** Proposições da ALEMA pela API pública (ADR-0079). Fixture minimizada da resposta de 2026-11-21. */
class AlemaProposicoesTest {

    private static final Prov PROV = new Prov(AlemaProposicoes.FONTE, AlemaProposicoes.API, Instant.parse("2026-11-21T12:00:00Z"));

    @Test
    void projetoDeLeiComSituacaoDataETexto() {
        Proposicao p = AlemaProposicoes.proposicao(Json.FONTE.readTree("""
                {"id": 38270, "matterTypeDescription": "Projeto de Lei Ordinária", "matterTypeSigla": "PLO", "number": "12",
                 "year": "2023", "ement": "DISPÕE SOBRE A SEMANA ESTADUAL.\\n", "authorNames": "Carlos Lula",
                 "situation": "Proposição transformada em lei", "presentationDate": "2023-02-06",
                 "files": [{"id": 32160, "name": "38270_texto_integral.pdf"}]}
                """), PROV);

        assertEquals("38270", p.idFonte());
        assertEquals("PLO 12/2023", p.identificacao());
        assertEquals("DISPÕE SOBRE A SEMANA ESTADUAL.", p.ementa());
        assertEquals("Proposição transformada em lei", p.situacao());
        assertEquals("2023-02-06", p.dataApresentacao());
        assertEquals("https://alemalegis.al.ma.leg.br/api/v1/public/legislative-matter/download/32160", p.urlTexto());
    }

    @Test
    void indicacaoERequerimentoFicamDeFora() {
        assertNull(AlemaProposicoes.proposicao(Json.FONTE.readTree("""
                {"id": 1, "matterTypeSigla": "IND", "number": "1", "year": "2023"}
                """), PROV));
    }

    @Test
    void nomeDeComissaoComVirgulaNaoSeDivide() {
        AutoriaPorNome autoria = new AutoriaPorNome(Map.of("a", "Carlos Lula", "b", "Iracema Vale"));

        assertEquals(List.of("Comissão de Constituição, Justiça e Cidadania"),
                AlemaProposicoes.autores("Comissão de Constituição, Justiça e Cidadania", autoria));
        assertEquals(List.of("Carlos Lula", "Iracema Vale"), AlemaProposicoes.autores("Carlos Lula, Iracema Vale", autoria));
        assertEquals(List.of("Carlos Lula"), AlemaProposicoes.autores("Carlos Lula ", autoria));
        // Coautor que não tem perfil não impede a divisão.
        assertEquals(List.of("Carlos Lula", "Rildo Amaral"), AlemaProposicoes.autores("Carlos Lula, Rildo Amaral", autoria));
    }
}
