# ADR-0021: Oitavo estado: ALESC e Governo de Santa Catarina

- Status: aceita
- Data: 2026-11-05

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais dos principais estados da federação.
Após a integração de São Paulo (ALESP, ADR-0013 e ADR-0014), Minas Gerais (ALMG, ADR-0015), Paraná (ALEP, ADR-0016), Bahia (ALBA, ADR-0017), Rio de Janeiro (ALERJ, ADR-0018), Rio Grande do Sul (ALRS, ADR-0019) e Ceará (ALECE, ADR-0020), a inclusão de Santa Catarina (SC) consolida a cobertura de 100% dos estados da Região Sul do Brasil (PR, RS e SC) e eleva a federação coberta a oito unidades federativas.
O levantamento técnico das fontes oficiais de Santa Catarina validou:
1. A Assembleia Legislativa do Estado de Santa Catarina (ALESC) disponibiliza portal institucional aberto (`https://www.alesc.sc.gov.br/`), servido via HTTPS na porta 443 padrão sem barreiras de autenticação nem bloqueios de rede em nuvem.
2. A listagem de deputados estaduais em exercício na 20ª Legislatura (2023–2027) é servida em `https://www.alesc.sc.gov.br/deputados/`, contendo com precisão os 40 deputados estaduais em cards semânticos com slugs textuais oficiais de perfil (ex.: `alex-brasil`, `altair-silva`, `ana-campagnolo`), siglas partidárias e fotografias oficiais do acervo parlamentar.
3. As páginas biográficas individuais (`https://www.alesc.sc.gov.br/deputado/{slug}/`) apresentam dados estruturados de nascimento, escolaridade, histórico parlamentar e links para atuação nas comissões permanentes no Portal e-Legis (`https://portalelegis.alesc.sc.gov.br/`).
4. No Poder Executivo estadual, o Governador Jorginho Mello (PL) exerce mandato até 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`sc-transparencia`, `https://transparencia.sc.gov.br/`) e Diário Oficial (`sc-diario-oficial`, `https://doe.sea.sc.gov.br/`).

## Decisão

1. Expansão interestadual: inclusão de Santa Catarina (UF: `SC`, ente: `SC`) na esfera estadual do projeto, atingindo cobertura integral da Região Sul.
2. Identificador canônico do deputado estadual da ALESC: `alesc-{slug}`, onde `{slug}` é o identificador textual hifenizado oficial utilizado nas rotas da Assembleia (ex.: `alesc-alex-brasil`, `alesc-altair-silva`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alesc` em `schemas/politico.schema.json` com padrão `^[a-z0-9-]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alesc-portal`, `alesc-deputados` e `alesc-deputados-detalhes` em `contrib/fontes.yaml` (`sc-transparencia` e `sc-diario-oficial` já constavam no catálogo).
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, e-mails institucionais e números de gabinete são descartados durante a extração e normalização, em cumprimento rigoroso a `AGENTS.md` e verificação com `Guarda`.
   - Nomes civis, nomes de urna e dados biográficos são utilizados para vinculação em memória com as bases de candidaturas do TSE.
7. Vínculo determinístico com o TSE:
   - Inclusão de Santa Catarina (`SC`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018, 2022 e 2026.
   - Vinculação por nome de urna, nome civil, casamento por palavras e remoção de títulos religiosos/profissionais (ex.: "Padre Pedro Baldissera" -> "Pedro Baldissera") com o TSE.
   - Parlamentares da ALESC com mandatos anteriores na Câmara Federal ou Senado são fundidos deterministicamente no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALESC (`Alesc.java`):
   - Coleta (`fetch`): armazena em `data/raw/alesc/` a listagem `deputados.html` e os perfis individuais `perfis/{slug}.html` via HTTPS na porta 443 padrão.
   - Normalização (`normalize`): gera `data/normalizado/alesc/pessoas.json` e `blocos/{slug}.json`.
9. Executivo estadual:
   - O Governador de Santa Catarina é vinculado ao catálogo de portais oficiais (`sc-transparencia` e `sc-diario-oficial`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir oito estados brasileiros (SP, MG, PR, BA, RJ, RS, CE e SC), consolidando 100% de cobertura de toda a Região Sul do Brasil (PR, RS, SC).
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
