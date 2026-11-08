package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
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
import etl.Registros.Despesa;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class AleceTest {

    /** CSV sintético da verba de desempenho parlamentar, na forma da fonte: linha inicial vazia com BOM e cabeçalho. */
    private static final String VERBA = "﻿;;;\n"
            + "DEPUTADO;PERIODO;EMPENHO;DESCRICAO;CNPJ;CREDOR;VALOR\n"
            + "DEP FULANA DE TAL;05/2025;2025NE000001;SERVICOS DE CONSULTORIA;11.222.333/0001-81;EMPRESA EXEMPLO LTDA;10.000,00\n"
            + "DEP FULANA DE TAL;05/2025;2025NE000002;REEMBOLSO; INTERNET;123.456.789-01;FULANA DE TAL SILVA;109,75\n"
            + "DEP SEM PERFIL;05/2025;2025NE000003;COMBUSTIVEL;11.222.333/0001-81;POSTO EXEMPLO;50,00\n"
            + "DEP. FULANA DE TAL - SERVICOS POSTAIS;05/2025;2025NE000004;POSTAGEM;11.222.333/0001-81;CORREIO EXEMPLO;20,00\n";

    @Test
    void listaDeVotacoesTrazDataMateriaEPlanilha() {
        String html = "<tr> <td> 31.ª LEGISLATURA </td> <td> 4.ª SESSÃO LEGISLATIVA </td> <td> 85.ª SESSÃO ORDINÁRIA </td>"
                + " <td> 03/11/2026 </td> <td> 92/2026 - Autoria do Poder Executivo - Autoriza transferência. </td>"
                + " <td> <a href=\"https://transparencia.al.ce.gov.br/uploads/votacao_nominal_materia/abc.pdf\"></a>"
                + " <a href=\"https://transparencia.al.ce.gov.br/uploads/votacao_nominal_materia_csv/abc.xlsx\"></a> </td> </tr>";

        List<Alece.VotacaoListada> lista = Alece.votacoesDaLista(html);

        assertEquals(1, lista.size());
        assertEquals(java.time.LocalDate.of(2026, 11, 3), lista.getFirst().data());
        assertEquals("85.ª SESSÃO ORDINÁRIA", lista.getFirst().sessao());
        assertEquals("92/2026 - Autoria do Poder Executivo - Autoriza transferência.", lista.getFirst().materia());
        assertEquals("https://transparencia.al.ce.gov.br/uploads/votacao_nominal_materia_csv/abc.xlsx", lista.getFirst().planilha());
    }

    @Test
    void planilhaDeVotosDizOVotoPelaColunaMarcada() {
        List<Map<String, String>> linhas = List.of(
                Map.of("A", "RELATÓRIO"),
                Map.of("A", "Nº", "B", "DEPUTADOS", "C", "PARTIDOS", "D", "VOTAÇÃO"),
                Map.of("D", "SIM", "E", "NÃO", "F", "ABSTENÇÃO"),
                Map.of("A", "1", "B", "FULANA DE TAL", "C", "AAA", "D", "SIM (P)"),
                Map.of("A", "2", "B", "SICRANO", "C", "BBB", "E", "NÃO (V)"),
                Map.of("A", "3", "B", "BELTRANA", "C", "CCC"));

        List<Alece.VotoNaPlanilha> votos = Alece.votosDaPlanilha(linhas);

        assertEquals(3, votos.size());
        assertEquals("Sim", votos.get(0).voto());
        assertEquals("AAA", votos.get(0).partido());
        assertEquals("Não", votos.get(1).voto());
        // Deputado listado sem coluna marcada: sem voto registrado.
        assertEquals(null, votos.get(2).voto());
    }

    @Test
    void verbaDoMesGuardaSoCnpjEDescartaNomeSemPerfil() {
        Prov prov = new Prov(Alece.FONTE_DESPESAS, Alece.urlDespesas(2025, 5), Instant.parse("2026-11-06T12:00:00Z"));
        int[] descartes = {0};

        Map<String, List<Despesa>> porSlug = Alece.despesasDoMes(VERBA, Map.of("FULANA DE TAL", "fulana-de-tal"), prov, descartes);

        List<Despesa> despesas = porSlug.get("fulana-de-tal");
        // O complemento " - SERVICOS POSTAIS" junto ao nome não impede o vínculo.
        assertEquals(3, despesas.size());
        assertEquals("FULANA DE TAL", Alece.chaveDoNome("DEP. FULANA DE TAL."));
        assertEquals("DRA SILVANA", Alece.chaveDoNome("Dra. Silvana"));
        assertEquals("DRA SILVANA", Alece.chaveDoNome("DEP DRA SILVANA POR SOLICITACAO DA DEPUTADA"));
        Despesa consultoria = despesas.get(0);
        assertEquals(2025, consultoria.ano());
        assertEquals(5, consultoria.mes());
        assertEquals(Alece.CATEGORIA_DESPESAS, consultoria.categoria());
        assertEquals("11222333000181", consultoria.fornecedorCnpj());
        assertEquals(new java.math.BigDecimal("10000.00"), consultoria.valor());
        assertEquals("2025NE000001", consultoria.documentoNumero());
        // Um ";" na descrição não desloca o valor; o CPF do reembolso não fica.
        Despesa reembolso = despesas.get(1);
        assertEquals(new java.math.BigDecimal("109.75"), reembolso.valor());
        assertEquals(null, reembolso.fornecedorCnpj());
        assertEquals(1, descartes[0]);
        assertEquals("https://transparencia.al.ce.gov.br/despesas/verba-desempenho-parlamentar/csv?mes=05&ano=2025", prov.urlOrigem());
    }

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Agenor Neto", Alece.formatarNome("AGENOR NETO"));
        assertEquals("Alcides Fernandes", Alece.formatarNome("ALCIDES FERNANDES"));
        assertEquals("Davi de Raimundão", Alece.formatarNome("DAVI DE RAIMUNDÃO"));
        assertEquals("De Assis Diniz", Alece.formatarNome("DE ASSIS DINIZ"));
        assertEquals("Guilherme Landim", Alece.formatarNome("GUILHERME LANDIM"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <div class="main_page deputado_page container">
                    <div
                        class="deputado_card "
                        style="max-width: 300px"
                    >
                        <img
                            width="100%"
                            height="230"
                            style="object-fit: cover"
                            src="https://www.al.ce.gov.br/image/389109"
                            alt="Foto de Agenor Neto"
                        >

                        <p class="deputado_card--nome s-font-1"><a href="https://www.al.ce.gov.br/deputados/agenor-neto">                            Agenor Neto </a></p>
                        <p class="deputado_card--partido s-font-1"> MDB </p>
                    </div>
                    <div
                        class="deputado_card licenciado"
                        style="max-width: 300px"
                    >
                        <img
                            width="100%"
                            height="230"
                            style="object-fit: cover"
                            src="https://www.al.ce.gov.br/image/392721"
                            alt="Foto de Alcides Fernandes"
                        >

                        <p class="deputado_card--nome s-font-1"><a href="https://www.al.ce.gov.br/deputados/alcides-fernandes">                            Alcides Fernandes </a></p>
                        <p class="deputado_card--partido s-font-1"> PL </p>
                    </div>
                </div>
                """;

        List<Alece.DeputadoBruto> deputados = Alece.extrairDeputadosDoHtml(html);
        assertEquals(2, deputados.size());

        Alece.DeputadoBruto d1 = deputados.get(0);
        assertEquals("agenor-neto", d1.slug());
        assertEquals("Agenor Neto", d1.nome());
        assertEquals("MDB", d1.partido());
        assertEquals("https://www.al.ce.gov.br/image/389109", d1.fotoUrl());

        Alece.DeputadoBruto d2 = deputados.get(1);
        assertEquals("alcides-fernandes", d2.slug());
        assertEquals("Alcides Fernandes", d2.nome());
        assertEquals("PL", d2.partido());
        assertEquals("https://www.al.ce.gov.br/image/392721", d2.fotoUrl());
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-05T12:00:00Z"), null);

        Path rawAlece = ctx.raw().resolve("alece");
        Files.createDirectories(rawAlece.resolve("perfis"));

        String html = """
                <div class="main_page deputado_page container">
                    <div
                        class="deputado_card "
                        style="max-width: 300px"
                    >
                        <img
                            width="100%"
                            height="230"
                            style="object-fit: cover"
                            src="https://www.al.ce.gov.br/image/389109"
                            alt="Foto de Agenor Neto"
                        >

                        <p class="deputado_card--nome s-font-1"><a href="https://www.al.ce.gov.br/deputados/agenor-neto">Agenor Neto</a></p>
                        <p class="deputado_card--partido s-font-1"> MDB </p>
                    </div>
                </div>
                """;

        Files.writeString(rawAlece.resolve("deputados.html"), html);
        gravarMetadado(rawAlece, "deputados.html", "https://www.al.ce.gov.br/deputados");

        Alece alece = new Alece();
        alece.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alece/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertEquals(1, pessoasNode.size());

        JsonNode p1 = pessoasNode.get(0);
        assertEquals("alece", p1.get("fonte").stringValue());
        assertEquals("agenor-neto", p1.get("id_fonte").stringValue());
        assertEquals("Agenor Neto", p1.get("nome").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("CE", p1.get("uf").stringValue());
        assertEquals("MDB", p1.get("partido").stringValue());
        assertEquals("https://www.al.ce.gov.br/image/389109", p1.get("foto_url").stringValue());
        assertTrue(p1.get("em_exercicio").booleanValue());

        Path blocoAgenor = ctx.normalizado().resolve("alece/blocos/agenor-neto.json");
        assertTrue(Files.exists(blocoAgenor));
        JsonNode bAgenor = Json.ler(blocoAgenor);
        assertNotNull(bAgenor.get("mandatos"));
        assertEquals(1, bAgenor.get("mandatos").size());

        JsonNode m1 = bAgenor.get("mandatos").get(0);
        assertEquals("2023-02-01", m1.get("inicio").stringValue());
        assertEquals("2027-01-31", m1.get("fim").stringValue());
        assertEquals(31, m1.get("legislatura").asInt());
    }

    @Test
    void extraiComissoesDeFragmentoHtml() {
        String html = """
                <h1>Composição - Agropecuária</h1>
                <table>
                    <tbody>
                        <tr>
                            <td>&nbsp;</td>
                            <td colspan="2"><p><span><strong>PRESIDENTE</strong></span></p></td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <p><span><strong>Titular:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/missias-dias">Missias Dias, PT</a>
                            </td>
                            <td>
                                <p><span><strong>Suplente:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/bruno-pedrosa">Bruno Pedrosa, PT</a>
                            </td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td colspan="2"><p><span><strong>VICE-PRESIDENTE</strong></span></p></td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <p><span><strong>Titular:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/agenor-neto">Agenor Neto, MDB</a>
                            </td>
                            <td>&nbsp;</td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td colspan="2"><p><span><strong>MEMBROS</strong></span></p></td>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <p><span><strong>Titular:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/salmito">Salmito, PSB</a>
                            </td>
                            <td>
                                <p><span><strong>Suplente:</strong></span></p>
                                <a href="https://www.al.ce.gov.br/deputados/simao-pedro">Simão Pedro, PSD</a>
                            </td>
                            <td>&nbsp;</td>
                        </tr>
                    </tbody>
                </table>
                """;

        Prov prov = new Prov("alece-deputados-detalhes", "https://www.al.ce.gov.br/comissoes-permanentes/agropecuaria/composicao", Instant.parse("2026-11-05T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> resultado = Alece.extrairComissoesDoHtml(html, "agropecuaria", prov);

        assertEquals(5, resultado.size());
        assertEquals("Presidente (Titular)", resultado.get("missias-dias").get(0).papel());
        assertEquals("Presidente (Suplente)", resultado.get("bruno-pedrosa").get(0).papel());
        assertEquals("Vice-Presidente (Titular)", resultado.get("agenor-neto").get(0).papel());
        assertEquals("Membro", resultado.get("salmito").get(0).papel());
        assertEquals("Suplente", resultado.get("simao-pedro").get(0).papel());
        assertEquals("Comissão de Agropecuária", resultado.get("missias-dias").get(0).nome());
        assertEquals("comissao", resultado.get("missias-dias").get(0).tipo());
    }

    @Test
    void extraiProposicoesDeFragmentoHtml() {
        String html = """
                <table border='0' width='95%'>
                  <tr><td>
                    <table border='0' cellspacing='3'>
                      <tr>
                        <td width='15%' bgcolor='#EFEFEF'><b>N&ordm; do Proj.:</b></font><br><font face='Verdana' size='1'>1/23 </font></td>
                        <td width='55%' bgcolor='#EFEFEF'><font face='Verdana' size='1'><b>Autor:</b></font><br><font face='Verdana' size='1'>RENATO ROSENO</font></td>
                        <td width='16%' bgcolor='#EFEFEF'><font face='Verdana' size='1'><b>Entrada:</b></font><br><font face='Verdana' size='1'>02.02.23</font></td>
                      </tr>
                    </table>
                  </td></tr>
                  <tr><td>
                    <table border='0' cellspacing='3'>
                      <tr>
                        <td width='100%' bgcolor='#EFEFEF'><b>Ementa:</b></font><br><font face='Verdana' size='1'><a href='https://www2.al.ce.gov.br/legislativo/tramit2023/pl1_23.htm'>ACRESCE DISPOSITIVO &Agrave; LEI DO IPVA.</a></font></td>
                      </tr>
                    </table>
                  </td></tr>
                </table>
                """;

        List<Alece.ProposicaoBruta> lista = Alece.extrairProposicoesDoHtml(html);
        assertEquals(1, lista.size());
        assertEquals("1/23", lista.getFirst().numeroCompleto());
        assertEquals("RENATO ROSENO", lista.getFirst().autor());
        assertEquals("02.02.23", lista.getFirst().dataEntrada());
        assertEquals("https://www2.al.ce.gov.br/legislativo/tramit2023/pl1_23.htm", lista.getFirst().urlTramitacao());
        assertEquals("ACRESCE DISPOSITIVO À LEI DO IPVA.", lista.getFirst().ementa());
    }

    @Test
    void detectaTotalDePaginasDeProposicoes() {
        String html = "<b>Página 1 de 124: </b><b>[ </b><b>1</b> <a href='...'>2</a>";
        assertEquals(124, Alece.totalPaginasDoHtml(html));

        String htmlSemPaginacao = "N&ordm; do Proj.: 1/23";
        assertEquals(1, Alece.totalPaginasDoHtml(htmlSemPaginacao));

        assertEquals(0, Alece.totalPaginasDoHtml(""));
    }

    @Test
    void lePaginaDeProposicaoComByteWindows1252NoMeioDeUtf8(@TempDir Path tempDir) throws Exception {
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(tempDir, dados, null, null, Instant.parse("2026-11-08T12:00:00Z"), null);
        Path rawAlece = ctx.raw().resolve("alece");
        Files.createDirectories(rawAlece.resolve("proposicoes"));

        String modelo = """
                \uFEFF<b>Página 1 de 29: </b><b>[ </b><b>1</b>
                <table border='0' width='95%'>
                  <tr><td>
                    <table border='0' cellspacing='3'>
                      <tr>
                        <td width='15%' bgcolor='#EFEFEF'><b>N&ordm; do Proj.:</b></font><br><font face='Verdana' size='1'>1/23 </font></td>
                        <td width='55%' bgcolor='#EFEFEF'><font face='Verdana' size='1'><b>Autor:</b></font><br><font face='Verdana' size='1'>RENATO ROSENO</font></td>
                        <td width='16%' bgcolor='#EFEFEF'><font face='Verdana' size='1'><b>Entrada:</b></font><br><font face='Verdana' size='1'>02.02.23</font></td>
                      </tr>
                    </table>
                  </td></tr>
                  <tr><td>
                    <table border='0' cellspacing='3'>
                      <tr>
                        <td width='100%' bgcolor='#EFEFEF'><b>Ementa:</b></font><br><font face='Verdana' size='1'><a href='https://www2.al.ce.gov.br/legislativo/tramit2023/pl1_23.htm'>CIPDOWN EN_DASH 06&ordf; REUNIAO</a></font></td>
                      </tr>
                    </table>
                  </td></tr>
                </table>
                """;
        byte[] utf8 = modelo.getBytes(StandardCharsets.UTF_8);
        byte[] marcador = "EN_DASH".getBytes(StandardCharsets.UTF_8);
        int pos = indexOf(utf8, marcador);
        assertTrue(pos >= 0);
        byte[] html = new byte[utf8.length - marcador.length + 1];
        System.arraycopy(utf8, 0, html, 0, pos);
        html[pos] = (byte) 0x96;
        System.arraycopy(utf8, pos + marcador.length, html, pos + 1, utf8.length - pos - marcador.length);

        Path arquivo = rawAlece.resolve("proposicoes/projeto_lei-T-1.html");
        Files.write(arquivo, html);
        gravarMetadado(rawAlece, "proposicoes/projeto_lei-T-1.html",
                "https://www2.al.ce.gov.br/legislativo/proposicoes1/index.php?tabela=projeto_lei&opcao=T&absolutepage=1");

        assertThrows(MalformedInputException.class, () -> Files.readString(arquivo));

        String lida = Alece.lerPagina(arquivo);
        assertTrue(lida.contains("Página 1 de 29"));
        assertFalse(lida.contains("PÃ¡gina"));

        Coleta.Arquivo meta = Coleta.metadados(ctx, "alece", "proposicoes/projeto_lei-T-1.html");
        assertEquals(29, Alece.totalPaginasDeProposicoes(ctx, meta));

        Alece.DeputadoBruto deputado = new Alece.DeputadoBruto("renato-roseno", "RENATO ROSENO", "PSOL", null);
        Map<String, List<Proposicao>> porSlug = Alece.proposicoesPorSlug(ctx, List.of(deputado));
        List<Proposicao> props = porSlug.get("renato-roseno");
        assertNotNull(props);
        assertEquals(1, props.size());
        assertEquals("RENATO ROSENO", deputado.nome());
        assertTrue(props.getFirst().ementa().contains("CIPDOWN"));
        assertTrue(props.getFirst().ementa().contains("06ª REUNIAO")
                || props.getFirst().ementa().contains("06&ordf; REUNIAO")
                || props.getFirst().ementa().contains("REUNIAO"));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void gravarMetadado(Path pastaFonte, String relativo, String url) throws IOException {
        Path arquivo = pastaFonte.resolve(relativo);
        Path meta = arquivo.resolveSibling(arquivo.getFileName() + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alece/" + relativo, "hash-teste", 100, Instant.now());
        Json.gravar(meta, arq, true);
    }
}
