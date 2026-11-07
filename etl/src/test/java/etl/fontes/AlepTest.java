package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;

class AlepTest {

    @Test
    void itemVotadoLeCabecalhoEVotosPeloNomeCivil() {
        List<Map<String, String>> linhas = List.of(
                Map.of("A", "ITEM 10.0", "B", "PROJETO DE LEI 243/2024"),
                Map.of("A", "SESSÃO / REUNIÃO:", "B", "20ª SESSÃO ORDINÁRIA"),
                Map.of("A", "DATA:", "B", "31/03/2025 02:30:00"),
                Map.of("A", "TIPO VOTAÇÃO:", "B", "NOMINAL"),
                Map.of("A", "EMENTA:", "B", "Insere data no calendário oficial."),
                Map.of("A", "RESULTADO DA VOTAÇÃO:", "B", "APROVADO"),
                Map.of("A", "PARLAMENTAR", "B", "PARTIDO", "C", "VOTO"),
                Map.of("A", "FULANO DE TAL SILVA", "B", "AAA", "C", "SIM"),
                Map.of("A", "BELTRANA DOS SANTOS", "B", "BBB", "C", "NÃO VOTOU"));

        Alep.ItemVotado item = Alep.itemVotado(linhas);

        assertTrue(item.nominal());
        assertEquals("PROJETO DE LEI 243/2024", item.proposicao());
        assertEquals(java.time.LocalDate.of(2025, 3, 31), item.data());
        assertEquals("APROVADO", item.resultado());
        assertEquals(2, item.votos().size());
        assertEquals("FULANO DE TAL SILVA", item.votos().getFirst().nomeCivil());
        assertEquals("Sim", Alep.votoLegivel(item.votos().getFirst().voto()));
        assertEquals("Não votou", Alep.votoLegivel(item.votos().get(1).voto()));
        assertEquals("fulano-de-tal-silva", Alep.arquivoDeVotos("Fulano de Tal Silva"));
    }

    @Test
    void chaveDeAutoriaIgnoraCargoTituloEPontuacao() {
        assertEquals("FULANO DE TAL", Alep.chaveDeAutoria("DEPUTADO FULANO DE TAL"));
        assertEquals("DOUTORA BELTRANA", Alep.chaveDeAutoria(" DEPUTADA DRA. BELTRANA"));
        assertEquals("DOUTORA BELTRANA", Alep.chaveDeAutoria("Doutora Beltrana"));
        assertEquals("LUIS RAIMUNDO CORTI", Alep.chaveDeAutoria("luis-raimundo-corti"));
    }

