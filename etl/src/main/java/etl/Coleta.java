package etl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache de dados brutos em data/raw/{fonte}. Cada arquivo tem ao lado um .meta.json
 * com URL, hash, tamanho e instante de captura.
 */
public final class Coleta {

    public enum Politica {
        /** Baixa de novo a cada execução (período aberto). */
        SEMPRE,
        /** Reusa o arquivo em cache (período fechado). */
        SE_AUSENTE,
        /** Dado que muda pouco: reusa a cópia por até uma semana. Cada arquivo tem seu dia de rodízio, e a carga fica espalhada. */
        SEMANAL,
        /**
         * Como SEMANAL, mas todas as páginas da mesma pasta renovam no mesmo dia: lista paginada que desloca a cada item
         * novo. Com um dia por página, o item que muda de página entre duas renovações fica fora até o rodízio seguinte.
         */
        SEMANAL_EM_LISTA;

        /**
         * Arquivo de um ano: o ano corrente fica aberto. O ano anterior fica aberto até 31 de março,
         * porque registros dele ainda podem chegar ou mudar no início do ano seguinte. Os demais ficam fechados.
         */
        public static Politica porAno(int ano, LocalDate hoje) {
            boolean aberto = ano >= hoje.getYear() || ano == hoje.getYear() - 1 && hoje.getMonthValue() <= 3;
            return aberto ? SEMPRE : SE_AUSENTE;
        }

        /** Como porAno, mas o ano aberto renova uma vez por semana, em rodízio: listagens grandes que mudam pouco. */
        public static Politica porAnoSemanal(int ano, LocalDate hoje) {
            return porAno(ano, hoje) == SEMPRE ? SEMANAL : SE_AUSENTE;
        }

        /** Como porAnoSemanal, para as páginas de uma lista que desloca: o ano aberto renova inteiro no mesmo dia. */
        public static Politica porAnoSemanalEmLista(int ano, LocalDate hoje) {
            return porAno(ano, hoje) == SEMPRE ? SEMANAL_EM_LISTA : SE_AUSENTE;
        }
    }

    public record Arquivo(String url, String caminho, String sha256, long bytes, Instant capturadoEm) {
    }

    private final Contexto ctx;
    private final String fonte;
    private final List<Arquivo> arquivos = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> contagens = Collections.synchronizedMap(new TreeMap<>());
    private volatile Meta.Periodo periodo;
    static final int LIMITE_DE_FALHAS = 3;
    static final int DIAS_DO_RODIZIO = 7;
    private final Map<String, Integer> falhas = new ConcurrentHashMap<>();
    private final Map<String, Integer> reservas = new ConcurrentSkipListMap<>();
    private final Map<String, String> primeiroErro = new ConcurrentHashMap<>();
    /** Linha de progresso a cada tantos downloads: o log mostra o ritmo da coleta mesmo se o passo estourar o limite de tempo. */
    private static final int PROGRESSO_A_CADA = 250;
    private final long inicio = System.nanoTime();
    private final AtomicInteger baixados = new AtomicInteger();
    private final AtomicInteger doCache = new AtomicInteger();

    /** Tempo máximo de coleta. Esgotado, o restante vem do cache e a fonte fica parcial. Nulo: sem limite. */
    private final Duration orcamento;
    private final AtomicBoolean orcamentoAvisado = new AtomicBoolean();

    public Coleta(Contexto ctx, String fonte) {
        this(ctx, fonte, orcamentoDoAmbiente());
    }

    public Coleta(Contexto ctx, String fonte, Duration orcamento) {
        this.ctx = ctx;
        this.fonte = fonte;
        this.orcamento = orcamento;
        // O orçamento vale também para a requisição que já passou por obter e espera vaga ou nova tentativa no cliente.
        if (ctx.http() != null) {
            ctx.http().definirPrazo(orcamento == null ? null : inicio + orcamento.toNanos());
        }
    }

    /** ETL_ORCAMENTO_MINUTOS: abaixo do limite de tempo do passo no CI, para o cursor e o cache serem gravados. */
    static Duration orcamentoDoAmbiente() {
        String minutos = System.getenv("ETL_ORCAMENTO_MINUTOS");
        return minutos == null || minutos.isBlank() ? null : Duration.ofMinutes(Long.parseLong(minutos.strip()));
    }

    boolean orcamentoEsgotado() {
        return orcamento != null && System.nanoTime() - inicio >= orcamento.toNanos();
    }

