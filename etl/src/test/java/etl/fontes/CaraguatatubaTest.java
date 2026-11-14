package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Json;
import etl.Registros.Despesa;
import etl.Registros.Evento;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.fontes.Caraguatatuba.Ficha;
import etl.fontes.Caraguatatuba.Legislatura;
import etl.fontes.Caraguatatuba.MandatoDaFicha;
import etl.fontes.Caraguatatuba.Marca;
import etl.fontes.Caraguatatuba.Materia;
import etl.fontes.Caraguatatuba.MembroDeComissao;
import etl.fontes.Caraguatatuba.Nomes;
import etl.fontes.Caraguatatuba.PaginaDeMaterias;
import etl.fontes.Caraguatatuba.SessaoDaLista;
import tools.jackson.databind.JsonNode;

/** Fixtures sintéticas, com a marcação das páginas oficiais e nomes fictícios. */
class CaraguatatubaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 13);
    private static final Prov PROV = new Prov(Caraguatatuba.FONTE_SAGL, "https://exemplo.invalid/", Instant.parse("2026-11-13T12:00:00Z"));

    private static final String INDICE = """
            <select id="lst_num_legislatura" name="lst_num_legislatura">
              <option
                 value="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_index_html?hdn_num_legislatura=19">
                 19ª (2025 - 2028)
                    (Atual)
              </option>
              <option value="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_index_html?hdn_num_legislatura=18">
                 18ª (2021 - 2024)
              </option>
              <option value="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_index_html?hdn_num_legislatura=17">
                 17ª (2017 - 2020)
              </option>
              <option value="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_index_html?hdn_num_legislatura=-1">
                 sem mandato cadastrado
              </option>
            </select>
            <a href="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_mostrar_proc?cod_parlamentar=901"><img></a>
            <a href="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_mostrar_proc?cod_parlamentar=901">Fulano do Bairro</a>
            <a href="https://sistema.camaracaragua.sp.gov.br/consultas/parlamentar/parlamentar_mostrar_proc?cod_parlamentar=902">Beltrana</a>
            """;

    private static final String FICHA = """
            <html><head><script>var x = "<li>Telefone</li>";</script></head><body>
            <main role="main"><div class="row"><div class="col-9">
              <h1 class="firstHeading">
                Fulano do Bairro
              </h1>
            </div></div>
            <div class="card mb-3">
              <img alt="Fulano de Tal Exemplo" src="https://sistema.camaracaragua.sp.gov.br/sapl_documentos/parlamentar/fotos/901_foto_parlamentar" class="mx-auto img-fluid">
              <ul class="infoVereador">
                <li><b>Nome civil:</b> Fulano de Tal Exemplo</li>
                <li><b>Telefone: </b>(12) 3333-4444</li>
                <li><a href="mailto:fulano@exemplo.invalid"><i class="fa fa-fw fa-envelope"></i> fulano@exemplo.invalid</a></li>
              </ul>
              <span class=" badge badge-primary mt-2">Em exercício</span>
            </div>
            <ul class="nav nav-tabs border-0" id="nav_parlamentar" role="tablist"></ul>
            <div class="tab-content bg-white">
              <div class="tab-pane p-3 border fade show active" id="biografia">
                <legend>Biografia</legend>
                <p>Texto biográfico fictício, com vida familiar.</p>
              </div>
              <div class="tab-pane p-3 border fade" id="filiacao">
                <legend>Filiações Partidárias</legend>
                <table class="table">
                  <thead class="table-secondary"><tr><th>Partido</th><th>Filiação</th><th>Desfiliação</th></tr></thead>
                  <tbody>
                    <tr><td>
                        AGIR36
                      </td><td>
                        01/05/2024
                      </td><td >
                      </td></tr>
                    <tr><td> ABC </td><td> 01/01/2016 </td><td > 01/05/2024 </td></tr>
                  </tbody>
                </table>
              </div>
              <div class="tab-pane p-3 border fade" id="mandato">
                <legend>Histórico de Mandatos</legend>
                <table class="table">
                  <thead class="table-secondary"><tr><th width="30%">Legislatura</th><th>Titular?</th><th>Votos</th><th>Início</th><th>Fim</th></tr></thead>
                  <tbody>
                    <tr><td>
                         19ª (2025 - 2028)
                       </td><td>Sim</td><td></td><td>01/01/2025</td><td>01/01/2029</td></tr>
                    <tr><td> 18ª (2021 - 2024) </td><td>Não</td><td>0</td><td>02/01/2021</td><td>31/03/2024</td></tr>
                    <tr><td> 17ª (2017 - 2020) </td><td>Sim</td><td>617</td><td>01/01/2017</td><td>31/12/2020</td></tr>
                  </tbody>
                </table>
              </div>
              <div class="tab-pane p-3 border fade" id="mesa">
                <legend>Participações na Mesa Diretora</legend>
                <table class="table" >
                  <thead class="table-secondary"><tr><th>Cargo na Mesa</th><th>Período</th><th>Legislatura</th></tr></thead>
                  <tr><td>Vice-Presidente</td><td>01/01/2023 a 31/12/2024</td><td>18ª (2021 - 2024)</td></tr>
                  <tr><td>1º Secretário</td><td>01/01/2019 a 31/12/2020</td><td>17ª (2017 - 2020)</td></tr>
                  </td></tr>
                </table>
              </div>
              <div class="tab-pane p-3 border fade" id="comissao">
                <legend>Participações em Comissões</legend>
                <table class="table">
                  <thead class="table-secondary"><tr><th width="60%">Comissão</th><th width="15%">Cargo</th><th>Período</th></tr><thead>
                  <tr><td>
                      Comissão de Exemplo, Justiça e Redação
                    </td><td>
                      Suplente
                    </td><td>
                      02/02/2021 a 31/12/2022 </td></tr>
                  <tr><td> Comissão Antiga </td><td> Membro </td><td> 01/01/2019 a 31/12/2020 </td></tr>
                </table>
              </div>
              <div class="tab-pane p-3 border fade" id="proposicao">
                <legend>Proposições Apresentadas</legend>
                <table class="table">
                  <tr><td><a href="https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_pesquisar_proc?incluir=0&txt_ano=2026&hdn_cod_autor=77&lst_tip_autor=Parlamentar">Indicação</a></td><td class="text-center">9</td></tr>
                </table>
              </div>
            </div></main></body></html>
            """;

    private static final String PESQUISA = """
            <div class="card-header"><i class="far fa-list-alt"></i> Resultado: 1.203 matérias </div>
            <ul class="list-group list-group-flush">
            <li class="list-group-item">
              <div class="row"><div class="col-12 col-sm-9 col-lg-10">
                <a href="https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_mostrar_proc?cod_materia=Mzk1OA==" >
                  <p class="h6 font-weight-bold text-uppercase mb-1"><i class="fas fa-caret-right"></i> Projeto de Lei nº 94/2025</p></a>
              </div><div class="col-12 col-sm-3 col-lg-2 text-sm-right"><span class="badge badge-primary">Em Tramitação</span></div></div>
              <div class="row"><div class="col-12">
                <p class="mb-0">Dispõe sobre exemplo &amp; teste. Contato: (12) 99999-8888.</p>
              </div></div>
              <div class="row"><div class="col-12"><b>Autoria:</b> Fulano do Bairro </div></div>
              <div class="row"><div class="col-12"><b>Data de Apresentação:</b> 17/11/2025</div>
                <div class="col-12"><b>Protocolo:</b> 1255/2025</div></div>
              <div class="row"><div class="col-12"><b>Matéria Anexada:</b>
                <a href="https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_mostrar_proc?cod_materia=NDg3Mw==" > VET 1/2026</a></div></div>
              <div class="row"><div class="col-12"><b>Situação em 19/11/2025 13:06:41:</b> Solicita parecer jurídico</div></div>
              <a class="btn btn-sm btn-primary" href="https://sistema.camaracaragua.sp.gov.br/pysc/download_materia_pysc?cod_materia=Mzk1OA==&amp;texto_original=1" target="_blank">Texto Integral</a>
            </li>
            <li class="list-group-item">
              <a href="https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_mostrar_proc?cod_materia=MzYyMw==" >
                <p class="h6 font-weight-bold text-uppercase mb-1"><i class="fas fa-caret-right"></i> Indicação nº 7/2025</p></a>
              <p class="mb-0">Solicita a limpeza de um canal.</p>
              <b>Data de Apresentação:</b> 08/09/2025
            </li>
            <li class="list-group-item">
              <a href="https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_mostrar_proc?cod_materia=OTk5" >
                <p class="h6 font-weight-bold text-uppercase mb-1"><i class="fas fa-caret-right"></i> Documento sem número</p></a>
              <p class="mb-0">Item que a leitura não reconhece.</p>
            </li>
            </ul>
            <nav aria-label="Paginação"></nav>
            """;

    private static final String COMISSOES = """
            <script>document.querySelectorAll('.commission-item');</script>
            <div class="d-flex flex-column gap-5" id="commissions-container">
              <section class="card shadow-sm border rounded-3 overflow-hidden commission-item" data-commission-name="comissão permanente de exemplo">
                <h2 class="card-title-md text-primary mb-0">Comissão Permanente de Exemplo</h2>
                <h3 class="label-sm text-muted text-uppercase mb-4">Composição Atual</h3>
                <div class="col-12 col-md-6 commission-member" data-member-name="fulano de tal exemplo">
                  <div class="border rounded-3 p-4 commission-member-card bg-primary-light">
                    <a href="/vereadores-da-casa/fulano-de-tal-exemplo"><p class="fw-bold text-dark mb-1 card-title-small hover-text-primary transition-all">Fulano de Tal Exemplo</p></a>
                    <p class="text-xs fw-medium text-primary mb-1">Presidente</p>
                    <p class="text-xs text-muted mb-0">Partido Exemplo (PEX)</p>
                  </div>
                </div>
                <div class="col-12 col-md-6 commission-member" data-member-name="beltrana exemplo de souza">
                  <div class="border rounded-3 p-4 commission-member-card ">
                    <p class="fw-bold text-dark mb-1 card-title-small hover-text-primary transition-all">Beltrana Exemplo de Souza</p>
                    <p class="text-xs fw-medium text-primary mb-1">Suplente</p>
                  </div>
                </div>
              </section>
              <section class="card shadow-sm border rounded-3 overflow-hidden commission-item" data-commission-name="comissão permanente de teste">
                <h2 class="card-title-md text-primary mb-0">Comissão Permanente de Teste</h2>
                <div class="col-12 col-md-6 commission-member" data-member-name="beltrana exemplo de souza">
                  <p class="fw-bold text-dark mb-1 card-title-small hover-text-primary transition-all">Beltrana Exemplo de Souza</p>
                  <p class="text-xs fw-medium text-primary mb-1">Vice Presidente</p>
                </div>
              </section>
            </div>
            """;

    private static final String LISTAGEM = """
            {"success": true, "pagination": {"current_page": 1, "last_page": 1},
             "data": [
              {"name": "<span class=\\"fw-bold\\">19a Sessão Ordinária de 2026</span>",
               "sessions_display": "<span class=\\"badge badge-light fs-7\\">Sessão Ordinária nº 19 de 09/06/2026</span><br>",
               "view_attendance": "<button type=\\"button\\" data-attendance-id=\\"60\\" data-attendance-name=\\"19a\\">Ver / Imprimir</button>"},
              {"name": "<span class=\\"fw-bold\\">Semestre</span>",
               "sessions_display": "<span class=\\"badge\\">Sessão Ordinária nº 1 de 03/02/2026</span><br><span class=\\"badge\\">Sessão Ordinária nº 2 de 10/02/2026</span><br>",
               "view_attendance": "<button type=\\"button\\" data-attendance-id=\\"61\\">Ver / Imprimir</button>"},
              {"name": "<span>Sem padrão</span>", "sessions_display": "<span>Reunião sem número</span>",
               "view_attendance": "<button data-attendance-id=\\"62\\">Ver</button>"}
             ]}
            """;

    private static final String LISTA = """
            <style>.listPresenca tr{border: 1px solid #c0c8cf;}</style>
            <table width="100%" class="listPresenca">
              <tr><td class="titTable">Sessões</td><td style="text-align:center">ORD</td><td colspan="4" rowspan="3" style="text-align:center">TOTAL<br>DO<br>PERÍODO</td></tr>
              <tr style="border: none;"><td style="border: none;">&nbsp;</td></tr>
              <tr><td class="titTable">Data</td><td style="text-align:center;">09/06</td></tr>
              <tr><td class="subtitTable">Vereadores</td><td colspan="1" class="subtitTable" style="text-align:center">Controle de Presença</td><td class="subtitTable countControle">P</td><td class="subtitTable countControle">L</td><td class="subtitTable countControle">FJ</td><td class="subtitTable countControle">F</td></tr>
              <tr><td style="padding:5px">Fulano de Tal Exemplo</td><td style="text-align:center;">P</td><td class="countControle">1</td><td class="countControle"></td><td class="countControle"></td><td class="countControle"></td></tr>
              <tr><td style="padding:5px">Beltrana Exemplo de Souza</td><td style="text-align:center;">FJ</td><td class="countControle"></td><td class="countControle"></td><td class="countControle">1</td><td class="countControle"></td></tr>
              <tr><td style="padding:5px">Cicrano Modelo da Silva</td><td style="text-align:center;">L</td><td class="countControle"></td><td class="countControle">1</td><td class="countControle"></td><td class="countControle"></td></tr>
              <tr><td style="padding:5px">Deltrano Amostra Lima</td><td style="text-align:center;">F</td><td class="countControle"></td><td class="countControle"></td><td class="countControle"></td><td class="countControle">1</td></tr>
              <tr><td colspan="6">Observações:</td></tr>
              <tr><td colspan="6">P - Presença</td></tr>
            </table>
            """;

    private static Ficha fichaDe(String nomeParlamentar, String nomeCivil) {
        return new Ficha(nomeParlamentar, nomeCivil, true, null, List.of(), List.of(), List.of(), List.of(), null);
    }

    @Test
    void legislaturasVemDoSeletorESoAsQueTocamACoberturaEntram() {
        List<Legislatura> todas = Caraguatatuba.legislaturas(INDICE);
        assertEquals(List.of(new Legislatura(17, 2017, 2020), new Legislatura(18, 2021, 2024), new Legislatura(19, 2025, 2028)), todas);
        assertEquals(List.of(new Legislatura(18, 2021, 2024), new Legislatura(19, 2025, 2028)), Caraguatatuba.cobertas(todas, HOJE));
        assertEquals(List.of(new Legislatura(18, 2021, 2024)), Caraguatatuba.cobertas(todas, LocalDate.of(2024, 6, 1)), "legislatura futura fica fora");
    }

    @Test
    void codigosDoIndiceSaemNaOrdemDaFonteESemRepeticao() {
        assertEquals(List.of("901", "902"), Caraguatatuba.parlamentaresDoIndice(INDICE));
        assertEquals(List.of(), Caraguatatuba.parlamentaresDoIndice(null));
    }

    @Test
    void fichaTrazNomesFiliacoesMandatosMesaComissoesEAutor() {
        Ficha f = Caraguatatuba.ficha(FICHA);
        assertEquals("Fulano do Bairro", f.nomeParlamentar());
        assertEquals("Fulano de Tal Exemplo", f.nomeCivil());
        assertTrue(f.emExercicio());
        assertEquals("https://sistema.camaracaragua.sp.gov.br/sapl_documentos/parlamentar/fotos/901_foto_parlamentar", f.fotoUrl());
        assertEquals("77", f.codAutor());

        assertEquals(2, f.filiacoes().size());
        assertEquals("AGIR", f.filiacoes().get(0).partido(), "a sigla sai sem os dígitos que o sistema cola no fim");
        assertEquals(LocalDate.of(2024, 5, 1), f.filiacoes().get(0).inicio());
        assertNull(f.filiacoes().get(0).fim());
        assertEquals("ABC", f.filiacoes().get(1).partido());
        assertEquals(LocalDate.of(2024, 5, 1), f.filiacoes().get(1).fim());

        assertEquals(3, f.mandatos().size());
        assertEquals(new MandatoDaFicha(17, true, LocalDate.of(2017, 1, 1), LocalDate.of(2020, 12, 31)), f.mandatos().get(0));
        assertEquals(new MandatoDaFicha(18, false, LocalDate.of(2021, 1, 2), LocalDate.of(2024, 3, 31)), f.mandatos().get(1), "suplente que exerceu");
        assertEquals(new MandatoDaFicha(19, true, LocalDate.of(2025, 1, 1), LocalDate.of(2029, 1, 1)), f.mandatos().get(2));

        assertEquals(2, f.mesa().size());
        assertEquals("Vice-Presidente", f.mesa().get(0).papel());
        assertEquals(LocalDate.of(2023, 1, 1), f.mesa().get(0).inicio());
        assertEquals(LocalDate.of(2024, 12, 31), f.mesa().get(0).fim());
        assertEquals("Comissão de Exemplo, Justiça e Redação", f.comissoes().get(0).orgao());
        assertEquals("Suplente", f.comissoes().get(0).papel());
    }

    @Test
    void fichaVaiParaOCacheSemContatoNemBiografiaEContinuaLegivel() throws IOException {
        String noCache = Caraguatatuba.semContatoNemBiografia(FICHA);
        assertFalse(noCache.contains("3333-4444"), "telefone fica fora do cache");
        assertFalse(noCache.contains("exemplo.invalid"), "e-mail fica fora do cache");
        assertFalse(noCache.contains("vida familiar"), "biografia fica fora do cache");
        assertFalse(noCache.contains("<script"));
        assertEquals(Caraguatatuba.ficha(FICHA), Caraguatatuba.ficha(noCache), "o que o projeto usa continua na ficha");
        assertEquals(List.of(), etl.Guarda.violacoes(noCache));
    }

    @Test
    void fichaComBiografiaESemSecaoDepoisDelaNaoEGravada() {
        String semFiliacao = "<div class=\"tab-pane p-3\" id=\"biografia\"><p>Texto</p></div>";
        assertThrows(IOException.class, () -> Caraguatatuba.semContatoNemBiografia(semFiliacao));
    }

    @Test
    void fichaVaziaNaoQuebraEFicaSemDado() {
        Ficha f = Caraguatatuba.ficha("<html></html>");
        assertNull(f.nomeParlamentar());
        assertFalse(f.emExercicio());
        assertEquals(List.of(), f.mandatos());
        assertNull(f.codAutor());
    }

    @Test
    void mesaEComissoesDaFichaEntramSoComPeriodoNaCobertura() {
        List<OrgaoOuFrente> orgaos = Caraguatatuba.orgaos(Caraguatatuba.ficha(FICHA), PROV);
        assertEquals(2, orgaos.size(), "Mesa de 2019 a 2020 e comissão de 2019 a 2020 ficam fora");
        assertEquals("orgao", orgaos.get(0).tipo());
        assertEquals("Mesa Diretora", orgaos.get(0).nome());
        assertEquals("Vice-Presidente", orgaos.get(0).papel());
        assertEquals("comissao", orgaos.get(1).tipo());
        assertEquals(LocalDate.of(2022, 12, 31), orgaos.get(1).fim());
    }

    @Test
    void pesquisaDeMateriasTrazTotalEMateriasComTipoNumeroEAno() {
        PaginaDeMaterias pagina = Caraguatatuba.materias(PESQUISA);
        assertEquals(1203, pagina.total());
        assertEquals(2, pagina.materias().size(), "item sem tipo, número e ano fica fora");
        Materia pl = pagina.materias().get(0);
        assertEquals("3958", pl.id(), "o código da URL é o identificador em base64");
        assertEquals("Mzk1OA==", pl.codigo());
        assertEquals("Projeto de Lei", pl.tipo());
        assertEquals(94, pl.numero());
        assertEquals(2025, pl.ano());
        assertEquals("Dispõe sobre exemplo & teste. Contato: .", pl.ementa(), "telefone sai da ementa");
        assertEquals(LocalDate.of(2025, 11, 17), pl.apresentacao());
        assertEquals("https://sistema.camaracaragua.sp.gov.br/pysc/download_materia_pysc?cod_materia=Mzk1OA==&texto_original=1", pl.urlTexto());
        Materia indicacao = pagina.materias().get(1);
        assertEquals("3623", indicacao.id());
        assertEquals("Indicação", indicacao.tipo());
        assertNull(indicacao.urlTexto());
        assertEquals(0, Caraguatatuba.materias("<p>Nenhuma ocorrência encontrada.</p>").total());
    }

    @Test
    void idDaMateriaQueNaoEBase64DeNumeroFicaComoVeio() {
        assertEquals("3958", Caraguatatuba.idDaMateria("Mzk1OA=="));
        assertEquals("abc", Caraguatatuba.idDaMateria("abc"));
        assertEquals("%%%", Caraguatatuba.idDaMateria("%%%"));
    }

    @Test
    void proposicaoSaiSemSituacaoEApontaParaAPaginaDaMateria() {
        Coleta.Arquivo arq = new Coleta.Arquivo("https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_pesquisar_proc?x=1",
                "raw/caraguatatuba/sagl/materias/901-2025/p1.html", "h", 10, Instant.parse("2026-11-13T12:00:00Z"));
        Proposicao p = Caraguatatuba.proposicao(Caraguatatuba.materias(PESQUISA).materias().get(0), arq);
        assertEquals("Projeto de Lei nº 94/2025", p.identificacao());
        assertNull(p.situacao(), "a fonte não mantém a tramitação: o campo fica vazio");
        assertNull(p.temas());
        assertEquals("2025-11-17", p.dataApresentacao());
        assertEquals(Caraguatatuba.FONTE_SAGL, p.prov().fonteId());
        assertEquals("https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_mostrar_proc?cod_materia=Mzk1OA==", p.prov().urlOrigem());
    }

    @Test
    void urlDaPesquisaPedeDuzentasPorPaginaDoAutorNoAno() {
        String url = Caraguatatuba.urlDasMaterias("77", 2025, 2);
        assertTrue(url.contains("hdn_cod_autor=77") && url.contains("txt_ano=2025") && url.endsWith("&step=200&page=2"), url);
    }

    @Test
    void comissoesDoPortalTrazemMembroEPapelDeCadaComissao() {
        assertEquals(List.of(
                new MembroDeComissao("Comissão Permanente de Exemplo", "Fulano de Tal Exemplo", "Presidente"),
                new MembroDeComissao("Comissão Permanente de Exemplo", "Beltrana Exemplo de Souza", "Suplente"),
                new MembroDeComissao("Comissão Permanente de Teste", "Beltrana Exemplo de Souza", "Vice Presidente")),
                Caraguatatuba.comissoes(COMISSOES));
        assertEquals(List.of(), Caraguatatuba.comissoes("<html></html>"));
    }

    @Test
    void soListaDeUmaSessaoComTipoNumeroEDataEntra() throws Exception {
        JsonNode pagina = Json.FONTE.readTree(LISTAGEM);
        assertEquals(List.of(new SessaoDaLista("60", "Sessão Ordinária", 19, LocalDate.of(2026, 6, 9))), Caraguatatuba.listas(pagina));
    }

    @Test
    void marcasDaListaDeUmaSessao() {
        assertEquals(List.of(new Marca("Fulano de Tal Exemplo", "P"), new Marca("Beltrana Exemplo de Souza", "FJ"),
                new Marca("Cicrano Modelo da Silva", "L"), new Marca("Deltrano Amostra Lima", "F")), Caraguatatuba.marcas(LISTA));
        assertEquals(List.of(), Caraguatatuba.marcas(null));
    }

    @Test
    void listaComMaisDeUmaColunaDeDataFicaFora() {
        String duasColunas = LISTA.replace("<td style=\"text-align:center;\">09/06</td>",
                "<td style=\"text-align:center;\">09/06</td><td style=\"text-align:center;\">16/06</td>");
        assertEquals(List.of(), Caraguatatuba.marcas(duasColunas), "a página não diz o ano nem a sessão de cada coluna");
    }

    @Test
    void presencaFaltaJustificadaEFaltaViramEventoELicencaNao() {
        assertEquals("registrada", Caraguatatuba.PRESENCAS.get("P"));
        assertEquals("ausencia_justificada", Caraguatatuba.PRESENCAS.get("FJ"));
        assertEquals("falta", Caraguatatuba.PRESENCAS.get("F"));
        assertNull(Caraguatatuba.PRESENCAS.get("L"));
        Evento e = Caraguatatuba.evento(new SessaoDaLista("60", "Sessão Ordinária", 19, LocalDate.of(2026, 6, 9)), "falta", PROV);
        assertEquals("presenca-60", e.idFonte());
        assertEquals("2026-06-09", e.data());
        assertEquals("Sessão Ordinária", e.tipo());
        assertEquals("Sessão Ordinária nº 19", e.titulo());
        assertEquals("Plenário", e.orgao());
        assertEquals("falta", e.presenca());
    }

    @Test
    void listaAntigaPreenchidaFicaNoCacheERecenteOuVaziaRenova() {
        assertEquals(Politica.SE_AUSENTE, Caraguatatuba.politicaDaLista(LocalDate.of(2026, 6, 9), HOJE, false));
        assertEquals(Politica.SEMANAL, Caraguatatuba.politicaDaLista(LocalDate.of(2026, 6, 9), HOJE, true), "sem marcação: a Câmara ainda pode preencher");
        assertEquals(Politica.SEMANAL, Caraguatatuba.politicaDaLista(LocalDate.of(2026, 11, 10), HOJE, false));
    }

    @Test
    void nomeIdenticoSemAcentoESemCaixaLigaAoVereador() {
        Nomes nomes = new Nomes();
        nomes.ficha("901", fichaDe("Fulano do Bairro", "Fulano de Tal Exemplo"));
        nomes.ficha("902", fichaDe("Dra. Beltrana", "Beltrana Exemplo de Souza"));
        assertEquals("901", nomes.deVereador("FULANO DE TAL EXEMPLO"));
        assertEquals("902", nomes.deVereador("Beltrâna Exemplo de Souza"));
        assertEquals("902", nomes.deVereador("Dra Beltrana"), "nome parlamentar, sem a pontuação");
        assertNull(nomes.deVereador("Outra Pessoa Qualquer"));
        assertNull(nomes.deVereador(null));
        assertEquals("901", nomes.deBeneficiario("Fulano de Tal Exemplo", "-"), "nome civil idêntico vale sem cargo");
        assertNull(nomes.deBeneficiario("Fulano do Bairro", "VEREADOR"), "apelido não liga diária");
        assertEquals(List.of(), nomes.ligadosPorGrafia());
    }

    @Test
    void grafiaProximaValeSoOndeTodosSaoVereadoresEPassaAValerNasDiarias() {
        Nomes nomes = new Nomes();
        nomes.ficha("901", fichaDe("Otávio Exemplo", "Otávio Bertollini Exemplo"));
        assertNull(nomes.deBeneficiario("OTAVIO BERTOLINI EXEMPLO", "-"), "diária não liga por grafia próxima");
        assertEquals("901", nomes.deVereador("Otávio Bertolini Exemplo"), "uma letra de diferença numa palavra que não é a primeira");
        assertEquals(List.of("Otávio Bertolini Exemplo"), nomes.ligadosPorGrafia());
        assertEquals("901", nomes.deBeneficiario("OTAVIO BERTOLINI EXEMPLO", "-"), "a grafia do portal passou a ser nome do vereador");
    }

    @Test
    void grafiaProximaNaoConfundeParenteNemNomeCurto() {
        assertTrue(Nomes.grafiaProxima("OTAVIO BERTOLLINI EXEMPLO", "OTAVIO BERTOLINI EXEMPLO"));
        assertTrue(Nomes.grafiaProxima("HELENA CRISTINA DE MORAIS", "HELENA CRISTINA DE MORAES"));
        assertFalse(Nomes.grafiaProxima("ROBERTO MENDES EXEMPLO", "ROBERTA MENDES EXEMPLO"), "primeiro nome diferente é outra pessoa");
        assertFalse(Nomes.grafiaProxima("JOSE DA SILVA LIMA", "JOSE DA SILVA LAMA"), "palavra curta não conta");
        assertFalse(Nomes.grafiaProxima("CARLOS ALBERTO EXEMPLO", "CARLOS ALBERTO EXEMPLO JUNIOR"), "número de palavras diferente");
        assertFalse(Nomes.grafiaProxima("ANA MARIA FERREIRA SOUZA", "ANA MARTA FERREIRO SOUZA"), "duas palavras diferentes");
        assertFalse(Nomes.grafiaProxima("FULANO EXEMPLAR", "FULANO DIFERENTE"));
        assertEquals(1, Nomes.distancia("BERTOLINI", "BERTOLLINI"));
        assertEquals(3, Nomes.distancia("KITTEN", "SITTING"));
    }

    @Test
    void doisVereadoresPossiveisNinguemLeva() {
        Nomes nomes = new Nomes();
        nomes.ficha("901", fichaDe("Um", "Paulo Ferreira Santos"));
        nomes.ficha("902", fichaDe("Dois", "Paulo Ferreyra Santos"));
        assertNull(nomes.deVereador("Paulo Ferreiro Santos"), "grafia próxima de dois vereadores");
        Nomes homonimos = new Nomes();
        homonimos.ficha("901", fichaDe("Um", "Maria Exemplo Lima"));
        homonimos.ficha("902", fichaDe("Dois", "Maria Exemplo Lima"));
        assertNull(homonimos.deVereador("Maria Exemplo Lima"));
        assertNull(homonimos.deBeneficiario("Maria Exemplo Lima", "VEREADORA"));
    }

    @Test
    void sobrenomeAMaisValeSoNaDiariaComCargoDeVereador() {
        Nomes nomes = new Nomes();
        nomes.ficha("901", fichaDe("Helena", "Helena Cristina de Exemplo"));
        nomes.ficha("902", fichaDe("Beto", "Carlos Alberto de Exemplo"));
        assertEquals("901", nomes.deBeneficiario("HELENA CRISTINA DE EXEMPLO SANT ANNA", "VEREADORA"));
        assertEquals("901", nomes.deBeneficiario("Helena Cristina de Exemplo Sant´Anna", "Presidente da Câmara"));
        assertNull(nomes.deBeneficiario("HELENA CRISTINA DE EXEMPLO SANT ANNA", "-"), "sem cargo de vereador, só o nome idêntico");
        assertNull(nomes.deBeneficiario("HELENA CRISTINA DE EXEMPLO SANT ANNA", "AGENTE ADMINISTRATIVO"));
        assertNull(nomes.deBeneficiario("CARLOS ALBERTO DE EXEMPLO JUNIOR", "VEREADOR"), "Júnior é outra pessoa");
        assertNull(nomes.deVereador("Helena Cristina de Exemplo Sant Anna"), "na lista de presença, sobrenome a mais não liga");
    }

    @Test
    void cargoDeVereadorOuDePresidenteDaCamara() {
        assertTrue(Caraguatatuba.cargoDeVereador("VEREADORA"));
        assertTrue(Caraguatatuba.cargoDeVereador("Vereador"));
        assertTrue(Caraguatatuba.cargoDeVereador("PRESIDENTE"));
        assertTrue(Caraguatatuba.cargoDeVereador("PRESIDENTE DA CÂMARA CARAGUATATUBA"));
        assertFalse(Caraguatatuba.cargoDeVereador("PRESIDENTE DA COMISSÃO DE LICITAÇÃO"));
        assertFalse(Caraguatatuba.cargoDeVereador("-"));
        assertFalse(Caraguatatuba.cargoDeVereador(null));
    }

    @Test
    void soDiariaDeVereadorVaiParaOCacheSemNomeESemMatricula() throws Exception {
        Nomes nomes = new Nomes();
        nomes.ficha("901", fichaDe("Fulano do Bairro", "Fulano de Tal Exemplo"));
        JsonNode resposta = Json.FONTE.readTree("""
                {"cliente": {"nome": "Câmara"}, "diarias": [
                  {"ds_beneficiario": "FULANO DE TAL EXEMPLO", "chapa": "123", "cargo_beneficiario": "-", "nr_empenho": "626/2025",
                   "motivo": "Reunião", "destino": "São Paulo/SP - Brasil", "qtde": "1", "valor": 550.0,
                   "dt_concessao": "2025-12-15", "dt_empenho": "2025-12-15", "dt_saida": "2025-12-16", "dt_retorno": "2025-12-16"},
                  {"ds_beneficiario": "SERVIDORA FICTICIA DA SILVA", "chapa": "456", "cargo_beneficiario": "AGENTE DE SERVIÇOS EXTERNOS",
                   "nr_empenho": "20/2025", "valor": 100.0, "dt_concessao": "2025-01-16", "dt_empenho": "2025-01-16", "dt_saida": "2025-01-14"},
                  {"ds_beneficiario": "PESSOA SEM PERFIL", "chapa": "789", "cargo_beneficiario": "VEREADORA",
                   "nr_empenho": "30/2025", "valor": 150.0, "dt_concessao": "2025-02-01", "dt_empenho": "2025-02-01", "dt_saida": "2025-02-01"}
                ]}
                """);
        JsonNode derivado = Caraguatatuba.diariasDeVereadores(resposta, nomes);
        String texto = derivado.toString();
        assertFalse(texto.contains("FULANO") || texto.contains("SERVIDORA") || texto.contains("PESSOA SEM PERFIL"), "nenhum nome vai para o cache");
        assertFalse(texto.contains("chapa") || texto.contains("123"), "matrícula não vai para o cache");
        assertFalse(texto.contains("qtde") || texto.contains("dt_retorno"), "o que o projeto não publica não é gravado");
        assertEquals(Caraguatatuba.FORMATO_DAS_DIARIAS, derivado.get("formato").asInt());
        assertEquals(1, derivado.get("diarias").size());
        assertEquals("901", derivado.get("diarias").get(0).get("parlamentar").stringValue());
        assertEquals(3, derivado.get("total_na_fonte").asInt());
        assertEquals(1, derivado.get("de_vereador_sem_perfil").asInt(), "cargo de vereador e nome que não é de vereador: contada e fora");

        Despesa despesa = Caraguatatuba.diaria(derivado.get("diarias").get(0), PROV);
        assertEquals(LocalDate.of(2025, 12, 15), despesa.data(), "data de concessão");
        assertEquals(2025, despesa.ano());
        assertEquals(12, despesa.mes());
        assertEquals("Diária", despesa.categoria());
        assertEquals(0, new BigDecimal("550").compareTo(despesa.valor()));
        assertEquals("Empenho 626/2025", despesa.documentoNumero());
        assertNull(despesa.fornecedorNome());
        assertEquals("Destino: São Paulo/SP - Brasil. Motivo: Reunião.", despesa.descricao());
        JsonNode publicada = Json.arvore(despesa);
        assertEquals("Destino: São Paulo/SP - Brasil. Motivo: Reunião.", publicada.get("descricao").stringValue());
    }

    @Test
    void descricaoDaDiariaTrazDestinoEMotivoSemCampoVazioESemDadoPessoal() {
        assertEquals("Destino: São Paulo/SP - Brasil. Motivo: Reunião de exemplo.",
                Caraguatatuba.descricaoDaDiaria("São Paulo/SP - Brasil", "Reunião de exemplo."));
        assertEquals("Destino: Lorena/SP - Brasil.", Caraguatatuba.descricaoDaDiaria("LORENA/SP - BRASIL".replace("LORENA", "Lorena").replace("BRASIL", "Brasil"), "-"));
        assertEquals("Motivo: Curso de exemplo.", Caraguatatuba.descricaoDaDiaria(null, "Curso de exemplo"));
        assertNull(Caraguatatuba.descricaoDaDiaria(" - ", ""));
        assertNull(Caraguatatuba.descricaoDaDiaria(null, null));
        assertEquals("Motivo: Contato com o gabinete pelo e pelo.",
                Caraguatatuba.descricaoDaDiaria(null, "Contato com o gabinete pelo (12) 99999-8888 e pelo fulano@exemplo.invalid"), "telefone e e-mail saem do texto");
    }

    @Test
    void despesaSemDescricaoNaoGanhaOCampoNoJson() {
        Despesa semDescricao = new Despesa(LocalDate.of(2026, 3, 2), 2026, 3, "Combustível", null, null, new BigDecimal("10.00"), null, null, PROV);
        assertNull(semDescricao.descricao());
        assertFalse(Json.arvore(semDescricao).has("descricao"), "as demais casas publicam a despesa como antes");
    }

    @Test
    void copiaDerivadaDeFormatoAnteriorERefeitaNaHora() {
        assertEquals(Politica.SEMANAL, Caraguatatuba.politicaDasDiarias(Caraguatatuba.FORMATO_DAS_DIARIAS));
        assertEquals(Politica.SEMPRE, Caraguatatuba.politicaDasDiarias(1), "cópia sem destino e motivo");
        assertEquals(Politica.SEMPRE, Caraguatatuba.politicaDasDiarias(0), "sem cópia");
    }

    @Test
    void respostaDeDiariasSemAListaNaoSubstituiOCache() {
        assertThrows(IOException.class, () -> Caraguatatuba.diariasDeVereadores(Json.FONTE.readTree("{\"status\":\"500\"}"), new Nomes()));
    }

    @Test
    void diariaSemValorOuSemDataFicaFora() throws Exception {
        assertNull(Caraguatatuba.diaria(Json.FONTE.readTree("{\"parlamentar\":\"901\",\"valor\":null,\"dt_concessao\":\"2025-12-15\"}"), PROV));
        assertNull(Caraguatatuba.diaria(Json.FONTE.readTree("{\"parlamentar\":\"901\",\"valor\":10.0}"), PROV));
        Despesa pelaSaida = Caraguatatuba.diaria(Json.FONTE.readTree("{\"parlamentar\":\"901\",\"valor\":10.0,\"dt_saida\":\"2026-03-02\"}"), PROV);
        assertEquals(LocalDate.of(2026, 3, 2), pelaSaida.data());
        assertNull(pelaSaida.documentoNumero());
        assertNull(pelaSaida.descricao(), "cópia derivada antiga, sem destino e motivo: a despesa sai sem descrição");
    }

    @Test
    void anosDeMandatoFicamDentroDaCoberturaEDoAnoCorrente() {
        Ficha f = Caraguatatuba.ficha(FICHA);
        assertEquals(List.of(2022, 2023, 2024, 2025, 2026), Caraguatatuba.anosDeMandato(f.mandatos(), HOJE));
        List<MandatoDaFicha> ateOPrimeiroDia = List.of(new MandatoDaFicha(18, true, LocalDate.of(2021, 1, 1), LocalDate.of(2025, 1, 1)));
        assertEquals(List.of(2022, 2023, 2024), Caraguatatuba.anosDeMandato(ateOPrimeiroDia, HOJE), "mandato que termina em 1º de janeiro não alcança o ano novo");
        List<MandatoDaFicha> antigo = List.of(new MandatoDaFicha(18, true, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 5, 29)));
        assertEquals(List.of(), Caraguatatuba.anosDeMandato(antigo, HOJE));
        assertEquals(List.of(), Caraguatatuba.mandatosCobertos(antigo), "mandato encerrado antes de 2022 fica fora da cobertura");
    }

    @Test
    void emExercicioPedeAMarcaDaFonteEMandatoVigente() {
        Ficha f = Caraguatatuba.ficha(FICHA);
        assertTrue(Caraguatatuba.emExercicio(f, HOJE));
        assertFalse(Caraguatatuba.emExercicio(f, LocalDate.of(2024, 6, 1)), "entre o fim de um mandato e o início do outro");
        Ficha inativo = Caraguatatuba.ficha(FICHA.replace("Em exercício", "Inativo"));
        assertFalse(Caraguatatuba.emExercicio(inativo, HOJE));
        assertTrue(Caraguatatuba.emMandato(f.mandatos(), LocalDate.of(2024, 3, 31)));
        assertFalse(Caraguatatuba.emMandato(f.mandatos(), LocalDate.of(2024, 4, 1)));
        assertFalse(Caraguatatuba.emMandato(f.mandatos(), null));
    }

    @Test
    void situacaoDoPerfilEDoMandato() {
        assertEquals("Em exercício", Caraguatatuba.situacao(true, LocalDate.of(2029, 1, 1), HOJE));
        assertEquals("Mandato encerrado em 2024-12-31", Caraguatatuba.situacao(false, LocalDate.of(2024, 12, 31), HOJE));
        assertEquals("Fora de exercício", Caraguatatuba.situacao(false, LocalDate.of(2029, 1, 1), HOJE));
        MandatoDaFicha atual = new MandatoDaFicha(19, true, LocalDate.of(2025, 1, 1), LocalDate.of(2029, 1, 1));
        assertEquals("Em exercício", Caraguatatuba.situacaoDoMandato(atual, true, HOJE));
        assertEquals("Fora de exercício", Caraguatatuba.situacaoDoMandato(atual, false, HOJE));
        assertEquals("Mandato encerrado em 2024-12-31",
                Caraguatatuba.situacaoDoMandato(new MandatoDaFicha(18, true, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 12, 31)), true, HOJE));
    }

    @Test
    void partidoEODaFiliacaoVigenteNaData() {
        Ficha f = Caraguatatuba.ficha(FICHA);
        assertEquals("AGIR", Caraguatatuba.partidoEm(f.filiacoes(), HOJE));
        assertEquals("ABC", Caraguatatuba.partidoEm(f.filiacoes(), LocalDate.of(2024, 3, 31)));
        assertNull(Caraguatatuba.partidoEm(f.filiacoes(), LocalDate.of(2015, 1, 1)));
        assertEquals("SDD", Caraguatatuba.sigla(" SDD "));
        assertEquals("União", Caraguatatuba.sigla("União"));
        assertNull(Caraguatatuba.sigla(null));
    }

    @Test
    void unidadeEleitoralEMunicipioNoTceSp() {
        assertEquals("3510500", Tse.UES_MUNICIPAIS.get("63118").ibge());
        assertTrue(Tse.cargoValido(java.util.Map.of("DS_CARGO", "VEREADOR", "SG_UE", "63118", "ANO_ELEICAO", "2020")));
        assertTrue(Tse.cargoValido(java.util.Map.of("DS_CARGO", "PREFEITO", "SG_UE", "63118", "ANO_ELEICAO", "2024")));
        assertEquals("caraguatatuba", TceSp.MUNICIPIOS.get(Caraguatatuba.IBGE));
        assertEquals("https://transparencia.tce.sp.gov.br/api/json/despesas/caraguatatuba/2025/01",
                TceSp.url(Caraguatatuba.IBGE, java.time.YearMonth.of(2025, 1)));
    }
}
