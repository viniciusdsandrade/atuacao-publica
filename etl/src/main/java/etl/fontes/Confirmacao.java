package etl.fontes;

import java.time.Duration;
import java.util.function.Predicate;

import etl.Coleta;
import etl.Coleta.Politica;

/**
 * Confirmação de resposta vazia. Alguns servidores respondem 200 com lista vazia, em vez de erro, quando recebem
 * consultas demais ou quando falham por dentro (a verba da ALEPE). Guardar essa resposta como verdadeira apaga dado
 * real e, nos anos passados, para sempre. A resposta vazia só é aceita depois de consultas novas, pela rede.
 */
final class Confirmacao {

    /** Consultas para aceitar uma resposta vazia: a primeira e duas confirmações. */
    static final int TENTATIVAS = 3;
    static final Duration PAUSA = Duration.ofSeconds(3);

    private Confirmacao() {
    }

    /** Uma consulta ao servidor com a política dada. */
    interface Busca {
        Coleta.Arquivo buscar(Politica politica) throws Exception;
    }

    /**
     * Refaz a consulta, sempre pela rede, enquanto a resposta for vazia, até o limite de tentativas. A primeira
     * tentativa já foi feita. Devolve a última resposta: vazia só se todas vieram vazias. Falha na confirmação
     * mantém a resposta anterior.
     */
    static Coleta.Arquivo confirmandoVazio(Busca busca, Politica politica, Predicate<Coleta.Arquivo> vazio, Coleta.Arquivo primeira,
                                           int tentativas, Duration pausa) throws Exception {
        Coleta.Arquivo atual = primeira;
        for (int feitas = 1; feitas < tentativas && vazio.test(atual); feitas++) {
            Thread.sleep(pausa);
            Coleta.Arquivo nova = busca.buscar(Politica.SEMPRE);
            atual = nova != null ? nova : atual;
        }
        return atual;
    }
}
