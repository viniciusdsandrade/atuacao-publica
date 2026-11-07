# ADR-0032: Financiamento de campanha

- Status: aceita
- Data: 2026-11-06

## Contexto

A prestação de contas de campanha é pública. A lei manda divulgar os recursos recebidos com nome, CPF ou CNPJ do doador e valor (Lei 9.504/1997, art. 28, §§ 4º e 7º).
A prestação final chega até o 30º dia depois da eleição, ou até o 20º dia depois do segundo turno (art. 29, III e IV).
O TSE publica um ZIP por eleição: `prestacao_contas/prestacao_de_contas_eleitorais_candidatos_{ano}.zip`.
Em 2026-11-06, os ZIPs tinham 306 MB (2018), 1,25 GB (2020), 495 MB (2022), 1,33 GB (2024) e 375 MB (2026).
A fonte republica anos fechados: o ZIP de 2022 mudou em 2026-11-01, e os de 2018 e 2020 mudaram de tamanho desde setembro.
Cada ZIP traz, por UF e `_BRASIL`, receitas, receitas por doador originário, despesas contratadas e despesas pagas. O layout é o mesmo de 2018 a 2026.
As linhas trazem nome e CPF de doador e de fornecedor pessoa física, CPF do candidato e do vice, gênero e cor ou raça do candidato.
A CDN do TSE aceita requisição Range.

## Decisão

1. A coleta lê por Range só as entradas de receitas e de despesas contratadas (ADR-0005, item 10): o CSV `_BRASIL` na eleição geral e o `_SP` na municipal, porque os municípios cobertos ficam em SP.
2. O cache guarda o recorte: as linhas das candidaturas cobertas e só as colunas usadas, compactado. Fornecedor pessoa física fica sem documento e sem nome já no cache.
3. A eleição mais recente renova por semana, porque as contas chegam depois do pleito. As demais reaproveitam o cache.
4. Cada candidatura publica dois registros no bloco `financiamento_campanha`: receitas (total, quantidade, somas por fonte e por origem) e despesas contratadas (total, quantidade, somas por tipo e até 10 maiores fornecedores com CNPJ).
5. Doador e fornecedor pessoa física nunca aparecem por nome nem por CPF. Entram só nas somas.
6. Fornecedor com CNPJ aparece pelo nome da Receita Federal, quando a fonte traz, e pelo CNPJ. A sequência de CPF no nome de MEI é removida.
7. Cada receita (`SQ_RECEITA`) e cada despesa (`SQ_DESPESA`) contam uma vez.
8. Fonte e origem repartem o mesmo total. O site não soma uma com a outra.
9. Despesas pagas e doador originário não entram.

## Consequências

- No ensaio local de 2026-11-06, a coleta leu as 10 entradas em cerca de 2 minutos. O recorte ocupa 64 MB no cache, contra cerca de 4,2 GB das entradas inteiras.
- 63.524 candidaturas cobertas têm prestação de contas na fonte. 1.800 dos 1.867 perfis passaram a ter o bloco preenchido.
- Na eleição de 2026, 1.428 perfis têm receitas, num total de R$ 3,09 bilhões: R$ 2,29 bilhões do Fundo Especial, R$ 566 milhões de outros recursos e R$ 237 milhões do Fundo Partidário.
- O perfil cresce, na mediana, 8,7 KB, e no máximo 19,4 KB.
- A republicação de uma eleição fechada só entra no site quando o cache é renovado.
- A prestação de contas da eleição de 2026 ainda pode mudar: retificação e entrega fora do prazo.

## Alternativas consideradas

- Baixar os ZIPs inteiros: cerca de 3,8 GB na primeira carga.
- DivulgaCandContas: recusa o User-Agent do projeto (`docs/FONTES.md`).
- Publicar o doador pessoa física por nome, como a lei permite: a minimização (LGPD, art. 6º, III) basta com as somas.
- Despesas pagas: outra visão do mesmo gasto, pelo regime de caixa. Pode entrar depois.
- Guardar a entrada inteira no cache: passaria de 4 GB e guardaria CPF e nome de pessoa física sem uso.
