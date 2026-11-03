package etl.fontes;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Poder Executivo do Estado de São Paulo (ADR-0014):
 * 1. Agenda oficial do Governador de São Paulo na plataforma SP-Agendas (SGGD).
 * 2. Despesas orçamentárias mensais agregadas no Portal da Transparência do Estado de São Paulo.
 */
public final class ExecutivoSp implements Fonte {

    public static final String FONTE_AGENDAS = "sp-agendas";
    public static final String FONTE_TRANSPARENCIA = "sp-transparencia";
    public static final String FONTE_LEGISLACAO = "sp-legislacao";

    public static final String URL_AGENDAS = "https://spagendas.sp.gov.br";
    public static final String URL_TRANSPARENCIA = "https://www.transparencia.sp.gov.br";
    public static final String URL_LEGISLACAO = "https://www.al.sp.gov.br/repositorioDados/legislacao/legislacao_normas.zip";
    public static final String UF = "SP";

    // UUID do Gabinete do Governador na plataforma SP-Agendas
    public static final String ORGAO_GABINETE_GOVERNADOR = "40e96b34-9c5c-436c-82bd-e79d9f080697";
    // UUID do Cargo de Governador
    public static final String CARGO_GOVERNADOR = "ef4562ac-5970-4647-92ed-bcc38432ed4c";

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Compromisso(String ente, String data, String hora, String textoOficial, String local,
                              List<String> participantes, String snapshotUrl, @JsonUnwrapped Prov prov) {
    }

    public record Agregado(String ente, int ano, int mes, String orgao, String categoria, BigDecimal valor,
                           int quantidade, @JsonUnwrapped Prov prov) {
    }

    public record Ato(String ente, String idFonte, String tipo, String numero, String data,
                      String nome, String ementa, @JsonUnwrapped Prov prov) {
    }

