# ADR-0085: Proposições da ALMT

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Na ALMT, os 24 deputados tinham só mandatos e cargos na Mesa (ADR-0039).
A API oficial da Assembleia exige OAuth com credencial da Casa. A pesquisa de proposições do portal (`/proposicao`) é HTML, com dez itens por página, por tipo e ano, em ordem de número.
Cada item traz a ementa, a linha "Projeto de lei nº 2/2025 Dep. Eduardo Botelho - Protocolo nº 3/2025" e o PDF do texto.

## Decisão

1. A coleta da ALMT passa a trazer PEC, PLC, PL, PDL e PR da 20ª legislatura, pela pesquisa do portal (`almt-proposicoes`), uma consulta por tipo e ano, até a página incompleta.
2. As páginas do ano aberto renovam juntas, uma vez por semana; as dos anos fechados vêm do cache.
3. O autor sai da linha do item e se liga ao deputado com a ligação comum (`AutoriaPorNome`), que tira o "Dep.". A grafia "Carlos Avalone" da pesquisa vira "Carlos Avallone", a do perfil.
4. A data de apresentação e a situação ficam nulas: a lista não as publica. O nome do PDF traz um carimbo de hora, mas a fonte não diz que é a data de apresentação.
5. O link do texto é o PDF do item; sem ele, a página da proposição.
6. O passo `fetch almt` do CI ganha 25 min de orçamento: a carga inicial tem cerca de 1.300 páginas.

## Consequências

- 12.616 proposições da legislatura (PL 7.234, PR 3.623, PLC 142, PEC 34, PDL 27), com 11.181 autorias nos 24 deputados.

## Limitações

- Sem data de apresentação: o bloco ordena por ano e número.
- Autoria de suplente ou de quem deixou a Assembleia fica de fora, como nas outras casas (320 nomes de autor sem perfil, entre eles a Mesa Diretora e o Tribunal de Justiça).
- Votos e presença: não encontrados fora da API com OAuth.
