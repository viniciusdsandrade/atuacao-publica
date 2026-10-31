package etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import etl.Publicador.Perfil;
import etl.fontes.Sapl;
import etl.fontes.Tse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Monta os perfis a partir das fontes normalizadas e resolve o vínculo entre fontes (ADR-0002).
 * As chaves sensíveis do vínculo são lidas do cache bruto, só em memória, e nunca são gravadas (ADR-0003).
 */
final class Vinculo {

    private Vinculo() {
    }

    static List<Perfil> perfis(Contexto ctx, Catalogo catalogo) {
        List<Perfil> perfis = new ArrayList<>();
        perfis.addAll(legisladores(ctx, "camara", "cam", "camara"));
        perfis.addAll(legisladores(ctx, "senado", "sen", "senado"));
        perfis.addAll(legisladores(ctx, "sapl", "sapl-" + Sapl.IBGE, "sapl"));
        List<JsonNode> candidaturas = Tse.linhas(ctx);
        if (!candidaturas.isEmpty()) {
            Map<String, Tse.Chave> chaves = Tse.chaves(ctx);
            Map<String, List<JsonNode>> porCpf = new HashMap<>();
            for (JsonNode c : candidaturas) {
                Tse.Chave chave = chaves.get(c.get("sq").stringValue());
                if (chave != null && chave.cpf() != null) {
                    porCpf.computeIfAbsent(chave.cpf(), k -> new ArrayList<>()).add(c);
                }
            }
            vincularLegisladores(ctx, perfis, chaves, porCpf, candidaturas);
            perfis.addAll(executivos(ctx, catalogo, perfis, chaves, porCpf, candidaturas));
            perfis.addAll(legisladoresEleitos(ctx, perfis, chaves, porCpf, candidaturas));
            atos(ctx, perfis);
        }
        return perfis;
    }

    /** Um perfil por pessoa de uma casa legislativa, com os blocos normalizados da casa. */
    static List<Perfil> legisladores(Contexto ctx, String fonte, String prefixo, String chaveId) {
        Path pasta = ctx.normalizado().resolve(fonte);
        List<Perfil> perfis = new ArrayList<>();
        if (!Files.exists(pasta.resolve("pessoas.json"))) {
            return perfis;
        }
        for (JsonNode pessoa : Json.ler(pasta.resolve("pessoas.json"))) {
            String idFonte = pessoa.get("id_fonte").stringValue();
            Perfil p = new Perfil();
            p.id = prefixo + "-" + idFonte;
            p.chaveExterna = fonte + ":" + idFonte;
            p.fonteCli = fonte;
            p.nome = Json.texto(pessoa, "nome");
            boolean emExercicio = !pessoa.hasNonNull("em_exercicio") || pessoa.get("em_exercicio").asBoolean();
            p.papel(Json.texto(pessoa, "cargo"), Json.texto(pessoa, "esfera"), Json.texto(pessoa, "ente"), Json.texto(pessoa, "uf"),
                    Json.texto(pessoa, "partido"), Json.texto(pessoa, "situacao"),
                    emExercicio ? Perfil.EM_EXERCICIO : Perfil.FORA_DE_EXERCICIO, null);
            p.foto = Json.texto(pessoa, "foto_url");
            p.ids.put(chaveId, idFonte);
            pessoa.path("links").forEach(p.links::add);
            JsonNode blocos = Json.ler(pasta.resolve("blocos").resolve(idFonte + ".json"));
            for (var bloco : blocos.properties()) {
                if (!bloco.getKey().equals("fontes")) {
                    bloco.getValue().forEach(p.bloco(bloco.getKey())::add);
                }
            }
            for (var usadas : blocos.path("fontes").properties()) {
                List<JsonNode> lista = p.fontes.computeIfAbsent(usadas.getKey(), k -> new ArrayList<>());
                usadas.getValue().forEach(lista::add);
            }
            perfis.add(p);
        }
        return perfis;
    }

