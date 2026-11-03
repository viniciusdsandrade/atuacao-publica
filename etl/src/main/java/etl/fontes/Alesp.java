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
    public static final String FONTE_COMISSOES_VOTACOES = "alesp-comissoes-votacoes";

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
        coleta.obter(REPO + "/processo_legislativo/comissoes_permanentes_votacoes.xml",
                "comissoes_permanentes_votacoes.xml", "application/xml", Politica.SEMANAL);

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
                         List<Despesa> despesas,
                         @JsonProperty("orgaos_e_frentes") List<OrgaoOuFrente> orgaosEFrentes,
                         Map<String, List<FonteUsada>> fontes) {
    }

    private static final class Acumulado {
        final List<Mandato> mandatos = new ArrayList<>();
        final List<Despesa> despesas = new ArrayList<>();
        final Set<String> chavesDespesa = new HashSet<>();
        final List<OrgaoOuFrente> orgaos = new ArrayList<>();
        final List<Voto> votos = new ArrayList<>();
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

            Acumulado ac = new Acumulado();
            acumulados.put(matricula, ac);

            Coleta.Arquivo arqDet = Coleta.metadados(ctx, "alesp", "detalhes/" + matricula + ".json");
            JsonNode det = (arqDet != null && Files.exists(Coleta.caminho(ctx, arqDet)))
                    ? Json.ler(Coleta.caminho(ctx, arqDet)) : p;

            String nome = primeiro(Json.texto(det, "txNomeParlamentar"), Json.texto(p, "txNomeParlamentar"));
            String partido = primeiro(Json.texto(det, "txPartido"), Json.texto(p, "txPartido"));
            boolean emExercicio = det.path("isEmExercicio").asBoolean(false);
            String situacao = emExercicio ? "Exercício" : "Fim de mandato";

            String fotoUrl = null;
            JsonNode bio = det.path("biografia");
            if (bio.hasNonNull("txFotoGrande")) {
                fotoUrl = urlAbsoluta(bio.get("txFotoGrande").stringValue());
            } else if (p.hasNonNull("txFoto")) {
                fotoUrl = urlAbsoluta(p.get("txFoto").stringValue());
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

        // Despesas de gabinete em streaming
        processarDespesas(ctx, acumulados);

        // Comissões permanentes e órgãos
        processarColegiados(ctx, acumulados);

        // Votações nominais em comissões em streaming
        processarVotacoes(ctx, acumulados, matriculaPorIdParlamentar);

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
            Blocos blocos = new Blocos(ac.mandatos, part, ac.votos, ac.despesas, ac.orgaos, ac.fontes);
            Json.gravar(pastaBlocos.resolve(matricula + ".json"), Json.SAIDA.valueToTree(blocos));
        }

        System.out.println("alesp: " + pessoas.size() + " deputados estaduais normalizados");
    }

    private static String urlAbsoluta(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return null;
        }
        if (caminho.startsWith("http://") || caminho.startsWith("https://")) {
            return caminho;
        }
        return SITE + (caminho.startsWith("/") ? "" : "/") + caminho;
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
                    Despesa d = lerDespesa(reader, prov);
                    if (d != null) {
                        String mat = readerProperty(reader, "matriculaTemp");
                        Acumulado ac = acumulados.get(mat);
                        if (ac != null) {
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

    public static Despesa lerDespesa(XMLStreamReader reader, Prov prov) throws Exception {
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

        // Guarda a matrícula no reader para associação
        readerPropertySet(reader, "matriculaTemp", mat.strip());

        return new Despesa(data, ano, mes, primeiro(tipo, "sem categoria na fonte"),
                fornecedor, cnpj, valor, null, null, prov);
    }

    private static final Map<XMLStreamReader, Map<String, String>> READER_PROPS = new HashMap<>();

    private static void readerPropertySet(XMLStreamReader reader, String key, String val) {
        READER_PROPS.computeIfAbsent(reader, k -> new HashMap<>()).put(key, val);
    }

    private static String readerProperty(XMLStreamReader reader, String key) {
        Map<String, String> m = READER_PROPS.get(reader);
        return m != null ? m.get(key) : null;
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
            String mat = null;
            if (item.hasNonNull("nuMatriculaParlamentar")) {
                mat = item.get("nuMatriculaParlamentar").stringValue();
            } else if (item.path("parlamentar").hasNonNull("nuMatricula")) {
                mat = item.path("parlamentar").get("nuMatricula").stringValue();
            }
            if (mat == null) {
                continue;
            }
            Acumulado ac = acumulados.get(mat);
            if (ac == null) {
                continue;
            }

            String nomeColegiado = item.hasNonNull("colegiado")
                    ? (item.get("colegiado").isObject() ? Json.texto(item.path("colegiado"), "nome") : item.get("colegiado").stringValue())
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
                                 Map<Integer, String> matriculaPorIdParlamentar) throws Exception {
        Coleta.Arquivo arqVot = Coleta.metadados(ctx, "alesp", "comissoes_permanentes_votacoes.xml");
        if (arqVot == null) {
            return;
        }
        Path caminho = Coleta.caminho(ctx, arqVot);
        if (!Files.exists(caminho)) {
            return;
        }

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
                    String votoTexto = null, tipoVoto = null, idDepStr = null, idDoc = null, idReuniao = null;
                    while (reader.hasNext()) {
                        int sub = reader.next();
                        if (sub == XMLStreamConstants.START_ELEMENT) {
                            String tag = reader.getLocalName();
                            String text = reader.getElementText();
                            if ("Voto".equals(tag)) votoTexto = text;
                            else if ("TipoVoto".equals(tag)) tipoVoto = text;
                            else if ("IdDeputado".equals(tag)) idDepStr = text;
                            else if ("IdDocumento".equals(tag)) idDoc = text;
                            else if ("IdReuniao".equals(tag)) idReuniao = text;
                        } else if (sub == XMLStreamConstants.END_ELEMENT && "ReuniaoComissaoVotacao".equals(reader.getLocalName())) {
                            break;
                        }
                    }

                    if (idDepStr != null) {
                        try {
                            int idDep = Integer.parseInt(idDepStr.strip());
                            String mat = matriculaPorIdParlamentar.get(idDep);
                            if (mat != null) {
                                Acumulado ac = acumulados.get(mat);
                                if (ac != null) {
                                    String votoPadrao = converterTipoVoto(tipoVoto, votoTexto);
                                    String votacaoId = "comissao-" + idReuniao + "-" + idDoc;
                                    Voto v = new Voto(votacaoId, null, "Comissão Permanente",
                                            "Documento " + idDoc, votoTexto != null ? votoTexto.strip() : null,
                                            votoPadrao, null, null, null, null, prov);
                                    ac.votos.add(v);
                                    ac.fontes.computeIfAbsent("votos", k -> new ArrayList<>()).add(fonteUsada);
                                }
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
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
        return lista;
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
