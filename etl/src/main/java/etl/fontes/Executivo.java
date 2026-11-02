package etl.fontes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.AtoNormativo;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Atos do Poder Executivo federal: leis, leis complementares, medidas provisórias, decretos e vetos.
 * Fonte: API do Senado (/legislacao/lista e /materia/vetos). A agenda presidencial entra por contribuição manual.
 */
public final class Executivo implements Fonte {

    static final List<String> TIPOS = List.of("LEI", "LCP", "MPV", "DEC");

    @Override
    public String nome() {
        return "executivo";
    }

    /**
     * Início da cobertura: o primeiro mandato presidencial de contrib/mandatos.yaml (dado, não código),
     * nunca antes de Camara.INICIO_DA_COBERTURA (ADR-0009).
     * Os atos de mandatos encerrados continuam no perfil de quem os assinou (ADR-0006).
     */
    static LocalDate inicioDaCobertura(Contexto ctx) {
        LocalDate primeiro = null;
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            LocalDate inicio = LocalDate.parse(Json.texto(regra, "inicio"));
            if ("presidente".equals(Json.texto(regra, "cargo")) && !ctx.hoje().isBefore(inicio)
                    && (primeiro == null || inicio.isBefore(primeiro))) {
                primeiro = inicio;
            }
        }
        if (primeiro == null) {
            throw new IllegalStateException("contrib/mandatos.yaml sem regra de mandato presidencial já iniciado");
        }
        return primeiro.isBefore(Camara.INICIO_DA_COBERTURA) ? Camara.INICIO_DA_COBERTURA : primeiro;
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        int anoAtual = ctx.hoje().getYear();
        LocalDate inicio = inicioDaCobertura(ctx);
        for (int ano = inicio.getYear(); ano <= anoAtual; ano++) {
            Politica politica = Politica.porAno(ano, ctx.hoje());
            for (String tipo : TIPOS) {
                coleta.obter(Senado.API + "/legislacao/lista?tipo=" + tipo + "&ano=" + ano, "legislacao/" + tipo + "-" + ano + ".json",
                        "application/json", politica);
            }
            coleta.obter(Senado.API + "/materia/vetos/" + ano, "vetos/" + ano + ".json", "application/json", politica);
        }
        coleta.periodo(new Meta.Periodo(inicio, ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) {
        List<AtoNormativo> atos = new ArrayList<>();
        LocalDate inicio = inicioDaCobertura(ctx);
        for (int ano = inicio.getYear(); ano <= ctx.hoje().getYear(); ano++) {
            for (String tipo : TIPOS) {
                Coleta.Arquivo arq = arquivo(ctx, "legislacao/" + tipo + "-" + ano + ".json");
                for (JsonNode n : Json.lista(Json.ler(Coleta.caminho(ctx, arq)).path("ListaDocumento").path("documentos").path("documento"))) {
                    String id = Json.texto(n, "id");
                    String data = Texto.dataBr(Json.texto(n, "dataassinatura"));
                    atos.add(new AtoNormativo(id, tipo, Json.texto(n, "numero"), data == null ? null : LocalDate.parse(data),
                            Json.texto(n, "normaNome"), Texto.semEntidades(Json.texto(n, "ementa")),
                            Prov.de("senado-api", Senado.API + "/legislacao/" + id, arq)));
                }
            }
            Coleta.Arquivo arqVetos = arquivo(ctx, "vetos/" + ano + ".json");
            for (JsonNode v : Json.lista(Json.ler(Coleta.caminho(ctx, arqVetos)).path("ListaVetosAnoCN").path("Vetos").path("Veto"))) {
                JsonNode materia = v.path("Materia");
                JsonNode vetada = v.path("MateriaVetada");
                String numero = Json.texto(materia, "Numero") + "/" + Json.texto(materia, "Ano");
                String alvo = Json.texto(vetada, "Sigla") == null ? "" : " (" + Json.texto(vetada, "Sigla") + " "
                        + Json.texto(vetada, "Numero") + "/" + Json.texto(vetada, "Ano") + ")";
                String data = Json.texto(v, "DataPublicacao");
                atos.add(new AtoNormativo(Json.texto(v, "Codigo"), "VET", numero,
                        data == null ? null : LocalDate.parse(data.substring(0, 10)), "VET " + numero + alvo,
                        Texto.semEntidades(Json.texto(vetada, "Ementa")),
                        Prov.de("senado-api", Senado.API + "/materia/vetos/" + ano, arqVetos)));
            }
        }
        Json.gravar(ctx.normalizado().resolve("executivo").resolve("atos.json"), atos);
        System.out.println("executivo: " + atos.size() + " atos normativos normalizados");
    }

    private static Coleta.Arquivo arquivo(Contexto ctx, String relativo) {
        Coleta.Arquivo arquivo = Coleta.metadados(ctx, "executivo", relativo);
        if (arquivo == null) {
            throw new IllegalStateException("arquivo bruto ausente: raw/executivo/" + relativo + " (rode fetch executivo)");
        }
        return arquivo;
    }
}
