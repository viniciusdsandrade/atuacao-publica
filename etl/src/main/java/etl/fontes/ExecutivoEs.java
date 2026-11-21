package etl.fontes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.fontes.ExecutivoSp.Compromisso;

/**
 * Poder Executivo do Estado do Espírito Santo (ADR-0077): agenda oficial do governador, no calendário público que o
 * portal do governo publica como "Agenda do Governador". Decretos e despesas não têm fonte coletável validada.
 */
public final class ExecutivoEs implements Fonte {

    public static final String FONTE_AGENDA = "es-agenda-governador";
    public static final String URL_AGENDA =
            "https://calendar.google.com/calendar/ical/4oc9k18uk3p5ffmpm621hm33ig%40group.calendar.google.com/public/basic.ics";
    public static final String UF = "ES";

    /** Início da cobertura (ADR-0009). */
    static final LocalDate INICIO = LocalDate.of(2022, 1, 1);

    private static final Pattern INICIO_EVENTO = Pattern.compile("^DTSTART(;[^:]*)?:(\\d{8})(T(\\d{6})(Z)?)?$");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String nome() {
        return "executivoes";
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        coleta.obter(URL_AGENDA, "agenda/governador.ics", "text/calendar", Politica.SEMPRE);
        coleta.periodo(new Meta.Periodo(INICIO, ctx.hoje()));
    }

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Path saida = ctx.normalizado().resolve(nome());
        Files.createDirectories(saida);
        List<Compromisso> compromissos = new ArrayList<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "agenda/governador.ics");
        if (arq != null && Files.exists(Coleta.caminho(ctx, arq))) {
            String ics;
            try (var in = Json.abrir(Coleta.caminho(ctx, arq))) {
                ics = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            compromissos.addAll(compromissos(ics, Prov.de(FONTE_AGENDA, URL_AGENDA, arq)));
        }
        Map<String, Compromisso> unicos = new LinkedHashMap<>();
        for (Compromisso c : compromissos) {
            unicos.putIfAbsent(c.data() + "|" + c.hora() + "|" + c.textoOficial(), c);
        }
        List<Compromisso> agenda = new ArrayList<>(unicos.values());
        agenda.sort(Comparator.comparing(Compromisso::data).thenComparing(c -> c.hora() == null ? "" : c.hora()));
        Json.gravar(saida.resolve("compromissos.json"), Json.SAIDA.valueToTree(agenda));
        Json.gravar(saida.resolve("atos.json"), Json.SAIDA.createArrayNode());
        Json.gravar(saida.resolve("despesas.json"), Json.SAIDA.createArrayNode());
        System.out.println("executivoes: " + agenda.size() + " compromissos normalizados");
    }

    /**
     * Compromissos do iCalendar a partir do início da cobertura. A hora em UTC (sufixo Z) passa ao fuso que o calendário declara.
     * Evento de dia inteiro fica sem hora. "Feriado" não é compromisso.
     */
    static List<Compromisso> compromissos(String ics, Prov prov) {
        // O fuso vem do próprio calendário (X-WR-TIMEZONE). Sem ele, a hora em UTC não é convertida e fica de fora.
        ZoneId fuso = null;
        Matcher declarado = Pattern.compile("(?m)^X-WR-TIMEZONE:(\\S+)$").matcher(ics.replace("\r", ""));
        if (declarado.find()) {
            fuso = ZoneId.of(declarado.group(1).strip());
        }
        List<Compromisso> lista = new ArrayList<>();
        // Linhas dobradas (RFC 5545): a continuação começa com espaço ou tabulação.
        String[] linhas = ics.replace("\r\n", "\n").replaceAll("\n[ \t]", "").split("\n");
        Map<String, String> evento = null;
        for (String linha : linhas) {
            if (linha.equals("BEGIN:VEVENT")) {
                evento = new LinkedHashMap<>();
            } else if (linha.equals("END:VEVENT") && evento != null) {
                Compromisso c = compromisso(evento, fuso, prov);
                if (c != null) {
                    lista.add(c);
                }
                evento = null;
            } else if (evento != null) {
                int dois = linha.indexOf(':');
                if (dois > 0) {
                    String chave = linha.substring(0, dois);
                    String nome = chave.contains(";") ? chave.substring(0, chave.indexOf(';')) : chave;
                    evento.put(nome, nome.equals("DTSTART") ? linha : linha.substring(dois + 1));
                }
            }
        }
        return lista;
    }

    private static Compromisso compromisso(Map<String, String> evento, ZoneId fuso, Prov prov) {
        if ("CANCELLED".equals(evento.get("STATUS"))) {
            return null;
        }
        String resumo = texto(evento.get("SUMMARY"));
        Matcher m = INICIO_EVENTO.matcher(evento.getOrDefault("DTSTART", ""));
        if (resumo == null || !m.matches() || resumo.toLowerCase().startsWith("feriado")) {
            return null;
        }
        String data;
        String hora = null;
        if (m.group(3) == null) {
            data = LocalDate.parse(m.group(2), DateTimeFormatter.BASIC_ISO_DATE).toString();
        } else {
            LocalDateTime local = LocalDateTime.parse(m.group(2) + m.group(4), DATA_HORA);
            boolean utc = m.group(5) != null;
            if (utc && fuso != null) {
                local = local.atOffset(ZoneOffset.UTC).atZoneSameInstant(fuso).toLocalDateTime();
            }
            data = local.toLocalDate().toString();
            hora = utc && fuso == null ? null : String.format("%02d:%02d", local.getHour(), local.getMinute());
        }
        if (data.compareTo(INICIO.toString()) < 0) {
            return null;
        }
        String local = texto(evento.get("LOCATION"));
        return new Compromisso(UF, data, hora, Guarda.sanitizar(resumo), local == null ? null : Guarda.sanitizar(local),
                List.of(), null, prov);
    }

    /** Valor de propriedade sem os escapes do iCalendar ("\,", "\;", "\n") e com os espaços juntos. */
    static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
                .replaceAll("\\s+", " ").strip();
        return t.isEmpty() ? null : t;
    }
}
