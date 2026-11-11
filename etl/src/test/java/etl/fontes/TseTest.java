package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import etl.Registros.Prov;

import org.junit.jupiter.api.Test;

class TseTest {

    @Test
    void eleicoesCobertasNaoEncolhemComOTempo() {
        assertEquals(List.of(2018, 2020, 2022, 2024, 2026), Tse.eleicoes(2026));
        assertEquals(List.of(2018, 2020, 2022, 2024, 2026), Tse.eleicoes(2027));
        assertEquals(List.of(2018, 2020, 2022, 2024, 2026, 2028, 2030), Tse.eleicoes(2030));
    }

    @Test
    void aceitaDeputadoEstadualParaUfsCobertas() {
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "BA")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "RJ")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "RS")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "CE")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "SC")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "GO")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "ES")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "PA")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "PE")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO DISTRITAL", "SG_UF", "DF")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "PR")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "SP")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "MG")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "AC")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "RR")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO ESTADUAL", "SG_UF", "AP")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "DEPUTADO FEDERAL", "SG_UF", "AP")));
    }

    @Test
    void cadaMunicipioComecaNaSuaPrimeiraEleicao() {
        // O piloto municipal entra desde 2020. A capital só desde 2024: em 2020 o eleito prefeito não completou o mandato.
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "62910", "ANO_ELEICAO", "2020")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "PREFEITO", "SG_UE", "62910", "ANO_ELEICAO", "2024")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072", "ANO_ELEICAO", "2020")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "PREFEITO", "SG_UE", "71072", "ANO_ELEICAO", "2020")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072", "ANO_ELEICAO", "2024")));
        assertTrue(Tse.cargoValido(Map.of("DS_CARGO", "VICE-PREFEITO", "SG_UE", "71072", "ANO_ELEICAO", "2024")));
    }

    @Test
    void municipioForaDaCoberturaOuSemDadosNaoEntra() {
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "99999", "ANO_ELEICAO", "2024")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "ANO_ELEICAO", "2024")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072")));
        assertFalse(Tse.cargoValido(Map.of("DS_CARGO", "VEREADOR", "SG_UE", "71072", "ANO_ELEICAO", "x")));
    }

    @Test
    void tituloLevaOCpfDeOutraEleicaoSoQuandoACorrespondenciaEInequivoca() {
        // Dados sintéticos. Em 2024 o CPF vem mascarado e o título vem completo.
        Map<String, Tse.Chave> chaves = new HashMap<>();
        Map<String, String> titulos = new HashMap<>();
        // Mesma pessoa em 2022 e 2024, com o nome civil grafado de outro jeito: o título faz a ponte.
        chaves.put("sq-a-2022", new Tse.Chave("12345678909", "FULANA DE TAL", "SP", "1970-01-01", "DEPUTADO FEDERAL"));
        chaves.put("sq-a-2024", new Tse.Chave(null, "FULANA DE TAL SOBRENOME", "SP", "1970-01-01", "VEREADOR"));
        titulos.put("sq-a-2022", "100000000001");
        titulos.put("sq-a-2024", "100000000001");
        // O mesmo título com dois CPFs (erro de digitação na fonte): ninguém herda CPF.
        chaves.put("sq-b-2022", new Tse.Chave("11111111111", "BELTRANO", "RJ", "1980-02-02", "DEPUTADO FEDERAL"));
        chaves.put("sq-b-2026", new Tse.Chave("22222222222", "BELTRANO", "RJ", "1980-02-02", "DEPUTADO FEDERAL"));
        chaves.put("sq-b-2024", new Tse.Chave(null, "BELTRANO", "RJ", "1980-02-02", "VEREADOR"));
        titulos.put("sq-b-2022", "200000000002");
        titulos.put("sq-b-2026", "200000000002");
        titulos.put("sq-b-2024", "200000000002");
        // O mesmo CPF com dois títulos: também fica de fora.
        chaves.put("sq-c-2018", new Tse.Chave("33333333333", "CICRANO", "MG", "1990-03-03", "SENADOR"));
        chaves.put("sq-c-2022", new Tse.Chave("33333333333", "CICRANO", "MG", "1990-03-03", "DEPUTADO FEDERAL"));
        chaves.put("sq-c-2024", new Tse.Chave(null, "CICRANO", "MG", "1990-03-03", "VEREADOR"));
        titulos.put("sq-c-2018", "300000000003");
        titulos.put("sq-c-2022", "300000000033");
        titulos.put("sq-c-2024", "300000000003");
        // Título igual com data de nascimento diferente: fica de fora.
        chaves.put("sq-d-2022", new Tse.Chave("44444444444", "DEUTRANO", "BA", "1960-04-04", "DEPUTADO FEDERAL"));
        chaves.put("sq-d-2024", new Tse.Chave(null, "DEUTRANO", "BA", "1961-04-04", "VEREADOR"));
        titulos.put("sq-d-2022", "400000000004");
        titulos.put("sq-d-2024", "400000000004");
        // Pessoa só com a eleição mascarada: segue sem CPF e não conta como ambígua.
        chaves.put("sq-e-2024", new Tse.Chave(null, "ESSETRANO", "SP", "1950-05-05", "VEREADOR"));
        titulos.put("sq-e-2024", "500000000005");

        Tse.PeloTitulo resultado = Tse.completarCpfPeloTitulo(chaves, titulos);

        assertEquals("12345678909", resultado.chaves().get("sq-a-2024").cpf());
        assertEquals("FULANA DE TAL SOBRENOME", resultado.chaves().get("sq-a-2024").nomeCivil());
        assertNull(resultado.chaves().get("sq-b-2024").cpf());
        assertNull(resultado.chaves().get("sq-c-2024").cpf());
        assertNull(resultado.chaves().get("sq-d-2024").cpf());
        assertNull(resultado.chaves().get("sq-e-2024").cpf());
        assertEquals("11111111111", resultado.chaves().get("sq-b-2022").cpf(), "candidatura com CPF próprio não muda");
        assertEquals(1, resultado.ligadas());
        assertEquals(3, resultado.ambiguas());
    }

    @Test
    void enderecoDeclaradoComTelefoneOuEmailNuncaEntra() {
        // Endereços fictícios. Link de WhatsApp ou Telegram por número leva ao telefone do candidato.
        assertTrue(Tse.temDadoPessoal("https://wa.me/5511999998888"));
        assertTrue(Tse.temDadoPessoal("https://api.whatsapp.com/send?phone=5511999998888"));
        assertTrue(Tse.temDadoPessoal("https://t.me/+5511999998888"));
        assertTrue(Tse.temDadoPessoal("https://chat.whatsapp.com/ConviteExemplo123"), "convite de grupo mostra o telefone de quem administra");
        assertTrue(Tse.temDadoPessoal("https://api.whatsapp.com/message/LINKEXEMPLO"));
        assertTrue(Tse.temDadoPessoal("HTTPS://WA.LINK/EXEMPLO"));
        assertTrue(Tse.temDadoPessoal("https://exemplo.invalid/contato?fone=+5511999998888"));
        assertTrue(Tse.temDadoPessoal("mailto:fulano@exemplo.invalid"));
        assertTrue(Tse.temDadoPessoal("fulano@exemplo.invalid"));
        assertFalse(Tse.temDadoPessoal("https://www.instagram.com/perfil.exemplo"));
        assertFalse(Tse.temDadoPessoal("https://www.whatsapp.com/channel/canalExemplo"), "canal público não leva a telefone");
        assertNull(Tse.enderecoDeclarado("https://wa.me/5511999998888"));
    }

    @Test
    void enderecoDeclaradoSemEsquemaGanhaHttpsETextoSemDominioFicaFora() {
        assertEquals("https://instagram.com/perfil.exemplo", Tse.enderecoDeclarado("instagram.com/perfil.exemplo"));
        assertEquals("https://www.site-exemplo.com.br", Tse.enderecoDeclarado(" www.site-exemplo.com.br "));
        assertEquals("http://site-exemplo.com.br/", Tse.enderecoDeclarado("http://site-exemplo.com.br/"), "esquema declarado fica");
        // O arquivo de 2026 traz endereços todos em maiúsculas: esquema e domínio não diferenciam, o caminho fica como veio.
        assertEquals("https://www.instagram.com/PERFIL.EXEMPLO", Tse.enderecoDeclarado("HTTPS://WWW.INSTAGRAM.COM/PERFIL.EXEMPLO"));
        assertEquals("https://site-exemplo.com.br:8080/Pagina?Q=1", Tse.enderecoDeclarado("HTTPS://SITE-EXEMPLO.COM.BR:8080/Pagina?Q=1"));
        assertNull(Tse.enderecoDeclarado("https://usuario@site-exemplo.com.br/"), "endereço com credencial embutida fica fora");
        assertNull(Tse.enderecoDeclarado("@perfil.exemplo"), "o site não adivinha a rede");
        assertNull(Tse.enderecoDeclarado("PERFIL EXEMPLO"));
        assertNull(Tse.enderecoDeclarado("https://site-exemplo.com.br/a b"));
        assertNull(Tse.enderecoDeclarado(""));
    }

    @Test
    void redeVemDoDominioEODemaisESite() {
        assertEquals("Instagram", Tse.rede("https://www.instagram.com/perfil.exemplo"));
        assertEquals("Facebook", Tse.rede("https://m.facebook.com/perfil.exemplo"));
        assertEquals("X (Twitter)", Tse.rede("https://x.com/perfil_exemplo"));
        assertEquals("X (Twitter)", Tse.rede("https://twitter.com/perfil_exemplo"));
        assertEquals("YouTube", Tse.rede("https://youtu.be/exemplo"));
        assertEquals("Site", Tse.rede("https://naoinstagram.com/perfil"), "domínio parecido não é a rede");
        assertEquals("Site", Tse.rede("https://www.site-exemplo.com.br"));
    }

    @Test
    void prestacaoDeContasLeOCsvBrasilNaEleicaoGeralEODaUfDosMunicipiosNaMunicipal() {
        assertEquals(List.of("receitas_candidatos_2026_BRASIL.csv", "despesas_contratadas_candidatos_2026_BRASIL.csv"),
                Tse.entradasDeContas(2026));
        assertEquals(List.of("receitas_candidatos_2024_SP.csv", "despesas_contratadas_candidatos_2024_SP.csv"),
                Tse.entradasDeContas(2024));
        // Município novo fora de SP precisa de outro recorte da prestação de contas.
        for (var municipio : Tse.UES_MUNICIPAIS.values()) {
            assertTrue(municipio.ibge().startsWith("35"), "município fora de SP: " + municipio.ibge());
        }
    }

    @Test
    void recorteGuardaSoLinhasCobertasEColunasUsadasSemDocumentoNemNomeDePessoaFisica(@org.junit.jupiter.api.io.TempDir java.nio.file.Path pasta)
            throws Exception {
        String csv = "\"SQ_CANDIDATO\";\"SQ_DESPESA\";\"DS_ORIGEM_DESPESA\";\"NR_CPF_CNPJ_FORNECEDOR\";\"NM_FORNECEDOR\";\"NM_FORNECEDOR_RFB\";"
                + "\"VR_DESPESA_CONTRATADA\";\"NR_CPF_CANDIDATO\";\"DS_COR_RACA\"\r\n"
                + "\"250000000001\";\"1\";\"Despesas com pessoal\";\"12345678909\";\"PESSOA FORNECEDORA EXEMPLO\";\"#NULO\";\"100,00\";\"98765432100\";\"COR\"\r\n"
                + "\"250000000001\";\"2\";\"Publicidade\";\"99999999000199\";\"GRAFICA EXEMPLO LTDA\";\"GRAFICA EXEMPLO\";\"50,00\";\"98765432100\";\"COR\"\r\n"
                + "\"250000000002\";\"3\";\"Publicidade\";\"99999999000199\";\"GRAFICA EXEMPLO LTDA\";\"GRAFICA EXEMPLO\";\"70,00\";\"11111111111\";\"COR\"\r\n";
        java.nio.file.Path destino = pasta.resolve("despesas.csv.gz");

        Tse.recortarCandidaturas(new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)), destino,
                java.util.Set.of("250000000001"), Tse.COLUNAS_DE_DESPESA);

        String recorte;
        try (var entrada = new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(destino))) {
            recorte = new String(entrada.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        String[] linhas = recorte.split("\r\n");
        assertEquals(3, linhas.length, "cabeçalho e as duas despesas da candidatura coberta");
        assertEquals("\"250000000001\";\"1\";\"Despesas com pessoal\";\"\";\"\";\"\";\"100,00\"", linhas[1]);
        assertTrue(linhas[2].contains("99999999000199") && linhas[2].contains("GRAFICA EXEMPLO"));
        assertFalse(recorte.contains("12345678909") || recorte.contains("PESSOA FORNECEDORA") || recorte.contains("98765432100")
                || recorte.contains("COR"), "CPF, nome de pessoa física e colunas não usadas ficam fora do cache");
    }

    private static Map<String, String> receita(String id, String fonte, String origem, String valor) {
        return Map.of("SQ_RECEITA", id, "DS_FONTE_RECEITA", fonte, "DS_ORIGEM_RECEITA", origem, "VR_RECEITA", valor,
                "NR_CPF_CNPJ_DOADOR", "12345678909", "NM_DOADOR", "PESSOA DOADORA EXEMPLO");
    }

    private static Map<String, String> despesa(String id, String tipo, String documento, String nome, String valor) {
        return Map.of("SQ_DESPESA", id, "DS_ORIGEM_DESPESA", tipo, "NR_CPF_CNPJ_FORNECEDOR", documento, "NM_FORNECEDOR", nome,
                "NM_FORNECEDOR_RFB", "#NULO", "VR_DESPESA_CONTRATADA", valor);
    }

    @Test
    void receitasSomamPorFonteEOrigemSemNomeNemCpfDeDoador() {
        Tse.Somas somas = new Tse.Somas();
        somas.receita(receita("1", "FUNDO ESPECIAL", "Recursos de partido político", "1.000,00"));
        somas.receita(receita("2", "OUTROS RECURSOS", "Recursos de pessoas físicas", "200,50"));
        somas.receita(receita("2", "OUTROS RECURSOS", "Recursos de pessoas físicas", "200,50"));
        somas.receita(receita("3", "#NULO", "Recursos de pessoas físicas", "99,50"));
        Prov prov = new Prov("tse-cdn", "https://exemplo.invalid/contas.zip#receitas.csv", java.time.Instant.parse("2026-11-06T00:00:00Z"));

        var receitas = somas.receitas(2026, "DEPUTADO FEDERAL", prov);

        assertEquals(new BigDecimal("1300.00"), receitas.total());
        assertEquals(3, receitas.quantidade(), "a receita repetida (mesmo SQ_RECEITA) conta uma vez");
        assertEquals("FUNDO ESPECIAL", receitas.porFonte().getFirst().categoria());
        assertEquals("(vazio na fonte)", receitas.porFonte().getLast().categoria());
        assertEquals(new BigDecimal("300.00"), receitas.porOrigem().stream()
                .filter(s -> s.categoria().equals("Recursos de pessoas físicas")).findFirst().orElseThrow().valor());
        String json = etl.Json.arvore(receitas).toString();
        assertFalse(json.contains("12345678909") || json.contains("PESSOA DOADORA"), "doador pessoa física não sai");
    }

    @Test
    void despesasListamSoFornecedorComCnpjComONomeSemCpf() {
        Tse.Somas somas = new Tse.Somas();
        somas.despesa(despesa("1", "Publicidade por materiais impressos", "99999999000199", "GRAFICA EXEMPLO LTDA", "500,00"));
        somas.despesa(despesa("2", "Publicidade por materiais impressos", "99999999000199", "GRAFICA EXEMPLO LTDA", "250,00"));
        somas.despesa(despesa("3", "Despesas com pessoal", "12345678909", "PESSOA FORNECEDORA EXEMPLO", "1.000,00"));
        somas.despesa(despesa("4", "Serviços prestados por terceiros", "88888888000188", "FULANO EXEMPLO 12345678909", "10,00"));
        for (int i = 0; i < 12; i++) {
            somas.despesa(despesa("x" + i, "Despesas com transporte", String.format("%014d", 1000 + i), "TRANSPORTE " + i, "1,00"));
        }
        Prov prov = new Prov("tse-cdn", "https://exemplo.invalid/contas.zip#despesas.csv", java.time.Instant.parse("2026-11-06T00:00:00Z"));

        var despesas = somas.despesas(2026, "DEPUTADO FEDERAL", prov);

        assertEquals(new BigDecimal("1772.00"), despesas.total());
        assertEquals(16, despesas.quantidade());
        assertEquals(Tse.MAIORES_FORNECEDORES, despesas.maioresFornecedores().size());
        var primeiro = despesas.maioresFornecedores().getFirst();
        assertEquals("99999999000199", primeiro.cnpj());
        assertEquals(new BigDecimal("750.00"), primeiro.valor());
        assertEquals("FULANO EXEMPLO", despesas.maioresFornecedores().get(1).nome(), "CPF no nome do MEI é removido");
        String json = etl.Json.arvore(despesas).toString();
        assertFalse(json.contains("12345678909") || json.contains("PESSOA FORNECEDORA"), "fornecedor pessoa física não sai por nome");
        assertEquals(new BigDecimal("1000.00"), despesas.porTipo().getFirst().valor(), "a soma por tipo inclui a pessoa física");
    }

    @Test
    void ueMunicipalTrazOCodigoIbgeDoEnte() {
        assertEquals("3509502", Tse.UES_MUNICIPAIS.get("62910").ibge());
        assertEquals("3550308", Tse.UES_MUNICIPAIS.get("71072").ibge());
    }
}
