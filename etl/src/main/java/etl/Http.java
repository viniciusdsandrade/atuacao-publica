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
    /** Fim do orçamento da coleta em curso, em System.nanoTime(). Nulo: sem limite. */
    private volatile Long prazo;

    /**
     * A coleta em curso marca o fim do orçamento. Depois dele, nenhuma tentativa nova sai, nem a de quem já esperava vaga
     * ou nova tentativa: com a fonte respondendo 504, essas filas passavam o limite do passo no CI.
     */
    public void definirPrazo(Long prazoNanos) {
        this.prazo = prazoNanos;
    }

    /** Orçamento de tempo da coleta esgotado antes de a requisição sair. */
    public static final class PrazoEsgotado extends IOException {
        public PrazoEsgotado(String mensagem) {
            super(mensagem);
        }
    }

    private void conferirPrazo(HttpRequest requisicao) throws PrazoEsgotado {
        Long fim = prazo;
        if (fim != null && System.nanoTime() - fim >= 0) {
            throw new PrazoEsgotado("orçamento de tempo esgotado antes de " + requisicao.uri());
        }
    }

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
        return baixar(url, destino, accept, Map.of());
    }

    public Baixado baixar(String url, Path destino, String accept, Map<String, String> cabecalhos) throws IOException, InterruptedException {
        return baixar(url, destino, accept, cabecalhos, null);
    }

    /** Com corpo JSON, a requisição vai por POST: API de consulta que só aceita o filtro no corpo. */
    public Baixado baixar(String url, Path destino, String accept, Map<String, String> cabecalhos, String corpoJson)
            throws IOException, InterruptedException {
        HttpRequest requisicao = corpoJson == null ? requisicao(url, accept, "GET", cabecalhos)
                : requisicao(url, accept, "POST", cabecalhos, corpoJson);
        HttpResponse<InputStream> resposta = enviar(requisicao, HttpResponse.BodyHandlers.ofInputStream());
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

    /**
     * Trechos de um recurso por requisição Range, sem compressão de transporte. Servidor que responde 200 em vez de 206
     * ignorou o Range e mandaria o arquivo inteiro: vira falha, não download silencioso.
     */
    public ZipRemoto.Trechos trechos(String url) {
        return new ZipRemoto.Trechos() {
            private long tamanho = -1;

            @Override
            public long tamanho() throws IOException, InterruptedException {
                if (tamanho < 0) {
                    HttpResponse<InputStream> resposta = faixa(url, 0, 0);
                    try (InputStream corpo = resposta.body()) {
                        corpo.readAllBytes();
                    }
                    String intervalo = resposta.headers().firstValue("Content-Range").orElse("");
                    int barra = intervalo.lastIndexOf('/');
                    if (barra < 0 || !intervalo.substring(barra + 1).matches("[0-9]+")) {
                        throw new IOException("resposta sem tamanho total (Content-Range) em " + url);
                    }
                    tamanho = Long.parseLong(intervalo.substring(barra + 1));
                }
                return tamanho;
            }

            @Override
            public byte[] ler(long inicio, long fim) throws IOException, InterruptedException {
                byte[] corpo;
                try (InputStream entrada = faixa(url, inicio, fim).body()) {
                    corpo = entrada.readAllBytes();
                }
                if (corpo.length != fim - inicio + 1) {
                    throw new IOException("trecho incompleto em " + url + ": " + corpo.length + " de " + (fim - inicio + 1) + " bytes");
                }
                return corpo;
            }

            @Override
            public InputStream abrir(long inicio, long fim) throws IOException, InterruptedException {
                return faixa(url, inicio, fim).body();
            }
        };
    }

    /** O corpo só é lido depois de conferir o 206: uma resposta 200 traria o arquivo inteiro. */
    private HttpResponse<InputStream> faixa(String url, long inicio, long fim) throws IOException, InterruptedException {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=" + inicio + "-" + fim)
                .GET().build();
        HttpResponse<InputStream> resposta = enviar(requisicao, HttpResponse.BodyHandlers.ofInputStream());
        if (resposta.statusCode() == 206) {
            return resposta;
        }
        fechar(resposta);
        if (resposta.statusCode() == 404 || resposta.statusCode() == 403) {
            throw new NaoEncontrado("HTTP " + resposta.statusCode() + " em " + url);
        }
        throw new IOException("HTTP " + resposta.statusCode() + " em " + url + " (esperado 206 para Range)");
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
        return requisicao(url, accept, metodo, Map.of());
    }

    private HttpRequest requisicao(String url, String accept, String metodo, Map<String, String> cabecalhos) {
        return requisicao(url, accept, metodo, cabecalhos, null);
    }

    private HttpRequest requisicao(String url, String accept, String metodo, Map<String, String> cabecalhos, String corpoJson) {
        var builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .header("Accept-Encoding", "gzip");
        // Corpo sem tipo declarado por quem chama é JSON; o formulário de postback declara o dele.
        if (corpoJson != null && (cabecalhos == null || !cabecalhos.containsKey("Content-Type"))) {
            builder.header("Content-Type", "application/json");
        }
        if (cabecalhos != null) {
            for (var entry : cabecalhos.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return builder.method(metodo, corpoJson == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(corpoJson)).build();
    }

    private <T> HttpResponse<T> enviar(HttpRequest requisicao, HttpResponse.BodyHandler<T> leitor)
            throws IOException, InterruptedException {
        IOException ultimaFalha = null;
        String retryAfter = null;
        for (int tentativa = 0; tentativa < TENTATIVAS; tentativa++) {
            if (tentativa > 0) {
                conferirPrazo(requisicao);
                // A espera fica fora da vaga: uma URL instável não trava as outras requisições.
                Thread.sleep(espera(tentativa, retryAfter));
            }
            esperarVez(requisicao.uri().getHost());
            vagas.acquire();
            try {
                conferirPrazo(requisicao);
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
            } catch (PrazoEsgotado e) {
                throw e;
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
