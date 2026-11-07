package etl;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Uma entrada de um ZIP remoto, lida por requisições Range: o fim do arquivo, o diretório central, o cabeçalho local
 * e os dados da entrada. O resto do ZIP não é baixado (ADR-0005, item 10).
 */
public final class ZipRemoto {

    private static final int FIM_DO_DIRETORIO = 0x06054b50;
    private static final int LOCALIZADOR_ZIP64 = 0x07064b50;
    private static final int FIM_DO_DIRETORIO_ZIP64 = 0x06064b50;
    private static final int ENTRADA_DO_DIRETORIO = 0x02014b50;
    private static final int CABECALHO_LOCAL = 0x04034b50;
    private static final long SEM_VALOR_32 = 0xFFFFFFFFL;

    private ZipRemoto() {
    }

    /** Trechos de um recurso: por HTTP na coleta, por bytes em memória nos testes. Intervalos inclusivos. */
    public interface Trechos {
        long tamanho() throws IOException, InterruptedException;

        byte[] ler(long inicio, long fim) throws IOException, InterruptedException;

        InputStream abrir(long inicio, long fim) throws IOException, InterruptedException;
    }

    /** Entrada como o diretório central a descreve. */
    public record Entrada(String nome, int metodo, long comprimido, long descomprimido, long crc, long inicioLocal) {
    }

