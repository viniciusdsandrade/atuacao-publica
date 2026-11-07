package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/** XLSX mínimo montado em memória: texto compartilhado, texto na célula e número. */
class PlanilhaTest {

    @Test
    void leTextoCompartilhadoTextoNaCelulaENumero() throws IOException {
        String compartilhados = "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>DEPUTADOS</t></si><si><r><t>FULANA </t></r><r><t>DE TAL</t></r></si></sst>";
        String planilha = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row r=\"1\"><c r=\"B1\" t=\"s\"><v>0</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\"><v>1</v></c><c r=\"B2\" t=\"s\"><v>1</v></c><c r=\"C2\" t=\"inlineStr\"><is><t>PT</t></is></c>"
                + "<c r=\"D2\" t=\"s\"></c></row></sheetData></worksheet>";

        List<Map<String, String>> linhas = Planilha.linhas(xlsx(compartilhados, planilha));

        assertEquals(2, linhas.size());
        assertEquals("DEPUTADOS", linhas.get(0).get("B"));
        assertEquals("1", linhas.get(1).get("A"));
        assertEquals("FULANA DE TAL", linhas.get(1).get("B"));
        assertEquals("PT", linhas.get(1).get("C"));
        // Célula vazia não entra.
        assertFalse(linhas.get(1).containsKey("D"));
    }

    private static byte[] xlsx(String compartilhados, String planilha) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(compartilhados.getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(planilha.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
