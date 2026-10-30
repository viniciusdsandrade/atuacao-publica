package etl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/** Catálogo de fontes (contrib/fontes.yaml): allowlist de fonte_id e links oficiais por UF. */
public final class Catalogo implements Fonte {

    public record Item(String id, String nome, String orgao, String esfera, String ente, String categoria,
                       String nivelConfianca, String urlBase,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Boolean dominioLivre, String formato,
                       String autenticacao, String licenca, String limitacoes, String status, String validadoEm) {

        /** A URL pertence à fonte: começa pela url_base (http e https equivalem). */
        public boolean contem(String url) {
            if (Boolean.TRUE.equals(dominioLivre)) {
                return true;
            }
            return semEsquema(url).startsWith(semEsquema(urlBase).replaceAll("/+$", ""));
        }

        private static String semEsquema(String url) {
            return url.replaceFirst("^https?://", "").toLowerCase();
        }
    }

    public record Verificacao(String id, String url, int status) {
    }

    private final Map<String, Item> itens = new LinkedHashMap<>();

    public Catalogo() {
    }

    public static Catalogo carregar(Contexto ctx) {
        Path arquivo = ctx.contrib().resolve("fontes.yaml");
        JsonNode arvore = Json.lerYaml(arquivo);
        List<String> erros = ctx.validador().validar("fontes", arvore);
        if (!erros.isEmpty()) {
            throw new IllegalStateException("contrib/fontes.yaml inválido: " + String.join("; ", erros));
        }
        Catalogo catalogo = new Catalogo();
        for (JsonNode no : arvore) {
            Item item = Json.YAML.treeToValue(no, Item.class);
            if (catalogo.itens.put(item.id(), item) != null) {
                throw new IllegalStateException("fonte_id repetido em contrib/fontes.yaml: " + item.id());
            }
        }
        return catalogo;
    }

    public Item item(String id) {
        return itens.get(id);
    }

    public List<Item> itens() {
        return List.copyOf(itens.values());
    }

    /** Portal da transparência e diário oficial da UF, nesta ordem. */
    public List<Item> linksDaUf(String uf) {
        List<Item> links = new ArrayList<>();
        for (Item item : itens.values()) {
            if (uf.equals(item.ente()) && ("transparencia".equals(item.categoria()) || "diario_oficial".equals(item.categoria()))) {
                links.add(item);
            }
        }
        links.sort((a, b) -> a.categoria().compareTo(b.categoria()) * -1);
        return links;
    }

    @Override
    public String nome() {
        return "fontes";
    }

    /** Revalida a url_base de cada fonte. Só informa: site fora do ar não bloqueia o ETL. */
    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        Catalogo catalogo = carregar(ctx);
        List<Verificacao> resultado = new ArrayList<>();
        int falhas = 0;
        for (Item item : catalogo.itens()) {
            int status = ctx.http().status(item.urlBase());
            resultado.add(new Verificacao(item.id(), item.urlBase(), status));
            if (status < 200 || status >= 400) {
                falhas++;
                System.out.println("fontes: " + item.id() + " respondeu " + status);
            }
        }
        Json.gravar(ctx.raw().resolve("fontes").resolve("validacao.json"), resultado, true);
        coleta.contar("fontes", resultado.size());
        coleta.contar("sem_resposta_valida", falhas);
        System.out.println("fontes: " + (resultado.size() - falhas) + " de " + resultado.size() + " responderam");
    }

    @Override
    public void normalize(Contexto ctx) {
        carregar(ctx);
    }
}
