package etl.fontes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Json;
import etl.Meta;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;

/**
 * Agenda oficial do Presidente da República, pela página de cada dia no portal do Planalto (ADR-0011).
 * A página do dia existe quando há agenda publicada; sem agenda, o servidor responde 404.
 * Para mandato cujas páginas saíram do ar, a coleta usa a captura do Internet Archive de cada dia.
 * O endereço da agenda de cada mandato vem de contrib/mandatos.yaml (campos agenda e agenda_arquivo).
 */
public final class Agenda implements Fonte {

    public static final String FONTE_ID = "planalto-agenda";
    public static final String ARQUIVO = "https://web.archive.org/web/";
    static final String ENTE = "BR";
    /** Dias dos últimos 14 dias renovam a cada execução; os demais vêm do cache. */
    static final int DIAS_DE_REVISAO = 14;
    private static final DateTimeFormatter COMPACTA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern ITEM = Pattern.compile("class=\"item-compromisso-wrapper\"");
    private static final Pattern INICIO = Pattern.compile("<time class=\"compromisso-inicio\">\\s*(\\d{1,2})h(\\d{2})");
    private static final Pattern TITULO = Pattern.compile("(?s)<h2 class=\"compromisso-titulo[^\"]*\">(.*?)</h2>");
    private static final Pattern LOCAL = Pattern.compile("(?s)<div class=\"compromisso-local\">(.*?)</div>");
    private static final Pattern PARTICIPANTES = Pattern.compile("(?s)<div class=\"compromisso-participantes\">(.*?)</div>");
    private static final Pattern LI = Pattern.compile("(?s)<li>(.*?)</li>");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    /** Mandato com agenda: período coberto, endereço base da agenda e se a coleta vai pelo Internet Archive. */
    public record Regra(LocalDate inicio, LocalDate fim, String base, boolean arquivo) {
    }

    public record Compromisso(String ente, String data, String hora, String textoOficial, String local, List<String> participantes,
                              String snapshotUrl, @JsonUnwrapped Prov prov) {
    }

    @Override
    public String nome() {
        return "agenda";
    }

    /** Regras presidenciais de contrib/mandatos.yaml que informam a agenda, limitadas ao início da cobertura. */
    static List<Regra> regras(Contexto ctx) {
        List<Regra> regras = new ArrayList<>();
        for (JsonNode regra : Json.lerYaml(ctx.contrib().resolve("mandatos.yaml"))) {
            String base = Json.texto(regra, "agenda");
            if (!"presidente".equals(Json.texto(regra, "cargo")) || base == null) {
                continue;
            }
            LocalDate inicio = LocalDate.parse(Json.texto(regra, "inicio"));
            LocalDate fim = LocalDate.parse(Json.texto(regra, "fim"));
            if (inicio.isBefore(Camara.INICIO_DA_COBERTURA)) {
                inicio = Camara.INICIO_DA_COBERTURA;
            }
            if (!inicio.isAfter(ctx.hoje()) && fim.isAfter(inicio)) {
                regras.add(new Regra(inicio, fim, base.replaceAll("/+$", ""), regra.path("agenda_arquivo").asBoolean(false)));
            }
        }
        return regras;
    }

