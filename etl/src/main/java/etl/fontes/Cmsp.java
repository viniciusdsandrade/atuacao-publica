package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Câmara Municipal de São Paulo (CMSP, ADR-0027).
 * Lê o serviço oficial SPLegisWS: vereadores com mandatos e cargos, ocupação de gabinetes e comissões.
 * Só entram os vereadores em exercício: quem ocupa um gabinete vigente e tem mandato vigente.
 */
public final class Cmsp implements Fonte {

    public static final String FONTE_API = "cmsp-splegisws";
    public static final String FONTE_PORTAL = "cmsp-portal";

    public static final String API = "https://splegisws.saopaulo.sp.leg.br/ws/ws2.asmx";
    public static final String URL_VEREADORES = API + "/VereadoresCMSPJSON";
    public static final String URL_GABINETES = API + "/OcupacaoGabineteJSON";
    public static final String URL_COMISSOES = API + "/ComissoesCMSPJSON";
    public static final String URL_MEMBROS = "https://www.saopaulo.sp.leg.br/vereadores/membros/";
    public static final String URL_PROJETOS_POR_ANO = API + "/ProjetosPorAnoJSON";
    public static final String URL_TRAMITACAO = API + "/ProjetosEmTramitacaoPorPromoventeJSON";
    public static final String URL_LEIS = API + "/LeisAprovadasPorPromoventeJSON";
    public static final String URL_VETADOS = API + "/ProjetosVetadosPorPromoventeJSON";
    /** Primeiro ano das proposições: a cobertura do projeto começa em 2022 (ADR-0009). */
    static final int PRIMEIRO_ANO_PROPOSICOES = 2022;

    public static final String IBGE = "3550308";
    public static final String UF = "SP";

    private static final String PREFIXO_COMISSAO = "Comissão - ";
    private static final String MESA_NORMALIZADA = "MESA DA CAMARA MUNICIPAL DE SAO PAULO";
    private static final Set<String> PREPOSICOES = Set.of("de", "da", "do", "das", "dos", "e", "em", "para", "a", "o");

    /** Vereador em exercício, com o mandato vigente e os cargos brutos (comissões e Mesa) do serviço. */
    public record Vereador(String chave, String nome, String partido, LocalDate inicio, LocalDate fim, int legislatura,
                           JsonNode cargos) {
    }

    @Override
    public String nome() {
        return "cmsp";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        System.out.println("cmsp: coleta iniciada");
        coleta.obter(URL_VEREADORES, "vereadores.json", "application/json", Politica.SEMANAL);
        coleta.obter(URL_GABINETES, "gabinetes.json", "application/json", Politica.SEMANAL);
        coleta.obterOuVazio(URL_COMISSOES, "comissoes.json", "application/json", Politica.SEMANAL);
        coleta.obterOuVazio(URL_MEMBROS, "membros.html", "text/html", Politica.SEMANAL);
        // Proposições: o autor só aparece nas consultas por vereador. Ementa e data vêm da lista anual de projetos.
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            coleta.obterOpcional(URL_PROJETOS_POR_ANO + "?Ano=" + ano, "projetos/" + ano + ".json", "application/json",
                    Politica.porAnoSemanal(ano, ctx.hoje()));
        }
        for (Vereador v : emExercicio(ctx)) {
            coleta.obterOpcional(URL_LEIS + "?Codigo=" + v.chave(), arquivoDeAutoria(v.chave(), "leis"), "application/json",
                    Politica.SEMANAL);
            coleta.obterOpcional(URL_VETADOS + "?Codigo=" + v.chave(), arquivoDeAutoria(v.chave(), "vetados"),
                    "application/json", Politica.SEMANAL);
            coleta.obterOpcional(URL_TRAMITACAO + "?Codigo=" + v.chave(), arquivoDeAutoria(v.chave(), "tramitacao"),
                    "application/json", Politica.SEMANAL);
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        Coleta.Arquivo arqVereadores = Coleta.metadados(ctx, nome(), "vereadores.json");
        Coleta.Arquivo arqGabinetes = Coleta.metadados(ctx, nome(), "gabinetes.json");
        if (!existe(ctx, arqVereadores) || !existe(ctx, arqGabinetes)) {
            System.out.println("cmsp: sem vereadores ou gabinetes coletados, nada a normalizar");
            return;
        }
        JsonNode vereadores = Json.ler(Coleta.caminho(ctx, arqVereadores));
        JsonNode gabinetes = Json.ler(Coleta.caminho(ctx, arqGabinetes));
        List<Vereador> emExercicio = emExercicio(vereadores, gabinetes, ctx.hoje());
        if (emExercicio.isEmpty()) {
            System.out.println("cmsp: nenhum vereador em exercício para normalizar");
            return;
        }

        Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, nome(), "comissoes.json");
        Map<Integer, String> siglas = existe(ctx, arqComissoes) ? siglasDasComissoes(Json.ler(Coleta.caminho(ctx, arqComissoes))) : Map.of();
        Coleta.Arquivo arqMembros = Coleta.metadados(ctx, nome(), "membros.html");

