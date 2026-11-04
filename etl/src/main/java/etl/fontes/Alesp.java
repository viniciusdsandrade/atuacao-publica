package etl.fontes;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Registros.Despesa;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Participacao;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Registros.Voto;
import etl.Texto;

/**
 * Adaptador da Assembleia Legislativa do Estado de São Paulo (ALESP) (ADR-0013).
 * Coleta parlamentares da 19ª (2019 a 2023) e 20ª (2023 a 2027) legislaturas,
 * despesas de verba de gabinete, comissões permanentes e votações.
 */
public final class Alesp implements Fonte {

    public static final String FONTE_DEPUTADOS = "alesp-deputados";
    public static final String FONTE_DESPESAS = "alesp-despesas";
    public static final String FONTE_COMISSOES = "alesp-comissoes";
    public static final String FONTE_COMISSOES_REUNIOES = "alesp-comissoes-reunioes";
    public static final String FONTE_COMISSOES_VOTACOES = "alesp-comissoes-votacoes";
    public static final String FONTE_PROPOSITURAS = "alesp-proposituras";

    public static final String UF = "SP";
    public static final String API = "https://legis-api-portal-prd.al.sp.gov.br";
    public static final String REPO = "https://www.al.sp.gov.br/repositorioDados";
    public static final String SITE = "https://www.al.sp.gov.br";

    private static final int LEGISLATURA_19 = 19;
    private static final int LEGISLATURA_20 = 20;

    /** Início e término oficial das legislaturas da ALESP cobertas. */
    private static final LocalDate INICIO_LEG_19 = LocalDate.of(2019, 3, 15);
    private static final LocalDate FIM_LEG_19 = LocalDate.of(2023, 3, 14);
    private static final LocalDate INICIO_LEG_20 = LocalDate.of(2023, 3, 15);
    private static final LocalDate FIM_LEG_20 = LocalDate.of(2027, 1, 31);

    /** Início do período de cobertura histórico (ADR-0009). */
    private static final LocalDate INICIO_COBERTURA = LocalDate.of(2022, 1, 1);

    @Override
    public String nome() {
        return "alesp";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        coleta.obter(API + "/parlamentar-portal/todos", "parlamentares-todos.json", "application/json", Politica.SEMANAL);
        coleta.obter(REPO + "/deputados/deputados.xml", "deputados.xml", "application/xml", Politica.SEMANAL);
        coleta.obter(REPO + "/deputados/despesas_gabinetes.xml", "despesas_gabinetes.xml", "application/xml", Politica.SEMANAL);
        coleta.obter(API + "/colegiado-ocupacao/todos", "colegiado-ocupacoes.json", "application/json", Politica.SEMANAL);
        coleta.obter(REPO + "/processo_legislativo/comissoes_permanentes_reunioes.xml",
                "comissoes_permanentes_reunioes.xml", "application/xml", Politica.SEMANAL);
        coleta.obter(REPO + "/processo_legislativo/comissoes_permanentes_votacoes.xml",
                "comissoes_permanentes_votacoes.xml", "application/xml", Politica.SEMANAL);
        coleta.obter(REPO + "/processo_legislativo/proposituras.zip", "proposituras.zip", "application/zip", Politica.SEMANAL);
        coleta.obter(REPO + "/processo_legislativo/documento_autor.zip", "documento_autor.zip", "application/zip", Politica.SEMANAL);

        Coleta.Arquivo arqTodos = Coleta.metadados(ctx, "alesp", "parlamentares-todos.json");
        if (arqTodos != null) {
            Path caminhoTodos = Coleta.caminho(ctx, arqTodos);
            if (Files.exists(caminhoTodos)) {
                JsonNode lista = Json.ler(caminhoTodos);
                for (JsonNode p : lista) {
                    if (temLegislaturaDeInteresse(p)) {
                        String mat = Json.texto(p, "nuMatricula");
                        if (mat != null && !mat.isBlank()) {
                            coleta.obter(API + "/parlamentar-portal/detalhes/" + mat,
                                    "detalhes/" + mat + ".json", "application/json", Politica.SEMANAL);
                        }
                    }
                }
            }
        }
    }

