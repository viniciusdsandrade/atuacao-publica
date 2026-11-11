package etl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import etl.Publicador.Perfil;
import etl.fontes.Alep;
import etl.fontes.Cmsp;
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
        perfis.addAll(legisladores(ctx, "alesp", "alesp", "alesp"));
        perfis.addAll(legisladores(ctx, "almg", "almg", "almg"));
        perfis.addAll(legisladores(ctx, "alep", "alep", "alep"));
        perfis.addAll(legisladores(ctx, "alba", "alba", "alba"));
        perfis.addAll(legisladores(ctx, "alerj", "alerj", "alerj"));
        perfis.addAll(legisladores(ctx, "alrs", "alrs", "alrs"));
        perfis.addAll(legisladores(ctx, "alece", "alece", "alece"));
        perfis.addAll(legisladores(ctx, "alesc", "alesc", "alesc"));
        perfis.addAll(legisladores(ctx, "alego", "alego", "alego"));
        perfis.addAll(legisladores(ctx, "ales", "ales", "ales"));
        perfis.addAll(legisladores(ctx, "alepa", "alepa", "alepa"));
        perfis.addAll(legisladores(ctx, "alepe", "alepe", "alepe"));
        perfis.addAll(legisladores(ctx, "cldf", "cldf", "cldf"));
        perfis.addAll(legisladores(ctx, "almt", "almt", "almt"));
        perfis.addAll(legisladores(ctx, "alems", "alems", "alems"));
        perfis.addAll(legisladores(ctx, "alpb", "alpb", "alpb"));
        perfis.addAll(legisladores(ctx, "aleam", "aleam", "aleam"));
        perfis.addAll(legisladores(ctx, "aleal", "aleal", "aleal"));
        perfis.addAll(legisladores(ctx, "cmsp", "cmsp", "cmsp"));
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
            despesasDoExecutivoEstadual(ctx, perfis);
            agendaEstadual(ctx, perfis);
            atosEstadual(ctx, perfis);
        }
        emendas(ctx, perfis);
        financiamento(ctx, perfis);
        return perfis;
    }

    /**
     * Receitas e despesas de campanha (ADR-0032) de cada candidatura já ligada ao perfil, pelo SQ_CANDIDATO.
     * Da eleição mais recente para a mais antiga; em cada eleição, receitas antes de despesas.
     */
    private static void financiamento(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("tse").resolve("financiamento.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode porCandidatura = Json.ler(arquivo);
        int comContas = 0;
        for (Perfil p : perfis) {
            List<JsonNode> registros = new ArrayList<>();
            for (String sq : p.tse.values()) {
                porCandidatura.path(sq).forEach(registros::add);
            }
            if (registros.isEmpty()) {
                continue;
            }
            registros.sort(java.util.Comparator.comparingInt((JsonNode r) -> r.get("eleicao").asInt()).reversed());
            for (JsonNode registro : registros) {
                p.bloco("financiamento_campanha").add(registro);
                usarFonte(p, "financiamento_campanha", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", registro.get("fonte_id"))
                        .<ObjectNode>set("url_origem", registro.get("url_origem")).set("capturado_em", registro.get("capturado_em")));
            }
            comContas++;
        }
        System.out.println("vinculo: financiamento de campanha em " + comContas + " perfil(is)");
    }

    /**
     * Emendas individuais ao Orçamento da União (ADR-0030). O código do autor na fonte liga as emendas ao perfil federal
     * cujo nome é um dos nomes do autor na fonte, sem pontuação. Sem perfil com esse nome, vale o nome de urna de uma
     * candidatura do perfil. Código com mais de um perfil possível fica fora, como o sem perfil: homônimos não recebem
     * emenda de outra pessoa.
     */
    private static void emendas(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("cgu").resolve("emendas.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        Map<String, Set<Perfil>> porNome = new HashMap<>();
        Map<String, Set<Perfil>> porNomeDeUrna = new HashMap<>();
        for (Perfil p : perfis) {
            if (p.cargos.contains("deputado_federal") || p.cargos.contains("senador")) {
                if (p.nome != null) {
                    porNome.computeIfAbsent(semPontos(Texto.normalizarNome(p.nome)), k -> new HashSet<>()).add(p);
                }
                for (String nome : nomesDeUrna(p)) {
                    porNomeDeUrna.computeIfAbsent(nome, k -> new HashSet<>()).add(p);
                }
            }
        }
        int ligadas = 0;
        int autoresLigados = 0;
        int ambiguos = 0;
        int semPerfil = 0;
        int foraDoVinculo = 0;
        for (JsonNode autor : Json.ler(arquivo)) {
            Set<Perfil> possiveis = perfisDoAutor(autor, porNome);
            if (possiveis.isEmpty()) {
                possiveis = perfisDoAutor(autor, porNomeDeUrna);
            }
            JsonNode lista = autor.path("emendas");
            if (possiveis.size() != 1) {
                if (possiveis.isEmpty()) {
                    semPerfil++;
                } else {
                    ambiguos++;
                }
                foraDoVinculo += lista.size();
                continue;
            }
            Perfil p = possiveis.iterator().next();
            for (JsonNode emenda : lista) {
                p.bloco("emendas").add(emenda);
                usarFonte(p, "emendas", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", emenda.get("fonte_id"))
                        .<ObjectNode>set("url_origem", emenda.get("url_origem")).set("capturado_em", emenda.get("capturado_em")));
            }
            ligadas += lista.size();
            autoresLigados++;
        }
        System.out.println("vinculo: " + ligadas + " emenda(s) de " + autoresLigados + " autor(es) ligada(s) a perfis federais; "
                + (ambiguos + semPerfil) + " autor(es) fora do vínculo (" + ambiguos + " com mais de um perfil possível, " + semPerfil
                + " sem perfil), com " + foraDoVinculo + " emenda(s)");
    }

    private static Set<Perfil> perfisDoAutor(JsonNode autor, Map<String, Set<Perfil>> indice) {
        Set<Perfil> possiveis = new HashSet<>();
        for (JsonNode nome : autor.path("nomes")) {
            possiveis.addAll(indice.getOrDefault(semPontos(Texto.normalizarNome(nome.stringValue())), Set.of()));
        }
        return possiveis;
    }

    /** Nomes de urna das candidaturas do perfil, normalizados e sem pontos. */
    private static Set<String> nomesDeUrna(Perfil p) {
        Set<String> nomes = new HashSet<>();
        for (JsonNode candidatura : p.registros.getOrDefault("candidaturas", List.of())) {
            String urna = Json.texto(candidatura, "nome_urna");
            if (urna != null) {
                nomes.add(semPontos(Texto.normalizarNome(urna)));
            }
        }
        return nomes;
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

    private static Set<String> palavras(String texto) {
        if (texto == null || texto.isBlank()) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        for (String p : texto.split("\\s+")) {
            if (!p.isBlank()) {
                set.add(p);
            }
        }
        return set;
    }

    private static final Pattern TITULO_PATTERN = Pattern.compile("^(?:DEPUTADO\\.|DEPUTADA\\.|DEPUTADO|DEPUTADA|PADRE|DR\\.|DRA\\.|PROF\\.|PROFESSOR\\.|PROFESSORA\\.|DELEGADO\\.|DELEGADA\\.|CAPITAO\\.|PASTOR\\.|DR|DRA|PROF|PROFESSOR|PROFESSORA)\\s+", Pattern.CASE_INSENSITIVE);

    private static String limparTitulo(String nome) {
        if (nome == null || nome.isBlank()) {
            return nome;
        }
        return TITULO_PATTERN.matcher(nome.trim()).replaceFirst("").trim();
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
            JsonNode fontesPorBloco = blocos.path("fontes");
            if (fontesPorBloco.isArray()) {
                // Uma lista não diz de que bloco é cada fonte: o perfil sairia com os blocos sem fonte e sem data.
                throw new IllegalStateException(fonte + "/blocos/" + idFonte + ".json: \"fontes\" é uma lista; grave um objeto"
                        + " com as fontes de cada bloco (Registros.fontesDosBlocos)");
            }
            for (var usadas : fontesPorBloco.properties()) {
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
        Map<String, JsonNode> candPorCmspNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorCmspNomeUrna = new HashMap<>();
        Map<String, JsonNode> candPorAlespNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlespNomeUrna = new HashMap<>();
        Map<String, JsonNode> candPorAlmgNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlmgNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlmgChave = new HashMap<>();
        Map<String, JsonNode> candPorAlepNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlepNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlepChave = new HashMap<>();
        Map<String, JsonNode> candPorAlbaNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlbaNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlbaChave = new HashMap<>();
        Map<String, JsonNode> candPorAlerjNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlerjNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlerjChave = new HashMap<>();
        Map<String, JsonNode> candPorAlrsNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlrsNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlrsChave = new HashMap<>();
        Map<String, JsonNode> candPorAleceNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAleceNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAleceChave = new HashMap<>();
        Map<String, JsonNode> candPorAlescNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlescNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlescChave = new HashMap<>();
        Map<String, JsonNode> candPorAlegoNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlegoNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlegoChave = new HashMap<>();
        Map<String, JsonNode> candPorAlesNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlesNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlesChave = new HashMap<>();
        Map<String, JsonNode> candPorAlepaNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlepaNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlepaChave = new HashMap<>();
        Map<String, JsonNode> candPorAlepeNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlepeNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlepeChave = new HashMap<>();
        Map<String, JsonNode> candPorCldfNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorCldfNomeUrna = new HashMap<>();
        Map<String, String> cpfPorCldfChave = new HashMap<>();
        Map<String, JsonNode> candPorAlmtNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlmtNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlmtChave = new HashMap<>();
        Map<String, String> almtNomesCivis = lerNomesCivis(ctx, "almt");
        Map<String, JsonNode> candPorAlemsNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlemsNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlemsChave = new HashMap<>();
        Map<String, JsonNode> candPorAlpbNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlpbNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlpbChave = new HashMap<>();
        Map<String, String> alpbNomesCivis = lerNomesCivis(ctx, "alpb");
        Map<String, JsonNode> candPorAleamNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAleamNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAleamChave = new HashMap<>();
        Map<String, String> aleamNomesCivis = lerNomesCivis(ctx, "aleam");
        Map<String, JsonNode> candPorAlealNomeCivil = new HashMap<>();
        Map<String, JsonNode> candPorAlealNomeUrna = new HashMap<>();
        Map<String, String> cpfPorAlealChave = new HashMap<>();
        Map<String, String> alealNomesCivis = lerNomesCivis(ctx, "aleal");
        for (JsonNode c : candidaturas) {
            Tse.Chave chave = chaves.get(c.get("sq").stringValue());
            if (chave != null) {
                if (chave.cpf() != null && chave.cargo().matches("SENADOR|1º SUPLENTE|2º SUPLENTE")) {
                    cpfPorSenadoChave.put(chave.nomeCivil() + "|" + chave.uf() + "|" + chave.nascimento(), chave.cpf());
                } else if ("VEREADOR".equals(chave.cargo())) {
                    // A candidatura mais recente com o nome vale como ponte; as demais eleições vêm pelo CPF, em memória.
                    // Cada município tem os seus mapas: dois municípios podem ter vereadores com o mesmo nome.
                    String enteDaCandidatura = Json.texto(c.path("registro"), "ente");
                    if (Cmsp.IBGE.equals(enteDaCandidatura)) {
                        candPorCmspNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                        if (c.hasNonNull("nome_urna")) {
                            candPorCmspNomeUrna.merge(semPontos(Texto.normalizarNome(c.get("nome_urna").stringValue())), c, Vinculo::maisRecente);
                        }
                    } else if (Sapl.IBGE.equals(enteDaCandidatura)) {
                        candPorSaplNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                        if (c.hasNonNull("nome_urna")) {
                            candPorSaplNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                        }
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "SP".equals(chave.uf())) {
                    candPorAlespNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlespNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "MG".equals(chave.uf())) {
                    candPorAlmgNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlmgNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlmgChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "PR".equals(chave.uf())) {
                    candPorAlepNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlepNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlepChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "BA".equals(chave.uf())) {
                    candPorAlbaNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlbaNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlbaChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "RJ".equals(chave.uf())) {
                    candPorAlerjNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlerjNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlerjChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "RS".equals(chave.uf())) {
                    candPorAlrsNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlrsNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlrsChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "CE".equals(chave.uf())) {
                    candPorAleceNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAleceNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAleceChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "SC".equals(chave.uf())) {
                    candPorAlescNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlescNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlescChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "GO".equals(chave.uf())) {
                    candPorAlegoNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlegoNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlegoChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "ES".equals(chave.uf())) {
                    candPorAlesNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlesNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlesChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "PA".equals(chave.uf())) {
                    candPorAlepaNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlepaNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlepaChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "PE".equals(chave.uf())) {
                    candPorAlepeNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlepeNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlepeChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if (("DEPUTADO ESTADUAL".equals(chave.cargo()) || "DEPUTADO DISTRITAL".equals(chave.cargo())) && "DF".equals(chave.uf())) {
                    candPorCldfNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorCldfNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorCldfChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "MT".equals(chave.uf())) {
                    candPorAlmtNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlmtNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlmtChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "MS".equals(chave.uf())) {
                    candPorAlemsNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlemsNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlemsChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "PB".equals(chave.uf())) {
                    candPorAlpbNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlpbNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlpbChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "AM".equals(chave.uf())) {
                    candPorAleamNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAleamNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAleamChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
                    }
                } else if ("DEPUTADO ESTADUAL".equals(chave.cargo()) && "AL".equals(chave.uf())) {
                    candPorAlealNomeCivil.merge(chave.nomeCivil(), c, Vinculo::maisRecente);
                    if (c.hasNonNull("nome_urna")) {
                        candPorAlealNomeUrna.merge(Texto.normalizarNome(c.get("nome_urna").stringValue()), c, Vinculo::maisRecente);
                    }
                    if (chave.cpf() != null) {
                        cpfPorAlealChave.put(chave.nomeCivil() + "|" + chave.nascimento(), chave.cpf());
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
            } else if ("cmsp".equals(p.fonteCli)) {
                // O serviço da CMSP só tem o nome parlamentar, que costuma ser o nome de urna. Só a pontuação ("Jr.", "Dra.")
                // é ignorada: casar por palavras soltas ligaria homônimos parciais, e isso atribuiria candidaturas e bens a outra pessoa.
                String nomeParl = semPontos(Texto.normalizarNome(p.nome));
                JsonNode cand = candPorCmspNomeUrna.get(nomeParl);
                if (cand == null) {
                    cand = candPorCmspNomeCivil.get(nomeParl);
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    cpf = chaveSapl == null ? null : chaveSapl.cpf();
                }
            } else if ("alesp".equals(p.fonteCli)) {
                String nomeParl = Texto.normalizarNome(p.nome);
                Coleta.Arquivo arq = Coleta.metadados(ctx, "alesp", "detalhes/" + p.ids.get("alesp") + ".json");
                if (arq != null && Files.exists(Coleta.caminho(ctx, arq))) {
                    JsonNode det = Json.ler(Coleta.caminho(ctx, arq));
                    String deDet = Texto.normalizarNome(Json.texto(det, "txNomeParlamentar"));
                    if (deDet != null) {
                        nomeParl = deDet;
                    }
                }
                JsonNode cand = candPorAlespNomeUrna.get(nomeParl);
                if (cand == null) {
                    cand = candPorAlespNomeCivil.get(nomeParl);
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    cpf = chaveSapl == null ? null : chaveSapl.cpf();
                }
            } else if ("almg".equals(p.fonteCli)) {
                String nomeParl = Texto.normalizarNome(p.nome);
                String nomeServidor = null;
                String dataNasc = null;
                Coleta.Arquivo arq = Coleta.metadados(ctx, "almg", "deputados/" + p.ids.get("almg") + ".json");
                if (arq != null && Files.exists(Coleta.caminho(ctx, arq))) {
                    JsonNode det = Json.ler(Coleta.caminho(ctx, arq)).path("deputado");
                    String deDet = Texto.normalizarNome(Json.texto(det, "nome"));
                    if (deDet != null) {
                        nomeParl = deDet;
                    }
                    nomeServidor = Texto.normalizarNome(Json.texto(det, "nomeServidor"));
                    dataNasc = etl.fontes.Almg.normalizarDataIso(Json.texto(det, "dataNascimento"));
                }
                if (nomeServidor != null && dataNasc != null) {
                    cpf = cpfPorAlmgChave.get(nomeServidor + "|" + dataNasc);
                }
                JsonNode cand = null;
                if (nomeServidor != null) {
                    cand = candPorAlmgNomeCivil.get(nomeServidor);
                }
                if (cand == null) {
                    cand = candPorAlmgNomeUrna.get(nomeParl);
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alep".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                JsonNode cand = candPorAlepNomeCivil.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlepNomeUrna.get(nomeNorm);
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                    // Os votos da ALEP vêm pelo nome civil: o da candidatura do TSE liga o arquivo ao perfil.
                    if (chaveSapl != null && chaveSapl.nomeCivil() != null) {
                        anexarBlocos(p, ctx.normalizado().resolve("alep/votos").resolve(Alep.arquivoDeVotos(chaveSapl.nomeCivil()) + ".json"));
                    }
                }
            } else if ("alba".equals(p.fonteCli)) {
                String nomeCivil = null;
                String dataNasc = null;
                Path arqBio = ctx.normalizado().resolve("alba/biografias.json");
                if (Files.exists(arqBio)) {
                    String idDep = p.ids.get("alba");
                    JsonNode bioDep = idDep != null ? Json.ler(arqBio).path(idDep) : null;
                    if (bioDep != null && !bioDep.isMissingNode()) {
                        nomeCivil = Texto.normalizarNome(Json.texto(bioDep, "nome_civil"));
                        dataNasc = Json.texto(bioDep, "nascimento");
                    }
                }
                if (nomeCivil != null && dataNasc != null) {
                    cpf = cpfPorAlbaChave.get(nomeCivil + "|" + dataNasc);
                }
                JsonNode cand = null;
                if (nomeCivil != null) {
                    cand = candPorAlbaNomeCivil.get(nomeCivil);
                }
                if (cand == null) {
                    String nomeNorm = Texto.normalizarNome(p.nome);
                    cand = candPorAlbaNomeUrna.get(nomeNorm);
                    if (cand == null) {
                        cand = candPorAlbaNomeCivil.get(nomeNorm);
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alerj".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                JsonNode cand = candPorAlerjNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlerjNomeCivil.get(nomeNorm);
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlerjNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlerjNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alrs".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlrsNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlrsNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlrsNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlrsNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlrsNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlrsNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlrsNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlrsNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alece".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAleceNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAleceNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAleceNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAleceNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAleceNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAleceNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAleceNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAleceNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alesc".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlescNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlescNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlescNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlescNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlescNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlescNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlescNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlescNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alego".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlegoNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlegoNomeCivil.get(nomeNorm);
                }
                if (cand == null) {
                    cand = candPorAlegoNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlegoNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlegoNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlegoNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlegoNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlegoNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlegoNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("ales".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlesNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlesNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlesNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlesNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlesNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlesNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlesNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlesNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alepa".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlepaNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlepaNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlepaNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlepaNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlepaNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlepaNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlepaNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlepaNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alepe".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlepeNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlepeNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlepeNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlepeNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlepeNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlepeNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlepeNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlepeNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("cldf".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorCldfNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorCldfNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorCldfNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorCldfNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorCldfNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorCldfNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorCldfNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorCldfNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("almt".equals(p.fonteCli)) {
                String nomeCivilAlmt = almtNomesCivis.get(p.ids.get("almt"));
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = null;
                if (nomeCivilAlmt != null) {
                    cand = candPorAlmtNomeCivil.get(Texto.normalizarNome(nomeCivilAlmt));
                }
                if (cand == null) {
                    cand = candPorAlmtNomeUrna.get(nomeNorm);
                }
                if (cand == null) {
                    cand = candPorAlmtNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlmtNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlmtNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlmtNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlmtNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlmtNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlmtNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alems".equals(p.fonteCli)) {
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = candPorAlemsNomeUrna.get(nomeNorm);
                if (cand == null) {
                    cand = candPorAlemsNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlemsNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlemsNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlemsNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlemsNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlemsNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlemsNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("alpb".equals(p.fonteCli)) {
                String nomeCivilAlpb = alpbNomesCivis.get(p.ids.get("alpb"));
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = null;
                if (nomeCivilAlpb != null) {
                    cand = candPorAlpbNomeCivil.get(Texto.normalizarNome(nomeCivilAlpb));
                }
                if (cand == null) {
                    cand = candPorAlpbNomeUrna.get(nomeNorm);
                }
                if (cand == null) {
                    cand = candPorAlpbNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlpbNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlpbNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlpbNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlpbNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlpbNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlpbNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("aleam".equals(p.fonteCli)) {
                String nomeCivilAleam = aleamNomesCivis.get(p.ids.get("aleam"));
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = null;
                if (nomeCivilAleam != null) {
                    cand = candPorAleamNomeCivil.get(Texto.normalizarNome(nomeCivilAleam));
                }
                if (cand == null) {
                    cand = candPorAleamNomeUrna.get(nomeNorm);
                }
                if (cand == null) {
                    cand = candPorAleamNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAleamNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAleamNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAleamNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAleamNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAleamNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAleamNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
                    }
                }
            } else if ("aleal".equals(p.fonteCli)) {
                String nomeCivilAleal = alealNomesCivis.get(p.ids.get("aleal"));
                String nomeNorm = Texto.normalizarNome(p.nome);
                String nomeLimpo = Texto.normalizarNome(limparTitulo(p.nome));
                JsonNode cand = null;
                if (nomeCivilAleal != null) {
                    cand = candPorAlealNomeCivil.get(Texto.normalizarNome(nomeCivilAleal));
                }
                if (cand == null) {
                    cand = candPorAlealNomeUrna.get(nomeNorm);
                }
                if (cand == null) {
                    cand = candPorAlealNomeCivil.get(nomeNorm);
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    cand = candPorAlealNomeUrna.get(nomeLimpo);
                    if (cand == null) {
                        cand = candPorAlealNomeCivil.get(nomeLimpo);
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlealNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null) {
                    Set<String> palavras = palavras(nomeNorm);
                    for (var entry : candPorAlealNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlealNomeUrna.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand == null && !nomeLimpo.equals(nomeNorm)) {
                    Set<String> palavras = palavras(nomeLimpo);
                    for (var entry : candPorAlealNomeCivil.entrySet()) {
                        if (palavras(entry.getKey()).containsAll(palavras)) {
                            cand = entry.getValue();
                            break;
                        }
                    }
                }
                if (cand != null) {
                    candSapl = cand;
                    chaveSapl = chaves.get(cand.get("sq").stringValue());
                    if (cpf == null && chaveSapl != null) {
                        cpf = chaveSapl.cpf();
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

    private static String semPontos(String nome) {
        return nome == null ? null : nome.replace(".", "");
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
            List<JsonNode> origens = new ArrayList<>();
            origens.add(registro);
            // As redes declaradas vêm de outro arquivo do TSE: o bloco cita os dois (ADR-0031).
            registro.path("redes_declaradas").forEach(origens::add);
            for (JsonNode origem : origens) {
                ObjectNode usada = Json.SAIDA.createObjectNode();
                usada.set("fonte_id", origem.get("fonte_id"));
                usada.set("url_origem", origem.get("url_origem"));
                usada.set("capturado_em", origem.get("capturado_em"));
                if (!p.fontes.get("candidaturas").contains(usada)) {
                    p.fontes.get("candidaturas").add(usada);
                }
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
            boolean municipal = cargo.equals("prefeito") || cargo.equals("vice_prefeito");
            String enteDaRegra = regra.hasNonNull("ente") ? Json.texto(regra, "ente") : Sapl.IBGE;
            for (JsonNode c : eleitos(candidaturas, eleicao, cargo)) {
                // A regra municipal vale só para o município dela. Com mais de um município coberto, o eleito de outro não entra.
                if (municipal && !enteDaRegra.equals(Json.texto(c.path("registro"), "ente"))) {
                    continue;
                }
                Perfil p = perfilDoEleito(c, ligacoes, perfilPorCpf, perfilPorPessoa, novos);
                String uf = Json.texto(c, "uf");
                String esfera = cargo.equals("presidente") ? "federal" : (municipal ? "municipal" : "estadual");
                String ente = cargo.equals("presidente") ? "BR" : (municipal ? enteDaRegra : uf);
                // Candidatura a outro cargo na eleição seguinte: o mandato acaba no prazo de renúncia (ADR-0033).
                Fase faseDoEleito = fase;
                LocalDate fimDoEleito = fim;
                // A Constituição exige a renúncia do chefe do Executivo; o vice não precisa renunciar para concorrer.
                boolean chefe = cargo.equals("presidente") || cargo.equals("governador") || cargo.equals("prefeito");
                JsonNode outroCargo = chefe ? candidaturaAOutroCargo(c, eleicao + 4, candidaturas, ligacoes.chaves()) : null;
                LocalDate prazo = prazoDeRenuncia(eleicao + 4);
                if (outroCargo != null && prazo.isAfter(inicio) && prazo.isBefore(fim) && !ctx.hoje().isBefore(prazo)) {
                    fimDoEleito = prazo;
                    faseDoEleito = new Fase("Deixou o cargo para concorrer a " + Json.texto(outroCargo, "cargo").toLowerCase(Locale.ROOT)
                            + " em " + (eleicao + 4) + " (eleito em " + eleicao + "). A Constituição exige a renúncia até " + prazo
                            + " (art. 14, § 6º); o TSE não informa a data exata.", Perfil.FORA_DE_EXERCICIO);
                }
                p.papel(cargo, esfera, ente, cargo.equals("presidente") ? null : uf, Json.texto(c, "partido"), faseDoEleito.situacao(),
                        faseDoEleito.prioridade(), inicio);
                ObjectNode mandato = Json.SAIDA.createObjectNode();
                mandato.put("cargo", cargo).put("esfera", esfera).put("ente", ente).put("uf", cargo.equals("presidente") ? null : uf)
                        .put("inicio", inicio.toString()).put("fim", fimDoEleito.toString()).put("situacao", faseDoEleito.situacao())
                        .putNull("condicao").putNull("legislatura").put("eleicao", eleicao);
                for (String campo : List.of("texto_oficial", "fonte_id", "url_origem", "snapshot_url", "capturado_em", "contribuidor")) {
                    mandato.set(campo, regra.get(campo));
                }
                p.bloco("mandatos").add(mandato);
                usarFonte(p, "mandatos", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", regra.get("fonte_id"))
                        .<ObjectNode>set("url_origem", regra.get("url_origem")).set("capturado_em", regra.get("capturado_em")));
                if (!fimDoEleito.equals(fim)) {
                    // A candidatura do TSE é a evidência do fim antecipado: entra entre as fontes do bloco.
                    JsonNode registro = outroCargo.path("registro");
                    usarFonte(p, "mandatos", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", registro.get("fonte_id"))
                            .<ObjectNode>set("url_origem", registro.get("url_origem")).set("capturado_em", registro.get("capturado_em")));
                }
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

    /** Arquivo de blocos a mais para o perfil, lido no publish como os demais; as fontes dele entram no perfil. */
    private static void anexarBlocos(Perfil p, Path arquivo) {
        if (!Files.exists(arquivo) || p.arquivosDeBlocos.contains(arquivo)) {
            return;
        }
        JsonNode blocos = Json.ler(arquivo);
        if (blocos.path("fontes").isArray()) {
            throw new IllegalStateException(arquivo.getFileName() + ": \"fontes\" é uma lista; grave um objeto com as fontes de cada bloco");
        }
        p.arquivosDeBlocos.add(arquivo);
        for (var usadas : blocos.path("fontes").properties()) {
            List<JsonNode> lista = p.fontes.computeIfAbsent(usadas.getKey(), k -> new ArrayList<>());
            usadas.getValue().forEach(lista::add);
        }
    }

    /**
     * Candidatura da mesma pessoa a outro cargo na eleição indicada, ou null. A pessoa é a do CPF ou, sem ele, a do nome
     * civil com a data de nascimento (ADR-0003). Reeleição para o mesmo cargo não conta.
     */
    static JsonNode candidaturaAOutroCargo(JsonNode eleito, int eleicao, List<JsonNode> candidaturas, Map<String, Tse.Chave> chaves) {
        Tse.Chave chave = chaves.get(eleito.get("sq").stringValue());
        if (chave == null) {
            return null;
        }
        String cargo = Json.texto(eleito, "cargo");
        for (JsonNode c : candidaturas) {
            if (c.get("ano").asInt() != eleicao || cargo == null || cargo.equals(Json.texto(c, "cargo"))) {
                continue;
            }
            Tse.Chave outra = chaves.get(c.get("sq").stringValue());
            if (outra == null) {
                continue;
            }
            boolean mesmoCpf = chave.cpf() != null && chave.cpf().equals(outra.cpf());
            boolean mesmaPessoa = pessoa(chave) != null && pessoa(chave).equals(pessoa(outra));
            if (mesmoCpf || mesmaPessoa) {
                return c;
            }
        }
        return null;
    }

    /**
     * Prazo de renúncia do chefe do Executivo que concorre a outro cargo: seis meses antes do primeiro turno, que é no
     * primeiro domingo de outubro (CF, art. 14, § 6º, e art. 77).
     */
    static LocalDate prazoDeRenuncia(int eleicao) {
        return LocalDate.of(eleicao, 10, 1).with(java.time.temporal.TemporalAdjusters.firstInMonth(java.time.DayOfWeek.SUNDAY))
                .minusMonths(6);
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
                    if (registro.get("participantes") == null || registro.get("participantes").isNull()) {
                        registro.remove("participantes");
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

    /**
     * Despesas do Poder Executivo estadual (ADR-0014) no perfil do governador já empossado.
     */
    private static void despesasDoExecutivoEstadual(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("executivosp").resolve("despesas.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode agregados = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("governador")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                String ente = Json.texto(mandato, "ente");
                if (!"governador".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0 || !"SP".equals(ente)) {
                    continue;
                }
                for (JsonNode a : agregados) {
                    String primeiroDia = String.format("%04d-%02d-01", a.get("ano").asInt(), a.get("mes").asInt());
                    boolean noMandato = primeiroDia.compareTo(inicio.substring(0, 8) + "01") >= 0 && (fim == null || primeiroDia.compareTo(fim) < 0);
                    if (!noMandato) {
                        continue;
                    }
                    ObjectNode registro = Json.SAIDA.createObjectNode();
                    registro.set("ano", a.get("ano"));
                    registro.set("mes", a.get("mes"));
                    registro.set("categoria", a.get("categoria"));
                    registro.set("valor", a.get("valor"));
                    registro.set("quantidade", a.get("quantidade"));
                    registro.set("fonte_id", a.get("fonte_id"));
                    registro.set("url_origem", a.get("url_origem"));
                    registro.set("capturado_em", a.get("capturado_em"));
                    p.bloco("despesas_executivo").add(registro);
                    usarFonte(p, "despesas_executivo", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", a.get("fonte_id"))
                            .<ObjectNode>set("url_origem", a.get("url_origem")).set("capturado_em", a.get("capturado_em")));
                }
            }
        }
    }

    /** Compromissos da agenda oficial em cada mandato de governador (ADR-0014). */
    private static void agendaEstadual(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("executivosp").resolve("compromissos.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode compromissos = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("governador")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                String ente = Json.texto(mandato, "ente");
                if (!"governador".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0 || !"SP".equals(ente)) {
                    continue;
                }
                for (JsonNode c : compromissos) {
                    String data = Json.texto(c, "data");
                    if (data == null || data.compareTo(inicio) < 0 || (fim != null && data.compareTo(fim) >= 0)) {
                        continue;
                    }
                    ObjectNode registro = ((ObjectNode) c).deepCopy();
                    registro.remove("ente");
                    if (registro.get("snapshot_url") == null || registro.get("snapshot_url").isNull()) {
                        registro.remove("snapshot_url");
                    }
                    if (registro.get("participantes") == null || registro.get("participantes").isNull()) {
                        registro.remove("participantes");
                    }
                    p.bloco("agenda_oficial").add(registro);
                    usarFonte(p, "agenda_oficial", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", c.get("fonte_id"))
                            .put("url_origem", Json.texto(c, "url_origem").replaceAll("/[0-9]{4}-[0-9]{2}-[0-9]{2}$", ""))
                            .set("capturado_em", c.get("capturado_em")));
                }
            }
        }
    }

    /** Atos normativos e decretos do Poder Executivo estadual (ADR-0014) no perfil do governador. */
    private static void atosEstadual(Contexto ctx, List<Perfil> perfis) {
        Path arquivo = ctx.normalizado().resolve("executivosp").resolve("atos.json");
        if (!Files.exists(arquivo)) {
            return;
        }
        JsonNode atos = Json.ler(arquivo);
        String hoje = ctx.hoje().toString();
        for (Perfil p : perfis) {
            if (!p.cargos.contains("governador")) {
                continue;
            }
            for (JsonNode mandato : p.bloco("mandatos")) {
                String inicio = Json.texto(mandato, "inicio");
                String fim = Json.texto(mandato, "fim");
                String ente = Json.texto(mandato, "ente");
                if (!"governador".equals(Json.texto(mandato, "cargo")) || inicio == null || inicio.compareTo(hoje) > 0 || !"SP".equals(ente)) {
                    continue;
                }
                for (JsonNode ato : atos) {
                    String data = Json.texto(ato, "data");
                    if (data == null || data.compareTo(inicio) < 0 || (fim != null && data.compareTo(fim) >= 0)) {
                        continue;
                    }
                    ObjectNode registro = ((ObjectNode) ato).deepCopy();
                    registro.remove("ente");
                    p.bloco("atos_normativos").add(registro);
                    usarFonte(p, "atos_normativos", Json.SAIDA.createObjectNode().<ObjectNode>set("fonte_id", ato.get("fonte_id"))
                            .<ObjectNode>set("url_origem", ato.get("url_origem")).set("capturado_em", ato.get("capturado_em")));
                }
            }
        }
    }

    static Map<String, String> lerNomesCivis(Contexto ctx, String fonte) {
        Path arquivo = ctx.normalizado().resolve(fonte).resolve("nomes-civis.json");
        if (!Files.exists(arquivo)) {
            return Map.of();
        }
        try {
            Map<String, String> mapa = new HashMap<>();
            JsonNode no = Json.ler(arquivo);
            for (var entry : no.properties()) {
                mapa.put(entry.getKey(), entry.getValue().stringValue());
            }
            return mapa;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
