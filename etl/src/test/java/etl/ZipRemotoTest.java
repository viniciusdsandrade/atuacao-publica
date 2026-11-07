package etl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class ZipRemotoTest {

    /** Trechos de um ZIP em memória, com os mesmos intervalos inclusivos do HTTP Range. */
    private static ZipRemoto.Trechos emMemoria(byte[] zip) {
        return new ZipRemoto.Trechos() {
            @Override
            public long tamanho() {
                return zip.length;
            }

            @Override
            public byte[] ler(long inicio, long fim) {
                return Arrays.copyOfRange(zip, (int) inicio, (int) fim + 1);
            }

            @Override
            public InputStream abrir(long inicio, long fim) {
                return new ByteArrayInputStream(zip, (int) inicio, (int) (fim - inicio + 1));
            }
        };
    }

    private static byte[] csvGrande() {
        StringBuilder csv = new StringBuilder("\"SQ_CANDIDATO\";\"VR_RECEITA\"\r\n");
        for (int i = 0; i < 20_000; i++) {
            csv.append('"').append(250000000000L + i).append("\";\"").append(i % 997).append(",50\"\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] zip(byte[] grande, byte[] pequeno) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("leiame.txt"));
            zip.write("arquivo sintético".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("receitas_candidatos_2026_BRASIL.csv"));
            zip.write(grande);
            zip.closeEntry();
            ZipEntry semCompressao = new ZipEntry("receitas_candidatos_2026_SP.csv");
            semCompressao.setMethod(ZipEntry.STORED);
            CRC32 crc = new CRC32();
            crc.update(pequeno);
            semCompressao.setSize(pequeno.length);
            semCompressao.setCrc(crc.getValue());
            zip.putNextEntry(semCompressao);
            zip.write(pequeno);
            zip.closeEntry();
            zip.setComment("comentário do ZIP");
        }
        return saida.toByteArray();
    }

    @Test
    void extraiSoAEntradaPedidaComprimidaOuNaoEConfereOHash() throws Exception {
        byte[] grande = csvGrande();
        byte[] pequeno = "\"SQ_CANDIDATO\"\r\n\"250000000001\"\r\n".getBytes(StandardCharsets.ISO_8859_1);
        ZipRemoto.Trechos trechos = emMemoria(zip(grande, pequeno));

        ZipRemoto.Entrada entrada = ZipRemoto.entrada(trechos, "receitas_candidatos_2026_BRASIL.csv");
        assertEquals(ZipEntry.DEFLATED, entrada.metodo());
        assertEquals(grande.length, entrada.descomprimido());
        try (ZipRemoto.Conferido dados = ZipRemoto.abrir(trechos, entrada)) {
            assertArrayEquals(grande, dados.readAllBytes());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(grande)), dados.sha256());
        }

        ZipRemoto.Entrada guardada = ZipRemoto.entrada(trechos, "receitas_candidatos_2026_SP.csv");
        assertEquals(ZipEntry.STORED, guardada.metodo());
        try (ZipRemoto.Conferido dados = ZipRemoto.abrir(trechos, guardada)) {
            assertArrayEquals(pequeno, dados.readAllBytes());
        }
    }

    @Test
    void entradaAusenteOuDadoCorrompidoViramFalha() throws Exception {
        byte[] grande = csvGrande();
        byte[] zip = zip(grande, new byte[0]);
        assertThrows(IOException.class, () -> ZipRemoto.entrada(emMemoria(zip), "despesas_contratadas_candidatos_2026_BRASIL.csv"));

        ZipRemoto.Entrada entrada = ZipRemoto.entrada(emMemoria(zip), "receitas_candidatos_2026_BRASIL.csv");
        // Um byte trocado no meio dos dados comprimidos: a descompactação ou o CRC-32 precisa acusar.
        byte[] corrompido = zip.clone();
        int meio = (int) (entrada.inicioLocal() + 30 + "receitas_candidatos_2026_BRASIL.csv".length() + entrada.comprimido() / 2);
        corrompido[meio] ^= 0x5A;
        assertThrows(IOException.class, () -> {
            try (ZipRemoto.Conferido dados = ZipRemoto.abrir(emMemoria(corrompido), entrada)) {
                dados.readAllBytes();
            }
        });
    }
}
