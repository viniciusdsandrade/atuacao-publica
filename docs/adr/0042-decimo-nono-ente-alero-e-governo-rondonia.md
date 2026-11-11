# ADR-0042: Décimo nono ente: ALERO e Governo de Rondônia

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Após a integração de Alagoas como 18º ente federativo (garantindo a maioria dos estados do Nordeste), o projeto expande a cobertura na Região Norte. Com Pará (ALEPA) e Amazonas (ALEAM) já integrados, a inclusão da Assembleia Legislativa do Estado de Rondônia (ALERO) e do Poder Executivo rondoniense eleva para 3 os estados cobertos na Região Norte, totalizando 19 unidades federativas com representação completa (18 estados + Distrito Federal + dois municípios pilotos).

Rondônia conta com 24 deputados estaduais em exercício na 11ª Legislatura (2023–2027) da ALERO.

A ALERO adota como plataforma tecnológica oficial o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL), hospedado em `https://sapl.al.ro.leg.br/`. O sistema disponibiliza API REST JSON aberta internacionalmente, sem bloqueios de rede ou geográficos:
- `/api/parlamentares/mandato/?legislatura=11`: mandatos da 11ª Legislatura (2023–2027), cobrindo deputados titulares e suplentes em exercício;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia institucional;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 11ª Legislatura.

O Poder Executivo estadual é chefiado pelo Governador Marcos Rocha (mandatos 2019–2022 e 2023–2026), coberto pelas candidaturas oficiais do TSE de 2018 e 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 11ª Legislatura (2023–2027) da ALERO via API REST JSON do SAPL.
   - Extração e sanitização do nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora da 11ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Inclusão do Governador do Estado de Rondônia (Marcos Rocha) e vice via candidaturas do TSE e portais oficiais (`ro-transparencia` e `ro-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para Rondônia (`RO`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alero-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `alero-256`, `alero-287`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `alero`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alero"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `RO`, uf `RO`).

3. **Fontes de Dados**:
   - `alero-deputados`: mandatos e deputados da 11ª Legislatura (`https://sapl.al.ro.leg.br/api/parlamentares/mandato/?legislatura=11`).
   - `alero-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl.al.ro.leg.br/api/parlamentares/parlamentar/`).
   - `alero-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl.al.ro.leg.br/api/parlamentares/composicaomesa/`).
   - `alero-filiacao`: filiação partidária oficial (`https://sapl.al.ro.leg.br/api/parlamentares/filiacao/`).
   - `alero-portal`: fotografias oficiais e mídia institucional (`https://sapl.al.ro.leg.br/media/sapl/public/parlamentar/`).
   - `ro-transparencia`: Portal da Transparência do Governo de Rondônia (`https://transparencia.ro.gov.br/`).
   - `ro-diario-oficial`: Diário Oficial do Estado de Rondônia (`https://diof.ro.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pela API são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 19 entes federativos cobertos (18 estados + DF), ampliando a cobertura da Região Norte para 3 estados (PA, AM e RO).
- O acervo histórico passa a cobrir os deputados estaduais de Rondônia e o Executivo rondoniense.
- O seletor de UF na interface web passa a exibir `RO` entre as unidades federativas ativas.
