# ADR-0026: Décimo terceiro ente da federação: CLDF e Governo do Distrito Federal (Poder Legislativo Distrital)

- **Status**: Aceita
- **Data**: 2026-11-06

## Contexto

O Distrito Federal possui natureza federativa e institucional singular na República Federativa do Brasil, acumulando simultaneamente competências legislativas e executivas de natureza estadual e municipal (CF/88, art. 32). O Poder Legislativo local é exercido pela Câmara Legislativa do Distrito Federal (CLDF), integrada por 24 deputados distritais eleitos para legislaturas quadrienais (atualmente na 9ª Legislatura, 2023–2027).

A integração do Distrito Federal consolida a representatividade no coração político do país, complementando a cobertura já existente da Bancada do DF no Congresso Nacional (Câmara dos Deputados e Senado Federal) com o parlamento distrital e o Executivo local (Governador Ibaneis Rocha, mandatos 2019–2023 e 2023–2027).

A CLDF mantém portal institucional aberto (`https://www.cl.df.gov.br`) com o catálogo completo dos 24 parlamentares distritais da legislatura em `/deputados-2023-2026`, fotografias oficiais, biografias e composições detalhadas das comissões permanentes em `/comissoes-permanentes`.

## Decisão

1. **Escopo**:
   - Ingestão dos 24 deputados distritais em exercício na 9ª Legislatura (2023–2027) da CLDF.
   - Coleta de dados oficiais: nome parlamentar, partido, biografia oficial, fotografia oficial em alta resolução e links institucionais.
   - Ingestão das 16 comissões permanentes da CLDF (CCJ, CEOF, CAS, CESC, CTMU, etc.), mapeando a titularidade e suplência dos parlamentares com cargos diretivos (Presidente, Vice-Presidente e Membro).
   - Inclusão do Governador do Distrito Federal (Ibaneis Rocha, mandatos 2019–2023 e 2023–2027) via candidaturas do TSE e portais do GDF.
   - Vínculo determinístico com candidaturas do TSE para o Distrito Federal (`DF`) com suporte a cargos de Governador, Senador, Deputado Federal e Deputado Distrital para as eleições de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados distritais: `cldf-{slug}` (ex.: `cldf-chico-vigilante`, `cldf-fabio-felix`, `cldf-paula-belmonte`).
   - Chave em `ids_externos`: `"cldf"`, validada por regex `^[a-z0-9-]+$`.
   - Cargo formal: `deputado_distrital` (esfera `estadual`, ente `DF`, uf `DF`).

3. **Fontes de Dados**:
   - `cldf-deputados`: catálogo de parlamentares distritais da 9ª Legislatura (`https://www.cl.df.gov.br/deputados-2023-2026`).
   - `cldf-deputados-detalhes`: perfis biográficos individuais (`https://www.cl.df.gov.br/web/guest/{slug}`) e composições de comissões permanentes (`https://www.cl.df.gov.br/comissoes-permanentes`).
   - `cldf-portal`: repositório de fotografias oficiais em alta resolução hospedadas no portal (`https://www.cl.df.gov.br/documents/`).
   - `df-transparencia`: Portal da Transparência do Distrito Federal (`https://www.transparencia.df.gov.br/`).
   - `df-dodf`: Diário Oficial do Distrito Federal (`https://dodf.df.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones de gabinete, e-mails funcionais e números de sala são estritamente descartados na normalização.
   - Aplicação de `Guarda.semCpf` e `Texto.limparEspacos`.

## Consequências

- A base histórica do projeto passa a cobrir 13 entes federativos (12 estados + Distrito Federal).
- Adição de 24 deputados distritais da capital federal ao arquivo factual de políticos.
- Disponibilização do chip `DF` no seletor de UF da interface web e incorporação do Distrito Federal aos índices consolidados.
