package etl;

import java.util.List;

/** Ponto de entrada: {@code java -jar etl.jar <fetch|normalize|publish> [fonte]}. */
public final class Main {

    private static final List<String> COMANDOS = List.of("fetch", "normalize", "publish");

    public static void main(String[] args) {
        if (args.length == 0 || !COMANDOS.contains(args[0])) {
            System.err.println("uso: java -jar etl.jar <fetch|normalize|publish> [fonte]");
            System.exit(2);
        }
        System.out.println(Projeto.NOME + " " + Projeto.VERSAO + ": comando " + args[0]);
    }
}