        Prov provVereadores = Prov.de(FONTE_API, URL_VEREADORES, arqVereadores);
        List<LinkOficial> links = existe(ctx, arqMembros)
                ? List.of(new LinkOficial("Vereadores no portal da Câmara Municipal de São Paulo", URL_MEMBROS,
                        Prov.de(FONTE_PORTAL, URL_MEMBROS, arqMembros)))
                : List.of();

        Path pastaBlocos = ctx.normalizado().resolve("cmsp/blocos");
        try {
            Files.createDirectories(pastaBlocos);
        } catch (Exception e) {
            throw new RuntimeException("cmsp: erro ao criar pasta de blocos", e);
        }

        Map<String, List<Autoria>> autorias = new HashMap<>();
        Set<String> procurados = new HashSet<>();
        for (Vereador v : emExercicio) {
            List<Autoria> listas = List.of(autoria(ctx, v.chave(), "leis"), autoria(ctx, v.chave(), "vetados"),
                    autoria(ctx, v.chave(), "tramitacao"));
            autorias.put(v.chave(), listas);
            for (Autoria a : listas) {
                for (JsonNode item : itens(a)) {
                    String id = identificacao(item.has("projeto") ? item.path("projeto") : item);
                    if (id != null) {
                        procurados.add(id);
                    }
                }
            }
        }
        Map<Integer, Coleta.Arquivo> listasAnuais = new HashMap<>();
        Map<String, ProjetoDoAno> projetos = projetosDoAno(ctx, procurados, listasAnuais);

