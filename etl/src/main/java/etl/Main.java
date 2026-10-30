package etl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import etl.fontes.Camara;
import etl.fontes.Contrib;
import etl.fontes.Executivo;
import etl.fontes.Senado;
import etl.fontes.Tse;

/** Ponto de entrada: {@code java -jar etl.jar <fetch|normalize|publish> [fonte]}, a partir da raiz do repositório. */
public final class Main {

    private static final Map<String, Fonte> FONTES = new LinkedHashMap<>();

    static {
        registrar(new Camara());
        registrar(new Senado());
        registrar(new Tse());
        registrar(new Executivo());
        registrar(new Contrib());
        registrar(new Catalogo());
    }

    /** Fontes que rodam quando o comando não recebe fonte. contrib e fontes rodam só quando pedidas no fetch. */
    private static final List<String> FETCH_PADRAO = List.of("camara", "senado", "tse", "executivo");
    private static final List<String> NORMALIZE_PADRAO = List.of("camara", "senado", "tse", "executivo", "contrib");

    public static void main(String[] args) {
        // O validador de schema usa SLF4J sem provedor; o aviso de "no providers" não interessa aqui.
        System.setProperty("slf4j.internal.verbosity", "ERROR");
        if (args.length == 0 || !List.of("fetch", "normalize", "publish").contains(args[0])
                || (args.length > 1 && !FONTES.containsKey(args[1]))) {
            System.err.println("uso: java -jar etl/target/etl.jar <fetch|normalize|publish> [fonte]");
            System.err.println("fontes: " + String.join(", ", FONTES.keySet()));
            System.exit(2);
        }
        Contexto ctx = Contexto.doAmbiente();
        String filtro = args.length > 1 ? args[1] : null;
        int codigo = switch (args[0]) {
            case "fetch" -> fetch(ctx, selecionar(filtro, FETCH_PADRAO));
            case "normalize" -> normalize(ctx, selecionar(filtro, NORMALIZE_PADRAO));
            default -> new Publicador(ctx).publicar();
        };
        System.exit(codigo);
    }

    private static void registrar(Fonte fonte) {
        FONTES.put(fonte.nome(), fonte);
    }

    private static List<Fonte> selecionar(String filtro, List<String> padrao) {
        List<Fonte> selecionadas = new ArrayList<>();
        for (String nome : filtro == null ? padrao : List.of(filtro)) {
            selecionadas.add(FONTES.get(nome));
        }
        return selecionadas;
    }

    /** Cada fonte roda isolada: a falha de uma não impede as outras. O cursor registra o resultado. */
    static int fetch(Contexto ctx, List<Fonte> fontes) {
        int falhas = 0;
        for (Fonte fonte : fontes) {
            Meta.Cursor anterior = Meta.cursor(ctx, fonte.nome());
            Coleta coleta = new Coleta(ctx, fonte.nome());
            String status = "ok";
            List<String> erros = List.of();
            try {
                fonte.fetch(ctx, coleta);
                if (!coleta.reservasUsadas().isEmpty()) {
                    status = "parcial";
                    erros = coleta.reservasUsadas();
                    erros.forEach(e -> System.err.println(fonte.nome() + ": coleta parcial: " + e));
                }
            } catch (Exception e) {
                status = "falha";
                erros = List.of(mensagem(e));
                falhas++;
                System.err.println(fonte.nome() + ": falha na coleta: " + mensagem(e));
            }
            boolean coletou = !"falha".equals(status);
            Meta.gravar(ctx, new Meta.Cursor(fonte.nome(), status, ctx.agora(),
                    coletou ? ctx.agora() : anterior.ultimaColetaOk(),
                    coletou ? periodo(coleta, anterior) : anterior.periodo(),
                    coletou ? coleta.contagens() : anterior.contagens(),
                    coletou ? coleta.arquivos() : anterior.arquivos(), erros));
        }
        return falhas == 0 ? 0 : 1;
    }

    static int normalize(Contexto ctx, List<Fonte> fontes) {
        int falhas = 0;
        for (Fonte fonte : fontes) {
            try {
                fonte.normalize(ctx);
            } catch (Exception e) {
                falhas++;
                System.err.println(fonte.nome() + ": falha na normalização: " + mensagem(e));
            }
        }
        return falhas == 0 ? 0 : 1;
    }

    private static Meta.Periodo periodo(Coleta coleta, Meta.Cursor anterior) {
        return coleta.periodo() != null ? coleta.periodo() : anterior.periodo();
    }

    static String mensagem(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
