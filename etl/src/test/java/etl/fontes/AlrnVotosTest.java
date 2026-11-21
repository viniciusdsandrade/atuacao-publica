package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.Voto;

/** Votos da ALRN pelo detalhamento da votação (ADR-0081). Fixture minimizada das respostas de 2026-11-21. */
class AlrnVotosTest {

    @Test
    void votoPeloCodigoDoPortalEParticipacaoSoNoPlenario(@TempDir Path tmp) throws Exception {
        Contexto ctx = new Contexto(tmp, tmp.resolve("data"), null, null, Instant.parse("2026-11-21T12:00:00Z"), null);
        grava(ctx, "votacoes/lista-0.json", AlrnVotos.url(0), """
                {"total": 4, "dados": [
                  {"id": 1, "ementa": "Reconhece a festa.", "tipoVotacao": "A",
                   "propositura": {"tipo": {"sigla": "PL"}, "numero": 289, "ano": 2026},
                   "reuniaoProcesso": [{"id": 10, "dataSituacaoAbertura": "2026-11-18T10:52:49.000Z", "reuniao": {"id": 100}}]},
                  {"id": 2, "ementa": "Institui o dia.", "tipoVotacao": "O",
                   "propositura": {"tipo": {"sigla": "PL"}, "numero": 300, "ano": 2026},
                   "reuniaoProcesso": [{"id": 20, "dataSituacaoAbertura": "2026-11-19T10:00:00.000Z", "reuniao": {"id": 200}}]},
                  {"id": 3, "tipoVotacao": "S", "reuniaoProcesso": [{"id": 30, "reuniao": {"id": 300}}]},
                  {"id": 4, "tipoVotacao": null, "reuniaoProcesso": []}
                ]}
                """);
        grava(ctx, "votacoes/detalhe/1-100.json", "x", """
                {"reuniao": {"tipoReuniao": {"nome": "Reunião de Comissão Ordinária", "sessao": false},
                  "reuniaoComissao": [{"comissao": {"nome": "Comissão de Educação"}}]},
                 "votacao": {"dados": [{"voto": 1, "parlamentar": {"nomeParlamentar": "DEPUTADO FRANCISCO DO PT", "partido": {"nome": "PT"}}}]}}
                """);
        grava(ctx, "votacoes/detalhe/2-200.json", "x", """
                {"reuniao": {"tipoReuniao": {"nome": "Sessão Ordinária", "sessao": true}},
                 "votacao": {"dados": [{"voto": 0, "parlamentar": {"nomeParlamentar": "DEPUTADA ISOLDA DANTAS", "partido": {"nome": "PT"}}},
                                       {"voto": 2, "parlamentar": {"nomeParlamentar": "DEPUTADO SEM PERFIL"}}]}}
                """);

        AlrnVotos.Resultado r = AlrnVotos.porDeputado(ctx, new AutoriaPorNome(Map.of("12", "Francisco do PT", "18", "Isolda Dantas")));

        // A votação secreta não entra.
        assertEquals(2, AlrnVotos.votacoes(ctx).size());
        Voto francisco = r.votos().get("12").getFirst();
        assertEquals("Sim", francisco.voto());
        assertEquals("Comissão de Educação", francisco.orgao());
        assertEquals("PL 289/2026", francisco.proposicao());
        assertEquals("2026-11-18", francisco.data());
        assertEquals("Não", r.votos().get("18").getFirst().voto());
        // Participação conta só o plenário: Isolda votou na única votação em plenário; Francisco, em nenhuma.
        assertEquals(1, r.participacao().get("18").getFirst().votosRegistrados());
        assertEquals(1, r.participacao().get("12").getFirst().votacoesNominais());
        assertEquals(0, r.participacao().get("12").getFirst().votosRegistrados());
        assertTrue(r.votos().containsKey("12"));
        assertEquals("votou", AlrnVotos.valor(Json.FONTE.readTree("\"votou\"")));
        assertEquals("Abstenção", AlrnVotos.valor(Json.FONTE.readTree("2")));
    }

    private static void grava(Contexto ctx, String caminho, String url, String json) throws Exception {
        Path arquivo = ctx.raw().resolve("alrn").resolve(caminho);
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, json);
        Json.gravar(arquivo.resolveSibling(arquivo.getFileName() + ".meta.json"),
                new Coleta.Arquivo(url, "raw/alrn/" + caminho, "abc", json.length(), Instant.parse("2026-11-21T12:00:00Z")), true);
    }
}