    private static boolean temLegislaturaDeInteresse(JsonNode parl) {
        JsonNode legs = parl.path("listaLegislaturas");
        if (legs.isArray()) {
            for (JsonNode l : legs) {
                int num = l.asInt();
                if (num == LEGISLATURA_19 || num == LEGISLATURA_20) {
                    return true;
                }
            }
        }
        return false;
    }

    public record Blocos(List<Mandato> mandatos, List<Participacao> participacaoVotacoes, List<Voto> votos,
                         List<Despesa> despesas, List<Proposicao> proposicoes,
                         @JsonProperty("orgaos_e_frentes") List<OrgaoOuFrente> orgaosEFrentes,
                         Map<String, List<FonteUsada>> fontes) {
    }

    private static final class Acumulado {
        final List<Mandato> mandatos = new ArrayList<>();
        final List<Despesa> despesas = new ArrayList<>();
        final Set<String> chavesDespesa = new HashSet<>();
        final List<OrgaoOuFrente> orgaos = new ArrayList<>();
        final List<Voto> votos = new ArrayList<>();
        final List<Proposicao> proposicoes = new ArrayList<>();
        final Map<String, int[]> participacao = new TreeMap<>();
        final Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>();
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path pastaRaw = ctx.raw().resolve("alesp");
        if (!Files.exists(pastaRaw)) {
            System.out.println("alesp: sem dados coletados para normalizar");
            return;
        }

        Coleta.Arquivo arqTodos = Coleta.metadados(ctx, "alesp", "parlamentares-todos.json");
        if (arqTodos == null) {
            System.out.println("alesp: parlamentares-todos.json ausente");
            return;
        }

        JsonNode listaTodos = Json.ler(Coleta.caminho(ctx, arqTodos));
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        Map<Integer, String> matriculaPorIdParlamentar = new HashMap<>();
        Map<String, String> matriculaPorNome = new HashMap<>();
        Map<Integer, String> matriculaPorIdAutor = new HashMap<>();
        List<Pessoa> pessoas = new ArrayList<>();

        for (JsonNode p : listaTodos) {
            if (!temLegislaturaDeInteresse(p)) {
                continue;
            }
            String matricula = Json.texto(p, "nuMatricula");
            if (matricula == null || matricula.isBlank()) {
                continue;
            }
            Integer idParl = Json.inteiro(p, "idParlamentar");
            if (idParl != null) {
                matriculaPorIdParlamentar.put(idParl, matricula);
            }
            String nomeP = Json.texto(p, "txNomeParlamentar");
            if (nomeP != null && !nomeP.isBlank()) {
                matriculaPorNome.put(Texto.normalizarNome(nomeP), matricula);
            }
            String nomePlen = Json.texto(p, "txNomePlenario");
            if (nomePlen != null && !nomePlen.isBlank()) {
                matriculaPorNome.put(Texto.normalizarNome(nomePlen), matricula);
            }

            Acumulado ac = new Acumulado();
            acumulados.put(matricula, ac);

            Coleta.Arquivo arqDet = Coleta.metadados(ctx, "alesp", "detalhes/" + matricula + ".json");
            JsonNode det = (arqDet != null && Files.exists(Coleta.caminho(ctx, arqDet)))
                    ? Json.ler(Coleta.caminho(ctx, arqDet)) : p;

            int idAutor = det.path("idAutor").asInt(0);
            if (idAutor > 0) {
                matriculaPorIdAutor.put(idAutor, matricula);
            }
            String detNome = Json.texto(det, "txNomeParlamentar");
            if (detNome != null && !detNome.isBlank()) {
                matriculaPorNome.put(Texto.normalizarNome(detNome), matricula);
            }

            String nome = primeiro(Json.texto(det, "txNomeParlamentar"), Json.texto(p, "txNomeParlamentar"));
            String partido = primeiro(Json.texto(det, "txPartido"), Json.texto(p, "txPartido"));
            boolean emExercicio = det.path("isEmExercicio").asBoolean(false);
            String situacao = emExercicio ? "Exercício" : "Fim de mandato";

            String fotoUrl = null;
            JsonNode bio = det.path("biografia");
            if (bio.hasNonNull("txFotoGrande")) {
                fotoUrl = urlAbsoluta(Json.texto(bio, "txFotoGrande"));
            } else if (p.hasNonNull("txFoto")) {
                fotoUrl = urlAbsoluta(Json.texto(p, "txFoto"));
            }

            Prov provDep = Prov.de(FONTE_DEPUTADOS, API + "/parlamentar-portal/detalhes/" + matricula,
                    arqDet != null ? arqDet : arqTodos);

            List<LinkOficial> links = List.of(
                    new LinkOficial("Página na ALESP", SITE + "/deputado/?matricula=" + matricula, provDep));

            pessoas.add(new Pessoa("alesp", matricula, nome, "deputado_estadual", "estadual", UF, UF,
                    partido, fotoUrl, situacao, emExercicio, links));

            // Mandatos
            montarMandatos(det, ac, provDep);
            ac.fontes.computeIfAbsent("mandatos", k -> new ArrayList<>()).add(FonteUsada.de(FONTE_DEPUTADOS, arqTodos));
        }

