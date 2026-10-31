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
