# ADR-0037: Décimo quarto ente: ALMT e Governo de Mato Grosso

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Com a integração consolidada de 13 entes federativos (100% das regiões Sul e Sudeste, 3 estados do Nordeste, 1 estado da Região Norte, além de Goiás e Distrito Federal no Centro-Oeste), o projeto avança na cobertura do Centro-Oeste brasileiro com o Estado de Mato Grosso (MT).

Mato Grosso possui 24 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da Assembleia Legislativa do Estado de Mato Grosso (ALMT).

A ALMT disponibiliza portal institucional aberto (`https://www.al.mt.gov.br`), servido via HTTPS na porta 443 padrão, com tempos de resposta rápidos, acessível de redes nacionais e internacionais (inclusive runners do GitHub Actions sem bloqueio geográfico). O portal publica:
- relação completa dos deputados da legislatura em `/parlamento/deputados`;
- perfis individuais em `/parlamento/deputados/{id}/perfil`, contendo nome parlamentar, nome civil completo, filiação partidária e biografia parlamentar;
- fotografias oficiais em alta resolução hospedadas no servidor institucional `https://storage.al.mt.gov.br/`;
- composições da Mesa Diretora por sessão legislativa da 20ª Legislatura (1ª, 2ª, 3ª e 4ª sessões) em `/parlamento/legislatura/21/sessao-legislativa`.

O Poder Executivo de Mato Grosso é chefiado pelo Governador Mauro Mendes (mandatos 2019–2023 e 2023–2027), coberto pelas candidaturas oficiais do TSE de 2018 e 2022 e pela regra de renúncia do ADR-0033.

## Decisão

1. **Escopo**:
   - Ingestão dos 24 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da ALMT.
   - Coleta de dados oficiais: nome parlamentar, nome civil completo, partido, biografia oficial, fotografia oficial em alta resolução e links oficiais institucionais.
   - Ingestão dos cargos da Mesa Diretora da 20ª Legislatura populando o bloco `orgaos_e_frentes` com papéis institucionais (Presidente, Vice-Presidentes e Secretários) e vigências de cada sessão legislativa.
   - Inclusão do Governador do Estado de Mato Grosso (Mauro Mendes) via candidaturas do TSE e portais oficiais do estado (`mt-transparencia` e `mt-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para Mato Grosso (`MT`), com suporte a eleições de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `almt-{id}`, onde `{id}` é o identificador numérico oficial utilizado nas rotas da ALMT (ex.: `almt-129`, `almt-56`, `almt-28`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"almt"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `MT`, uf `MT`).

3. **Fontes de Dados**:
   - `almt-deputados`: catálogo de deputados em exercício (`https://www.al.mt.gov.br/parlamento/deputados`).
   - `almt-deputados-detalhes`: perfis biográficos individuais (`https://www.al.mt.gov.br/parlamento/deputados/{id}/perfil`).
   - `almt-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://www.al.mt.gov.br/parlamento/legislatura/21/sessao-legislativa`).
   - `almt-portal`: fotos oficiais dos parlamentares hospedadas em `https://storage.al.mt.gov.br/`.
   - `mt-transparencia`: Portal da Transparência do Governo de Mato Grosso (`https://www.transparencia.mt.gov.br/`).
   - `mt-diario-oficial`: Imprensa Oficial do Estado de Mato Grosso / IOMAT (`https://www.iomat.mt.gov.br/`).

4. **Privacidade e Minimização**:
   - Dados de contato, endereços e eventuais informações familiares são estritamente descartados na normalização.
   - Aplicação de `Guarda.semCpf` e `Texto.limparEspacos`.

## Consequências

- O projeto **Atuação Pública** alcança 14 entes federativos cobertos (13 estados + DF), consolidando a maioria das 27 unidades federativas brasileiras.
- O arquivo histórico incorpora 24 novos deputados estaduais do Centro-Oeste e o Executivo mato-grossense.
- O seletor de UF na interface web passa a exibir `MT` entre as unidades federativas ativas.
