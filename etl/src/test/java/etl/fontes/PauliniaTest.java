package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Leitura das páginas do portal da Câmara Municipal de Paulínia (ADR-0066). Nomes sintéticos. */
class PauliniaTest {

    private static final String VEREADORES = """
            <html><body><header><a href="/contato">Fale conosco (19) 3333-4444</a></header>
            <main>
              <h2>Vereadores em Exercício</h2>
              <p><strong>15ª Legislatura &#8211; 01/01/2025 a 31/12/2028</strong></p>
              <div><p style="text-align: center;">PRTB</p><h4><a href="/ana-teste/">ANA TESTE</a></h4>
                <p><i class="fa fa-phone">&nbsp;</i>&nbsp;(19) 3874-0000</p></div>
              <div><p>DEMOCRACIA CRIST&Atilde;</p><h4>BETO EXEMPLO</h4>
                <p>(19) 3874-1111 e (19) 3874-2222</p><p>(19) 9 9999-0000 (com WhatsApp)</p>
                <p>beto@camara.exemplo.gov.br</p></div>
            </main>
            <footer>Rua Exemplo, 100</footer></body></html>
            """;

    @Test
    void linhasFicamSemTelefoneSemEmailESemOQueEstaForaDoMiolo() {
        List<String> linhas = Paulinia.linhas(VEREADORES);
        assertEquals(List.of("Vereadores em Exercício", "15ª Legislatura – 01/01/2025 a 31/12/2028", "PRTB", "ANA TESTE",
                "DEMOCRACIA CRISTÃ", "BETO EXEMPLO"), linhas);
    }

    @Test
    void composicaoLeACabecalhoDaLegislaturaEOsParesDePartidoENome() {
        Paulinia.Composicao composicao = Paulinia.composicao(Paulinia.linhas(VEREADORES));
        assertEquals(15, composicao.legislatura());
        assertEquals(LocalDate.of(2025, 1, 1), composicao.inicio());
        assertEquals(LocalDate.of(2028, 12, 31), composicao.fim());
        assertEquals("15ª Legislatura – 01/01/2025 a 31/12/2028", composicao.cabecalho());
        assertEquals(List.of(new Paulinia.EmExercicio("ANA TESTE", "PRTB"), new Paulinia.EmExercicio("BETO EXEMPLO", "DEMOCRACIA CRISTÃ")),
                composicao.vereadores());
    }

    @Test
    void paginaForaDoFormatoNaoViraVereador() {
        // Linha a mais depois da lista: os pares de partido e nome não fecham, e nada é lido.
        List<String> linhas = List.of("Vereadores em Exercício", "15ª Legislatura – 01/01/2025 a 31/12/2028", "PRTB", "ANA TESTE", "Aviso");
        assertTrue(Paulinia.composicao(linhas).vereadores().isEmpty());
        // Sem o cabeçalho da legislatura não há período, e nada é lido.
        assertTrue(Paulinia.composicao(List.of("Vereadores", "PRTB", "ANA TESTE")).vereadores().isEmpty());
        assertNull(Paulinia.composicao(List.of("Vereadores", "PRTB", "ANA TESTE")).legislatura());
    }

    @Test
    void legislaturasSeparamNomeCivilNomeParlamentarPartidoESuplente() {
        List<String> linhas = Paulinia.linhas("""
                <main><p><strong>15ª Legislatura 01/01/2025 a 31/12/2028</strong></p>
                <ul><li>Ana Maria Teste dos Santos – Ana Teste (PRTB)</li>
                <li>Roberto Exemplo Junior, Beto Exemplo (DC)<br /> Carla Amostra de Souza – Carla Amostra, Cacá (PL)</li>
                <li>Davi Modelo Lima (MDB)</li></ul>
                <p><strong>13ª Legislatura 01/01/2017 A 31/12/2020</strong></p>
                <ul><li>Eva da Farmácia (PP) – Suplente</li></ul></main>
                """);
        List<Paulinia.Legislatura> legislaturas = Paulinia.legislaturas(linhas);
        assertEquals(2, legislaturas.size());
        Paulinia.Legislatura atual = legislaturas.getFirst();
        assertEquals(15, atual.numero());
        assertEquals(LocalDate.of(2025, 1, 1), atual.inicio());
        assertEquals(List.of(
                new Paulinia.Membro("Ana Maria Teste dos Santos", List.of("Ana Teste"), "PRTB", false),
                new Paulinia.Membro("Roberto Exemplo Junior", List.of("Beto Exemplo"), "DC", false),
                new Paulinia.Membro("Carla Amostra de Souza", List.of("Carla Amostra", "Cacá"), "PL", false),
                new Paulinia.Membro("Davi Modelo Lima", List.of(), "MDB", false)), atual.membros());
        Paulinia.Legislatura antiga = legislaturas.get(1);
        assertEquals(LocalDate.of(2020, 12, 31), antiga.fim());
        assertTrue(antiga.membros().getFirst().suplente());
    }

