package etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import etl.Publicador.Perfil;
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
            p.cargo = Json.texto(pessoa, "cargo");
            p.esfera = Json.texto(pessoa, "esfera");
            p.ente = Json.texto(pessoa, "ente");
            p.uf = Json.texto(pessoa, "uf");
            p.partido = Json.texto(pessoa, "partido");
            p.situacao = Json.texto(pessoa, "situacao");
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
     * Presidente e governadores eleitos cujo mandato vigora hoje, segundo contrib/mandatos.yaml.
     * O TSE registra a eleição; renúncia ou sucessão entram por contribuição com fonte T0.
     */
    private static List<Perfil> executivos(Contexto ctx, Catalogo catalogo, List<Perfil> existentes, Map<String, Tse.Chave> chaves,
                                           Map<String, List<JsonNode>> porCpf, List<JsonNode> candidaturas) {
        List<Perfil> perfis = new ArrayList<>();
        Map<String, Perfil> legisladorPorCpf = new HashMap<>();
        for (Perfil p : existentes) {
            for (String sq : p.tse.values()) {
                Tse.Chave chave = chaves.get(sq);
                if (chave != null && chave.cpf() != null) {
                    legisladorPorCpf.put(chave.cpf(), p);
                }
            }
        }
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            LocalDate inicio = LocalDate.parse(Json.texto(regra, "inicio"));
            LocalDate fim = LocalDate.parse(Json.texto(regra, "fim"));
            if (ctx.hoje().isBefore(inicio) || !ctx.hoje().isBefore(fim)) {
                continue;
            }
            String cargo = Json.texto(regra, "cargo");
            int eleicao = Json.inteiro(regra, "eleicao");
            for (JsonNode c : candidaturas) {
                if (c.get("ano").asInt() != eleicao || !c.get("eleito").asBoolean()
                        || !cargo.equals(Texto.normalizarNome(c.get("cargo").stringValue()).toLowerCase())) {
                    continue;
                }
                String sq = c.get("sq").stringValue();
                Tse.Chave chave = chaves.get(sq);
                Perfil p = chave == null || chave.cpf() == null ? null : legisladorPorCpf.get(chave.cpf());
                boolean novo = p == null;
                if (novo) {
                    p = new Perfil();
                    p.id = "tse-" + sq;
                    p.chaveExterna = "tse:" + sq;
                    p.fonteCli = "tse";
                    p.nome = Json.texto(c, "nome_urna");
                    p.situacao = "Eleito (TSE)";
                    if (chave != null && chave.cpf() != null) {
                        anexarCandidaturas(p, porCpf.getOrDefault(chave.cpf(), List.of(c)));
                    } else {
                        anexarCandidaturas(p, List.of(c));
                    }
                }
                String uf = Json.texto(c, "uf");
                p.cargo = cargo;
                p.esfera = cargo.equals("presidente") ? "federal" : "estadual";
                p.ente = cargo.equals("presidente") ? "BR" : uf;
                p.uf = cargo.equals("presidente") ? null : uf;
                p.partido = Json.texto(c, "partido");
                ObjectNode mandato = Json.SAIDA.createObjectNode();
                mandato.put("cargo", cargo).put("esfera", p.esfera).put("ente", p.ente).put("uf", p.uf)
                        .put("inicio", inicio.toString()).put("fim", fim.toString()).put("situacao", "Eleito (TSE)")
                        .putNull("condicao").putNull("legislatura").put("eleicao", eleicao);
                for (String campo : List.of("texto_oficial", "fonte_id", "url_origem", "snapshot_url", "capturado_em", "contribuidor")) {
                    mandato.set(campo, regra.get(campo));
                }
                p.bloco("mandatos").add(mandato);
                p.fontes.put("mandatos", new ArrayList<>(List.of(Json.SAIDA.createObjectNode()
                        .<ObjectNode>set("fonte_id", regra.get("fonte_id")).<ObjectNode>set("url_origem", regra.get("url_origem"))
                        .set("capturado_em", regra.get("capturado_em")))));
                if (p.esfera.equals("estadual")) {
                    for (Catalogo.Item item : catalogo.linksDaUf(uf)) {
                        p.links.add(Json.SAIDA.createObjectNode().put("rotulo", item.nome()).put("url", item.urlBase())
                                .put("fonte_id", item.id()).put("url_origem", item.urlBase())
                                .put("capturado_em", item.validadoEm() + "T00:00:00Z"));
                    }
                }
                if (novo) {
                    perfis.add(p);
                }
            }
        }
        return perfis;
    }

    /** Atos do Poder Executivo federal publicados no período do mandato presidencial vigente. */
    private static void atos(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("executivo").resolve("atos.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode atos = Json.ler(arquivo);
        for (Perfil p : perfis) {
            if (!"presidente".equals(p.cargo)) {
                continue;
            }
            JsonNode mandato = p.bloco("mandatos").getFirst();
            String inicio = mandato.get("inicio").stringValue();
            String fim = mandato.get("fim").stringValue();
            List<JsonNode> usadas = p.fontes.computeIfAbsent("atos_normativos", k -> new ArrayList<>());
            for (JsonNode ato : atos) {
                String data = Json.texto(ato, "data");
                if (data != null && data.compareTo(inicio) >= 0 && data.compareTo(fim) < 0) {
                    p.bloco("atos_normativos").add(ato);
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
