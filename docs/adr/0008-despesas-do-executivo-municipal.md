# ADR-0008: Despesas do Executivo municipal no período do mandato

- Status: aceita
- Data: 2026-11-01

## Contexto

O perfil do prefeito do piloto municipal (ADR-0007) tem mandato, candidaturas e links oficiais, mas nenhum dado de atividade.
A prefeitura não publica dados abertos, e o portal da transparência exige chave.
O Tribunal de Contas do Estado de São Paulo publica a despesa de todos os municípios paulistas em API JSON sem autenticação (`tce-sp-transparencia-api`, `docs/FONTES.md`).
Cada resposta traz um mês de um município, com órgão, evento contábil (`Empenhado`, `Valor Liquidado`, `Valor Pago`, `Anulação`, `Reforço`), fornecedor, empenho, data e valor.
A fonte identifica só o órgão. Não separa o gabinete do prefeito.
Fornecedor pessoa física aparece com nome e parte do CPF.

## Decisão

1. O perfil de prefeito ganha o bloco `despesas_executivo`: "despesas pagas pela administração direta do município no período do mandato".
2. Fonte: `despesas/{municipio}/{ano}/{mes}` da API do TCE-SP, para cada mês do mandato já iniciado, até o mês corrente.
3. O ETL agrega por mês, órgão e evento. Nenhum lançamento individual é publicado: nem fornecedor, nem empenho, nem data.
4. No perfil entram só os registros do órgão da administração direta (nome que começa com "PREFEITURA MUNICIPAL") e do evento `Valor Pago`.
   Autarquias, fundações, instituto de previdência e a Câmara Municipal ficam fora. Empenho e liquidação ficam fora.
5. O bloco publica o total, o valor por mês e o valor por órgão por ano, com a quantidade de pagamentos. O formato é o de `bloco_despesas`, sem arquivos de detalhe.
6. O rótulo do bloco diz "no período do mandato". A fonte não informa quem autorizou cada pagamento, e o site não afirma isso.
7. Adaptador `TceSp.java`, fonte `tcesp` na CLI. A ligação com o perfil usa o ente (código IBGE) e o período do mandato em `contrib/mandatos.yaml`, como os atos normativos do Presidente.

## Consequências

- O prefeito passa a ter um bloco de atividade com proveniência (URL do mês na API, data de captura).
- O valor não é comparável com a cota parlamentar dos vereadores. O bloco tem nome e nota próprios.
- A defasagem da fonte (cerca de 2 meses) aparece como meses sem registro. O ETL renova os últimos 6 meses toda semana e guarda os anteriores em cache.
- Estender a outros municípios paulistas exige só o nome do município na API. Outros estados dependem do tribunal de contas de cada um.

## Alternativas consideradas

- Publicar os lançamentos individuais: descartado. A fonte traz nome de pessoa física com parte do CPF, e o volume passa de 10 mil lançamentos por mês.
- Somar todos os órgãos do município: descartado. A Câmara é outro poder, e autarquias e fundações têm ordenador próprio.
- Usar o valor empenhado: descartado. O empenho é reserva, não gasto. O valor pago é o que saiu do caixa no mês.
- Arquivos anuais do TCE (`tce-sp-conjunto-dados`): descartado. São do estado inteiro (1,96 GiB) e não têm o ano corrente.
