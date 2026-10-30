package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Contexto;
import etl.Http;
import etl.Validador;

/** Regras de contribuição manual: allowlist, domínio da fonte e bloqueio de dado pessoal. Dados sintéticos. */
class ContribTest {

    private static final Path RAIZ = acharRaiz();

    @TempDir
    Path tmp;
    private Contexto ctx;

    @BeforeEach
    void preparar() throws IOException {
        Files.createDirectories(tmp.resolve("contrib"));
        Files.copy(RAIZ.resolve("contrib/fontes.yaml"), tmp.resolve("contrib/fontes.yaml"));
        Files.copy(RAIZ.resolve("contrib/mandatos.yaml"), tmp.resolve("contrib/mandatos.yaml"));
        ctx = new Contexto(tmp, tmp.resolve("data"), new Http("teste/0", 1), new Validador(RAIZ.resolve("schemas")),
                Instant.parse("2026-10-30T12:00:00Z"), null);
    }

    @Test
    void aceitaEntradaValida() throws IOException {
        gravar("agenda_oficial", entrada("planalto-agenda",
                "https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica/2026-10-27",
                "Reunião de trabalho"));
        assertDoesNotThrow(() -> new Contrib().normalize(ctx));
    }

    @Test
    void recusaFonteForaDaAllowlist() throws IOException {
        gravar("agenda_oficial", entrada("jornal-qualquer", "https://www.gov.br/planalto/pt-br/x", "Reunião"));
        assertThrows(IllegalStateException.class, () -> new Contrib().normalize(ctx));
    }

    @Test
    void recusaUrlForaDoDominioDaFonte() throws IOException {
        gravar("agenda_oficial", entrada("planalto-agenda", "https://exemplo.invalid/agenda", "Reunião"));
        assertThrows(IllegalStateException.class, () -> new Contrib().normalize(ctx));
    }

    @Test
    void recusaCpfTelefoneEEmailNoTexto() throws IOException {
        for (String texto : new String[] {"Reunião com 123.456.789-09", "Contato (61) 3215-5000", "Contato x@exemplo.invalid"}) {
            gravar("agenda_oficial", entrada("planalto-agenda",
                    "https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica/2026-10-27", texto));
            assertThrows(IllegalStateException.class, () -> new Contrib().normalize(ctx), texto);
        }
    }

    @Test
    void recusaFonteDeNivelNaoAceitoParaOTipo() throws IOException {
        gravar("agenda_oficial", entrada("t1-site-mandato", "https://exemplo.invalid/agenda", "Reunião"));
        assertThrows(IllegalStateException.class, () -> new Contrib().normalize(ctx));
    }

    private void gravar(String tipo, String conteudo) throws IOException {
        Path arquivo = tmp.resolve("contrib/federal/presidente/tse-990000000001/" + tipo + ".yaml");
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, conteudo);
    }

    private static String entrada(String fonte, String url, String texto) {
        return """
                - tipo: agenda_oficial
                  data: 2026-10-27
                  texto_oficial: "%s"
                  fonte_id: %s
                  url_origem: %s
                  snapshot_url: https://web.archive.org/web/20261027120000/%s
                  capturado_em: 2026-10-27T15:00:00Z
                  contribuidor: login-exemplo
                """.formatted(texto, fonte, url, url);
    }

    private static Path acharRaiz() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("schemas")) && Files.isDirectory(p.resolve("contrib"))) {
                return p;
            }
        }
        throw new IllegalStateException("raiz do repositório não encontrada");
    }
}
