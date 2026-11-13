package etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Publicador.Perfil;
import etl.Registros.Candidatura;
import etl.Registros.Prov;
import etl.Registros.FonteUsada;
import etl.fontes.Cgu;
import etl.fontes.Tse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Testes sintéticos das regras de vínculo entre casas legislativas, TSE e emendas CGU. */
class VinculoTest {

    private static final Instant CAPTURA = Instant.parse("2026-11-12T12:00:00Z");
    private static final Prov PROV = new Prov("tse-cdn", "https://exemplo.invalid/cand.zip", CAPTURA);

    @Test
    void senadorSemCandidaturaAoSenadoVinculaPorNomeENascimentoUnicoEFundeComCamara(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.pessoa("camara", "1001", "Fulano Federal", "deputado_federal", "SP", true, "2023-02-01", "2027-01-31");
        f.camaraDetalhe("1001", "FULANO DA SILVA", "11122233344");

        f.pessoa("senado", "5001", "Fulano Senador", "senador", "BR", true, "2026-06-01", "2027-01-31");
        f.senadoDetalhe("5001", "FULANO DA SILVA", "1975-03-10");

        // Outro senador com nome civil e nascimento idênticos a dois CPFs distintos: não deve vincular por ambiguidade.
        f.pessoa("senado", "5002", "Homonimo Ambiguo", "senador", "BR", true, "2023-02-01", "2027-01-31");
        f.senadoDetalhe("5002", "JOSE HOMONIMO", "1980-01-01");

        f.candidatura(2022, "202201", "DEPUTADO FEDERAL", "SP", "FULANO FEDERAL", "AAA", true,
                "FULANO DA SILVA", "11122233344", "10/03/1975");
        f.candidatura(2022, "202202", "DEPUTADO FEDERAL", "RJ", "JOSE UM", "AAA", false,
                "JOSE HOMONIMO", "22233344455", "01/01/1980");
        f.candidatura(2022, "202203", "DEPUTADO FEDERAL", "MG", "JOSE DOIS", "BBB", false,
                "JOSE HOMONIMO", "33344455566", "01/01/1980");

        List<Perfil> perfis = f.vincular();
        Perfil fundido = porId(perfis, "camara", "1001");
        assertNotNull(fundido);
        assertEquals("5001", fundido.ids.get("senado"));
        assertTrue(fundido.aliases.contains("sen-5001"));
        assertTrue(fundido.tse.containsKey("2022"));
        assertEquals("202201", fundido.tse.get("2022"));
        assertTrue(fundido.nomesParlamentares.contains("Fulano Federal"));
        assertTrue(fundido.nomesParlamentares.contains("Fulano Senador"));

        Perfil ambiguo = porId(perfis, "senado", "5002");
        assertNotNull(ambiguo);
        assertTrue(ambiguo.tse.isEmpty(), "Par (nome civil, nascimento) com dois CPFs não vincula");
    }

    @Test
    void estadualPrefereCandidaturaEleitaDoCicloVigenteAntesDeSuplenteFuturo(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.pessoa("alesc", "paulinha", "Paulinha", "deputado_estadual", "SC", true, "2023-02-01", "2027-01-31");

        f.candidatura(2022, "240001", "DEPUTADO ESTADUAL", "SC", "PAULINHA", "AAA", true,
                "ANA PAULA SILVA", "44455566677", "05/05/1978");
        f.candidatura(2026, "240002", "DEPUTADO ESTADUAL", "SC", "PAULINHA", "BBB", false,
                "PAULA OUTRA PESSOA", "55566677788", "09/09/1982");

        List<Perfil> perfis = f.vincular();
        Perfil p = porId(perfis, "alesc", "paulinha");
        assertNotNull(p);
        assertEquals("240001", p.tse.get("2022"));
        assertNull(p.tse.get("2026"), "Candidatura de 2026 de outra pessoa com mesmo nome de urna não vence eleita de 2022");
    }

