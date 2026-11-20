package etl.fontes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import etl.Contexto;
import etl.Json;
import etl.Registros.FonteUsada;
import etl.Registros.Prov;

/**
 * Mantém o registro acumulado de parlamentares já observados em casas cuja listagem oficial traz apenas quem está
 * em exercício (ADR-0006, ADR-0059). Fica em {@code data/raw/{fonte}/historico-deputados.json} para acompanhar
 * o cache bruto entre execuções sem inventar perfis fora das fontes.
 */
public final class HistoricoDeputados {

    public static final String ARQUIVO = "historico-deputados.json";

    /**
     * Fim da legislatura que cada adaptador cobre (ADR-0071). Depois dele, a listagem de quem está em exercício já é a da
     * legislatura seguinte: quem aparece nela pela primeira vez depois do fim não exerceu a legislatura coberta.
     */
    private static final Map<String, LocalDate> FIM_DA_LEGISLATURA = Map.ofEntries(
            Map.entry("alap", Alap.FIM_LEG_9),
            Map.entry("alba", Alba.FIM_LEG_20),
            Map.entry("alece", Alece.FIM_LEG_31),
            Map.entry("alego", Alego.FIM_LEG_20),
            Map.entry("alema", Alema.FIM_LEG_20),
            Map.entry("alems", Alems.FIM_LEG_12),
            Map.entry("alep", Alep.FIM_LEG_20),
            Map.entry("alepa", Alepa.FIM_LEG_60),
            Map.entry("alepe", Alepe.FIM_LEG_20),
            Map.entry("alerj", Alerj.FIM_LEG_13),
            Map.entry("ales", Ales.FIM_LEG_20),
            Map.entry("alesc", Alesc.FIM_LEG_20),
            Map.entry("alese", Alese.FIM_LEG_20),
            Map.entry("almt", Almt.FIM_LEG_20),
            Map.entry("alrn", Alrn.FIM_LEG_63),
            Map.entry("alrs", Alrs.FIM_LEG_56),
            Map.entry("cldf", Cldf.FIM_LEG_9));

    private HistoricoDeputados() {
    }

    /** Retrato de um parlamentar extraído da fonte oficial para consolidação no histórico. */
    public record Snapshot(String id, String nome, String partido, String fotoUrl, String extra, Prov prov) {

        public static Snapshot de(String id, String nome, String partido, String fotoUrl) {
            return new Snapshot(id, nome, partido, fotoUrl, null, null);
        }

        public static Snapshot de(String id, String nome, String partido, String fotoUrl, String extra, Prov prov) {
            return new Snapshot(id, nome, partido, fotoUrl, extra, prov);
        }
    }

    /** Registro persistido no arquivo bruto acumulado. */
    public record Salvo(String id, String nome, String partido, String fotoUrl, String extra, Prov prov,
                        LocalDate primeiroVistoEm, LocalDate ultimoVistoEm) {
    }

    /** Estrutura em disco de {@link #ARQUIVO}. */
    public record ListaSalva(List<Salvo> registros) {
    }

    /** Entrada consolidada para coleta ou normalização. */
    public record Entrada(String id, String nome, String partido, String fotoUrl, String extra, Prov prov,
                          LocalDate primeiroVistoEm, LocalDate ultimoVistoEm, boolean emExercicio) {

        public Prov provOu(Prov padrao) {
            return prov != null && prov.capturadoEm() != null ? prov : padrao;
        }

        public String situacao(String textoAtivo) {
            return emExercicio ? textoAtivo : "Fora de exercício";
        }

        public FonteUsada fonteUsada(Prov padrao) {
            Prov efetiva = provOu(padrao);
            return new FonteUsada(efetiva.fonteId(), efetiva.urlOrigem(), efetiva.capturadoEm());
        }
    }

