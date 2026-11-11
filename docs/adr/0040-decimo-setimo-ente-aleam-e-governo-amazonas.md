# ADR-0040: Décimo sétimo ente: ALEAM e Governo do Amazonas

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Com a integração da ALPB (Paraíba), o projeto alcançou 16 unidades federativas no total, com 100% de cobertura no Sul, Sudeste e Centro-Oeste, além de 4 estados no Nordeste. Na Região Norte, apenas o Pará (ALEPA) estava integrado até o momento. A inclusão da Assembleia Legislativa do Estado do Amazonas (ALEAM) e do Poder Executivo estadual estende a presença do projeto para o segundo maior estado do Norte em termos populacionais e legislativos, ampliando a representatividade amazônica no projeto.

O Amazonas conta com 24 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da ALEAM.

A ALEAM utiliza oficialmente o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL), servido em `https://sapl.al.am.leg.br/`. O sistema expõe API REST JSON aberta internacionalmente, sem restrição geográfica e sem necessidade de autenticação:
- `/api/parlamentares/mandato/?legislatura=20`: mandatos da 20ª Legislatura, identificando deputados titulares e suplentes em exercício;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 20ª Legislatura.

O Poder Executivo estadual é chefiado pelo Governador Wilson Lima (mandatos 2019–2022 e 2023–2026), coberto pelas candidaturas oficiais do TSE de 2018 e 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 20ª Legislatura (2023–2027) da ALEAM via API REST JSON do SAPL.
   - Extração de nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora nas sessões legislativas da 20ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Inclusão do Governador do Estado do Amazonas (Wilson Lima) e vice via candidaturas do TSE e portais oficiais (`am-transparencia` e `am-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para o Amazonas (`AM`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `aleam-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `aleam-5`, `aleam-45`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"aleam"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `AM`, uf `AM`).

3. **Fontes de Dados**:
   - `aleam-deputados`: mandatos e deputados da 20ª Legislatura (`https://sapl.al.am.leg.br/api/parlamentares/mandato/?legislatura=20`).
   - `aleam-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl.al.am.leg.br/api/parlamentares/parlamentar/`).
   - `aleam-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl.al.am.leg.br/api/parlamentares/composicaomesa/`).
   - `aleam-filiacao`: filiação partidária oficial (`https://sapl.al.am.leg.br/api/parlamentares/filiacao/`).
   - `aleam-portal`: fotografias oficiais e mídia institucional (`https://sapl.al.am.leg.br/media/sapl/public/parlamentar/`).
   - `am-transparencia`: Portal da Transparência do Governo do Amazonas (`https://transparencia.am.gov.br/`).
   - `am-diario-oficial`: Diário Oficial do Estado do Amazonas (`https://diario.imprensaoficial.am.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pela API são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 17 entes federativos cobertos (16 estados + DF), avançando na cobertura da Região Norte.
- O acervo histórico passa a cobrir os deputados estaduais do Amazonas e o Governador amazonense.
- O seletor de UF na interface web passa a exibir `AM` entre as unidades federativas ativas.