        // Índices legados e nomes de deputados.xml
        Coleta.Arquivo arqDepXml = Coleta.metadados(ctx, "alesp", "deputados.xml");
        if (arqDepXml != null && Files.exists(Coleta.caminho(ctx, arqDepXml))) {
            carregarIndicesDeputadosXml(Coleta.caminho(ctx, arqDepXml), matriculaPorIdParlamentar, matriculaPorNome);
        }

        // Despesas de gabinete em streaming
        processarDespesas(ctx, acumulados);

        // Comissões permanentes e órgãos
        processarColegiados(ctx, acumulados);

        // Votações nominais em comissões em streaming
        processarVotacoes(ctx, acumulados, matriculaPorIdParlamentar, matriculaPorNome);

        // Proposições e autoria em streaming
        processarProposicoes(ctx, acumulados, matriculaPorIdAutor);

        // Gravação
        Path saida = ctx.normalizado().resolve("alesp");
        Path pastaBlocos = saida.resolve("blocos");
        Files.createDirectories(pastaBlocos);
        Json.gravar(saida.resolve("pessoas.json"), Json.SAIDA.valueToTree(pessoas));

        for (var e : acumulados.entrySet()) {
            String matricula = e.getKey();
            Acumulado ac = e.getValue();
            List<Participacao> part = participacao(ac.participacao,
                    ac.fontes.getOrDefault("votos", List.of()).isEmpty() ? null
                            : new Prov(FONTE_COMISSOES_VOTACOES, REPO + "/processo_legislativo/comissoes_permanentes_votacoes.xml",
                            ctx.agora()));
            Blocos blocos = new Blocos(ac.mandatos, part, ac.votos, ac.despesas, ac.proposicoes, ac.orgaos, ac.fontes);
            Json.gravar(pastaBlocos.resolve(matricula + ".json"), Json.SAIDA.valueToTree(blocos));
        }

        System.out.println("alesp: " + pessoas.size() + " deputados estaduais normalizados");
    }

