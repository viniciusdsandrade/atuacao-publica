# ADR-0024: Décimo primeiro estado da federação: ALEPA e Governo do Estado do Pará (estreia na Região Norte)

- **Status**: Aceita
- **Data**: 2026-11-06

## Contexto

Com a integração consolidada de 10 estados da Federação (100% da Região Sul [PR, RS, SC], 100% da Região Sudeste [SP, MG, RJ, ES], além de Bahia, Ceará e Goiás), a **Região Norte** e a Amazônia Legal permaneciam como a única grande macrorregião geográfica brasileira sem representatividade oficial no arquivo histórico do projeto.

O Estado do Pará possui o maior colégio eleitoral e o maior parlamento estadual da Região Norte, com 41 deputados estaduais em exercício na 60ª Legislatura (2023–2027) da Assembleia Legislativa do Estado do Pará (ALEPA).

A ALEPA disponibiliza portal institucional aberto (`https://www.alepa.pa.gov.br`) com relação completa dos deputados da legislatura, fotografias oficiais em alta resolução, biografia parlamentar, composições das comissões permanentes e representações institucionais.

## Decisão

1. **Escopo**:
   - Ingestão dos 41 deputados estaduais em exercício na 60ª Legislatura (2023–2027) da ALEPA.
   - Coleta de dados biográficos oficiais, partidos, datas de nascimento, biografias e fotografias oficiais em alta resolução.
   - Ingestão das comissões permanentes e representações oficiais da ALEPA, mapeando os papéis institucionais (Presidente, Vice-Presidente, Titular e Suplente) via vínculo determinístico pelos identificadores oficiais.
   - Inclusão do Governador do Estado do Pará (Helder Barbalho, mandatos 2019–2023 e 2023–2027) via candidaturas do TSE e portais estaduais de transparência e diário oficial.
   - Vínculo determinístico com candidaturas do TSE para o Estado do Pará (`PA`), com suporte a eleições de 2018, 2022 e 2026.

2. **Identificador e Chave Externa**:
   - Identificador canônico dos deputados estaduais: `alepa-{id}` (ex.: `alepa-1024`, `alepa-1011`).
   - Chave em `ids_externos`: `"alepa"`, validada por regex `^[0-9]+$`.

3. **Fontes de Dados**:
   - `alepa-deputados`: catálogo de deputados em exercício (`https://www.alepa.pa.gov.br/Home/Page/Deputados`).
   - `alepa-deputados-detalhes`: perfil biográfico individual (`https://www.alepa.pa.gov.br/Institucional/Deputado/{id}`) e comissões (`https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes`).
   - `alepa-portal`: fotos oficiais dos parlamentares hospedadas no repositório do portal.
   - `pa-transparencia`: Portal da Transparência do Governo do Pará (`https://www.transparencia.pa.gov.br/`).
   - `pa-ioepa`: Diário Oficial do Estado do Pará / Imprensa Oficial (`https://www.ioepa.com.br/`).

4. **Privacidade e Minimização**:
   - Telefones, endereços e e-mails de gabinete são estritamente descartados na normalização.
   - Aplicação de `Guarda.semCpf` e `Texto.limparEspacos`.

## Consequências

- O projeto **Atuação Pública** alcança presença oficial em **todas as 5 Grandes Regiões do Brasil** (Norte, Nordeste, Centro-Oeste, Sudeste e Sul).
- O arquivo histórico incorpora 41 novos deputados estaduais da Amazônia e o Executivo paraense.
- O seletor de UF na interface web passa a contar com o chip `PA` entre as unidades federativas ativas.