        Map<String, LocalDate> maisRecentes = inicioMaisRecenteDosExclusivos(emExercicio, ctx.hoje());
        List<Pessoa> pessoas = new ArrayList<>();
        int totalProposicoes = 0;
        for (Vereador v : emExercicio) {
            String textoMandato = "Vereador na " + v.legislatura() + "ª Legislatura (" + v.inicio().getYear()
                    + (v.fim() == null ? ")" : "–" + v.fim().getYear() + ")");
            List<Mandato> mandatos = List.of(new Mandato("vereador", "municipal", IBGE, UF, v.inicio(), v.fim(),
                    "Em exercício", "Titular", v.legislatura(), v.inicio().getYear() - 1, textoMandato, provVereadores));
            List<OrgaoOuFrente> orgaos = orgaos(v.cargos(), siglas, maisRecentes, ctx.hoje(), provVereadores);

            pessoas.add(new Pessoa("cmsp", v.chave(), titulo(v.nome()), "vereador", "municipal", IBGE, UF,
                    v.partido(), null, "Em exercício", true, links));

            Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
            fontes.put("mandatos", List.of(
                    FonteUsada.de(FONTE_API, arqVereadores),
                    new FonteUsada(FONTE_API, URL_GABINETES, arqGabinetes.capturadoEm())));
            if (!orgaos.isEmpty()) {
                List<FonteUsada> deOrgaos = new ArrayList<>();
                deOrgaos.add(FonteUsada.de(FONTE_API, arqVereadores));
                if (existe(ctx, arqComissoes)) {
                    deOrgaos.add(new FonteUsada(FONTE_API, URL_COMISSOES, arqComissoes.capturadoEm()));
                }
                fontes.put("orgaos_e_frentes", deOrgaos);
            }

            List<Autoria> listas = autorias.get(v.chave());
            List<Proposicao> proposicoes = proposicoes(listas.get(0), listas.get(1), listas.get(2), projetos);
            totalProposicoes += proposicoes.size();
            if (!proposicoes.isEmpty()) {
                // A autoria vem da consulta do vereador; ementa e data, da lista anual do ano do projeto.
                Set<FonteUsada> deProposicoes = new LinkedHashSet<>();
                for (Proposicao p : proposicoes) {
                    deProposicoes.add(new FonteUsada(p.prov().fonteId(), p.prov().urlOrigem(), p.prov().capturadoEm()));
                }
                for (Proposicao p : proposicoes) {
                    Coleta.Arquivo anual = listasAnuais.get(p.ano());
                    if (anual != null && projetos.containsKey(p.identificacao())) {
                        deProposicoes.add(FonteUsada.de(FONTE_API, anual));
                    }
                }
                fontes.put("proposicoes", List.copyOf(deProposicoes));
            }

            var blocos = Json.SAIDA.createObjectNode();
            blocos.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocos.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            blocos.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            blocos.set("fontes", Json.SAIDA.valueToTree(fontes));
            Json.gravar(pastaBlocos.resolve(v.chave() + ".json"), blocos);
        }

