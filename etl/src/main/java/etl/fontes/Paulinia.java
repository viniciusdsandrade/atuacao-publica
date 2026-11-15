package etl.fontes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etl.Coleta;
import etl.Coleta.Politica;
import etl.Contexto;
import etl.Fonte;
import etl.Guarda;
import etl.Json;
import etl.Meta;
import etl.Registros;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Câmara Municipal de Paulínia (ADR-0066). Uma fonte: o portal da Câmara, em quatro páginas.
 * - vereadores em exercício, com partido e o período da legislatura;
 * - legislaturas, com o nome civil de cada vereador (só para o vínculo com o TSE);
 * - Mesa Diretora e comissões, com o cargo de cada membro.
 *
 * Regras que moldam a coleta (docs/FONTES.md):
 * - o sistema legislativo da Câmara (SISCAM), que tem proposituras, votações e frequência, não é coletado:
 *   o robots.txt dele só libera buscadores nomeados;
 * - as páginas individuais dos vereadores trazem biografia e não são coletadas (ADR-0054);
 * - as páginas são texto editado à mão, sem contrato: a leitura é por linhas de texto, e linha que não cabe
 *   no formato esperado fica fora, sem inventar valor;
 * - telefone e e-mail saem antes de a página ir para o cache: só as linhas de texto são guardadas.
 */
public final class Paulinia implements Fonte {

    public static final String FONTE_PORTAL = "paulinia-camara-portal";

    public static final String IBGE = "3536505";
    public static final String UF = "SP";

    public static final String PORTAL = "https://www.camarapaulinia.sp.gov.br";
    static final String URL_VEREADORES = PORTAL + "/vereadores/";
    static final String URL_LEGISLATURAS = PORTAL + "/legislaturas/";
    static final String URL_MESA = PORTAL + "/mesa-diretora/";
    static final String URL_COMISSOES = PORTAL + "/comissoes/";

    private static final String ARQ_VEREADORES = "portal/vereadores.json";
    private static final String ARQ_LEGISLATURAS = "portal/legislaturas.json";
    private static final String ARQ_MESA = "portal/mesa.json";
    private static final String ARQ_COMISSOES = "portal/comissoes.json";

    private static final String HTML = "text/html";
    /** Como a página chama o órgão. */
    private static final String MESA = "Mesa Diretora";
    /** Servidor pequeno: no mínimo 1 segundo entre chamadas. */
    private static final Duration INTERVALO = Duration.ofSeconds(1);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** O maior nome de comissão tem cerca de 110 caracteres. Linha maior que isto é texto corrido. */
    private static final int LINHA_MAXIMA = 200;

