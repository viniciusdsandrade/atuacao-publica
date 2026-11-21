# ADR-0088: Situação das proposições da ALRN e da ALMT

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

As proposições da ALRN (ADR-0080) e da ALMT (ADR-0085) saíam com a situação nula: as listas não a publicam.
As duas casas publicam a tramitação de cada proposição, uma chamada por proposição:

- ALRN: `processo/{id}/tramitacao`, em JSON, da mais nova para a mais velha, com `situacaoProcesso.nome`;
- ALMT: `/proposicao/{id}/tramitacoes`, em HTML, da mais velha para a mais nova, com a data e o texto de cada andamento, sem campo de situação.

Na ALERJ, a situação só sai no documento de cada proposição, de 20 s a 45 s por chamada, para cerca de 11 mil proposições. Fica de fora.

## Decisão

1. A coleta lê a tramitação de cada proposição de tipo legislativo, da mais nova para a mais velha. Cada arquivo renova uma vez por semana, no seu dia de rodízio; a carga inicial se espalha pelas execuções, pelo orçamento de cada passo.
2. ALRN: a situação é a `situacaoProcesso.nome` da tramitação mais recente.
3. ALMT: a situação é o texto do último andamento ("Lei n° 12.809/2025 D.O. Estado...", "Apto para apreciação..."), sem a data. A fonte não tem campo de situação; o último andamento é o que ela registra como estado atual.
4. Sem a tramitação em cache, a situação fica nula, como antes.

## Consequências

- ALRN: as 2.259 autorias têm situação (por exemplo, 1.126 com "Determinado o arquivamento" e 182 com "Aguardando inclusão na Pauta").
- ALMT: a situação chega aos poucos; a carga inicial das 12.616 tramitações leva mais de uma execução.

## Limitações

- Na ALMT, o último andamento pode ser um passo administrativo ("Ao Expediente"), e não uma situação no sentido estrito.
- ALERJ e ALEPA seguem sem situação.
