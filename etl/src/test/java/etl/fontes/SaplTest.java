package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import etl.Coleta.Politica;
import etl.Json;
import tools.jackson.databind.JsonNode;

class SaplTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 31);
    private static final Sapl.Legislatura LEG19 = new Sapl.Legislatura(19, LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31));

    @Test
    void parseDeLegislaturasIdentificaVigente() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"numero": 18, "data_inicio": "2021-01-01", "data_fim": "2024-12-31"},
                    {"numero": 19, "data_inicio": "2025-01-01", "data_fim": "2028-12-31"}
                  ]
                }
                """);
        List<Sapl.Legislatura> legs = Sapl.legislaturas(json);
        assertEquals(2, legs.size());
        assertEquals(19, Sapl.legislaturaVigente(legs, HOJE).id());
        assertEquals(18, Sapl.legislaturaVigente(legs, LocalDate.of(2022, 6, 1)).id());
        // Desde 2022 (ADR-0009): a 18ª entra pela parte a partir do início da cobertura.
        List<Sapl.Legislatura> cobertas = Sapl.legislaturasCobertas(legs, HOJE);
        assertEquals(List.of(18, 19), cobertas.stream().map(Sapl.Legislatura::id).toList());
        assertEquals(LocalDate.of(2022, 1, 1), Sapl.inicioDaCobertura(cobertas));
        assertEquals(List.of(2022, 2023, 2024, 2025, 2026), Sapl.anos(cobertas, HOJE));
    }

    @Test
    void extraiIdNumericoDeUrlOuNumero() {
        JsonNode node = Json.FONTE.readTree("""
                {"link": "https://sapl.campinas.sp.leg.br/api/parlamentares/parlamentar/101/", "direto": "202"}
                """);
        assertEquals("101", Sapl.extrairId(node, "link"));
        assertEquals("202", Sapl.extrairId(node, "direto"));
    }

    @Test
    void mandatosDiferenciamEmExercicioEQuemSaiu() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {
                      "parlamentar": "https://sapl.../parlamentar/10/",
                      "data_inicio_mandato": "2025-01-01",
                      "data_fim_mandato": "2028-12-31",
                      "titular": true,
                      "tipo_afastamento": null
                    },
                    {
                      "parlamentar": "20",
                      "data_inicio_mandato": "2025-01-01",
                      "data_fim_mandato": "2026-03-15",
                      "titular": false,
                      "tipo_afastamento": "Licença para assumir secretaria"
                    }
                  ]
                }
                """);
        Map<String, List<Sapl.MandatoSapl>> map = Sapl.mandatos(json, LEG19, HOJE);
        assertEquals(2, map.size());
        assertEquals(19, map.get("10").getFirst().legislatura());

        Sapl.MandatoSapl titular = map.get("10").getFirst();
        assertTrue(titular.emExercicio());
        assertEquals("Exercício", titular.situacao());
        assertTrue(titular.titular());

        Sapl.MandatoSapl suplente = map.get("20").getFirst();
        assertFalse(suplente.emExercicio());
        assertEquals("Fora de exercício (Licença para assumir secretaria)", suplente.situacao());
        assertFalse(suplente.titular());
    }

    @Test
    void afastamentoSaiPelaDescricaoDoTipoENaoPeloIdentificador() {
        JsonNode tipos = Json.FONTE.readTree("""
                {"results": [{"id": 3, "descricao": "Cassação", "indicador": "F"}, {"id": 4, "descricao": "Renúncia", "indicador": "F"}]}
                """);
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"parlamentar": 30, "data_inicio_mandato": "2025-01-01", "data_fim_mandato": "2026-09-09", "titular": true, "tipo_afastamento": 3},
                    {"parlamentar": 31, "data_inicio_mandato": "2025-01-01", "data_fim_mandato": "2026-02-01", "titular": true, "tipo_afastamento": 99}
                  ]
                }
                """);
        Map<String, List<Sapl.MandatoSapl>> map = Sapl.mandatos(json, LEG19, HOJE, Sapl.tiposDeAfastamento(tipos));
        assertEquals("Fora de exercício (Cassação)", map.get("30").getFirst().situacao());
        // Tipo fora da lista: o identificador não vai para a tela.
        assertEquals("Fora de exercício", map.get("31").getFirst().situacao());
    }

    @Test
    void votoGanhaGrafiaUnicaEValorSemLetraViraAusencia() {
        assertEquals("Não", Sapl.voto("Nao"));
        assertEquals("Não", Sapl.voto(" NÃO "));
        assertEquals("Sim", Sapl.voto("SIM"));
        assertEquals("Abstenção", Sapl.voto("Abstencao"));
        // O que a fonte grafa de outro jeito fica como ela grafa.
        assertEquals("Ausente", Sapl.voto("Ausente"));
        assertEquals("Não Votou", Sapl.voto("Não Votou"));
        // Marcas sem letra que a câmara deixou em item não votado.
        assertEquals(null, Sapl.voto("["));
        assertEquals(null, Sapl.voto("]"));
        assertEquals(null, Sapl.voto("-1"));
        assertEquals(null, Sapl.voto(""));
        assertTrue(Sapl.registrado(Sapl.voto("Nao")));
    }

    @Test
    void itemNaoVotadoNaoEVotacaoETipoDeSessaoNaoRepetePalavra() {
        assertTrue(Sapl.naoVotado("Não votado"));
        assertFalse(Sapl.naoVotado("Aprovado"));
        assertFalse(Sapl.naoVotado(null));
        assertEquals("Sessão Ordinária", Sapl.tipoDeSessao("Ordinária"));
        assertEquals("Sessão de Julgamento", Sapl.tipoDeSessao("Sessão de Julgamento"));
    }

    @Test
    void emExercicioEQuemACamaraListaComoAtivo() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"parlamentar": 40, "data_inicio_mandato": "2025-01-01", "data_fim_mandato": "2028-12-31", "titular": true, "tipo_afastamento": null},
                    {"parlamentar": 41, "data_inicio_mandato": "2025-05-07", "data_fim_mandato": "2028-12-31", "titular": false, "tipo_afastamento": null}
                  ]
                }
                """);
        Map<String, List<Sapl.MandatoSapl>> map = Sapl.mandatos(json, LEG19, HOJE);
        // Titular licenciado: mandato aberto, sem afastamento registrado, e fora da lista de ativos. O suplente ocupa a cadeira.
        Set<String> ativos = Set.of("41");
        assertFalse(Sapl.emExercicio("40", map.get("40"), ativos));
        assertTrue(Sapl.emExercicio("41", map.get("41"), ativos));
        assertEquals("Fora de exercício", Sapl.situacaoDoMandato(map.get("40").getFirst(), false));
        assertEquals("Exercício", Sapl.situacaoDoMandato(map.get("41").getFirst(), true));
        // Sem a lista de ativos, vale o mandato vigente sem afastamento.
        assertTrue(Sapl.emExercicio("40", map.get("40"), Set.of()));
    }

    @Test
    void registroDeVotacaoTrazResultadoEMateria() {
        JsonNode json = Json.FONTE.readTree("""
                {"id": 27012, "__str__": "Ordem: 1 - Emenda fls. 55 a 57 - Votação: Aprovado - Matéria: Emenda fls. 55 a 57",
                 "ordem": 15555, "expediente": null}
                """);
        Sapl.RegistroVotacao reg = Sapl.registro(json);
        assertEquals(27012, reg.id());
        assertEquals(15555, reg.ordem());
        assertEquals(null, reg.expediente());
        assertEquals("Aprovado", reg.resultado());
        assertEquals("Emenda fls. 55 a 57", reg.materia());
    }

    @Test
    void itemDaOrdemDoDiaDaDataEMateriaAoVoto() {
        JsonNode json = Json.FONTE.readTree("""
                {"id": 15555, "__str__": "1 - Projeto de Lei Ordinária nº 280 de 2009", "data_ordem": "2026-06-29"}
                """);
        Sapl.Item item = Sapl.item(json, LocalDate.of(2026, 6, 29));
        assertEquals(LocalDate.of(2026, 6, 29), item.data());
        // Sessão de mais de um dia: o item votado no segundo dia fica com a data dele.
        assertEquals(LocalDate.of(2026, 6, 29), Sapl.item(json, LocalDate.of(2026, 6, 28)).data());
        // Item com data anterior à sessão em que foi votado: vale a data da sessão.
        assertEquals(LocalDate.of(2026, 6, 30), Sapl.item(json, LocalDate.of(2026, 6, 30)).data());
        assertEquals("Projeto de Lei Ordinária nº 280 de 2009", item.materia());
        JsonNode semData = Json.FONTE.readTree("{\"id\": 1, \"__str__\": \"2 - Moção nº 1 de 2026\", \"data_ordem\": null}");
        assertEquals(LocalDate.of(2026, 6, 30), Sapl.item(semData, LocalDate.of(2026, 6, 30)).data());
    }

    @Test
    void paginasDaMesmaListaRenovamJuntas() {
        // Lista que desloca: com um dia por página, o item que muda de página entre duas renovações some ou repete.
        Instant captura = Instant.parse("2026-10-01T06:00:00Z");
        String lista = "materias/1-2026";
        List<Instant> mesmoDia = List.of(captura, captura.plusSeconds(90), captura.plusSeconds(200));
        int renovacoes = 0;
        for (int dia = 1; dia <= 7; dia++) {
            Politica politica = Sapl.politicaDaLista(mesmoDia, lista, Politica.SEMANAL, captura.plus(Duration.ofDays(dia)));
            assertTrue(politica == Politica.SEMPRE || politica == Politica.SE_AUSENTE, "decisão única para a lista inteira");
            if (politica == Politica.SEMPRE) {
                renovacoes++;
            }
        }
        assertEquals(1, renovacoes, "a lista renova inteira num dia da semana");
        // Cópias de coletas diferentes: a lista renova já, mesmo de período fechado.
        List<Instant> diasDiferentes = List.of(captura, captura.plus(Duration.ofDays(3)));
        Instant agora = captura.plus(Duration.ofDays(3)).plusSeconds(3600);
        assertEquals(Politica.SEMPRE, Sapl.politicaDaLista(diasDiferentes, lista, Politica.SEMANAL, agora));
        assertEquals(Politica.SEMPRE, Sapl.politicaDaLista(diasDiferentes, lista, Politica.SE_AUSENTE, agora));
        // Período fechado com cópias da mesma coleta segue em cache. Sem cópia, vale a política pedida.
        assertEquals(Politica.SE_AUSENTE, Sapl.politicaDaLista(mesmoDia, lista, Politica.SE_AUSENTE, agora));
        assertEquals(Politica.SEMANAL, Sapl.politicaDaLista(List.of(), lista, Politica.SEMANAL, agora));
    }

    @Test
    void itemRepetidoEmDuasPaginasEntraUmaVez() throws Exception {
        List<JsonNode> p1 = List.of(Json.FONTE.readTree("{\"id\": 10}"), Json.FONTE.readTree("{\"id\": 11}"));
        List<JsonNode> p2 = List.of(Json.FONTE.readTree("{\"id\": 11}"), Json.FONTE.readTree("{\"id\": 12}"),
                Json.FONTE.readTree("{\"nome\": \"sem id\"}"), Json.FONTE.readTree("{\"nome\": \"sem id\"}"));

        List<JsonNode> itens = Sapl.semRepetidos(List.of(p1, p2));

        assertEquals(5, itens.size());
        assertEquals(List.of("10", "11", "12"), itens.stream().filter(i -> i.has("id")).map(i -> Json.texto(i, "id")).toList());
    }

    @Test
    void participacaoContaSoVotoRegistrado() {
        assertTrue(Sapl.registrado("Sim"));
        assertTrue(Sapl.registrado("Abstenção"));
        assertFalse(Sapl.registrado("Não Votou"));
        assertFalse(Sapl.registrado("Ausente"));
        assertFalse(Sapl.registrado(null));
    }

    @Test
    void sessoesFicamRestritasALegislaturaVigente() {
        JsonNode json = Json.FONTE.readTree("""
                [
                  {"id": 2106, "numero": 1, "data_inicio": "2025-01-01", "hora_inicio": "10:00", "tipo": "Solene", "legislatura_numero": 19},
                  {"id": 2000, "numero": 50, "data_inicio": "2024-12-10", "hora_inicio": "18:00", "tipo": "Ordinária", "legislatura_numero": 18}
                ]
                """);
        Map<Integer, Sapl.Sessao> sessoes = Sapl.sessoes(json, List.of(LEG19));
        assertEquals(1, sessoes.size());
        assertEquals(1, Sapl.realizadas(sessoes, HOJE).size());
        assertEquals(0, Sapl.realizadas(sessoes, LocalDate.of(2024, 12, 31)).size());
        assertEquals(etl.Coleta.Politica.SE_AUSENTE, Sapl.politicaDaSessao(sessoes.get(2106), HOJE));
        assertEquals(etl.Coleta.Politica.SEMANAL, Sapl.politicaDaSessao(sessoes.get(2106), LocalDate.of(2025, 2, 1)));
        assertEquals("2025-01-01T10:00", Sapl.instante(sessoes.get(2106)));
        assertEquals("1ª Sessão Solene", Sapl.tituloDaSessao(sessoes.get(2106)));
    }

    @Test
    void normaMunicipalViraAtoComTipoDoSchema() {
        JsonNode json = Json.FONTE.readTree("""
                {"id": 55896, "__str__": "Lei Complementar nº 513, de 31 de dezembro de 2024", "numero": "513", "ano": 2024,
                 "data": "2024-12-31", "ementa": "ALTERA A LEI Nº 13.213."}
                """);
        etl.Coleta.Arquivo arq = new etl.Coleta.Arquivo("https://exemplo.invalid/l", "raw/sapl/normas/2024-LCP-p1.json", "x", 1,
                java.time.Instant.parse("2026-11-02T00:00:00Z"));
        Sapl.Norma norma = Sapl.norma(json, Sapl.TIPOS_DE_NORMA.get("2"), arq);
        assertEquals("LCP", norma.tipo());
        assertEquals("513", norma.numero());
        assertEquals(LocalDate.of(2024, 12, 31), norma.data());
        assertEquals("3509502", norma.ente());
        assertEquals("https://sapl.campinas.sp.leg.br/norma/55896", norma.prov().urlOrigem());
        assertEquals("Membro", Sapl.papel("Membro", true));
        assertEquals("Presidente (suplente)", Sapl.papel("Presidente", false));
        assertEquals("Suplente", Sapl.papel("Suplente", false));
    }

    @Test
    void partidoNaDataSegueAsFiliacoes() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"__str__": "Nome - ANTIGO - Partido Antigo", "data": "2020-01-01", "data_desfiliacao": "2024-03-01"},
                    {"__str__": "Nome - PL - Partido Liberal", "data": "2024-03-02", "data_desfiliacao": null}
                  ]
                }
                """);
        List<Sapl.Filiacao> filiacoes = Sapl.filiacoes(json);
        assertEquals("ANTIGO", Sapl.partidoEm(filiacoes, LocalDate.of(2023, 6, 1)));
        assertEquals("PL", Sapl.partidoEm(filiacoes, LocalDate.of(2025, 6, 1)));
        assertEquals(null, Sapl.partidoEm(filiacoes, LocalDate.of(2019, 6, 1)));
    }

    @Test
    void filiacaoAtivaDeterminaPartido() {
        JsonNode json = Json.FONTE.readTree("""
                {
                  "results": [
                    {"__str__": "Nome - ANTIGO - Partido Antigo", "data": "2020-01-01", "data_desfiliacao": "2024-03-01"},
                    {"__str__": "Nome - PL - Partido Liberal", "data": "2024-03-02", "data_desfiliacao": null}
                  ]
                }
                """);
        assertEquals("PL", Sapl.extrairPartido(json, HOJE));
    }
}