    /** Câmara por CPF; Senado por nome civil, UF e data de nascimento entre candidaturas ao Senado. */
    private static void vincularLegisladores(Contexto ctx, List<Perfil> perfis, Map<String, Tse.Chave> chaves,
                                             Map<String, List<JsonNode>> porCpf, List<JsonNode> candidaturas) {
        Map<String, String> cpfPorSenadoChave = new HashMap<>();
        for (JsonNode c : candidaturas) {
            Tse.Chave chave = chaves.get(c.get("sq").stringValue());
            if (chave != null && chave.cpf() != null && chave.cargo().matches("SENADOR|1º SUPLENTE|2º SUPLENTE")) {
                cpfPorSenadoChave.put(chave.nomeCivil() + "|" + chave.uf() + "|" + chave.nascimento(), chave.cpf());
            }
        }
        for (Perfil p : perfis) {
            String cpf = null;
            if ("camara".equals(p.fonteCli)) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "camara", "deputados/" + p.ids.get("camara") + ".json");
                if (arq != null) {
                    String bruto = Texto.digitos(Json.texto(Json.ler(Coleta.caminho(ctx, arq)).path("dados"), "cpf"));
                    cpf = bruto.length() == 11 ? bruto : null;
                }
            } else if ("senado".equals(p.fonteCli)) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "senado", "senador/" + p.ids.get("senado") + ".json");
                if (arq != null) {
                    JsonNode s = Json.ler(Coleta.caminho(ctx, arq)).path("DetalheParlamentar").path("Parlamentar");
                    String chave = Texto.normalizarNome(Json.texto(s.path("IdentificacaoParlamentar"), "NomeCompletoParlamentar"))
                            + "|" + p.uf + "|" + Json.texto(s.path("DadosBasicosParlamentar"), "DataNascimento");
                    cpf = cpfPorSenadoChave.get(chave);
                }
            }
            if (cpf != null) {
                anexarCandidaturas(p, porCpf.getOrDefault(cpf, List.of()));
            }
        }
    }

    private static void anexarCandidaturas(Perfil p, List<JsonNode> linhas) {
        for (JsonNode linha : linhas) {
            p.bloco("candidaturas").add(linha.get("registro"));
            p.tse.put(String.valueOf(linha.get("ano").asInt()), linha.get("sq").stringValue());
            p.fontes.computeIfAbsent("candidaturas", k -> new ArrayList<>());
            JsonNode registro = linha.get("registro");
            ObjectNode usada = Json.SAIDA.createObjectNode();
            usada.set("fonte_id", registro.get("fonte_id"));
            usada.set("url_origem", registro.get("url_origem"));
            usada.set("capturado_em", registro.get("capturado_em"));
            if (!p.fontes.get("candidaturas").contains(usada)) {
                p.fontes.get("candidaturas").add(usada);
            }
        }
    }

    /**
     * Presidente e governadores de todas as regras de contrib/mandatos.yaml: mandato vigente, encerrado ou com posse
     * prevista (ADR-0006). O TSE registra a eleição; renúncia ou sucessão entram por contribuição com fonte T0.
     */
    private static List<Perfil> executivos(Contexto ctx, Catalogo catalogo, List<Perfil> existentes, Map<String, Tse.Chave> chaves,
                                           Map<String, List<JsonNode>> porCpf, List<JsonNode> candidaturas) {
        List<Perfil> novos = new ArrayList<>();
        Map<String, Perfil> perfilPorCpf = perfilPorCpf(existentes, chaves);
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            LocalDate inicio = LocalDate.parse(Json.texto(regra, "inicio"));
            LocalDate fim = LocalDate.parse(Json.texto(regra, "fim"));
            String cargo = Json.texto(regra, "cargo");
            int eleicao = Json.inteiro(regra, "eleicao");
            Fase fase = Fase.de(ctx.hoje(), inicio, fim, eleicao);
            for (JsonNode c : eleitos(candidaturas, eleicao, cargo)) {
                Perfil p = perfilDoEleito(c, chaves, porCpf, perfilPorCpf, novos);
                String uf = Json.texto(c, "uf");
                String esfera = cargo.equals("presidente") ? "federal" : "estadual";
                String ente = cargo.equals("presidente") ? "BR" : uf;
                p.papel(cargo, esfera, ente, cargo.equals("presidente") ? null : uf, Json.texto(c, "partido"), fase.situacao(),
                        fase.prioridade(), inicio);
                ObjectNode mandato = Json.SAIDA.createObjectNode();
                mandato.put("cargo", cargo).put("esfera", esfera).put("ente", ente).put("uf", cargo.equals("presidente") ? null : uf)
                        .put("inicio", inicio.toString()).put("fim", fim.toString()).put("situacao", fase.situacao())
                        .putNull("condicao").putNull("legislatura").put("eleicao", eleicao);
                for (String campo : List.of("texto_oficial", "fonte_id", "url_origem", "snapshot_url", "capturado_em", "contribuidor")) {
                    mandato.set(campo, regra.get(campo));
                }
                p.bloco("mandatos").add(mandato);
                usarFonte(p, "mandatos", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", regra.get("fonte_id"))
                        .<ObjectNode>set("url_origem", regra.get("url_origem")).set("capturado_em", regra.get("capturado_em")));
                if (esfera.equals("estadual")) {
                    for (Catalogo.Item item : catalogo.linksDaUf(uf)) {
                        ObjectNode link = Json.SAIDA.createObjectNode().put("rotulo", item.nome()).put("url", item.urlBase())
                                .put("fonte_id", item.id()).put("url_origem", item.urlBase())
                                .put("capturado_em", item.validadoEm() + "T00:00:00Z");
                        if (!p.links.contains(link)) {
                            p.links.add(link);
                        }
                    }
                }
            }
        }
        return novos;
    }

    /**
     * Deputados e senadores da eleição mais recente que a casa ainda não registra em exercício (ADR-0006).
     * A posse é o início da legislatura, segundo a Câmara (Constituição, art. 57, § 4º). Antes dela, o perfil mostra a
     * posse prevista. Depois, quem a casa ainda não registra continua no arquivo, sem registro de exercício.
     */
    private static List<Perfil> legisladoresEleitos(Contexto ctx, List<Perfil> existentes, Map<String, Tse.Chave> chaves,
                                                    Map<String, List<JsonNode>> porCpf, List<JsonNode> candidaturas) {
        List<Perfil> novos = new ArrayList<>();
        Coleta.Arquivo arquivoLeg = Coleta.metadados(ctx, "camara", "legislatura.json");
        if (arquivoLeg == null) {
            return novos;
        }
        JsonNode leg = Json.ler(Coleta.caminho(ctx, arquivoLeg)).path("dados").path(0);
        int idLeg = Json.inteiro(leg, "id");
        LocalDate inicioLeg = LocalDate.parse(Json.texto(leg, "dataInicio"));
        LocalDate fimLeg = LocalDate.parse(Json.texto(leg, "dataFim"));
        int proxima = fimLeg.getYear() - 1;
        boolean haProxima = candidaturas.stream().anyMatch(c -> c.get("ano").asInt() == proxima);
        int eleicao = haProxima ? proxima : inicioLeg.getYear() - 1;
        LocalDate posse = haProxima ? fimLeg.plusDays(1) : inicioLeg;
        int legislatura = haProxima ? idLeg + 1 : idLeg;
        boolean antesDaPosse = ctx.hoje().isBefore(posse);
        ObjectNode fonte = Json.SAIDA.createObjectNode().put("fonte_id", "camara-api")
                .put("url_origem", "https://dadosabertos.camara.leg.br/api/v2/legislaturas/" + idLeg)
                .put("capturado_em", arquivoLeg.capturadoEm().toString());
        Map<String, Perfil> perfilPorCpf = perfilPorCpf(existentes, chaves);
        for (String cargo : List.of("deputado_federal", "senador")) {
            String casa = cargo.equals("deputado_federal") ? "camara" : "senado";
            String situacao = antesDaPosse ? "Eleito em " + eleicao + "; posse prevista em " + posse
                    : "Eleito em " + eleicao + "; sem registro de exercício " + (casa.equals("camara") ? "na Câmara dos Deputados" : "no Senado Federal");
            for (JsonNode c : eleitos(candidaturas, eleicao, cargo)) {
                Tse.Chave chave = chaves.get(c.get("sq").stringValue());
                Perfil existente = chave == null || chave.cpf() == null ? null : perfilPorCpf.get(chave.cpf());
                if (!antesDaPosse && existente != null && casa.equals(existente.fonteCli)) {
                    continue;
                }
                Perfil p = perfilDoEleito(c, chaves, porCpf, perfilPorCpf, novos);
                String uf = Json.texto(c, "uf");
                p.papel(cargo, "federal", "BR", uf, Json.texto(c, "partido"), situacao,
                        antesDaPosse ? Perfil.POSSE_PREVISTA : Perfil.FORA_DE_EXERCICIO, posse);
                ObjectNode mandato = Json.SAIDA.createObjectNode();
                mandato.put("cargo", cargo).put("esfera", "federal").put("ente", "BR").put("uf", uf)
                        .put("inicio", posse.toString()).putNull("fim").put("situacao", situacao)
                        .putNull("condicao").put("legislatura", legislatura).put("eleicao", eleicao);
                mandato.setAll(fonte);
                p.bloco("mandatos").add(mandato);
                usarFonte(p, "mandatos", fonte);
            }
        }
        return novos;
    }

    /** Fase de um mandato com datas de contrib/mandatos.yaml, no intervalo [inicio, fim). */
    record Fase(String situacao, int prioridade) {
        static Fase de(LocalDate hoje, LocalDate inicio, LocalDate fim, int eleicao) {
            if (hoje.isBefore(inicio)) {
                return new Fase("Eleito em " + eleicao + "; posse prevista em " + inicio, Perfil.POSSE_PREVISTA);
            }
            if (hoje.isBefore(fim)) {
                return new Fase("Mandato vigente (eleito em " + eleicao + "), até " + fim, Perfil.EM_EXERCICIO);
            }
            return new Fase("Mandato encerrado em " + fim + " (eleito em " + eleicao + ")", Perfil.FORA_DE_EXERCICIO);
        }
    }

    private static List<JsonNode> eleitos(List<JsonNode> candidaturas, int eleicao, String cargo) {
        List<JsonNode> eleitos = new ArrayList<>();
        for (JsonNode c : candidaturas) {
            if (c.get("ano").asInt() == eleicao && c.get("eleito").asBoolean()
                    && cargo.equals(Texto.normalizarNome(c.get("cargo").stringValue()).toLowerCase().replace(' ', '_'))) {
                eleitos.add(c);
            }
        }
        return eleitos;
    }

    /** Perfis já montados por CPF, a chave sensível que fica só em memória (ADR-0003). */
    private static Map<String, Perfil> perfilPorCpf(List<Perfil> perfis, Map<String, Tse.Chave> chaves) {
        Map<String, Perfil> porCpf = new HashMap<>();
        for (Perfil p : perfis) {
            for (String sq : p.tse.values()) {
                Tse.Chave chave = chaves.get(sq);
                if (chave != null && chave.cpf() != null) {
                    porCpf.putIfAbsent(chave.cpf(), p);
                }
            }
        }
        return porCpf;
    }

    /** O perfil da pessoa eleita: o que já existe para o mesmo CPF ou um perfil novo, a partir do TSE. */
    private static Perfil perfilDoEleito(JsonNode c, Map<String, Tse.Chave> chaves, Map<String, List<JsonNode>> porCpf,
                                         Map<String, Perfil> perfilPorCpf, List<Perfil> novos) {
        String sq = c.get("sq").stringValue();
        Tse.Chave chave = chaves.get(sq);
        String cpf = chave == null ? null : chave.cpf();
        Perfil p = cpf == null ? null : perfilPorCpf.get(cpf);
        if (p != null) {
            return p;
        }
        p = new Perfil();
        p.id = "tse-" + sq;
        p.chaveExterna = "tse:" + sq;
        p.fonteCli = "tse";
        p.nome = Json.texto(c, "nome_urna");
        anexarCandidaturas(p, cpf == null ? List.of(c) : porCpf.getOrDefault(cpf, List.of(c)));
        if (cpf != null) {
            perfilPorCpf.put(cpf, p);
        }
        novos.add(p);
        return p;
    }

    private static void usarFonte(Perfil p, String bloco, ObjectNode fonte) {
        List<JsonNode> usadas = p.fontes.computeIfAbsent(bloco, k -> new ArrayList<>());
        if (!usadas.contains(fonte)) {
            usadas.add(fonte);
        }
    }

    /** Atos do Poder Executivo federal publicados em cada mandato presidencial já iniciado da pessoa. */
    private static void atos(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("executivo").resolve("atos.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode atos = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("presidente")) {
                continue;
            }
            List<JsonNode> usadas = p.fontes.computeIfAbsent("atos_normativos", k -> new ArrayList<>());
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                if (!"presidente".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0) {
                    continue;
                }
                for (JsonNode ato : atos) {
                    String data = Json.texto(ato, "data");
                    if (data != null && data.compareTo(inicio) >= 0 && (fim == null || data.compareTo(fim) < 0)) {
                        p.bloco("atos_normativos").add(ato);
                    }
                }
            }
            for (JsonNode ato : atos) {
                ObjectNode usada = Json.SAIDA.createObjectNode().put("fonte_id", "senado-api")
                        .put("url_origem", Json.texto(ato, "url_origem").replaceFirst("/legislacao/[0-9]+$", "/legislacao/lista"))
                        .put("capturado_em", Json.texto(ato, "capturado_em"));
                if (!usadas.contains(usada)) {
                    usadas.add(usada);
                }
            }
        }
    }
}
