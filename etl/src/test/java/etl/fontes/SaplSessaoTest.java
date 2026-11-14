package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.Evento;
import etl.Registros.Participacao;
import etl.Registros.Voto;
import tools.jackson.databind.JsonNode;

class SaplSessaoTest {

    @Test
    void derivarSemIpRemoveCamposSensiveisEConservaVoto(@TempDir Path tempDir) throws IOException {
        Path origem = tempDir.resolve("bruto.json");
        Path destino = tempDir.resolve("limpo.json");
        Files.writeString(origem, """
                {
                    "pagination": {"links": {"next": null}},
                    "results": [
                        {
                            "id": 10,
                            "parlamentar": 89,
                            "votacao": 500,
                            "voto": "Sim",
                            "data_hora": "2025-02-05T11:30:11.061399-03:00",
                            "ip": "192.0.2.55",
                            "user": 4,
                            "__str__": "Ordem: 1 - PL 1/2025 - Votação: Aprovado - Matéria: PL 1/2025 - Parlamentar: Fulano"
                        }
                    ]
                }
                """);

        SaplSessao.derivarSemIp(origem, destino);
        String texto = Files.readString(destino);
        assertFalse(texto.contains("192.0.2.55"));
        assertFalse(texto.contains("\"ip\""));
        assertFalse(texto.contains("\"user\""));

        JsonNode limpo = Json.ler(destino);
        assertEquals(1, limpo.path("results").size());
        assertEquals("Sim", limpo.path("results").get(0).path("voto").stringValue());
    }

    @Test
    void instanteSemFusoNormalizaParaSchema() {
        assertEquals("2025-02-05T11:30:11", SaplSessao.instanteSemFuso("2025-02-05T11:30:11.061399-03:00"));
        assertEquals("2024-03-10T09:15:00", SaplSessao.instanteSemFuso("2024-03-10T09:15:00Z"));
        assertEquals("2024-03-10", SaplSessao.instanteSemFuso("2024-03-10"));
        assertNull(SaplSessao.instanteSemFuso(null));
    }

    @Test
    void parseResumoVotoTrataFormatosComESemMateriaExplicita() {
        String[] comMateria = SaplSessao.parseResumoVoto(
                "Ordem: 1 - PL 123/2025 em 12ª Sessão Ordinária - Votação: Aprovado - Matéria: PL 123/2025 - Parlamentar: Fulano");
        assertEquals("PL 123/2025", comMateria[0]);
        assertEquals("Resultado: Aprovado", comMateria[1]);

        String[] semMateria = SaplSessao.parseResumoVoto(
                "Votação: Ordem do Dia: 2 - PLO 45/2024 - Projeto de Lei em 3ª Sessão Ordinária - Votação: Aprovado por maioria");
        assertEquals("PLO 45/2024 - Projeto de Lei", semMateria[0]);
        assertEquals("Resultado: Aprovado por maioria", semMateria[1]);
    }

    @Test
    void normalizarTipoSessaoPadronizaNomes() {
        assertEquals("Sessão Ordinária", SaplSessao.normalizarTipoSessao("Ordinária", null));
        assertEquals("Sessão Solene", SaplSessao.normalizarTipoSessao("Sessão Solene", null));
        assertEquals("Sessão Extraordinária",
                SaplSessao.normalizarTipoSessao(null, "14ª Sessão Extraordinária da 3ª Sessão Legislativa"));
        assertEquals("Sessão Plenária", SaplSessao.normalizarTipoSessao(null, null));
    }

