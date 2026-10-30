package etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Cursor por fonte (_meta/{fonte}.json) e relatório de execução (_meta/execucao.json). */
public final class Meta {

    public record Periodo(LocalDate inicio, LocalDate fim) {
    }

    public record Cursor(String fonte, String status, Instant ultimaExecucao, Instant ultimaColetaOk, Periodo periodo,
                         Map<String, Integer> contagens, List<Coleta.Arquivo> arquivos, List<String> erros) {

        public static Cursor vazio(String fonte) {
            return new Cursor(fonte, "nao_executada", null, null, null, Map.of(), List.of(), List.of());
        }

        public boolean desatualizado() {
            return "falha".equals(status);
        }
    }

    public record Projeto(String nome, String versao, String repositorioUrl) {
    }

    public record StatusFonte(String fonte, String status, Instant ultimaColetaOk, String mensagem) {
    }

    public record Rejeitado(String arquivo, String motivo) {
    }

    public record Execucao(Projeto projeto, Instant iniciadoEm, Instant terminadoEm, List<StatusFonte> fontes,
                           int publicados, List<Rejeitado> rejeitados) {
    }

    private Meta() {
    }

    public static Path pasta(Contexto ctx) {
        return ctx.publicado().resolve("_meta");
    }

    public static Cursor cursor(Contexto ctx, String fonte) {
        Path arquivo = pasta(ctx).resolve(fonte + ".json");
        return Files.exists(arquivo) ? Json.ler(arquivo, Cursor.class) : Cursor.vazio(fonte);
    }

    public static void gravar(Contexto ctx, Cursor cursor) {
        Json.gravar(pasta(ctx).resolve(cursor.fonte() + ".json"), cursor, true);
    }
}
