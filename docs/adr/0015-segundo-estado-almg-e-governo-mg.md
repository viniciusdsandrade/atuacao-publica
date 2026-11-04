# ADR-0015: Segundo estado: ALMG e Governo de Minas Gerais

- Status: aceita
- Data: 2026-11-03

## Contexto

A fase 2 do projeto prevê a expansão da cobertura para a esfera estadual além do estado de São Paulo.
Minas Gerais (MG) é o segundo colégio eleitoral do país, o estado com maior número de municípios da federação e possui infraestrutura oficial de dados abertos consolidada e padronizada.
O levantamento técnico das fontes oficiais de Minas Gerais validou:
1. A Assembleia Legislativa de Minas Gerais (ALMG) disponibiliza API REST v2 oficial (`https://dadosabertos.almg.gov.br/api/v2/`) sob licença Creative Commons Atribuição 4.0 Internacional, sem necessidade de autenticação.
2. A listagem de deputados estaduais de todas as legislaturas é obtida estruturada via `/api/v2/legislaturas/pesquisa_deputados?leg={num}`, abrangendo a 19ª (2019-2023, cobrindo o ano de 2022) e a 20ª legislatura (2023-2027, cobrindo 2023 a 2026).
3. O detalhamento biográfico individual (`/api/v2/deputados/{id}`) expõe nome parlamentar, nome civil completo (`nomeServidor`), data de nascimento, naturalidade (município e UF), filiações partidárias e mandatos com datas exatas de início e término.
4. As verbas indenizatórias mensais de cada parlamentar estão disponíveis em `/api/v2/prestacao_contas/verbas_indenizatorias/deputados/{id}/{ano}/{mes}`, com discriminação de data de emissão, tipo de despesa, valor reembolsado, nome do emitente, documento fiscal e CPF/CNPJ.
5. As participações em órgãos e comissões da ALMG (permanentes e especiais) são fornecidas por parlamentar via `/api/v2/deputados/{id}/participacoes_comissoes`, com cargo ocupado (Presidente, Vice-Presidente, Efetivo, Suplente) e datas de vigência.
6. As fotografias institucionais oficiais dos deputados são servidas diretamente em `https://www.almg.gov.br/export/sites/portal/a-assembleia/deputados/fotos/{id}.jpg`.
7. No Poder Executivo estadual, o Governador Romeu Zema teve mandatos eleitos em 2018 e 2022 registrados no repositório do TSE, com transparência e diário oficial listados no catálogo oficial (`mg-transparencia` e `mg-diario-oficial`).

## Decisão

1. Expansão interestadual: inclusão de Minas Gerais (UF: `MG`, ente: `MG`) na esfera estadual do projeto.
2. Identificador canônico do deputado estadual da ALMG: `almg-{id}`, onde `{id}` é o identificador numérico oficial mantido pela ALMG (ex.: `almg-26148`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `almg` em `schemas/politico.schema.json` e nos perfis publicados.
5. Bloqueio de dados pessoais e privacidade:
   - Nas verbas indenizatórias da ALMG, o campo `cpfCnpj` contém tanto CNPJ quanto CPF de prestadores de serviços pessoas físicas. O ETL descarta sequências de 11 dígitos em `cnpj_cpf_fornecedor` e aplica `Guarda.semCpf`, retendo apenas CNPJs (14 dígitos).
   - Telefones de gabinetes e endereços residenciais são descartados na ingestão.
6. Vínculo determinístico com o TSE:
   - O campo `nomeServidor` (nome civil completo) e a data de nascimento retornados pela API da ALMG estabelecem vínculo determinístico com o repositório de candidaturas do TSE (chaves de `Tse.Chave`).
   - Deputados que também exerceram mandatos federais na Câmara dos Deputados ou no Senado são unificados em perfil único por intermédio das chaves de pessoa física do TSE em memória (sem persistir CPF).
7. Adaptador da ALMG (`Almg.java`):
   - Coleta (`fetch`): armazena em `data/raw/almg/` a pesquisa de deputados por legislatura (19 e 20), os detalhes biográficos de cada parlamentar, as participações em comissões e as prestações de contas mensais de verba indenizatória dentro do período 2022 a 2026.
   - Normalização (`normalize`): gera `data/normalizado/almg/pessoas.json` e `blocos/{id}.json`.
8. Executivo estadual:
   - O Governador de Minas Gerais é incorporado pelo mecanismo existente de mandatos do Executivo (`contrib/mandatos.yaml`), associando os links oficiais de transparência e diário oficial de MG (`mg-transparencia`, `mg-diario-oficial`).

## Consequências

- O projeto passa a cobrir os poderes Legislativo e Executivo dos dois maiores estados do Sudeste e do Brasil (São Paulo e Minas Gerais).
- Os padrões de privacidade e segurança do projeto são integralmente preservados.
- O pipeline estático continua independente, com dados versionados e validados por schema JSON.
