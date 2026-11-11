# ADR-0043: Vigésimo ente: ALEPI e Governo do Piauí

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Após a integração de Rondônia como 19º ente federativo, o projeto expande a cobertura na Região Nordeste, alcançando o marco histórico de 20 entes federativos (19 estados + Distrito Federal + dois municípios pilotos). Com Bahia (ALBA), Pernambuco (ALEPE), Ceará (ALECE), Paraíba (ALPB) e Alagoas (ALEAL) já integrados, a inclusão da Assembleia Legislativa do Estado do Piauí (ALEPI) e do Poder Executivo piauiense eleva a cobertura do Nordeste para 6 de 9 estados (66,7% da região).

O Piauí conta com 30 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da ALEPI.

A ALEPI adota como plataforma tecnológica oficial o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL), hospedado em `https://sapl.al.pi.leg.br/`. O sistema disponibiliza API REST JSON aberta internacionalmente, sem restrições de rede ou geográficas:
- `/api/parlamentares/mandato/?legislatura=20`: mandatos da 20ª Legislatura (2023–2027), cobrindo deputados titulares e suplentes em exercício;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia institucional;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 20ª Legislatura.

O Poder Executivo estadual é chefiado pelo Governador Rafael Fonteles (mandato 2023–2026), coberto pelas candidaturas oficiais do TSE de 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 20ª Legislatura (2023–2027) da ALEPI via API REST JSON do SAPL.
   - Extração e sanitização do nome parlamentar, nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora da 20ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Inclusão do Governador do Estado do Piauí (Rafael Fonteles) e vice via candidaturas do TSE e portais oficiais (`pi-transparencia` e `pi-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para o Piauí (`PI`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alepi-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `alepi-278`, `alepi-324`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `alero`, `alepi`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alepi"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `PI`, uf `PI`).

3. **Fontes de Dados**:
   - `alepi-deputados`: mandatos e deputados da 20ª Legislatura (`https://sapl.al.pi.leg.br/api/parlamentares/mandato/?legislatura=20`).
   - `alepi-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl.al.pi.leg.br/api/parlamentares/parlamentar/`).
   - `alepi-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl.al.pi.leg.br/api/parlamentares/composicaomesa/`).
   - `alepi-filiacao`: filiação partidária oficial (`https://sapl.al.pi.leg.br/api/parlamentares/filiacao/`).
   - `alepi-portal`: fotografias oficiais e mídia institucional (`https://sapl.al.pi.leg.br/media/sapl/public/parlamentar/`).
   - `pi-transparencia`: Portal da Transparência do Governo do Piauí (`https://transparencia.pi.gov.br/`).
   - `pi-diario-oficial`: Diário Oficial do Estado do Piauí (`https://www.diariooficial.pi.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pela API são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 20 entes federativos cobertos (19 estados + DF), atingindo 66,7% dos estados da Região Nordeste (6 de 9).
- O acervo histórico passa a cobrir os deputados estaduais do Piauí e o Executivo piauiense.
- O seletor de UF na interface web passa a exibir `PI` entre as unidades federativas ativas.
