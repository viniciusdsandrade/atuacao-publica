package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;

class ExecutivoSpTest {

    @Test
    void formataDataIsoCorretamente() {
        assertEquals("2023-05-10", ExecutivoSp.formatarDataIso("2023-05-10"));
        assertEquals("2024-08-25", ExecutivoSp.formatarDataIso("25/08/2024"));
        assertNull(ExecutivoSp.formatarDataIso("data-invalida"));
        assertNull(ExecutivoSp.formatarDataIso(null));
        assertNull(ExecutivoSp.formatarDataIso("   "));
    }

    @Test
    void formataHoraCorretamente() {
        assertEquals("09:30", ExecutivoSp.formatarHora("09:30"));
        assertEquals("09:30", ExecutivoSp.formatarHora("9:30"));
        assertEquals("14:15", ExecutivoSp.formatarHora("14h15"));
        assertEquals("08:00", ExecutivoSp.formatarHora("8h00"));
        assertEquals("11:00", ExecutivoSp.formatarHora("11h00 - 11h30"));
        assertEquals("15:30", ExecutivoSp.formatarHora("15h30 - 16h00"));
        assertEquals("09:00", ExecutivoSp.formatarHora("Dia inteiro"));
        assertEquals("09:00", ExecutivoSp.formatarHora(null));
    }

    @Test
    void converteColunaParaIndiceCorretamente() {
        assertEquals(0, ExecutivoSp.colunaParaIndice("A1"));
        assertEquals(1, ExecutivoSp.colunaParaIndice("B12"));
        assertEquals(25, ExecutivoSp.colunaParaIndice("Z5"));
        assertEquals(26, ExecutivoSp.colunaParaIndice("AA1"));
        assertEquals(-1, ExecutivoSp.colunaParaIndice(null));
    }

    @Test
    void processaCsvDeAgendaSanitizandoDadosPessoais(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("compromissos-2024.csv");
        String conteudo = """
                Data;Horário;Título;Local;Participantes
                2024-05-15;10:00;Reunião com Secretários;Palácio dos Bandeirantes;Secretário da Fazenda, Secretário da Saúde
                16/05/2024;14h30;Audiência com João 123.456.789-01;Sala de Reuniões;João da Silva
                """;
        Files.writeString(csv, conteudo);

        Coleta.Arquivo arq = new Coleta.Arquivo("https://spagendas.sp.gov.br", csv.toString(), "hash", 100, Instant.now());
        List<ExecutivoSp.Compromisso> lista = new ArrayList<>();
        ExecutivoSp.processarArquivoAgenda(csv, arq, lista);

        assertEquals(2, lista.size());

        ExecutivoSp.Compromisso c1 = lista.get(0);
        assertEquals("SP", c1.ente());
        assertEquals("2024-05-15", c1.data());
        assertEquals("10:00", c1.hora());
        assertEquals("Reunião com Secretários", c1.textoOficial());
        assertEquals("Palácio dos Bandeirantes", c1.local());
        assertNotNull(c1.participantes());
        assertEquals(2, c1.participantes().size());

        ExecutivoSp.Compromisso c2 = lista.get(1);
        assertEquals("2024-05-16", c2.data());
        assertEquals("14:30", c2.hora());
        // CPF no título deve ser removido pelo Guarda.semCpf
        assertEquals("Audiência com João", c2.textoOficial());
    }

    @Test
    void processaNormasEstaduaisDoExecutivo() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Normas>
                  <LegislacaoNorma>
                    <Data>2023-01-24T00:00:00-02:00</Data>
                    <Ementa>Atribui, ao Vice-Governador do Estado, a missão especial de coordenar</Ementa>
                    <IdNorma>206763</IdNorma>
                    <Numero>67457</Numero>
                    <IdTipo>3</IdTipo>
                    <Promulg>E</Promulg>
                    <URLFicha>http://www.al.sp.gov.br/norma/206763</URLFicha>
                  </LegislacaoNorma>
                  <LegislacaoNorma>
                    <Data>2024-05-28T00:00:00-03:00</Data>
                    <Ementa>Institui o Programa Escola Cívico-Militar 123.456.789-01</Ementa>
                    <IdNorma>210100</IdNorma>
                    <Numero>1398</Numero>
                    <IdTipo>2</IdTipo>
                    <Promulg>E</Promulg>
                    <URLFicha>http://www.al.sp.gov.br/norma/210100</URLFicha>
                  </LegislacaoNorma>
                  <LegislacaoNorma>
                    <Data>2025-03-12T00:00:00-03:00</Data>
                    <Ementa>Estabelece procedimento para serviços de entrega</Ementa>
                    <IdNorma>211200</IdNorma>
                    <Numero>18105</Numero>
                    <IdTipo>9</IdTipo>
                    <Promulg>E</Promulg>
                    <URLFicha>http://www.al.sp.gov.br/norma/211200</URLFicha>
                  </LegislacaoNorma>
                  <LegislacaoNorma>
                    <!-- Norma legislativa da Alesp: Promulg L deve ser ignorada -->
                    <Data>2023-03-06T00:00:00-03:00</Data>
                    <Ementa>Resolução da Alesp</Ementa>
                    <IdNorma>207000</IdNorma>
                    <Numero>935</Numero>
                    <IdTipo>19</IdTipo>
                    <Promulg>L</Promulg>
                  </LegislacaoNorma>
                  <LegislacaoNorma>
                    <!-- Norma anterior a 2022: deve ser ignorada -->
                    <Data>2020-05-10T00:00:00-03:00</Data>
                    <Ementa>Decreto antigo</Ementa>
                    <IdNorma>100000</IdNorma>
                    <Numero>55000</Numero>
                    <IdTipo>3</IdTipo>
                    <Promulg>E</Promulg>
                  </LegislacaoNorma>
                </Normas>
                """;
        Coleta.Arquivo arq = new Coleta.Arquivo("https://www.al.sp.gov.br", "legislacao_normas.zip", "hash", 100, Instant.now());
        List<ExecutivoSp.Ato> lista = new ArrayList<>();
        try (var in = new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            ExecutivoSp.processarNormasEstaduaisXml(in, arq, lista);
        }

        assertEquals(3, lista.size());

        ExecutivoSp.Ato dec = lista.stream().filter(a -> "DEC".equals(a.tipo())).findFirst().orElseThrow();
        assertEquals("SP", dec.ente());
        assertEquals("67457", dec.numero());
        assertEquals("2023-01-24", dec.data());
        assertEquals("Decreto nº 67457", dec.nome());
        assertEquals("sp-dec-67457", dec.idFonte());
        assertEquals("http://www.al.sp.gov.br/norma/206763", dec.prov().urlOrigem());

        ExecutivoSp.Ato lcp = lista.stream().filter(a -> "LCP".equals(a.tipo())).findFirst().orElseThrow();
        assertEquals("1398", lcp.numero());
        assertEquals("2024-05-28", lcp.data());
        assertEquals("Lei Complementar nº 1398", lcp.nome());
        // CPF sanitizado da ementa
        assertEquals("Institui o Programa Escola Cívico-Militar", lcp.ementa());

        ExecutivoSp.Ato lei = lista.stream().filter(a -> "LEI".equals(a.tipo())).findFirst().orElseThrow();
        assertEquals("18105", lei.numero());
        assertEquals("2025-03-12", lei.data());
        assertEquals("Lei nº 18105", lei.nome());
    }
}
