# ADR-0025: Décimo segundo estado da federação: ALEPE e Governo do Estado de Pernambuco (expansão da Região Nordeste)

- **Status**: Aceita
- **Data**: 2026-11-06

## Contexto

Com a integração do Pará (ALEPA) consolidando a estreia da Região Norte e presença em todas as cinco macrorregiões brasileiras, a expansão federativa prossegue para consolidar a representatividade no **Nordeste**, fortalecendo o segundo maior parlamento estadual da região: a Assembleia Legislativa do Estado de Pernambuco (ALEPE).

Pernambuco conta com 49 deputados estaduais em exercício na 20ª Legislatura (2023–2027). A ALEPE mantém um portal institucional aberto (`https://www.alepe.pe.gov.br`) que disponibiliza catálogo estruturado de parlamentares, perfis biográficos individuais detalhados (nome civil, profissão, naturalidade, biografia, fotografia oficial) e páginas dedicadas às comissões temáticas permanentes com identificação clara dos respectivos membros e cargos diretivos.

## Decisão

1. **Escopo**:
   - Ingestão dos 49 deputados estaduais em exercício na 20ª Legislatura (2023–2027) da ALEPE.
   - Coleta de dados biográficos oficiais: nome parlamentar, nome civil, partido, naturalidade, biografia oficial e fotos em alta resolução.
   - Ingestão das 18 comissões permanentes e especiais da ALEPE, associando os parlamentares a seus papéis diretivos (Presidente, Vice-Presidente, Membro titular) com rastreabilidade formal.
   - Inclusão do Governador do Estado de Pernambuco (mandato 2023–2027) e histórico das eleições gerais no estado via dados abertos do TSE.
   - Vínculo determinístico com candidaturas do TSE para Pernambuco (`PE`).

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alepe-{slug}` (ex.: `alepe-abimael-santos`, `alepe-dani-portela`).
   - Chave em `ids_externos`: `"alepe"`, validada por regex `^[a-z0-9-]+$`.

3. **Fontes de Dados**:
   - `alepe-deputados`: catálogo de parlamentares em exercício (`https://www.alepe.pe.gov.br/parlamentares/`).
   - `alepe-deputados-detalhes`: perfis biográficos individuais (`https://www.alepe.pe.gov.br/parlamentar/{slug}/`) e composições de comissões (`https://www.alepe.pe.gov.br/comissao/{slug}/`).
   - `alepe-portal`: repositório de fotografias oficiais do portal da ALEPE (`https://www.alepe.pe.gov.br/wp-content/`).
   - `pe-transparencia`: Portal da Transparência do Governo de Pernambuco (`https://web.transparencia.pe.gov.br/`).
   - `pe-cepe`: Diário Oficial do Estado de Pernambuco / Companhia Editora de Pernambuco (`https://diariooficial.cepe.com.br/`).

4. **Privacidade e Minimização**:
   - Telefones de gabinete, e-mails funcionais e números de sala são estritamente descartados na normalização.
   - Aplicação de `Guarda.semCpf` e `Texto.limparEspacos`.

## Consequências

- A base histórica do projeto passa a cobrir 12 estados da Federação (PR, RS, SC, SP, RJ, MG, ES, BA, CE, GO, PA e PE).
- Adição de 49 deputados estaduais pernambucanos ao arquivo factual de políticos.
- Disponibilização do chip `PE` no seletor de UF da interface web e incorporação de Pernambuco aos índices consolidados.
