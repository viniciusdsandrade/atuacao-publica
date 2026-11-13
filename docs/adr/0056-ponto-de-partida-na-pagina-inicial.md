# ADR-0056: Ponto de partida na página inicial

- **Status**: Aceita
- **Data**: 2026-11-13

## Contexto

A página inicial abre com a lista de todos os políticos em exercício, em ordem alfabética, 48 cartões por vez (ADR-0012, item 5).
A lista mostra a principal função do site, mas chega sem ser pedida.

Medidas de 2026-11-13, no site publicado (2.523 políticos no índice, 1.785 em exercício):

- A página inicial tem 7.795 px de altura em 390 px e 3.275 px em 1366 px.
- Os 48 primeiros cartões são todos de nomes que começam com "A".
- A contagem "1.785 de 2.523 políticos" abre uma lista que ninguém pediu.

Problemas:

- Quem chega recebe quase dois mil nomes de mesmo peso, sem critério para escolher um. É a fadiga de escolha.
- A amostra é arbitrária e sempre a mesma: a ordem alfabética dá a primeira tela aos mesmos nomes para todo visitante.
- A lista longa sugere que há muito por ver e nenhum caminho curto.

Restrições mantidas: neutralidade dos ADR-0012, 0035 e 0036, CSP, nenhuma requisição a terceiros, WCAG 2.2 AA.

## Decisão

1. **Sem busca e sem recorte, a página inicial não lista ninguém.** No lugar da lista entra o ponto de partida, com o título "Por onde começar".
2. **O ponto de partida tem dois índices.**
   - "Pelo estado": os estados com alguém em exercício, em ordem alfabética do nome, todos do mesmo tamanho.
   - "Pelo cargo": os cargos agrupados por esfera, na ordem institucional que o site já usa nos filtros.
3. **Cada porta de entrada mostra a contagem de quem está em exercício.** O número é o que a lista vai mostrar depois do clique.
4. **A lista aparece quando a pessoa pede**: ao digitar na busca, ao escolher um estado ou um cargo, ou em "Ver todos". Filtros, contagem, cartões e "Mostrar mais" seguem como no ADR-0035.
5. **"Ver todos os N em exercício" continua a um clique**, abaixo dos índices, ao lado de "aguardando posse" e "fora de exercício". O endereço ganha `lista=completa`.
6. **Escolher uma porta de entrada cria entrada no histórico.** Voltar retorna ao ponto de partida, na rolagem em que ele estava. A tela vai aos filtros e o foco vai à contagem.
7. **"Limpar tudo" e a remoção do último filtro voltam ao ponto de partida.** Com a lista completa aberta, o chip "Lista completa" faz esse papel.
8. **Com um estado escolhido, os cargos do recorte viram chips com contagem**, no lugar da esfera. É o passo seguinte de quem começa pelo estado. Sem estado escolhido, a esfera segue como antes.
9. **A data dos dados e o estado das coletas aparecem também no ponto de partida.**

### Neutralidade

- Nenhum político aparece sem busca ou recorte. A vitrine fixa dos nomes com "A" some.
- Estados e cargos são categorias institucionais completas. A ordem é alfabética ou institucional. Nenhuma porta é maior, mais colorida ou vem antes por tamanho.
- Dentro de cada recorte a lista continua alfabética.
- A contagem é de pessoas no arquivo, igual à dos filtros.

### O que este ADR revê

- ADR-0012, item 5: a lista de 48 cartões por vez deixa de ser a tela padrão. Vale depois de busca, recorte ou "Ver todos".
- ADR-0035, página inicial: situação e esfera ficam à vista na lista, e não mais na abertura da página.
- ADR-0035, filtros: com estado escolhido, o cargo aparece em chips. Antes ficava só na lista de seleção.

## Alternativas consideradas

- **Mostrar só 12 cartões, com "ver mais"**: descartada. A página encurta, mas a amostra arbitrária fica, mais concentrada.
- **Índice de A a Z no topo da lista**: descartada. Ajuda a navegar, e a lista continua sendo a primeira coisa na tela.
- **Mapa do Brasil em blocos, um por estado**: descartada. A ordem de leitura do teclado e do leitor de tela não bate com a posição dos blocos, e a ordem alfabética é mais simples de achar.
- **Lista de seleção de estado**: descartada. Esconde as 27 opções atrás de um toque. O índice à vista se reconhece de relance.
- **Pedir a localização do aparelho**: descartada. O site não pede dado do visitante.
- **Agrupar a lista por cargo, com amostra de cada grupo**: descartada. Cada amostra repetiria o problema dos primeiros nomes.

## Consequências

- A página inicial cai de 7.795 px para 3.071 px em 390 px e de 3.275 px para 1.679 px em 1366 px. São 36 portas de entrada (27 estados e 9 cargos) e nenhum cartão.
- Quem usava a lista completa passa a dar um clique a mais, em "Ver todos". O endereço `#/?lista=completa` pode ser guardado.
- Endereços com recorte (`#/?uf=SP`, `#/?q=...`) abrem direto na lista, como antes.
- O E2E muda no mesmo commit: os fluxos que partiam da lista aberta passam a partir de `#/?lista=completa`. Entram quatro fluxos novos e a varredura do axe ganha duas rotas.
- "Limpar tudo" não cria entrada no histórico. Depois dele, o primeiro Voltar pode parecer não fazer nada.

## Limitações

- Não validado: teste com pessoas, leitor de tela real e aparelho real.
- A medida de altura compara o site publicado, com os dados de 2026-11-13, com o build novo sobre os dados de 2026-11-11.
- No celular, o índice de estados tem 14 linhas em duas colunas.
