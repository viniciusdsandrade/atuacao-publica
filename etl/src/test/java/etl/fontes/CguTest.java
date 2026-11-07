package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import etl.Json;
import etl.Registros.Prov;

class CguTest {

    private static final Prov PROV = new Prov(Cgu.FONTE_ID, "https://exemplo.invalid/cpgf/202506", Instant.parse("2026-11-02T00:00:00Z"));

    @Test
    void cartaoSomaSoOOrgaoPresidenciaIncluindoSigiloSemLerPortador() {
        String csv = "\"CÓDIGO ÓRGÃO SUPERIOR\";\"NOME ÓRGÃO SUPERIOR\";\"CÓDIGO ÓRGÃO\";\"NOME ÓRGÃO\";\"CÓDIGO UNIDADE GESTORA\";\"NOME UNIDADE GESTORA\";"
                + "\"ANO EXTRATO\";\"MÊS EXTRATO\";\"CPF PORTADOR\";\"NOME PORTADOR\";\"CNPJ OU CPF FAVORECIDO\";\"NOME FAVORECIDO\";\"TRANSAÇÃO\";\"DATA TRANSAÇÃO\";\"VALOR TRANSAÇÃO\"\r\n"
                + "\"20000\";\"Presidência da República\";\"20101\";\"Presidência da República\";\"110322\";\"GABINETE DE SEGURANCA INSTITUCIONAL/PR\";\"2025\";\"06\";\"***.111.111-**\";\"Fulano\";\"-11\";\"Sigiloso\";\"Informações protegidas por sigilo\";\"\";\"102,00\"\r\n"
                + "\"20000\";\"Presidência da República\";\"20101\";\"Presidência da República\";\"110322\";\"GABINETE DE SEGURANCA INSTITUCIONAL/PR\";\"2025\";\"06\";\"***.222.222-**\";\"Beltrano\";\"12345678000199\";\"POSTO X\";\"COMPRA A/V - R$ - APRES\";\"14/05/2025\";\"1.000,50\"\r\n"
                + "\"20000\";\"Presidência da República\";\"20415\";\"Empresa Brasil de Comunicação\";\"115406\";\"EBC\";\"2025\";\"06\";\"***.333.333-**\";\"Sicrano\";\"-11\";\"Sigiloso\";\"Informações protegidas por sigilo\";\"\";\"8,84\"\r\n";
        Cgu.Somas somas = new Cgu.Somas();
        Cgu.paraCada(csv, linha -> Cgu.somarCartao(linha, somas));
        List<Cgu.Agregado> agregados = somas.agregados(PROV);
        assertEquals(1, agregados.size());
        Cgu.Agregado a = agregados.getFirst();
        assertEquals("Cartão de pagamento: GABINETE DE SEGURANCA INSTITUCIONAL/PR", a.categoria());
        assertEquals(new BigDecimal("1102.50"), a.valor());
        assertEquals(2, a.quantidade());
        assertEquals(6, a.mes());
        assertEquals("BR", a.ente());
        String json = Json.arvore(agregados).toString();
        assertTrue(!json.contains("Fulano") && !json.contains("Beltrano") && !json.contains("111.111"));
    }

    private static final String CABECALHO_EMENDAS = "\"Código da Emenda\";\"Ano da Emenda\";\"Tipo de Emenda\";\"Código do Autor da Emenda\";"
            + "\"Nome do Autor da Emenda\";\"Número da emenda\";\"Localidade de aplicação do recurso\";\"Código Município IBGE\";\"Município\";"
            + "\"Código UF IBGE\";\"UF\";\"Região\";\"Código Função\";\"Nome Função\";\"Código Subfunção\";\"Nome Subfunção\";\"Código Programa\";"
            + "\"Nome Programa\";\"Código Ação\";\"Nome Ação\";\"Código Plano Orçamentário\";\"Nome Plano Orçamentário\";\"Valor Empenhado\";"
            + "\"Valor Liquidado\";\"Valor Pago\";\"Valor Restos A Pagar Inscritos\";\"Valor Restos A Pagar Cancelados\";\"Valor Restos A Pagar Pagos\"\r\n";

    /** Linha sintética do arquivo de emendas, com os campos que o ETL lê; os demais ficam como na fonte. */
    private static String emenda(String codigo, String ano, String tipo, String autor, String nome, String subfuncao, String empenhado) {
        return String.join(";", List.of(codigo, ano, tipo, autor, nome, "0001", "CAMPINAS - SP", "3509502", "CAMPINAS", "3500000",
                "SÃO PAULO", "Sudeste", "10", "Saúde", "301", subfuncao, "5119", "ATENÇÃO PRIMÁRIA À SAÚDE", "2E89",
                "INCREMENTO TEMPORÁRIO AO CUSTEIO", "0000", "SEM PLANO", empenhado, "100,00", "50,00", "0,00", "0,00", "0,00")
                .stream().map(c -> "\"" + c + "\"").toList()) + "\r\n";
    }

