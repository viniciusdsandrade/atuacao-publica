# ADR-0055: Refino da experiência: resposta, retorno da busca e acabamento

- **Status**: Aceita
- **Data**: 2026-11-13

## Contexto

O ADR-0012 refez a interface e o ADR-0035 reorganizou a estrutura em 2026-11-07.
A estrutura fica como está.
Este ADR trata do que acontece entre o gesto da pessoa e a resposta do site: tempo de carga, retorno da busca, toque, movimento e leitura das tabelas.

Restrições mantidas: neutralidade dos ADR-0012, 0035 e 0036, CSP `default-src 'self'`, nenhuma requisição a terceiros, fonte do sistema, WCAG 2.2 AA nos dois temas, rotas e parâmetros de URL sem mudança.

Método: leitura de `web/src`; capturas do site com os dados publicados em 2026-11-11, em 390 e 1366 px, nos dois temas; medição de carga no Chromium com rede e processador limitados; leitura de orientações públicas de UX e da plataforma web.

Problemas observados:

| Problema | Evidência | Severidade |
|---|---|---|
| Digitar na busca da inicial não muda nada na primeira tela. A contagem e os cartões ficam abaixo dos filtros. | A contagem começa a 805 px em 1366 × 800 e a 1.007 px em 390 × 844. | alta |
| Enter, ou a tecla de busca do celular, só grava o endereço. O teclado continua aberto sobre a lista. | `web/src/app/lista/lista.html`, envio do formulário | alta |
| O perfil só pede o próprio arquivo depois de o índice chegar. Quem chega por link direto baixa 105 kB de índice sem precisar. | `combineLatest` em `web/src/app/perfil/perfil.ts` | alta |
| O índice só é pedido depois de o JS baixar e iniciar. | `web/src/app/dados.ts` | média |
| JS inicial de 451,68 kB, a 48 kB do limite de aviso do build. O conversor de Markdown dos documentos entra na primeira carga. | build de produção de 2026-11-13 | média |
| Ao abrir um perfil vindo da lista, a tela mostra um esqueleto cinza, embora nome, cargo, partido e situação já estejam em memória. | `web/src/app/perfil/perfil.html` | média |
| Falha de rede e perfil inexistente dão a mesma mensagem, sem "tentar de novo". | `web/src/app/perfil/perfil.html` | média |
| Com tema escolhido diferente do aparelho, a página pisca no tema do aparelho até o site iniciar. | `aplicarTemaNoDocumento` só roda no início do Angular | baixa |
| Nenhum botão ou cartão responde ao toque além do clarão padrão do navegador. | folhas de estilo, sem `:active` | baixa |
| "Sem registro na fonte." repetido em toda linha pesa tanto quanto o dado presente. Cabeçalho de coluna numérica à esquerda, valores à direita. | tabela de votos do perfil de referência | média |
| O perfil não tem como compartilhar o endereço. | cabeçalho do perfil | média |
| A busca não acha por estado: "silva sp" não devolve os deputados federais de São Paulo. | chave de busca em `web/src/app/lista/lista.ts` | média |
| O interruptor de tema não diz o que faz. | cabeçalho | baixa |

## Fontes consultadas

Consultadas em 2026-11-13.
O que não foi confirmado numa página aberta ficou fora das decisões.

