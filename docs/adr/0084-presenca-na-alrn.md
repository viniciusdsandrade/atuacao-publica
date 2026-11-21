# ADR-0084: Presença nas sessões da ALRN

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

A API do e-Legis da ALRN (ADR-0080) lista as reuniões realizadas (`reuniao?situacao=R`) e a presença de cada deputado por reunião (`reuniao/presenca/{id}`).
A presença vem como evento: `presenca`, `ausencia-justificada` ou nulo.

## Decisão

1. A coleta lê as reuniões realizadas de cada ano e a presença das sessões deliberativas do plenário: ordinária (tipo 1), extraordinária (2) e extraordinária de vetos (12). Sessão solene e audiência pública ficam de fora.
2. Cada sessão vira um evento no perfil do deputado. `presenca` vira `registrada`; `ausencia-justificada`, `ausencia_justificada`. Sem registro, a presença fica nula: a fonte não diz que foi falta.
3. A sessão realizada não muda: a presença vem uma vez e fica no cache. A coleta vai da sessão mais nova para a mais velha.
4. O campo `total` da lista de reuniões é o tamanho da página: a coleta pagina até vir uma página incompleta.

## Consequências

- 242 sessões com presença, nos 24 deputados: 4.150 presenças, 711 ausências justificadas e 947 sem registro.

## Limitações

- As sessões de 2023 e parte das de 2024 respondem HTTP 500 na presença (conferido em 2026-11-21). Elas ficam sem evento, e a coleta tenta de novo nas execuções seguintes, até o limite de falhas da coleta.
- A presença em comissão não é coletada.
- O site não calcula taxa de presença, como nas outras casas.
- Não validado: se a API responde ao runner do GitHub, fora do Brasil.