    @Test
    void pontuacaoETitulosVinculamTseSemAmbiguidade(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.pessoa("alrs", "10", "Neri, O Carteiro", "deputado_estadual", "RS", true, "2023-02-01", "2027-01-31");
        f.pessoa("ales", "20", "Engenheiro José Esmeraldo", "deputado_estadual", "ES", true, "2023-02-01", "2027-01-31");
        f.pessoa("alep", "30", "Doutor Antenor", "deputado_estadual", "PR", true, "2023-02-01", "2027-01-31");
        f.pessoa("alep", "31", "Doutor Ambiguo", "deputado_estadual", "PR", true, "2023-02-01", "2027-01-31");

        f.candidatura(2022, "310001", "DEPUTADO ESTADUAL", "RS", "NERI O CARTEIRO", "AAA", true,
                "NERI SILVA", "10101010101", "01/02/1970");
        f.candidatura(2022, "320001", "DEPUTADO ESTADUAL", "ES", "JOSE ESMERALDO", "AAA", true,
                "JOSE ESMERALDO SANTOS", "20202020202", "02/03/1965");
        f.candidatura(2022, "330001", "DEPUTADO ESTADUAL", "PR", "DR. ANTENOR", "AAA", true,
                "ANTENOR LIMA", "30303030303", "03/04/1960");
        // Duas pessoas diferentes no PR cujo nome sem título colide em "AMBIGUO": não vincula via fallback sem título.
        f.candidatura(2022, "330002", "DEPUTADO ESTADUAL", "PR", "DR. AMBIGUO", "AAA", true,
                "AMBIGUO UM", "40404040404", "04/05/1971");
        f.candidatura(2022, "330003", "DEPUTADO ESTADUAL", "PR", "PROF. AMBIGUO", "BBB", false,
                "AMBIGUO DOIS", "50505050505", "05/06/1972");

        List<Perfil> perfis = f.vincular();
        assertEquals("310001", porId(perfis, "alrs", "10").tse.get("2022"));
        assertEquals("320001", porId(perfis, "ales", "20").tse.get("2022"));
        assertEquals("330001", porId(perfis, "alep", "30").tse.get("2022"));
        assertTrue(porId(perfis, "alep", "31").tse.isEmpty(), "Fallback sem título em ALEP não deve chutar entre dois candidatos");
    }

    @Test
    void emendasVinculamPorNomesAposFusaoCivilTituloEDesambiguacaoPorMandatoFederal(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        // 1. Nome parlamentar alternativo após fusão Câmara + Senado.
        f.pessoa("camara", "101", "Fulano Federal", "deputado_federal", "SP", false, "2019-02-01", "2023-01-31");
        f.camaraDetalhe("101", "FULANO OLIVEIRA", "60606060606");
        f.pessoa("senado", "501", "Fulano Senador", "senador", "BR", true, "2023-02-01", "2031-01-31");
        f.senadoDetalhe("501", "FULANO OLIVEIRA", "1970-08-15");
        f.candidatura(2022, "5012022", "SENADOR", "SP", "FULANO SENADOR", "AAA", true,
                "FULANO OLIVEIRA", "60606060606", "15/08/1970");

        // 2. Fallback por nome civil e por título.
        f.pessoa("camara", "102", "Beltrano", "deputado_federal", "PR", true, "2023-02-01", "2027-01-31");
        f.camaraDetalhe("102", "BELTRANO SOUZA JUNIOR", "70707070707");

        // 3. Homônimos em períodos distintos: mandato vigente no ano da emenda desambigua.
        f.pessoa("camara", "201", "Homonimo Silva", "deputado_federal", "PI", false, "2019-02-01", "2023-01-31");
        f.camaraDetalhe("201", "HOMONIMO SILVA ANTIGO", "80808080808");
        f.pessoa("camara", "202", "Homonimo Silva", "deputado_federal", "PI", true, "2023-02-01", "2027-01-31");
        f.camaraDetalhe("202", "HOMONIMO SILVA NOVO", "90909090909");

        // 4. Homônimos com mandatos sobrepostos no ano da emenda: continua ambíguo e não atribui.
        f.pessoa("camara", "301", "Duplo Caso", "deputado_federal", "RJ", true, "2023-02-01", "2027-01-31");
        f.camaraDetalhe("301", "DUPLO UM", "12121212121");
        f.pessoa("camara", "302", "Duplo Caso", "deputado_federal", "MG", true, "2023-02-01", "2027-01-31");
        f.camaraDetalhe("302", "DUPLO DOIS", "23232323232");

        f.emenda("A1", List.of("FULANO SENADOR"), 2024, "2024A1");
        f.emenda("A2", List.of("BELTRANO SOUZA JUNIOR"), 2024, "2024A2");
        f.emenda("A3", List.of("DR. BELTRANO"), 2025, "2025A3");
        f.emenda("A4", List.of("Homonimo Silva"), 2025, "2025A4");
        f.emenda("A5", List.of("Duplo Caso"), 2025, "2025A5");

        List<Perfil> perfis = f.vincular();
        Perfil p101 = porId(perfis, "camara", "101");
        assertEquals(List.of("2024A1"), codigosEmenda(p101));

        Perfil p102 = porId(perfis, "camara", "102");
        assertEquals(List.of("2024A2", "2025A3"), codigosEmenda(p102));

        Perfil p201 = porId(perfis, "camara", "201");
        Perfil p202 = porId(perfis, "camara", "202");
        assertTrue(codigosEmenda(p201).isEmpty());
        assertEquals(List.of("2025A4"), codigosEmenda(p202));

        assertTrue(codigosEmenda(porId(perfis, "camara", "301")).isEmpty());
        assertTrue(codigosEmenda(porId(perfis, "camara", "302")).isEmpty());
    }

