package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.Despesa;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class AlerjTest {

    /** Orçamento mensal sintético do DOCIGP, com lançamentos embutidos, na forma da API. */
    private static final String ORCAMENTO = """
            {"id": 1, "month": "06", "budget": {"date": "2026-06-01T03:00:00.000000Z"},
             "entries": [
               {"date": "2026-06-30T16:34:21.000000Z", "value": "-4500.00", "cost_center_id": 21, "document_number": null,
                "to": "LOCADORA EXEMPLO", "provider": {"name": "LOCADORA EXEMPLO LTDA", "cpf_cnpj": "11.222.333/0001-81",
                "type": "CNPJ", "street": "RUA EXEMPLO", "zipcode": "00000-000"}},
               {"date": "2026-06-15T10:00:00.000000Z", "value": "-180.00", "cost_center_id": 23, "document_number": "77",
                "to": "FULANO DE TAL", "provider": {"name": "FULANO DE TAL", "cpf_cnpj": "123.456.789-01", "type": "CPF"}},
               {"date": "2026-06-30T18:00:00.000000Z", "value": "-900.00", "cost_center_id": 26,
                "to": "ALERJ", "provider": {"name": "ALERJ", "cpf_cnpj": "11.222.333/0001-81", "type": "CNPJ"}}]}
            """;

    @Test
    void orcamentoDoDocigpTrazSoDespesasComValorPositivo() {
        Map<String, Alerj.CentroDeCusto> centros = Map.of(
                "21", new Alerj.CentroDeCusto("VI.a", "VI.a - Locação ou fretamento de veículos automotores"),
                "23", new Alerj.CentroDeCusto("VII", "VII - Combustíveis e lubrificantes"),
                "26", new Alerj.CentroDeCusto("4", "4 - Devolução de saldo"));
        Prov prov = new Prov(Alerj.FONTE_DESPESAS, Alerj.API_DOCIGP + "/congressmen/1/legislatures/2/budgets?page=1",
                Instant.parse("2026-11-07T00:00:00Z"));

        List<Despesa> despesas = Alerj.despesasDoOrcamento(Json.FONTE.readTree(ORCAMENTO), centros, prov);

        // A devolução de saldo não é despesa e fica fora.
        assertEquals(2, despesas.size());
        Despesa locacao = despesas.get(0);
        assertEquals(2026, locacao.ano());
        assertEquals(6, locacao.mes());
        assertEquals(LocalDate.of(2026, 6, 30), locacao.data());
        assertEquals("Locação ou fretamento de veículos automotores", locacao.categoria());
        assertEquals(new java.math.BigDecimal("4500.00"), locacao.valor());
        assertEquals("11222333000181", locacao.fornecedorCnpj());
        assertEquals("LOCADORA EXEMPLO LTDA", locacao.fornecedorNome());
        Despesa combustivel = despesas.get(1);
        assertEquals("Combustíveis e lubrificantes", combustivel.categoria());
        assertEquals(null, combustivel.fornecedorCnpj());
        assertEquals("77", combustivel.documentoNumero());
    }

    @Test
    void formataNomeComPreposicoesEMaiusculas() {
        assertEquals("Alan Lopes", Alerj.formatarNome("ALAN LOPES"));
        assertEquals("Alexandre Knoploch", Alerj.formatarNome("ALEXANDRE KNOPLOCH"));
        assertEquals("Celia Jordão", Alerj.formatarNome("CELIA JORDÃO"));
        assertEquals("Dionisio Lins", Alerj.formatarNome("DIONISIO LINS"));
        assertEquals("Valdecy da Saúde", Alerj.formatarNome("VALDECY DA SAÚDE"));
        assertEquals("Carlos Minc", Alerj.formatarNome("CARLOS MINC"));
    }

    @Test
    void extraiDeputadosDeFragmentoHtml() {
        String html = """
                <div class="lista_deputados">
                    <h2>13&#186; Legislatura (2023 – 2027)<span> 70 deputados</span></h2>
                    <div class="controle_deputado">
                        <div class="imagem">
                            <a href="/Deputados/PerfilDeputado/505?Legislatura=20"><img src="/Uploads/PerfilDeputado/Imagem/24022023_133211AlanLopes.jpg" alt="ALAN LOPES"></a>
                        </div>
                        <div class="descricao">
                            <div class="partido">PL</div>
                            <div class="nome"><a href="/Deputados/PerfilDeputado/505?Legislatura=20">ALAN LOPES</a></div>
                        </div>
                        <span></span>
                    </div>
                    <div class="controle_deputado lider">
                        <div class="imagem">
                            <a href="/Deputados/PerfilDeputado/420?Legislatura=20"><img src="/Uploads/PerfilDeputado/Imagem/28012026_1149073x4.jpeg" alt="ALEXANDRE KNOPLOCH"></a>
                        </div>
                        <div class="descricao">
                            <div class="partido">PL</div>
                            <div class="nome"><a href="/Deputados/PerfilDeputado/420?Legislatura=20">ALEXANDRE KNOPLOCH</a></div>
                        </div>
                        <span></span>
                    </div>
                    <div class="controle_deputado">
                        <div class="imagem">
                            <a href="/Deputados/PerfilDeputado/417?Legislatura=20"><img src="/Uploads/PerfilDeputado/Imagem/chico 3 . 4.jpeg" alt="CHICO MACHADO"></a>
                        </div>
                        <div class="descricao">
                            <div class="partido">SOLIDARIEDADE</div>
                            <div class="nome"><a href="/Deputados/PerfilDeputado/417?Legislatura=20">CHICO MACHADO</a></div>
                        </div>
                        <span></span>
                    </div>
                </div>
                """;

        List<Alerj.DeputadoBruto> deputados = Alerj.extrairDeputadosDoHtml(html);
        assertEquals(3, deputados.size());

        Alerj.DeputadoBruto d1 = deputados.get(0);
        assertEquals("505", d1.id());
        assertEquals("ALAN LOPES", d1.nome());
        assertEquals("PL", d1.partido());
        assertEquals("https://www.alerj.rj.gov.br/Uploads/PerfilDeputado/Imagem/24022023_133211AlanLopes.jpg", d1.fotoUrl());

        Alerj.DeputadoBruto d2 = deputados.get(1);
        assertEquals("420", d2.id());
        assertEquals("ALEXANDRE KNOPLOCH", d2.nome());
        assertEquals("PL", d2.partido());
        assertEquals("https://www.alerj.rj.gov.br/Uploads/PerfilDeputado/Imagem/28012026_1149073x4.jpeg", d2.fotoUrl());

        Alerj.DeputadoBruto d3 = deputados.get(2);
        assertEquals("417", d3.id());
        assertEquals("CHICO MACHADO", d3.nome());
        assertEquals("https://www.alerj.rj.gov.br/Uploads/PerfilDeputado/Imagem/chico%203%20.%204.jpeg", d3.fotoUrl());
        assertEquals("https://www.alerj.rj.gov.br/Uploads/PerfilDeputado/Imagem/28012026_1149073x4.jpeg", d2.fotoUrl());
    }

    @Test
    void extraiPosseEAnteriorDeFragmentoHtml() {
        String htmlComPosse = """
                <div id="divAtuacaoAtual" class="controle_linha sanfona">
                    <h3>PARTIDO POLÍTICOS</h3>
                    <p class="margin_bottom_20">
                        Posse na ALERJ: PL - Data 11/01/2023
                    </p>
                </div>
                """;
        assertEquals(LocalDate.of(2023, 1, 11), Alerj.extrairPosseDoHtml(htmlComPosse));
        assertFalse(Alerj.temLegislaturaAnterior(htmlComPosse));

        String htmlComAnterior = """
                <div id="divAtuacaoAtual">
                    Posse na ALERJ: PODE - Data 08/01/2025
                </div>
                <div id="divAtuacao">
                    <h1>Legislaturas Anteriores</h1>
                    Posse na ALERJ: PSL - Data 29/01/2019- 12ª Legislatura
                </div>
                """;
        assertEquals(LocalDate.of(2025, 1, 8), Alerj.extrairPosseDoHtml(htmlComAnterior));
        assertTrue(Alerj.temLegislaturaAnterior(htmlComAnterior));
    }

    @Test
    void normalizaDeputadosComTempDir(@TempDir Path tempDir) throws Exception {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-05T12:00:00Z"), null);

        Path rawAlerj = ctx.raw().resolve("alerj");
        Files.createDirectories(rawAlerj.resolve("perfis"));

        String htmlDeputados = """
                <div class="lista_deputados">
                    <div class="controle_deputado">
                        <div class="imagem">
                            <a href="/Deputados/PerfilDeputado/505?Legislatura=20"><img src="/Uploads/PerfilDeputado/Imagem/505.jpg" alt="ALAN LOPES"></a>
                        </div>
                        <div class="descricao">
                            <div class="partido">PL</div>
                            <div class="nome"><a href="/Deputados/PerfilDeputado/505?Legislatura=20">ALAN LOPES</a></div>
                        </div>
                    </div>
                </div>
                """;
        Files.writeString(rawAlerj.resolve("deputados.html"), htmlDeputados);
        gravarMetadado(rawAlerj, "deputados.html", "https://www.alerj.rj.gov.br/Deputados/QuemSao");

        String htmlPerfil505 = """
                <div id="divAtuacaoAtual">
                    Posse na ALERJ: PL - Data 01/02/2023
                </div>
                """;
        Files.writeString(rawAlerj.resolve("perfis/505.html"), htmlPerfil505);
        gravarMetadado(rawAlerj, "perfis/505.html", "https://www.alerj.rj.gov.br/Deputados/PerfilDeputado/505?Legislatura=20");

        Alerj alerj = new Alerj();
        alerj.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alerj/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertEquals(1, pessoasNode.size());
        JsonNode p1 = pessoasNode.get(0);
        assertEquals("505", p1.get("id_fonte").stringValue());
        assertEquals("Alan Lopes", p1.get("nome").stringValue());
        assertEquals("PL", p1.get("partido").stringValue());
        assertEquals("deputado_estadual", p1.get("cargo").stringValue());
        assertEquals("RJ", p1.get("uf").stringValue());
        assertTrue(p1.get("em_exercicio").booleanValue());

        Path bloco505 = ctx.normalizado().resolve("alerj/blocos/505.json");
        assertTrue(Files.exists(bloco505));
        JsonNode b505 = Json.ler(bloco505);
        assertNotNull(b505.get("mandatos"));
        assertEquals(1, b505.get("mandatos").size());
        assertEquals("2023-02-01", b505.get("mandatos").get(0).get("inicio").stringValue());
        assertEquals("2027-01-31", b505.get("mandatos").get(0).get("fim").stringValue());
    }

    private static void gravarMetadado(Path pastaFonte, String relativo, String url) throws IOException {
        Path arquivo = pastaFonte.resolve(relativo);
        Path meta = arquivo.resolveSibling(arquivo.getFileName() + ".meta.json");
        var arq = new Coleta.Arquivo(url, "raw/alerj/" + relativo, "hash-teste", 100, Instant.now());
        Json.gravar(meta, arq, true);
    }
}
