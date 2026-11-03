package etl.fontes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import etl.Catalogo;
import etl.Coleta;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/** Registros manuais de contrib/{esfera}/{cargo}/{politico_id}/{tipo}.yaml e regras de contrib/mandatos.yaml. */
public final class Contrib implements Fonte {

    /** Registro manual pronto para publicar. */
    public record Registro(String politicoId, String esfera, String cargo, String tipo, String nivelFonte, JsonNode entrada) {
    }

    private static final Pattern CAMINHO = Pattern.compile(
            "^(federal|estadual|municipal)/(presidente|governador|deputado_federal|senador|deputado_estadual|prefeito|vice_prefeito|vereador)/([a-z]{2,10}-[0-9A-Za-z]{1,32})/"
                    + "(posicionamento_declarado|agenda_oficial|link_oficial|mandato)\\.yaml$");

    /** Nível de fonte aceito por tipo de registro. T2 nunca entra direto: o registro aponta para a fonte T0. */
    private static final Map<String, Set<String>> NIVEIS = Map.of(
            "posicionamento_declarado", Set.of("T0", "T1"),
            "agenda_oficial", Set.of("T0"),
            "link_oficial", Set.of("T0", "T1"),
            "mandato", Set.of("T0"));

    /** Lista curta de palavras de juízo. Gera só aviso: a revisão humana decide. */
    private static final List<String> JUIZO = List.of(
            "absurdo", "absurda", "brilhante", "corrupto", "corrupta", "criminoso", "criminosa", "desastroso",
            "desastrosa", "escandalo", "escandaloso", "excelente", "genial", "golpista", "heroico", "heroica",
            "incrivel", "lamentavel", "mentiroso", "mentirosa", "otimo", "otima", "pessimo", "pessima", "polemico",
            "polemica", "ridiculo", "ridicula", "traidor", "traidora", "vergonhoso", "vergonhosa");

    @Override
    public String nome() {
        return "contrib";
    }

