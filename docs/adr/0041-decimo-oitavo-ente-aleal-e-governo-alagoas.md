# ADR-0041: Décimo oitavo ente: ALEAL e Governo de Alagoas

- **Status**: Aceita
- **Data**: 2026-11-11

## Contexto

Após a integração do Amazonas como 17º ente federativo, o projeto expande novamente sua cobertura na Região Nordeste. Com Bahia (ALBA), Pernambuco (ALEPE), Ceará (ALECE) e Paraíba (ALPB) já integrados, a inclusão da Assembleia Legislativa do Estado de Alagoas (ALEAL) e do Poder Executivo alagoano faz o projeto alcançar a maioria dos estados nordestinos (5 de 9 estados, ou 55,5% da região), totalizando 18 unidades federativas cobertas em todo o país.

Alagoas conta com 27 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da ALEAL.

A ALEAL utiliza oficialmente o Sistema de Apoio ao Processo Legislativo do Interlegis/Senado Federal (SAPL), hospedado em `https://sapl.al.al.leg.br/`. O sistema disponibiliza API REST JSON aberta internacionalmente, sem restrições de rede ou geográficas:
- `/api/parlamentares/mandato/?legislatura=35`: mandatos da 20ª Legislatura (identificada como legislatura 35 no sistema), cobrindo deputados titulares e suplentes em exercício;
- `/api/parlamentares/parlamentar/{id}/`: cadastro individual com nome parlamentar, nome civil completo, biografia oficial e fotografia;
- `/api/parlamentares/filiacao/?parlamentar={id}`: filiação partidária oficial e datas de vigência;
- `/api/parlamentares/mesadiretora/` e `/api/parlamentares/composicaomesa/`: composição da Mesa Diretora por sessão legislativa da 20ª Legislatura.

O Poder Executivo estadual é chefiado pelo Governador Paulo Dantas (mandatos 2022 e 2023–2026), coberto pelas candidaturas oficiais do TSE de 2022.

## Decisão

1. **Escopo**:
   - Ingestão dos deputados estaduais da 20ª Legislatura (2023–2027) da ALEAL via API REST JSON do SAPL.
   - Extração e sanitização do nome parlamentar (removendo prefixos redundantes como "DEPUTADO " / "DEPUTADA "), nome civil completo, filiação partidária vigente, biografia oficial minimizada, fotografia oficial e link para a ficha do parlamentar.
   - Extração da composição da Mesa Diretora da 20ª Legislatura populando o bloco `orgaos_e_frentes`.
   - Inclusão do Governador do Estado de Alagoas (Paulo Dantas) e vice via candidaturas do TSE e portais oficiais (`al-transparencia` e `al-diario-oficial`).
   - Vínculo determinístico com candidaturas do TSE para Alagoas (`AL`), cobrindo eleições gerais de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `aleal-{id}`, onde `{id}` é o identificador numérico oficial do parlamentar no SAPL (ex.: `aleal-16`, `aleal-46`).
   - Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `alepa`, `alepe`, `cldf`, `almt`, `alems`, `alpb`, `aleam`, `aleal`, `cmsp`, `sapl-{ibge}`, `tse`.
   - Chave em `ids_externos`: `"aleal"`, validada por regex `^[0-9]+$`.
   - Cargo formal: `deputado_estadual` (esfera `estadual`, ente `AL`, uf `AL`).

3. **Fontes de Dados**:
   - `aleal-deputados`: mandatos e deputados da 20ª Legislatura (`https://sapl.al.al.leg.br/api/parlamentares/mandato/?legislatura=35`).
   - `aleal-parlamentares`: cadastro de parlamentares, biografias e fotografias (`https://sapl.al.al.leg.br/api/parlamentares/parlamentar/`).
   - `aleal-mesa-diretora`: composição da Mesa Diretora por sessão legislativa (`https://sapl.al.al.leg.br/api/parlamentares/composicaomesa/`).
   - `aleal-filiacao`: filiação partidária oficial (`https://sapl.al.al.leg.br/api/parlamentares/filiacao/`).
   - `aleal-portal`: fotografias oficiais e mídia institucional (`https://sapl.al.al.leg.br/media/sapl/public/parlamentar/`).
   - `al-transparencia`: Portal da Transparência do Governo de Alagoas (`https://transparencia.al.gov.br/`).
   - `al-diario-oficial`: Diário Oficial do Estado de Alagoas (`https://diario.imprensaoficial.al.gov.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete eventualmente fornecidos pela API são descartados na normalização.
   - Aplicação de `Guarda.semCpf` no texto das biografias.

## Consequências

- O projeto **Atuação Pública** alcança 18 entes federativos cobertos (17 estados + DF), atingindo a maioria absoluta da Região Nordeste.
- O acervo histórico passa a cobrir os deputados estaduais de Alagoas e o Executivo alagoano.
- O seletor de UF na interface web passa a exibir `AL` entre as unidades federativas ativas.
