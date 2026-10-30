package etl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cache de dados brutos em data/raw/{fonte}. Cada arquivo tem ao lado um .meta.json
 * com URL, hash, tamanho e instante de captura.
 */
public final class Coleta {

    public enum Politica {
        /** Baixa de novo a cada execução (período aberto). */
        SEMPRE,
        /** Reusa o arquivo em cache (período fechado). */
        SE_AUSENTE
    }

    public record Arquivo(String url, String caminho, String sha256, long bytes, Instant capturadoEm) {
    }

    private final Contexto ctx;
    private final String fonte;
    private final List<Arquivo> arquivos = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> contagens = Collections.synchronizedMap(new TreeMap<>());
    private volatile Meta.Periodo periodo;

    public Coleta(Contexto ctx, String fonte) {
        this.ctx = ctx;
        this.fonte = fonte;
    }

    public Arquivo obter(String url, String caminho, String accept, Politica politica)
            throws IOException, InterruptedException {
        Path destino = ctx.raw().resolve(fonte).resolve(caminho);
        Path meta = lateral(destino);
        if (politica == Politica.SE_AUSENTE && Files.exists(meta)) {
            Arquivo emCache = Json.ler(meta, Arquivo.class);
            if (Files.exists(ctx.dados().resolve(emCache.caminho()))) {
                arquivos.add(emCache);
                return emCache;
            }
        }
        Http.Baixado baixado = ctx.http().baixar(url, destino, accept);
        Arquivo arquivo = new Arquivo(url, ctx.relativo(baixado.arquivo()), baixado.sha256(), baixado.bytes(), ctx.agora());
        Json.gravar(meta, arquivo, true);
        arquivos.add(arquivo);
        return arquivo;
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