    /** Confere se url_origem e snapshot_url de cada entrada respondem. */
    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        List<String> falhas = new ArrayList<>();
        int verificadas = 0;
        for (Path arquivo : arquivos(ctx)) {
            for (JsonNode entrada : Json.lerYaml(arquivo)) {
                for (String campo : List.of("url_origem", "snapshot_url")) {
                    String url = Json.texto(entrada, campo);
                    if (url == null) {
                        continue;
                    }
                    int status = ctx.http().status(url);
                    verificadas++;
                    if (status < 200 || status >= 400) {
                        falhas.add(relativo(ctx, arquivo) + ": " + campo + " respondeu " + status);
                    }
                }
            }
        }
        coleta.contar("urls_verificadas", verificadas);
        coleta.contar("urls_com_falha", falhas.size());
        falhas.forEach(f -> System.out.println("contrib: " + f));
        System.out.println("contrib: " + (verificadas - falhas.size()) + " de " + verificadas + " URLs responderam");
        if (!falhas.isEmpty()) {
            throw new IllegalStateException(falhas.size() + " URL(s) de contrib sem resposta válida");
        }
    }

    /** Valida schema, allowlist, nível da fonte, domínio, dado pessoal e lint de juízo. */
    @Override
    public void normalize(Contexto ctx) throws IOException {
        Catalogo catalogo = Catalogo.carregar(ctx);
        List<String> erros = new ArrayList<>();
        List<Registro> registros = new ArrayList<>();

        JsonNode mandatos = Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"));
        erros.addAll(prefixar("contrib/mandatos.yaml", ctx.validador().validar("mandatos", mandatos)));
        for (JsonNode regra : mandatos) {
            erros.addAll(prefixar("contrib/mandatos.yaml", regrasDeFonte(catalogo, regra, Set.of("T0"))));
        }

        for (Path arquivo : arquivos(ctx)) {
            String caminho = ctx.contrib().relativize(arquivo).toString().replace('\\', '/');
            var m = CAMINHO.matcher(caminho);
            if (!m.matches()) {
                erros.add("contrib/" + caminho + ": caminho fora do padrão {esfera}/{cargo}/{politico_id}/{tipo}.yaml");
                continue;
            }
            String tipo = m.group(4);
            JsonNode entradas = Json.lerYaml(arquivo);
            List<String> doArquivo = new ArrayList<>(ctx.validador().validar("contrib", entradas));
            if (doArquivo.isEmpty()) {
                for (JsonNode entrada : entradas) {
                    if (!tipo.equals(Json.texto(entrada, "tipo"))) {
                        doArquivo.add("tipo da entrada difere do nome do arquivo (" + tipo + ")");
                    }
                    doArquivo.addAll(regrasDeFonte(catalogo, entrada, NIVEIS.get(tipo)));
                    doArquivo.addAll(Guarda.violacoes(entrada));
                    if (Instant.parse(Json.texto(entrada, "capturado_em")).isAfter(ctx.agora())) {
                        doArquivo.add("capturado_em no futuro");
                    }
                    avisarJuizo("contrib/" + caminho, entrada);
                    String nivel = catalogo.item(Json.texto(entrada, "fonte_id")) == null ? null
                            : catalogo.item(Json.texto(entrada, "fonte_id")).nivelConfianca();
                    registros.add(new Registro(m.group(3), m.group(1), m.group(2), tipo, nivel, entrada));
                }
            }
            erros.addAll(prefixar("contrib/" + caminho, doArquivo));
        }

        if (!erros.isEmpty()) {
            erros.forEach(e -> System.out.println("contrib: ERRO " + e));
            throw new IllegalStateException(erros.size() + " erro(s) em contrib/");
        }
        Path saida = ctx.normalizado().resolve("contrib");
        Json.gravar(saida.resolve("registros.json"), registros);
        Json.gravar(saida.resolve("mandatos.json"), mandatos);
        System.out.println("contrib: " + registros.size() + " registro(s) manuais e " + mandatos.size() + " regra(s) de mandato válidos");
    }

    private static List<String> regrasDeFonte(Catalogo catalogo, JsonNode entrada, Set<String> niveis) {
        List<String> erros = new ArrayList<>();
        String fonteId = Json.texto(entrada, "fonte_id");
        Catalogo.Item fonte = catalogo.item(fonteId);
        if (fonte == null) {
            erros.add("fonte_id " + fonteId + " fora da allowlist (contrib/fontes.yaml)");
            return erros;
        }
        if (!niveis.contains(fonte.nivelConfianca())) {
            erros.add("fonte " + fonteId + " é " + fonte.nivelConfianca() + "; este tipo aceita " + niveis);
        }
        String url = Json.texto(entrada, "url_origem");
        if (url != null && !fonte.contem(url)) {
            erros.add("url_origem fora do domínio da fonte " + fonteId);
        }
        return erros;
    }

    private static void avisarJuizo(String arquivo, JsonNode entrada) {
        for (String campo : List.of("texto_oficial", "resumo_factual")) {
            String texto = Texto.normalizarNome(Json.texto(entrada, campo));
            if (texto == null) {
                continue;
            }
            for (String palavra : JUIZO) {
                if (Pattern.compile("\\b" + palavra.toUpperCase() + "\\b").matcher(texto).find()) {
                    System.out.println("contrib: AVISO " + arquivo + ": palavra de juízo \"" + palavra + "\" em " + campo
                            + " (confira se é texto literal da fonte)");
                }
            }
        }
    }

    private static List<String> prefixar(String arquivo, List<String> erros) {
        return erros.stream().map(e -> arquivo + ": " + e).toList();
    }

    private static String relativo(Contexto ctx, Path arquivo) {
        return "contrib/" + ctx.contrib().relativize(arquivo).toString().replace('\\', '/');
    }

    /** Arquivos de registro manual. fontes.yaml e mandatos.yaml, na raiz de contrib/, ficam de fora. */
    static List<Path> arquivos(Contexto ctx) throws IOException {
        try (Stream<Path> todos = Files.walk(ctx.contrib())) {
            return todos.filter(p -> p.toString().endsWith(".yaml"))
                    .filter(p -> !p.getParent().equals(ctx.contrib()))
                    .sorted()
                    .toList();
        }
    }
}