- Nielsen Norman Group, busca visível e simples: o campo fica no alto e a pessoa chega a ele de qualquer página (`nngroup.com/articles/search-visible-and-simple`).
- Nielsen Norman Group, mensagens de erro: dizer o problema específico e o próximo passo, em tom neutro (`nngroup.com/articles/error-message-guidelines`).
- Nielsen Norman Group, estados vazios: distinguir carregando, erro e sem conteúdo (`nngroup.com/articles/empty-state-interface-design`).
- Nielsen Norman Group, tabelas de dados e tabelas no celular: linha fácil de seguir, cabeçalho visível, atributos agrupados (`nngroup.com/articles/data-tables`, `nngroup.com/articles/mobile-tables`).
- Nielsen Norman Group, tabelas de comparação: todo atributo preenchido para os dois lados; ausência com texto, nunca célula vazia (`nngroup.com/articles/comparison-tables`).
- Nielsen Norman Group, sumário de página, divulgação progressiva, categorias de filtro e cartões (`nngroup.com/articles/table-of-contents`, `progressive-disclosure`, `filter-categories-values`, `cards-component`).
- GOV.UK Design System, lista de resumo: par de chave e valor, com texto que diz o que falta (`design-system.service.gov.uk/components/summary-list`).
- MDN e angular.dev, View Transitions e `withViewTransitions` (`developer.mozilla.org/en-US/docs/Web/API/View_Transition_API`, `angular.dev/api/router/withViewTransitions`).
- MDN, `light-dark()`, `text-wrap` e `::details-content`.
- web.dev, `content-visibility` e tarefas longas (`web.dev/articles/content-visibility`, `web.dev/articles/optimize-long-tasks`).
- webstatus.dev, estado de suporte de cada recurso.

Estado de suporte segundo o webstatus.dev: View Transitions no mesmo documento em todos os motores desde 2025-10; `::details-content` desde 2025-09; `interpolate-size` só no Chromium; `text-wrap: pretty` sem Firefox.
No Angular 21.2, `withViewTransitions` está marcado como prévia para desenvolvedores.

## Decisão

Todo recurso novo da plataforma entra como melhoria progressiva: sem suporte, o site funciona como antes.

### Carga

1. **Perfil sem esperar o índice.** O arquivo do perfil é pedido na hora. O índice só é consultado se o arquivo não existir, para resolver identificador antigo (fusão de perfis).
2. **Cabeçalho imediato.** Vindo da lista, o perfil mostra nome, cargo, local, partido e situação a partir do índice já em memória. Os blocos entram quando o arquivo chega. O cabeçalho não é refeito.
3. **Índice adiantado na inicial.** `web/public/arranque.js` pede o índice antes de o site iniciar e o site usa essa resposta. É arquivo próprio porque a CSP não aceita script embutido.
4. **Tema antes da primeira pintura.** O mesmo arquivo aplica o tema salvo.
5. **Páginas secundárias em arquivos separados.** Lista e perfil ficam no arquivo inicial. Detalhe anual, lado a lado, dados abertos, estado das coletas e documentos carregam à parte, baixados em segundo plano 4 segundos depois da primeira tela. No modo de economia de dados, só baixam quando abertos.
6. **Perfil pedido antes do clique.** Com o mouse parado 120 ms sobre um cartão, o perfil começa a baixar. Não vale para toque nem para economia de dados.

### Busca

7. **Retorno ao lado do campo.** Abaixo do campo, uma linha de altura fixa mostra a dica "A busca ignora acento e maiúscula." e, ao digitar, a contagem e o botão "Ver na lista".
8. **Enter leva à lista.** Enter, a tecla de busca do celular ou o botão levam a tela e o foco à contagem, logo acima dos cartões, e fecham o teclado.
9. **Termo marcado.** O trecho que casou aparece marcado no nome, no cargo e no local do cartão, com `<mark>`. O texto mostrado é o original.
10. **Estado na busca.** A chave de busca ganha a sigla e o nome do estado. O rótulo passa a "Buscar por nome, estado, município, cargo ou partido".

### Erros

11. **Falha de rede não é perfil inexistente.** Sem resposta do servidor, a tela diz "Não foi possível carregar o perfil" e oferece "Tentar de novo". Com 404, "Perfil não encontrado". Detalhe anual e documentos também ganham "Tentar de novo".

### Toque e movimento

