package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;

import etl.Registros.Despesa;
import etl.Registros.Prov;

class AlespTest {

    private static final Prov PROV = new Prov(Alesp.FONTE_DESPESAS,
            Alesp.REPO + "/deputados/despesas_gabinetes.xml", Instant.parse("2026-11-03T03:00:00Z"));

    @Test
    void parseDeDespesaPreservaCnpjCom14Digitos() throws Exception {
        String xml = """
                <despesa>
                  <Ano>2023</Ano>
                  <Matricula>300607</Matricula>
                  <Mes>5</Mes>
                  <Valor>250.75</Valor>
                  <CNPJ>12345678000199</CNPJ>
                  <Deputado>DANILO BALAS</Deputado>
                  <Tipo>COMBUSTÍVEIS</Tipo>
                  <Fornecedor>AUTO POSTO CENTRAL LTDA</Fornecedor>
                </despesa>
                """;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        reader.next(); // START_ELEMENT <despesa>

        Alesp.DespesaComMatricula dm = Alesp.lerDespesa(reader, PROV);
        assertNotNull(dm);
        assertEquals("300607", dm.matricula());
        Despesa d = dm.despesa();
        assertNotNull(d);
        assertEquals(2023, d.ano());
        assertEquals(5, d.mes());
        assertEquals(new BigDecimal("250.75"), d.valor());
        assertEquals("12345678000199", d.fornecedorCnpj());
        assertEquals("AUTO POSTO CENTRAL LTDA", d.fornecedorNome());
        assertEquals("COMBUSTÍVEIS", d.categoria());
        assertEquals(LocalDate.of(2023, 5, 1), d.data());
    }

    @Test
    void parseDeDespesaDescartaCpfDePessoaFisicaEAnonimizaFornecedor() throws Exception {
        String xml = """
                <despesa>
                  <Ano>2024</Ano>
                  <Matricula>300607</Matricula>
                  <Mes>8</Mes>
                  <Valor>1200.00</Valor>
                  <CNPJ>12345678901</CNPJ>
                  <Deputado>DANILO BALAS</Deputado>
                  <Tipo>LOCAÇÃO DE IMÓVEL</Tipo>
                  <Fornecedor>JOAO DA SILVA 123.456.789-01</Fornecedor>
                </despesa>
                """;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        reader.next();

        Alesp.DespesaComMatricula dm = Alesp.lerDespesa(reader, PROV);
        assertNotNull(dm);
        assertEquals("300607", dm.matricula());
        Despesa d = dm.despesa();
        assertNotNull(d);
        // CPF no CNPJ vira null
        assertNull(d.fornecedorCnpj(), "CPF de 11 dígitos deve virar null");
        // CPF no nome do fornecedor é removido
        assertEquals("JOAO DA SILVA", d.fornecedorNome());
        assertEquals(2024, d.ano());
        assertEquals(8, d.mes());
    }

    @Test
    void parseDeDespesaIgnoraAnosAnterioresA2022() throws Exception {
        String xml = """
                <despesa>
                  <Ano>2021</Ano>
                  <Matricula>300607</Matricula>
                  <Mes>12</Mes>
                  <Valor>500.00</Valor>
                  <CNPJ>12345678000199</CNPJ>
                  <Deputado>DANILO BALAS</Deputado>
                  <Tipo>COMBUSTÍVEIS</Tipo>
                  <Fornecedor>AUTO POSTO</Fornecedor>
                </despesa>
                """;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        reader.next();

        Alesp.DespesaComMatricula dm = Alesp.lerDespesa(reader, PROV);
        assertNull(dm, "Despesas anteriores a 2022 devem ser descartadas");
    }

    @Test
    void urlAbsolutaNormalizaEspacosEmBranco() {
        assertEquals("https://www.al.sp.gov.br/biografia/fotos/download/Aldo%20Demarchi.png",
                Alesp.urlAbsoluta("/biografia/fotos/download/Aldo Demarchi.png"));
        assertEquals("https://exemplo.org/foto%20teste.jpg",
                Alesp.urlAbsoluta("https://exemplo.org/foto teste.jpg"));
        assertNull(Alesp.urlAbsoluta("   "));
        assertNull(Alesp.urlAbsoluta(null));
    }
}