    /** "15ª Legislatura – 01/01/2025 a 31/12/2028", com ou sem o traço. */
    private static final Pattern LEGISLATURA = Pattern.compile(
            "^(\\d{1,3})ª\\s+Legislatura\\D{0,5}(\\d{2}/\\d{2}/\\d{4})\\s+[aA]\\s+(\\d{2}/\\d{2}/\\d{4})$");
    /** "Nome civil – Nome parlamentar (Partido)", com "– Suplente" opcional no fim. */
    private static final Pattern MEMBRO = Pattern.compile("^(.+?)\\s*\\(([^()]{1,40})\\)\\s*(?:[–-]\\s*(Suplente))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAPEL = Pattern.compile(
            "^(Presidente|Vice-Presidente|(\\dº\\s+)?Secret[aá]ri[oa]|Membro|Relator|Relatora|Suplente|Tesoureiro|Tesoureira)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARADOR_DE_NOMES = Pattern.compile("\\s+[–-]\\s+");
    /** Linha com oito dígitos ou mais é telefone, e não nome nem partido. */
    private static final Pattern MUITOS_DIGITOS = Pattern.compile("(\\D*\\d){8,}");

    private static final Pattern BLOCO_FORA = Pattern.compile(
            "(?is)<(script|style|nav|header|footer|form|select|noscript)\\b.*?</\\1>");
    private static final Pattern PRINCIPAL = Pattern.compile("(?is)<main\\b.*?</main>");
    private static final Pattern ARTIGO = Pattern.compile("(?is)<article\\b.*?</article>");
    private static final Pattern QUEBRA = Pattern.compile("(?i)<(br|/p|/div|/li|/tr|/td|/h[1-6])\\b[^>]*>");
    private static final Pattern MARCACAO = Pattern.compile("<[^>]+>");

    /** Texto de uma página do portal, em linhas, sem telefone nem e-mail. É o que vai para o cache. */
    public record Pagina(List<String> linhas) {
    }

    public record Legislatura(int numero, LocalDate inicio, LocalDate fim, List<Membro> membros) {
    }

    /** Linha da página de legislaturas: nome civil, nomes parlamentares que a página cita e partido. */
    public record Membro(String nomeCivil, List<String> nomesParlamentares, String partido, boolean suplente) {
    }

    /** Vereador da página "Vereadores em Exercício". */
    public record EmExercicio(String nome, String partido) {
    }

    /** O que a página de vereadores diz: a legislatura do cabeçalho e os vereadores em exercício. */
    public record Composicao(Integer legislatura, LocalDate inicio, LocalDate fim, String cabecalho, List<EmExercicio> vereadores) {
    }

    /** Cargo na Mesa (órgão nulo) ou numa comissão. */
    public record Cargo(String orgao, String nome, String papel) {
    }

    @Override
    public String nome() {
        return "paulinia";
    }

    // ---------------------------------------------------------------- fetch

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        ctx.http().respeitar(java.net.URI.create(PORTAL).getHost(), INTERVALO);
        // Antes e depois de renovar a lista: quem sai dela fica no histórico do cache (ADR-0059).
        registrarHistorico(ctx);
        guardar(coleta, URL_VEREADORES, ARQ_VEREADORES, Politica.SEMPRE);
        guardar(coleta, URL_LEGISLATURAS, ARQ_LEGISLATURAS, Politica.SEMANAL);
        guardar(coleta, URL_MESA, ARQ_MESA, Politica.SEMANAL);
        guardar(coleta, URL_COMISSOES, ARQ_COMISSOES, Politica.SEMANAL);
        registrarHistorico(ctx);
        Composicao composicao = composicao(ctx);
        coleta.contar("vereadores_em_exercicio", composicao == null ? 0 : composicao.vereadores().size());
        if (composicao != null && composicao.inicio() != null) {
            coleta.periodo(new Meta.Periodo(composicao.inicio(), ctx.hoje()));
        }
    }

    /** Só as linhas de texto vão para o cache: telefone, e-mail e marcação ficam fora. */
    private static void guardar(Coleta coleta, String url, String caminho, Politica politica) throws IOException, InterruptedException {
        coleta.obterDerivado(url, caminho, HTML, politica,
                (baixado, destino) -> Json.gravar(destino, new Pagina(linhas(Texto.lerArquivo(baixado)))));
    }

