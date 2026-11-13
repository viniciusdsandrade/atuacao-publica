package etl.fontes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
import etl.Registros.Despesa;
import etl.Registros.Evento;
import etl.Registros.FonteUsada;
import etl.Registros.LinkOficial;
import etl.Registros.Mandato;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Pessoa;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import etl.Texto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Câmara Municipal de Caraguatatuba (ADR-0057). Três fontes oficiais, nenhuma com SAPL:
 * - sistema legislativo (SAGL): vereadores das legislaturas cobertas, filiações, mandatos, Mesa, comissões e matérias;
 * - portal da Câmara: composição atual das comissões e lista de presença de cada sessão;
 * - portal da transparência do município: diárias pagas a vereadores.
 *
 * Regras das fontes que moldam a coleta (docs/FONTES.md):
 * - o sistema legislativo e o portal respondem em HTML, sem contrato: a leitura é por marcação, e marcação que muda
 *   deixa o registro fora, sem inventar valor;
 * - a situação de tramitação da matéria não é mantida pela Câmara e não é publicada;
 * - cada sistema grafa o nome civil de um jeito: a ligação entre eles segue as regras de {@link Nomes}.
 */
public final class Caraguatatuba implements Fonte {

    public static final String FONTE_SAGL = "caraguatatuba-camara-sagl";
    public static final String FONTE_PORTAL = "caraguatatuba-camara-portal";
    public static final String FONTE_TRANSPARENCIA = "caraguatatuba-transparencia";

    public static final String IBGE = "3510500";
    public static final String UF = "SP";

    public static final String SAGL = "https://sistema.camaracaragua.sp.gov.br";
    public static final String PORTAL = "https://caraguatatuba.sp.leg.br";
    public static final String TRANSPARENCIA = "https://portaldatransparencia.caraguatatuba.sp.gov.br/api";

    static final String URL_INDICE = SAGL + "/consultas/parlamentar/parlamentar_index_html";
    static final String URL_FICHA = SAGL + "/consultas/parlamentar/parlamentar_mostrar_proc?cod_parlamentar=";
    static final String URL_MATERIAS = SAGL + "/consultas/materia/materia_pesquisar_proc";
    static final String URL_MATERIA = SAGL + "/consultas/materia/materia_mostrar_proc?cod_materia=";
    static final String URL_COMISSOES = PORTAL + "/vereadores/comissoes";
    static final String URL_LISTAS = PORTAL + "/api/table-list/data?table=LegislativeAttendances&limit=100&search=&portal_id=1&show_pagination=true&page=";
    static final String URL_LISTA = PORTAL + "/api/legislative/attendances/";
    /** Entidade 1 do portal da transparência: a Câmara. O parâmetro do ano é uma data. */
    static final String URL_DIARIAS = TRANSPARENCIA + "/execucao/diarias/diarias?id_entidade=1&cp_ano=";

    private static final String HTML = "text/html";
    private static final String JSON = "application/json";
    /** Como a ficha chama o órgão ("Participações na Mesa Diretora"). */
    private static final String MESA = "Mesa Diretora";
    /** Servidores pequenos: no mínimo 1 segundo entre chamadas a cada um. */
    private static final Duration INTERVALO = Duration.ofSeconds(1);
    private static final int MATERIAS_POR_PAGINA = 200;
    private static final int LIMITE_DE_PAGINAS = 50;
    /** Lista de presença de sessão com mais de 60 dias, já preenchida, vem do cache. */
    private static final int DIAS_DE_REVISAO = 60;
    /** P, FJ e F da lista de presença. L (licença) não vira evento. */
    static final Map<String, String> PRESENCAS = Map.of("P", "registrada", "FJ", "ausencia_justificada", "F", "falta");
    private static final Set<String> MARCAS = Set.of("P", "L", "FJ", "F");

    public record Legislatura(int numero, int anoInicio, int anoFim) {
    }

    public record Filiacao(String partido, LocalDate inicio, LocalDate fim) {
    }

    public record MandatoDaFicha(int legislatura, boolean titular, LocalDate inicio, LocalDate fim) {
    }

    /** Cargo na Mesa ou em comissão, com o período da fonte. */
    public record Cargo(String orgao, String papel, LocalDate inicio, LocalDate fim) {
    }

    public record Ficha(String nomeParlamentar, String nomeCivil, boolean emExercicio, String fotoUrl, List<Filiacao> filiacoes,
                        List<MandatoDaFicha> mandatos, List<Cargo> mesa, List<Cargo> comissoes, String codAutor) {
    }

    /** Matéria da pesquisa. O código é o da URL (base64 do identificador numérico). */
    public record Materia(String id, String codigo, String tipo, Integer numero, Integer ano, String ementa, LocalDate apresentacao,
                          String urlTexto) {
    }

    public record PaginaDeMaterias(int total, List<Materia> materias) {
    }

    public record MembroDeComissao(String comissao, String nome, String papel) {
    }

    /** Lista de presença de uma sessão: identificador da lista, tipo, número e data da sessão. */
    public record SessaoDaLista(String idLista, String tipo, Integer numero, LocalDate data) {
    }

    public record Marca(String nome, String marca) {
    }

    @Override
    public String nome() {
        return "caraguatatuba";
    }

    // ---------------------------------------------------------------- fetch

    @Override
    public void fetch(Contexto ctx, Coleta coleta) throws Exception {
        for (String servidor : List.of(SAGL, PORTAL, TRANSPARENCIA)) {
            ctx.http().respeitar(java.net.URI.create(servidor).getHost(), INTERVALO);
        }
        LocalDate hoje = ctx.hoje();
        Coleta.Arquivo arqIndice = coleta.obter(URL_INDICE, "sagl/legislaturas.html", HTML, Politica.SEMPRE);
        List<Legislatura> cobertas = cobertas(legislaturas(ler(ctx, arqIndice)), hoje);

        Set<String> cods = new LinkedHashSet<>();
        for (Legislatura leg : cobertas) {
            Coleta.Arquivo arq = coleta.obterOpcional(URL_INDICE + "?hdn_num_legislatura=" + leg.numero(),
                    arquivoDaLegislatura(leg.numero()), HTML, leg.anoFim() >= hoje.getYear() ? Politica.SEMPRE : Politica.SEMANAL);
            if (existe(ctx, arq)) {
                cods.addAll(parlamentaresDoIndice(ler(ctx, arq)));
            }
        }
        coleta.contar("vereadores", cods.size());

        for (String cod : cods) {
            // Telefone, e-mail e biografia saem antes de a ficha ir para o cache (ADR-0057).
            Coleta.Arquivo arq = coleta.obterDerivado(URL_FICHA + cod, arquivoDaFicha(cod), HTML, Politica.SEMANAL,
                    (baixado, destino) -> Files.writeString(destino, semContatoNemBiografia(Texto.lerArquivo(baixado))));
            if (!existe(ctx, arq)) {
                continue;
            }
            Ficha ficha = ficha(ler(ctx, arq));
            if (ficha.codAutor() == null) {
                continue;
            }
            for (int ano : anosDeMandato(ficha.mandatos(), hoje)) {
                materiasDoAno(ctx, coleta, cod, ficha.codAutor(), ano, hoje);
            }
        }

        coleta.obterOpcional(URL_COMISSOES, "portal/comissoes.html", HTML, Politica.SEMANAL);
        int listas = 0;
        for (SessaoDaLista sessao : sessoesDasListas(ctx, coleta)) {
            // Sessão futura já tem lista, vazia: só entra depois da data.
            if (sessao.data().isAfter(hoje)) {
                continue;
            }
            coleta.obterOpcional(URL_LISTA + sessao.idLista() + "/view", arquivoDaLista(sessao.idLista()), JSON,
                    politicaDaLista(sessao.data(), hoje, semMarcacaoNoCache(ctx, sessao.idLista())));
            listas++;
        }
        coleta.contar("listas_de_presenca", listas);

        // Só diária de vereador vai para o cache: nome de servidor e matrícula não são gravados (ADR-0057).
        // Todos os anos renovam por semana: vereador que entra na cobertura passa a ter as diárias dos anos fechados.
        Nomes nomes = nomes(ctx);
        for (int ano = Camara.INICIO_DA_COBERTURA.getYear(); ano <= hoje.getYear(); ano++) {
            coleta.obterDerivado(URL_DIARIAS + ano + "-01-01", arquivoDasDiarias(ano), JSON, Politica.SEMANAL,
                    (baixado, destino) -> Json.gravar(destino, diariasDeVereadores(Json.ler(baixado), nomes)));
        }
        coleta.periodo(new Meta.Periodo(Camara.INICIO_DA_COBERTURA, hoje));
    }

