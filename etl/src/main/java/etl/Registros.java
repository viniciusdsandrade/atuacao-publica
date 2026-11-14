package etl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import tools.jackson.databind.JsonNode;

/** Registros tipados do modelo canônico (schemas/registros.schema.json). Saída em snake_case. */
public final class Registros {

    private Registros() {
    }

    /**
     * Fontes de cada bloco, tiradas da proveniência dos próprios registros e sem repetição. O bloco cita os mesmos
     * arquivos, com o mesmo instante de captura, que os seus registros. Bloco vazio fica sem fonte.
     */
    public static Map<String, List<FonteUsada>> fontesDosBlocos(JsonNode blocos) {
        Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
        for (var bloco : blocos.properties()) {
            if (bloco.getKey().equals("fontes")) {
                continue;
            }
            Set<FonteUsada> usadas = new LinkedHashSet<>();
            for (JsonNode registro : bloco.getValue()) {
                String fonteId = Json.texto(registro, "fonte_id");
                String url = Json.texto(registro, "url_origem");
                String capturado = Json.texto(registro, "capturado_em");
                if (fonteId != null && url != null && capturado != null) {
                    usadas.add(new FonteUsada(fonteId, url, Instant.parse(capturado)));
                }
            }
            if (!usadas.isEmpty()) {
                fontes.put(bloco.getKey(), List.copyOf(usadas));
            }
        }
        return fontes;
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

    /**
     * Despesa de parlamentar. A descrição é o texto do lançamento na fonte (destino e motivo de uma diária, por exemplo)
     * e só sai no JSON quando a fonte a traz.
     */
    public record Despesa(LocalDate data, int ano, int mes, String categoria, String fornecedorNome,
                          String fornecedorCnpj, BigDecimal valor, String documentoNumero, String documentoUrl,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String descricao, @JsonUnwrapped Prov prov) {

        /** Lançamento sem descrição: é o caso da maior parte das fontes. */
        public Despesa(LocalDate data, int ano, int mes, String categoria, String fornecedorNome, String fornecedorCnpj,
                       BigDecimal valor, String documentoNumero, String documentoUrl, Prov prov) {
            this(data, ano, mes, categoria, fornecedorNome, fornecedorCnpj, valor, documentoNumero, documentoUrl, null, prov);
        }
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
                              List<RedeDeclarada> redesDeclaradas, @JsonUnwrapped Prov prov) {
    }

    /** Rede social ou site que o candidato declarou ao TSE no registro da candidatura (ADR-0031). */
    public record RedeDeclarada(String rede, String url, @JsonUnwrapped Prov prov) {
    }

    /** Soma de uma categoria da prestação de contas, com a quantidade de lançamentos. */
    public record Soma(String categoria, BigDecimal valor, int quantidade) {
    }

    /** Fornecedor pessoa jurídica de campanha, pelo CNPJ. Pessoa física nunca aparece por nome. */
    public record FornecedorDeCampanha(String nome, String cnpj, BigDecimal valor, int quantidade) {
    }

    /** Receitas da campanha de uma candidatura, somadas por fonte e por origem (ADR-0032). */
    public record ReceitasDeCampanha(int eleicao, String cargo, String tipo, BigDecimal total, int quantidade,
                                     List<Soma> porFonte, List<Soma> porOrigem, @JsonUnwrapped Prov prov) {
    }

    /** Despesas contratadas da campanha de uma candidatura, por tipo, com os maiores fornecedores com CNPJ (ADR-0032). */
    public record DespesasDeCampanha(int eleicao, String cargo, String tipo, BigDecimal total, int quantidade,
                                     List<Soma> porTipo, List<FornecedorDeCampanha> maioresFornecedores,
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
