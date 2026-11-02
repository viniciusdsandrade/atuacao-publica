package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Identificadores sintéticos. */
class PublicadorTest {

    private static Publicador.Perfil perfil(String id, String chave, Map<String, String> tse) {
        Publicador.Perfil p = new Publicador.Perfil();
        p.id = id;
        p.chaveExterna = chave;
        p.tse.putAll(tse);
        return p;
    }

    @Test
    void eleitoQueTomaPosseMantemOIdPublicadoAntesDaPosse() {
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of("tse:900", "tse-900"), Set.of("tse-900"), Map.of());
        Publicador.Perfil deputado = perfil("cam-1", "camara:1", Map.of("2026", "900"));
        deputado.ids.put("camara", "1");

        Map<String, String> aliases = Publicador.identificadores(List.of(deputado), anterior);

        assertEquals("tse-900", deputado.id);
        assertEquals(Map.of("cam-1", "tse-900"), aliases);
        assertTrue(Publicador.sumiram(anterior, Set.of("tse-900"), aliases, Set.of()).isEmpty());
    }

    @Test
    void chaveDaCasaVenceChaveDoTseQuandoApontamParaOMesmoIdPublicado() {
        // Vereador publicado como sapl-306 com a candidatura X; um ex-deputado novo no arquivo tem a mesma candidatura X
        // (mesma pessoa, ainda não fundida): o id publicado fica com quem tem a chave da casa, e o outro segue com o seu.
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of("sapl:306", "sapl-3509502-306", "tse:X", "sapl-3509502-306"),
                Set.of("sapl-3509502-306"), Map.of());
        Publicador.Perfil deputado = perfil("cam-1", "camara:1", Map.of("2024", "X"));
        deputado.ids.put("camara", "1");
        Publicador.Perfil vereador = perfil("sapl-3509502-306", "sapl:306", Map.of("2024", "X"));
        vereador.ids.put("sapl", "306");

        Map<String, String> aliases = Publicador.identificadores(List.of(deputado, vereador), anterior);

        assertEquals("cam-1", deputado.id);
        assertEquals("sapl-3509502-306", vereador.id);
        assertTrue(aliases.isEmpty());
    }

    @Test
    void idPublicadoNaoPassaParaPerfilQueJaTemEsseIdComoDerivado() {
        // Pessoa publicada como sapl-306, fundida com a Câmara (camara:152610) e com o TSE (tse:X). Numa coleta incompleta
        // o vínculo não funde: o deputado e o vereador voltam em separado, e o índice anterior aponta as chaves do deputado
        // para sapl-306. O id publicado fica com o vereador, que já o tem como derivado; o deputado segue com o seu.
        // O resultado não pode depender da ordem dos perfis.
        Publicador.Anterior anterior = new Publicador.Anterior(
                Map.of("camara:152610", "sapl-3509502-306", "tse:X", "sapl-3509502-306"), Set.of("sapl-3509502-306"), Map.of());
        for (boolean deputadoPrimeiro : new boolean[] {true, false}) {
            Publicador.Perfil deputado = perfil("cam-152610", "camara:152610", Map.of("2022", "X"));
            deputado.ids.put("camara", "152610");
            Publicador.Perfil vereador = perfil("sapl-3509502-306", "sapl:306", Map.of());
            vereador.ids.put("sapl", "306");
            List<Publicador.Perfil> perfis = deputadoPrimeiro ? List.of(deputado, vereador) : List.of(vereador, deputado);

            Map<String, String> aliases = Publicador.identificadores(perfis, anterior);

            assertEquals("cam-152610", deputado.id);
            assertEquals("sapl-3509502-306", vereador.id);
            assertTrue(aliases.isEmpty());
            assertTrue(Publicador.sumiram(anterior, Set.of("cam-152610", "sapl-3509502-306"), aliases, Set.of()).isEmpty());
        }
    }

    @Test
    void aliasPublicadoContinuaValendo() {
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of("camara:1", "tse-900"), Set.of("tse-900"),
                Map.of("cam-1", "tse-900"));
        Publicador.Perfil deputado = perfil("cam-1", "camara:1", Map.of());

        Map<String, String> aliases = Publicador.identificadores(List.of(deputado), anterior);

        assertEquals(Map.of("cam-1", "tse-900"), aliases);
    }

    @Test
    void perfilQueSomeBloqueiaOPublishSalvoSeLiberado() {
        Publicador.Anterior anterior = new Publicador.Anterior(Map.of(), Set.of("cam-1", "cam-2"), Map.of());

        assertEquals(List.of("cam-2"), Publicador.sumiram(anterior, Set.of("cam-1"), Map.of(), Set.of()));
        assertTrue(Publicador.sumiram(anterior, Set.of("cam-1"), Map.of(), Set.of("cam-2")).isEmpty());
    }
}
