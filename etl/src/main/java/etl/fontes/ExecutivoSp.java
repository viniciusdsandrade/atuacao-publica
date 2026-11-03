package etl.fontes;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static final String URL_AGENDAS = "https://spagendas.sp.gov.br";
    public static final String URL_TRANSPARENCIA = "https://www.transparencia.sp.gov.br";
    public static final String UF = "SP";

    // UUID do Gabinete do Governador na plataforma SP-Agendas
    public static final String ORGAO_GABINETE_GOVERNADOR = "40e96b34-9c5c-436c-82bd-e79d9f080697";
    // UUID do Cargo de Governador
    public static final String CARGO_GOVERNADOR = "ef4562ac-5970-4647-92ed-bcc38432ed4c";

    public record Compromisso(String ente, String data, String hora, String textoOficial, String local,
                              List<String> participantes, String snapshotUrl, @JsonUnwrapped Prov prov) {
    }

    public record Agregado(String ente, int ano, int mes, String orgao, String categoria, BigDecimal valor,
                           int quantidade, @JsonUnwrapped Prov prov) {
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

        // Exportação de compromissos por ano para o Gabinete do Governador
        for (int ano = anoInicio; ano <= anoAtual; ano++) {
            Politica politica = Politica.porAno(ano, ctx.hoje());
            String inicioAno = ano + "-01-01";
            String fimAno = (ano == anoAtual) ? ctx.hoje().toString() : ano + "-12-31";
            coleta.obter(URL_AGENDAS + "/Home/Exportar?orgaoId=" + ORGAO_GABINETE_GOVERNADOR + "&dataInicial=" + inicioAno + "&dataFinal=" + fimAno,
                    "agendas/compromissos-" + ano + ".csv", "text/csv", politica);
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

        if (Files.exists(pastaRaw)) {
            // Processa compromissos da agenda oficial
            int anoAtual = ctx.hoje().getYear();
            for (int ano = 2023; ano <= anoAtual; ano++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, "executivosp", "agendas/compromissos-" + ano + ".csv");
                if (arq != null && Files.exists(Coleta.caminho(ctx, arq))) {
                    processarCsvAgenda(Coleta.caminho(ctx, arq), arq, compromissos);
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
        }

        compromissos.sort(Comparator.comparing(Compromisso::data).thenComparing(Compromisso::hora));
        Json.gravar(pastaSaida.resolve("compromissos.json"), Json.SAIDA.valueToTree(compromissos));
        Json.gravar(pastaSaida.resolve("despesas.json"), Json.SAIDA.valueToTree(despesas));

        System.out.println("executivosp: " + compromissos.size() + " compromissos e " + despesas.size() + " agregados de despesa normalizados");
    }

    public static void processarCsvAgenda(Path caminho, Coleta.Arquivo arq, List<Compromisso> destino) throws Exception {
        List<String> linhas = Files.readAllLines(caminho);
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
                    participantes.isEmpty() ? null : participantes, null, prov));
        }
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
        texto = texto.strip();
        if (texto.matches("^\\d{1,2}:\\d{2}$")) {
            return texto.length() == 4 ? "0" + texto : texto;
        }
        if (texto.matches("^\\d{1,2}h\\d{2}$")) {
            String[] p = texto.split("h");
            String h = p[0].length() == 1 ? "0" + p[0] : p[0];
            return h + ":" + p[1];
        }
        return "09:00";
    }
}
