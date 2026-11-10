package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.Despesa;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

class AlescTest {

    @Test
    void listaDeSessoesTrazSoAsRealizadasComOrdemDoDia() {
        // Trecho no formato da lista do e-Legis em 2026-11-10: a sessão futura vem em "Horário regimental".
        String html = """
                <div class="card card-alesc mb-4"><div class="card-body">
                <h4 class="mb-1">
                    101ª Sessão Ordinária                </h4>
                <span class="text-nowrap"><i class="far fa-clock"></i> 10/11/2026 · 14:00 </span>
                <span class="badge rounded-pill bg-light text-secondary-emphasis"><i class="fas fa-circle fa-2xs text-warning"></i> Horário regimental</span>
                <a href="/sessoes-plenarias/5qB4N/ordem-do-dia" class="btn btn-sm btn-outline-default text-nowrap"><i class="fas fa-calendar-days"></i> Ordem do Dia</a>
                </div></div>
                <div class="card card-alesc mb-4"><div class="card-body">
                <h4 class="mb-1">
                    100ª Sessão Ordinária                </h4>
                <span class="text-nowrap"><i class="far fa-clock"></i> 27/10/2026 · 14:00 </span>
                <span class="badge rounded-pill bg-light text-secondary-emphasis"><i class="fas fa-circle fa-2xs text-success"></i> Realizada</span>
                <a href="/sessoes-plenarias/5bnZ5/ordem-do-dia" class="btn btn-sm btn-outline-default text-nowrap"><i class="fas fa-calendar-days"></i> Ordem do Dia</a>
                </div></div>
                <ul class="pagination"><li><a href="/sessoes-plenarias?page=2">2</a></li><li><a href="/sessoes-plenarias?page=64">64</a></li></ul>
                """;

        var sessoes = Alesc.sessoesDaPagina(html);

        assertEquals(2, sessoes.size());
        assertEquals("100ª Sessão Ordinária", sessoes.get(1).titulo());
        assertEquals(java.time.LocalDate.of(2026, 10, 27), sessoes.get(1).data());
        assertTrue(sessoes.get(1).realizada());
        assertEquals("Horário regimental", sessoes.get(0).situacao());
        assertTrue(!sessoes.get(0).realizada());
        assertEquals(64, Alesc.paginasDaLista(html));
    }

    @Test
    void ordemDoDiaDaSoAsVotacoesNominaisComOVotoDeQuemVotou() {
        // Trechos no formato da ordem do dia e do extrato do e-Legis, com nomes de exemplo.
        String ordem = """
                <tr><th>Início</th>
                <td>27/10/2026 às 14:00</td></tr>
                <div class="border-bottom mb-3"><h5 class="text-success m-0 me-1"><a href="/proposicoes/5aAJj">PCL/0269/2026</a></h5>
                <span class="badge text-bg-success">Aprovado</span>                <span class="badge text-bg-light">Votação simbólica</span>
                <p class="fst-italic text-secondary mb-1">Altera o quadro de pessoal.</p></div>
                <div class="border-bottom mb-3"><h5 class="text-success m-0 me-1"><a href="/proposicoes/K9JW0">PLC/0007/2026</a></h5>
                <span class="badge text-bg-success">Aprovado</span>                <span class="badge text-bg-light">Votação nominal</span>
                <p class="fst-italic text-secondary mb-1">Altera a Resolu&ccedil;&atilde;o n&ordm; 001, de 2006.</p>
                <a hx-get="/extrato-votacao/5qgL2" hx-trigger="click" class="btn btn-outline-default btn-sm btn-extrato-votacao">Extrato de votação</a></div>
                """;
        String extrato = """
                <h4 class="row text-center">PLC/0007/2026</h4>
                <div class="d-flex justify-content-between align-items-center w-100">
                    <div>Deputada Exemplo</div>
                    <div><span class="badge text-bg-success fw-normal">Sim</span></div>
                </div>
                <div class="d-flex justify-content-between align-items-center w-100">
                    <div>Deputado Teste</div>
                    <div><span class="badge text-bg-danger fw-normal">N&atilde;o</span></div>
                </div>
                """;

        var votacoes = Alesc.votacoesNominais(ordem);
        var votos = Alesc.votosDoExtrato(extrato);

        assertEquals(java.time.LocalDate.of(2026, 10, 27), Alesc.dataDaOrdemDoDia(ordem));
        assertEquals(1, votacoes.size());
        assertEquals("5qgL2", votacoes.getFirst().idExtrato());
        assertEquals("PLC 7/2026", votacoes.getFirst().proposicao());
        assertEquals("Aprovado", votacoes.getFirst().resultado());
        assertEquals("Altera a Resolução nº 001, de 2006.", votacoes.getFirst().ementa());
        assertEquals(List.of(new Alesc.VotoNoExtrato("Deputada Exemplo", "Sim"), new Alesc.VotoNoExtrato("Deputado Teste", "Não")), votos);
        assertEquals("PL 143/2026", Alesc.identificacao("PL./0143/2026"));
    }