    @Test
    void normalizeDeduplicaVotosPresencasECalculaParticipacao(@TempDir Path tempDir) throws IOException {
        Contexto ctx = new Contexto(tempDir, tempDir.resolve("data"), null, null,
                Instant.parse("2026-11-14T12:00:00Z"), null);
        Path rawAlpb = ctx.raw().resolve("alpb");
        Files.createDirectories(rawAlpb.resolve("filiacoes"));
        Files.createDirectories(rawAlpb.resolve("sessoes"));
        Files.createDirectories(rawAlpb.resolve("presencas"));
        Files.createDirectories(rawAlpb.resolve("votos"));

        Files.writeString(rawAlpb.resolve("filiacoes/89.json"), """
                {
                    "results": [
                        {
                            "__str__": "Adriano - MDB - Movimento Democrático Brasileiro",
                            "data_filiacao": "2022-01-01",
                            "data_desfiliacao": "2024-06-30"
                        },
                        {
                            "__str__": "Adriano - REPUBLICANOS - Republicanos",
                            "data_filiacao": "2024-07-01",
                            "data_desfiliacao": null
                        }
                    ]
                }
                """);
        gravarMetadado(rawAlpb, "filiacoes/89.json", "https://sapl3.al.pb.leg.br/api/parlamentares/filiacao/?parlamentar=89");

        Files.writeString(rawAlpb.resolve("sessoes/tipos-p1.json"), """
                {
                    "pagination": {"links": {"next": null}},
                    "results": [
                        {"id": 1, "nome": "Ordinária"}
                    ]
                }
                """);
        gravarMetadado(rawAlpb, "sessoes/tipos-p1.json", "https://sapl3.al.pb.leg.br/api/sessao/tiposessaoplenaria/");

        Files.writeString(rawAlpb.resolve("sessoes/2025-p1.json"), """
                {
                    "pagination": {"links": {"next": null}},
                    "results": [
                        {
                            "id": 300,
                            "tipo": 1,
                            "numero": 10,
                            "data_inicio": "2025-02-05",
                            "hora_inicio": "09:30:00",
                            "__str__": "10ª Sessão Ordinária da 3ª Sessão Legislativa"
                        },
                        {
                            "id": 100,
                            "tipo": 1,
                            "numero": 1,
                            "data_inicio": "2023-01-15",
                            "hora_inicio": "09:00:00",
                            "__str__": "Sessão anterior à legislatura"
                        }
                    ]
                }
                """);
        gravarMetadado(rawAlpb, "sessoes/2025-p1.json",
                "https://sapl3.al.pb.leg.br/api/sessao/sessaoplenaria/?data_inicio__year=2025");

        Files.writeString(rawAlpb.resolve("presencas/p1.json"), """
                {
                    "pagination": {"links": {"next": null}},
                    "results": [
                        {"id": 1, "sessao_plenaria": 300, "parlamentar": 89},
                        {"id": 2, "sessao_plenaria": 300, "parlamentar": 89},
                        {"id": 3, "sessao_plenaria": 100, "parlamentar": 89}
                    ]
                }
                """);
        gravarMetadado(rawAlpb, "presencas/p1.json",
                "https://sapl3.al.pb.leg.br/api/sessao/sessaoplenariapresenca/?sessao_plenaria__gte=300");

        Files.writeString(rawAlpb.resolve("votos/2025-p1.json"), """
                {
                    "pagination": {"links": {"next": "https://sapl3.al.pb.leg.br/api/sessao/votoparlamentar/?page=2"}},
                    "results": [
                        {
                            "id": 1,
                            "parlamentar": 89,
                            "votacao": 500,
                            "voto": "Sim",
                            "data_hora": "2025-02-05T11:30:11.061399-03:00",
                            "__str__": "Ordem: 1 - PL 1/2025 em 10ª Sessão Ordinária - Votação: Aprovado - Matéria: PL 1/2025"
                        },
                        {
                            "id": 2,
                            "parlamentar": 89,
                            "votacao": 501,
                            "voto": "Não Votou",
                            "data_hora": "2025-02-05T11:45:00-03:00",
                            "__str__": "Ordem: 2 - PL 2/2025 em 10ª Sessão Ordinária - Votação: Aprovado - Matéria: PL 2/2025"
                        }
                    ]
                }
                """);
        gravarMetadado(rawAlpb, "votos/2025-p1.json",
                "https://sapl3.al.pb.leg.br/api/sessao/votoparlamentar/?data_hora__year=2025");

        Files.writeString(rawAlpb.resolve("votos/2025-p2.json"), """
                {
                    "pagination": {"links": {"next": null}},
                    "results": [
                        {
                            "id": 1,
                            "parlamentar": 89,
                            "votacao": 500,
                            "voto": "Sim",
                            "data_hora": "2025-02-05T11:30:11.061399-03:00",
                            "__str__": "Ordem: 1 - PL 1/2025 em 10ª Sessão Ordinária - Votação: Aprovado - Matéria: PL 1/2025"
                        }
                    ]
                }
                """);
        gravarMetadado(rawAlpb, "votos/2025-p2.json",
                "https://sapl3.al.pb.leg.br/api/sessao/votoparlamentar/?data_hora__year=2025&page=2");

        SaplSessao.DadosNormalizados dados = SaplSessao.normalize(
                ctx, "alpb", "https://sapl3.al.pb.leg.br/api",
                "alpb-votacoes", "alpb-presenca",
                LocalDate.of(2023, 2, 1), List.of(2025),
                Set.of(89), pid -> "REPUBLICANOS");

        List<Voto> votos = dados.votos(89);
        assertEquals(2, votos.size(), "Deve deduplicar votacao_id=500 entre páginas");
        assertEquals("501", votos.get(0).votacaoId());
        assertEquals("2025-02-05T11:45:00", votos.get(0).data());
        assertEquals("REPUBLICANOS", votos.get(0).partidoNaData());
        assertEquals("alpb-votacoes", votos.get(0).prov().fonteId());

        List<Participacao> participacoes = dados.participacoes(89);
        assertEquals(1, participacoes.size());
        Participacao p = participacoes.get(0);
        assertEquals("2025", p.periodo());
        assertEquals(2, p.votacoesNominais());
        assertEquals(1, p.votosRegistrados());
        assertNull(p.semRegistro());
        assertEquals(Map.of("Não Votou", 1, "Sim", 1), p.porTipo());

        List<Evento> eventos = dados.eventos(89);
        assertEquals(1, eventos.size(), "Deve deduplicar presença na sessão 300 e ignorar sessão 100");
        Evento ev = eventos.get(0);
        assertEquals("sessao-300", ev.idFonte());
        assertEquals("2025-02-05T09:30", ev.data());
        assertEquals("Sessão Ordinária", ev.tipo());
        assertEquals("registrada", ev.presenca());
        assertNotNull(ev.prov());
        assertEquals("alpb-presenca", ev.prov().fonteId());
        assertEquals("REPUBLICANOS", dados.partido(89));
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alpb/" + arquivo, "abc", 100L,
                Instant.parse("2026-11-14T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
