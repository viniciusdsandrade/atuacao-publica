# ADR-0044: Vigésimo primeiro ente: ALRN e Governo do Rio Grande do Norte

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Após a integração do Piauí como 20º ente federativo, o projeto expande a cobertura da Região Nordeste para 7 de seus 9 estados (77,8% da região). A inclusão da Assembleia Legislativa do Estado do Rio Grande do Norte (ALRN) e do Poder Executivo potiguar consolida 21 unidades federativas com representação completa (20 estados + Distrito Federal + dois municípios pilotos).

O Rio Grande do Norte conta com 24 deputados estaduais em exercício na 63ª Legislatura (2023–2027) da ALRN.

A ALRN mantém portal oficial aberto internacionalmente em `https://www.al.rn.leg.br/`, sem restrições geográficas:
- `/deputados`: catálogo institucional com os 24 deputados estaduais da legislatura, fotografias em alta resolução, filiação partidária e links para perfis;
- `/deputado/{id}/{slug}`: cadastro individual com nome parlamentar, nome civil completo e biografia oficial;
- `/deputados/mesadiretora`: composição oficial da Mesa Diretora da 63ª Legislatura (Presidente, 1º e 2º Vice-Presidentes e 1º a 4º Secretários).

O Poder Executivo estadual é chefiado pela Governadora Fátima Bezerra (mandatos 2019–2022 e 2023–2026), coberta pelas candidaturas oficiais do TSE de 2018 e 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 63ª Legislatura (2023–2027) da ALRN a partir do portal oficial.
   - Extração do identificador numérico, nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha institucional.
   - Extração da composição da Mesa Diretora da 63ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Inclusão da Governadora do Estado do Rio Grande do Norte (Fátima Bezerra) e vice via candidaturas do TSE e portais oficiais (`rn-transparencia` e `rn-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para o Rio Grande do Norte (`RN`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alrn-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no portal da ALRN (ex.: `alrn-72`, `alrn-137`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `alero`, `alepi`, `alrn`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alrn"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `RN`, uf `RN`).

3. **Fontes de Dados**:
   - `alrn-deputados`: lista de deputados e mandatos (`https://www.al.rn.leg.br/deputados`).
   - `alrn-parlamentares`: biografias e fichas individuais (`https://www.al.rn.leg.br/deputado/{id}/{slug}`).
   - `alrn-mesa-diretora`: composição da Mesa Diretora (`https://www.al.rn.leg.br/deputados/mesadiretora`).
   - `alrn-portal`: fotos oficiais (`https://www.al.rn.leg.br/storage/deputados/`).
   - `rn-transparencia`: Portal da Transparência do Governo do Rio Grande do Norte (`https://transparencia.rn.gov.br/`).
   - `rn-diario-oficial`: Diário Oficial do Estado do Rio Grande do Norte (`https://www.diariooficial.rn.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete são descartados na normalização.
   - Aplicação de `Guarda.semCpf` nas biografias oficiais.

## Consequências

- O projeto **Atuação Pública** alcança 21 entes federativos cobertos (20 estados + DF), atingindo 77,8% dos estados da Região Nordeste (7 de 9).
- O acervo histórico passa a cobrir os deputados estaduais do Rio Grande do Norte e o Executivo potiguar.
- O seletor de UF na interface web passa a exibir `RN` entre as unidades federativas ativas.
