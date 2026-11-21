# ADR-0086: Governador em exercício do Amazonas

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

No AM, o governador e o vice saíram em 2026-04-04 (ADR-0074), e o presidente da Assembleia Legislativa, Roberto Cidade, assumiu o governo.
O site mostrava o AM sem governador em exercício. O perfil de Roberto Cidade vem da ALEAM, como deputado estadual fora de exercício.
O Diário Oficial do Estado traz Roberto Maia Cidade Filho como governador no expediente desde a edição de 2026-04-06, e os decretos de 2026-11-17 ainda saem assinados por ele como Governador do Estado do Amazonas.

## Decisão

1. Uma contribuição T0 no perfil de Roberto Cidade (`aleam-45`) registra o mandato de governador de 2026-04-05 a 2027-01-06, com o expediente da edição de 2026-04-06 do Diário Oficial do Estado, que tem snapshot.
2. O início vem do termo de assunção do Diário Oficial Eletrônico da Aleam (edição extra 2422), que não tem snapshot. O comentário do arquivo diz isso.
3. O fim é a posse dos governadores eleitos em 2026, pela regra da EC 111/2021 em `contrib/mandatos.yaml`.
4. A situação diz "com a saída do governador e do vice", sem dizer se o exercício é interino ou por eleição indireta: a eleição indireta de maio de 2026 só aparece na imprensa, e não foi validada.

## Consequências

- Os 27 governos têm governador em exercício no site.
- Em 2027-01-07, o perfil sai com o mandato de governador encerrado em 2027-01-06.

## Limitações

- Não validado: se a Assembleia elegeu Roberto Cidade em eleição indireta, e em que data. O Diário Oficial do Estado de maio de 2026 não traz a eleição na pesquisa por palavra.