    private static Perfil porId(List<Perfil> perfis, String casa, String id) {
        for (Perfil p : perfis) {
            if (id.equals(p.ids.get(casa))) {
                return p;
            }
        }
        return null;
    }

    private static List<String> codigosEmenda(Perfil p) {
        List<String> codigos = new ArrayList<>();
        for (JsonNode n : p.bloco("emendas")) {
            codigos.add(n.get("codigo").stringValue());
        }
        codigos.sort(null);
        return codigos;
    }

    private static final class Fixture {
        private final Path raiz;
        private final Path dados;
        private final Contexto ctx;
        private final Map<String, ArrayNode> pessoasPorCasa = new java.util.LinkedHashMap<>();
        private final List<Tse.Linha> linhasTse = new ArrayList<>();
        private final Map<String, List<String>> csvPorAno = new java.util.LinkedHashMap<>();
        private final List<Cgu.AutorDeEmendas> autoresEmendas = new ArrayList<>();

        Fixture(Path raiz) throws IOException {
            this.raiz = raiz;
            this.dados = raiz.resolve("data");
            Files.createDirectories(raiz.resolve("contrib"));
            Files.writeString(raiz.resolve("contrib").resolve("mandatos.yaml"), "[]\n");
            this.ctx = new Contexto(raiz, dados, null, null, CAPTURA, null);
        }

        void pessoa(String casa, String id, String nome, String cargo, String uf, boolean emExercicio,
                String inicio, String fim) throws IOException {
            Path dir = ctx.normalizado().resolve(casa);
            Files.createDirectories(dir.resolve("blocos"));

            ObjectNode pessoa = Json.SAIDA.createObjectNode();
            pessoa.put("fonte", casa);
            pessoa.put("id_fonte", id);
            pessoa.put("nome", nome);
            pessoa.put("cargo", cargo);
            pessoa.put("esfera", "camara".equals(casa) || "senado".equals(casa) ? "federal" : "estadual");
            pessoa.put("ente", "senado".equals(casa) ? "BR" : uf);
            pessoa.put("uf", uf);
            pessoa.put("partido", "AAA");
            pessoa.put("situacao", emExercicio ? "Exercício" : "Fim de mandato");
            pessoa.put("em_exercicio", emExercicio);
            pessoa.set("links", Json.SAIDA.createArrayNode());
            pessoasPorCasa.computeIfAbsent(casa, k -> Json.SAIDA.createArrayNode()).add(pessoa);
            Json.gravar(dir.resolve("pessoas.json"), pessoasPorCasa.get(casa));

            ObjectNode mandato = Json.SAIDA.createObjectNode();
            mandato.put("cargo", cargo);
            mandato.put("esfera", pessoa.get("esfera").stringValue());
            mandato.put("ente", pessoa.get("ente").stringValue());
            mandato.put("uf", uf);
            mandato.put("inicio", inicio);
            mandato.put("fim", fim);
            mandato.put("situacao", emExercicio ? "Exercício" : "Fim de mandato");
            mandato.put("fonte_id", casa + "-teste");
            mandato.put("url_origem", "https://exemplo.invalid/" + casa + "/" + id);
            mandato.put("capturado_em", CAPTURA.toString());

            ObjectNode blocos = Json.SAIDA.createObjectNode();
            ArrayNode mandatos = Json.SAIDA.createArrayNode().add(mandato);
            blocos.set("mandatos", mandatos);
            blocos.set("fontes", Json.SAIDA.valueToTree(Registros.fontesDosBlocos(blocos)));
            Json.gravar(dir.resolve("blocos").resolve(id + ".json"), blocos);
        }

        void camaraDetalhe(String id, String nomeCivil, String cpf) throws IOException {
            ObjectNode root = Json.SAIDA.createObjectNode();
            ObjectNode dadosDep = Json.SAIDA.createObjectNode();
            dadosDep.put("nomeCivil", nomeCivil);
            dadosDep.put("cpf", cpf);
            root.set("dados", dadosDep);
            gravarJsonRaw("camara", "deputados/" + id + ".json", "https://exemplo.invalid/camara/" + id, root);
        }