    /** Procura a entrada pelo nome no diretório central. */
    public static Entrada entrada(Trechos trechos, String nome) throws IOException, InterruptedException {
        long total = trechos.tamanho();
        // Fim do diretório (22 bytes), comentário de até 64 KB e o localizador ZIP64 (20 bytes) logo antes.
        long inicio = Math.max(0, total - (22 + 65535 + 20));
        byte[] cauda = trechos.ler(inicio, total - 1);
        int fim = ultimo(cauda, FIM_DO_DIRETORIO, cauda.length - 22);
        if (fim < 0) {
            throw new IOException("ZIP sem fim de diretório central");
        }
        long entradas = u16(cauda, fim + 10);
        long tamanhoDoDiretorio = u32(cauda, fim + 12);
        long inicioDoDiretorio = u32(cauda, fim + 16);
        if (entradas == 0xFFFF || tamanhoDoDiretorio == SEM_VALOR_32 || inicioDoDiretorio == SEM_VALOR_32) {
            int localizador = ultimo(cauda, LOCALIZADOR_ZIP64, fim - 20);
            if (localizador < 0) {
                throw new IOException("ZIP64 sem localizador do fim de diretório");
            }
            long posicao = u64(cauda, localizador + 8);
            byte[] fim64 = trechos.ler(posicao, posicao + 55);
            if (u32(fim64, 0) != FIM_DO_DIRETORIO_ZIP64) {
                throw new IOException("ZIP64 com fim de diretório inválido");
            }
            tamanhoDoDiretorio = u64(fim64, 40);
            inicioDoDiretorio = u64(fim64, 48);
        }
        byte[] diretorio = trechos.ler(inicioDoDiretorio, inicioDoDiretorio + tamanhoDoDiretorio - 1);
        int p = 0;
        while (p + 46 <= diretorio.length && u32(diretorio, p) == ENTRADA_DO_DIRETORIO) {
            int flags = (int) u16(diretorio, p + 8);
            int metodo = (int) u16(diretorio, p + 10);
            long crc = u32(diretorio, p + 16);
            long comprimido = u32(diretorio, p + 20);
            long descomprimido = u32(diretorio, p + 24);
            int n = (int) u16(diretorio, p + 28);
            int x = (int) u16(diretorio, p + 30);
            int c = (int) u16(diretorio, p + 32);
            long local = u32(diretorio, p + 42);
            String nomeDaEntrada = new String(diretorio, p + 46, n,
                    (flags & 0x800) != 0 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
            // Campo extra ZIP64: só traz, nesta ordem, os valores que no diretório vêm como 0xFFFFFFFF.
            int q = p + 46 + n;
            int fimDoExtra = q + x;
            while (q + 4 <= fimDoExtra) {
                int tipo = (int) u16(diretorio, q);
                int tamanho = (int) u16(diretorio, q + 2);
                if (tipo == 1) {
                    int v = q + 4;
                    if (descomprimido == SEM_VALOR_32) {
                        descomprimido = u64(diretorio, v);
                        v += 8;
                    }
                    if (comprimido == SEM_VALOR_32) {
                        comprimido = u64(diretorio, v);
                        v += 8;
                    }
                    if (local == SEM_VALOR_32) {
                        local = u64(diretorio, v);
                    }
                }
                q += 4 + tamanho;
            }
            if (nomeDaEntrada.equals(nome)) {
                return new Entrada(nomeDaEntrada, metodo, comprimido, descomprimido, crc, local);
            }
            p += 46 + n + x + c;
        }
        throw new IOException("entrada " + nome + " ausente do ZIP");
    }

    /** Dados descompactados da entrada. No fim da leitura, confere o tamanho e o CRC-32 do diretório central. */
    public static Conferido abrir(Trechos trechos, Entrada entrada) throws IOException, InterruptedException {
        byte[] local = trechos.ler(entrada.inicioLocal(), entrada.inicioLocal() + 29);
        if (u32(local, 0) != CABECALHO_LOCAL) {
            throw new IOException("cabeçalho local inválido em " + entrada.nome());
        }
        long inicioDosDados = entrada.inicioLocal() + 30 + u16(local, 26) + u16(local, 28);
        InputStream comprimido = entrada.comprimido() == 0 ? InputStream.nullInputStream()
                : trechos.abrir(inicioDosDados, inicioDosDados + entrada.comprimido() - 1);
        InputStream dados = switch (entrada.metodo()) {
            case 0 -> comprimido;
            // O Inflater sem cabeçalho zlib pode pedir um byte a mais no fim, como faz o ZipFile da JDK.
            case 8 -> new InflaterInputStream(new SequenceInputStream(comprimido, new ByteArrayInputStream(new byte[1])),
                    new Inflater(true), 65536);
            default -> throw new IOException("método de compressão " + entrada.metodo() + " sem suporte em " + entrada.nome());
        };
        return new Conferido(dados, entrada);
    }

    /** Fluxo que soma o CRC-32, o tamanho e o sha256 do que passa. A conferência acontece quando a leitura chega ao fim. */
    public static final class Conferido extends FilterInputStream {
        private final Entrada entrada;
        private final CRC32 crc = new CRC32();
        private final MessageDigest sha = novoSha256();
        private long lidos;
        private boolean conferido;

        Conferido(InputStream dados, Entrada entrada) {
            super(dados);
            this.entrada = entrada;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b < 0) {
                conferir();
            } else {
                crc.update(b);
                sha.update((byte) b);
                lidos++;
            }
            return b;
        }

        @Override
        public int read(byte[] destino, int inicio, int tamanho) throws IOException {
            int n = super.read(destino, inicio, tamanho);
            if (n < 0) {
                conferir();
            } else {
                crc.update(destino, inicio, n);
                sha.update(destino, inicio, n);
                lidos += n;
            }
            return n;
        }

        private void conferir() throws IOException {
            if (conferido) {
                return;
            }
            if (lidos != entrada.descomprimido() || crc.getValue() != entrada.crc()) {
                throw new IOException("entrada " + entrada.nome() + " não confere com o diretório central (tamanho ou CRC-32)");
            }
            conferido = true;
        }

        /** sha256 da entrada descompactada. Só depois de ler até o fim. */
        public String sha256() throws IOException {
            if (!conferido) {
                transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(sha.digest());
        }
    }

    private static int ultimo(byte[] dados, int assinatura, int aPartirDe) {
        for (int i = Math.min(aPartirDe, dados.length - 4); i >= 0; i--) {
            if (u32(dados, i) == (assinatura & SEM_VALOR_32)) {
                return i;
            }
        }
        return -1;
    }

    private static long u16(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] b, int i) {
        return u16(b, i) | u16(b, i + 2) << 16;
    }

    private static long u64(byte[] b, int i) {
        return u32(b, i) | u32(b, i + 4) << 32;
    }

    private static MessageDigest novoSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
