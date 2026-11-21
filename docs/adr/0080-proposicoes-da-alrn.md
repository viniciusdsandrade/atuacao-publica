# ADR-0080: Proposições da ALRN

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Na ALRN, os 24 deputados tinham só mandatos e cargos na Mesa (ADR-0041).
O portal de transparência legislativa da Assembleia usa uma API pública do e-Legis, achada no levantamento de 2026-11-21 (ADR-0079).
A lista de processos traz a propositura (tipo, número e ano), a ementa, a data de entrada e a iniciativa, com o deputado autor.

## Decisão

1. A coleta da ALRN passa a trazer os processos da 63ª legislatura pela lista `processo`, com o período de entrada desde 2023-02-01, 200 por página (`alrn-elegis`).
2. Entram PEC, PLC, PL, PDL e os projetos de resolução (PRES e PRTH, o de título honorífico).
3. A identificação é a da propositura ("PL 458/2026"), não o número do processo. A data é a de entrada.
4. A autoria é a da iniciativa: um autor por processo, proponente. O nome se liga ao deputado com a ligação comum (`AutoriaPorNome`), que passa a tirar "Deputado" e "Deputada" e vários tratamentos seguidos ("Deputado Dr. Bernardo").
5. A situação fica nula: só sai na tramitação de cada processo, uma chamada por processo.
6. O link do texto é o conteúdo do documento principal do processo.
7. A coleta não chama o endpoint de parlamentar da API: ele traz CPF e data de nascimento.

## Consequências

- 2.525 proposições da legislatura, com 2.259 autorias ligadas aos 24 deputados.

## Limitações

- Processo de outro Poder, da Mesa ou de quem deixou a Assembleia antes do início da coleta fica sem perfil.
- A API também tem votos por deputado (`processo/{id}/detalhamento-votacao/{reuniao}`) e presença por reunião (`reuniao/presenca/{id}`). Não coletados nesta etapa.
- Não validado: se a API responde ao runner do GitHub, fora do Brasil.

## Alternativas consideradas

- **Situação pela tramitação de cada processo**: adiada. São cerca de 2.500 chamadas na carga inicial.