12. **Estado pressionado** em botões, chips, cartões, segmentos e itens de menu, no lugar do clarão padrão do toque.
13. **Troca de página e de tema com esmaecer de 160 ms**, pela API View Transitions. Mudança de filtro ou de fragmento na mesma página não anima. Com `prefers-reduced-motion`, nada anima.
14. **Sanfona com altura animada** onde o navegador anima até `auto`. Nos outros e na impressão, abre de uma vez.
15. **Uma curva e duas durações** para o site todo (`--curva`, `--transicao`, `--transicao-lenta`).
16. **Ícone no interruptor de tema**: sol no claro, lua no escuro. O papel `switch` e o rótulo "Tema claro" não mudam.

### Leitura

17. **Valor ausente em tom mais baixo.** A frase é a mesma ("Sem registro na fonte."), na cor `--texto-3`, acima de 4,5:1 em toda superfície. O dado presente aparece primeiro. Nenhum dado some.
18. **Cabeçalho de coluna numérica alinhado à direita**, como os valores.
19. **"ver na fonte" em uma linha** nas tabelas largas.
20. **`text-wrap: pretty`** nos parágrafos e espaçamento de letra mais fechado em `h1` e `h2`.

### Compartilhar e inclusão

21. **Botão "Compartilhar" no perfil.** No celular e no tablet abre o compartilhamento do aparelho. No computador copia o endereço e confirma. O endereço vai sem o fragmento do bloco.
22. **Metadados Open Graph** de título e descrição, para a prévia do link. Sem endereço fixo e sem imagem.
23. **`prefers-contrast: more`**: moldura mais forte e um só tom de texto.
24. **`scrollbar-gutter: stable`**: a página não pula de lado quando a barra de rolagem aparece.

## Alternativas consideradas

- **`light-dark()` para unificar os tokens de tema**: descartada. Em navegador anterior a 2024 a declaração é inválida e o site perderia as cores. O ganho seria só de código.
- **`<link rel="preload">` para o índice**: descartada. No WebKit a pré-carga não foi reaproveitada e o índice baixou duas vezes.
- **Atalho de uma tecla para a busca ("/")**: descartada. O critério 2.1.4 da WCAG exige meio de desligar ou remapear atalho de tecla única.
- **Sugestões abaixo da busca do cabeçalho (combobox)**: adiada. Exige teste em aparelho real com teclado virtual e com leitor de tela.
- **Filtros em coluna lateral ou recolhidos no celular**: descartada. O ADR-0035 decidiu situação e esfera à vista há seis dias, e não houve teste com pessoas.
- **`content-visibility: auto` nos cartões**: descartada. O recorte cortaria a sombra e o anel de foco.
- **"Mostrar só diferenças" no lado a lado**: descartada. Destacar diferença contraria o ADR-0036.
- **Pedir o perfil no toque**: descartada. Rolar a lista começa com um toque num cartão e gastaria dados móveis.
- **Baixar as páginas secundárias logo depois da primeira tela**: descartada depois de medida. Disputou processador com o perfil e anulou o ganho no link direto.
- **Alternância entre lista compacta e cartões, aplicativo instalável e uso sem rede**: fora desta revisão.

## Consequências

- Mais um arquivo no site (`arranque.js`, 1,4 kB) e uma requisição que bloqueia a leitura do HTML por uma ida e volta.
- A linha de retorno da busca e o rótulo mais longo descem a lista: 34 px em 1366 px e 56 px em 390 px. Enter leva à lista.
- O JS passa a vir em vários arquivos. O HTML gerado declara os iniciais com `modulepreload`.
- A chave do tema existe em dois lugares (`tema.ts` e `arranque.js`). Um teste unitário confere que são iguais.
- Sem o índice em memória, o perfil mostra o esqueleto, como antes.
- O E2E ganha dois fluxos: Enter na busca e nova tentativa no perfil.
- Mudança que mexa em ordem, comparação ou destaque entre políticos continua exigindo ADR.

## Limitações

- Não validado: teste com pessoas, leitor de tela real e aparelho real.
- A medição de carga é de laboratório, no Chromium, com servidor local.
- O ganho na página inicial é pequeno: o tempo dela depende do total de bytes, que quase não mudou.
- A prévia de link em aplicativos de mensagem não foi testada.
