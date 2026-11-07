# ADR-0036: Lado a lado de dois parlamentares da mesma casa

- **Status**: Aceita
- **Data**: 2026-11-07

## Contexto

O ADR-0012, item 3, diz que a interface não ordena nem compara políticos.
A rota `/comparar` entrou em 2026-11-06, pelo item 18 do roadmap, sem ADR que revisse essa regra.
A versão publicada punha lado a lado dois totais de despesa e dois totais de proposição, sem o período de cada mandato.
Também mostrava como "votações registradas" o número de linhas anuais do bloco, e não o de votos.
A busca aceitava qualquer político, inclusive governador e prefeito.
O ADR-0035 listou três caminhos: retirar a rota, mantê-la com regras escritas ou mantê-la como estava.

## Decisão

A rota fica, com o nome "Lado a lado" e estas regras:

1. Só parlamentares: deputados federais, senadores, deputados estaduais e distritais e vereadores.
2. Os dois perfis são da mesma casa: a Câmara dos Deputados, o Senado, a mesma assembleia ou a mesma câmara municipal.
3. Os dois lados mostram os mesmos blocos, na mesma ordem e com os mesmos títulos do perfil. Bloco que um perfil não tem aparece como "sem registro na fonte" naquele lado.
4. Todo número traz o período a que se refere.
5. Nenhum destaque de valor maior ou menor: sem cor, barra, seta, ordem por tamanho ou diferença calculada. Contagens por tipo seguem a ordem alfabética, para as duas colunas ficarem alinhadas.
6. Busca e sugestões na ordem alfabética do índice, só da casa do lado já escolhido. As sugestões são da mesma UF e em exercício.
7. A entrada é pelo perfil de cada parlamentar e pelo rodapé. O lado a lado sai do menu principal.
8. A rota `/comparar` e os parâmetros `p1` e `p2` continuam, para não quebrar links já compartilhados. Link com perfis de casas diferentes, ou com quem não é parlamentar, mostra o motivo e não monta o lado a lado.

Esta é a única exceção ao item 3 do ADR-0012.
Outra forma de pôr políticos lado a lado exige ADR novo.

## Consequências

- O título da página passa de "Comparação Parlamentar" para "Lado a lado". O teste de ponta a ponta muda junto. O nome da região "Dados comparativos" e os rótulos dos campos continuam.
- As votações nominais passam a mostrar a soma dos votos registrados e das votações nominais de cada ano, com o período e os votos por valor da fonte.
- O perfil só mostra o atalho para o lado a lado quando o cargo é de parlamentar.
- A regra de mesma casa fica em `casaLegislativa`, em `web/src/app/rotulos.ts`, com teste unitário.

## Alternativas consideradas

- Retirar a rota: descartada. Ver dois mandatos da mesma casa com os mesmos campos tem uso legítimo quando segue as regras acima.
- Manter como estava: descartada. Números sem período e totais lado a lado funcionam como ranking, o que o ADR-0012 veda.
- Mostrar a diferença entre os dois lados: descartada. Seria juízo implícito (política editorial, princípio 2).
