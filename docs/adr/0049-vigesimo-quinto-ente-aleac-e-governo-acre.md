# ADR-0049: Vigésimo quinto ente: ALEAC e Governo do Acre

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Após a conclusão da cobertura total de 100% da Região Nordeste com a integração do Maranhão (24º ente federativo), o projeto retoma a expansão na Região Norte com o objetivo de alcançar a cobertura integral de todas as unidades federativas do Brasil. Com Pará (ALEPA), Amazonas (ALEAM), Rondônia (ALERO) e Tocantins (ALETO) já integrados, a inclusão da Assembleia Legislativa do Estado do Acre (ALEAC) e do Governo do Estado do Acre eleva a cobertura da Região Norte para 5 de 7 estados (mais de 71% da região).

O Acre conta com 24 deputados estaduais titulares na 16ª Legislatura (2023–2027) da ALEAC.

A ALEAC adota como plataforma tecnológica oficial o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL 3), hospedado em `https://sapl.al.ac.leg.br/`. O sistema disponibiliza API REST JSON aberta, sem necessidade de autenticação:
- `/api/parlamentares/legislatura/17`: 16ª Legislatura (2023–2027), id 17 no sistema;
- `/api/parlamentares/mandato/?legislatura=17`: mandatos dos deputados estaduais da 16ª Legislatura;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia institucional;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 16ª Legislatura;
- `/api/materia/materialegislativa/`: proposições legislativas de autoria dos deputados estaduais.

O Poder Executivo estadual é chefiado pelo Governador Gladson Cameli (mandato 2023–2026), coberto pelas candidaturas oficiais do TSE de 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 16ª Legislatura (2023–2027) da ALEAC via API REST JSON do SAPL.
   - Extração e sanitização do nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora da 16ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Ingestão de proposições legislativas de autoria dos parlamentares da legislatura via `SaplMateria`.
   - Inclusão do Governador do Estado do Acre (Gladson Cameli) e vice via candidaturas do TSE e portais oficiais (`ac-transparencia` e `ac-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para o Acre (`AC`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `aleac-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `aleac-248`, `aleac-249`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `alero`, `alepi`, `alrn`, `aleto`, `alese`, `alema`, `aleac`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"aleac"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `AC`, uf `AC`).

3. **Fontes de Dados**:
   - `aleac-deputados`: mandatos e deputados da 16ª Legislatura (`https://sapl.al.ac.leg.br/api/parlamentares/mandato/?legislatura=17`).
   - `aleac-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl.al.ac.leg.br/api/parlamentares/parlamentar/`).
   - `aleac-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl.al.ac.leg.br/api/parlamentares/composicaomesa/`).
   - `aleac-filiacao`: filiação partidária oficial (`https://sapl.al.ac.leg.br/api/parlamentares/filiacao/`).
   - `aleac-proposicoes`: proposições de autoria dos parlamentares (`https://sapl.al.ac.leg.br/api/materia/materialegislativa/`).
   - `aleac-portal`: fotografias oficiais e mídia institucional (`https://sapl.al.ac.leg.br/media/sapl/public/parlamentar/`).
   - `ac-transparencia`: Portal da Transparência do Governo do Acre (`https://transparencia.ac.gov.br/`).
   - `ac-diario-oficial`: Diário Oficial do Estado do Acre (`https://diario.ac.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pela API são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 25 entes federativos cobertos (24 estados + DF), correspondendo a mais de 92% do território nacional.
- A cobertura da Região Norte sobe para 5 de 7 estados (71,4%).
- O seletor de UF na interface web passa a exibir `AC` entre as unidades federativas ativas.
