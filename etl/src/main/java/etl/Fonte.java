package etl;

/** Adaptador de fonte: fetch baixa para data/raw; normalize converte para data/normalizado. */
public interface Fonte {

    String nome();

    void fetch(Contexto ctx, Coleta coleta) throws Exception;

    void normalize(Contexto ctx) throws Exception;
}