    private void registrarHistorico(Contexto ctx) {
        Composicao composicao = composicao(ctx);
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), ARQ_VEREADORES);
        if (composicao == null || composicao.vereadores().isEmpty() || arq == null) {
            return;
        }
        HistoricoDeputados.registrar(ctx, nome(), retratos(composicao, legislaturas(ctx)), Prov.de(FONTE_PORTAL, URL_VEREADORES, arq));
    }

    // ---------------------------------------------------------------- normalize

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Coleta.Arquivo arqVereadores = Coleta.metadados(ctx, nome(), ARQ_VEREADORES);
        Composicao composicao = composicao(ctx);
        if (!existe(ctx, arqVereadores) || composicao == null) {
            System.out.println("paulinia: sem dados coletados para normalizar");
            return;
        }
        if (composicao.vereadores().isEmpty()) {
            // Página fora do formato esperado: nada é gravado, e a publicação fica com a cópia anterior (ADR-0006).
            System.out.println("paulinia: a página de vereadores não está no formato esperado; nada foi normalizado");
            return;
        }
        Prov provVereadores = Prov.de(FONTE_PORTAL, URL_VEREADORES, arqVereadores);
        List<Legislatura> legislaturas = legislaturas(ctx);
        List<HistoricoDeputados.Entrada> entradas = HistoricoDeputados.mesclar(ctx, nome(), retratos(composicao, legislaturas), provVereadores);
        Map<String, List<OrgaoOuFrente>> orgaos = orgaos(ctx, composicao);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivis = new TreeMap<>();
        Path saida = ctx.normalizado().resolve(nome());
        int semNomeCivil = 0;
        for (HistoricoDeputados.Entrada entrada : entradas) {
            Retrato retrato = Retrato.de(entrada.extra());
            if (entrada.id() == null || entrada.nome() == null || retrato == null) {
                continue;
            }
            boolean emExercicio = entrada.emExercicio();
            String situacao = entrada.situacao("Em exercício");
            Prov prov = entrada.provOu(provVereadores);
            List<LinkOficial> links = List.of(new LinkOficial("Vereadores em exercício no portal da Câmara Municipal de Paulínia",
                    URL_VEREADORES, prov));
            pessoas.add(new Pessoa(nome(), entrada.id(), entrada.nome(), "vereador", "municipal", IBGE, UF, entrada.partido(),
                    null, situacao, emExercicio, links));
            if (retrato.nomeCivil() != null) {
                nomesCivis.put(entrada.id(), retrato.nomeCivil());
            } else {
                semNomeCivil++;
            }

            // O portal não publica a data de posse de cada vereador. O início é o da legislatura para quem a página de
            // legislaturas lista sem a marca de suplente. Para os demais fica vazio.
            LocalDate inicio = retrato.listado() && !retrato.suplente() ? retrato.inicio() : null;
            LocalDate fim = emExercicio ? retrato.fim() : null;
            List<Mandato> mandatos = List.of(new Mandato("vereador", "municipal", IBGE, UF, inicio, fim, situacao,
                    retrato.suplente() ? "Suplente" : null, retrato.legislatura(), null, retrato.cabecalho(), prov));
            List<OrgaoOuFrente> doVereador = emExercicio ? orgaos.getOrDefault(entrada.id(), List.of()) : List.of();

            ObjectNode blocos = Json.SAIDA.createObjectNode();
            blocos.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocos.set("orgaos_e_frentes", Json.SAIDA.valueToTree(doVereador));
            Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>(Registros.fontesDosBlocos(blocos));
            blocos.set("fontes", Json.SAIDA.valueToTree(fontes));
            Json.gravar(saida.resolve("blocos").resolve(entrada.id() + ".json"), blocos);
        }
        pessoas.sort(Comparator.comparing(p -> Texto.normalizarNome(p.nome())));
        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        // Nome civil que a própria Câmara publica: só o vínculo com o TSE o lê, e ele não sai no perfil.
        Json.gravar(saida.resolve("nomes-civis.json"), nomesCivis);
        long emExercicio = pessoas.stream().filter(Pessoa::emExercicio).count();
        long cargos = orgaos.values().stream().mapToInt(List::size).sum();
        System.out.println("paulinia: " + pessoas.size() + " vereadores normalizados (" + emExercicio + " em exercício), " + cargos
                + " cargo(s) na Mesa e em comissões; " + semNomeCivil + " sem nome civil na página de legislaturas");
    }

    /** Cargos da Mesa e das comissões por identificador do vereador. Nome que não é de vereador em exercício fica fora. */
    private Map<String, List<OrgaoOuFrente>> orgaos(Contexto ctx, Composicao composicao) {
        Map<String, String> idPorNome = new TreeMap<>();
        for (EmExercicio v : composicao.vereadores()) {
            idPorNome.put(Texto.normalizarNome(v.nome()), slug(v.nome()));
        }
        Map<String, List<OrgaoOuFrente>> porId = new LinkedHashMap<>();
        Set<String> semVereador = new HashSet<>();
        Coleta.Arquivo arqMesa = Coleta.metadados(ctx, nome(), ARQ_MESA);
        if (existe(ctx, arqMesa)) {
            Prov prov = Prov.de(FONTE_PORTAL, URL_MESA, arqMesa);
            for (Cargo c : mesa(pagina(ctx, arqMesa))) {
                String id = idPorNome.get(Texto.normalizarNome(c.nome()));
                if (id == null) {
                    semVereador.add(c.nome());
                    continue;
                }
                // Sem data: a página mostra a composição atual e não diz desde quando.
                porId.computeIfAbsent(id, k -> new ArrayList<>()).add(new OrgaoOuFrente("orgao", MESA, null, c.papel(), null, null, prov));
            }
        }
        Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, nome(), ARQ_COMISSOES);
        if (existe(ctx, arqComissoes)) {
            Prov prov = Prov.de(FONTE_PORTAL, URL_COMISSOES, arqComissoes);
            for (Cargo c : comissoes(pagina(ctx, arqComissoes))) {
                String id = idPorNome.get(Texto.normalizarNome(c.nome()));
                if (id == null) {
                    semVereador.add(c.nome());
                    continue;
                }
                porId.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new OrgaoOuFrente("comissao", c.orgao(), null, c.papel(), null, null, prov));
            }
        }
        if (!semVereador.isEmpty()) {
            System.out.println("paulinia: nome(s) na Mesa ou em comissão sem vereador em exercício: " + semVereador);
        }
        return porId;
    }

    // ---------------------------------------------------------------- leitura das páginas

    /**
     * Texto do miolo da página, uma linha por parágrafo, item de lista ou quebra. Linha com e-mail, telefone ou muitos
     * dígitos sai inteira: nome e partido não têm isso.
     */
    public static List<String> linhas(String html) {
        if (html == null) {
            return List.of();
        }
        Matcher principal = PRINCIPAL.matcher(html);
        String miolo;
        if (principal.find()) {
            miolo = principal.group();
        } else {
            Matcher artigo = ARTIGO.matcher(html);
            miolo = artigo.find() ? artigo.group() : html;
        }
        miolo = BLOCO_FORA.matcher(miolo).replaceAll(" ");
        miolo = QUEBRA.matcher(miolo).replaceAll("\n");
        miolo = MARCACAO.matcher(miolo).replaceAll(" ");
        List<String> linhas = new ArrayList<>();
        for (String bruta : miolo.split("\n")) {
            // As entidades são trocadas linha a linha: a troca junta os espaços, e juntaria também as linhas.
            String semEntidades = Texto.semEntidades(bruta);
            String linha = semEntidades == null ? "" : semEntidades.replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
            if (linha.isEmpty()) {
                continue;
            }
            // Texto corrido (o regimento, na página da Mesa) não é lido e não vai para o cache.
            if (linha.length() > LINHA_MAXIMA) {
                continue;
            }
            // O cabeçalho da legislatura tem datas e fica. Fora ele, linha com muitos dígitos é telefone.
            boolean contato = MUITOS_DIGITOS.matcher(linha).find() || !Guarda.violacoes(linha).isEmpty() || linha.contains("@");
            if (LEGISLATURA.matcher(linha).matches() || !contato) {
                linhas.add(linha);
            }
        }
        return linhas;
    }

    /** Página "Vereadores em Exercício": o cabeçalho da legislatura e, depois dele, pares de partido e nome. */
    public static Composicao composicao(List<String> linhas) {
        int cabecalho = -1;
        Matcher legislatura = null;
        for (int i = 0; i < linhas.size(); i++) {
            Matcher m = LEGISLATURA.matcher(linhas.get(i));
            if (m.matches()) {
                cabecalho = i;
                legislatura = m;
                break;
            }
        }
        if (legislatura == null) {
            return new Composicao(null, null, null, null, List.of());
        }
        List<String> resto = linhas.subList(cabecalho + 1, linhas.size());
        List<EmExercicio> vereadores = new ArrayList<>();
        // Par incompleto ou linha longa demais: a página saiu do formato, e nada é lido.
        if (resto.size() % 2 == 0) {
            for (int i = 0; i < resto.size(); i += 2) {
                String partido = resto.get(i);
                String nome = resto.get(i + 1);
                if (partido.length() > 40 || nome.length() > 60) {
                    vereadores.clear();
                    break;
                }
                vereadores.add(new EmExercicio(nome, partido));
            }
        }
        return new Composicao(Integer.valueOf(legislatura.group(1)), LocalDate.parse(legislatura.group(2), DATA_BR),
                LocalDate.parse(legislatura.group(3), DATA_BR), linhas.get(cabecalho), List.copyOf(vereadores));
    }

    /** Página de legislaturas: cada cabeçalho abre uma legislatura, e cada linha com partido entre parênteses é um membro. */
    public static List<Legislatura> legislaturas(List<String> linhas) {
        List<Legislatura> legislaturas = new ArrayList<>();
        List<Membro> membros = null;
        for (String linha : linhas) {
            Matcher cabecalho = LEGISLATURA.matcher(linha);
            if (cabecalho.matches()) {
                membros = new ArrayList<>();
                legislaturas.add(new Legislatura(Integer.parseInt(cabecalho.group(1)), LocalDate.parse(cabecalho.group(2), DATA_BR),
                        LocalDate.parse(cabecalho.group(3), DATA_BR), membros));
                continue;
            }
            Matcher membro = MEMBRO.matcher(linha);
            if (membros == null || !membro.matches()) {
                continue;
            }
            // "Nome civil – Nome parlamentar, apelido" ou "Nome civil, Nome parlamentar": o primeiro é o nome civil.
            String nomes = membro.group(1).strip();
            String[] partes = SEPARADOR_DE_NOMES.split(nomes, 2);
            if (partes.length == 1) {
                partes = nomes.split(",", 2);
            }
            List<String> parlamentares = new ArrayList<>();
            if (partes.length == 2) {
                for (String nome : partes[1].split(",")) {
                    if (!nome.isBlank()) {
                        parlamentares.add(nome.strip());
                    }
                }
            }
            membros.add(new Membro(partes[0].strip(), List.copyOf(parlamentares), membro.group(2).strip(), membro.group(3) != null));
        }
        return legislaturas;
    }

    /** Página da Mesa: trincas de partido, nome e cargo, até o texto do regimento. */
    public static List<Cargo> mesa(List<String> linhas) {
        List<Cargo> cargos = new ArrayList<>();
        for (int i = 0; i + 2 < linhas.size(); i++) {
            if (PAPEL.matcher(linhas.get(i + 2)).matches() && linhas.get(i + 1).length() <= 60) {
                cargos.add(new Cargo(null, linhas.get(i + 1), linhas.get(i + 2)));
                i += 2;
            }
        }
        return cargos;
    }

    /** Página das comissões: o nome da comissão abre o grupo, e cada membro é uma trinca de partido, nome e cargo. */
    public static List<Cargo> comissoes(List<String> linhas) {
        List<Cargo> cargos = new ArrayList<>();
        String comissao = null;
        for (int i = 0; i < linhas.size(); i++) {
            if (i + 2 < linhas.size() && PAPEL.matcher(linhas.get(i + 2)).matches() && linhas.get(i + 1).length() <= 60) {
                if (comissao != null) {
                    cargos.add(new Cargo(comissao, linhas.get(i + 1), linhas.get(i + 2)));
                }
                i += 2;
            } else if (linhas.get(i).toLowerCase(java.util.Locale.ROOT).startsWith("comiss") && linhas.get(i).length() <= 200) {
                comissao = linhas.get(i);
            }
        }
        // O título da página ("Comissões Parlamentares") não é comissão: só vale o nome que tem membro.
        return cargos;
    }

    /**
     * Linha da legislatura que corresponde ao vereador em exercício: nome igual a um dos nomes da linha, ou todas as
     * palavras do nome dentro do nome civil de uma única linha. Sem isso, nulo.
     */
    public static Membro membroDe(String nomeDoVereador, List<Membro> membros) {
        String nome = Texto.normalizarNome(nomeDoVereador);
        if (nome == null || nome.isBlank()) {
            return null;
        }
        for (Membro m : membros) {
            if (nome.equals(Texto.normalizarNome(m.nomeCivil()))) {
                return m;
            }
            for (String parlamentar : m.nomesParlamentares()) {
                if (nome.equals(Texto.normalizarNome(parlamentar))) {
                    return m;
                }
            }
        }
        Set<String> procuradas = Set.of(nome.split("\\s+"));
        Membro unico = null;
        for (Membro m : membros) {
            String civil = Texto.normalizarNome(m.nomeCivil());
            if (civil != null && Set.of(civil.split("\\s+")).containsAll(procuradas)) {
                if (unico != null) {
                    return null;
                }
                unico = m;
            }
        }
        return unico;
    }

    /** Identificador estável a partir do nome parlamentar: "FÁBIO DA VAN" vira "fabio-da-van". */
    public static String slug(String nome) {
        String normalizado = Texto.normalizarNome(nome);
        if (normalizado == null) {
            return null;
        }
        String slug = normalizado.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? null : slug;
    }

    // ---------------------------------------------------------------- histórico

    /**
     * O que o histórico guarda de cada vereador além de nome e partido: legislatura, período, se a página de
     * legislaturas o lista, se o marca como suplente, o cabeçalho da página e o nome civil.
     */
    record Retrato(int legislatura, LocalDate inicio, LocalDate fim, boolean listado, boolean suplente, String cabecalho, String nomeCivil) {

        String texto() {
            return String.join("|", String.valueOf(legislatura), String.valueOf(inicio), String.valueOf(fim), String.valueOf(listado),
                    String.valueOf(suplente), cabecalho == null ? "" : cabecalho.replace("|", " "), nomeCivil == null ? "" : nomeCivil.replace("|", " "));
        }

        static Retrato de(String texto) {
            if (texto == null) {
                return null;
            }
            String[] p = texto.split("\\|", -1);
            if (p.length != 7) {
                return null;
            }
            try {
                return new Retrato(Integer.parseInt(p[0]), LocalDate.parse(p[1]), LocalDate.parse(p[2]), Boolean.parseBoolean(p[3]),
                        Boolean.parseBoolean(p[4]), Texto.vazioParaNulo(p[5]), Texto.vazioParaNulo(p[6]));
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    /** Vereadores em exercício como retratos do histórico. A legislatura do cabeçalho dá o período e os nomes civis. */
    static List<HistoricoDeputados.Snapshot> retratos(Composicao composicao, List<Legislatura> legislaturas) {
        if (composicao == null || composicao.legislatura() == null) {
            return List.of();
        }
        List<Membro> membros = legislaturas.stream().filter(l -> l.numero() == composicao.legislatura())
                .findFirst().map(Legislatura::membros).orElse(List.of());
        List<HistoricoDeputados.Snapshot> retratos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (EmExercicio v : composicao.vereadores()) {
            String id = slug(v.nome());
            // Dois nomes com o mesmo identificador: nenhum entra, para um não levar os dados do outro.
            if (id == null || !vistos.add(id)) {
                retratos.removeIf(r -> r.id().equals(id));
                continue;
            }
            Membro membro = membroDe(v.nome(), membros);
            Retrato retrato = new Retrato(composicao.legislatura(), composicao.inicio(), composicao.fim(), membro != null,
                    membro != null && membro.suplente(), composicao.cabecalho(), membro == null ? null : membro.nomeCivil());
            retratos.add(HistoricoDeputados.Snapshot.de(id, Texto.capitalizarNome(v.nome()), v.partido(), null, retrato.texto(), null));
        }
        return retratos;
    }

    // ---------------------------------------------------------------- cache

    private Composicao composicao(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), ARQ_VEREADORES);
        return existe(ctx, arq) ? composicao(pagina(ctx, arq)) : null;
    }

    private List<Legislatura> legislaturas(Contexto ctx) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), ARQ_LEGISLATURAS);
        return existe(ctx, arq) ? legislaturas(pagina(ctx, arq)) : List.of();
    }

    private static List<String> pagina(Contexto ctx, Coleta.Arquivo arquivo) {
        List<String> linhas = new ArrayList<>();
        for (JsonNode linha : Json.ler(Coleta.caminho(ctx, arquivo)).path("linhas")) {
            linhas.add(linha.stringValue());
        }
        return linhas;
    }

    private static boolean existe(Contexto ctx, Coleta.Arquivo arquivo) {
        return arquivo != null && arquivo.bytes() > 0 && Files.exists(Coleta.caminho(ctx, arquivo));
    }
}