    @Override
    public String nome() {
        return "executivosp";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        int anoAtual = ctx.hoje().getYear();
        // Início da cobertura do mandato vigente (2023)
        int anoInicio = 2023;

        // Metadados institucionais da SP-Agendas
        coleta.obter(URL_AGENDAS + "/Home/GetOrgaosPorNaturezaJuridica?naturezaJuridicaId=5a077308-f1b9-4b54-8bb9-440b86802a4e",
                "orgaos-secretarias.json", "application/json", Politica.SEMANAL);
        coleta.obter(URL_AGENDAS + "/Home/GetCargosPorOrgao?orgaoId=" + ORGAO_GABINETE_GOVERNADOR,
                "cargos-gabinete.json", "application/json", Politica.SEMANAL);

        // Atos normativos e decretos do Estado de São Paulo
        coleta.obter(URL_LEGISLACAO, "legislacao_normas.zip", "application/zip", Politica.SEMANAL);

        // Exportação de compromissos por ano na plataforma SP-Agendas (formato XLSX com parâmetros em dd/MM/yyyy)
        for (int ano = anoInicio; ano <= anoAtual; ano++) {
            Politica politica = Politica.porAno(ano, ctx.hoje());
            String inicioAno = "01/01/" + ano;
            String fimAno = (ano == anoAtual)
                    ? String.format("%02d/%02d/%04d", ctx.hoje().getDayOfMonth(), ctx.hoje().getMonthValue(), ctx.hoje().getYear())
                    : "31/12/" + ano;
            String url = URL_AGENDAS + "/Home/Exportar?dataInicial="
                    + URLEncoder.encode(inicioAno, StandardCharsets.UTF_8)
                    + "&dataFinal="
                    + URLEncoder.encode(fimAno, StandardCharsets.UTF_8);
            coleta.obter(url, "agendas/compromissos-" + ano + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", politica);
        }

        coleta.periodo(new Meta.Periodo(LocalDate.of(anoInicio, 1, 1), ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path pastaRaw = ctx.raw().resolve("executivosp");
        Path pastaSaida = ctx.normalizado().resolve("executivosp");
        Files.createDirectories(pastaSaida);

        List<Compromisso> compromissos = new ArrayList<>();
        List<Agregado> despesas = new ArrayList<>();
        List<Ato> atos = new ArrayList<>();

        if (Files.exists(pastaRaw)) {
            // Processa compromissos da agenda oficial
            int anoAtual = ctx.hoje().getYear();
            for (int ano = 2023; ano <= anoAtual; ano++) {
                Coleta.Arquivo arqXlsx = Coleta.metadados(ctx, "executivosp", "agendas/compromissos-" + ano + ".xlsx");
                if (arqXlsx != null && Files.exists(Coleta.caminho(ctx, arqXlsx))) {
                    processarArquivoAgenda(Coleta.caminho(ctx, arqXlsx), arqXlsx, compromissos);
                } else {
                    Coleta.Arquivo arqCsv = Coleta.metadados(ctx, "executivosp", "agendas/compromissos-" + ano + ".csv");
                    if (arqCsv != null && Files.exists(Coleta.caminho(ctx, arqCsv))) {
                        processarArquivoAgenda(Coleta.caminho(ctx, arqCsv), arqCsv, compromissos);
                    }
                }
            }

            // Processa despesas do Portal da Transparência se existirem
            Coleta.Arquivo arqDesp = Coleta.metadados(ctx, "executivosp", "despesas.json");
            if (arqDesp != null && Files.exists(Coleta.caminho(ctx, arqDesp))) {
                JsonNode lista = Json.ler(Coleta.caminho(ctx, arqDesp));
                Prov prov = Prov.de(FONTE_TRANSPARENCIA, URL_TRANSPARENCIA + "/Despesas", arqDesp);
                for (JsonNode n : lista) {
                    despesas.add(new Agregado(UF, n.path("ano").asInt(), n.path("mes").asInt(),
                            Json.texto(n, "orgao"), Json.texto(n, "categoria"),
                            n.path("valor").decimalValue(), n.path("quantidade").asInt(), prov));
                }
            }

            // Processa atos normativos estaduais se existirem
            Coleta.Arquivo arqNormas = Coleta.metadados(ctx, "executivosp", "legislacao_normas.zip");
            if (arqNormas != null && Files.exists(Coleta.caminho(ctx, arqNormas))) {
                processarNormasEstaduais(Coleta.caminho(ctx, arqNormas), arqNormas, atos);
            }
        }

        Map<String, Compromisso> unicos = new java.util.LinkedHashMap<>();
        for (Compromisso c : compromissos) {
            String chave = c.data() + "|" + c.hora() + "|" + c.textoOficial() + "|" + (c.local() == null ? "" : c.local());
            unicos.putIfAbsent(chave, c);
        }
        List<Compromisso> deduplicados = new ArrayList<>(unicos.values());
        deduplicados.sort(Comparator.comparing(Compromisso::data).thenComparing(Compromisso::hora));
        Json.gravar(pastaSaida.resolve("compromissos.json"), Json.SAIDA.valueToTree(deduplicados));
        Json.gravar(pastaSaida.resolve("despesas.json"), Json.SAIDA.valueToTree(despesas));
        Json.gravar(pastaSaida.resolve("atos.json"), Json.SAIDA.valueToTree(atos));

        System.out.println("executivosp: " + deduplicados.size() + " compromissos, " + despesas.size() + " agregados de despesa e " + atos.size() + " atos normalizados");
    }

    public static void processarArquivoAgenda(Path caminho, Coleta.Arquivo arq, List<Compromisso> destino) throws Exception {
        byte[] cabecalho = new byte[4];
        try (InputStream in = Files.newInputStream(caminho)) {
            int lidos = in.read(cabecalho);
            if (lidos >= 2 && cabecalho[0] == 'P' && cabecalho[1] == 'K') {
                processarXlsxAgenda(caminho, arq, destino);
                return;
            }
        }
        processarCsvAgenda(caminho, arq, destino);
    }

    public static void processarXlsxAgenda(Path caminho, Coleta.Arquivo arq, List<Compromisso> destino) throws Exception {
        List<String> sharedStrings = new ArrayList<>();
        Prov prov = Prov.de(FONTE_AGENDAS, URL_AGENDAS, arq);

        try (ZipFile zip = new ZipFile(caminho.toFile())) {
            ZipEntry sstEntry = zip.getEntry("xl/sharedStrings.xml");
            if (sstEntry != null) {
                XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
                try (InputStream in = zip.getInputStream(sstEntry)) {
                    XMLStreamReader reader = factory.createXMLStreamReader(in);
                    StringBuilder text = new StringBuilder();
                    boolean inT = false;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            if ("t".equals(reader.getLocalName())) {
                                inT = true;
                            } else if ("si".equals(reader.getLocalName())) {
                                text.setLength(0);
                            }
                        } else if (event == XMLStreamConstants.CHARACTERS && inT) {
                            text.append(reader.getText());
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            if ("t".equals(reader.getLocalName())) {
                                inT = false;
                            } else if ("si".equals(reader.getLocalName())) {
                                sharedStrings.add(text.toString());
                            }
                        }
                    }
                }
            }

            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry != null) {
                XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
                try (InputStream in = zip.getInputStream(sheetEntry)) {
                    XMLStreamReader reader = factory.createXMLStreamReader(in);
                    Map<Integer, String> currentRow = null;
                    String currentCellType = null;
                    int currentCellCol = -1;
                    StringBuilder cellValue = new StringBuilder();
                    boolean inV = false;
                    boolean isHeader = true;

                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            String name = reader.getLocalName();
                            if ("row".equals(name)) {
                                currentRow = new HashMap<>();
                            } else if ("c".equals(name)) {
                                String ref = reader.getAttributeValue(null, "r");
                                currentCellCol = colunaParaIndice(ref);
                                currentCellType = reader.getAttributeValue(null, "t");
                                cellValue.setLength(0);
                            } else if ("v".equals(name)) {
                                inV = true;
                            }
                        } else if (event == XMLStreamConstants.CHARACTERS && inV) {
                            cellValue.append(reader.getText());
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            String name = reader.getLocalName();
                            if ("v".equals(name)) {
                                inV = false;
                            } else if ("c".equals(name)) {
                                String val = cellValue.toString();
                                if ("s".equals(currentCellType) && !val.isEmpty()) {
                                    try {
                                        int idx = Integer.parseInt(val);
                                        val = idx < sharedStrings.size() ? sharedStrings.get(idx) : val;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                                if (currentRow != null && currentCellCol >= 0) {
                                    currentRow.put(currentCellCol, val);
                                }
                            } else if ("row".equals(name)) {
                                if (currentRow != null) {
                                    if (isHeader) {
                                        isHeader = false;
                                    } else {
                                        processarLinhaXlsx(currentRow, prov, destino);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void processarLinhaXlsx(Map<Integer, String> r, Prov prov, List<Compromisso> destino) {
        String autoridade = r.getOrDefault(0, "").strip();
        String orgao = r.getOrDefault(1, "").strip();
        String tipoObr = r.getOrDefault(3, "").strip();
        String tipoNaoObr = r.getOrDefault(4, "").strip();
        String assunto = r.getOrDefault(5, "").strip();
        if (assunto.isBlank()) {
            assunto = (tipoObr + " " + tipoNaoObr).strip();
        }
        String dataRaw = r.getOrDefault(6, "").strip();
        String horaRaw = r.getOrDefault(7, "").strip();
        String localRaw = r.getOrDefault(8, "").strip();
        String partRaw = r.getOrDefault(12, "").strip();

        boolean isGabinete = orgao.toUpperCase().contains("GABINETE DO GOVERNADOR");
        boolean isGovernador = assunto.toUpperCase().contains("GOVERNADOR") || autoridade.toUpperCase().contains("GOVERNADOR");
        if (!isGabinete && !isGovernador) {
            return;
        }

        String dataIso = formatarDataIso(dataRaw);
        if (dataIso == null) {
            return;
        }
        String horaFormatada = formatarHora(horaRaw);
        String tituloSanitizado = Guarda.semCpf(assunto);
        String localSanitizado = localRaw.isBlank() ? null : Guarda.semCpf(localRaw);
        List<String> participantes = new ArrayList<>();
        if (!partRaw.isBlank()) {
            for (String p : partRaw.split(",")) {
                String part = p.strip();
                if (!part.isBlank()) {
                    participantes.add(Guarda.semCpf(part));
                }
            }
        }

        destino.add(new Compromisso(UF, dataIso, horaFormatada, tituloSanitizado, localSanitizado,
                participantes, null, prov));
    }

    public static void processarCsvAgenda(Path caminho, Coleta.Arquivo arq, List<Compromisso> destino) throws Exception {
        List<String> linhas;
        try {
            linhas = Files.readAllLines(caminho, StandardCharsets.UTF_8);
        } catch (Exception e) {
            linhas = Files.readAllLines(caminho, StandardCharsets.ISO_8859_1);
        }
        if (linhas.isEmpty()) {
            return;
        }
        Prov prov = Prov.de(FONTE_AGENDAS, URL_AGENDAS, arq);
        // Header: Data, Horário, Título, Local, Participantes
        for (int i = 1; i < linhas.size(); i++) {
            String linha = linhas.get(i).strip();
            if (linha.isBlank()) {
                continue;
            }
            String[] partes = linha.split(";", -1);
            if (partes.length < 3) {
                continue;
            }
            String dataRaw = partes[0].strip();
            String horaRaw = partes[1].strip();
            String tituloRaw = partes[2].strip();
            String localRaw = partes.length > 3 ? partes[3].strip() : null;
            String partRaw = partes.length > 4 ? partes[4].strip() : null;

            String dataIso = formatarDataIso(dataRaw);
            if (dataIso == null) {
                continue;
            }
            String horaFormatada = formatarHora(horaRaw);
            String tituloSanitizado = Guarda.semCpf(tituloRaw);
            String localSanitizado = localRaw != null && !localRaw.isBlank() ? Guarda.semCpf(localRaw) : null;
            List<String> participantes = new ArrayList<>();
            if (partRaw != null && !partRaw.isBlank()) {
                for (String p : partRaw.split(",")) {
                    String part = p.strip();
                    if (!part.isBlank()) {
                        participantes.add(Guarda.semCpf(part));
                    }
                }
            }

            destino.add(new Compromisso(UF, dataIso, horaFormatada, tituloSanitizado, localSanitizado,
                    participantes, null, prov));
        }
    }

    static int colunaParaIndice(String ref) {
        if (ref == null) {
            return -1;
        }
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                col = col * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return col - 1;
    }

    public static String formatarDataIso(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        texto = texto.strip();
        if (texto.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return texto;
        }
        if (texto.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            String[] p = texto.split("/");
            return p[2] + "-" + p[1] + "-" + p[0];
        }
        return null;
    }

    public static String formatarHora(String texto) {
        if (texto == null || texto.isBlank()) {
            return "09:00";
        }
        Matcher m = Pattern.compile("(\\d{1,2})[h:](\\d{2})").matcher(texto.strip());
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            return String.format("%02d:%02d", h, min);
        }
        return "09:00";
    }

    public static void processarNormasEstaduais(Path caminhoZip, Coleta.Arquivo arq, List<Ato> destino) throws Exception {
        try (ZipFile zip = new ZipFile(caminhoZip.toFile())) {
            ZipEntry entry = zip.getEntry("legislacao_normas.xml");
            if (entry == null) {
                return;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                processarNormasEstaduaisXml(in, arq, destino);
            }
        }
        destino.sort(Comparator.comparing(Ato::data).reversed().thenComparing(Ato::idFonte));
    }

    public static void processarNormasEstaduaisXml(InputStream in, Coleta.Arquivo arq, List<Ato> destino) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XMLStreamReader reader = factory.createXMLStreamReader(in, "UTF-8");
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "LegislacaoNorma".equals(reader.getLocalName())) {
                String dataStr = null, ementaRaw = null, idNorma = null, numero = null, idTipo = null, promulg = null, urlFicha = null;
                while (reader.hasNext()) {
                    int sub = reader.next();
                    if (sub == XMLStreamConstants.START_ELEMENT) {
                        String tag = reader.getLocalName();
                        String text = reader.getElementText();
                        if ("Data".equals(tag)) dataStr = text;
                        else if ("Ementa".equals(tag)) ementaRaw = text;
                        else if ("IdNorma".equals(tag)) idNorma = text;
                        else if ("Numero".equals(tag)) numero = text;
                        else if ("IdTipo".equals(tag)) idTipo = text;
                        else if ("Promulg".equals(tag)) promulg = text;
                        else if ("URLFicha".equals(tag)) urlFicha = text;
                    } else if (sub == XMLStreamConstants.END_ELEMENT && "LegislacaoNorma".equals(reader.getLocalName())) {
                        break;
                    }
                }

                if (dataStr != null && dataStr.length() >= 10 && "E".equalsIgnoreCase(promulg)) {
                    String data = dataStr.substring(0, 10);
                    if (data.compareTo("2022-01-01") >= 0) {
                        String tipo = switch (idTipo != null ? idTipo.strip() : "") {
                            case "3" -> "DEC";
                            case "9" -> "LEI";
                            case "2" -> "LCP";
                            default -> null;
                        };
                        if (tipo != null) {
                            String rotuloTipo = switch (tipo) {
                                case "DEC" -> "Decreto nº ";
                                case "LEI" -> "Lei nº ";
                                case "LCP" -> "Lei Complementar nº ";
                                default -> "Norma nº ";
                            };
                            String numLimpo = numero != null ? numero.strip() : null;
                            String nome = numLimpo != null ? rotuloTipo + numLimpo : rotuloTipo + (idNorma != null ? idNorma.strip() : "");
                            String idFonte = "sp-" + tipo.toLowerCase() + "-" + (numLimpo != null ? numLimpo : idNorma);
                            String ementa = Guarda.semCpf(ementaRaw);
                            String urlOrigem = urlFicha != null && !urlFicha.isBlank() ? urlFicha.strip() : URL_LEGISLACAO;
                            Prov prov = Prov.de(FONTE_LEGISLACAO, urlOrigem, arq);
                            destino.add(new Ato(UF, idFonte, tipo, numLimpo, data, nome, ementa, prov));
                        }
                    }
                }
            }
        }
    }
}
