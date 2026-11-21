# ADR-0081: Votos da ALRN

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

A API de transparência do e-Legis da ALRN (ADR-0080) publica as votações: a lista "últimas votações" traz cada processo com a votação mais recente, e o detalhamento da votação traz o voto de cada deputado.
O voto vem como código. O próprio portal de transparência traduz o código: 0 é Não, 1 é Sim e 2 é Abstenção. O tipo da votação também: A é aberta, O é ostensiva, S é secreta e X é simbólica.

## Decisão

1. A coleta lê a lista `processo/ultimas-votacoes` (200 por página, renovada uma vez por semana) e o detalhamento `processo/{id}/detalhamento-votacao/{reunião}` de cada votação aberta ou ostensiva. A votação encerrada não muda: o detalhe vem uma vez e fica no cache.
2. Cada voto vira um registro no bloco de votos do deputado, com a data da abertura da votação, o órgão (a comissão ou o tipo da sessão), a propositura e a ementa. O texto que a fonte já escreve ("votou") fica como está.
3. A participação por ano conta só o plenário, onde todos votam. A maior parte das votações em plenário não lista voto nenhum (425 de 508 na coleta de 2026-11-21): é simbólica, mesmo marcada como aberta. Conta como votação nominal a que lista mais de um voto.
4. O votante se liga ao deputado pelo nome, com a ligação comum (`AutoriaPorNome`).

## Consequências

- 1.354 votações lidas, com 4.442 votos nos 24 deputados; nenhum votante sem perfil.
- Participação em plenário: 8 votações nominais em 2024, 40 em 2025 e 21 em 2026.

## Limitações

- A lista traz só a votação mais recente de cada processo: a votação anterior do mesmo processo, em outra comissão, fica de fora.
- A lista começa em 2024: não há votação de 2023 na fonte.
- A votação secreta e a simbólica não têm voto por deputado.
- A presença por reunião (`reuniao/presenca/{id}`) existe na API e não é coletada nesta etapa.
- Não validado: se a API responde ao runner do GitHub, fora do Brasil.

## Alternativas consideradas

- **Participação com todas as votações em plenário**: descartada. As votações simbólicas tirariam todos os deputados da conta.
