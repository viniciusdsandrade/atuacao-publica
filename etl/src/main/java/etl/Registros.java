package etl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Registros tipados do modelo canônico (schemas/registros.schema.json). Saída em snake_case. */
public final class Registros {

    private Registros() {
    }

    /** Proveniência de todo registro automático. */
    public record Prov(String fonteId, String urlOrigem, Instant capturadoEm) {

        public static Prov de(String fonteId, String urlOrigem, Coleta.Arquivo arquivo) {
            return new Prov(fonteId, urlOrigem, arquivo.capturadoEm());
        }
    }

    /** Arquivo ou endpoint de onde saíram os registros de um bloco. */
    public record FonteUsada(String fonteId, String urlOrigem, Instant capturadoEm) {

        public static FonteUsada de(String fonteId, Coleta.Arquivo arquivo) {
            return new FonteUsada(fonteId, arquivo.url(), arquivo.capturadoEm());
        }
    }

    public record Mandato(String cargo, String esfera, String ente, String uf, LocalDate inicio, LocalDate fim,
                          String situacao, String condicao, Integer legislatura, Integer eleicao, String textoOficial,
                          @JsonUnwrapped Prov prov) {
    }

    public record Voto(String votacaoId, String data, String orgao, String proposicao, String descricao, String voto,
                       String partidoNaData, String orientacaoPartido, String orientacaoPartidoBancada,
                       String orientacaoGoverno, @JsonUnwrapped Prov prov) {
    }

    public record Participacao(String periodo, int votacoesNominais, int votosRegistrados, Integer semRegistro,
                               Map<String, Integer> porTipo, @JsonUnwrapped Prov prov) {
    }

    public record Despesa(LocalDate data, int ano, int mes, String categoria, String fornecedorNome,
                          String fornecedorCnpj, BigDecimal valor, String documentoNumero, String documentoUrl,
                          @JsonUnwrapped Prov prov) {
    }

    public record DespesaAgregada(int ano, Integer mes, String categoria, BigDecimal valor, int quantidade,
                                  @JsonUnwrapped Prov prov) {
    }

    public record Proposicao(String idFonte, String tipo, Integer numero, Integer ano, String identificacao,
                             String ementa, String situacao, List<String> temas, String dataApresentacao,
                             Integer ordemAssinatura, Boolean proponente, String urlTexto, @JsonUnwrapped Prov prov) {
    }

    public record Discurso(String data, String tipo, String fase, String resumo, String transcricaoUrl,
                           @JsonUnwrapped Prov prov) {
    }

    public record Evento(String idFonte, String data, String tipo, String titulo, String orgao, String situacao,
                         String presenca, @JsonUnwrapped Prov prov) {
    }

    public record OrgaoOuFrente(String tipo, String nome, String sigla, String papel, LocalDate inicio, LocalDate fim,
                                @JsonUnwrapped Prov prov) {
    }

    public record Candidatura(int eleicao, int turno, String cargo, String ente, String nomeUrna, String partido,
                              String numero, String situacaoCandidatura, String resultado,
                              BigDecimal bensTotalDeclarado, String planoDeGovernoUrl, String planoDeGovernoArquivo,
                              @JsonUnwrapped Prov prov) {
    }

    public record AtoNormativo(String idFonte, String tipo, String numero, LocalDate data, String nome, String ementa,
                               @JsonUnwrapped Prov prov) {
    }

    public record LinkOficial(String rotulo, String url, @JsonUnwrapped Prov prov) {
    }

    /**
     * Pessoa como uma fonte a vê, antes do vínculo entre fontes.
     * Não guarda dado sensível: o vínculo lê as chaves do cache bruto, em memória (ADR-0003).
     */
    /** Pessoa de uma casa legislativa. Quem saiu continua na lista, com emExercicio falso (ADR-0006). */
    public record Pessoa(String fonte, String idFonte, String nome, String cargo, String esfera, String ente, String uf,
                         String partido, String fotoUrl, String situacao, boolean emExercicio, List<LinkOficial> links) {
    }
}
