package etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Caminhos, cliente HTTP e instante da execução. Tudo relativo à raiz do repositório. */
public record Contexto(Path raiz, Path dados, Http http, Validador validador, Instant agora, String repositorioUrl) {

    public static Contexto doAmbiente() {
        Path raiz = acharRaiz(Path.of("").toAbsolutePath());
        Path dados = raiz.resolve(System.getenv().getOrDefault("ETL_DADOS", "data")).normalize();
        String repositorio = urlDoRepositorio();
        String agente = Projeto.NOME + "/" + Projeto.VERSAO + (repositorio == null ? "" : " (+" + repositorio + ")");
        int concorrencia = Integer.parseInt(System.getenv().getOrDefault("ETL_CONCORRENCIA", "2"));
        return new Contexto(raiz, dados, new Http(agente, concorrencia), new Validador(raiz.resolve("schemas")),
                Instant.now().truncatedTo(ChronoUnit.SECONDS), repositorio);
    }

    /** A raiz é a pasta com schemas/ e contrib/. Aceita rodar a partir de etl/. */
    static Path acharRaiz(Path inicio) {
        for (Path p = inicio; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("schemas")) && Files.isDirectory(p.resolve("contrib"))) {
                return p;
            }
        }
        throw new IllegalStateException("rode o ETL a partir da raiz do repositório (pasta com schemas/ e contrib/)");
    }

    /** URL do repositório para o User-Agent: ETL_REPO_URL ou, no GitHub Actions, as variáveis do runner. */
    static String urlDoRepositorio() {
        String explicita = System.getenv("ETL_REPO_URL");
        if (explicita != null && !explicita.isBlank()) {
            return explicita.strip();
        }
        String servidor = System.getenv("GITHUB_SERVER_URL");
        String repositorio = System.getenv("GITHUB_REPOSITORY");
        return servidor != null && repositorio != null ? servidor + "/" + repositorio : null;
    }

    public Path raw() {
        return dados.resolve("raw");
    }

    public Path normalizado() {
        return dados.resolve("normalizado");
    }

    public Path publicado() {
        return dados.resolve("publicado");
    }

    public Path contrib() {
        return raiz.resolve("contrib");
    }

    public LocalDate hoje() {
        return LocalDate.ofInstant(agora, ZoneOffset.UTC);
    }

    /** Caminho relativo à pasta de dados, como aparece nos metadados. */
    public String relativo(Path arquivo) {
        return dados.relativize(arquivo).toString().replace('\\', '/');
    }
}