    /** Registra os parlamentares atualmente em exercício no arquivo acumulado sem alterar os demais. */
    public static void registrar(Contexto ctx, String fonte, List<Snapshot> ativos, Prov provPadrao) {
        if (ctx == null || ativos == null || ativos.isEmpty()) {
            return;
        }
        mesclar(ctx, fonte, ativos, provPadrao, List.of());
    }

    /** Sobrecarga conveniente que mapeia registros brutos da fonte para {@link Snapshot}. */
    public static <T> void registrar(Contexto ctx, String fonte, List<T> ativos, Prov provPadrao,
                                     Function<T, Snapshot> mapear) {
        if (ctx == null || ativos == null || ativos.isEmpty()) {
            return;
        }
        registrar(ctx, fonte, mapearSnapshots(ativos, mapear), provPadrao);
    }

    /** Retorna os registros já salvos no histórico bruto (todos marcados como não ativos nesta leitura isolada). */
    public static List<Entrada> salvos(Contexto ctx, String fonte) {
        if (ctx == null) {
            return List.of();
        }
        List<Salvo> lista = ler(ctx.raw().resolve(fonte).resolve(ARQUIVO));
        List<Entrada> entradas = new ArrayList<>(lista.size());
        for (Salvo s : lista) {
            entradas.add(new Entrada(s.id(), s.nome(), s.partido(), s.fotoUrl(), s.extra(), s.prov(),
                    s.primeiroVistoEm(), s.ultimoVistoEm(), false));
        }
        return entradas;
    }

    public static List<Entrada> mesclar(Contexto ctx, String fonte, List<Snapshot> ativos, Prov provPadrao) {
        return mesclar(ctx, fonte, ativos, provPadrao, List.of());
    }

    /** Sobrecarga conveniente que mapeia registros brutos em exercício antes de consolidar o histórico. */
    public static <T> List<Entrada> mesclar(Contexto ctx, String fonte, List<T> ativos, Prov provPadrao,
                                            Function<T, Snapshot> mapear) {
        return mesclar(ctx, fonte, mapearSnapshots(ativos, mapear), provPadrao, List.of());
    }

    private static <T> List<Snapshot> mapearSnapshots(List<T> itens, Function<T, Snapshot> mapear) {
        if (itens == null || itens.isEmpty()) {
            return List.of();
        }
        List<Snapshot> lista = new ArrayList<>(itens.size());
        for (T item : itens) {
            lista.add(mapear.apply(item));
        }
        return lista;
    }

