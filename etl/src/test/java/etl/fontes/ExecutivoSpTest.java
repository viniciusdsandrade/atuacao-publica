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
}