    /** Dias do mandato já passados, do primeiro ao último antes do fim e até hoje. */
    static List<LocalDate> dias(Regra regra, LocalDate hoje) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate ultimo = regra.fim().minusDays(1).isBefore(hoje) ? regra.fim().minusDays(1) : hoje;
        for (LocalDate dia = regra.inicio(); !dia.isAfter(ultimo); dia = dia.plusDays(1)) {
            dias.add(dia);
        }
        return dias;
    }

    static String urlDoDia(Regra regra, LocalDate dia) {
        return regra.base() + "/" + dia;
    }

    /** Captura do Internet Archive mais próxima do meio-dia do próprio dia, sem a barra de ferramentas (id_). */
    static String urlNoArquivo(Regra regra, LocalDate dia) {
        return ARQUIVO + dia.format(COMPACTA) + "120000id_/" + urlDoDia(regra, dia);
    }

    static Politica politica(LocalDate dia, LocalDate hoje) {
        return dia.isBefore(hoje.minusDays(DIAS_DE_REVISAO)) ? Politica.SE_AUSENTE : Politica.SEMPRE;
    }

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        ctx.http().respeitar("web.archive.org", Duration.ofSeconds(4));
        int dias = 0;
        LocalDate primeiro = null;
        for (Regra regra : regras(ctx)) {
            List<String> ids = new ArrayList<>();
            for (LocalDate dia : dias(regra, ctx.hoje())) {
                ids.add(dia.toString());
                dias++;
                primeiro = primeiro == null || dia.isBefore(primeiro) ? dia : primeiro;
            }
            // Os dias vão em paralelo, limitados pela concorrência do cliente HTTP; o intervalo por host serializa o archive.org.
            List<String> falhas = Camara.paraCadaDeputado(ids, id -> {
                LocalDate dia = LocalDate.parse(id);
                String url = regra.arquivo() ? urlNoArquivo(regra, dia) : urlDoDia(regra, dia);
                coleta.obterOuVazio(url, "dias/" + dia + ".html", "text/html", politica(dia, ctx.hoje()));
            });
            for (String falha : falhas) {
                coleta.falhaParcial("dias", falha);
            }
        }
        coleta.contar("dias", dias);
        if (primeiro != null) {
            coleta.periodo(new Meta.Periodo(primeiro, ctx.hoje()));
        }
    }

    @Override
    public void normalize(Contexto ctx) {
        List<Compromisso> compromissos = new ArrayList<>();
        int diasSemAgenda = 0;
        for (Regra regra : regras(ctx)) {
            for (LocalDate dia : dias(regra, ctx.hoje())) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "agenda", "dias/" + dia + ".html");
                if (arq == null) {
                    continue;
                }
                if (arq.bytes() == 0) {
                    diasSemAgenda++;
                    continue;
                }
                String html;
                try (java.io.InputStream entrada = Json.abrir(Coleta.caminho(ctx, arq))) {
                    // A fonte entrega a página com gzip, e o cache guarda como veio (.html.gz).
                    html = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                Prov prov = Prov.de(FONTE_ID, urlDoDia(regra, dia), arq);
                compromissos.addAll(compromissos(html, dia, regra.arquivo() ? arq.url() : null, prov));
            }
        }
        Json.gravar(ctx.normalizado().resolve("agenda").resolve("compromissos.json"), compromissos);
        System.out.println("agenda: " + compromissos.size() + " compromissos normalizados, " + diasSemAgenda + " dia(s) sem agenda publicada");
    }

    /** Os compromissos de uma página de dia, na ordem da página. */
    static List<Compromisso> compromissos(String html, LocalDate dia, String snapshotUrl, Prov prov) {
        List<Compromisso> lista = new ArrayList<>();
        List<Integer> inicios = new ArrayList<>();
        Matcher m = ITEM.matcher(html);
        while (m.find()) {
            inicios.add(m.start());
        }
        for (int i = 0; i < inicios.size(); i++) {
            String trecho = html.substring(inicios.get(i), i + 1 < inicios.size() ? inicios.get(i + 1) : html.length());
            String titulo = primeiro(TITULO, trecho);
            if (titulo == null) {
                continue;
            }
            Matcher hora = INICIO.matcher(trecho);
            String horaTexto = hora.find() ? String.format("%02d:%s", Integer.parseInt(hora.group(1)), hora.group(2)) : null;
            List<String> participantes = new ArrayList<>();
            Matcher bloco = PARTICIPANTES.matcher(trecho);
            if (bloco.find()) {
                Matcher li = LI.matcher(bloco.group(1));
                while (li.find()) {
                    String nome = limpar(li.group(1)).replaceAll(",$", "").strip();
                    if (!nome.isEmpty()) {
                        participantes.add(nome);
                    }
                }
            }
            lista.add(new Compromisso(ENTE, dia.toString(), horaTexto, titulo, primeiro(LOCAL, trecho), participantes, snapshotUrl, prov));
        }
        return lista;
    }

    private static String primeiro(Pattern padrao, String trecho) {
        Matcher m = padrao.matcher(trecho);
        if (!m.find()) {
            return null;
        }
        String texto = limpar(m.group(1));
        return texto.isEmpty() ? null : texto;
    }

    /** Texto sem tags, entidades nem caracteres invisíveis da edição (espaço de largura zero), com espaços simples. */
    static String limpar(String html) {
        String texto = Texto.semEntidades(TAGS.matcher(html).replaceAll(" "));
        return texto == null ? "" : texto.replace("\u200b", "").replace("\ufeff", "").replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
    }
}
