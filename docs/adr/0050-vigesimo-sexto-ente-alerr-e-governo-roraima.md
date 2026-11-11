# ADR-0050: Vigésimo sexto ente: ALERR e Governo de Roraima

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Após a integração do Acre (25º ente federativo), o projeto avança para a penúltima unidade federativa antes da cobertura nacional absoluta. Com Pará (ALEPA), Amazonas (ALEAM), Rondônia (ALERO), Tocantins (ALETO) e Acre (ALEAC) já integrados, a inclusão da Assembleia Legislativa do Estado de Roraima (ALERR) e do Governo do Estado de Roraima eleva a cobertura da Região Norte para 6 de 7 estados (85,7% da região).

Roraima conta com 24 deputados estaduais titulares na 9ª Legislatura (2023–2026) da ALERR.

A ALERR adota o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL 3), hospedado em `https://sapl.al.rr.leg.br/`. O sistema disponibiliza API REST JSON aberta, sem necessidade de autenticação:
- `/api/parlamentares/legislatura/9`: 9ª Legislatura (2023–2026), id 9 no sistema;
- `/api/parlamentares/mandato/?legislatura=9`: mandatos dos deputados estaduais da 9ª Legislatura;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia institucional;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 9ª Legislatura;
- `/api/materia/materialegislativa/`: proposições legislativas de autoria dos deputados estaduais.

O Poder Executivo estadual é chefiado pelo Governador Antonio Denarium (mandato 2023–2026), coberto pelas candidaturas oficiais do TSE de 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 9ª Legislatura (2023–2026) da ALERR via API REST JSON do SAPL.
   - Extração e sanitização do nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora da 9ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Ingestão de proposições legislativas de autoria dos parlamentares da legislatura via `SaplMateria`.
   - Inclusão do Governador do Estado de Roraima (Antonio Denarium) e vice via candidaturas do TSE e portais oficiais (`rr-transparencia` e `rr-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para Roraima (`RR`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alerr-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `alerr-1`, `alerr-110`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `alero`, `alepi`, `alrn`, `aleto`, `alese`, `alema`, `aleac`, `alerr`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alerr"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `RR`, uf `RR`).

3. **Fontes de Dados**:
   - `alerr-deputados`: mandatos e deputados da 9ª Legislatura (`https://sapl.al.rr.leg.br/api/parlamentares/mandato/?legislatura=9`).
   - `alerr-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl.al.rr.leg.br/api/parlamentares/parlamentar/`).
   - `alerr-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl.al.rr.leg.br/api/parlamentares/composicaomesa/`).
   - `alerr-filiacao`: filiação partidária oficial (`https://sapl.al.rr.leg.br/api/parlamentares/filiacao/`).
   - `alerr-proposicoes`: proposições de autoria dos parlamentares (`https://sapl.al.rr.leg.br/api/materia/materialegislativa/`).
   - `alerr-portal`: fotografias oficiais e mídia institucional (`https://sapl.al.rr.leg.br/media/sapl/public/parlamentar/`).
   - `rr-transparencia`: Portal da Transparência do Governo de Roraima (`https://transparencia.rr.gov.br/`).
   - `rr-diario-oficial`: Diário Oficial do Estado de Roraima (`https://www.imprensaoficial.rr.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pela API são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 26 entes federativos cobertos (25 estados + DF), correspondendo a mais de 96% do território nacional.
- A cobertura da Região Norte sobe para 6 de 7 estados (85,7%), restando apenas o Amapá para a cobertura total de 100% de todo o território brasileiro.
- O seletor de UF na interface web passa a exibir `RR` entre as unidades federativas ativas.
