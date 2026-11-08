package etl.fontes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import etl.Coleta;
import etl.Contexto;
import etl.Json;
import etl.Registros.OrgaoOuFrente;
import etl.Registros.Proposicao;
import etl.Registros.Prov;
import tools.jackson.databind.JsonNode;

class AlepeTest {

    private static final Prov PROV = new Prov(Alepe.FONTE_PROPOSICOES, Alepe.API_PROPOSICOES + "requerimentos/?ano=2025",
            Instant.parse("2026-11-07T12:00:00Z"));

    private static Map<String, String> slugsDeExemplo() {
        return Map.of(Alepe.chaveDeAutoria("Álvaro Porto"), "alvaro-porto",
                Alepe.chaveDeAutoria("Maria do Carmo"), "maria-do-carmo");
    }

    private static Map<String, List<Proposicao>> ler(String xml, Alepe.ListaDeProposicoes lista) throws XMLStreamException {
        return Alepe.proposicoesDaLista(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), lista, slugsDeExemplo(), PROV);
    }

    @Test
    void requerimentoFicaComCadaDeputadoAutorPeloNome() throws XMLStreamException {
        // Dado sintético no formato da API: a ementa vem em HTML, com entidades.
        String xml = """
                <?xml version='1.0' encoding='utf-8'?>
                <requerimentos>
                  <requerimento docid="1" numero="10" ano="2025" legislatura="VIGÉSIMA" tipo="requerimento" subtipo="Voto de Aplauso" ementa="&lt;p style=&quot;text-align:justify&quot;&gt;Requeremos &amp;agrave; Mesa voto de aplauso&amp;nbsp;ao &lt;b&gt;13&amp;ordm;&lt;/b&gt; Batalh&amp;atilde;o.&lt;/p&gt;" dataPublicacao="03/02/2025"><autores><autor nome="Alvaro Porto" tipo="DEPUTADO"/><autor nome="Comissão de Justiça" tipo="COMISSAO"/><autor nome="Maria do Carmo" tipo="DEPUTADO"/></autores></requerimento>
                  <requerimento docid="2" numero="11" ano="2025" legislatura="VIGÉSIMA" tipo="requerimento" subtipo="Diversos" ementa="" dataPublicacao="04/02/2025"><autores><autor nome="Ex Deputado" tipo="DEPUTADO"/></autores></requerimento>
                </requerimentos>
                """;
        Map<String, List<Proposicao>> porSlug = ler(xml, Alepe.LISTAS.get(2));

        assertEquals(2, porSlug.size(), "autor sem perfil em exercício fica fora");
        Proposicao doPrimeiro = porSlug.get("alvaro-porto").getFirst();
        assertEquals("REQUERIMENTO", doPrimeiro.tipo());
        assertEquals("REQUERIMENTO 10/2025", doPrimeiro.identificacao());
        assertEquals("Requeremos à Mesa voto de aplauso ao 13º Batalhão.", doPrimeiro.ementa());
        assertEquals("2025-02-03", doPrimeiro.dataApresentacao());
        assertEquals("https://proposicoes.alepe.pe.gov.br/proposicoes/detalhes?docid=1&tipoprop=r", doPrimeiro.urlTexto());
        assertEquals(1, doPrimeiro.ordemAssinatura());
        assertEquals(3, porSlug.get("maria-do-carmo").getFirst().ordemAssinatura(), "a ordem conta todos os autores");
    }

    @Test
    void despesasDoDocumentoUsamARubricaDoAnoECortamCpf() {
        // Dado sintético no formato do portal: o pedido de março de 2024, com quatro notas.
        JsonNode documento = Json.FONTE.readTree("{\"docid\":\"5734\",\"numero\":\"193\",\"tipo\":\"P\",\"ano\":2024,\"total\":\"49830,98\"}");
        JsonNode notas = Json.FONTE.readTree("""
                [{"rubrica":4,"sequencial":1,"data":"28/03/2024","cnpj":"43683859000155","empresa":"amss consultoria em serviços e informações ltda","valor":"25844"},
                 {"rubrica":5,"sequencial":1,"data":"28/03/2024","cnpj":"34763489000175","empresa":"2A comunicação","valor":"9900"},
                 {"rubrica":5,"sequencial":2,"data":"28/03/2024","cnpj":"08079830000140","empresa":"NEWCOM","valor":"13914"},
                 {"rubrica":9,"sequencial":1,"data":"31/03/2024","cnpj":"12345678901","empresa":"Maria  Prestadora 123.456.789-01","valor":"172,98"}]
                """);
        JsonNode legenda = Json.FONTE.readTree("""
                [{"id":98,"nome_categoria":"CONSULTORIAS E TRABALHOS TÉCNICOS","numero_categoria":4,"numero_romano":"IV"},
                 {"id":99,"nome_categoria":"DIVULGAÇÃO DA ATIVIDADE PARLAMENTAR","numero_categoria":5,"numero_romano":"V"}]
                """);
        Prov prov = new Prov(Alepe.FONTE_DESPESAS, Alepe.API_VERBAS + "verbaindenizatorianotas.php?docid=5734", Instant.parse("2026-11-07T12:00:00Z"));

        Prov provDoDocumento = new Prov(Alepe.FONTE_DESPESAS, Alepe.API_VERBAS + "verbaindenizatoria.php?dep=4508&ano=2024&mes=3",
                Instant.parse("2026-11-07T12:00:00Z"));

        var despesas = Alepe.despesasDoDocumento(documento, notas, Alepe.rubricasDoAno(legenda), 2024, 3, prov, provDoDocumento);

        assertEquals(4, despesas.size());
        assertEquals("CONSULTORIAS E TRABALHOS TÉCNICOS", despesas.get(0).categoria());
        assertEquals("43683859000155", despesas.get(0).fornecedorCnpj());
        assertEquals(java.time.LocalDate.of(2024, 3, 28), despesas.get(0).data());
        assertEquals(2024, despesas.get(0).ano());
        assertEquals(3, despesas.get(0).mes());
        assertEquals("Pedido 193", despesas.get(0).documentoNumero());
        assertEquals("Rubrica 9", despesas.get(3).categoria(), "rubrica fora da legenda do ano");
        assertEquals(null, despesas.get(3).fornecedorCnpj(), "CPF no campo do CNPJ sai");
        assertEquals("Maria Prestadora", despesas.get(3).fornecedorNome(), "CPF no nome da empresa sai");
        assertEquals(new java.math.BigDecimal("49830.98"),
                despesas.stream().map(d -> d.valor()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                "a soma das notas é o total do documento");
    }

    @Test
    void documentoComTotalESemNotaViraLinhaUnica() {
        JsonNode complemento = Json.FONTE.readTree("{\"docid\":\"7001\",\"numero\":\"12\",\"tipo\":\"C\",\"total\":\"4209,67\"}");
        Prov provNotas = new Prov(Alepe.FONTE_DESPESAS, Alepe.API_VERBAS + "verbaindenizatorianotas.php?docid=7001", Instant.EPOCH);
        Prov provDocumento = new Prov(Alepe.FONTE_DESPESAS, Alepe.API_VERBAS + "verbaindenizatoria.php?dep=4508&ano=2024&mes=5", Instant.EPOCH);

        var despesas = Alepe.despesasDoDocumento(complemento, Json.FONTE.readTree("[]"), Map.of(), 2024, 5, provNotas, provDocumento);

        assertEquals(1, despesas.size());
        assertEquals(Alepe.SEM_DETALHAMENTO, despesas.getFirst().categoria());
        assertEquals(new java.math.BigDecimal("4209.67"), despesas.getFirst().valor());
        assertEquals("Complemento 12", despesas.getFirst().documentoNumero());
        assertEquals(null, despesas.getFirst().fornecedorNome());
        assertEquals(provDocumento, despesas.getFirst().prov(), "a fonte é o documento, que traz o total");
        assertEquals(0, Alepe.despesasDoDocumento(Json.FONTE.readTree("{\"docid\":\"7002\",\"numero\":\"13\",\"tipo\":\"P\",\"total\":\"0\"}"),
                Json.FONTE.readTree("[]"), Map.of(), 2024, 5, provNotas, provDocumento).size(), "total zero não gera linha");
    }

    @Test
    void idDoPortalSoVaiParaNomeSemHomonimo() {
        JsonNode lista = Json.FONTE.readTree("""
                [{"id":"4508","nome":"Abimael Santos","partido":"PL","email":"x@example.org"},
                 {"id":"4496","nome":"João Paulo","partido":"PT"},
                 {"id":"9001","nome":"Zé Homônimo","partido":"PP"},
                 {"id":"9002","nome":"Ze Homonimo","partido":"PSD"}]
                """);
        Map<String, String> slugPorChave = Map.of(
                Alepe.chaveDeAutoria("Abimael Santos"), "abimael-santos",
                Alepe.chaveDeAutoria("João Paulo do PT"), "joao-paulo-do-pt",
                Alepe.chaveDeAutoria("Zé Homônimo"), "ze-homonimo");

        Map<String, String> ids = Alepe.idsDoPortalPorSlug(lista, slugPorChave);

        assertEquals(Map.of("abimael-santos", "4508"), ids,
                "sem nome igual não há id, e nome repetido na lista do portal fica fora");
    }

    @Test
    void projetoUsaOTipoDaFonte() throws XMLStreamException {
        String xml = """
                <?xml version='1.0' encoding='utf-8'?>
                <projetos>
                  <projeto docid="13539" numero="3" ano="2024" legislatura="VIGÉSIMA " tipo="PROJETO DE LEI ORDINÁRIA" subtipo="" ementa="Institui a semana estadual de exemplo." dataPublicacao="14/06/2024"><autores><autor nome="Maria do Carmo" tipo="DEPUTADO"/></autores></projeto>
                </projetos>
                """;
        Proposicao projeto = ler(xml, Alepe.LISTAS.getFirst()).get("maria-do-carmo").getFirst();
        assertEquals("PROJETO DE LEI ORDINÁRIA 3/2024", projeto.identificacao());
        assertEquals("Institui a semana estadual de exemplo.", projeto.ementa());
        assertEquals("https://proposicoes.alepe.pe.gov.br/proposicoes/detalhes?docid=13539&tipoprop=p", projeto.urlTexto());
    }

    @Test
    void formataNomeComPreposicoes() {
        assertEquals("Abimael Santos", Alepe.formatarNome("ABIMAEL SANTOS"));
        assertEquals("Álvaro Porto", Alepe.formatarNome("ÁLVARO PORTO"));
        assertEquals("Maria do Carmo", Alepe.formatarNome("MARIA DO CARMO"));
        assertEquals("Claudiano Martins Filho", Alepe.formatarNome("Claudiano Martins Filho"));
    }

    @Test
    void normalizaUrlFotoEPartido() {
        assertEquals("https://www.alepe.pe.gov.br/wp-content/foto.webp",
                Alepe.normalizarUrlFoto("/wp-content/foto.webp"));
        assertEquals("https://www.alepe.pe.gov.br/wp-content/foto%20perfil.webp",
                Alepe.normalizarUrlFoto("https://www.alepe.pe.gov.br/wp-content/foto perfil.webp"));
        assertEquals("https://www.alepe.pe.gov.br/img.php?src=teste&scale=1",
                Alepe.normalizarUrlFoto("https://www.alepe.pe.gov.br/img.php?src=teste&amp;scale=1"));

        assertEquals("PL", Alepe.normalizarPartido("PL"));
        assertEquals("PODE", Alepe.normalizarPartido("Podemos"));
        assertEquals("UNIÃO", Alepe.normalizarPartido("União"));
        assertEquals("REPUBLICANOS", Alepe.normalizarPartido("Republicanos"));
        assertEquals("NOVO", Alepe.normalizarPartido("Novo"));
    }

    @Test
    void extraiMunicipioEUfDeNaturalidade() {
        String[] r1 = Alepe.extrairMunicipioUf("Recife - PE");
        assertEquals("Recife", r1[0]);
        assertEquals("PE", r1[1]);

        String[] r2 = Alepe.extrairMunicipioUf("Canhotinho (PE)");
        assertEquals("Canhotinho", r2[0]);
        assertEquals("PE", r2[1]);

        String[] r3 = Alepe.extrairMunicipioUf("Caruaru");
        assertEquals("Caruaru", r3[0]);
        assertEquals(null, r3[1]);
    }

    @Test
    void extraiDeputadosDoHtml() {
        String html = """
                <html>
                <body>
                  <select name="field-deputados" id="field-deputados">
                    <option value="/parlamentar/abimael-santos">Abimael Santos</option>
                    <option value="/parlamentar/dani-portela">Dani Portela</option>
                  </select>
                  <ul class="parlamentares-list-ul">
                    <li>
                      <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                        <figure class="figure figure-avatar">
                          <img src="https://www.alepe.pe.gov.br/wp-content/abimael.webp" />
                        </figure>
                        <div class="text">
                          <h2 class="title parlamentares-nome">Abimael Santos</h2>
                          <span class="subtitle parlamentares-partido">PL</span>
                        </div>
                      </a>
                    </li>
                    <li>
                      <a href='/parlamentar/dani-portela' class="parlamentares-list-a">
                        <figure class="figure figure-avatar">
                          <img src="https://www.alepe.pe.gov.br/wp-content/dani.webp" />
                        </figure>
                        <div class="text">
                          <h2 class="title parlamentares-nome">Dani Portela</h2>
                          <span class="subtitle parlamentares-partido">PT</span>
                        </div>
                      </a>
                    </li>
                  </ul>
                </body>
                </html>
                """;

        List<Alepe.DeputadoBruto> deputados = Alepe.extrairDeputadosDeHtml(html);
        assertEquals(2, deputados.size());

        Alepe.DeputadoBruto d1 = deputados.get(0);
        assertEquals("abimael-santos", d1.slug());
        assertEquals("Abimael Santos", d1.nome());
        assertEquals("PL", d1.partido());
        assertEquals("https://www.alepe.pe.gov.br/wp-content/abimael.webp", d1.fotoUrl());

        Alepe.DeputadoBruto d2 = deputados.get(1);
        assertEquals("dani-portela", d2.slug());
        assertEquals("Dani Portela", d2.nome());
        assertEquals("PT", d2.partido());
    }

    @Test
    void extraiComissoesEMembros() {
        String htmlComissao = """
                <h1 class="title news-title">
                  <img src='/wp-content/icone.png' />
                  Constituição, Legislação e Justiça
                </h1>
                <ul class="parlamentares-list-ul">
                  <li>
                    <a href='/parlamentar/coronel-alberto-feitosa' class="parlamentares-list-a">
                      <div class="text">
                        <h2 class="title parlamentares-nome">Coronel Alberto Feitosa</h2>
                        <span class="subtitle parlamentares-partido">PRESIDENTE</span>
                      </div>
                    </a>
                  </li>
                  <li>
                    <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                      <div class="text">
                        <h2 class="title parlamentares-nome">Abimael Santos</h2>
                        <span class="subtitle parlamentares-partido">VICE-PRESIDENTE</span>
                      </div>
                    </a>
                  </li>
                  <li>
                    <a href='/parlamentar/dani-portela' class="parlamentares-list-a">
                      <div class="text">
                        <h2 class="title parlamentares-nome">Dani Portela</h2>
                        <span class="subtitle parlamentares-partido"></span>
                      </div>
                    </a>
                  </li>
                </ul>
                """;

        Prov prov = new Prov("alepe-deputados-detalhes", "https://www.alepe.pe.gov.br/comissao/ccj/", Instant.parse("2026-11-06T12:00:00Z"));
        Map<String, List<OrgaoOuFrente>> mapa = new HashMap<>();
        Alepe.extrairMembrosComissao(htmlComissao, "https://www.alepe.pe.gov.br/comissao/ccj/", prov, mapa);

        assertEquals(3, mapa.size());
        assertEquals("Presidente", mapa.get("coronel-alberto-feitosa").get(0).papel());
        assertEquals("Vice-Presidente", mapa.get("abimael-santos").get(0).papel());
        assertEquals("Membro", mapa.get("dani-portela").get(0).papel());
        assertEquals("Constituição, Legislação e Justiça", mapa.get("abimael-santos").get(0).nome());
    }

    @Test
    void normalizaDeputadoComPerfilEComissoes(@TempDir Path tempDir) throws IOException {
        Path raiz = tempDir;
        Path dados = tempDir.resolve("data");
        Contexto ctx = new Contexto(raiz, dados, null, null, Instant.parse("2026-11-06T12:00:00Z"), null);

        Path rawAlepe = ctx.raw().resolve("alepe");
        Files.createDirectories(rawAlepe.resolve("perfis"));
        Files.createDirectories(rawAlepe.resolve("comissoes"));

        String listaHtml = """
                <select name="field-deputados" id="field-deputados">
                  <option value="/parlamentar/abimael-santos">Abimael Santos</option>
                </select>
                <select name="field-comissoes" id="field-comissoes">
                  <option value="/comissao/constituicao-legislacao-e-justica">Constituição, Legislação e Justiça</option>
                </select>
                <ul class="parlamentares-list-ul">
                  <li>
                    <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                      <figure class="figure figure-avatar">
                        <img src="https://www.alepe.pe.gov.br/wp-content/abimael.webp" />
                      </figure>
                      <div class="text">
                        <h2 class="title parlamentares-nome">Abimael Santos</h2>
                        <span class="subtitle parlamentares-partido">PL</span>
                      </div>
                    </a>
                  </li>
                </ul>
                """;
        Files.writeString(rawAlepe.resolve("deputados.html"), listaHtml);
        gravarMetadado(rawAlepe, "deputados.html", "https://www.alepe.pe.gov.br/parlamentares/");

        String perfilHtml = """
                <link rel='shortlink' href='https://www.alepe.pe.gov.br/?p=481457' />
                <h1 class="title">Abimael Santos</h1>
                <div class="resume">
                  <p>Abimael Santos é deputado estadual de Pernambuco eleito para o primeiro mandato.</p>
                </div>
                <div class="parlamentares-view-info">
                  <dl>
                    <dt>Nome civil:</dt>
                    <dd>Abimael dos Santos Pereira</dd>
                    <dt>Naturalidade:</dt>
                    <dd>Recife - PE</dd>
                    <dt>Profissão:</dt>
                    <dd>Eletricista</dd>
                    <dt>Redes sociais:</dt>
                    <dd>
                      <a href="https://www.instagram.com/abimaelsantos.pernambuco">Instagram</a>
                    </dd>
                  </dl>
                </div>
                """;
        Files.writeString(rawAlepe.resolve("perfis/abimael-santos.html"), perfilHtml);
        gravarMetadado(rawAlepe, "perfis/abimael-santos.html", "https://www.alepe.pe.gov.br/parlamentar/abimael-santos/");

        String comissaoHtml = """
                <h1 class="title news-title">Constituição, Legislação e Justiça</h1>
                <ul class="parlamentares-list-ul">
                  <li>
                    <a href='/parlamentar/abimael-santos' class="parlamentares-list-a">
                      <h2 class="title parlamentares-nome">Abimael Santos</h2>
                      <span class="subtitle parlamentares-partido">VICE-PRESIDENTE</span>
                    </a>
                  </li>
                </ul>
                """;
        Files.writeString(rawAlepe.resolve("comissoes/constituicao-legislacao-e-justica.html"), comissaoHtml);
        gravarMetadado(rawAlepe, "comissoes/constituicao-legislacao-e-justica.html", "https://www.alepe.pe.gov.br/comissao/constituicao-legislacao-e-justica/");

        Alepe alepe = new Alepe();
        alepe.normalize(ctx);

        Path pessoasJson = ctx.normalizado().resolve("alepe/pessoas.json");
        assertTrue(Files.exists(pessoasJson));

        JsonNode pessoasNode = Json.ler(pessoasJson);
        assertTrue(pessoasNode.isArray());
        assertEquals(1, pessoasNode.size());

        JsonNode dep = pessoasNode.get(0);
        assertEquals("alepe", dep.get("fonte").stringValue());
        assertEquals("abimael-santos", dep.get("id_fonte").stringValue());
        assertEquals("Abimael Santos", dep.get("nome").stringValue());
        assertEquals("deputado_estadual", dep.get("cargo").stringValue());
        assertEquals("estadual", dep.get("esfera").stringValue());
        assertEquals("PE", dep.get("ente").stringValue());
        assertEquals("PL", dep.get("partido").stringValue());

        Path blocoJson = ctx.normalizado().resolve("alepe/blocos/abimael-santos.json");
        assertTrue(Files.exists(blocoJson));

        JsonNode bloco = Json.ler(blocoJson);
        JsonNode mandatos = bloco.get("mandatos");
        assertNotNull(mandatos);
        assertEquals(1, mandatos.size());

        JsonNode m1 = mandatos.get(0);
        assertEquals("deputado_estadual", m1.get("cargo").stringValue());
        assertEquals(20, m1.get("legislatura").asInt());
        assertEquals("Em exercício", m1.get("situacao").stringValue());

        JsonNode orgaos = bloco.get("orgaos_e_frentes");
        assertNotNull(orgaos);
        assertEquals(1, orgaos.size());
        assertEquals("Constituição, Legislação e Justiça", orgaos.get(0).get("nome").stringValue());
        assertEquals("Vice-Presidente", orgaos.get(0).get("papel").stringValue());

        assertEquals("Abimael dos Santos Pereira", bloco.get("nome_civil").stringValue());
        assertTrue(bloco.get("biografia").stringValue().contains("Abimael Santos é deputado estadual"));
    }

    private static void gravarMetadado(Path dir, String arquivo, String url) throws IOException {
        Path meta = dir.resolve(arquivo + ".meta.json");
        Coleta.Arquivo arq = new Coleta.Arquivo(url, "raw/alepe/" + arquivo, "abc", 100L, Instant.parse("2026-11-06T12:00:00Z"));
        Json.gravar(meta, arq, true);
    }
}
