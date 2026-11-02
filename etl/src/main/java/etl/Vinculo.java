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
            // O TSE mascara o CPF em alguns arquivos (eleição de 2024): nome civil e data de nascimento, em memória, fazem a ponte.
            Map<String, List<JsonNode>> porPessoa = new HashMap<>();
            for (JsonNode c : candidaturas) {
                Tse.Chave chave = chaves.get(c.get("sq").stringValue());
                if (chave != null && chave.cpf() != null) {
                    porCpf.computeIfAbsent(chave.cpf(), k -> new ArrayList<>()).add(c);
                }
                if (pessoa(chave) != null) {
                    porPessoa.computeIfAbsent(pessoa(chave), k -> new ArrayList<>()).add(c);
                }
            }
            Ligacoes ligacoes = new Ligacoes(chaves, porCpf, porPessoa);
            vincularLegisladores(ctx, perfis, ligacoes, candidaturas);
            perfis.addAll(executivos(ctx, catalogo, perfis, ligacoes, candidaturas));
            perfis.addAll(legisladoresEleitos(ctx, perfis, ligacoes, candidaturas));
            atos(ctx, perfis);
            despesasDoExecutivoMunicipal(ctx, perfis);
            despesasDoExecutivoFederal(ctx, perfis);
            agendaPresidencial(ctx, perfis);
            atosMunicipais(ctx, perfis);
        }
        return perfis;
    }

    /** Chaves do TSE em memória (ADR-0003): candidaturas por CPF e por nome civil com data de nascimento. */
    record Ligacoes(Map<String, Tse.Chave> chaves, Map<String, List<JsonNode>> porCpf, Map<String, List<JsonNode>> porPessoa) {
        /** Todas as candidaturas da pessoa: as do CPF e as do nome com nascimento (eleição com CPF mascarado), sem repetir. */
        List<JsonNode> daPessoa(Tse.Chave chave, JsonNode candidatura) {
            List<JsonNode> todas = new ArrayList<>();
            if (chave != null && chave.cpf() != null) {
                todas.addAll(porCpf.getOrDefault(chave.cpf(), List.of()));
            }
            String p = pessoa(chave);
            if (p != null) {
                for (JsonNode c : porPessoa.getOrDefault(p, List.of())) {
                    if (todas.stream().noneMatch(t -> t.get("sq").equals(c.get("sq")))) {
                        todas.add(c);
                    }
                }
            }
            return todas.isEmpty() ? List.of(candidatura) : todas;
        }

        /** O mesmo, a partir só do CPF (Câmara e Senado): a pessoa vem da primeira candidatura com esse CPF. */
        List<JsonNode> doCpf(String cpf) {
            List<JsonNode> doCpf = porCpf.getOrDefault(cpf, List.of());
            return doCpf.isEmpty() ? doCpf : daPessoa(chaves.get(doCpf.getFirst().get("sq").stringValue()), doCpf.getFirst());
        }

        /** Nome civil com nascimento da pessoa desse CPF, pela primeira candidatura dela; null sem candidatura. */
        String pessoaDoCpf(String cpf) {
            List<JsonNode> doCpf = cpf == null ? List.of() : porCpf.getOrDefault(cpf, List.of());
            return doCpf.isEmpty() ? null : pessoa(chaves.get(doCpf.getFirst().get("sq").stringValue()));
        }
    }

    static String pessoa(Tse.Chave chave) {
        return chave == null || chave.nomeCivil() == null || chave.nascimento() == null ? null
                : chave.nomeCivil() + "|" + chave.nascimento();
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
            // Só mandatos e fontes ficam em memória: contribuições e vínculo os consultam. Os blocos pesados (votos,
            // despesas, proposições) são lidos de novo ao publicar cada perfil: a memória é a de um perfil, não a do arquivo todo.
            Path arquivoDeBlocos = pasta.resolve("blocos").resolve(idFonte + ".json");
            JsonNode blocos = Json.ler(arquivoDeBlocos);
            blocos.path("mandatos").forEach(p.bloco("mandatos")::add);
            p.arquivosDeBlocos.add(arquivoDeBlocos);
            for (var usadas : blocos.path("fontes").properties()) {
                List<JsonNode> lista = p.fontes.computeIfAbsent(usadas.getKey(), k -> new ArrayList<>());
                usadas.getValue().forEach(lista::add);
            }
            perfis.add(p);
        }
        return perfis;
    }

    /** Câmara por CPF; Senado por nome civil, UF e data de nascimento; SAPL por nome civil e de urna (ADR-0007). */
    private static void vincularLegisladores(Contexto ctx, List<Perfil> perfis, Ligacoes ligacoes, List<JsonNode> candidaturas) {
        Map<String, Tse.Chave> chaves = ligacoes.chaves();
        Map<String, List<JsonNode>> porCpf = ligacoes.porCpf();
        Map<String, String> cpfPorSenadoChave = new HashMap<>();
        Map<String, JsonNode> candPorSaplNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorSaplNomeUrna = new HashMap<>();
        for (JsonNode c : candidaturas) {
            Tse.Chave chave = chaves.get(c.get("sq").stringValue());
            if (chave != null) {
                if (chave.cpf() != null && chave.cargo().matches("SENADOR|1º SUPLENTE|2º SUPLENTE")) {
                    cpfPorSenadoChave.put(chave.nomeCivil() + "|" + chave.uf() + "|" + chave.nascimento(), chave.cpf());
                } else if ("VEREADOR".equals(chave.cargo())) {
                    // A candidatura mais recente com o nome vale como ponte; as demais eleições vêm pelo CPF, em memória.
                    candPorSaplNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorSaplNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                }
            }
        }
        // Uma pessoa, duas casas (deputado que virou senador, ou deputado que virou vereador): um perfil só, pelo CPF
        // em memória ou, quando o TSE mascara o CPF, pelo nome civil com data de nascimento.
        Map<String, Perfil> legisladorPorCpf = new HashMap<>();
        Map<String, Perfil> legisladorPorPessoa = new HashMap<>();
        List<Perfil> fundidos = new ArrayList<>();
        for (Perfil p : perfis) {
            String cpf = null;
            Tse.Chave chaveSapl = null;
            JsonNode candSapl = null;
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
            } else if ("sapl".equals(p.fonteCli)) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "sapl", "parlamentares/" + p.ids.get("sapl") + ".json");
                if (arq != null) {
                    JsonNode parl = Json.ler(Coleta.caminho(ctx, arq));
                    String nomeCompleto = Texto.normalizarNome(Json.texto(parl, "nome_completo"));
                    String nomeParlamentar = Texto.normalizarNome(Json.texto(parl, "nome_parlamentar"));
                    JsonNode cand = candPorSaplNomeCivil.get(nomeCompleto);
                    if (cand == null) {
                        cand = candPorSaplNomeUrna.get(nomeParlamentar);
                    }
                    if (cand == null) {
                        cand = candPorSaplNomeUrna.get(nomeCompleto);
                    }
                    if (cand != null) {
                        candSapl = cand;
                        chaveSapl = chaves.get(cand.get("sq").stringValue());
                        cpf = chaveSapl == null ? null : chaveSapl.cpf();
                    }
                }
            }
            String pessoa = chaveSapl != null ? pessoa(chaveSapl) : ligacoes.pessoaDoCpf(cpf);
            Perfil alvo = cpf == null ? null : legisladorPorCpf.putIfAbsent(cpf, p);
            if (alvo == null && pessoa != null) {
                alvo = legisladorPorPessoa.putIfAbsent(pessoa, p);
            }
            if (alvo != null && alvo != p) {
                fundir(alvo, p);
                fundidos.add(p);
                continue;
            }
            if (pessoa != null) {
                legisladorPorPessoa.putIfAbsent(pessoa, p);
            }
            if (cpf != null) {
                anexarCandidaturas(p, ligacoes.doCpf(cpf));
            } else if (candSapl != null) {
                anexarCandidaturas(p, ligacoes.daPessoa(chaveSapl, candSapl));
            }
        }
        perfis.removeAll(fundidos);
        if (!fundidos.isEmpty()) {
            System.out.println("vinculo: " + fundidos.size() + " perfil(is) de outra casa fundido(s) na mesma pessoa");
        }
    }

    /** Junta no primeiro perfil (casa de maior precedência, ADR-0002) os cargos, blocos e fontes do segundo (ADR-0006). */
    private static void fundir(Perfil alvo, Perfil outro) {
        alvo.papel(outro.cargo, outro.esfera, outro.ente, outro.uf, outro.partido, outro.situacao, outro.prioridade, outro.inicioDoPapel);
        outro.ids.forEach(alvo.ids::putIfAbsent);
        outro.tse.forEach(alvo.tse::putIfAbsent);
        for (JsonNode link : outro.links) {
            if (!alvo.links.contains(link)) {
                alvo.links.add(link);
            }
        }
        outro.registros.forEach((bloco, lista) -> alvo.bloco(bloco).addAll(lista));
        outro.fontes.forEach((bloco, lista) -> {
            List<JsonNode> usadas = alvo.fontes.computeIfAbsent(bloco, k -> new ArrayList<>());
            lista.forEach(f -> {
                if (!usadas.contains(f)) {
                    usadas.add(f);
                }
            });
        });
        alvo.arquivosDeBlocos.addAll(outro.arquivosDeBlocos);
        alvo.aliases.add(outro.id);
        alvo.aliases.addAll(outro.aliases);
        if (alvo.foto == null) {
            alvo.foto = outro.foto;
        }
    }

    private static JsonNode maisRecente(JsonNode a, JsonNode b) {
        return b.get("ano").asInt() > a.get("ano").asInt() ? b : a;
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
    private static List<Perfil> executivos(Contexto ctx, Catalogo catalogo, List<Perfil> existentes, Ligacoes ligacoes,
                                           List<JsonNode> candidaturas) {
        List<Perfil> novos = new ArrayList<>();
        Map<String, Perfil> perfilPorCpf = perfilPorCpf(existentes, ligacoes.chaves());
        Map<String, Perfil> perfilPorPessoa = perfilPorPessoa(existentes, ligacoes.chaves());
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            LocalDate inicio = LocalDate.parse(Json.texto(regra, "inicio"));
            LocalDate fim = LocalDate.parse(Json.texto(regra, "fim"));
            String cargo = Json.texto(regra, "cargo");
            int eleicao = Json.inteiro(regra, "eleicao");
            Fase fase = Fase.de(ctx.hoje(), inicio, fim, eleicao);
            for (JsonNode c : eleitos(candidaturas, eleicao, cargo)) {
                Perfil p = perfilDoEleito(c, ligacoes, perfilPorCpf, perfilPorPessoa, novos);
                String uf = Json.texto(c, "uf");
                boolean municipal = cargo.equals("prefeito") || cargo.equals("vice_prefeito");
                String esfera = cargo.equals("presidente") ? "federal" : (municipal ? "municipal" : "estadual");
                String ente = cargo.equals("presidente") ? "BR" : (municipal ? (regra.hasNonNull("ente") ? Json.texto(regra, "ente") : Sapl.IBGE) : uf);
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
                if (esfera.equals("estadual") || esfera.equals("municipal")) {
                    for (Catalogo.Item item : catalogo.linksDaUf(ente)) {
                        if (item.validadoEm() != null) {
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
        }
        return novos;
    }

    /**
     * Deputados e senadores da eleição mais recente que a casa ainda não registra em exercício (ADR-0006).
     * A posse é o início da legislatura, segundo a Câmara (Constituição, art. 57, § 4º). Antes dela, o perfil mostra a
     * posse prevista. Depois, quem a casa ainda não registra continua no arquivo, sem registro de exercício.
     */
    private static List<Perfil> legisladoresEleitos(Contexto ctx, List<Perfil> existentes, Ligacoes ligacoes,
                                                    List<JsonNode> candidaturas) {
        Map<String, Tse.Chave> chaves = ligacoes.chaves();
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
        Map<String, Perfil> perfilPorPessoa = perfilPorPessoa(existentes, chaves);
        for (String cargo : List.of("deputado_federal", "senador")) {
            String casa = cargo.equals("deputado_federal") ? "camara" : "senado";
            String situacao = antesDaPosse ? "Eleito em " + eleicao + "; posse prevista em " + posse
                    : "Eleito em " + eleicao + "; sem registro de exercício " + (casa.equals("camara") ? "na Câmara dos Deputados" : "no Senado Federal");
            for (JsonNode c : eleitos(candidaturas, eleicao, cargo)) {
                Tse.Chave chave = chaves.get(c.get("sq").stringValue());
                Perfil existente = chave == null || chave.cpf() == null ? null : perfilPorCpf.get(chave.cpf());
                if (existente == null && pessoa(chave) != null) {
                    existente = perfilPorPessoa.get(pessoa(chave));
                }
                if (!antesDaPosse && existente != null && casa.equals(existente.fonteCli)) {
                    continue;
                }
                Perfil p = perfilDoEleito(c, ligacoes, perfilPorCpf, perfilPorPessoa, novos);
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
                    && cargo.equals(Texto.normalizarNome(c.get("cargo").stringValue()).toLowerCase().replace(' ', '_').replace('-', '_'))) {
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

    /** O perfil da pessoa eleita: o que já existe para o mesmo CPF, ou para o mesmo nome civil e nascimento, ou um perfil novo. */
    private static Perfil perfilDoEleito(JsonNode c, Ligacoes ligacoes, Map<String, Perfil> perfilPorCpf,
                                         Map<String, Perfil> perfilPorPessoa, List<Perfil> novos) {
        String sq = c.get("sq").stringValue();
        Tse.Chave chave = ligacoes.chaves().get(sq);
        String cpf = chave == null ? null : chave.cpf();
        String pessoa = pessoa(chave);
        Perfil p = cpf == null ? null : perfilPorCpf.get(cpf);
        if (p == null && pessoa != null) {
            p = perfilPorPessoa.get(pessoa);
        }
        if (p != null) {
            return p;
        }
        p = new Perfil();
        p.id = "tse-" + sq;
        p.chaveExterna = "tse:" + sq;
        p.fonteCli = "tse";
        p.nome = Json.texto(c, "nome_urna");
        anexarCandidaturas(p, ligacoes.daPessoa(chave, c));
        if (cpf != null) {
            perfilPorCpf.put(cpf, p);
        }
        if (pessoa != null) {
            perfilPorPessoa.put(pessoa, p);
        }
        novos.add(p);
        return p;
    }

    private static Map<String, Perfil> perfilPorPessoa(List<Perfil> perfis, Map<String, Tse.Chave> chaves) {
        Map<String, Perfil> porPessoa = new HashMap<>();
        for (Perfil p : perfis) {
            for (String sq : p.tse.values()) {
                String pessoa = pessoa(chaves.get(sq));
                if (pessoa != null) {
                    porPessoa.putIfAbsent(pessoa, p);
                }
            }
        }
        return porPessoa;
    }

    private static void usarFonte(Perfil p, String bloco, ObjectNode fonte) {
        List<JsonNode> usadas = p.fontes.computeIfAbsent(bloco, k -> new ArrayList<>());
        if (!usadas.contains(fonte)) {
            usadas.add(fonte);
        }
    }

    /**
     * Despesas pagas pela administração direta do município em cada mandato de prefeito já iniciado (ADR-0008).
     * Só o órgão da prefeitura e o evento "Valor Pago" entram no perfil.
     */
    private static void despesasDoExecutivoMunicipal(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("tcesp").resolve("despesas.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode agregados = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("prefeito")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                String ente = Json.texto(mandato, "ente");
                if (!"prefeito".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0 || ente == null) {
                    continue;
                }
                for (JsonNode a : agregados) {
                    String orgao = Json.texto(a, "orgao");
                    String primeiroDia = String.format("%04d-%02d-01", a.get("ano").asInt(), a.get("mes").asInt());
                    boolean noMandato = primeiroDia.compareTo(inicio.substring(0, 8) + "01") >= 0 && (fim == null || primeiroDia.compareTo(fim) < 0);
                    if (!ente.equals(Json.texto(a, "ente")) || !noMandato || orgao == null
                            || !orgao.toUpperCase().startsWith("PREFEITURA MUNICIPAL") || !"Valor Pago".equals(Json.texto(a, "evento"))) {
                        continue;
                    }
                    ObjectNode registro = Json.SAIDA.createObjectNode();
                    registro.put("ano", a.get("ano").asInt()).put("mes", a.get("mes").asInt()).put("categoria", orgao)
                            .set("valor", a.get("valor"));
                    registro.put("quantidade", a.get("quantidade").asInt());
                    for (String campo : List.of("fonte_id", "url_origem", "capturado_em")) {
                        registro.set(campo, a.get(campo));
                    }
                    p.bloco("despesas_executivo").add(registro);
                    usarFonte(p, "despesas_executivo", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", a.get("fonte_id"))
                            .<ObjectNode>set("url_origem", a.get("url_origem")).set("capturado_em", a.get("capturado_em")));
                }
            }
        }
    }

    /** Compromissos da agenda oficial em cada mandato presidencial já iniciado (ADR-0011). */
    private static void agendaPresidencial(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("agenda").resolve("compromissos.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode compromissos = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("presidente")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                if (!"presidente".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0) {
                    continue;
                }
                for (JsonNode c : compromissos) {
                    String data = Json.texto(c, "data");
                    if (!"BR".equals(Json.texto(c, "ente")) || data == null || data.compareTo(inicio) < 0
                            || (fim != null && data.compareTo(fim) >= 0)) {
                        continue;
                    }
                    ObjectNode registro = ((ObjectNode) c).deepCopy();
                    registro.remove("ente");
                    if (registro.get("snapshot_url") == null || registro.get("snapshot_url").isNull()) {
                        registro.remove("snapshot_url");
                    }
                    p.bloco("agenda_oficial").add(registro);
                    usarFonte(p, "agenda_oficial", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", c.get("fonte_id"))
                            .put("url_origem", Json.texto(c, "url_origem").replaceAll("/[0-9]{4}-[0-9]{2}-[0-9]{2}$", ""))
                            .set("capturado_em", c.get("capturado_em")));
                }
            }
        }
    }

    /** Cartão de pagamento e viagens da Presidência da República em cada mandato presidencial já iniciado (ADR-0010). */
    private static void despesasDoExecutivoFederal(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("cgu").resolve("despesas.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode agregados = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("presidente")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                if (!"presidente".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0) {
                    continue;
                }
                for (JsonNode a : agregados) {
                    String primeiroDia = String.format("%04d-%02d-01", a.get("ano").asInt(), a.get("mes").asInt());
                    boolean noMandato = primeiroDia.compareTo(inicio.substring(0, 8) + "01") >= 0 && (fim == null || primeiroDia.compareTo(fim) < 0);
                    if (!"BR".equals(Json.texto(a, "ente")) || !noMandato) {
                        continue;
                    }
                    ObjectNode registro = Json.SAIDA.createObjectNode();
                    registro.put("ano", a.get("ano").asInt()).put("mes", a.get("mes").asInt()).put("categoria", Json.texto(a, "categoria"))
                            .set("valor", a.get("valor"));
                    registro.put("quantidade", a.get("quantidade").asInt());
                    for (String campo : List.of("fonte_id", "url_origem", "capturado_em")) {
                        registro.set(campo, a.get(campo));
                    }
                    p.bloco("despesas_executivo").add(registro);
                    usarFonte(p, "despesas_executivo", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", a.get("fonte_id"))
                            .<ObjectNode>set("url_origem", a.get("url_origem")).set("capturado_em", a.get("capturado_em")));
                }
            }
        }
    }

    /** Leis, leis complementares e decretos do município em cada mandato de prefeito já iniciado (ADR-0009). */
    private static void atosMunicipais(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("sapl").resolve("normas.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode normas = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("prefeito")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                String ente = Json.texto(mandato, "ente");
                if (!"prefeito".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0 || ente == null) {
                    continue;
                }
                for (JsonNode n : normas) {
                    String data = Json.texto(n, "data");
                    if (!ente.equals(Json.texto(n, "ente")) || data == null || data.compareTo(inicio) < 0
                            || (fim != null && data.compareTo(fim) >= 0)) {
                        continue;
                    }
                    ObjectNode ato = ((ObjectNode) n).deepCopy();
                    ato.remove("ente");
                    p.bloco("atos_normativos").add(ato);
                    usarFonte(p, "atos_normativos", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", n.get("fonte_id"))
                            .put("url_origem", Sapl.API + "/norma/normajuridica/").set("capturado_em", n.get("capturado_em")));
                }
            }
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