    static String urlAbsoluta(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return null;
        }
        String normalizado = caminho.strip().replace(" ", "%20");
        if (normalizado.startsWith("http://") || normalizado.startsWith("https://")) {
            return normalizado;
        }
        return SITE + (normalizado.startsWith("/") ? "" : "/") + normalizado;
    }

    private static void montarMandatos(JsonNode det, Acumulado ac, Prov prov) {
        JsonNode listaMandatos = det.path("listaMandatos");
        if (listaMandatos.isArray() && !listaMandatos.isEmpty()) {
            for (JsonNode m : listaMandatos) {
                int leg = m.path("idLegislatura").asInt();
                if (leg == LEGISLATURA_19) {
                    ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, INICIO_LEG_19, FIM_LEG_19,
                            "Exercício", "Titular", LEGISLATURA_19, 2018, null, prov));
                } else if (leg == LEGISLATURA_20) {
                    ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, INICIO_LEG_20, FIM_LEG_20,
                            "Exercício", "Titular", LEGISLATURA_20, 2022, null, prov));
                }
            }
        }
        if (ac.mandatos.isEmpty()) {
            // Fallback pela listaLegislaturas
            JsonNode legs = det.path("listaLegislaturas");
            if (legs.isArray()) {
                for (JsonNode l : legs) {
                    int leg = l.isNumber() ? l.asInt() : l.path("idLegislatura").asInt();
                    if (leg == LEGISLATURA_19) {
                        ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, INICIO_LEG_19, FIM_LEG_19,
                                "Exercício", "Titular", LEGISLATURA_19, 2018, null, prov));
                    } else if (leg == LEGISLATURA_20) {
                        ac.mandatos.add(new Mandato("deputado_estadual", "estadual", UF, UF, INICIO_LEG_20, FIM_LEG_20,
                                "Exercício", "Titular", LEGISLATURA_20, 2022, null, prov));
                    }
                }
            }
        }
        ac.mandatos.sort(Comparator.comparing(Mandato::inicio));
    }

    public record DespesaComMatricula(String matricula, Despesa despesa) {
    }

    static void processarDespesas(Contexto ctx, Map<String, Acumulado> acumulados) throws Exception {
        Coleta.Arquivo arqDesp = Coleta.metadados(ctx, "alesp", "despesas_gabinetes.xml");
        if (arqDesp == null) {
            return;
        }
        Path caminho = Coleta.caminho(ctx, arqDesp);
        if (!Files.exists(caminho)) {
            return;
        }

        Prov prov = Prov.de(FONTE_DESPESAS, REPO + "/deputados/despesas_gabinetes.xml", arqDesp);
        FonteUsada fonteUsada = FonteUsada.de(FONTE_DESPESAS, arqDesp);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        try (InputStream in = Files.newInputStream(caminho)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "despesa".equals(reader.getLocalName())) {
                    DespesaComMatricula dm = lerDespesa(reader, prov);
                    if (dm != null) {
                        Acumulado ac = acumulados.get(dm.matricula());
                        if (ac != null) {
                            Despesa d = dm.despesa();
                            String chave = d.ano() + "|" + d.mes() + "|" + d.categoria() + "|" + d.valor() + "|" + d.fornecedorNome();
                            if (ac.chavesDespesa.add(chave)) {
                                ac.despesas.add(d);
                                ac.fontes.computeIfAbsent("despesas", k -> new ArrayList<>()).add(fonteUsada);
                            }
                        }
                    }
                }
            }
        }

        for (Acumulado ac : acumulados.values()) {
            ac.despesas.sort(Comparator.comparing(Despesa::ano).thenComparing(Despesa::mes).reversed());
        }
    }

    public static DespesaComMatricula lerDespesa(XMLStreamReader reader, Prov prov) throws Exception {
        String anoStr = null, mat = null, mesStr = null, valorStr = null, cnpjRaw = null, tipo = null, fornRaw = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = reader.getLocalName();
                String text = reader.getElementText();
                if ("Ano".equals(tag)) anoStr = text;
                else if ("Matricula".equals(tag)) mat = text;
                else if ("Mes".equals(tag)) mesStr = text;
                else if ("Valor".equals(tag)) valorStr = text;
                else if ("CNPJ".equals(tag)) cnpjRaw = text;
                else if ("Tipo".equals(tag)) tipo = text;
                else if ("Fornecedor".equals(tag)) fornRaw = text;
            } else if (event == XMLStreamConstants.END_ELEMENT && "despesa".equals(reader.getLocalName())) {
                break;
            }
        }
        if (anoStr == null || mesStr == null || valorStr == null || mat == null) {
            return null;
        }
        int ano;
        int mes;
        try {
            ano = Integer.parseInt(anoStr.strip());
            mes = Integer.parseInt(mesStr.strip());
        } catch (NumberFormatException e) {
            return null;
        }
        if (ano < 2022 || mes < 1 || mes > 12) {
            return null;
        }
        BigDecimal valor = Texto.valor(valorStr);
        if (valor == null) {
            return null;
        }

        // Privacidade: descarta estritamente CPF (11 dígitos) e limpa nome
        String cnpj = Texto.cnpj(cnpjRaw);
        String fornecedor = Guarda.semCpf(fornRaw);
        LocalDate data = LocalDate.of(ano, mes, 1);

        Despesa d = new Despesa(data, ano, mes, primeiro(tipo, "sem categoria na fonte"),
                fornecedor, cnpj, valor, null, null, prov);
        return new DespesaComMatricula(mat.strip(), d);
    }

    static void processarColegiados(Contexto ctx, Map<String, Acumulado> acumulados) throws Exception {
        Coleta.Arquivo arqColegiados = Coleta.metadados(ctx, "alesp", "colegiado-ocupacoes.json");
        if (arqColegiados == null) {
            return;
        }
        Path caminho = Coleta.caminho(ctx, arqColegiados);
        if (!Files.exists(caminho)) {
            return;
        }

        Prov prov = Prov.de(FONTE_COMISSOES, API + "/colegiado-ocupacao/todos", arqColegiados);
        FonteUsada fonteUsada = FonteUsada.de(FONTE_COMISSOES, arqColegiados);

        JsonNode lista = Json.ler(caminho);
        for (JsonNode item : lista) {
            String mat = Json.texto(item, "nuMatriculaParlamentar");
            if (mat == null) {
                mat = Json.texto(item.path("parlamentar"), "nuMatricula");
            }
            if (mat == null) {
                continue;
            }
            Acumulado ac = acumulados.get(mat);
            if (ac == null) {
                continue;
            }

            String nomeColegiado = item.hasNonNull("colegiado")
                    ? (item.get("colegiado").isObject() ? Json.texto(item.path("colegiado"), "nome") : Json.texto(item, "colegiado"))
                    : null;
            if (nomeColegiado == null || nomeColegiado.isBlank()) {
                continue;
            }

            String sigla = item.path("colegiado").hasNonNull("sigla") ? Json.texto(item.path("colegiado"), "sigla") : null;
            String papel = primeiro(Json.texto(item, "ocupacao"), "Membro");

            String dtInicioStr = Json.texto(item, "dtInicio");
            String dtFimStr = Json.texto(item, "dtFim");
            LocalDate inicio = dtInicioStr != null && dtInicioStr.length() >= 10 ? LocalDate.parse(dtInicioStr.substring(0, 10)) : null;
            LocalDate fim = dtFimStr != null && dtFimStr.length() >= 10 ? LocalDate.parse(dtFimStr.substring(0, 10)) : null;

            if (fim != null && fim.isBefore(INICIO_COBERTURA)) {
                continue;
            }

            ac.orgaos.add(new OrgaoOuFrente("comissao", nomeColegiado, sigla, papel, inicio, fim, prov));
            ac.fontes.computeIfAbsent("orgaos_e_frentes", k -> new ArrayList<>()).add(fonteUsada);
        }

        for (Acumulado ac : acumulados.values()) {
            ac.orgaos.sort(Comparator.comparing(OrgaoOuFrente::inicio, Comparator.nullsLast(Comparator.naturalOrder())));
        }
    }

    static void processarVotacoes(Contexto ctx, Map<String, Acumulado> acumulados,
                                 Map<Integer, String> matriculaPorIdParlamentar,
                                 Map<String, String> matriculaPorNome) throws Exception {
        Coleta.Arquivo arqVot = Coleta.metadados(ctx, "alesp", "comissoes_permanentes_votacoes.xml");
        if (arqVot == null) {
            return;
        }
        Path caminho = Coleta.caminho(ctx, arqVot);
        if (!Files.exists(caminho)) {
            return;
        }

        Map<String, String> dataPorIdReuniao = carregarDatasReunioes(ctx);

        Prov prov = Prov.de(FONTE_COMISSOES_VOTACOES,
                REPO + "/processo_legislativo/comissoes_permanentes_votacoes.xml", arqVot);
        FonteUsada fonteUsada = FonteUsada.de(FONTE_COMISSOES_VOTACOES, arqVot);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        try (InputStream in = Files.newInputStream(caminho)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "ReuniaoComissaoVotacao".equals(reader.getLocalName())) {
                    String votoTexto = null, tipoVoto = null, idDepStr = null, idDoc = null, idReuniao = null, nomeDep = null;
                    while (reader.hasNext()) {
                        int sub = reader.next();
                        if (sub == XMLStreamConstants.START_ELEMENT) {
                            String tag = reader.getLocalName();
                            String text = reader.getElementText();
                            if ("Voto".equals(tag)) votoTexto = text;
                            else if ("TipoVoto".equals(tag)) tipoVoto = text;
                            else if ("IdDeputado".equals(tag)) idDepStr = text;
                            else if ("Deputado".equals(tag)) nomeDep = text;
                            else if ("IdDocumento".equals(tag)) idDoc = text;
                            else if ("IdReuniao".equals(tag)) idReuniao = text;
                        } else if (sub == XMLStreamConstants.END_ELEMENT && "ReuniaoComissaoVotacao".equals(reader.getLocalName())) {
                            break;
                        }
                    }

                    String mat = null;
                    if (idDepStr != null) {
                        try {
                            mat = matriculaPorIdParlamentar.get(Integer.parseInt(idDepStr.strip()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (mat == null && nomeDep != null && !nomeDep.isBlank()) {
                        mat = matriculaPorNome.get(Texto.normalizarNome(nomeDep));
                    }

                    if (mat != null) {
                        Acumulado ac = acumulados.get(mat);
                        if (ac != null) {
                            String data = idReuniao != null ? dataPorIdReuniao.get(idReuniao.strip()) : null;
                            if (data != null && data.compareTo("2022-01-01") >= 0) {
                                String votoPadrao = converterTipoVoto(tipoVoto, votoTexto);
                                String votacaoId = "comissao-" + idReuniao.strip() + "-" + (idDoc != null ? idDoc.strip() : "0");
                                Voto v = new Voto(votacaoId, data, "Comissão Permanente",
                                        "Documento " + (idDoc != null ? idDoc.strip() : "s/n"),
                                        votoTexto != null && !votoTexto.isBlank() ? votoTexto.strip() : null,
                                        votoPadrao, null, null, null, null, prov);
                                ac.votos.add(v);
                                ac.fontes.computeIfAbsent("votos", k -> new ArrayList<>()).add(fonteUsada);

                                String ano = data.substring(0, 4);
                                int[] cont = ac.participacao.computeIfAbsent(ano, k -> new int[2]);
                                cont[0]++;
                                cont[1]++;
                            }
                        }
                    }
                }
            }
        }

        for (Acumulado ac : acumulados.values()) {
            ac.votos.sort(Comparator.comparing(Voto::data).reversed());
        }
    }

    static void carregarIndicesDeputadosXml(Path caminho, Map<Integer, String> porId, Map<String, String> porNome) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        try (InputStream in = Files.newInputStream(caminho)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "Deputado".equals(reader.getLocalName())) {
                    String idDepStr = null, idSplStr = null, matStr = null, nome = null;
                    while (reader.hasNext()) {
                        int sub = reader.next();
                        if (sub == XMLStreamConstants.START_ELEMENT) {
                            String tag = reader.getLocalName();
                            String text = reader.getElementText();
                            if ("IdDeputado".equals(tag)) idDepStr = text;
                            else if ("IdSPL".equals(tag)) idSplStr = text;
                            else if ("Matricula".equals(tag)) matStr = text;
                            else if ("NomeParlamentar".equals(tag)) nome = text;
                        } else if (sub == XMLStreamConstants.END_ELEMENT && "Deputado".equals(reader.getLocalName())) {
                            break;
                        }
                    }
                    if (matStr != null && !matStr.isBlank()) {
                        String mat = matStr.strip();
                        if (idDepStr != null) {
                            try { porId.put(Integer.parseInt(idDepStr.strip()), mat); } catch (NumberFormatException ignored) {}
                        }
                        if (idSplStr != null) {
                            try { porId.put(Integer.parseInt(idSplStr.strip()), mat); } catch (NumberFormatException ignored) {}
                        }
                        if (nome != null && !nome.isBlank()) {
                            porNome.put(Texto.normalizarNome(nome), mat);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("alesp: erro ao ler deputados.xml: " + e.getMessage());
        }
    }

    static Map<String, String> carregarDatasReunioes(Contexto ctx) {
        Map<String, String> datas = new HashMap<>();
        Coleta.Arquivo arqReunioes = Coleta.metadados(ctx, "alesp", "comissoes_permanentes_reunioes.xml");
        if (arqReunioes == null) {
            return datas;
        }
        Path caminho = Coleta.caminho(ctx, arqReunioes);
        if (!Files.exists(caminho)) {
            return datas;
        }
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        try (InputStream in = Files.newInputStream(caminho)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "ReuniaoComissao".equals(reader.getLocalName())) {
                    String idReuniao = null;
                    String dataStr = null;
                    while (reader.hasNext()) {
                        int sub = reader.next();
                        if (sub == XMLStreamConstants.START_ELEMENT) {
                            String tag = reader.getLocalName();
                            String text = reader.getElementText();
                            if ("IdReuniao".equals(tag)) idReuniao = text;
                            else if ("Data".equals(tag)) dataStr = text;
                        } else if (sub == XMLStreamConstants.END_ELEMENT && "ReuniaoComissao".equals(reader.getLocalName())) {
                            break;
                        }
                    }
                    if (idReuniao != null && dataStr != null && dataStr.length() >= 10) {
                        datas.put(idReuniao.strip(), dataStr.substring(0, 10));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("alesp: erro ao ler comissoes_permanentes_reunioes.xml: " + e.getMessage());
        }
        return datas;
    }

    private static String converterTipoVoto(String tipo, String texto) {
        if ("F".equalsIgnoreCase(tipo)) return "Favorável";
        if ("C".equalsIgnoreCase(tipo)) return "Contrário";
        if ("P".equalsIgnoreCase(tipo)) return "Em separado";
        if (texto != null && !texto.isBlank()) {
            return texto.strip();
        }
        return "Registrado";
    }

    static List<Participacao> participacao(Map<String, int[]> contagens, Prov prov) {
        if (prov == null) {
            return List.of();
        }
        List<Participacao> lista = new ArrayList<>();
        contagens.forEach((ano, valores) -> {
            int total = valores[0];
            int votou = valores[1];
            lista.add(new Participacao(ano, total, votou, total - votou, Map.of(), prov));
        });
        lista.sort(Comparator.comparing(Participacao::periodo).reversed());
        return lista;
    }

    public record TipoNatureza(String nome, String sigla) {
    }

    public static TipoNatureza natureza(String idNat) {
        if (idNat == null) return new TipoNatureza("Proposição", "PROP");
        return switch (idNat.strip()) {
            case "1" -> new TipoNatureza("Projeto de Lei", "PL");
            case "2" -> new TipoNatureza("Projeto de Lei Complementar", "PLC");
            case "3" -> new TipoNatureza("Projeto de Resolução", "PR");
            case "4" -> new TipoNatureza("Projeto de Decreto Legislativo", "PDL");
            case "5" -> new TipoNatureza("Proposta de Emenda Constitucional", "PEC");
            case "6" -> new TipoNatureza("Moção", "MOC");
            case "7" -> new TipoNatureza("Requerimento de Congratulações", "RCN");
            case "8" -> new TipoNatureza("Requerimento de Informação", "RIC");
            case "9" -> new TipoNatureza("Indicação", "IND");
            case "1000000003" -> new TipoNatureza("Requerimento de Comissão", "REQ");
            default -> new TipoNatureza("Proposição", "PROP");
        };
    }

    static void processarProposicoes(Contexto ctx, Map<String, Acumulado> acumulados,
                                     Map<Integer, String> matriculaPorIdAutor) throws Exception {
        Coleta.Arquivo arqAutores = Coleta.metadados(ctx, "alesp", "documento_autor.zip");
        Coleta.Arquivo arqProps = Coleta.metadados(ctx, "alesp", "proposituras.zip");
        if (arqAutores == null || arqProps == null) {
            return;
        }
        Path camAutores = Coleta.caminho(ctx, arqAutores);
        Path camProps = Coleta.caminho(ctx, arqProps);
        if (!Files.exists(camAutores) || !Files.exists(camProps)) {
            return;
        }

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        Map<String, List<String>> matriculasPorDocumento = new HashMap<>();

        // 1. Ler documento_autor.xml em streaming do zip
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(camAutores))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("documento_autor.xml".equalsIgnoreCase(entry.getName())) {
                    XMLStreamReader reader = factory.createXMLStreamReader(zis, "UTF-8");
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT && "DocumentoAutor".equals(reader.getLocalName())) {
                            String idAutorStr = null, idDoc = null;
                            while (reader.hasNext()) {
                                int sub = reader.next();
                                if (sub == XMLStreamConstants.START_ELEMENT) {
                                    String tag = reader.getLocalName();
                                    String text = reader.getElementText();
                                    if ("IdAutor".equals(tag)) idAutorStr = text;
                                    else if ("IdDocumento".equals(tag)) idDoc = text;
                                } else if (sub == XMLStreamConstants.END_ELEMENT && "DocumentoAutor".equals(reader.getLocalName())) {
                                    break;
                                }
                            }
                            if (idAutorStr != null && idDoc != null) {
                                try {
                                    int idAut = Integer.parseInt(idAutorStr.strip());
                                    String mat = matriculaPorIdAutor.get(idAut);
                                    if (mat != null) {
                                        matriculasPorDocumento.computeIfAbsent(idDoc.strip(), k -> new ArrayList<>()).add(mat);
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        if (matriculasPorDocumento.isEmpty()) {
            return;
        }

        Prov prov = Prov.de(FONTE_PROPOSITURAS, REPO + "/processo_legislativo/proposituras.zip", arqProps);
        FonteUsada fonteUsada = FonteUsada.de(FONTE_PROPOSITURAS, arqProps);

        // 2. Ler proposituras.xml em streaming do zip
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(camProps))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("proposituras.xml".equalsIgnoreCase(entry.getName())) {
                    XMLStreamReader reader = factory.createXMLStreamReader(zis, "UTF-8");
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT && "propositura".equals(reader.getLocalName())) {
                            String anoStr = null, idDoc = null, idNat = null, nroStr = null, ementaRaw = null, dtPub = null;
                            while (reader.hasNext()) {
                                int sub = reader.next();
                                if (sub == XMLStreamConstants.START_ELEMENT) {
                                    String tag = reader.getLocalName();
                                    String text = reader.getElementText();
                                    if ("AnoLegislativo".equals(tag)) anoStr = text;
                                    else if ("IdDocumento".equals(tag)) idDoc = text;
                                    else if ("IdNatureza".equals(tag)) idNat = text;
                                    else if ("NroLegislativo".equals(tag)) nroStr = text;
                                    else if ("Ementa".equals(tag)) ementaRaw = text;
                                    else if ("DtPublicacao".equals(tag)) dtPub = text;
                                } else if (sub == XMLStreamConstants.END_ELEMENT && "propositura".equals(reader.getLocalName())) {
                                    break;
                                }
                            }

                            if (idDoc != null && anoStr != null) {
                                try {
                                    int ano = Integer.parseInt(anoStr.strip());
                                    if (ano >= 2022) {
                                        List<String> mats = matriculasPorDocumento.get(idDoc.strip());
                                        if (mats != null && !mats.isEmpty()) {
                                            Integer nro = nroStr == null || nroStr.isBlank() ? null : Integer.parseInt(nroStr.strip());
                                            TipoNatureza nat = natureza(idNat);
                                            String ident = nro != null ? (nat.sigla() + " " + nro + "/" + ano) : (nat.nome() + " " + idDoc.strip());
                                            String ementa = Guarda.semCpf(ementaRaw);
                                            String dataApresentacao = (dtPub != null && dtPub.length() >= 10) ? dtPub.substring(0, 10) : null;
                                            String urlTexto = SITE + "/propositura/" + idDoc.strip();

                                            Proposicao prop = new Proposicao(idDoc.strip(), nat.nome(), nro, ano, ident,
                                                    ementa, "Apresentada", null, dataApresentacao, null, true, urlTexto, prov);

                                            for (String mat : mats) {
                                                Acumulado ac = acumulados.get(mat);
                                                if (ac != null) {
                                                    ac.proposicoes.add(prop);
                                                    ac.fontes.computeIfAbsent("proposicoes", k -> new ArrayList<>()).add(fonteUsada);
                                                }
                                            }
                                        }
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        for (Acumulado ac : acumulados.values()) {
            ac.proposicoes.sort(Comparator.comparing(Proposicao::dataApresentacao, Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }
}