    /**
     * Combina os parlamentares em exercício no retrato atual, eventuais suplementares descobertos em índice oficial
     * (como o sitemap da ALESC) e os parlamentares vistos em coletas anteriores. Persiste o histórico atualizado e
     * devolve a lista unificada indicando quem segue em exercício nesta leitura.
     */
    public static List<Entrada> mesclar(Contexto ctx, String fonte, List<Snapshot> ativos, Prov provPadrao,
                                        List<Snapshot> suplementares) {
        Path arquivo = ctx.raw().resolve(fonte).resolve(ARQUIVO);
        List<Salvo> anteriores = ler(arquivo);
        Map<String, Salvo> porId = new LinkedHashMap<>();
        for (Salvo s : anteriores) {
            if (s.id() != null && !s.id().isBlank()) {
                porId.put(s.id(), s);
            }
        }

        LocalDate hoje = hoje(ctx);
        Set<String> ativosIds = new HashSet<>();
        if (ativos != null) {
            for (Snapshot a : ativos) {
                if (a == null || a.id() == null || a.id().isBlank()) {
                    continue;
                }
                ativosIds.add(a.id());
                Salvo prev = porId.get(a.id());
                LocalDate primeiro = prev != null && prev.primeiroVistoEm() != null ? prev.primeiroVistoEm() : hoje;
                String nome = naoVazio(a.nome(), prev == null ? null : prev.nome());
                String partido = naoVazio(a.partido(), prev == null ? null : prev.partido());
                String foto = naoVazio(a.fotoUrl(), prev == null ? null : prev.fotoUrl());
                String extra = naoVazio(a.extra(), prev == null ? null : prev.extra());
                Prov prov = a.prov() != null ? a.prov() : (provPadrao != null ? provPadrao : (prev == null ? null : prev.prov()));
                porId.put(a.id(), new Salvo(a.id(), nome, partido, foto, extra, prov, primeiro, hoje));
            }
        }

        if (suplementares != null) {
            for (Snapshot sup : suplementares) {
                if (sup == null || sup.id() == null || sup.id().isBlank() || ativosIds.contains(sup.id())) {
                    continue;
                }
                Salvo prev = porId.get(sup.id());
                if (prev == null) {
                    Prov prov = sup.prov() != null ? sup.prov() : provPadrao;
                    porId.put(sup.id(), new Salvo(sup.id(), sup.nome(), sup.partido(), sup.fotoUrl(), sup.extra(),
                            prov, null, null));
                } else {
                    String nome = naoVazio(prev.nome(), sup.nome());
                    String partido = naoVazio(prev.partido(), sup.partido());
                    String foto = naoVazio(prev.fotoUrl(), sup.fotoUrl());
                    String extra = naoVazio(prev.extra(), sup.extra());
                    Prov prov = prev.prov() != null ? prev.prov() : (sup.prov() != null ? sup.prov() : provPadrao);
                    porId.put(sup.id(), new Salvo(sup.id(), nome, partido, foto, extra, prov,
                            prev.primeiroVistoEm(), prev.ultimoVistoEm()));
                }
            }
        }

        List<Salvo> atualizados = List.copyOf(porId.values());
        if (!atualizados.isEmpty() && (!Files.exists(arquivo) || !Objects.equals(anteriores, atualizados))) {
            Json.gravar(arquivo, new ListaSalva(atualizados), true);
        }

        List<Entrada> resultado = new ArrayList<>(atualizados.size());
        LocalDate fimDaLegislatura = FIM_DA_LEGISLATURA.get(fonte);
        int vistosDepoisDoFim = 0;
        for (Salvo s : atualizados) {
            if (vistoSoDepoisDoFim(s, fimDaLegislatura)) {
                // Segue no arquivo acumulado, para quando o adaptador cobrir a legislatura nova.
                vistosDepoisDoFim++;
                continue;
            }
            resultado.add(new Entrada(s.id(), s.nome(), s.partido(), s.fotoUrl(), s.extra(), s.prov(),
                    s.primeiroVistoEm(), s.ultimoVistoEm(), ativosIds.contains(s.id())));
        }
        if (vistosDepoisDoFim > 0) {
            System.out.println(fonte + ": " + vistosDepoisDoFim + " parlamentares vistos só depois do fim da legislatura"
                    + " coberta (" + fimDaLegislatura + ") ficam fora até o adaptador cobrir a legislatura nova (ADR-0071)");
        }
        return resultado;
    }

    /** Quem foi visto pela primeira vez depois do fim da legislatura coberta é da legislatura seguinte. */
    static boolean vistoSoDepoisDoFim(Salvo salvo, LocalDate fimDaLegislatura) {
        return fimDaLegislatura != null && salvo.primeiroVistoEm() != null && salvo.primeiroVistoEm().isAfter(fimDaLegislatura);
    }

    private static List<Salvo> ler(Path arquivo) {
        if (!Files.exists(arquivo)) {
            return List.of();
        }
        try {
            ListaSalva salva = Json.ler(arquivo, ListaSalva.class);
            return salva == null || salva.registros() == null ? List.of() : salva.registros();
        } catch (Exception e) {
            System.err.println("historico-deputados: aviso ao ler " + arquivo + ": " + e.getMessage());
            return List.of();
        }
    }

    private static LocalDate hoje(Contexto ctx) {
        return ctx != null && ctx.agora() != null ? ctx.hoje() : LocalDate.now(ZoneOffset.UTC);
    }

    private static String naoVazio(String preferido, String reserva) {
        return preferido != null && !preferido.isBlank() ? preferido : reserva;
    }
}