    /** Registra uma falha que não derruba a fonte: ela fica parcial, com o motivo no cursor. */
    public void falhaParcial(String grupo, String erro) {
        reserva(grupo, null, erro);
    }

    /** Indica se há cópia em cache da URL exata ainda válida pela política. */
    public boolean emCacheValido(String url, String caminho, Politica politica) {
        Path destino = ctx.raw().resolve(fonte).resolve(caminho);
        Arquivo emCache = emCache(lateral(destino));
        return emCache != null && url.equals(emCache.url()) && valeACopia(politica, caminho, emCache);
    }

    /** Indica se o orçamento de tempo desta coleta já se esgotou. */
    public boolean orcamentoExaurido() {
        return orcamentoEsgotado();
    }

    public Arquivo obter(String url, String caminho, String accept, Politica politica)
            throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, false, null, false, Map.of());
    }

    public Arquivo obter(String url, String caminho, String accept, Politica politica, Map<String, String> cabecalhos)
            throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, false, null, false, cabecalhos);
    }

    /** Transforma o download antes de guardar: de um arquivo grande, só a parte útil fica no cache. */
    public interface Derivacao {
        void derivar(Path baixado, Path destino) throws IOException;
    }

    /**
     * Recurso que pode não existir (página de um dia sem agenda, mês ainda não publicado): 404 e 403 guardam um arquivo
     * vazio, com bytes 0 no meta, e não contam como falha. O rodízio da política renova a tentativa depois.
     */
    public Arquivo obterOuVazio(String url, String caminho, String accept, Politica politica)
            throws IOException, InterruptedException {
        return obterOuVazio(url, caminho, accept, politica, Map.of());
    }

    public Arquivo obterOuVazio(String url, String caminho, String accept, Politica politica, Map<String, String> cabecalhos)
            throws IOException, InterruptedException {
        Path destino = ctx.raw().resolve(fonte).resolve(caminho);
        Path meta = lateral(destino);
        Arquivo emCache = emCache(meta);
        if (emCache != null && url.equals(emCache.url()) && emCache.bytes() == 0 && valeACopia(politica, caminho, emCache)) {
            doCache.incrementAndGet();
            arquivos.add(emCache);
            return emCache;
        }
        try {
            return obter(url, caminho, accept, politica, true, null, true, cabecalhos);
        } catch (Http.NaoEncontrado e) {
            Files.createDirectories(destino.getParent());
            Files.write(destino, new byte[0]);
            Arquivo vazio = new Arquivo(url, ctx.relativo(destino), "", 0, ctx.agora());
            Json.gravar(meta, vazio, true);
            arquivos.add(vazio);
            return vazio;
        }
    }

    /** Como obterOpcional, mas guarda o resultado da derivação, não o download. O meta registra a URL e o hash do original. */
    public Arquivo obterDerivado(String url, String caminho, String accept, Politica politica, Derivacao derivacao)
            throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, true, derivacao, false, Map.of());
    }

    /**
     * Para dado complementar: se a fonte falhar e não houver cópia anterior, devolve null e a coleta fica parcial.
     * Depois de LIMITE_DE_FALHAS falhas no tipo de arquivo, nem tenta.
     */
    public Arquivo obterOpcional(String url, String caminho, String accept, Politica politica)
            throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, true, null, false, Map.of());
    }

    public Arquivo obterOpcional(String url, String caminho, String accept, Politica politica, Map<String, String> cabecalhos)
            throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, true, null, false, cabecalhos);
    }

    /**
     * Como obterOpcional, para API que só aceita o filtro num corpo JSON (POST). O caminho no cache distingue cada consulta,
     * e o meta guarda a URL, que é a proveniência.
     */
    public Arquivo obterPostOpcional(String url, String corpoJson, String caminho, String accept, Politica politica)
            throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, true, null, false, Map.of(), corpoJson);
    }

    /** POST de formulário, como o postback de uma página ASP.NET. Os campos vão na ordem do mapa. */
    public Arquivo obterFormularioOpcional(String url, Map<String, String> campos, String caminho, String accept, Politica politica)
            throws IOException, InterruptedException {
        StringJoiner corpo = new StringJoiner("&");
        campos.forEach((nome, valor) -> corpo.add(java.net.URLEncoder.encode(nome, java.nio.charset.StandardCharsets.UTF_8) + "="
                + java.net.URLEncoder.encode(valor == null ? "" : valor, java.nio.charset.StandardCharsets.UTF_8)));
        return obter(url, caminho, accept, politica, true, null, false, Map.of("Content-Type", "application/x-www-form-urlencoded"),
                corpo.toString());
    }

    private Arquivo obter(String url, String caminho, String accept, Politica politica, boolean opcional, Derivacao derivacao,
                          boolean propagarNaoEncontrado) throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, opcional, derivacao, propagarNaoEncontrado, Map.of());
    }

    private Arquivo obter(String url, String caminho, String accept, Politica politica, boolean opcional, Derivacao derivacao,
                          boolean propagarNaoEncontrado, Map<String, String> cabecalhos) throws IOException, InterruptedException {
        return obter(url, caminho, accept, politica, opcional, derivacao, propagarNaoEncontrado, cabecalhos, null);
    }

    private Arquivo obter(String url, String caminho, String accept, Politica politica, boolean opcional, Derivacao derivacao,
                          boolean propagarNaoEncontrado, Map<String, String> cabecalhos, String corpoJson)
            throws IOException, InterruptedException {
        Path destino = ctx.raw().resolve(fonte).resolve(caminho);
        Path meta = lateral(destino);
        Arquivo emCache = emCache(meta);
        if (emCache != null && !url.equals(emCache.url())) {
            // Outro recurso no mesmo caminho (a consulta mudou com a cobertura): a cópia anterior não vale nem como reserva.
            emCache = null;
        }
        if (emCache != null && valeACopia(politica, caminho, emCache)) {
            doCache.incrementAndGet();
            arquivos.add(emCache);
            return emCache;
        }
        String grupo = grupo(caminho);
        if ((emCache != null || opcional) && falhas.getOrDefault(grupo, 0) >= LIMITE_DE_FALHAS) {
            return reserva(grupo, emCache, null);
        }
        if (orcamentoEsgotado()) {
            String motivo = "orçamento de tempo de " + orcamento.toMinutes() + " min esgotado";
            if (orcamentoAvisado.compareAndSet(false, true)) {
                System.err.println(fonte + ": " + motivo + "; o restante vem do cache");
            }
            if (emCache != null || opcional) {
                return reserva(grupo, emCache, motivo);
            }
            throw new IOException(motivo + " antes de " + caminho);
        }
        try {
            Http.Baixado baixado;
            if (derivacao == null) {
                baixado = ctx.http().baixar(url, destino, accept, cabecalhos, corpoJson);
            } else {
                Path temporario = Files.createTempFile(ctx.raw(), "download-", ".tmp");
                try {
                    baixado = ctx.http().baixar(url, temporario, accept, cabecalhos, corpoJson);
                    Files.createDirectories(destino.getParent());
                    derivacao.derivar(baixado.arquivo(), destino);
                    baixado = new Http.Baixado(destino, baixado.sha256(), baixado.bytes());
                } finally {
                    Files.deleteIfExists(temporario);
                    Files.deleteIfExists(temporario.resolveSibling(temporario.getFileName() + ".gz"));
                }
            }
            Arquivo arquivo = new Arquivo(url, ctx.relativo(baixado.arquivo()), baixado.sha256(), baixado.bytes(), ctx.agora());
            Json.gravar(meta, arquivo, true);
            arquivos.add(arquivo);
            baixou();
            return arquivo;
        } catch (Http.NaoEncontrado e) {
            // Recurso que pode não existir: quem chamou obterOuVazio trata; os demais seguem o caminho comum de falha.
            if (propagarNaoEncontrado && emCache == null) {
                throw e;
            }
            if (falhas.merge(grupo, 1, Integer::sum) <= LIMITE_DE_FALHAS) {
                System.err.println(fonte + ": falha em " + caminho + ": " + e.getMessage());
            }
            if (emCache == null && !opcional) {
                throw e;
            }
            return reserva(grupo, emCache, e.getMessage());
        } catch (IOException e) {
            if (falhas.merge(grupo, 1, Integer::sum) <= LIMITE_DE_FALHAS) {
                System.err.println(fonte + ": falha em " + caminho + ": " + e.getMessage());
            }
            if (emCache == null && !opcional) {
                throw e;
            }
            return reserva(grupo, emCache, e.getMessage());
        }
    }

    /**
     * A atualização falhou e há cópia anterior: publica a cópia, com a data de captura original.
     * Depois de LIMITE_DE_FALHAS falhas no mesmo tipo de arquivo, nem tenta de novo nesta execução:
     * fonte instável que alterna sucesso e erro não pode prender a coleta por horas.
     */
    private Arquivo reserva(String grupo, Arquivo emCache, String erro) {
        reservas.merge(grupo, 1, Integer::sum);
        if (erro != null) {
            primeiroErro.putIfAbsent(grupo, erro);
        }
        if (emCache != null) {
            arquivos.add(emCache);
        }
        return emCache;
    }

    /** Falhas de atualização cobertas por cópia anterior, por tipo de arquivo. Vazio quando tudo foi atualizado. */
    public List<String> reservasUsadas() {
        List<String> descricoes = new ArrayList<>();
        reservas.forEach((grupo, n) -> descricoes.add(grupo + ": " + n + " arquivo(s) sem atualização, com cópia anterior quando existe ("
                + primeiroErro.getOrDefault(grupo, "falhas seguidas") + ")"));
        return descricoes;
    }

    private Arquivo emCache(Path meta) {
        if (!Files.exists(meta)) {
            return null;
        }
        Arquivo arquivo = Json.ler(meta, Arquivo.class);
        return Files.exists(ctx.dados().resolve(arquivo.caminho())) ? arquivo : null;
    }

    /** Cópia em cache que ainda vale pela política. Na lista, o dia de rodízio é o da pasta, e não o de cada página. */
    private boolean valeACopia(Politica politica, String caminho, Arquivo emCache) {
        return switch (politica) {
            case SE_AUSENTE -> true;
            case SEMANAL -> !vencida(caminho, emCache.capturadoEm(), ctx.agora());
            case SEMANAL_EM_LISTA -> !vencida(pasta(caminho), emCache.capturadoEm(), ctx.agora());
            case SEMPRE -> false;
        };
    }

    /** Pasta do arquivo no cache da fonte: proposicoes/PL-2026-3.json → proposicoes. */
    static String pasta(String caminho) {
        int barra = caminho.lastIndexOf('/');
        return barra < 0 ? caminho : caminho.substring(0, barra);
    }

    /** Cópia SEMANAL vencida: é o dia de rodízio do arquivo, ou a cópia passou de uma semana porque uma execução falhou. */
    static boolean vencida(String caminho, Instant capturadaEm, Instant agora) {
        LocalDate hoje = LocalDate.ofInstant(agora, ZoneOffset.UTC);
        boolean diaDoRodizio = Math.floorMod(caminho.hashCode() + hoje.toEpochDay(), DIAS_DO_RODIZIO) == 0;
        return diaDoRodizio || capturadaEm.isBefore(agora.minus(Duration.ofDays(DIAS_DO_RODIZIO)));
    }

    /** Tipo de arquivo para o disjuntor: a primeira pasta do caminho (frentes/204554.json → frentes). */
    private static String grupo(String caminho) {
        int barra = caminho.indexOf('/');
        return barra < 0 ? caminho : caminho.substring(0, barra);
    }

    /** Recorte de uma entrada descompactada antes de gravar: do CSV grande, só as linhas úteis ficam no cache. */
    public interface Recorte {
        void recortar(InputStream entrada, Path destino) throws IOException;
    }

    /**
     * Uma entrada de um ZIP grande, lida por Range, sem baixar o ZIP (ADR-0005, item 10). O cache guarda o recorte.
     * O meta registra a URL do ZIP com o nome da entrada depois de "#", o sha256 e o tamanho da entrada descompactada,
     * conferida pelo CRC-32 do diretório central. Dado complementar: sem cópia anterior, a falha deixa a fonte parcial.
     */
    public Arquivo extrairDoZip(String url, String entrada, String caminho, Politica politica, Recorte recorte)
            throws IOException, InterruptedException {
        String origem = url + "#" + entrada;
        Path destino = ctx.raw().resolve(fonte).resolve(caminho);
        Path meta = lateral(destino);
        Arquivo emCache = emCache(meta);
        if (emCache != null && !origem.equals(emCache.url())) {
            emCache = null;
        }
        if (emCache != null && valeACopia(politica, caminho, emCache)) {
            doCache.incrementAndGet();
            arquivos.add(emCache);
            return emCache;
        }
        String grupo = grupo(caminho);
        if (falhas.getOrDefault(grupo, 0) >= LIMITE_DE_FALHAS) {
            return reserva(grupo, emCache, null);
        }
        if (orcamentoEsgotado()) {
            String motivo = "orçamento de tempo de " + orcamento.toMinutes() + " min esgotado";
            if (orcamentoAvisado.compareAndSet(false, true)) {
                System.err.println(fonte + ": " + motivo + "; o restante vem do cache");
            }
            return reserva(grupo, emCache, motivo);
        }
        Path temporario = destino.resolveSibling(destino.getFileName() + ".parcial");
        try {
            ZipRemoto.Trechos trechos = ctx.http().trechos(url);
            ZipRemoto.Entrada dados = ZipRemoto.entrada(trechos, entrada);
            Files.createDirectories(destino.getParent());
            String sha256;
            try (ZipRemoto.Conferido conferido = ZipRemoto.abrir(trechos, dados)) {
                recorte.recortar(conferido, temporario);
                sha256 = conferido.sha256();
            }
            Files.move(temporario, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Arquivo arquivo = new Arquivo(origem, ctx.relativo(destino), sha256, dados.descomprimido(), ctx.agora());
            Json.gravar(meta, arquivo, true);
            arquivos.add(arquivo);
            baixou();
            return arquivo;
        } catch (IOException | RuntimeException e) {
            // O leitor de CSV embrulha a falha de leitura (CRC-32 que não confere, conexão cortada) em exceção não checada.
            Files.deleteIfExists(temporario);
            String mensagem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (falhas.merge(grupo, 1, Integer::sum) <= LIMITE_DE_FALHAS) {
                System.err.println(fonte + ": falha em " + caminho + ": " + mensagem);
            }
            return reserva(grupo, emCache, mensagem);
        }
    }

    /**
     * Baixa um ZIP grande só para ler o índice de arquivos. Guarda a lista e o sha256 do ZIP, não o ZIP.
     * Lê o diretório central (ZipFile): funciona com entradas STORED e DEFLATED.
     */
    public Arquivo listarZip(String url, String caminho, Politica politica) throws IOException, InterruptedException {
        Path destino = ctx.raw().resolve(fonte).resolve(caminho);
        Path meta = lateral(destino);
        if (politica == Politica.SE_AUSENTE && Files.exists(meta) && Files.exists(destino)) {
            Arquivo emCache = Json.ler(meta, Arquivo.class);
            doCache.incrementAndGet();
            arquivos.add(emCache);
            return emCache;
        }
        Path temporario = Files.createTempFile(ctx.raw(), "zip-", ".zip");
        try {
            Http.Baixado baixado = ctx.http().baixar(url, temporario, "application/zip");
            List<String> nomes = new ArrayList<>();
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(baixado.arquivo().toFile())) {
                zip.stream().forEach(e -> nomes.add(e.getName()));
            }
            Json.gravar(destino, nomes, true);
            Arquivo arquivo = new Arquivo(url, ctx.relativo(destino), baixado.sha256(), baixado.bytes(), ctx.agora());
            Json.gravar(meta, arquivo, true);
            arquivos.add(arquivo);
            baixou();
            return arquivo;
        } finally {
            Files.deleteIfExists(temporario);
        }
    }

    private void baixou() {
        if (baixados.incrementAndGet() % PROGRESSO_A_CADA == 0) {
            System.out.println(resumo());
        }
    }

    /** Downloads, cópias do cache, tempo decorrido e tentativas HTTP que falharam até aqui. */
    public String resumo() {
        long minutos = TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - inicio);
        return fonte + ": " + baixados.get() + " arquivo(s) baixado(s) e " + doCache.get() + " do cache em " + minutos
                + " min; tentativas com falha até aqui: " + ctx.http().tentativasFalhas();
    }

    public void contar(String chave, int quantidade) {
        contagens.merge(chave, quantidade, Integer::sum);
    }

    /** Período coberto pela coleta, gravado no cursor da fonte. */
    public void periodo(Meta.Periodo coberto) {
        this.periodo = coberto;
    }

    public Meta.Periodo periodo() {
        return periodo;
    }

    public List<Arquivo> arquivos() {
        synchronized (arquivos) {
            return List.copyOf(arquivos);
        }
    }

    public Map<String, Integer> contagens() {
        synchronized (contagens) {
            return Map.copyOf(contagens);
        }
    }

    /** Metadados de um arquivo já baixado, ou null se ainda não houve coleta. */
    public static Arquivo metadados(Contexto ctx, String fonte, String caminho) {
        Path meta = lateral(ctx.raw().resolve(fonte).resolve(caminho));
        return Files.exists(meta) ? Json.ler(meta, Arquivo.class) : null;
    }

    public static Path caminho(Contexto ctx, Arquivo arquivo) {
        return ctx.dados().resolve(arquivo.caminho());
    }

    private static Path lateral(Path destino) {
        return destino.resolveSibling(destino.getFileName() + ".meta.json");
    }
}