    @Test
    void emendaIndividualDesde2022AgrupaPeloCodigoDoAutorComONomeComoAFonteEscreve() {
        String csv = CABECALHO_EMENDAS
                + emenda("202640010001", "2026", "Emenda Individual - Transferências com Finalidade Definida", "4001", "FULANA EXEMPLO", "Atenção básica", "1.234.567,89")
                + emenda("202540010002", "2025", "Emenda Individual - Transferências Especiais", "4001", "FULANA EXEMPLO", "Sem informação", "500,00")
                + emenda("202640010003", "2026", "Emenda Individual - Transferências Especiais", "4001",
                        "PESSOA EXEMPLO (EX-PARLAMENTAR FULANA EXEMPLO, NOS TERMOS DO ART. 78 DA LDO)", "Atenção básica", "10,00")
                + emenda("202650000001", "2026", "Emenda de Bancada", "5000", "BANCADA DE EXEMPLO", "Atenção básica", "9.999,00")
                + emenda("202140010009", "2021", "Emenda Individual - Transferências com Finalidade Definida", "4001", "FULANA EXEMPLO", "Atenção básica", "1,00")
                + emenda("202440020001", "2024", "Emenda Individual - Transferências com Finalidade Definida", "4002", "BELTRANO EXEMPLO", "Atenção básica", "2,00");
        Prov prov = new Prov(Cgu.FONTE_ID, Cgu.EMENDAS_URL, Instant.parse("2026-11-06T00:00:00Z"));

        List<Cgu.AutorDeEmendas> autores = Cgu.emendasPorAutor(
                new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)), prov);

        assertEquals(List.of("4001", "4002"), autores.stream().map(Cgu.AutorDeEmendas::codigo).toList(), "bancada e 2021 ficam fora");
        Cgu.AutorDeEmendas fulana = autores.getFirst();
        assertEquals(List.of("FULANA EXEMPLO", "PESSOA EXEMPLO (EX-PARLAMENTAR FULANA EXEMPLO, NOS TERMOS DO ART. 78 DA LDO)"), fulana.nomes());
        assertEquals(List.of("202640010001", "202640010003", "202540010002"), fulana.emendas().stream().map(Cgu.Emenda::codigo).toList());
        Cgu.Emenda primeira = fulana.emendas().getFirst();
        assertNull(primeira.autorNaFonte(), "nome principal do código não se repete no registro");
        assertEquals(new BigDecimal("1234567.89"), primeira.valorEmpenhado());
        assertEquals(new BigDecimal("50.00"), primeira.valorPago());
        assertEquals("CAMPINAS - SP", primeira.localidade());
        assertEquals("PESSOA EXEMPLO (EX-PARLAMENTAR FULANA EXEMPLO, NOS TERMOS DO ART. 78 DA LDO)", fulana.emendas().get(1).autorNaFonte());
        assertNull(fulana.emendas().get(2).subfuncao(), "\"Sem informação\" da fonte vira nulo");
        assertEquals(2025, fulana.emendas().get(2).ano());
    }

    @Test
    void viagemRealizadaEntraNoMesDeInicioComDiariasLiquidasDeDevolucao() {
        String csv = "\"Identificador do processo de viagem\";\"Número da Proposta (PCDP)\";\"Situação\";\"Viagem Urgente\";\"Justificativa Urgência Viagem\";"
                + "\"Código do órgão superior\";\"Nome do órgão superior\";\"Código órgão solicitante\";\"Nome órgão solicitante\";\"CPF viajante\";\"Nome\";\"Cargo\";\"Função\";\"Descrição Função\";"
                + "\"Período - Data de início\";\"Período - Data de fim\";\"Destinos\";\"Motivo\";\"Valor diárias\";\"Valor passagens\";\"Valor devolução\";\"Valor outros gastos\"\r\n"
                + "\"1\";\"000001/25-1C\";\"Realizada\";\"SIM\";\"\";\"20000\";\"Presidência da República\";\"20101\";\"Presidência da República\";\"***.111.111-**\";\"Fulano\";\"\";\"CCX\";\"CCX\";\"14/01/2025\";\"20/01/2025\";\"Praia/Cabo Verde\";\"Fórum\";\"11039,88\";\"20231,46\";\"39,88\";\"165,16\"\r\n"
                + "\"2\";\"000002/25-1C\";\"Não realizada\";\"NAO\";\"\";\"20000\";\"Presidência da República\";\"20101\";\"Presidência da República\";\"***.222.222-**\";\"Beltrano\";\"\";\"\";\"\";\"03/02/2025\";\"04/02/2025\";\"Brasília/DF\";\"Reunião\";\"500,00\";\"0,00\";\"0,00\";\"0,00\"\r\n"
                + "\"3\";\"000003/25-1C\";\"Realizada\";\"NAO\";\"\";\"20000\";\"Presidência da República\";\"60000\";\"Gabinete da Vice-Presidência da República\";\"***.333.333-**\";\"Sicrano\";\"\";\"\";\"\";\"05/03/2025\";\"06/03/2025\";\"São Paulo/SP\";\"Evento\";\"400,00\";\"900,00\";\"0,00\";\"0,00\"\r\n";
        Cgu.Somas somas = new Cgu.Somas();
        Cgu.paraCada(csv, linha -> Cgu.somarViagem(linha, somas));
        List<Cgu.Agregado> agregados = somas.agregados(PROV);
        assertEquals(3, agregados.size());
        assertEquals(List.of("Diárias de viagem", "Outros gastos de viagem", "Passagens"), agregados.stream().map(Cgu.Agregado::categoria).sorted().toList());
        Cgu.Agregado diarias = agregados.stream().filter(a -> a.categoria().equals("Diárias de viagem")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("11000.00"), diarias.valor());
        assertEquals(1, diarias.mes());
        assertEquals(1, diarias.quantidade());
    }
}