        pessoas.sort(Comparator.comparing(Pessoa::nome));
        Json.gravar(ctx.normalizado().resolve("cmsp/pessoas.json"), Json.SAIDA.valueToTree(pessoas));
        System.out.println("cmsp: " + pessoas.size() + " vereadores em exercício normalizados, com " + totalProposicoes
                + " proposições desde " + PRIMEIRO_ANO_PROPOSICOES + " (" + projetos.size() + " com ementa e data da lista anual)");
    }

    /** Lista de autoria de um vereador (leis aprovadas, vetos ou projetos em tramitação), com a proveniência da consulta. */
    public record Autoria(JsonNode itens, Prov prov) {
    }

    /** Data e ementa do projeto na lista anual, que as listas de autoria nem sempre trazem. */
    public record ProjetoDoAno(String data, String ementa) {
    }

    /**
     * Proposições do vereador desde 2022, pelas listas de autoria do serviço. O projeto que está em mais de uma lista
     * entra uma vez, com a situação mais avançada: lei, veto, tramitação. Projeto encerrado sem virar lei fica fora,
     * porque o serviço não informa o autor dele.
     */
    public static List<Proposicao> proposicoes(Autoria leis, Autoria vetados, Autoria tramitacao,
                                               Map<String, ProjetoDoAno> projetosDoAno) {
        Map<String, String> vetos = new HashMap<>();
        for (JsonNode v : itens(vetados)) {
            String id = identificacao(v);
            if (id != null) {
                vetos.put(id, Json.texto(v.path("veto"), "nome"));
            }
        }
        Map<String, Proposicao> porIdentificacao = new LinkedHashMap<>();
        for (JsonNode lei : itens(leis)) {
            JsonNode projeto = lei.path("projeto");
            String id = identificacao(projeto);
            if (id != null && !porIdentificacao.containsKey(id)) {
                String situacao = situacaoDaLei(lei);
                String veto = vetos.get(id);
                porIdentificacao.put(id, proposicao(projeto, id, Json.texto(projeto, "ementa"),
                        veto == null ? situacao : situacao + " (" + veto + ")", projetosDoAno, leis.prov()));
            }
        }
        for (JsonNode v : itens(vetados)) {
            String id = identificacao(v);
            if (id != null && !porIdentificacao.containsKey(id)) {
                String veto = vetos.get(id);
                porIdentificacao.put(id, proposicao(v, id, null, veto == null ? "Vetado" : veto, projetosDoAno, vetados.prov()));
            }
        }
        for (JsonNode t : itens(tramitacao)) {
            String id = identificacao(t);
            if (id != null && !porIdentificacao.containsKey(id)) {
                porIdentificacao.put(id, proposicao(t, id, null, "Em tramitação", projetosDoAno, tramitacao.prov()));
            }
        }
        return new ArrayList<>(porIdentificacao.values());
    }

    /** "PL 384/2023", ou null fora da cobertura (antes de 2022) ou sem tipo, número e ano. */
    static String identificacao(JsonNode projeto) {
        String tipo = Json.texto(projeto, "tipo");
        Integer numero = Json.inteiro(projeto, "numero");
        Integer ano = Json.inteiro(projeto, "ano");
        if (tipo == null || numero == null || ano == null || ano < PRIMEIRO_ANO_PROPOSICOES) {
            return null;
        }
        return tipo + " " + numero + "/" + ano;
    }

    private static Proposicao proposicao(JsonNode projeto, String identificacao, String ementa, String situacao,
                                         Map<String, ProjetoDoAno> projetosDoAno, Prov prov) {
        ProjetoDoAno doAno = projetosDoAno.get(identificacao);
        String tipo = Json.texto(projeto, "tipo");
        Integer numero = Json.inteiro(projeto, "numero");
        Integer ano = Json.inteiro(projeto, "ano");
        return new Proposicao(tipo + "-" + numero + "-" + ano, tipo, numero, ano, identificacao,
                ementa != null ? ementa : doAno == null ? null : doAno.ementa(), situacao, null,
                doAno == null ? null : doAno.data(), null, null, null, prov);
    }

    /** "Lei nº 18.253, publicada em 2025-05-22". */
    private static String situacaoDaLei(JsonNode lei) {
        Integer numero = Json.inteiro(lei, "numero");
        String texto = numero == null ? "Lei" : "Lei nº " + String.format(Locale.forLanguageTag("pt-BR"), "%,d", numero);
        String publicacao = dia(lei, "publicacao");
        return publicacao == null ? texto : texto + ", publicada em " + publicacao;
    }

    /** Data ISO (aaaa-mm-dd) de um campo com data e hora. */
    private static String dia(JsonNode no, String campo) {
        String texto = Json.texto(no, campo);
        return texto == null || texto.length() < 10 ? null : texto.substring(0, 10);
    }

    private static Iterable<JsonNode> itens(Autoria autoria) {
        return autoria == null || autoria.itens() == null ? List.of() : autoria.itens();
    }

    private static String arquivoDeAutoria(String chave, String lista) {
        return "autoria/" + chave + "-" + lista + ".json";
    }

    /** Lista de autoria em cache. Sem cópia, ou com cópia vazia (404 ou 403), a lista fica vazia. */
    private Autoria autoria(Contexto ctx, String chave, String lista) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDeAutoria(chave, lista));
        if (!existe(ctx, arq) || arq.bytes() == 0) {
            return new Autoria(null, null);
        }
        JsonNode itens = Json.ler(Coleta.caminho(ctx, arq));
        return new Autoria(itens.isArray() ? itens : null, Prov.de(FONTE_API, arq.url(), arq));
    }

    /** Data e ementa dos projetos procurados, nas listas anuais desde 2022. Guarda em {@code usadas} a lista de cada ano. */
    private Map<String, ProjetoDoAno> projetosDoAno(Contexto ctx, Set<String> procurados, Map<Integer, Coleta.Arquivo> usadas) {
        Map<String, ProjetoDoAno> projetos = new HashMap<>();
        for (int ano = PRIMEIRO_ANO_PROPOSICOES; ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "projetos/" + ano + ".json");
            if (!existe(ctx, arq) || arq.bytes() == 0) {
                continue;
            }
            usadas.put(ano, arq);
            for (JsonNode p : Json.ler(Coleta.caminho(ctx, arq))) {
                String id = identificacao(p);
                if (id != null && procurados.contains(id)) {
                    projetos.putIfAbsent(id, new ProjetoDoAno(dia(p, "data"), Json.texto(p, "ementa")));
                }
            }
        }
        return projetos;
    }

    /** Vereadores em exercício pelas cópias em cache de vereadores e gabinetes. Vazio se faltar uma delas. */
    private List<Vereador> emExercicio(Contexto ctx) {
        Coleta.Arquivo arqVereadores = Coleta.metadados(ctx, nome(), "vereadores.json");
        Coleta.Arquivo arqGabinetes = Coleta.metadados(ctx, nome(), "gabinetes.json");
        if (!existe(ctx, arqVereadores) || !existe(ctx, arqGabinetes)) {
            return List.of();
        }
        return emExercicio(Json.ler(Coleta.caminho(ctx, arqVereadores)), Json.ler(Coleta.caminho(ctx, arqGabinetes)), ctx.hoje());
    }

    /**
     * Vereadores em exercício em {@code hoje}: ocupam um gabinete vigente na legislatura mais recente e têm mandato vigente.
     * O código da ocupação é a chave do vereador. A lista de mandatos sozinha inclui suplentes sem exercício.
     */
    public static List<Vereador> emExercicio(JsonNode vereadores, JsonNode gabinetes, LocalDate hoje) {
        int legislatura = 0;
        for (JsonNode g : gabinetes) {
            legislatura = Math.max(legislatura, g.path("legislatura").asInt());
        }
        Set<Integer> ocupantes = new HashSet<>();
        for (JsonNode g : gabinetes) {
            if (g.path("legislatura").asInt() == legislatura && vigente(data(g, "inicio"), data(g, "fim"), hoje)) {
                ocupantes.add(g.path("codigo").asInt());
            }
        }
        List<Vereador> lista = new ArrayList<>();
        for (JsonNode v : vereadores) {
            int chave = v.path("chave").asInt();
            if (!ocupantes.contains(chave)) {
                continue;
            }
            for (JsonNode m : v.path("mandatos")) {
                LocalDate inicio = data(m, "inicio");
                LocalDate fim = data(m, "fim");
                if (vigente(inicio, fim, hoje)) {
                    lista.add(new Vereador(String.valueOf(chave), Json.texto(v, "nome"),
                            Json.texto(m.path("partido"), "sigla"), inicio, fim, legislatura, v.path("cargos")));
                    break;
                }
            }
        }
        return lista;
    }

    /**
     * Início mais recente de cada cargo exclusivo vigente entre os vereadores em exercício.
     * A fonte deixa cargo antigo sem data de fim quando há sucessor. O de início anterior deixa de valer.
     */
    public static Map<String, LocalDate> inicioMaisRecenteDosExclusivos(List<Vereador> vereadores, LocalDate hoje) {
        Map<String, LocalDate> maisRecentes = new HashMap<>();
        for (Vereador v : vereadores) {
            for (JsonNode c : v.cargos()) {
                LocalDate inicio = data(c, "inicio");
                if (vigente(inicio, data(c, "fim"), hoje) && exclusivo(c)) {
                    maisRecentes.merge(chaveDoCargo(c), inicio, (a, b) -> b.isAfter(a) ? b : a);
                }
            }
        }
        return maisRecentes;
    }

    /** Cargos vigentes em comissões e na Mesa. Cargo sem data de fim ainda vale, salvo se um sucessor o substituiu. */
    public static List<OrgaoOuFrente> orgaos(JsonNode cargos, Map<Integer, String> siglas, Map<String, LocalDate> maisRecentes,
                                             LocalDate hoje, Prov prov) {
        List<OrgaoOuFrente> lista = new ArrayList<>();
        for (JsonNode c : cargos) {
            LocalDate inicio = data(c, "inicio");
            LocalDate fim = data(c, "fim");
            String nomeDoEnte = Json.texto(c.path("ente"), "nome");
            if (nomeDoEnte == null || !vigente(inicio, fim, hoje)) {
                continue;
            }
            LocalDate recente = exclusivo(c) ? maisRecentes.get(chaveDoCargo(c)) : null;
            if (recente != null && inicio.isBefore(recente)) {
                continue;
            }
            if (nomeDoEnte.startsWith(PREFIXO_COMISSAO)) {
                String nome = titulo(nomeDoEnte.substring(PREFIXO_COMISSAO.length()));
                String sigla = siglas.get(c.path("ente").path("chave").asInt());
                lista.add(new OrgaoOuFrente("comissao", nome, sigla, papel(Json.texto(c, "chave"), Json.texto(c, "nome"), true),
                        inicio, fim, prov));
            } else {
                String nome = Texto.normalizarNome(nomeDoEnte).equals(MESA_NORMALIZADA)
                        ? "Mesa da Câmara Municipal de São Paulo" : titulo(nomeDoEnte);
                lista.add(new OrgaoOuFrente("orgao", nome, null, papel(Json.texto(c, "chave"), Json.texto(c, "nome"), false),
                        inicio, fim, prov));
            }
        }
        lista.sort(Comparator.comparing(OrgaoOuFrente::nome).thenComparing(OrgaoOuFrente::papel));
        return lista;
    }

    private static boolean eComissao(JsonNode cargo) {
        String nomeDoEnte = Json.texto(cargo.path("ente"), "nome");
        return nomeDoEnte != null && nomeDoEnte.startsWith(PREFIXO_COMISSAO);
    }

    /** Cargo com um titular por vez: Mesa, presidência e vice-presidência de comissão. Membro e relator não são. */
    static boolean exclusivo(JsonNode cargo) {
        String chave = Json.texto(cargo, "chave");
        return !eComissao(cargo) || "P".equals(chave) || "V".equals(chave);
    }

    static String chaveDoCargo(JsonNode cargo) {
        return cargo.path("ente").path("chave").asInt() + "|" + Json.texto(cargo, "chave");
    }

    static Map<Integer, String> siglasDasComissoes(JsonNode comissoes) {
        Map<Integer, String> siglas = new HashMap<>();
        for (JsonNode c : comissoes) {
            String sigla = Json.texto(c, "sigla");
            if (sigla != null) {
                siglas.put(c.path("chave").asInt(), sigla);
            }
        }
        return siglas;
    }

    /** Papéis de comissão vêm como letra (P, V, M, R); os da Mesa vêm com o nome do cargo. */
    static String papel(String chave, String nome, boolean comissao) {
        if (comissao && chave != null) {
            switch (chave) {
                case "P":
                    return "Presidente";
                case "V":
                    return "Vice-Presidente";
                case "M":
                    return "Membro";
                case "R":
                    return "Relator";
                default:
                    break;
            }
        }
        return nome == null ? "Membro" : nome;
    }

    /** O serviço entrega nomes em caixa alta. */
    static String titulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String[] partes = texto.strip().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            if (i > 0 && PREPOSICOES.contains(p)) {
                sb.append(p);
            } else {
                sb.append(Character.toUpperCase(p.charAt(0)));
                sb.append(p, 1, p.length());
            }
            if (i < partes.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    static LocalDate data(JsonNode no, String campo) {
        String texto = Json.texto(no, campo);
        if (texto == null || texto.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(texto.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    static boolean vigente(LocalDate inicio, LocalDate fim, LocalDate hoje) {
        return inicio != null && !inicio.isAfter(hoje) && (fim == null || !fim.isBefore(hoje));
    }

    private static boolean existe(Contexto ctx, Coleta.Arquivo arquivo) {
        return arquivo != null && Files.exists(Coleta.caminho(ctx, arquivo));
    }
}
