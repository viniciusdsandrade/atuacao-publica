package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Formato do CEAPS do Senado: latin-1, ponto e vírgula, linha de metadado antes do cabeçalho. Dados sintéticos. */
class CsvTest {

    private static List<Map<String, String>> ler(String conteudo, int pular) {
        List<Map<String, String>> linhas = new ArrayList<>();
        Csv.paraCada(new ByteArrayInputStream(conteudo.getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.ISO_8859_1,
                pular, linhas::add);
        return linhas;
    }

    @Test
    void leLatin1PulandoMetadadoEMantendoAcentos() {
        String csv = "\"ULTIMA ATUALIZACAO\";\"29/10/2026 02:02\"\r\n"
                + "\"ANO\";\"SENADOR\";\"TIPO_DESPESA\";\"VALOR_REEMBOLSADO\"\r\n"
                + "\"2026\";\"PESSOA EXEMPLO\";\"Locomoção, hospedagem e alimentação\";\"1.234,56\"\r\n";
        List<Map<String, String>> linhas = ler(csv, 1);
        assertEquals(1, linhas.size());
        assertEquals("Locomoção, hospedagem e alimentação", linhas.getFirst().get("TIPO_DESPESA"));
        assertEquals("1234.56", Texto.valor(linhas.getFirst().get("VALOR_REEMBOLSADO")).toPlainString());
    }

    @Test
    void toleraAspaSemEscapeNoMeioDoCampo() {
        String csv = "\"FORNECEDOR\";\"VALOR\"\r\n\"Exemplo\"s Eventos Ltda\";\"10,00\"\r\n\"Outro\";\"5,00\"\r\n";
        List<Map<String, String>> linhas = ler(csv, 0);
        assertEquals(2, linhas.size());
        assertEquals("Exemplo\"s Eventos Ltda", linhas.getFirst().get("FORNECEDOR"));
    }

    @Test
    void campoComQuebraDeLinhaContinuaNoMesmoRegistro() {
        String csv = "\"DETALHAMENTO\";\"VALOR\"\r\n\"linha um\r\nlinha dois\";\"1,00\"\r\n\"simples\";\"2,00\"\r\n";
        List<Map<String, String>> linhas = ler(csv, 0);
        assertEquals(2, linhas.size());
        assertEquals("linha um\r\nlinha dois", linhas.getFirst().get("DETALHAMENTO"));
    }

    @Test
    void aceitaCampoNumericoSemAspasComoNoTse() {
        String csv = "\"SQ_CANDIDATO\";\"NM_URNA_CANDIDATO\";\"VR_BEM_CANDIDATO\"\r\n990000000001;\"PESSOA EXEMPLO\";\"100000,50\"\r\n";
        List<Map<String, String>> linhas = ler(csv, 0);
        assertEquals("990000000001", linhas.getFirst().get("SQ_CANDIDATO"));
    }
}
