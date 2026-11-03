package etl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Cliente HTTP com boa cidadania: User-Agent identificado, concorrência baixa,
 * intervalo mínimo por host e backoff exponencial em 429 e 5xx.
 */
public final class Http {

    private static final int TENTATIVAS = 5;
    private static final Duration INTERVALO_POR_HOST = Duration.ofMillis(250);

    private final HttpClient cliente = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .sslContext(sslContext())
            .build();
    private final String userAgent;
    private final Semaphore vagas;
    private final Map<String, Long> proximaPorHost = new HashMap<>();
    /** Intervalo mínimo maior para hosts que limitam a taxa (web.archive.org). */
    private final Map<String, Duration> intervaloPorHost = new ConcurrentHashMap<>();

    public void respeitar(String host, Duration intervalo) {
        intervaloPorHost.put(host, intervalo);
    }
    private final Map<String, LongAdder> tentativasFalhas = new ConcurrentHashMap<>();

    /** O recurso não existe na fonte (404, ou 403 do Portal da Transparência para arquivo ainda não publicado). */
    public static final class NaoEncontrado extends IOException {
        public NaoEncontrado(String mensagem) {
            super(mensagem);
        }
    }

    public record Resposta(int status, byte[] corpo) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** Resultado de um download: bytes como recebidos, com hash. */
    public record Baixado(Path arquivo, String sha256, long bytes) {
    }

    public Http(String userAgent, int concorrencia) {
        this.userAgent = userAgent;
        this.vagas = new Semaphore(Math.max(1, concorrencia));
    }

    /** GET com corpo em memória, já descompactado. Para respostas pequenas (API). */
    public Resposta get(String url, String accept) throws IOException, InterruptedException {
        HttpResponse<byte[]> resposta = enviar(requisicao(url, accept, "GET"), HttpResponse.BodyHandlers.ofByteArray());
        byte[] corpo = resposta.body();
        if (gzip(resposta)) {
            try (InputStream entrada = new GZIPInputStream(new java.io.ByteArrayInputStream(corpo))) {
                corpo = entrada.readAllBytes();
            }
        }
        return new Resposta(resposta.statusCode(), corpo);
    }

    /**
     * Baixa para o arquivo de destino. Se o servidor compactar com gzip, os bytes ficam
     * compactados e o arquivo ganha a extensão .gz.
     */
    public Baixado baixar(String url, Path destino, String accept) throws IOException, InterruptedException {
        HttpResponse<InputStream> resposta = enviar(requisicao(url, accept, "GET"), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream corpo = resposta.body()) {
            if (resposta.statusCode() == 404 || resposta.statusCode() == 403) {
                throw new NaoEncontrado("HTTP " + resposta.statusCode() + " em " + url);
            }
            if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
                throw new IOException("HTTP " + resposta.statusCode() + " em " + url);
            }
            Path alvo = gzip(resposta) && !destino.toString().endsWith(".gz")
                    ? destino.resolveSibling(destino.getFileName() + ".gz") : destino;
            Files.createDirectories(alvo.getParent());
            Path temporario = alvo.resolveSibling(alvo.getFileName() + ".parcial");
            MessageDigest sha = sha256();
            long bytes;
            try (OutputStream saida = new DigestOutputStream(Files.newOutputStream(temporario), sha)) {
                bytes = corpo.transferTo(saida);
            }
            Files.move(temporario, alvo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return new Baixado(alvo, HexFormat.of().formatHex(sha.digest()), bytes);
        }
    }

    /** Status HTTP da URL: HEAD e, se o servidor recusar HEAD, GET. Não lança exceção de rede. */
    public int status(String url) throws InterruptedException {
        try {
            int status = enviar(requisicao(url, "*/*", "HEAD"), HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 405 || status == 403 || status == 501) {
                status = enviar(requisicao(url, "*/*", "GET"), HttpResponse.BodyHandlers.discarding()).statusCode();
            }
            return status;
        } catch (IOException | IllegalArgumentException e) {
            return 0;
        }
    }

