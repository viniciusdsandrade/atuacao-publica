# ADR-0017: Quarto estado: ALBA e Governo da Bahia

- Status: aceita
- Data: 2026-11-04

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais dos principais estados da federação.
Após a integração de São Paulo (ALESP, ADR-0013 e ADR-0014), Minas Gerais (ALMG, ADR-0015) e Paraná (ALEP, ADR-0016), a inclusão da Bahia (BA) marca o ingresso formal da plataforma na região Nordeste e no quarto maior colégio eleitoral do Brasil.
O levantamento técnico das fontes oficiais da Bahia validou:
1. A Assembleia Legislativa do Estado da Bahia (ALBA) disponibiliza portal institucional aberto (`https://www.al.ba.gov.br/`), com renderização em servidor (Thymeleaf/Spring) acessível publicamente sem autenticação nem chave de API.
2. A listagem de deputados estaduais em exercício é servida em `https://www.al.ba.gov.br/deputados/deputados-estaduais`, listando os 63 deputados da 20ª legislatura (2023–2027) com identificador numérico interno (ex.: `910629`), nome parlamentar, partido político e foto oficial no repositório institucional (`/fserver/:imagensAlbanet:fotos:...`).
3. O perfil individual de cada deputado em `https://www.al.ba.gov.br/deputados/deputado-estadual/{id}` fornece nome civil completo oficial, data e local de nascimento, histórico biográfico, mandatos anteriores e atuação partidária/parlamentar.
4. A composição de comissões permanentes e temporárias da ALBA é obtida em `https://www.al.ba.gov.br/comissoes/composicao` e nos detalhes individuais em `/comissoes/composicao/{id}`, contendo presidente, vice-presidente, membros titulares e membros suplentes.
5. No Poder Executivo estadual, o Governador Jerônimo Rodrigues (PT) foi eleito em 2022 para o mandato de 2023 a 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`ba-transparencia`, `https://www.transparencia.ba.gov.br/`) e Diário Oficial (`ba-diario-oficial`, `https://dool.egba.ba.gov.br/`).

## Decisão

1. Expansão interestadual: inclusão da Bahia (UF: `BA`, ente: `BA`) na esfera estadual do projeto.
2. Identificador canônico do deputado estadual da ALBA: `alba-{id}`, onde `{id}` é o identificador numérico oficial utilizado nas rotas da Assembleia (ex.: `alba-910629` para Adolfo Menezes).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alba` em `schemas/politico.schema.json` com padrão `^[0-9]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alba-portal`, `alba-deputados`, `alba-deputados-detalhes`, `alba-comissoes`, `ba-transparencia` e `ba-diario-oficial` em `contrib/fontes.yaml`.
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, endereços de gabinete, dados familiares (cônjuge e filhos) e e-mails são descartados durante a extração, em estrito cumprimento a `AGENTS.md`.
   - Nome civil e data de nascimento são utilizados exclusivamente em memória para vinculação com o TSE e resolução de entidades, sem persistência fora dos schemas previstos.
7. Vínculo determinístico com o TSE:
   - Inclusão da Bahia (`BA`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018 e 2022.
   - Vinculação por nome civil, nome de urna e data de nascimento.
   - Deputados da ALBA com mandatos anteriores na Câmara Federal ou Senado são fundidos no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALBA (`Alba.java`):
   - Coleta (`fetch`): armazena em `data/raw/alba/` a listagem de `deputados/deputados-estaduais`, os perfis individuais `perfil/{id}.html` e as comissões `comissoes/composicao.html` e `comissoes/{id}.html`.
   - Normalização (`normalize`): gera `data/normalizado/alba/pessoas.json` e `blocos/{id}.json`.
9. Executivo estadual:
   - O Governador da Bahia é vinculado ao catálogo de portais oficiais (`ba-transparencia` e `ba-diario-oficial`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir quatro unidades federativas essenciais: SP, MG, PR e BA, expandindo o escopo para além das regiões Sul e Sudeste.
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
