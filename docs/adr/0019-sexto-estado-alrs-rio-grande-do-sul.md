# ADR-0019: Sexto estado: ALRS e Governo do Rio Grande do Sul

- Status: aceita
- Data: 2026-11-05

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais dos principais estados da federação.
Após a integração de São Paulo (ALESP, ADR-0013 e ADR-0014), Minas Gerais (ALMG, ADR-0015), Paraná (ALEP, ADR-0016), Bahia (ALBA, ADR-0017) e Rio de Janeiro (ALERJ, ADR-0018), a inclusão do Rio Grande do Sul (RS) complementa o núcleo da Região Sul e incorpora o quinto maior estado em população e economia do país.
O levantamento técnico das fontes oficiais do Rio Grande do Sul validou:
1. A Assembleia Legislativa do Estado do Rio Grande do Sul (ALRS) disponibiliza portal institucional aberto (`https://www.al.rs.gov.br/` e `https://ww4.al.rs.gov.br/`), acessível publicamente via HTTP/2 e HTTP/1.1 sem barreiras de autenticação nem chave de API.
2. A aplicação oficial da ALRS consome uma API de dados abertos estruturada em JSON servida em `https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados`, fornecendo a listagem exata dos 55 deputados estaduais em exercício na 56ª Legislatura (2023–2027) com identificadores numéricos oficiais inteiros (`idDeputado`, ex.: `2144`, `13`, `2145`), nomes parlamentares, siglas partidárias e URLs oficiais de fotografias em alta resolução no repositório de arquivos institucional (`https://ww2.al.rs.gov.br/filerepository/fotografias/...`).
3. As páginas institucionais de cada parlamentar (`https://ww4.al.rs.gov.br/deputados/{idDeputado}`) e de suas comissões parlamentares (`https://ww4.al.rs.gov.br/deputados/{idDeputado}/comissoes`) detalham a atuação parlamentar e composições legislativas.
4. No Poder Executivo estadual, o Governador Eduardo Leite (PSDB) exerce mandato até 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`rs-transparencia`, `https://transparencia.al.rs.gov.br/`) e Diário Oficial (`rs-diario-oficial`, `https://www.corag.rs.gov.br/`).

## Decisão

1. Expansão interestadual: inclusão do Rio Grande do Sul (UF: `RS`, ente: `RS`) na esfera estadual do projeto.
2. Identificador canônico do deputado estadual da ALRS: `alrs-{id}`, onde `{id}` é o identificador numérico oficial utilizado nas rotas e na API da Assembleia (ex.: `alrs-2144` para Adão Pretto Filho, `alrs-13` para Adolfo Brito).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alrs` em `schemas/politico.schema.json` com padrão `^[0-9]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alrs-portal`, `alrs-deputados`, `alrs-deputados-detalhes`, `rs-transparencia` e `rs-diario-oficial` em `contrib/fontes.yaml`.
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, endereços de gabinete e e-mails institucionais são descartados durante a extração e normalização, em estrito cumprimento a `AGENTS.md` e verificação com `Guarda`.
   - Nomes civis, nomes de urna e siglas partidárias são utilizados para vinculação em memória com as bases de candidaturas do TSE.
7. Vínculo determinístico com o TSE:
   - Inclusão do Rio Grande do Sul (`RS`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018, 2022 e 2026.
   - Vinculação por nome de urna, nome civil, casamento por palavras e remoção de títulos honoríficos e profissionais (ex.: "Dr.", "Prof.") com o TSE.
   - Parlamentares da ALRS com mandatos anteriores na Câmara Federal ou Senado são fundidos deterministicamente no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALRS (`Alrs.java`):
   - Coleta (`fetch`): armazena em `data/raw/alrs/deputados.json` a resposta da API oficial `https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados`.
   - Normalização (`normalize`): gera `data/normalizado/alrs/pessoas.json` e `blocos/{id}.json`.
9. Executivo estadual:
   - O Governador do Rio Grande do Sul é vinculado ao catálogo de portais oficiais (`rs-transparencia` e `rs-diario-oficial`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir seis unidades federativas essenciais: SP, MG, PR, BA, RJ e RS, ampliando a representatividade nacional com dois estados do Sul, três do Sudeste e um do Nordeste.
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
