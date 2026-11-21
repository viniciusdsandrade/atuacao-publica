# ADR-0087: Proposições da ALEPA e sessão por cookie no cliente HTTP

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Na ALEPA, os 41 deputados tinham mandatos, comissões e votos (ADR-0040 e ADR-0064), sem proposições.
A página de proposições é um painel DevExpress. A primeira página sai do callback do painel (`/Legislativo/CallbackPanelProposicoes`), com o filtro de tipo e ano.
As seguintes saem do callback da lista de cartões (`/Legislativo/CardViewProposicoes`), com o estado cifrado que a página anterior devolve e o cookie de sessão (`ASP.NET_SessionId`) que o primeiro callback cria. Sem o cookie, a página seguinte vem vazia.
O cliente HTTP do ETL não guardava cookie.

## Decisão

1. O cliente HTTP passa a aceitar e reenviar cookies só dos hosts que pedem sessão (`Http.manterSessao`). Os outros hosts não recebem cookie, e o comportamento das outras fontes não muda.
2. A coleta da ALEPA traz PEC, PLC, PL, PDL e PR da 60ª legislatura (`alepa-proposicoes`). Cada tipo e ano é uma cadeia: a primeira página e as seguintes, cada uma com o estado da anterior, na mesma sessão.
3. O argumento do callback é prefixado pelos tamanhos, como o protocolo do DevExpress pede: "GB|20;12|PAGERONCLICK3|PN1;" pede a página 2, e da página 11 em diante os tamanhos mudam.
4. A cadeia é inteira ou nada. O ano fechado com a cadeia completa fica no cache; o ano aberto renova uma vez por semana. Na leitura, página de uma coleta mais velha que a primeira fica de fora.
5. Cada cartão traz autor, tipo, número, data e ementa. A autoria se liga pelo nome, com a grafia dos votos para os quatro deputados cujo nome na lista difere do perfil (Aveilton, Bordalo, Ronie e Torrinho).
6. A situação fica nula: só sai no detalhe de cada proposição.

## Consequências

- 3.680 proposições da legislatura (PL 3.211), com 3.178 autorias nos 41 deputados.
- Em cada tipo e ano, o número de cartões únicos é igual ao total que a fonte informa.

## Limitações

- A situação de cada proposição não é coletada.
- O estado da cadeia vale só na sessão: falha no meio refaz a cadeia inteira na execução seguinte.
- Não validado: se a ALEPA responde ao runner do GitHub, fora do Brasil, nesta página.
