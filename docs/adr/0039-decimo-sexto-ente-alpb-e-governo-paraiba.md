# ADR-0039: Décimo sexto ente: ALPB e Governo da Paraíba

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Com a integração da ALEMS, o projeto atingiu 100% de cobertura no Centro-Oeste brasileiro, somando-se a Sul e Sudeste. No Nordeste, os estados da Bahia (ALBA), Pernambuco (ALEPE) e Ceará (ALECE) já integram o catálogo. A inclusão da Assembleia Legislativa do Estado da Paraíba (ALPB) e do Poder Executivo estadual eleva para quatro o número de estados nordestinos cobertos, aproximando a região de 50% de representatividade.

A Paraíba possui 36 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da ALPB.

A ALPB utiliza oficialmente o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL 3), servido via HTTPS em `https://sapl3.al.pb.leg.br/`. O sistema expõe API REST JSON aberta internacionalmente sem bloqueio geográfico nem necessidade de autenticação:
- `/api/parlamentares/mandato/?legislatura=20`: mandatos da 20ª Legislatura, identificando deputados titulares e suplentes em exercício;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 20ª Legislatura.

O Poder Executivo estadual é chefiado pelo Governador João Azevêdo (mandatos 2019–2022 e 2023–2026), coberto pelas candidaturas oficiais do TSE de 2018 e 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 20ª Legislatura (2023–2027) da ALPB via API REST JSON do SAPL 3.
   - Extração de nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora nas sessões legislativas da 20ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Inclusão do Governador do Estado da Paraíba (João Azevêdo) e vice via candidaturas do TSE e portais oficiais (`pb-transparencia` e `pb-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para a Paraíba (`PB`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alpb-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `alpb-89`, `alpb-157`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alpb"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `PB`, uf `PB`).

3. **Fontes de Dados**:
   - `alpb-deputados`: mandatos e deputados da 20ª Legislatura (`https://sapl3.al.pb.leg.br/api/parlamentares/mandato/?legislatura=20`).
   - `alpb-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl3.al.pb.leg.br/api/parlamentares/parlamentar/`).
   - `alpb-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl3.al.pb.leg.br/api/parlamentares/composicaomesa/`).
   - `alpb-filiacao`: filiação partidária oficial (`https://sapl3.al.pb.leg.br/api/parlamentares/filiacao/`).
   - `alpb-portal`: fotografias oficiais e mídia institucional (`https://sapl3.al.pb.leg.br/media/sapl/public/parlamentar/`).
   - `pb-transparencia`: Portal da Transparência do Governo da Paraíba (`https://transparencia.pb.gov.br/`).
   - `pb-diario-oficial`: Diário Oficial do Estado da Paraíba (`https://auniao.pb.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails institucionais de gabinete fornecidos pela API são estritamente descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 16 entes federativos cobertos (15 estados + DF), avançando na cobertura do Nordeste.
- O arquivo histórico incorpora os deputados estaduais da Paraíba e o Executivo paraibano.
- O seletor de UF na interface web passa a exibir `PB` entre as unidades federativas ativas.
