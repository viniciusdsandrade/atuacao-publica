package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import etl.Coleta;

class ConfirmacaoTest {

    @Test
    void listaVaziaSoEAceitaDepoisDeConfirmadaNaRede() throws Exception {
        java.util.function.Function<String, Coleta.Arquivo> arquivo = marca -> new Coleta.Arquivo("u", "c", marca, 2, Instant.EPOCH);
        java.util.function.Predicate<Coleta.Arquivo> vazio = a -> a.sha256().equals("vazio");

        // A segunda consulta ainda vem vazia, a terceira traz dado: para aí, sempre pela rede.
        java.util.List<String> respostas = new java.util.ArrayList<>(java.util.List.of("vazio", "cheio"));
        java.util.List<Coleta.Politica> politicas = new java.util.ArrayList<>();
        Coleta.Arquivo final1 = Confirmacao.confirmandoVazio(politica -> {
            politicas.add(politica);
            return arquivo.apply(respostas.removeFirst());
        }, Coleta.Politica.SE_AUSENTE, vazio, arquivo.apply("vazio"), 3, java.time.Duration.ZERO);
        assertEquals("cheio", final1.sha256());
        assertEquals(java.util.List.of(Coleta.Politica.SEMPRE, Coleta.Politica.SEMPRE), politicas);

        // Todas vazias: aceita a vazia depois das três tentativas (a primeira mais duas confirmações).
        int[] chamadas = {0};
        Coleta.Arquivo final2 = Confirmacao.confirmandoVazio(politica -> {
            chamadas[0]++;
            return arquivo.apply("vazio");
        }, Coleta.Politica.SE_AUSENTE, vazio, arquivo.apply("vazio"), Confirmacao.TENTATIVAS, java.time.Duration.ZERO);
        assertEquals("vazio", final2.sha256());
        assertEquals(Confirmacao.TENTATIVAS - 1, chamadas[0]);

        // Primeira resposta com dado: nenhuma consulta a mais. Falha na confirmação mantém a resposta anterior.
        int[] semChamadas = {0};
        assertEquals("cheio", Confirmacao.confirmandoVazio(politica -> {
            semChamadas[0]++;
            return arquivo.apply("vazio");
        }, Coleta.Politica.SE_AUSENTE, vazio, arquivo.apply("cheio"), 3, java.time.Duration.ZERO).sha256());
        assertEquals(0, semChamadas[0]);
        assertEquals("vazio", Confirmacao.confirmandoVazio(politica -> null, Coleta.Politica.SE_AUSENTE, vazio, arquivo.apply("vazio"), 3,
                java.time.Duration.ZERO).sha256());
    }
}