    /** Páginas da pesquisa de matérias de um autor num ano, até a fonte não ter mais resultado. */
    private void materiasDoAno(Contexto ctx, Coleta coleta, String cod, String codAutor, int ano, LocalDate hoje) throws Exception {
        Politica politica = Politica.porAnoSemanalEmLista(ano, hoje);
        int lidas = 0;
        for (int pagina = 1; pagina <= LIMITE_DE_PAGINAS; pagina++) {
            Coleta.Arquivo arq = coleta.obterOpcional(urlDasMaterias(codAutor, ano, pagina), arquivoDasMaterias(cod, ano, pagina), HTML, politica);
            if (!existe(ctx, arq)) {
                return;
            }
            PaginaDeMaterias lida = materias(ler(ctx, arq));
            lidas += lida.materias().size();
            if (lida.materias().isEmpty() || lidas >= lida.total()) {
                return;
            }
        }
    }

    /** Sessões das listas de presença, pelas páginas da listagem. A listagem renova a cada execução. */
    private List<SessaoDaLista> sessoesDasListas(Contexto ctx, Coleta coleta) throws Exception {
        List<SessaoDaLista> sessoes = new ArrayList<>();
        for (int pagina = 1; pagina <= LIMITE_DE_PAGINAS; pagina++) {
            Coleta.Arquivo arq = coleta.obterOpcional(URL_LISTAS + pagina, arquivoDaListagem(pagina), JSON, Politica.SEMPRE);
            if (!existe(ctx, arq)) {
                break;
            }
            JsonNode lida = Json.ler(Coleta.caminho(ctx, arq));
            sessoes.addAll(listas(lida));
            if (pagina >= lida.path("pagination").path("last_page").asInt(1)) {
                break;
            }
        }
        return sessoes;
    }

    static String urlDasMaterias(String codAutor, int ano, int pagina) {
        return URL_MATERIAS + "?incluir=0&existe_ocorrencia=0&lst_tip_materia=&txt_numero=&txt_prot_pref=&txt_ano=" + ano
                + "&txt_npc=&txt_num_protocolo=&dt_apres=&dt_apres2=&dt_public=&dt_public2=&hdn_txt_autor=&hdn_cod_autor=" + codAutor
                + "&lst_tip_autor=Parlamentar&lst_cod_partido=&txt_relator=&txt_assunto=&rad_tramitando=&lst_localizacao=&lst_tramitou="
                + "&lst_status=&rd_ordenacao=1&chk_coautor=0&step=" + MATERIAS_POR_PAGINA + "&page=" + pagina;
    }

    static String arquivoDaLegislatura(int numero) {
        return "sagl/legislatura-" + numero + ".html";
    }

    static String arquivoDaFicha(String cod) {
        return "sagl/parlamentares/" + cod + ".html";
    }

    /** Uma pasta por autor e ano: as páginas da mesma pesquisa renovam no mesmo dia. */
    static String arquivoDasMaterias(String cod, int ano, int pagina) {
        return "sagl/materias/" + cod + "-" + ano + "/p" + pagina + ".html";
    }

    static String arquivoDaListagem(int pagina) {
        return "portal/presencas/listagem-p" + pagina + ".json";
    }

    static String arquivoDaLista(String id) {
        return "portal/presencas/listas/" + id + ".json";
    }

    static String arquivoDasDiarias(int ano) {
        return "transparencia/diarias-" + ano + ".json";
    }

    /** Lista recente, ou ainda sem marcação, renova toda semana. A antiga já preenchida fica no cache. */
    static Politica politicaDaLista(LocalDate data, LocalDate hoje, boolean semMarcacaoNoCache) {
        return data.isBefore(hoje.minusDays(DIAS_DE_REVISAO)) && !semMarcacaoNoCache ? Politica.SE_AUSENTE : Politica.SEMANAL;
    }

