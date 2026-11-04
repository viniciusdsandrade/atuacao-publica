# ADR-0016: Terceiro estado: ALEP e Governo do Paraná

- Status: aceita
- Data: 2026-11-03

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais dos principais estados da federação.
Após a conclusão dos estados de São Paulo (ALESP, ADR-0013 e ADR-0014) e Minas Gerais (ALMG, ADR-0015), o Paraná (PR) consolida a representação do Sul do país e o quinto maior eleitorado nacional.
O levantamento técnico das fontes oficiais do Paraná validou:
1. A Assembleia Legislativa do Estado do Paraná (ALEP) disponibiliza portal oficial modernizado (`https://www.assembleia.pr.leg.br/`) com páginas institucionais de parlamentares da 20ª legislatura (2023–2027) e histórico da 19ª legislatura (2019–2023), acessíveis publicamente sem necessidade de chave de autenticação.
2. A listagem de deputados estaduais em exercício é obtida via `https://www.assembleia.pr.leg.br/deputados/conheca`, expondo slug canônico (`/deputados/perfil/{slug}`), nome parlamentar, partido e fotografia oficial hospedada em infraestrutura própria (`storage2.assembleia.pr.leg.br`).
3. O detalhamento biográfico e histórico de cada deputado é servido em `https://www.assembleia.pr.leg.br/deputados/perfil/{slug}`, contendo histórico de atuação, cidade natal e trajetória pública.
4. A composição de comissões permanentes e temporárias da ALEP é publicada em `https://www.assembleia.pr.leg.br/atividade-parlamentar/comissoes-permanentes-e-temporarias`, listando colegiados, membros efetivos e presidências.
5. O Portal da Transparência da ALEP (`https://transparencia.assembleia.pr.leg.br/`) publica relatórios orçamentários, demonstrativos de despesas e execuções financeiras abertas em `https://storage2.assembleia.pr.leg.br/file/public-media/transparencia/despesas/consolidados/`.
6. No Poder Executivo estadual, o Governador Ratinho Junior (PSD) exerceu mandato de 2019 a 2023 (eleição 2018) e mandato vigente de 2023 a 2027 (eleição 2022), ambos já integrados via TSE e com portais oficiais no catálogo (`pr-transparencia` e `pr-diario-oficial`).

## Decisão

1. Expansão interestadual: inclusão do Paraná (UF: `PR`, ente: `PR`) na esfera estadual do projeto.
2. Identificador canônico do deputado estadual da ALEP: `alep-{slug}`, onde `{slug}` é o identificador textual único utilizado nas rotas oficiais da Assembleia (ex.: `alep-renato-freitas`, `alep-alexandre-curi`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alep` em `schemas/politico.schema.json` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alep-portal`, `alep-deputados`, `alep-comissoes` e `alep-transparencia` em `contrib/fontes.yaml`.
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, e-mails de gabinete e dados residenciais são descartados na ingestão.
   - Fornecedores de prestação de contas passam obrigatoriamente por `Guarda.semCpf` e `Texto.cnpj`.
7. Vínculo determinístico com o TSE:
   - Vinculação por nome parlamentar/civil e UF (`PR`), cruzando com candidaturas a deputado estadual das eleições de 2018 e 2022 no repositório oficial do TSE.
   - Deputados da ALEP que tenham exercido mandato federal na Câmara dos Deputados são fundidos em perfil único em memória sem expor CPF.
8. Adaptador da ALEP (`Alep.java`):
   - Coleta (`fetch`): armazena em `data/raw/alep/` a listagem de deputados de `conheca`, perfis individuais em `perfil/{slug}` e composição das comissões.
   - Normalização (`normalize`): gera `data/normalizado/alep/pessoas.json` e `blocos/{slug}.json`.
9. Executivo estadual:
   - O Governador do Paraná é vinculado ao catálogo oficial de portais (`pr-transparencia`, `pr-diario-oficial`) e mantém seus dois mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir os poderes Legislativo e Executivo dos três principais estados do Centro-Sul: São Paulo, Minas Gerais e Paraná.
- A integridade dos schemas JSON, a política editorial e a estrita proteção à privacidade são preservadas.
