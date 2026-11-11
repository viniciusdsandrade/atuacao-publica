# ADR-0051: Vigésimo sétimo ente: ALAP, Governo do Amapá e 100% de cobertura nacional

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Com a integração sucessiva de 26 entes federativos, restava apenas o Amapá para que o projeto **Atuação Pública** completasse a cobertura integral e absoluta de todos os 26 estados brasileiros e do Distrito Federal (100% das 27 unidades federativas do país).

O Amapá conta com 24 deputados estaduais em exercício na 9ª Legislatura (2023–2027) da Assembleia Legislativa do Estado do Amapá (ALAP).

A ALAP mantém seu portal institucional oficial em `https://www.al.ap.leg.br/`, aberto internacionalmente sem restrições ou autenticação:
- `pagina.php?pg=exibir_legislatura`: relação oficial dos 24 deputados estaduais da legislatura, fornecendo identificadores numéricos individuais (`iddeputado`), nome parlamentar, nome civil completo, filiação partidária, profissão e fotografias oficiais em alta resolução (`fotodeputado/`);
- `pagina.php?pg=exibir_parlamentar&iddeputado={id}`: ficha parlamentar institucional de cada deputado estadual;
- `pagina.php?pg=exibir_mesadiretora`: composição oficial da Mesa Diretora da legislatura (Presidente Alliny Serrão, Vice-Presidentes e Secretários).

O Poder Executivo estadual é chefiado pelo Governador Clécio Luís (mandato 2023–2026), coberto pelas candidaturas oficiais do TSE de 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos 24 deputados estaduais da 9ª Legislatura (2023–2027) da ALAP via portal institucional oficial.
   - Extração estruturada de nome parlamentar, nome civil completo, filiação partidária, profissão/biografia, fotografia oficial e link para a ficha individual no portal da ALAP.
   - Extração da composição da Mesa Diretora da ALAP (Presidente Alliny Serrão, Vice-Presidentes e Secretários) populando o bloco `orgaos_e_frentes`.
   - Inclusão do Governador do Estado do Amapá (Clécio Luís) e vice via candidaturas do TSE e portais oficiais (`ap-transparencia` e `ap-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para o Amapá (`AP`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alap-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar na ALAP (ex.: `alap-74`, `alap-75`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `alero`, `alepi`, `alrn`, `aleto`, `alese`, `alema`, `aleac`, `alerr`, `alap`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"alap"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `AP`, uf `AP`).

3. **Fontes de Dados**:
   - `alap-deputados`: mandatos e deputados da 9ª Legislatura (`https://www.al.ap.leg.br/pagina.php?pg=exibir_legislatura`).
   - `alap-parlamentares`: fichas individuais de parlamentares (`https://www.al.ap.leg.br/pagina.php?pg=exibir_parlamentar&iddeputado={id}`).
   - `alap-mesa-diretora`: composição da Mesa Diretora (`https://www.al.ap.leg.br/pagina.php?pg=exibir_mesadiretora`).
   - `alap-portal`: portal institucional e repositório de fotografias oficiais (`https://www.al.ap.leg.br/fotodeputado/`).
   - `ap-transparencia`: Portal da Transparência do Governo do Amapá (`https://transparencia.ap.gov.br/`).
   - `ap-diario-oficial`: Diário Oficial do Estado do Amapá (`https://diofe.portal.ap.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pelo portal são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias e profissões.

## Consequências

- **COBERTURA NACIONAL COMPLETA (100%)**: Todas as 27 unidades federativas do Brasil (todos os 26 estados mais o Distrito Federal) passam a estar plenamente integradas, cobertas e ativas no projeto **Atuação Pública**.
- O Amapá fecha com chave de ouro a Região Norte (7 de 7 estados: PA, AM, RO, TO, AC, RR e AP).
- Todas as cinco regiões do país (Norte, Nordeste, Centro-Oeste, Sudeste e Sul) atingem 100% de cobertura de suas assembleias legislativas e governos estaduais.
- O seletor de UF na interface web exibe todas as 27 siglas federativas.