    @Test
    void idQueSoDifereNaCaixaNaoCaiNoMesmoArquivo() {
        // Disco que não diferencia maiúsculas guardaria "5bnZ5" e "5bnz5" no mesmo arquivo.
        assertEquals("5bn_z5", Alesc.nomeDeArquivo("5bnZ5"));
        assertTrue(!Alesc.nomeDeArquivo("5bnZ5").equalsIgnoreCase(Alesc.nomeDeArquivo("5bnz5")));
    }

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Alex Brasil", Alesc.formatarNome("ALEX BRASIL"));
        assertEquals("Altair Silva", Alesc.formatarNome("ALTAIR SILVA"));
        assertEquals("Ana Campagnolo", Alesc.formatarNome("ANA CAMPAGNOLO"));
        assertEquals("Fabiano da Luz", Alesc.formatarNome("FABIANO DA LUZ"));
        assertEquals("Mauro de Nadal", Alesc.formatarNome("MAURO DE NADAL"));
    }

    @Test
    void normalizaFotoUrlRemovendoSufixoDeMiniatura() {
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500.jpg",
                Alesc.normalizarFotoUrl("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg"));
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Ana-Campagnolo-PL-scaled-aspect-ratio-500-500.png",
                Alesc.normalizarFotoUrl("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Ana-Campagnolo-PL-scaled-aspect-ratio-500-500-150x150.png"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <article class="lab-card-team mt-5">
                    <div class="row align-items-center flex-xl-nowrap">
                        <div class="col-xl-4 mb-3 mb-xl-0">
                            <a href="https://www.alesc.sc.gov.br/deputado/alex-brasil/">
                                <div class="row align-items-center gx-3">
                                    <div class="col-auto">
                                        <img src="https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg" alt="Alex Brasil" class="lab-team-img" width="150" height="150" loading="lazy">
                                    </div>
                                    <div class="col">
                                        <h3 class="lab-title-news">Alex Brasil</h3>
                                        <span class="lab-button px-2 py-1 lab-text mt-2" style="background: #404040; color: #ffffff;">PL</span>
                                    </div>
                                </div>
                            </a>
                        </div>
                    </div>
                </article>
                <article class="lab-card-team mt-5">
                    <div class="row align-items-center flex-xl-nowrap">
                        <div class="col-xl-4 mb-3 mb-xl-0">
                            <a href="https://www.alesc.sc.gov.br/deputado/altair-silva/">
                                <div class="row align-items-center gx-3">
                                    <div class="col-auto">
                                        <img src="https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Altair-Silva-PP-scaled-aspect-ratio-500-500-150x150.jpg" alt="Altair Silva" class="lab-team-img" width="150" height="150" loading="lazy">
                                    </div>
                                    <div class="col">
                                        <h3 class="lab-title-news">Altair Silva</h3>
                                        <span class="lab-button px-2 py-1 lab-text mt-2" style="background: #404040; color: #ffffff;">PP</span>
                                    </div>
                                </div>
                            </a>
                        </div>
                    </div>
                </article>
                """;

        List<Alesc.DeputadoBruto> deputados = Alesc.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        Alesc.DeputadoBruto d1 = deputados.get(0);
        assertEquals("alex-brasil", d1.slug());
        assertEquals("Alex Brasil", d1.nome());
        assertEquals("PL", d1.partido());
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg", d1.fotoUrl());

        Alesc.DeputadoBruto d2 = deputados.get(1);
        assertEquals("altair-silva", d2.slug());
        assertEquals("Altair Silva", d2.nome());
        assertEquals("PP", d2.partido());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String modalHtml = """
                <dl class="mb-3">
                    <dt>Presidente</dt>
                    <dd>Pepê Collaço</dd>
                </dl>
                <dl class="mb-3">
                    <dt>Vice-Presidente</dt>
                    <dd>Rodrigo Minotto</dd>
                </dl>
                <dl class="mb-3">
                    <dt>Membro</dt>
                    <dd>Alex Brasil</dd>
                    <dd>Fabiano da Luz</dd>
                </dl>
                """;

        Map<String, String> slugPorNome = Map.of(
                Texto.normalizarNome("Pepê Collaço"), "pepe-collaco",
                Texto.normalizarNome("Rodrigo Minotto"), "rodrigo-minotto",
                Texto.normalizarNome("Alex Brasil"), "alex-brasil",
                Texto.normalizarNome("Fabiano da Luz"), "fabiano-da-luz"
        );

        Prov prov = new Prov("alesc-deputados-detalhes", "https://portalelegis.alesc.sc.gov.br/comissoes-permanentes/bzJD5", Instant.parse("2026-11-05T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> resultado = Alesc.extrairComissoesDoHtml(
                modalHtml, "Comissão de Constituição e Justiça", slugPorNome, prov);

        assertEquals(4, resultado.size());
        assertEquals("Presidente", resultado.get("pepe-collaco").get(0).papel());
        assertEquals("Vice-Presidente", resultado.get("rodrigo-minotto").get(0).papel());
        assertEquals("Membro", resultado.get("alex-brasil").get(0).papel());
        assertEquals("Comissão de Constituição e Justiça", resultado.get("alex-brasil").get(0).nome());
        assertEquals("comissao", resultado.get("alex-brasil").get(0).tipo());
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-05T12:00:00Z"), null);

        Path rawAlesc = ctx.raw().resolve("alesc");
        Files.createDirectories(rawAlesc.resolve("perfis"));
        Files.createDirectories(rawAlesc.resolve("comissoes"));

        String html = """
                <article class="lab-card-team mt-5">
                    <div class="row align-items-center flex-xl-nowrap">
                        <div class="col-xl-4 mb-3 mb-xl-0">
                            <a href="https://www.alesc.sc.gov.br/deputado/alex-brasil/">
                                <div class="row align-items-center gx-3">
                                    <div class="col-auto">
                                        <img src="https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500-150x150.jpg" alt="Alex Brasil" class="lab-team-img" width="150" height="150" loading="lazy">
                                    </div>
                                    <div class="col">
                                        <h3 class="lab-title-news">Alex Brasil</h3>
                                        <span class="lab-button px-2 py-1 lab-text mt-2" style="background: #404040; color: #ffffff;">PL</span>
                                    </div>
                                </div>
                            </a>
                        </div>
                    </div>
                </article>
                """;

        Files.writeString(rawAlesc.resolve("deputados.html"), html);
        gravarMetadado(rawAlesc, "deputados.html", "https://www.alesc.sc.gov.br/deputados/");

        Alesc alesc = new Alesc();
        alesc.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alesc/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("alesc", dep.get("fonte").stringValue());
        assertEquals("alex-brasil", dep.get("id_fonte").stringValue());
        assertEquals("Alex Brasil", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("SC", dep.get("ente").stringValue());
        assertEquals("SC", dep.get("uf").stringValue());
        assertEquals("PL", dep.get("partido").stringValue());
        assertEquals("https://www.alesc.sc.gov.br/wp-content/uploads/2026/01/Dep.-Alex-Brasil-PL-aspect-ratio-500-500.jpg", dep.get("foto_url").stringValue());

        Path blocoJson = ctx.normalizado().resolve("alesc/blocos/alex-brasil.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        assertNotNull(bloco.get("mandatos"));
        assertEquals(1, bloco.get("mandatos").size());
        assertEquals("deputado_estadual", bloco.get("mandatos").get(0).get("cargo").stringValue());
        assertEquals("SC", bloco.get("mandatos").get(0).get("uf").stringValue());
    }

    @Test
    void divideCsvComPontoEVirgulaEEspas() {
        String linha = "ALMOXARIFADO;\"Jornais e Revistas\";\"Ana Paula da Silva (Paulinha)\";;;\t26/06/2026;733,00";
        String[] partes = Alesc.splitCsvPontoEVirgula(linha);
        assertEquals(7, partes.length);
        assertEquals("ALMOXARIFADO", partes[0]);
        assertEquals("Jornais e Revistas", partes[1]);
        assertEquals("Ana Paula da Silva (Paulinha)", partes[2]);
        assertEquals("26/06/2026", partes[5]);
        assertEquals("733,00", partes[6]);
    }

    @Test
    void resolveSlugPorNomeIgnoraParenteses() {
        Map<String, String> mapa = Map.of(
                "ANTIDIO LUNELLI", "antidio-lunelli"
        );
        assertEquals("paulinha", Alesc.resolverSlugPorNome("Ana Paula da Silva (Paulinha)", mapa));
        assertEquals("antidio-lunelli", Alesc.resolverSlugPorNome("Antídio Lunelli", mapa));
    }

    @Test
    void vinculaDespesaPeloNomeCivilERemoveCpfDoFavorecido(@TempDir Path tempDir) throws Exception {
        Contexto ctx = new Contexto(tempDir, tempDir.resolve("data"), null, null, Instant.parse("2026-11-05T12:00:00Z"), null);
        Path rawAlesc = ctx.raw().resolve("alesc");
        Files.createDirectories(rawAlesc.resolve("despesas"));

        // Cabeçalho com BOM, como na fonte. O favorecido MEI vem como "NOME + CPF"; o estorno vem com valor negativo.
        String csv = "﻿Verba;Descrição;Conta;Favorecido;Trecho;\"Data de Referência\";Valor\n"
                + "REEMBOLSOS;\"Material de Escritório\";\"Jerry Edson Comper\";\"FULANO DE TAL 12345678909\";;15/03/2026;1.234,50\n"
                + "REEMBOLSOS;\"Material de Escritório\";\"Jerry Edson Comper\";\"PAPELARIA EXEMPLO LTDA\";;16/03/2026;-20,00\n"
                + "ALMOXARIFADO;\"Jornais e Revistas\";\"Alex Brasil\";;;10/03/2026;733,00\n"
                + "ALMOXARIFADO;\"Jornais e Revistas\";\"Suplente Sem Perfil\";;;11/03/2026;10,00\n";
        Files.writeString(rawAlesc.resolve("despesas/2026.csv"), csv);
        gravarMetadado(rawAlesc, "despesas/2026.csv", Alesc.URL_DESPESAS + "2026");

        Map<String, String> slugPorNome = Map.of(
                Texto.normalizarNome("Jerry Comper"), "jerry-comper",
                Texto.normalizarNome("Alex Brasil"), "alex-brasil");

        Map<String, List<Despesa>> despesas = Alesc.extrairDespesas(ctx, slugPorNome);

        assertEquals(Set.of("jerry-comper", "alex-brasil"), despesas.keySet());

        List<Despesa> jerry = despesas.get("jerry-comper");
        assertEquals(2, jerry.size());
        assertEquals(Set.of("FULANO DE TAL", "PAPELARIA EXEMPLO LTDA"),
                jerry.stream().map(Despesa::fornecedorNome).collect(Collectors.toSet()));
        BigDecimal total = jerry.stream().map(Despesa::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("1214.50").compareTo(total));
        assertEquals("Reembolsos - Material de Escritório", jerry.get(0).categoria());
        assertEquals(3, jerry.get(0).mes());

        assertNull(despesas.get("alex-brasil").get(0).fornecedorNome());
    }

    @Test
    void mapeiaIniciativasEFormatosDeProposicao() {
        assertEquals("lunelli", Alesc.iniciativaParaSlug("antidio-lunelli"));
        assertEquals("pedro-baldissera", Alesc.iniciativaParaSlug("padre-pedro-baldissera"));
        assertEquals("vicente-caropreso", Alesc.iniciativaParaSlug("dr-vicente-caropreso"));
        assertEquals("altair-silva", Alesc.iniciativaParaSlug("altair-silva"));

        assertEquals("Projeto de Lei", Alesc.nomeTipo("PL"));
        assertEquals("Projeto de Lei Complementar", Alesc.nomeTipo("PLC"));
        assertEquals("Projeto de Decreto Legislativo", Alesc.nomeTipo("PDL"));
        assertEquals("Proposta de Emenda Constitucional", Alesc.nomeTipo("PEC"));
        assertEquals("Projeto de Resolução", Alesc.nomeTipo("PR"));

        assertTrue(Alesc.slugCasaComAutor("altair-silva", "DEPUTADO ALTAIR SILVA"));
        assertTrue(Alesc.slugCasaComAutor("padre-pedro-baldissera", "DEPUTADO PADRE PEDRO BALDISSERA"));
        assertTrue(Alesc.slugCasaComAutor("dr-vicente-caropreso", "DEPUTADO DR. VICENTE CAROPRESO"));
        assertTrue(Alesc.slugCasaComAutor("ana-campagnolo", "DEPUTADA ANA CAMPAGNOLO"));

        String htmlAntigo = """
                <div class="card card-alesc mb-3">
                  <div class="row">
                    <div class="col-lg-2 text-lg-end fw-bold no-wrap">Entrada</div>
                    <div class="col-lg-10">05/06/2008</div>
                  </div>
                </div>
                """;
        assertTrue(Alesc.devePararPaginacaoDoHtml(htmlAntigo));

        String htmlAtual = """
                <div class="card card-alesc mb-3">
                  <div class="row">
                    <div class="col-lg-2 text-lg-end fw-bold no-wrap">Entrada</div>
                    <div class="col-lg-10">07/10/2026</div>
                  </div>
                </div>
                """;
        org.junit.jupiter.api.Assertions.assertFalse(Alesc.devePararPaginacaoDoHtml(htmlAtual));
    }

    @Test
    void extraiProposicoesDeFragmentoHtml() {
        String html = """
                <div class="card card-alesc mb-3">
                    <div class="card-body">
                        <h4 class="card-title">
                            <a href="/proposicoes/NQeb8">PL./0640/2026</a>
                        </h4>
                        <p class="mb-1 fst-italic" style="-webkit-line-clamp: 3;">Altera a Lei Estadual nº 19.720, para incluir município.</p>
                        <div>
                            <div class="row">
                                <div class="col-lg-2 text-lg-end fw-bold no-wrap">Entrada</div>
                                <div class="col-lg-10">07/10/2026</div>
                            </div>
                            <div class="row">
                                <div class="col-lg-2 text-lg-end fw-bold">Autoria</div>
                                <div class="col-lg-10"><ul class="m-0 list-unstyled"><li>Deputado Altair Silva</li><li>Deputado Dr. Vicente Caropreso</li></ul></div>
                            </div>
                            <div class="row">
                                <div class="col-lg-2 text-lg-end fw-bold">Situação atual</div>
                                <div class="col-lg-10">Aguardando apreciação pela Comissão</div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="card card-alesc mb-3">
                    <div class="card-body">
                        <h4 class="card-title">
                            <a href="/proposicoes/ABC12">PL./0001/2020</a>
                        </h4>
                        <p class="mb-1 fst-italic">Proposição de mandato anterior.</p>
                        <div>
                            <div class="row">
                                <div class="col-lg-2 text-lg-end fw-bold no-wrap">Entrada</div>
                                <div class="col-lg-10">10/02/2020</div>
                            </div>
                        </div>
                    </div>
                </div>
                """;

        Prov prov = new Prov("alesc-proposicoes", "https://portalelegis.alesc.sc.gov.br/proposicoes/processo-legislativo?iniciativa=altair-silva&page=1", Instant.parse("2026-11-08T12:00:00Z"));
        List<Proposicao> props = Alesc.extrairProposicoesDoHtml(html, "altair-silva", prov);

        // A segunda proposição é de 2020 (anterior a 2023), então é descartada da 20ª legislatura
        assertEquals(1, props.size());

        Proposicao p1 = props.get(0);
        assertEquals("NQeb8", p1.idFonte());
        assertEquals("PL./0640/2026", p1.identificacao());
        assertEquals("Projeto de Lei", p1.tipo());
        assertEquals(640, p1.numero());
        assertEquals(2026, p1.ano());
        assertEquals("2026-10-07", p1.dataApresentacao());
        assertEquals("Altera a Lei Estadual nº 19.720, para incluir município.", p1.ementa());
        assertEquals("Aguardando apreciação pela Comissão", p1.situacao());
        assertEquals(1, p1.ordemAssinatura());
        assertTrue(p1.proponente());
        assertEquals("https://portalelegis.alesc.sc.gov.br/proposicoes/NQeb8", p1.urlTexto());
        assertEquals(prov, p1.prov());

        // Testando segundo autor
        List<Proposicao> propsAutor2 = Alesc.extrairProposicoesDoHtml(html, "dr-vicente-caropreso", prov);
        assertEquals(1, propsAutor2.size());
        assertEquals(2, propsAutor2.get(0).ordemAssinatura());
        org.junit.jupiter.api.Assertions.assertFalse(propsAutor2.get(0).proponente());
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alesc/" + arquivo, "abc", 100L, Instant.parse("2026-11-05T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