    @Test
    void proposicoesDoAnoFicamComOsAutoresQueSaoDeputadosEmExercicio() {
        JsonNode resposta = Json.FONTE.readTree("""
                {"totalRegistrosSemLimitador": 2, "lista": [
                  {"codigo": 1, "numero": 54, "ano": 2025, "tipoProposicao": "PROJETO DE LEI", "status": "TRAMITANDO",
                   "ementa": "Concede título de utilidade pública.", "dataRecebimento": "2025-02-17T14:08:35.000+0000",
                   "autor": "DEPUTADO FULANO DE TAL, DEPUTADA DRA BELTRANA, PODER EXECUTIVO"},
                  {"codigo": 2, "numero": 7, "ano": 2025, "tipoProposicao": "REQUERIMENTO", "status": "ARQUIVADA",
                   "ementa": "Requer informações.", "dataRecebimento": "2025-03-01T10:00:00.000+0000",
                   "autor": "DEPUTADO SICRANO SEM PERFIL"}]}
                """);
        Prov prov = new Prov(Alep.FONTE_PROPOSICOES, Alep.API_PROPOSICOES, Instant.parse("2026-11-07T00:00:00Z"));

        Map<String, List<Proposicao>> porSlug = Alep.proposicoesDoAno(resposta,
                Map.of("FULANO DE TAL", "fulano-de-tal", "DOUTORA BELTRANA", "doutora-beltrana"), prov);

        assertEquals(2, porSlug.size());
        Proposicao doFulano = porSlug.get("fulano-de-tal").getFirst();
        assertEquals("PROJETO DE LEI 54/2025", doFulano.identificacao());
        assertEquals("TRAMITANDO", doFulano.situacao());
        assertEquals("2025-02-17", doFulano.dataApresentacao());
        assertEquals(1, doFulano.ordemAssinatura());
        assertEquals(2, porSlug.get("doutora-beltrana").getFirst().ordemAssinatura());
        assertEquals(prov, doFulano.prov());
    }

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Renato Freitas", Alep.formatarNome("RENATO FREITAS"));
        assertEquals("Alexandre Curi", Alep.formatarNome("ALEXANDRE CURI"));
        assertEquals("Gilson de Souza", Alep.formatarNome("GILSON DE SOUZA"));
        assertEquals("Adão Litro", Alep.formatarNome("ADÃO LITRO"));
        assertEquals("Delegado Tito Barichello", Alep.formatarNome("DELEGADO TITO BARICHELLO"));
        assertEquals("Ana Júlia", Alep.formatarNome("ANA JÚLIA"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <div class="deputado-card">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas" class="relative">
                        <img src="https://storage2.assembleia.pr.leg.br/img/foto1.jpg" alt="Foto">
                        <h3>RENATO FREITAS</h3>
                        <p class="text-gray-500 text-sm italic">PT</p>
                    </a>
                </div>
                <div class="deputado-card">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/alexandre-curi" class="relative">
                        <img src="https://storage2.assembleia.pr.leg.br/img/foto2.jpg" alt="Foto">
                        <h3>ALEXANDRE CURI</h3>
                        <p class="text-gray-500 text-sm italic">REP</p>
                    </a>
                </div>
                """;

        List<Alep.DeputadoBruto> deputados = Alep.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        assertEquals("renato-freitas", deputados.get(0).slug());
        assertEquals("RENATO FREITAS", deputados.get(0).nome());
        assertEquals("PT", deputados.get(0).partido());
        assertEquals("https://storage2.assembleia.pr.leg.br/img/foto1.jpg", deputados.get(0).fotoUrl());

        assertEquals("alexandre-curi", deputados.get(1).slug());
        assertEquals("ALEXANDRE CURI", deputados.get(1).nome());
        assertEquals("REP", deputados.get(1).partido());
        assertEquals("https://storage2.assembleia.pr.leg.br/img/foto2.jpg", deputados.get(1).fotoUrl());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String html = """
                <button>
                    <span class="font-bold text-[#004080] text-sm uppercase text-left">
                        Comissão de Constituição e Justiça
                    </span>
                </button>
                <div x-show="openAccordion === 'perm-1'">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/ana-julia">
                        <h3>ANA JÚLIA</h3>
                        <p class="text-gray-500 dd-14 text-sm">Suplente: RENATO FREITAS</p>
                    </a>
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/alexandre-curi">
                        <h3>ALEXANDRE CURI</h3>
                    </a>
                </div>
                """;

        var arq = new Coleta.Arquivo("https://exemplo", "raw/alep/comissoes.html", "hash", 100, Instant.now());
        Map<String, List<OrgaoOuFrente>> comissoes = Alep.extrairComissoesDoHtml(html, arq);

        assertTrue(comissoes.containsKey("ana-julia"));
        assertEquals("Comissão de Constituição e Justiça", comissoes.get("ana-julia").get(0).nome());
        assertEquals("comissao", comissoes.get("ana-julia").get(0).tipo());
        assertEquals("Suplente: RENATO FREITAS", comissoes.get("ana-julia").get(0).papel());

        assertTrue(comissoes.containsKey("alexandre-curi"));
        assertEquals("Membro", comissoes.get("alexandre-curi").get(0).papel());
    }

    @Test
    void normalizaDeputadoEComissoes(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-03T12:00:00Z"), null);

        Path rawAlep = ctx.raw().resolve("alep");
        Files.createDirectories(rawAlep.resolve("deputados"));
        Files.createDirectories(rawAlep.resolve("perfil"));

        String conhecaHtml = """
                <div class="deputado-card">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas" class="relative">
                        <img src="https://storage2.assembleia.pr.leg.br/img/renato.jpg" alt="Foto">
                        <h3>RENATO FREITAS</h3>
                        <p class="text-gray-500 text-sm italic">PT</p>
                    </a>
                </div>
                """;
        Files.writeString(rawAlep.resolve("deputados/conheca.html"), conhecaHtml);
        gravarMetadado(rawAlep, "deputados/conheca.html", "https://www.assembleia.pr.leg.br/deputados/conheca");

        String perfilHtml = "<div class='biografia'><p>Renato Freitas nasceu em Sorocaba...</p></div>";
        Files.writeString(rawAlep.resolve("perfil/renato-freitas.html"), perfilHtml);
        gravarMetadado(rawAlep, "perfil/renato-freitas.html", "https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas");

        String comissoesHtml = """
                <button>
                    <span class="uppercase">Comissão de Educação</span>
                </button>
                <div x-show="openAccordion === 'perm-1'">
                    <a href="https://www.assembleia.pr.leg.br/deputados/perfil/renato-freitas">
                        <h3>RENATO FREITAS</h3>
                        <p class="dd-14">Presidente</p>
                    </a>
                </div>
                """;
        Files.writeString(rawAlep.resolve("comissoes.html"), comissoesHtml);
        gravarMetadado(rawAlep, "comissoes.html", "https://www.assembleia.pr.leg.br/atividade-parlamentar/comissoes-permanentes-e-temporarias");

        Alep alep = new Alep();
        alep.normalize(ctx);

        // Verifica pessoas.json
        Path pessoasPath = ctx.normalizado().resolve("alep/pessoas.json");
        assertTrue(Files.exists(pessoasPath));
        JsonNode pessoas = Json.ler(pessoasPath);
        assertEquals(1, pessoas.size());
        JsonNode p = pessoas.get(0);
        assertEquals("alep", p.path("fonte").asText());
        assertEquals("renato-freitas", p.path("id_fonte").asText());
        assertEquals("Renato Freitas", p.path("nome").asText());
        assertEquals("deputado_estadual", p.path("cargo").asText());
        assertEquals("PR", p.path("uf").asText());
        assertEquals("PT", p.path("partido").asText());
        assertTrue(p.path("em_exercicio").asBoolean());

        // Verifica blocos/renato-freitas.json
        Path blocosPath = ctx.normalizado().resolve("alep/blocos/renato-freitas.json");
        assertTrue(Files.exists(blocosPath));
        JsonNode blocos = Json.ler(blocosPath);

        JsonNode mandatos = blocos.path("mandatos");
        assertEquals(1, mandatos.size());
        assertEquals("deputado_estadual", mandatos.get(0).path("cargo").asText());
        assertEquals("PR", mandatos.get(0).path("uf").asText());
        assertEquals(20, mandatos.get(0).path("legislatura").asInt());

        JsonNode orgaos = blocos.path("orgaos_e_frentes");
        assertEquals(1, orgaos.size());
        assertEquals("comissao", orgaos.get(0).path("tipo").asText());
        assertEquals("Comissão de Educação", orgaos.get(0).path("nome").asText());
        assertEquals("Presidente", orgaos.get(0).path("papel").asText());
    }

    private void gravarMetadado(Path pastaRaw, String arquivo, String url) throws IOException {
        Path meta = pastaRaw.resolve(arquivo + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alep/" + arquivo, "hash", 100, Instant.now());
        Files.createDirectories(meta.getParent());
        Json.gravar(meta, arq);
    }
}