    @Test
    void vereadorEmExercicioAchaALinhaDaLegislaturaPeloNomeOuPelasPalavrasDoNomeCivil() {
        List<Paulinia.Membro> membros = List.of(
                new Paulinia.Membro("Ana Maria Teste dos Santos", List.of("Ana Teste"), "PRTB", false),
                new Paulinia.Membro("Davi Modelo Lima", List.of(), "MDB", false),
                new Paulinia.Membro("Davi Modelo Prado", List.of(), "PL", false),
                new Paulinia.Membro("Lucas Amostra Barros", List.of(), "DC", false));
        assertEquals("Ana Maria Teste dos Santos", Paulinia.membroDe("ANA TESTE", membros).nomeCivil());
        // Sem nome parlamentar na página: todas as palavras do nome dentro de um único nome civil.
        assertEquals("Lucas Amostra Barros", Paulinia.membroDe("LUCAS BARROS", membros).nomeCivil());
        // Dois nomes civis possíveis: sem ligação.
        assertNull(Paulinia.membroDe("DAVI MODELO", membros));
        assertNull(Paulinia.membroDe("FULANO DE TAL", membros));
    }

    @Test
    void mesaEComissoesSaemEmTrincasDePartidoNomeECargo() {
        List<String> mesa = Paulinia.linhas("""
                <main><h2>Mesa Diretora</h2>
                <p>PRTB</p><h4><span>ANA TESTE<br /></span><span>Presidente</span></h4><p>(19) 3874-0000</p>
                <p>DC</p><h4><span>BETO EXEMPLO<br /></span><span>1º Secretário</span></h4>
                <p>Conforme Regimento Interno:</p><p>Art. 16 – À Mesa incumbe a direção dos trabalhos.</p></main>
                """);
        assertEquals(List.of(new Paulinia.Cargo(null, "ANA TESTE", "Presidente"), new Paulinia.Cargo(null, "BETO EXEMPLO", "1º Secretário")),
                Paulinia.mesa(mesa));

        List<String> comissoes = Paulinia.linhas("""
                <main><h2>Comissões Parlamentares</h2>
                <h3>Comissão de Legislação, Justiça e Redação</h3>
                <p>PRTB</p><h4><span>ANA TESTE<br /></span><span>Presidente</span></h4>
                <p>DC</p><h4><span>BETO EXEMPLO<br /></span><span>Secretário</span></h4>
                <h3>Comissão de Finanças, Orçamento e Tributos</h3>
                <p>DC</p><h4><span>BETO EXEMPLO<br /></span><span>Vice-Presidente</span></h4></main>
                """);
        assertEquals(List.of(
                new Paulinia.Cargo("Comissão de Legislação, Justiça e Redação", "ANA TESTE", "Presidente"),
                new Paulinia.Cargo("Comissão de Legislação, Justiça e Redação", "BETO EXEMPLO", "Secretário"),
                new Paulinia.Cargo("Comissão de Finanças, Orçamento e Tributos", "BETO EXEMPLO", "Vice-Presidente")),
                Paulinia.comissoes(comissoes));
    }

    @Test
    void identificadorVemDoNomeParlamentarSemAcento() {
        assertEquals("fabio-da-van", Paulinia.slug("FÁBIO DA VAN"));
        assertEquals("dr-teste", Paulinia.slug(" Dr. Teste "));
        assertNull(Paulinia.slug("  "));
    }

    @Test
    void retratoDoHistoricoGuardaLegislaturaEPeriodoEVoltaIgual() {
        Paulinia.Composicao composicao = Paulinia.composicao(Paulinia.linhas(VEREADORES));
        List<Paulinia.Legislatura> legislaturas = List.of(new Paulinia.Legislatura(15, LocalDate.of(2025, 1, 1), LocalDate.of(2028, 12, 31),
                List.of(new Paulinia.Membro("Ana Maria Teste dos Santos", List.of("Ana Teste"), "PRTB", false))));
        List<HistoricoDeputados.Snapshot> retratos = Paulinia.retratos(composicao, legislaturas);
        assertEquals(List.of("ana-teste", "beto-exemplo"), retratos.stream().map(HistoricoDeputados.Snapshot::id).toList());
        assertEquals("Ana Teste", retratos.getFirst().nome());
        Paulinia.Retrato ana = Paulinia.Retrato.de(retratos.getFirst().extra());
        assertEquals(15, ana.legislatura());
        assertEquals(LocalDate.of(2025, 1, 1), ana.inicio());
        assertTrue(ana.listado());
        assertEquals("Ana Maria Teste dos Santos", ana.nomeCivil());
        // Fora da página de legislaturas: sem nome civil e sem data de início garantida.
        Paulinia.Retrato beto = Paulinia.Retrato.de(retratos.get(1).extra());
        assertFalse(beto.listado());
        assertNull(beto.nomeCivil());
        assertNull(Paulinia.Retrato.de("texto fora do formato"));
    }
}
