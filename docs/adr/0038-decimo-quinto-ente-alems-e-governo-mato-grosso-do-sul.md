# ADR-0038: Décimo quinto ente: ALEMS e Governo de Mato Grosso do Sul

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Com a integração da ALMT, o projeto avançou na cobertura do Centro-Oeste brasileiro. A integração da Assembleia Legislativa do Estado de Mato Grosso do Sul (ALEMS) consolida **100% de cobertura da Região Centro-Oeste** (Distrito Federal, Goiás, Mato Grosso e Mato Grosso do Sul), unindo-se às regiões Sul e Sudeste (já 100% integradas).

Mato Grosso do Sul possui 24 deputados estaduais em exercício na 12ª Legislatura (2023–2027) da ALEMS.

A ALEMS disponibiliza portal institucional aberto (`https://al.ms.gov.br`), servido via HTTPS, com resposta rápida e acessível diretamente sem restrição geográfica internacional (inclusive pelos runners do GitHub Actions). O portal publica:
- relação completa dos deputados estaduais em exercício agrupados por partido em `/Partidos/Lista`;
- perfis parlamentares individuais em `/Deputados/Visualizar/{id}`, contendo nome parlamentar, partido, fotografia oficial, biografia oficial e canais oficiais de comunicação;
- relação histórica da 12ª Legislatura e composição da Mesa Diretora (biênio 2023–2025 e segundo biênio) em `/Paginas/783/12-legislatura`.

O Poder Executivo estadual é chefiado pelo Governador Eduardo Riedel (mandato 2023–2027), coberto pelas candidaturas oficiais do TSE de 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos 24 deputados estaduais em exercício na 12ª Legislatura (2023–2027) da ALEMS.
   - Coleta de dados oficiais: nome parlamentar, nome civil completo, partido, biografia oficial, fotografia oficial em alta resolução e links institucionais.
   - Ingestão dos cargos da Mesa Diretora da 12ª Legislatura populando o bloco `orgaos_e_frentes` com papéis institucionais (Presidente, Vice-Presidentes e Secretários).
   - Inclusão do Governador do Estado de Mato Grosso do Sul (Eduardo Riedel) via candidaturas do TSE e portais estaduais oficiais (`ms-transparencia` e `ms-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para Mato Grosso do Sul (`MS`), com suporte a eleições de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alems-{id}`, onde `{id}` é o identificador numérico oficial utilizado nas rotas da ALEMS (ex.: `alems-35`, `alems-6`, `alems-2043`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alems"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `MS`, uf `MS`).

3. **Fontes de Dados**:
   - `alems-deputados`: catálogo de deputados em exercício por bancada partidária (`https://al.ms.gov.br/Partidos/Lista`).
   - `alems-deputados-detalhes`: perfis biográficos individuais (`https://al.ms.gov.br/Deputados/Visualizar/{id}`).
   - `alems-mesa-diretora`: composição da Mesa Diretora e legislatura (`https://al.ms.gov.br/Paginas/783/12-legislatura`).
   - `alems-portal`: repositório de imagens oficiais hospedadas em `/upload/Deputy/`.
   - `ms-transparencia`: Portal da Transparência do Governo de Mato Grosso do Sul (`https://transparencia.ms.gov.br/`).
   - `ms-diario-oficial`: Imprensa Oficial do Estado de Mato Grosso do Sul (`https://diariooficial.al.ms.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails institucionais de gabinete são estritamente descartados na normalização.
   - Aplicação de `Guarda.semCpf` nas biografias.

## Consequências

- O projeto **Atuação Pública** alcança 15 entes federativos cobertos (14 estados + DF), alcançando 100% de cobertura nas regiões Sul, Sudeste e Centro-Oeste.
- O arquivo histórico incorpora os 24 deputados estaduais de Mato Grosso do Sul e o Executivo sul-mato-grossense.
- A navegação web e o seletor de UF passam a exibir `MS` entre as unidades federativas ativas.
