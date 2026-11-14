package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class PartidosTest {

    private static JsonNode candidatura(String partido, int ano) {
        return Json.SAIDA.createObjectNode().put("partido", partido).put("ano", ano);
    }

    /** Siglas como o TSE as grafa nos arquivos de candidaturas, com a mudança de grafia do PCdoB em 2026. */
    private static final Map<String, String> TSE = Partidos.grafiasDoTse(List.of(
            candidatura("UNIÃO", 2022), candidatura("UNIÃO", 2026), candidatura("REPUBLICANOS", 2026),
            candidatura("PC do B", 2018), candidatura("PCDOB", 2026), candidatura("PC do B", 2024),
            candidatura("SOLIDARIEDADE", 2026), candidatura("PODE", 2026), candidatura("PP", 2026),
            candidatura("CIDADANIA", 2026), candidatura("AVANTE", 2026), candidatura("NOVO", 2026),
            candidatura("MOBILIZA", 2024), candidatura("PL", 2026), candidatura("DEM", 2020), candidatura("PPS", 2018)));

    @Test
    void grafiaDoTseEADaEleicaoMaisRecenteEmQueASiglaAparece() {
        assertEquals("PCDOB", TSE.get("PCDOB"), "em 2026 o TSE passou a grafar PCDOB");
        assertEquals("UNIÃO", TSE.get("UNIAO"));
        assertEquals("DEM", TSE.get("DEM"), "sigla que só existe em eleição antiga continua na lista");
    }

    @Test
    void caixaAcentoEspacoEPontuacaoNaoSeparamOMesmoPartido() {
        for (String grafia : List.of("União", "UNIAO", "UNIÃO", "uniao", "UNI&#227;O")) {
            assertEquals("UNIÃO", Partidos.unificar(grafia, TSE), grafia);
        }
        for (String grafia : List.of("PCdoB", "PC do B", "PC DO B", "PCDOB", "pc do b")) {
            assertEquals("PCDOB", Partidos.unificar(grafia, TSE), grafia);
        }
        assertEquals("AVANTE", Partidos.unificar("Avante", TSE));
        assertEquals("NOVO", Partidos.unificar("Novo", TSE));
        assertEquals("MOBILIZA", Partidos.unificar("Mobiliza", TSE));
        assertEquals("PL", Partidos.unificar(" PL ", TSE));
    }

    @Test
    void nomePorExtensoEAbreviacaoDaCasaViramASiglaDoTse() {
        assertEquals("UNIÃO", Partidos.unificar("União Brasil", TSE));
        assertEquals("UNIÃO", Partidos.unificar("UNIÃO BRASIL", TSE));
        assertEquals("UNIÃO", Partidos.unificar("UB", TSE));
        for (String grafia : List.of("REP", "REPUB", "REPUBLICA", "REPUBLICANO", "Republicanos")) {
            assertEquals("REPUBLICANOS", Partidos.unificar(grafia, TSE), grafia);
        }
        assertEquals("SOLIDARIEDADE", Partidos.unificar("SD", TSE));
        assertEquals("SOLIDARIEDADE", Partidos.unificar("SDD", TSE));
        assertEquals("PODE", Partidos.unificar("Podemos", TSE));
        assertEquals("PP", Partidos.unificar("Progressistas", TSE));
        assertEquals("CIDADANIA", Partidos.unificar("CIDAD", TSE));
    }

    @Test
    void siglaAntigaNaoViraADoPartidoSucessor() {
        assertEquals("DEM", Partidos.unificar("DEM", TSE), "era o partido da pessoa na data");
        assertEquals("PPS", Partidos.unificar("PPS", TSE));
        assertEquals("PSL", Partidos.unificar("PSL", TSE), "sigla que o TSE normalizado não tem sai como veio");
    }

    @Test
    void semPartidoEUmaInformacaoDaCasaENaoAusenciaDeRegistro() {
        for (String grafia : List.of("S.PART.", "S/P", "S/Partido", "Sem partido", "SEM PARTIDO", "s/partido")) {
            assertEquals(Partidos.SEM_PARTIDO, Partidos.unificar(grafia, TSE), grafia);
        }
        assertEquals("SP", Partidos.unificar("SP", TSE), "duas letras sem separador não são a marca de sem partido");
        assertNull(Partidos.unificar(null, TSE));
        assertNull(Partidos.unificar("  ", TSE));
    }

    @Test
    void grafiaSemCorrespondenciaSaiComoACasaPublicaEEContada() {
        assertEquals("Partido Novo Exemplo", Partidos.unificar("Partido Novo Exemplo", TSE));
        assertFalse(Partidos.conhecida("Partido Novo Exemplo", TSE));
        assertTrue(Partidos.conhecida("União Brasil", TSE));
        assertTrue(Partidos.conhecida("S/P", TSE));
        assertTrue(Partidos.conhecida(null, TSE));

        List<Publicador.Perfil> perfis = new ArrayList<>();
        for (String partido : new String[] {"União", "UNIÃO", "UB", "PL", null, "Partido Novo Exemplo", "PSL"}) {
            Publicador.Perfil p = new Publicador.Perfil();
            p.partido = partido;
            perfis.add(p);
        }
        Partidos.Resultado resultado = Partidos.unificarPerfis(perfis, TSE);
        assertEquals(2, resultado.unificados(), "União e UB mudam; UNIÃO e PL já estavam na grafia do TSE");
        assertEquals(Map.of("Partido Novo Exemplo", 1, "PSL", 1), resultado.semCorrespondencia());
        assertEquals(List.of("UNIÃO", "UNIÃO", "UNIÃO", "PL"), perfis.subList(0, 4).stream().map(p -> p.partido).toList());
        assertNull(perfis.get(4).partido);
    }

    @Test
    void semOTseNormalizadoValeAGrafiaDeReservaDaTabela() {
        Map<String, String> vazio = Map.of();
        assertEquals("UNIÃO", Partidos.unificar("União Brasil", vazio));
        assertEquals("REPUBLICANOS", Partidos.unificar("REP", vazio));
        assertEquals("União", Partidos.unificar("União", vazio), "sem o TSE, a grafia da casa fica como veio");
    }
}