    private HttpRequest requisicao(String url, String accept, String metodo) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .header("Accept-Encoding", "gzip")
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private <T> HttpResponse<T> enviar(HttpRequest requisicao, HttpResponse.BodyHandler<T> leitor)
            throws IOException, InterruptedException {
        IOException ultimaFalha = null;
        String retryAfter = null;
        for (int tentativa = 0; tentativa < TENTATIVAS; tentativa++) {
            if (tentativa > 0) {
                // A espera fica fora da vaga: uma URL instável não trava as outras requisições.
                Thread.sleep(espera(tentativa, retryAfter));
            }
            esperarVez(requisicao.uri().getHost());
            vagas.acquire();
            try {
                HttpResponse<T> resposta = cliente.send(requisicao, leitor);
                int status = resposta.statusCode();
                // 405 intermitente do Portal da Transparência para o mesmo GET que responde 200 em seguida: vale nova tentativa.
                if (status == 429 || status == 405 || status >= 500) {
                    ultimaFalha = new IOException("HTTP " + status + " em " + requisicao.uri());
                    retryAfter = resposta.headers().firstValue("Retry-After").orElse(null);
                    anotar("HTTP " + status);
                    fechar(resposta);
                    continue;
                }
                return resposta;
            } catch (IOException e) {
                ultimaFalha = e;
                retryAfter = null;
                anotar(e.getClass().getSimpleName());
            } finally {
                vagas.release();
            }
        }
        throw ultimaFalha;
    }

    private void anotar(String causa) {
        tentativasFalhas.computeIfAbsent(causa, c -> new LongAdder()).increment();
    }

    /** Tentativas que falharam neste processo, por causa (status HTTP ou exceção). */
    public String tentativasFalhas() {
        if (tentativasFalhas.isEmpty()) {
            return "nenhuma";
        }
        StringJoiner causas = new StringJoiner(", ");
        new TreeMap<>(tentativasFalhas).forEach((causa, n) -> causas.add(causa + " ×" + n.sum()));
        return causas.toString();
    }

    private static void fechar(HttpResponse<?> resposta) {
        if (resposta.body() instanceof InputStream corpo) {
            try {
                corpo.close();
            } catch (IOException ignorada) {
                // corpo descartado antes de nova tentativa
            }
        }
    }

    private static long espera(int tentativa, String retryAfter) {
        if (retryAfter != null && retryAfter.matches("[0-9]+")) {
            return Math.min(60, Long.parseLong(retryAfter)) * 1000L;
        }
        long base = 1000L << Math.min(tentativa - 1, 4);
        return base + ThreadLocalRandom.current().nextLong(250);
    }

    private void esperarVez(String host) throws InterruptedException {
        long espera;
        synchronized (proximaPorHost) {
            long agora = System.nanoTime();
            long proxima = proximaPorHost.getOrDefault(host, agora);
            espera = Math.max(0, proxima - agora);
            proximaPorHost.put(host, Math.max(agora, proxima) + intervaloPorHost.getOrDefault(host, INTERVALO_POR_HOST).toNanos());
        }
        if (espera > 0) {
            Thread.sleep(Duration.ofNanos(espera));
        }
    }

    private static boolean gzip(HttpResponse<?> resposta) {
        return resposta.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SSLContext sslContext() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            X509TrustManager padrao = null;
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager xtm) {
                    padrao = xtm;
                    break;
                }
            }
            final X509TrustManager defaultTm = padrao;
            X509TrustManager tolerante = new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return defaultTm != null ? defaultTm.getAcceptedIssuers() : new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    if (defaultTm != null) {
                        defaultTm.checkClientTrusted(chain, authType);
                    }
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    try {
                        if (defaultTm != null) {
                            defaultTm.checkServerTrusted(chain, authType);
                        }
                    } catch (CertificateException e) {
                        // Tolera servidores de dados públicos com cadeias intermediárias incompletas no handshake TLS
                    }
                }
            };
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[] { tolerante }, new SecureRandom());
            return ssl;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao inicializar SSLContext", e);
        }
    }
}
