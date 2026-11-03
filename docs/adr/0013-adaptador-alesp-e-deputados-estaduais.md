# ADR-0013: Adaptador da ALESP e deputados estaduais

- Status: aceita
- Data: 2026-11-03

## Contexto

A fase 2 do projeto prevê a expansão para a esfera estadual, com assembleias legislativas e governos estaduais.
A Assembleia Legislativa do Estado de São Paulo (ALESP) atua na mesma unidade federativa do piloto municipal de Campinas e do Tribunal de Contas do Estado (TCE-SP), cujas fontes já integram o projeto.
O levantamento técnico das fontes oficiais da ALESP validou que:
1. A ALESP disponibiliza repositório de dados abertos em XML (`https://www.al.sp.gov.br/repositorioDados/`) e API REST moderna de processo legislativo (`https://legis-api-portal-prd.al.sp.gov.br`).
2. A listagem de deputados de todas as legislaturas está aberta na API (`/parlamentar-portal/todos`), e os deputados em exercício contam com arquivo próprio (`deputados.xml`).
3. As despesas de verba de gabinete são publicadas em arquivo XML consolidado desde 2015 (`despesas_gabinetes.xml`, cerca de 171 MB), com matrícula parlamentar, tipo de despesa, fornecedor, CNPJ ou CPF e valor.
4. As comissões permanentes possuem arquivos estruturados de composição (`comissoes_membros.xml`) e de votações nominais com pareceres (`comissoes_permanentes_votacoes.xml`, cerca de 67 MB).
5. As sessões plenárias e matérias votadas estão abertas via API REST (`/votacao/{ano}` e `/sessoes-plenarias/`). Os votos nominais individuais em plenário são publicados em PDF com o espelho do painel eletrônico.
6. As candidaturas a deputado estadual em São Paulo estão disponíveis no repositório do TSE (eleições de 2018, 2022 e 2026).

## Decisão

1. Cargo estadual: adição de `deputado_estadual` ao modelo e ao schema comum (`esfera = estadual`, `ente = SP`).
2. Identificador canônico do deputado estadual da ALESP: `alesp-{matricula}`, onde `{matricula}` é o número de matrícula parlamentar na ALESP (ex.: `alesp-300607`), estável e correspondente ao identificador usado na URL do portal oficial (`/deputado/?matricula=...`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `sapl-{ibge}`, `tse`. A casa federal prevalece caso a pessoa já tenha exercido mandato anterior em Brasília.
4. Campo `ids_externos`: inclusão da chave `alesp` no perfil e no índice publicado, armazenando a matrícula numérica.
5. Bloqueio de dados pessoais e privacidade:
   - O campo `CNPJ` nas despesas de gabinete da ALESP contém sequências de 11 dígitos referentes a CPFs de fornecedores pessoas físicas.
   - O ETL aplica `Texto.cnpj` (mantendo apenas registros válidos com 14 dígitos) e `Guarda.semCpf`, descartando CPFs de fornecedores na ingestão.
   - E-mails e telefones de gabinetes são descartados na ingestão.
6. Adaptador da ALESP (`Alesp.java`):
   - Coleta (`fetch`): armazena em `data/raw/alesp/` os arquivos XML de deputados, despesas e comissões, além das consultas complementares à API REST, respeitando cache e cabeçalhos HTTP.
   - Normalização (`normalize`): gera `pessoas.json` e `blocos/{matricula}.json`.
   - Streaming XML: usa leitor em streaming (StAX) para processar os arquivos de grande porte (`despesas_gabinetes.xml` e `comissoes_permanentes_votacoes.xml`), mantendo o consumo de memória controlado dentro dos limites de heap.
7. Vínculo com o TSE: vincula deputados da 19ª e 20ª legislaturas às candidaturas do TSE por nome civil normalizado, UF (`SP`) e data de nascimento em memória (e CPF quando presente).
8. Votações: publica as votações nominais em comissões permanentes. O espelho em PDF do plenário fica registrado na metodologia, sem parser de PDF não estruturado nesta fase.
9. Interface: adição do rótulo "Deputado(a) estadual" na visualização e nas opções de filtros.

## Consequências

- O catálogo do projeto cobre os deputados estaduais paulistas no período de cobertura (2022 a 2026).
- A política de privacidade de dados permanece estritamente atendida, sem vazamento de CPF de fornecedores.
- Novos estados poderão adotar adaptadores específicos (`alep`, `alerj`, etc.) seguindo o mesmo padrão de identificação `sigla-{id}`.

## Alternativas consideradas

- Identificador por `IdDeputado`: descartado porque a matrícula é o campo chave nas despesas, nas URLs públicas e na navegação do portal da ALESP.
- Extração de votos de plenário por OCR ou parser de PDF: descartada para manter o pipeline confiável apenas sobre dados primários estruturados (T0), evitando alucinação de votos.