        void senadoDetalhe(String id, String nomeCompleto, String nascimento) throws IOException {
            ObjectNode ident = Json.SAIDA.createObjectNode().put("NomeCompletoParlamentar", nomeCompleto);
            ObjectNode basicos = Json.SAIDA.createObjectNode().put("DataNascimento", nascimento);
            ObjectNode parl = Json.SAIDA.createObjectNode();
            parl.set("IdentificacaoParlamentar", ident);
            parl.set("DadosBasicosParlamentar", basicos);
            ObjectNode root = Json.SAIDA.createObjectNode();
            root.set("DetalheParlamentar", Json.SAIDA.createObjectNode().set("Parlamentar", parl));
            gravarJsonRaw("senado", "senador/" + id + ".json", "https://exemplo.invalid/senado/" + id, root);
        }

        void candidatura(int ano, String sq, String cargo, String uf, String nomeUrna, String partido, boolean eleito,
                String nomeCivil, String cpf, String nascimentoBr) {
            Candidatura c = new Candidatura(ano, 1, cargo, uf, nomeUrna, partido, "1010", "APTO",
                    eleito ? "ELEITO POR QP" : "SUPLENTE", null, null, null, List.of(), PROV);
            linhasTse.add(new Tse.Linha(sq, ano, cargo, uf, nomeUrna, partido, eleito, c));
            String linhaCsv = String.join(";", q(sq), q(cargo), q(uf), q(nomeCivil), q(cpf), q("123456789012"), q(nascimentoBr));
            csvPorAno.computeIfAbsent(String.valueOf(ano), k -> new ArrayList<>()).add(linhaCsv);
        }

        void emenda(String autorCodigo, List<String> nomes, int ano, String codigoEmenda) {
            Prov provCgu = new Prov("cgu-transparencia", "https://exemplo.invalid/emendas", CAPTURA);
            Cgu.Emenda e = new Cgu.Emenda(ano, "Emenda Individual", codigoEmenda, "0001", null, "BR",
                    "Saúde", "Atenção Básica", "Prog", "Acao", new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, provCgu);
            autoresEmendas.add(new Cgu.AutorDeEmendas(autorCodigo, nomes, List.of(e)));
        }

        List<Perfil> vincular() throws IOException {
            Files.createDirectories(ctx.normalizado().resolve("tse"));
            Json.gravar(ctx.normalizado().resolve("tse").resolve("candidaturas.json"), linhasTse);
            for (var e : csvPorAno.entrySet()) {
                gravarTseZip(e.getKey(), e.getValue());
            }
            if (!autoresEmendas.isEmpty()) {
                Files.createDirectories(ctx.normalizado().resolve("cgu"));
                Json.gravar(ctx.normalizado().resolve("cgu").resolve("emendas.json"), autoresEmendas);
            }
            return Vinculo.perfis(ctx, new Catalogo());
        }

        private void gravarJsonRaw(String fonte, String rel, String url, JsonNode root) throws IOException {
            Path destino = ctx.raw().resolve(fonte).resolve(rel);
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, Json.SAIDA.writeValueAsString(root));
            Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/" + fonte + "/" + rel, "abc", 10L, CAPTURA);
            Json.gravar(destino.resolveSibling(destino.getFileName() + ".meta.json"), arq, true);
        }

        private void gravarTseZip(String ano, List<String> linhas) throws IOException {
            String header = "\"SQ_CANDIDATO\";\"DS_CARGO\";\"SG_UF\";\"NM_CANDIDATO\";\"NR_CPF_CANDIDATO\";"
                    + "\"NR_TITULO_ELEITORAL_CANDIDATO\";\"DT_NASCIMENTO\"";
            StringBuilder csv = new StringBuilder(header).append("\r\n");
            for (String l : linhas) {
                csv.append(l).append("\r\n");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("consulta_cand_" + ano + "_BRASIL.csv"));
                zip.write(csv.toString().getBytes(StandardCharsets.ISO_8859_1));
                zip.closeEntry();
            }
            String rel = "consulta_cand_" + ano + ".zip";
            Path destino = ctx.raw().resolve("tse").resolve(rel);
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes.toByteArray());
            Coleta.Arquivo arq = new Coleta.Arquivo("https://exemplo.invalid/" + rel, "raw/tse/" + rel,
                    "abc", bytes.size(), CAPTURA);
            Json.gravar(destino.resolveSibling(destino.getFileName() + ".meta.json"), arq, true);
        }

        private static String q(String valor) {
            return "\"" + valor + "\"";
        }
    }
}