    private boolean semMarcacaoNoCache(Contexto ctx, String idLista) {
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaLista(idLista));
        return existe(ctx, arq) && marcas(Json.texto(Json.ler(Coleta.caminho(ctx, arq)), "html")).isEmpty();
    }

    // ------------------------------------------------------------ normalize

    @Override
    public void normalize(Contexto ctx) throws Exception {
        Coleta.Arquivo arqIndice = Coleta.metadados(ctx, nome(), "sagl/legislaturas.html");
        if (!existe(ctx, arqIndice)) {
            System.out.println("caraguatatuba: sem dados coletados para normalizar");
            return;
        }
        LocalDate hoje = ctx.hoje();
        Map<String, Ficha> fichas = fichas(ctx);
        Nomes nomes = nomes(ctx);
        Contagem contagem = new Contagem();
        Map<String, List<Evento>> eventos = presencas(ctx, nomes, contagem);
        Instant capturaDasListas = capturaMaisRecenteDasListas(ctx);
        Map<String, List<OrgaoOuFrente>> comissoesAtuais = comissoesAtuais(ctx, nomes, contagem);
        Map<String, List<Despesa>> diarias = diarias(ctx, fichas, contagem);

        List<Pessoa> pessoas = new ArrayList<>();
        Map<String, String> nomesCivis = new TreeMap<>();
        Path saida = ctx.normalizado().resolve(nome());
        int totalDeProposicoes = 0;
        for (var entrada : fichas.entrySet()) {
            String cod = entrada.getKey();
            Ficha ficha = entrada.getValue();
            List<MandatoDaFicha> cobertos = mandatosCobertos(ficha.mandatos());
            if (ficha.nomeParlamentar() == null || cobertos.isEmpty()) {
                continue;
            }
            Coleta.Arquivo arqFicha = Coleta.metadados(ctx, nome(), arquivoDaFicha(cod));
            Prov provFicha = Prov.de(FONTE_SAGL, URL_FICHA + cod, arqFicha);
            boolean emExercicio = emExercicio(ficha, hoje);
            LocalDate ultimoFim = cobertos.stream().map(MandatoDaFicha::fim).filter(f -> f != null).max(Comparator.naturalOrder()).orElse(null);
            String situacao = situacao(emExercicio, ultimoFim, hoje);
            // Quem saiu: partido na data em que o último mandato terminou, não a filiação de hoje.
            LocalDate referencia = emExercicio || ultimoFim == null || ultimoFim.isAfter(hoje) ? hoje : ultimoFim;
            List<LinkOficial> links = List.of(new LinkOficial("Ficha no sistema legislativo da Câmara Municipal de Caraguatatuba",
                    URL_FICHA + cod, provFicha));
            pessoas.add(new Pessoa(nome(), cod, ficha.nomeParlamentar(), "vereador", "municipal", IBGE, UF,
                    partidoEm(ficha.filiacoes(), referencia), ficha.fotoUrl(), situacao, emExercicio, links));
            if (ficha.nomeCivil() != null) {
                nomesCivis.put(cod, ficha.nomeCivil());
            }

            List<Mandato> mandatos = new ArrayList<>();
            for (MandatoDaFicha m : cobertos) {
                mandatos.add(new Mandato("vereador", "municipal", IBGE, UF, m.inicio(), m.fim(), situacaoDoMandato(m, emExercicio, hoje),
                        m.titular() ? "Titular" : "Suplente", m.legislatura(), null, null, provFicha));
            }
            List<OrgaoOuFrente> orgaos = orgaos(ficha, provFicha);
            orgaos.addAll(comissoesAtuais.getOrDefault(cod, List.of()));
            orgaos.sort(Comparator.comparing((OrgaoOuFrente o) -> o.inicio() == null ? LocalDate.MAX : o.inicio()).reversed()
                    .thenComparing(OrgaoOuFrente::nome));
            List<FonteUsada> fontesDasProposicoes = new ArrayList<>();
            List<Proposicao> proposicoes = proposicoes(ctx, cod, anosDeMandato(ficha.mandatos(), hoje), fontesDasProposicoes, contagem);
            totalDeProposicoes += proposicoes.size();
            List<Evento> doVereador = eventos.getOrDefault(cod, List.of());

            ObjectNode blocos = Json.SAIDA.createObjectNode();
            blocos.set("mandatos", Json.SAIDA.valueToTree(mandatos));
            blocos.set("despesas", Json.SAIDA.valueToTree(diarias.getOrDefault(cod, List.of())));
            blocos.set("proposicoes", Json.SAIDA.valueToTree(proposicoes));
            blocos.set("eventos", Json.SAIDA.valueToTree(doVereador));
            blocos.set("orgaos_e_frentes", Json.SAIDA.valueToTree(orgaos));
            // Mandatos, órgãos e despesas citam a proveniência dos próprios registros.
            Map<String, List<FonteUsada>> fontes = new LinkedHashMap<>(Registros.fontesDosBlocos(blocos));
            // Cada matéria aponta para a página dela; o bloco cita as páginas da pesquisa, de onde os registros saíram.
            fontes.remove("proposicoes");
            if (!proposicoes.isEmpty()) {
                fontes.put("proposicoes", fontesDasProposicoes);
            }
            if (!doVereador.isEmpty() && capturaDasListas != null) {
                // Uma lista por sessão: o bloco cita a listagem, com a captura mais recente.
                fontes.put("eventos", List.of(new FonteUsada(FONTE_PORTAL, URL_LISTAS + 1, capturaDasListas)));
            }
            blocos.set("fontes", Json.SAIDA.valueToTree(fontes));
            Json.gravar(saida.resolve("blocos").resolve(cod + ".json"), blocos);
        }
        pessoas.sort(Comparator.comparing(p -> Texto.normalizarNome(p.nome())));
        Json.gravar(saida.resolve("pessoas.json"), pessoas);
        // Nome civil que a própria Câmara publica: só o vínculo com o TSE o lê, e ele não sai no perfil.
        Json.gravar(saida.resolve("nomes-civis.json"), nomesCivis);
        long emExercicio = pessoas.stream().filter(Pessoa::emExercicio).count();
        System.out.println("caraguatatuba: " + pessoas.size() + " vereadores normalizados (" + emExercicio + " em exercício), "
                + totalDeProposicoes + " proposições, " + contagem.sessoes + " sessões com presença (" + contagem.eventos
                + " registros), " + contagem.diarias + " diárias");
        System.out.println("caraguatatuba: fora: " + contagem.listasSemMarcacao + " lista(s) sem marcação, " + contagem.licencas
                + " licença(s), " + contagem.outrasMarcas + " marca(s) desconhecida(s), " + contagem.nomesSemPerfil.size()
                + " nome(s) do portal sem vereador " + contagem.nomesSemPerfil + ", " + contagem.diariasSemPerfil
                + " diária(s) com cargo de vereador e nome que não é de vereador, " + contagem.diariasForaDoMandato
                + " diária(s) fora do mandato, " + contagem.materiasSemLeitura + " matéria(s) anunciada(s) pela pesquisa e não lida(s)");
        if (!nomes.ligadosPorGrafia().isEmpty()) {
            System.out.println("caraguatatuba: nomes ligados por grafia próxima: " + nomes.ligadosPorGrafia());
        }
    }

    /** O que entra e o que fica fora, para a saída do normalize. */
    static final class Contagem {
        int sessoes;
        int eventos;
        int listasSemMarcacao;
        int licencas;
        int outrasMarcas;
        int diarias;
        int diariasSemPerfil;
        int diariasForaDoMandato;
        int materiasSemLeitura;
        final Set<String> nomesSemPerfil = new TreeSet<>();
    }

    /** Fichas dos vereadores das legislaturas cobertas, pelo código do parlamentar, em ordem numérica. */
    private Map<String, Ficha> fichas(Contexto ctx) {
        Map<String, Ficha> fichas = new TreeMap<>(Comparator.<String>comparingInt(Integer::parseInt));
        Coleta.Arquivo arqIndice = Coleta.metadados(ctx, nome(), "sagl/legislaturas.html");
        if (!existe(ctx, arqIndice)) {
            return fichas;
        }
        for (Legislatura leg : cobertas(legislaturas(ler(ctx, arqIndice)), ctx.hoje())) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaLegislatura(leg.numero()));
            if (!existe(ctx, arq)) {
                continue;
            }
            for (String cod : parlamentaresDoIndice(ler(ctx, arq))) {
                Coleta.Arquivo arqFicha = Coleta.metadados(ctx, nome(), arquivoDaFicha(cod));
                if (existe(ctx, arqFicha) && !fichas.containsKey(cod)) {
                    fichas.put(cod, ficha(ler(ctx, arqFicha)));
                }
            }
        }
        return fichas;
    }

    /** Sessões das listas de presença já em cache. */
    private List<SessaoDaLista> sessoesEmCache(Contexto ctx) {
        List<SessaoDaLista> sessoes = new ArrayList<>();
        for (int pagina = 1; pagina <= LIMITE_DE_PAGINAS; pagina++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaListagem(pagina));
            if (!existe(ctx, arq)) {
                break;
            }
            sessoes.addAll(listas(Json.ler(Coleta.caminho(ctx, arq))));
        }
        return sessoes;
    }

    /**
     * Nomes de cada vereador: os da ficha e as grafias do portal. As listas de presença e a composição das comissões só
     * têm vereadores; por isso a grafia próxima vale nelas, e o nome ligado passa a valer nas diárias.
     */
    Nomes nomes(Contexto ctx) {
        Nomes nomes = new Nomes();
        fichas(ctx).forEach(nomes::ficha);
        for (SessaoDaLista sessao : sessoesEmCache(ctx)) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaLista(sessao.idLista()));
            if (existe(ctx, arq)) {
                marcas(Json.texto(Json.ler(Coleta.caminho(ctx, arq)), "html")).forEach(m -> nomes.deVereador(m.nome()));
            }
        }
        Coleta.Arquivo arqComissoes = Coleta.metadados(ctx, nome(), "portal/comissoes.html");
        if (existe(ctx, arqComissoes)) {
            comissoes(ler(ctx, arqComissoes)).forEach(m -> nomes.deVereador(m.nome()));
        }
        return nomes;
    }

    /** Presença de cada vereador, por lista. Lista sem marcação e sessão futura ficam fora. */
    private Map<String, List<Evento>> presencas(Contexto ctx, Nomes nomes, Contagem contagem) {
        Map<String, List<Evento>> porVereador = new HashMap<>();
        for (SessaoDaLista sessao : sessoesEmCache(ctx)) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaLista(sessao.idLista()));
            if (sessao.data().isAfter(ctx.hoje()) || !existe(ctx, arq)) {
                continue;
            }
            List<Marca> marcas = marcas(Json.texto(Json.ler(Coleta.caminho(ctx, arq)), "html"));
            if (marcas.isEmpty()) {
                contagem.listasSemMarcacao++;
                continue;
            }
            contagem.sessoes++;
            // O registro aponta para a mesma lista em PDF, que é a versão que a Câmara oferece para impressão.
            Prov prov = Prov.de(FONTE_PORTAL, URL_LISTA + sessao.idLista() + "/pdf", arq);
            Set<String> jaContados = new java.util.HashSet<>();
            for (Marca marca : marcas) {
                String presenca = PRESENCAS.get(marca.marca());
                if (presenca == null) {
                    if ("L".equals(marca.marca())) {
                        contagem.licencas++;
                    } else {
                        contagem.outrasMarcas++;
                    }
                    continue;
                }
                String cod = nomes.deVereador(marca.nome());
                if (cod == null) {
                    contagem.nomesSemPerfil.add(marca.nome());
                    continue;
                }
                if (jaContados.add(cod)) {
                    porVereador.computeIfAbsent(cod, k -> new ArrayList<>()).add(evento(sessao, presenca, prov));
                    contagem.eventos++;
                }
            }
        }
        porVereador.values().forEach(lista -> lista.sort(Comparator.comparing(Evento::data).reversed()));
        return porVereador;
    }

    static Evento evento(SessaoDaLista sessao, String presenca, Prov prov) {
        String titulo = sessao.numero() == null ? sessao.tipo() : sessao.tipo() + " nº " + sessao.numero();
        return new Evento("presenca-" + sessao.idLista(), sessao.data().toString(), sessao.tipo(), titulo, "Plenário", null, presenca, prov);
    }

    private Instant capturaMaisRecenteDasListas(Contexto ctx) {
        Instant recente = null;
        for (SessaoDaLista sessao : sessoesEmCache(ctx)) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDaLista(sessao.idLista()));
            if (existe(ctx, arq) && (recente == null || arq.capturadoEm().isAfter(recente))) {
                recente = arq.capturadoEm();
            }
        }
        return recente;
    }

    /** Composição atual das comissões no portal. A fonte não dá data de início nem de fim. */
    private Map<String, List<OrgaoOuFrente>> comissoesAtuais(Contexto ctx, Nomes nomes, Contagem contagem) {
        Map<String, List<OrgaoOuFrente>> porVereador = new HashMap<>();
        Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), "portal/comissoes.html");
        if (!existe(ctx, arq)) {
            return porVereador;
        }
        Prov prov = Prov.de(FONTE_PORTAL, URL_COMISSOES, arq);
        for (MembroDeComissao membro : comissoes(ler(ctx, arq))) {
            String cod = nomes.deVereador(membro.nome());
            if (cod == null) {
                contagem.nomesSemPerfil.add(membro.nome());
                continue;
            }
            porVereador.computeIfAbsent(cod, k -> new ArrayList<>())
                    .add(new OrgaoOuFrente("comissao", membro.comissao(), null, membro.papel(), null, null, prov));
        }
        return porVereador;
    }

    /** Mesa e comissões da ficha cujo período toca a cobertura. */
    static List<OrgaoOuFrente> orgaos(Ficha ficha, Prov prov) {
        List<OrgaoOuFrente> lista = new ArrayList<>();
        for (Cargo c : ficha.mesa()) {
            if (c.fim() == null || !c.fim().isBefore(Camara.INICIO_DA_COBERTURA)) {
                lista.add(new OrgaoOuFrente("orgao", MESA, null, c.papel(), c.inicio(), c.fim(), prov));
            }
        }
        for (Cargo c : ficha.comissoes()) {
            if (c.orgao() != null && (c.fim() == null || !c.fim().isBefore(Camara.INICIO_DA_COBERTURA))) {
                lista.add(new OrgaoOuFrente("comissao", c.orgao(), null, c.papel(), c.inicio(), c.fim(), prov));
            }
        }
        return lista;
    }

    /**
     * Matérias do vereador nos anos de mandato. A mesma matéria em duas páginas (lista que deslocou) entra uma vez.
     * A diferença entre o total que a pesquisa anuncia e o que foi lido vai para a contagem: é sinal de marcação que mudou.
     */
    private List<Proposicao> proposicoes(Contexto ctx, String cod, List<Integer> anos, List<FonteUsada> fontes, Contagem contagem) {
        Map<String, Proposicao> porId = new LinkedHashMap<>();
        for (int ano : anos) {
            int total = 0;
            Set<String> doAno = new java.util.HashSet<>();
            for (int pagina = 1; pagina <= LIMITE_DE_PAGINAS; pagina++) {
                Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDasMaterias(cod, ano, pagina));
                if (!existe(ctx, arq)) {
                    break;
                }
                PaginaDeMaterias lida = materias(ler(ctx, arq));
                total = Math.max(total, lida.total());
                for (Materia m : lida.materias()) {
                    doAno.add(m.id());
                    porId.putIfAbsent(m.id(), proposicao(m, arq));
                }
                fontes.add(FonteUsada.de(FONTE_SAGL, arq));
            }
            contagem.materiasSemLeitura += Math.max(0, total - doAno.size());
        }
        List<Proposicao> lista = new ArrayList<>(porId.values());
        lista.sort(Comparator.comparing((Proposicao p) -> p.dataApresentacao() == null ? "" : p.dataApresentacao()).reversed());
        return lista;
    }

    /** A situação fica vazia: a Câmara não mantém o campo de tramitação (ADR-0057). */
    static Proposicao proposicao(Materia m, Coleta.Arquivo arq) {
        return new Proposicao(m.id(), m.tipo(), m.numero(), m.ano(), m.tipo() + " nº " + m.numero() + "/" + m.ano(), m.ementa(), null,
                null, m.apresentacao() == null ? null : m.apresentacao().toString(), null, null, m.urlTexto(),
                Prov.de(FONTE_SAGL, URL_MATERIA + m.codigo(), arq));
    }

    /** Diárias de cada vereador, dos arquivos derivados. Só entra a diária com data dentro de um mandato dele. */
    private Map<String, List<Despesa>> diarias(Contexto ctx, Map<String, Ficha> fichas, Contagem contagem) {
        Map<String, List<Despesa>> porVereador = new HashMap<>();
        for (int ano = Camara.INICIO_DA_COBERTURA.getYear(); ano <= ctx.hoje().getYear(); ano++) {
            Coleta.Arquivo arq = Coleta.metadados(ctx, nome(), arquivoDasDiarias(ano));
            if (!existe(ctx, arq)) {
                continue;
            }
            JsonNode derivado = Json.ler(Coleta.caminho(ctx, arq));
            contagem.diariasSemPerfil += derivado.path("de_vereador_sem_perfil").asInt(0);
            Prov prov = Prov.de(FONTE_TRANSPARENCIA, arq.url(), arq);
            for (JsonNode d : derivado.path("diarias")) {
                String cod = Json.texto(d, "parlamentar");
                Ficha ficha = cod == null ? null : fichas.get(cod);
                Despesa despesa = diaria(d, prov);
                if (ficha == null || despesa == null) {
                    continue;
                }
                if (!emMandato(ficha.mandatos(), despesa.data())) {
                    contagem.diariasForaDoMandato++;
                    continue;
                }
                porVereador.computeIfAbsent(cod, k -> new ArrayList<>()).add(despesa);
                contagem.diarias++;
            }
        }
        porVereador.values().forEach(lista -> lista.sort(Comparator.comparing(Despesa::data).reversed()));
        return porVereador;
    }

    /** Diária como despesa: data de concessão (ou a do empenho, ou a da saída), valor e número do empenho. */
    static Despesa diaria(JsonNode d, Prov prov) {
        BigDecimal valor = Texto.valor(Json.texto(d, "valor"));
        LocalDate data = primeira(data(Json.texto(d, "dt_concessao")), data(Json.texto(d, "dt_empenho")), data(Json.texto(d, "dt_saida")));
        if (valor == null || data == null) {
            return null;
        }
        String empenho = Json.texto(d, "nr_empenho");
        return new Despesa(data, data.getYear(), data.getMonthValue(), "Diária", null, null, valor,
                empenho == null ? null : "Empenho " + empenho, null, prov);
    }

    /**
     * Da resposta do portal da transparência, só as diárias de vereador, sem nome e sem matrícula. Conta as que têm cargo
     * de vereador e nome que não é de vereador: ficam fora e aparecem na saída do normalize.
     */
    static ObjectNode diariasDeVereadores(JsonNode resposta, Nomes nomes) throws IOException {
        if (!resposta.path("diarias").isArray()) {
            throw new IOException("resposta de diárias sem a lista \"diarias\"");
        }
        ObjectNode saida = Json.SAIDA.createObjectNode();
        ArrayNode lista = saida.putArray("diarias");
        int total = 0;
        int semPerfil = 0;
        for (JsonNode d : resposta.path("diarias")) {
            total++;
            String cargo = Json.texto(d, "cargo_beneficiario");
            String cod = nomes.deBeneficiario(Json.texto(d, "ds_beneficiario"), cargo);
            if (cod == null) {
                if (cargoDeVereador(cargo)) {
                    semPerfil++;
                }
                continue;
            }
            ObjectNode registro = lista.addObject();
            registro.put("parlamentar", cod);
            for (String campo : List.of("valor", "dt_concessao", "dt_empenho", "dt_saida", "nr_empenho")) {
                registro.set(campo, d.get(campo));
            }
        }
        saida.put("total_na_fonte", total);
        saida.put("de_vereador_sem_perfil", semPerfil);
        return saida;
    }

    // ------------------------------------------------------------- leitura

    private static final Pattern OPCAO_DE_LEGISLATURA = Pattern.compile(
            "hdn_num_legislatura=(\\d+)\"[^>]*>\\s*\\d+(?:ª|&ordf;|&#170;)\\s*\\((\\d{4})\\s*-\\s*(\\d{4})\\)");
    private static final Pattern PARLAMENTAR = Pattern.compile("parlamentar_mostrar_proc\\?cod_parlamentar=(\\d+)");
    private static final Pattern SCRIPT_OU_ESTILO = Pattern.compile("<(script|style)\\b.*?</\\1>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_DE_CONTATO = Pattern.compile(
            "<li\\b[^>]*>(?:(?!</li>).)*?(?:Telefone|Celular|WhatsApp|mailto:|tel:)(?:(?!</li>).)*?</li>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PAINEL = Pattern.compile("<div class=\"tab-pane");
    private static final Pattern NOME_PARLAMENTAR = Pattern.compile("<h1 class=\"firstHeading\">(.*?)</h1>", Pattern.DOTALL);
    private static final Pattern NOME_CIVIL = Pattern.compile("<b>\\s*Nome civil:\\s*</b>([^<]*)");
    private static final Pattern SELO = Pattern.compile("<span class=\"[^\"]*badge[^\"]*\"[^>]*>([^<]*)</span>");
    private static final Pattern FOTO = Pattern.compile("src=\"(https?://[^\"]*/sapl_documentos/parlamentar/fotos/[^\"]+)\"");
    private static final Pattern LINHA = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELULA = Pattern.compile("<td\\b[^>]*>(.*?)</td>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");
    private static final Pattern NUMERO_DA_LEGISLATURA = Pattern.compile("^(\\d+)ª");
    private static final Pattern PERIODO = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})(?:\\s*a\\s*(\\d{2}/\\d{2}/\\d{4}))?");
    private static final Pattern COD_AUTOR = Pattern.compile("hdn_cod_autor=(\\d+)");
    private static final Pattern SIGLA_COM_DIGITOS = Pattern.compile("^(\\p{L}[\\p{L} ]*?)\\d+$");

    /** Legislaturas do seletor da página de vereadores. "Sem mandato cadastrado" (-1) fica fora. */
    static List<Legislatura> legislaturas(String html) {
        List<Legislatura> lista = new ArrayList<>();
        Matcher m = OPCAO_DE_LEGISLATURA.matcher(html == null ? "" : html);
        while (m.find()) {
            Legislatura leg = new Legislatura(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            if (!lista.contains(leg)) {
                lista.add(leg);
            }
        }
        lista.sort(Comparator.comparingInt(Legislatura::numero));
        return lista;
    }

    /** Legislaturas que tocam a cobertura: terminam no ano de início da cobertura ou depois, e já começaram. */
    static List<Legislatura> cobertas(List<Legislatura> legislaturas, LocalDate hoje) {
        List<Legislatura> cobertas = new ArrayList<>();
        for (Legislatura l : legislaturas) {
            if (l.anoFim() >= Camara.INICIO_DA_COBERTURA.getYear() && l.anoInicio() <= hoje.getYear()) {
                cobertas.add(l);
            }
        }
        return cobertas;
    }

    /** Códigos dos parlamentares de uma página de legislatura, na ordem da fonte e sem repetição. */
    static List<String> parlamentaresDoIndice(String html) {
        Set<String> cods = new LinkedHashSet<>();
        Matcher m = PARLAMENTAR.matcher(html == null ? "" : html);
        while (m.find()) {
            cods.add(m.group(1));
        }
        return new ArrayList<>(cods);
    }

    /**
     * Ficha como vai para o cache: sem script, sem os itens de contato e sem a aba de biografia (ADR-0054 e ADR-0057).
     * Se a aba de biografia existir e a marcação depois dela mudar, a ficha não é gravada.
     */
    static String semContatoNemBiografia(String html) throws IOException {
        String limpo = SCRIPT_OU_ESTILO.matcher(html).replaceAll("");
        limpo = ITEM_DE_CONTATO.matcher(limpo).replaceAll("");
        Matcher biografia = Pattern.compile("<div class=\"tab-pane[^\"]*\" id=\"biografia\"").matcher(limpo);
        if (biografia.find()) {
            Matcher proxima = PAINEL.matcher(limpo);
            if (!proxima.find(biografia.end())) {
                throw new IOException("ficha sem seção depois da biografia: a marcação mudou");
            }
            limpo = limpo.substring(0, biografia.start()) + limpo.substring(proxima.start());
        }
        return limpo;
    }

    /** Ficha do vereador no sistema legislativo. Campo que a marcação não traz fica nulo ou vazio. */
    static Ficha ficha(String html) {
        String h = html == null ? "" : html;
        int fimDoCabecalho = h.indexOf("id=\"nav_parlamentar\"");
        String cabecalho = fimDoCabecalho < 0 ? h : h.substring(0, fimDoCabecalho);
        String nomeParlamentar = grupo(NOME_PARLAMENTAR, cabecalho);
        String nomeCivil = grupo(NOME_CIVIL, cabecalho);
        boolean emExercicio = false;
        Matcher selo = SELO.matcher(cabecalho);
        while (selo.find()) {
            emExercicio |= "EM EXERCICIO".equals(Texto.normalizarNome(limpo(selo.group(1))));
        }
        Matcher foto = FOTO.matcher(cabecalho);
        String fotoUrl = foto.find() ? foto.group(1) : null;

        List<Filiacao> filiacoes = new ArrayList<>();
        for (List<String> c : linhas(secao(h, "filiacao"))) {
            String partido = c.isEmpty() ? null : sigla(c.get(0));
            if (partido != null && c.size() >= 2) {
                filiacoes.add(new Filiacao(partido, dataBr(c.get(1)), c.size() > 2 ? dataBr(c.get(2)) : null));
            }
        }
        filiacoes.sort(Comparator.comparing((Filiacao f) -> f.inicio() == null ? LocalDate.MIN : f.inicio()).reversed());

        List<MandatoDaFicha> mandatos = new ArrayList<>();
        for (List<String> c : linhas(secao(h, "mandato"))) {
            Matcher numero = c.isEmpty() || c.get(0) == null ? null : NUMERO_DA_LEGISLATURA.matcher(c.get(0));
            if (c.size() < 5 || numero == null || !numero.find() || dataBr(c.get(3)) == null) {
                continue;
            }
            mandatos.add(new MandatoDaFicha(Integer.parseInt(numero.group(1)), !"NAO".equals(Texto.normalizarNome(c.get(1))),
                    dataBr(c.get(3)), dataBr(c.get(4))));
        }
        mandatos.sort(Comparator.comparing(MandatoDaFicha::inicio));

        List<Cargo> mesa = new ArrayList<>();
        for (List<String> c : linhas(secao(h, "mesa"))) {
            LocalDate[] periodo = c.size() < 2 ? null : periodo(c.get(1));
            if (periodo != null && c.get(0) != null) {
                mesa.add(new Cargo(null, c.get(0), periodo[0], periodo[1]));
            }
        }
        List<Cargo> comissoes = new ArrayList<>();
        for (List<String> c : linhas(secao(h, "comissao"))) {
            LocalDate[] periodo = c.size() < 3 ? null : periodo(c.get(2));
            if (periodo != null && c.get(0) != null) {
                comissoes.add(new Cargo(c.get(0), c.get(1), periodo[0], periodo[1]));
            }
        }
        Matcher autor = COD_AUTOR.matcher(secao(h, "proposicao"));
        return new Ficha(nomeParlamentar, nomeCivil, emExercicio, fotoUrl, filiacoes, mandatos, mesa, comissoes,
                autor.find() ? autor.group(1) : null);
    }

    /** Trecho de uma aba da ficha, do início dela até a aba seguinte. */
    static String secao(String html, String id) {
        Matcher inicio = Pattern.compile("<div class=\"tab-pane[^\"]*\" id=\"" + Pattern.quote(id) + "\"").matcher(html);
        if (!inicio.find()) {
            return "";
        }
        Matcher proxima = PAINEL.matcher(html);
        return html.substring(inicio.end(), proxima.find(inicio.end()) ? proxima.start() : html.length());
    }

    /** Linhas de tabela com célula de dado, cada uma com o texto das células. Linha só de cabeçalho fica fora. */
    static List<List<String>> linhas(String html) {
        List<List<String>> linhas = new ArrayList<>();
        Matcher linha = LINHA.matcher(html == null ? "" : html);
        while (linha.find()) {
            List<String> celulas = new ArrayList<>();
            Matcher celula = CELULA.matcher(linha.group(1));
            while (celula.find()) {
                celulas.add(limpo(celula.group(1)));
            }
            if (!celulas.isEmpty()) {
                linhas.add(celulas);
            }
        }
        return linhas;
    }

    /** Sigla como a fonte grafa, sem os dígitos que o sistema cola no fim ("AGIR36"). */
    static String sigla(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = SIGLA_COM_DIGITOS.matcher(texto.strip());
        return Texto.vazioParaNulo(m.matches() ? m.group(1) : texto);
    }

    /** "01/01/2023 a 31/12/2024": início e fim. Sem a segunda data, o fim fica nulo. */
    static LocalDate[] periodo(String texto) {
        Matcher m = PERIODO.matcher(texto == null ? "" : texto);
        if (!m.find()) {
            return null;
        }
        return new LocalDate[] {dataBr(m.group(1)), m.group(2) == null ? null : dataBr(m.group(2))};
    }

    private static final Pattern RESULTADO = Pattern.compile("Resultado:\\s*([0-9.]+)\\s*mat");
    private static final Pattern ITEM_DE_MATERIA = Pattern.compile("<li class=\"list-group-item\">(.*?)</li>", Pattern.DOTALL);
    private static final Pattern CODIGO_DA_MATERIA = Pattern.compile("materia_mostrar_proc\\?cod_materia=([A-Za-z0-9+/=]+)");
    private static final Pattern TITULO_DA_MATERIA = Pattern.compile("fa-caret-right\"></i>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern IDENTIFICACAO = Pattern.compile("^(.+?)\\s+nº\\s+(\\d+)/(\\d{4})$");
    private static final Pattern EMENTA = Pattern.compile("<p class=\"mb-0\">(.*?)</p>", Pattern.DOTALL);
    private static final Pattern APRESENTACAO = Pattern.compile("Data de Apresentação:\\s*</b>\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern TEXTO_INTEGRAL = Pattern.compile("href=\"(https?://[^\"]*download_materia_pysc\\?cod_materia=[^\"]*texto_original=1)\"");

    /** Página da pesquisa de matérias: total da pesquisa e matérias da página. Item sem tipo, número e ano fica fora. */
    static PaginaDeMaterias materias(String html) {
        String h = html == null ? "" : html;
        Matcher resultado = RESULTADO.matcher(h);
        int total = resultado.find() ? Integer.parseInt(resultado.group(1).replace(".", "")) : 0;
        List<Materia> materias = new ArrayList<>();
        Matcher item = ITEM_DE_MATERIA.matcher(h);
        while (item.find()) {
            String corpo = item.group(1);
            Matcher codigo = CODIGO_DA_MATERIA.matcher(corpo);
            String titulo = limpo(grupoBruto(TITULO_DA_MATERIA, corpo));
            Matcher identificacao = titulo == null ? null : IDENTIFICACAO.matcher(titulo);
            if (!codigo.find() || identificacao == null || !identificacao.matches()) {
                continue;
            }
            Matcher texto = TEXTO_INTEGRAL.matcher(corpo);
            materias.add(new Materia(idDaMateria(codigo.group(1)), codigo.group(1), identificacao.group(1),
                    Integer.valueOf(identificacao.group(2)), Integer.valueOf(identificacao.group(3)),
                    Guarda.sanitizar(limpo(grupoBruto(EMENTA, corpo))), dataBr(grupoBruto(APRESENTACAO, corpo)),
                    texto.find() ? texto.group(1).replace("&amp;", "&") : null));
        }
        return new PaginaDeMaterias(total, materias);
    }

    /** O código da URL é o identificador numérico em base64. Se não for, vale o próprio código. */
    static String idDaMateria(String codigo) {
        try {
            String id = new String(Base64.getDecoder().decode(codigo), StandardCharsets.US_ASCII);
            return id.matches("^[0-9]+$") ? id : codigo;
        } catch (IllegalArgumentException e) {
            return codigo;
        }
    }

    private static final Pattern COMISSAO = Pattern.compile("commission-item\"[^>]*>(.*?)(?=<section\\b[^>]*commission-item\"|$)", Pattern.DOTALL);
    private static final Pattern NOME_DA_COMISSAO = Pattern.compile("<h2\\b[^>]*>(.*?)</h2>", Pattern.DOTALL);
    private static final Pattern MEMBRO = Pattern.compile("commission-member\"[^>]*>(.*?)(?=commission-member\"|$)", Pattern.DOTALL);
    private static final Pattern NOME_DO_MEMBRO = Pattern.compile("card-title-small[^\"]*\"[^>]*>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern PAPEL_DO_MEMBRO = Pattern.compile("<p class=\"text-xs fw-medium[^\"]*\"[^>]*>(.*?)</p>", Pattern.DOTALL);

    /** Composição atual das comissões, da página do portal. */
    static List<MembroDeComissao> comissoes(String html) {
        List<MembroDeComissao> membros = new ArrayList<>();
        Matcher comissao = COMISSAO.matcher(html == null ? "" : html);
        while (comissao.find()) {
            String corpo = comissao.group(1);
            String nome = limpo(grupoBruto(NOME_DA_COMISSAO, corpo));
            if (nome == null) {
                continue;
            }
            Matcher membro = MEMBRO.matcher(corpo);
            while (membro.find()) {
                String vereador = limpo(grupoBruto(NOME_DO_MEMBRO, membro.group(1)));
                if (vereador != null) {
                    membros.add(new MembroDeComissao(nome, vereador, limpo(grupoBruto(PAPEL_DO_MEMBRO, membro.group(1)))));
                }
            }
        }
        return membros;
    }

    private static final Pattern ID_DA_LISTA = Pattern.compile("data-attendance-id=\"(\\d+)\"");
    private static final Pattern SESSAO_DA_LISTA = Pattern.compile("<span\\b[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern SESSAO = Pattern.compile("^(.+?)\\s+nº\\s+(\\d+)\\s+de\\s+(\\d{2}/\\d{2}/\\d{4})$");

    /** Listas de presença de uma página da listagem. Só a lista de uma sessão entra: a de várias não diz a data de cada coluna. */
    static List<SessaoDaLista> listas(JsonNode pagina) {
        List<SessaoDaLista> listas = new ArrayList<>();
        for (JsonNode item : pagina.path("data")) {
            Matcher id = ID_DA_LISTA.matcher(textoOuVazio(item, "view_attendance"));
            List<String> sessoes = new ArrayList<>();
            Matcher sessao = SESSAO_DA_LISTA.matcher(textoOuVazio(item, "sessions_display"));
            while (sessao.find()) {
                String texto = limpo(sessao.group(1));
                if (texto != null) {
                    sessoes.add(texto);
                }
            }
            Matcher m = sessoes.size() == 1 ? SESSAO.matcher(sessoes.getFirst()) : null;
            if (id.find() && m != null && m.matches() && dataBr(m.group(3)) != null) {
                listas.add(new SessaoDaLista(id.group(1), m.group(1), Integer.valueOf(m.group(2)), dataBr(m.group(3))));
            }
        }
        return listas;
    }

    /**
     * Marcas da lista de presença de uma sessão: nome do vereador e P, L, FJ ou F. A tabela com mais de uma coluna de
     * data devolve lista vazia: a página não diz o ano de cada coluna.
     */
    static List<Marca> marcas(String html) {
        List<Marca> marcas = new ArrayList<>();
        int colunasDeData = -1;
        for (List<String> c : linhas(html)) {
            if (c.size() >= 2 && "Data".equals(c.get(0))) {
                colunasDeData = c.size() - 1;
            } else if (c.size() >= 2 && c.get(0) != null && c.get(1) != null && MARCAS.contains(c.get(1))) {
                marcas.add(new Marca(c.get(0), c.get(1)));
            }
        }
        return colunasDeData == 1 ? marcas : List.of();
    }

    // --------------------------------------------------------------- regras

    /** Em exercício: a fonte marca "Em exercício" e há mandato vigente na data. */
    static boolean emExercicio(Ficha ficha, LocalDate hoje) {
        return ficha.emExercicio() && emMandato(ficha.mandatos(), hoje);
    }

    static boolean emMandato(List<MandatoDaFicha> mandatos, LocalDate data) {
        for (MandatoDaFicha m : mandatos) {
            if (data != null && !data.isBefore(m.inicio()) && (m.fim() == null || !data.isAfter(m.fim()))) {
                return true;
            }
        }
        return false;
    }

    /** Mandatos que tocam a cobertura (ADR-0009). */
    static List<MandatoDaFicha> mandatosCobertos(List<MandatoDaFicha> mandatos) {
        List<MandatoDaFicha> cobertos = new ArrayList<>();
        for (MandatoDaFicha m : mandatos) {
            if (m.fim() == null || !m.fim().isBefore(Camara.INICIO_DA_COBERTURA)) {
                cobertos.add(m);
            }
        }
        return cobertos;
    }

    /** Anos de mandato dentro da cobertura, até o ano corrente. */
    static List<Integer> anosDeMandato(List<MandatoDaFicha> mandatos, LocalDate hoje) {
        Set<Integer> anos = new TreeSet<>();
        for (MandatoDaFicha m : mandatosCobertos(mandatos)) {
            int inicio = Math.max(m.inicio().getYear(), Camara.INICIO_DA_COBERTURA.getYear());
            // Mandato que termina em 1º de janeiro não alcança o ano novo.
            LocalDate ultimoDia = m.fim() == null ? hoje : m.fim().minusDays(1);
            int fim = Math.min(ultimoDia.getYear(), hoje.getYear());
            for (int ano = inicio; ano <= fim; ano++) {
                anos.add(ano);
            }
        }
        return new ArrayList<>(anos);
    }

    static String situacao(boolean emExercicio, LocalDate ultimoFim, LocalDate hoje) {
        if (emExercicio) {
            return "Em exercício";
        }
        return ultimoFim != null && ultimoFim.isBefore(hoje) ? "Mandato encerrado em " + ultimoFim : "Fora de exercício";
    }

    static String situacaoDoMandato(MandatoDaFicha m, boolean emExercicio, LocalDate hoje) {
        if (m.fim() != null && m.fim().isBefore(hoje)) {
            return "Mandato encerrado em " + m.fim();
        }
        if (hoje.isBefore(m.inicio())) {
            return "Mandato previsto";
        }
        return emExercicio ? "Em exercício" : "Fora de exercício";
    }

    /** Partido vigente na data: filiação iniciada até a data e sem desfiliação até ela. */
    static String partidoEm(List<Filiacao> filiacoes, LocalDate data) {
        for (Filiacao f : filiacoes) {
            boolean comecou = f.inicio() == null || !f.inicio().isAfter(data);
            boolean vigente = f.fim() == null || f.fim().isAfter(data);
            if (comecou && vigente) {
                return f.partido();
            }
        }
        return null;
    }

    static boolean cargoDeVereador(String cargo) {
        String c = Nomes.chave(cargo);
        return c != null && (c.startsWith("VEREADOR") || c.equals("PRESIDENTE") || c.startsWith("PRESIDENTE DA CAMARA"));
    }

    /**
     * Ligação por nome entre o sistema legislativo, o portal e o portal da transparência (ADR-0057). Nome idêntico, sem
     * acento e sem caixa, vale sempre. Grafia próxima vale só onde todos os nomes são de vereador. Sobrenome a mais vale
     * só nas diárias com cargo de vereador. Mais de um vereador possível: ninguém leva.
     */
    static final class Nomes {

        private static final String AMBIGUO = "";
        private static final Set<String> GERACAO = Set.of("JUNIOR", "JR", "FILHO", "NETO", "SOBRINHO", "SEGUNDO");

        /** Nome civil da ficha e grafias do portal já ligadas. */
        private final Map<String, String> codPorNomeCivil = new HashMap<>();
        private final Map<String, String> codPorNomeParlamentar = new HashMap<>();
        private final List<String> ligadosPorGrafia = new ArrayList<>();

        void ficha(String cod, Ficha ficha) {
            registrar(codPorNomeCivil, cod, ficha.nomeCivil());
            registrar(codPorNomeParlamentar, cod, ficha.nomeParlamentar());
        }

        private static void registrar(Map<String, String> mapa, String cod, String nome) {
            String chave = chave(nome);
            if (chave == null) {
                return;
            }
            String anterior = mapa.putIfAbsent(chave, cod);
            if (anterior != null && !anterior.equals(cod)) {
                mapa.put(chave, AMBIGUO);
            }
        }

        List<String> ligadosPorGrafia() {
            return ligadosPorGrafia;
        }

        /** Nome que só pode ser de vereador (lista de presença, composição de comissão). */
        String deVereador(String nome) {
            String chave = chave(nome);
            if (chave == null) {
                return null;
            }
            String cod = codPorNomeCivil.containsKey(chave) ? codPorNomeCivil.get(chave) : codPorNomeParlamentar.get(chave);
            if (cod != null) {
                return cod.equals(AMBIGUO) ? null : cod;
            }
            Set<String> possiveis = new TreeSet<>();
            codPorNomeCivil.forEach((civil, c) -> {
                if (!c.equals(AMBIGUO) && grafiaProxima(civil, chave)) {
                    possiveis.add(c);
                }
            });
            if (possiveis.size() != 1) {
                return null;
            }
            cod = possiveis.iterator().next();
            codPorNomeCivil.put(chave, cod);
            ligadosPorGrafia.add(nome);
            return cod;
        }

        /** Beneficiário de diária: nome idêntico ao civil ou, com cargo de vereador, o nome civil com sobrenome a mais. */
        String deBeneficiario(String nome, String cargo) {
            String chave = chave(nome);
            if (chave == null) {
                return null;
            }
            String cod = codPorNomeCivil.get(chave);
            if (cod != null) {
                return cod.equals(AMBIGUO) ? null : cod;
            }
            if (!cargoDeVereador(cargo)) {
                return null;
            }
            Set<String> possiveis = new TreeSet<>();
            codPorNomeCivil.forEach((civil, c) -> {
                if (!c.equals(AMBIGUO) && (comMaisSobrenomes(civil, chave) || comMaisSobrenomes(chave, civil))) {
                    possiveis.add(c);
                }
            });
            return possiveis.size() == 1 ? possiveis.iterator().next() : null;
        }

        /** Sem acento, em maiúsculas, só letras, dígitos e espaço simples. */
        static String chave(String nome) {
            String normalizado = Texto.normalizarNome(nome);
            if (normalizado == null) {
                return null;
            }
            return Texto.vazioParaNulo(normalizado.replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " "));
        }

        /** Mesmas palavras, menos uma que não é a primeira, com pelo menos cinco letras e até duas de diferença. */
        static boolean grafiaProxima(String a, String b) {
            String[] pa = a.split(" ");
            String[] pb = b.split(" ");
            if (pa.length != pb.length || pa.length < 2) {
                return false;
            }
            int diferente = -1;
            for (int i = 0; i < pa.length; i++) {
                if (!pa[i].equals(pb[i])) {
                    if (diferente >= 0) {
                        return false;
                    }
                    diferente = i;
                }
            }
            return diferente > 0 && Math.min(pa[diferente].length(), pb[diferente].length()) >= 5
                    && distancia(pa[diferente], pb[diferente]) <= 2;
        }

        /** O nome longo é o curto, de três palavras ou mais, seguido de sobrenomes. Júnior, Filho e Neto são outra pessoa. */
        static boolean comMaisSobrenomes(String curto, String longo) {
            String[] pc = curto.split(" ");
            String[] pl = longo.split(" ");
            if (pc.length < 3 || pl.length <= pc.length) {
                return false;
            }
            for (int i = 0; i < pc.length; i++) {
                if (!pc[i].equals(pl[i])) {
                    return false;
                }
            }
            for (int i = pc.length; i < pl.length; i++) {
                if (GERACAO.contains(pl[i])) {
                    return false;
                }
            }
            return true;
        }

        /** Distância de edição (Levenshtein). */
        static int distancia(String a, String b) {
            int[] anterior = new int[b.length() + 1];
            for (int j = 0; j <= b.length(); j++) {
                anterior[j] = j;
            }
            for (int i = 1; i <= a.length(); i++) {
                int[] atual = new int[b.length() + 1];
                atual[0] = i;
                for (int j = 1; j <= b.length(); j++) {
                    int troca = anterior[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                    atual[j] = Math.min(troca, Math.min(anterior[j], atual[j - 1]) + 1);
                }
                anterior = atual;
            }
            return anterior[b.length()];
        }
    }

    // -------------------------------------------------------------- helpers

    /** Texto de um trecho de HTML: sem etiquetas, com as entidades trocadas e espaço simples. Vazio vira null. */
    static String limpo(String html) {
        return html == null ? null : Texto.semEntidades(ETIQUETA.matcher(html).replaceAll(" "));
    }

    private static String textoOuVazio(JsonNode no, String campo) {
        String texto = Json.texto(no, campo);
        return texto == null ? "" : texto;
    }

    private static String grupo(Pattern padrao, String texto) {
        return limpo(grupoBruto(padrao, texto));
    }

    private static String grupoBruto(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto == null ? "" : texto);
        return m.find() ? m.group(1) : null;
    }

    static LocalDate dataBr(String texto) {
        String iso = Texto.dataBr(texto);
        try {
            return iso == null ? null : LocalDate.parse(iso);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    static LocalDate data(String texto) {
        if (texto == null || texto.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(texto.substring(0, 10));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate primeira(LocalDate... datas) {
        for (LocalDate d : datas) {
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static String ler(Contexto ctx, Coleta.Arquivo arquivo) {
        try {
            return Texto.lerArquivo(Coleta.caminho(ctx, arquivo));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("falha ao ler " + arquivo.caminho(), e);
        }
    }

    private static boolean existe(Contexto ctx, Coleta.Arquivo arquivo) {
        return arquivo != null && arquivo.bytes() > 0 && Files.exists(Coleta.caminho(ctx, arquivo));
    }
}
