# ADR-0035: Revisão de UX e UI do site para o público geral

- **Status**: Aceita
- **Data**: 2026-11-07

## Contexto

O ADR-0012 refez a interface em 2026-11-02.
Depois dele entraram, sem ADR próprio de interface, a rota `/comparar`, a página `/dados-abertos`, os chips "Maiores bancadas", as redes declaradas ao TSE, as emendas e o financiamento de campanha.
Em 2026-11-07 o site publica 1.867 perfis em três esferas, com 24 fontes.

Este ADR revisa UX e UI com um objetivo.
Quem chega entende em segundos o que o site oferece, sabe onde está cada recurso e tem vontade de explorar.
A informação aparece na medida certa: sem excesso na primeira tela e sem recurso escondido a ponto de não ser descoberto.

Restrições que a revisão respeita:

- neutralidade do ADR-0012 e da política editorial: nenhuma cor, ordem, tamanho ou destaque sugere ranking ou preferência; lista alfabética; bloco vazio visível;
- paleta neutra, sem vermelho e sem verde e amarelo combinados como cor de marca;
- sem biblioteca de componentes, fonte web ou ícone externo; CSP `default-src 'self'`;
- rotas, parâmetros de URL, papéis ARIA e rótulos acessíveis de que o E2E depende (ADR-0028) não mudam; onde um texto verificado pelo teste muda, o plano marca o risco;
- WCAG 2.2 AA nos dois temas, com `forced-colors` e `prefers-reduced-motion`;
- sem padrão manipulativo.

Método: leitura de `web/src/app` e `web/src/styles`; inspeção do site publicado em 2026-11-07 no Chromium, em 320, 390, 768 e 1366 px, nos temas claro e escuro, com medição de alturas por script; cálculo das razões de contraste dos tokens pela fórmula da WCAG.
Sem build, servidor local nem suíte de testes.
Nas páginas inspecionadas não houve requisição a terceiros nem erro de console.

Medidas que orientam o diagnóstico (tema claro):

| Medida | 320 px | 390 px | 768 px | 1366 px |
|---|---|---|---|---|
| Altura do cabeçalho | 235 px | 193 px | 151 px | 61 px |
| Linhas do menu principal | 3 | 2 | 1 | 1 |
| Parte da primeira tela ocupada pelo cabeçalho | 41 % | 23 % | 15 % | 8 % |
| Topo do campo de busca | 890 px | 726 px | 684 px | 509 px |
| Topo do primeiro cartão | 1.653 px | 1.351 px | 1.242 px | 1.022 px |
| Chips de filtro na inicial (no DOM) | 19 | 19 | 19 | 19 |

No celular o botão de tema cai numa linha só dele, abaixo do menu.
No perfil de deputado federal de referência, em 390 px: o cabeçalho do perfil tem 622 px e 10 links de ação; a faixa de blocos tem 12 itens e 1.631 px escondidos à direita, sem barra de rolagem; o primeiro bloco começa a 972 px.
Na metodologia, em 1366 px, a coluna de texto tem 816 px com fonte de 17 px, cerca de 95 caracteres por linha.

## Inventário de recursos

Cliques contados a partir da página inicial, sem contar rolagem.

| Recurso | Onde aparece | Cliques (computador / celular) | Divulgação | Avaliação |
|---|---|---|---|---|
| Busca por nome, município, cargo ou partido | Inicial, depois do herói | 0 / 0, após rolar quase uma tela | imediata | esconderijo relativo: abaixo da dobra no celular |
| Situação (4 opções) | Inicial | 0 / 0 | imediata | adequado |
| Esfera (4 opções) | Inicial; no celular dentro de "Mais filtros" | 0 / 1 | imediata ou 1 clique | adequado |
| UF | Lista de seleção; 28 chips quando a esfera é estadual | 0 / 1 | imediata ou 1 clique | excesso quando vira chips |
| Cargo | Lista de seleção | 0 / 1 | imediata ou 1 clique | adequado |
| Partido | 11 chips "Maiores bancadas" e lista de seleção | 0 / 0 | imediata | excesso e tensão com a neutralidade |
| Filtros ativos e "Limpar tudo" | Inicial | 0 / 0 | imediata | adequado |
| Mostrar mais e mostrar todos | Fim da lista | 0 / 0, após 48 cartões | imediata | adequado |
| Perfil | Cartão | 1 / 1 | 1 clique | adequado |
| Sumário de blocos | Perfil: coluna no computador, faixa no celular | 1 / 1 | imediata no perfil | esconderijo no celular: faixa sem pista de rolagem |
| Fonte e atualização do bloco | `<details>` em cada bloco | 2 / 2 | sob demanda | adequado, por desenho |
| Detalhe por ano | Chips no fim de cada bloco | 2 / 2 | 1 clique no perfil | esconderijo leve: depois da tabela |
| Filtro dentro do ano | Detalhe anual | 2 / 2 | imediata | adequado |
| Filtro de órgãos por tipo e liderança | Bloco de órgãos | 2 / 2 | imediata no bloco | adequado |
| Comparar | Menu principal, rodapé e perfil | 1 clique e mais 4 interações | imediata | excesso de destaque para um recurso em tensão com o ADR-0012 |
| Dados abertos (CSV nacional) | Menu principal e rodapé | 2 / 2 | imediata | adequado |
| JSON do perfil | Perfil | 2 / 2 | imediata no perfil | excesso: mesmo peso dos links oficiais |
| Redes e sites declarados ao TSE | Perfil | 1 / 1 | imediata | excesso no celular: 6 a 8 botões |
| Estado das coletas | Link no herói e no rodapé | 1 / 1 | 1 clique | adequado |
| Tema (Auto, Claro, Escuro) | Botão no cabeçalho | 1 a 2 | imediata | no celular só o ícone, sem rótulo |
| Impressão em preto e branco | Só pelo menu do navegador | sem pista | sob demanda, sem pista | esconderijo |
| Endereço por bloco | O fragmento muda ao clicar no sumário | 2 e copiar do endereço | sob demanda, sem pista | esconderijo |
| Metodologia, Fontes, Contribuir | Menu principal e rodapé | 1 / 1 | imediata | adequado; o custo no celular é a altura do cabeçalho |
| Política editorial | Herói e rodapé | 1 / 1 | 1 clique | adequado |
| Pular para o conteúdo | Primeiro Tab | 0 | sob demanda | adequado |

## Diagnóstico por rota

Severidade: alta quando impede, engana ou fere a acessibilidade; média quando atrapalha ou quebra a consistência; baixa quando é acabamento.

### Cabeçalho e rodapé

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| Seis links de menu com o mesmo peso, mais o botão de tema. No celular quebram em duas ou três linhas e o botão cai em outra. O cabeçalho ocupa 23 % da primeira tela em 390 px e 41 % em 320 px. | `web/src/app/app.html:9-30`; `web/src/styles/casca.css:49-54`, `81-86`, `193-197` | O herói e a busca saem da primeira tela. A pessoa vê menu antes de ver o que o site é. | alta |
| O menu não separa explorar (Políticos, Comparar, Dados abertos) de entender o projeto (Metodologia, Fontes, Contribuir). | `web/src/app/app.html:9-20` | Sem hierarquia, nada orienta a primeira escolha. | média |
| Botão de tema só com ícone no celular. O ícone de monitor para "Auto" não se explica sozinho. | `web/src/styles/casca.css:111-119`; `web/src/app/tema.ts:83-92` | Recurso pouco descoberto no celular. | baixa |
| A cor da barra do navegador (`theme-color`) segue só o tema do sistema. Com o tema forçado pelo botão, a barra fica na cor do outro tema. | `web/src/index.html:12-13`; `web/src/app/tema.ts:38-47` | Barra clara sobre site escuro, ou o contrário, no celular. | baixa |
| O rodapé está adequado: descrição, dois grupos de links e licenças. | `web/src/app/app.html:37-66` | Nenhum. Registrado para o inventário. | nenhuma |

### Página inicial (`/`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| O herói tem título de 11 palavras, parágrafo de 36 palavras com seis tipos de registro e quatro esferas, parágrafo de regras com dois links e linha de estado da coleta. A busca vem depois de tudo isso: a 726 px em 390 px e a 509 px em 1366 px. | `web/src/app/lista/lista.html:1-49`; medidas acima | A ação principal fica fora da primeira tela no celular. Texto demais antes de qualquer interação. | alta |
| A frase de regras tem peso 550 e cor de texto principal; o parágrafo que descreve o site tem cor secundária. | `web/src/styles/lista.css:20-33` | Hierarquia invertida: a ressalva pesa mais que a oferta. | média |
| Os números do índice somem no celular. | `web/src/styles/lista.css:11-13`, `204-230` | O celular perde a noção de escala que no computador orienta em segundos. | média |
| "Índice gerado em" e "7 fontes com aviso nas coletas", com ícone de alerta, dentro do herói. | `web/src/app/lista/lista.html:12-29` | Jargão operacional e tom de alarme na apresentação. A informação importa, mas pertence à linha dos resultados. | média |
| Dezenove chips no painel: 4 de situação, 4 de esfera e 11 de partido. Com esfera estadual entram até 28 de UF. No celular os 11 de partido ficam à mostra e a esfera fica em "Mais filtros". | `web/src/app/lista/lista.html:85-134`; `web/src/styles/lista.css:64-71`; `web/src/app/lista/lista.ts:204-239` | Painel de 611 px em 390 px. O filtro mais útil para orientar (esfera) fica escondido; o menos neutro (bancadas) fica à mostra. | alta |
| Chips "Maiores bancadas" ordenados pelo tamanho da bancada e limitados a dez. | `web/src/app/lista/lista.html:123`; `web/src/app/lista/lista.ts:220-229` | Ver "Tensões com a neutralidade". | alta |
| Estados de carga, vazio e erro estão bem resolvidos: esqueleto, mensagem com saída e botão de tentar de novo. | `web/src/app/lista/lista.html:51-57`, `215-231`, `275-292` | Nenhum. Modelo para as outras rotas. | nenhuma |

### Perfil (`/politico/:id`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| Cabeçalho com até dez links de ação no mesmo nível: página oficial, foto, comparar, JSON e seis redes. Em 390 px o cabeçalho tem 622 px e o primeiro bloco começa a 972 px. | `web/src/app/perfil/perfil.html:68-114`; `web/src/styles/perfil.css:63-73` | A pessoa rola mais de uma tela até o conteúdo que veio buscar. O link técnico (JSON) tem o mesmo peso da página oficial. | alta |
| A faixa de blocos no celular rola de lado com a barra escondida e sem pista visual. Em 390 px há 1.631 px de chips fora da tela. | `web/src/styles/perfil.css:120-133` | Quem não arrasta a faixa não descobre os outros nove blocos. | alta |
| Os links para o detalhe anual ficam só no fim de cada bloco, depois da tabela. | `web/src/app/perfil/perfil.html:361-374` | O caminho para "todos os registros" aparece depois de até 30 linhas. | média |
| Os botões de filtro de órgãos têm 26 px de altura. O ADR-0012 fixou 32 px para botões. | `web/src/styles/tabelas.css:258-271`; medida em 390 e 1366 px | Alvo pequeno no toque. | média |
| Os selos de liderança são links com cerca de 24 px de altura: no limite do critério 2.5.8 da WCAG 2.2 e abaixo da regra do projeto. | `web/src/app/perfil/perfil.html:39-47`; `web/src/styles/componentes.css:288-301`, `355-360` | Alvo pequeno, sem folga para zoom de texto. | média |
| A barra de despesa por ano tem contraste de 1,68:1 (claro) e 2,00:1 (escuro) com a trilha. | `web/src/styles/base.css:25`, `63`, `89`; `web/src/styles/perfil.css:418-431` | A comparação entre anos do mesmo perfil quase não se vê. O gráfico é redundante com o número, mas é o único gráfico do site. | média |
| Links de fonte nas tabelas com texto genérico: "origem", "votação", "evento", "registro", "documento". | `web/src/app/perfil/tabela.html:16`, `53`, `72`, `108`, `126` | O leitor de tela lista dezenas de links iguais. O contexto da tabela atende o critério 2.4.4, mas não ajuda a fluidez. | baixa |
| Nada indica que cada bloco tem endereço próprio nem que o perfil imprime em preto e branco. | `web/src/app/perfil/perfil.html:123-145`; `web/src/styles/impressao.css` | Dois recursos prontos sem descoberta. | média |
| Nome em caixa alta nos perfis que vêm do TSE (presidente e governadores). | perfis `tse-*` no índice publicado | Quebra visual e de leitura entre perfis. É dado, não CSS. Ver "Ideias que exigem dado novo". | baixa |

### Detalhe anual (`/politico/:id/:bloco/:ano`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| Migalhas, troca de ano, contagem, filtro, cartões e estados estão corretos e consistentes. | `web/src/app/detalhe/detalhe.html` | Nenhum. | nenhuma |
| O título "Votos: 2026" não traz o nome da pessoa. O nome fica nas migalhas e na aba. | `web/src/app/detalhe/detalhe.html:14`; `web/src/app/detalhe/detalhe.ts:115-117` | Captura de tela ou impressão sai sem identificar o político. | baixa |

### Comparar (`/comparar`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| "Votações registradas" mostra `participacao_votacoes.total`, que é o número de linhas anuais do bloco (5 e 4 no exemplo publicado), não de votações. | `web/src/app/comparar/comparar.ts:175-177`; `web/src/app/comparar/comparar.html:166-171`, `183-188` | Número errado apresentado como comparação. | alta |
| "sem partido" e "sem registro" em vez de "sem registro na fonte". "Sem partido" afirma um fato que a fonte não afirma. | `web/src/app/comparar/comparar.html:39`, `68`, `92`, `122`; princípio 5 da política editorial | Fere a política editorial. | alta |
| Campo de busca com `outline: none`. O anel de foco global usa `:where()`, com especificidade zero, e perde. | `web/src/styles/comparar.css:94-101`; `web/src/styles/base.css:208-211`; medição com o campo em foco: `outline-style: none` | Teclado sem foco visível (WCAG 2.4.7). O axe não testa foco. | alta |
| Lista de resultados com `role="listbox"` e filhos `<li><button>`. | `web/src/app/comparar/comparar.html:61`, `115` | Papel ARIA sem os filhos exigidos. Só não falha no CI porque a lista não existe no estado inicial, que é o único varrido. | alta |
| Totais de despesa e de proposições sem o período de cada mandato. | `web/src/app/comparar/comparar.html:203-241`, `291-319`; `web/src/app/comparar/comparar.ts:184-186`, `216-222` | Mandatos de duração diferente parecem comparáveis. Convite ao juízo que o ADR-0012 veda. | alta |
| Nenhuma mensagem quando a busca não acha ninguém, quando `p1` ou `p2` do endereço não existe, nem durante a carga. | `web/src/app/comparar/comparar.html:60`, `329-334`; `web/src/app/comparar/comparar.ts:88-89` (sinais de erro sem uso no template) | Silêncio em três estados. | média |
| Rótulo "Sugestões da mesma bancada/UF", mas o código sugere mesmo cargo e mesma UF. | `web/src/app/comparar/comparar.html:114`; `web/src/app/comparar/comparar.ts:113-119` | O texto diz uma coisa e a lista mostra outra. | média |
| Situação sempre com o selo de "Em exercício", mesmo para quem está fora. | `web/src/app/comparar/comparar.html:149`, `156` | O marcador contradiz o texto e o padrão do perfil. | média |
| Nome e cargo com `white-space: nowrap` e reticências. Em 390 px o partido some. | `web/src/styles/comparar.css:49-57`, `63-69`; captura em 390 px | Perda de conteúdo e risco no critério 1.4.12. | média |
| Títulos em Caixa Alta Por Palavra ("Comparação Parlamentar", "Identificação Institucional"). O resto do site usa caixa de frase. | `web/src/app/comparar/comparar.html:11`, `142`, `163`, `204`, `245`, `292` | Duas vozes no mesmo site. | baixa |
| A página define a própria largura e margem interna, somadas às de `main`. Em 390 px o conteúdo começa a 32 px da borda; nas outras rotas, a 16 px. | `web/src/styles/comparar.css:3-7`; `web/src/styles/casca.css:121-126`, `206-208`; capturas | Desalinhamento visível entre rotas. | baixa |
| `var(--r-cartao)` não existe: o raio renderizado é 0 px. Textos de 12 px. Ponto de quebra em `600px`. | `web/src/styles/comparar.css:91`, `105`, `129`, `152`, `243`, `258`; medição do raio | Cantos retos onde o resto é arredondado; texto abaixo do mínimo que o site pratica. | baixa |

### Dados abertos (`/dados-abertos`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| Campo de filtro com `outline: none`, sem rótulo visível, e `aria-label` ("Filtrar por UF") diferente do placeholder ("Filtrar por UF ou nome..."). | `web/src/styles/dados-abertos.css:124-131`; `web/src/app/dados-abertos/dados-abertos.html:118-127`; medição com o campo em foco: `outline-style: none` | Foco invisível no teclado. Nome acessível diferente do visível. | alta |
| Jargão: "Dumps Nacionais Consolidados", "REST / Estático", "UTF-8" como "Estrutura". | `web/src/app/dados-abertos/dados-abertos.html:30`, `88-98`, `115` | O público geral não sabe o que é "dump". | média |
| O selo de formato "CSV" usa o estilo de "Em exercício" (ponto azul); os demais formatos usam o estilo de partido. | `web/src/app/dados-abertos/dados-abertos.html:35`, `61`, `87`, `134` | O mesmo sinal significa duas coisas. | média |
| Sem esqueleto nem mensagem quando o manifesto falha: a página mostra só o cartão estático do índice. | `web/src/app/dados-abertos/dados-abertos.ts:23`; `web/src/app/dados-abertos/dados-abertos.html:32`, `58` | Falha silenciosa. | média |
| A citação para copiar aponta para um domínio próprio. O site publicado está em outro endereço. Não validado se o domínio pertence ao projeto. | `web/src/app/dados-abertos/dados-abertos.html:18`; `web/src/app/dados-abertos/dados-abertos.ts:69-70` | A citação pode levar a endereço errado. | média |
| 27 cartões de UF, um por estado, cada um com botão. | `web/src/app/dados-abertos/dados-abertos.html:130-147`; `web/src/styles/dados-abertos.css:133-137` | Muita área para pouca informação. Uma tabela de 27 linhas resolve. | baixa |
| Títulos em Caixa Alta Por Palavra; rótulos de 12 px em maiúsculas; margem dupla; `--r-cartao`. | `web/src/app/dados-abertos/dados-abertos.html:9`, `152`; `web/src/styles/dados-abertos.css:3-7`, `80-85`, `119`, `160-165` | As mesmas inconsistências da rota de comparação. | baixa |
| Classe `botao--secundario` sem regra CSS. | `web/src/app/dados-abertos/dados-abertos.html:21`, `76`; `web/src/app/comparar/comparar.html:17` | Nada quebra, mas indica vocabulário visual paralelo. | baixa |

### Estado das coletas (`/estado`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| "Coleta incompleta" e "Fonte desatualizada" usam o mesmo selo de aviso. "Coleta concluída" usa o selo de "Em exercício". | `web/src/app/estado/estado.ts:52-68` | Dois estados com o mesmo sinal; um sinal com dois significados no site. | média |
| 15 das 24 coletas aparecem só com o identificador técnico (`alesp`, `cldf`, `cmsp`), porque o mapa de nomes parou nas fontes federais. | `web/src/app/estado/estado.ts:11-21` | A pessoa não sabe que fonte é cada item. | média |
| Texto, esqueleto e erro estão corretos. | `web/src/app/estado/estado.ts:27-116` | Nenhum. | nenhuma |

### Documentos (`/metodologia`, `/fontes`, `/politica-editorial`, `/contribuir`)

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| Coluna de texto de 52 rem. Em 1366 px a prosa tem 816 px com fonte de 17 px: cerca de 95 caracteres por linha. O token `--largura-leitura` (46 rem) existe e não é usado. | `web/src/styles/doc.css:229-234`; `web/src/styles/base.css:31`; medição | Linha longa cansa em documento extenso. A metodologia tem 10.936 px de altura em 1366 px. | média |
| "Voltar ao topo" só no fim do documento. | `web/src/app/doc/doc.html:52-56` | Em documento de 21.863 px (390 px) o botão aparece tarde. O sumário fixo compensa no computador; no celular, não. | baixa |
| Sem migalhas, ao contrário das outras rotas. | `web/src/app/doc/doc.html` | Orientação só pelo item ativo do menu. Aceitável. | baixa |

### Transversal: tokens, tipografia e estados

| Problema | Evidência | Impacto | Severidade |
|---|---|---|---|
| Separação fraca entre fundo, superfície e borda: superfície sobre fundo 1,08:1; borda sobre fundo 1,29:1 (claro). | `web/src/styles/base.css:8-15`; cálculo | Cartões e painéis se destacam pouco; a página parece um bloco cinza contínuo. | média |
| O anel de foco global usa `:where()`, com especificidade zero. Qualquer `outline: none` o anula. | `web/src/styles/base.css:208-211`; `web/src/styles/comparar.css:100`; `web/src/styles/dados-abertos.css:130` | Regressões de foco passam despercebidas. Já aconteceu duas vezes. | alta |
| Tamanho mínimo de texto não é regra: 12 px em quatro lugares. | `web/src/styles/dados-abertos.css:81`, `161`; `web/src/styles/comparar.css:105`, `152` | Abaixo do mínimo que o resto do site pratica (13 px). | baixa |
| Seis variantes de rótulo em maiúsculas pequenas, cada uma com a própria regra. | `web/src/styles/perfil.css:285-293`, `517-525`; `web/src/styles/doc.css:267-275`; `web/src/styles/casca.css:158-165`; `web/src/styles/dados-abertos.css:80-85`; `web/src/styles/comparar.css:103-110` | Mais CSS e pequenas diferenças entre rotas. | baixa |
| O vocabulário de ausência varia: "Sem registro na fonte.", "sem registro", "sem partido", "(vazio na fonte)", "a fonte não informa temas", "não se aplica". | `web/src/app/rotulos.ts:108`; `web/src/app/comparar/comparar.html:39`, `68`; `web/src/app/perfil/tabela.html:34`, `50`, `90` | Algumas distinções são legítimas (vazio na fonte, não se aplica). "sem partido" e "sem registro" não são. | média |
| O selo "ativo" significa "em exercício", "coleta concluída", "em andamento" e "CSV". | `web/src/styles/componentes.css:318-322`; `web/src/app/estado/estado.ts:54`; `web/src/app/perfil/tabela.html:221`; `web/src/app/dados-abertos/dados-abertos.html:35` | Um sinal, quatro sentidos. | média |

## Tensões com a neutralidade

### Chips "Maiores bancadas"

O ADR-0012, item 3, diz que a interface não ordena nem compara políticos.
Os chips ordenam partidos pelo tamanho da bancada, mostram os dez maiores e chamam o grupo de "Maiores bancadas" (`web/src/app/lista/lista.ts:220-229`).
Os outros 37 partidos ficam só na lista de seleção.
O destaque é estrutural: quem é grande ganha um botão na primeira tela; quem é pequeno, não.
A ordem muda com os filtros, então um partido aparece e some conforme o recorte.

A regra do ADR fala de políticos, não de partidos.
Mas o princípio 1 da política editorial (mesma profundidade e mesma linguagem para todos) vale para o conjunto.
Este ADR trata o caso como quebra da regra, e não como exceção que mereça ADR próprio.

Decisão: o grupo passa a chamar "Partidos neste recorte", em ordem alfabética, e só aparece quando o recorte atual tem de 2 a 12 partidos (por exemplo, depois de escolher uma UF ou um cargo).
Acima disso, só a lista de seleção, que já é alfabética.
O `role="radiogroup"` e os `radio` com o nome da sigla continuam.
O teste `web/e2e/navegacao.spec.ts:37-45` segue válido com os dados sintéticos, que têm poucos partidos.
Com dados reais a página inicial deixa de mostrar chips de partido.

### Rota `/comparar`

A rota põe dois parlamentares lado a lado (`web/src/app/comparar/comparar.html:139-320`).
O ADR-0012, item 3, veda comparar políticos e manda a barra de despesa comparar só períodos do mesmo perfil.
A rota entrou pelo item 18 do roadmap em 2026-11-06, sem ADR que revisasse o item 3.
O rodapé da página afirma que o site não produz ranking, mas a tela coloca dois totais de despesa e dois totais de proposição lado a lado, sem o período de cada mandato.
Dois números lado a lado é a forma mais simples de ranking.

Três caminhos. Todos exigem ADR novo, porque mudam a regra do ADR-0012:

1. Retirar a rota. É o mais simples e o mais coerente com o ADR-0012. Perde o atalho do perfil e exige retirar dois testes do E2E (`web/e2e/navegacao.spec.ts:96-110`; `web/e2e/acessibilidade.spec.ts:14`).
2. Manter como "lado a lado", com regras escritas: só dois perfis da mesma casa; os mesmos blocos para os dois; todo número com o período a que se refere; nenhum destaque de valor maior ou menor; sugestões em ordem alfabética; entrada pelo perfil e pelo rodapé, fora do menu principal. A rota, os parâmetros `p1` e `p2` e os rótulos dos campos não mudam.
3. Manter como está. Incompatível com o ADR-0012 e com a política editorial.

Decisão: caminho 2, com as regras no ADR-0036.
As correções factuais da seção "Mudanças por rota" valem em qualquer caminho.

### Outros pontos

- "sem partido" na comparação afirma um dado negativo (princípio 5 da política editorial).
- O selo de "em exercício" marcando um formato de arquivo dilui o sinal que identifica a situação do político.
- Os contadores por tipo de proposição na comparação vão do maior para o menor (`web/src/app/comparar/comparar.ts:188-190`). Dentro do mesmo perfil isso é aceitável, como a barra de despesa.

## Decisão

### Princípios da revisão

1. A primeira tela responde "o que é isto" e "o que eu faço aqui" em uma leitura: nome, uma frase, a busca. O resto vem depois.
2. Cada recurso aparece no nível em que é usado. Busca e situação, sempre. Esfera, cargo, UF e partido, à vista no computador e a um clique no celular. Fonte, JSON, impressão e endereço por bloco, sob demanda, mas com pista visível.
3. Um sinal, um significado. O ponto azul é "em exercício". Formato de arquivo é selo neutro. Aviso é âmbar, com forma própria para incompleto e para falha.
4. Mesma linguagem em todas as rotas: caixa de frase nos títulos, "sem registro na fonte" para ausência, mínimo de 13 px, margens de `main` e nada mais.
5. Nada no CSS vence o anel de foco. `outline: none` só quando houver anel substituto em `:focus-visible` ou `:focus-within`.
6. Mudança que mexa em ordem, comparação ou destaque entre políticos exige ADR, como o ADR-0012 já diz.

### Mudanças por rota

#### Cabeçalho e rodapé

- Abaixo de 64 rem (celular e tablet): uma linha com a marca e o botão "Menu", com `aria-expanded`. O botão abre um painel com a busca (nas páginas internas), as páginas do site e o tema. A lista continua um `nav` com `aria-label="Principal"`. Altura alvo de 64 px, contra 193 px hoje em 390 px. Motivo: devolver a primeira tela ao conteúdo.
- No painel, o tema aparece como três opções com texto (Auto, Claro, Escuro). Motivo: dar rótulo ao recurso onde hoje só há ícone.
- A partir de 64 rem: dois grupos no mesmo menu. "Políticos" e "Dados abertos" primeiro, em peso normal. "Metodologia", "Fontes" e "Contribuir" depois de um separador, em peso menor. O botão de tema continua a alternar, com o mesmo rótulo acessível que o teste unitário verifica. Motivo: separar explorar de entender o projeto.
- "Comparar" sai do menu principal e fica no perfil e no rodapé (ver "Tensões"). Nessa página, o item "Políticos" fica marcado, como no perfil.
- A partir de 72 rem, nas páginas internas, um campo de busca no cabeçalho leva à página inicial com `?q=`. Abaixo disso, a busca fica no painel do menu.
- `theme-color` com a nova `--superficie`, que é a cor do cabeçalho. Com o tema forçado pelo botão, a cor acompanha o tema escolhido.
- Rodapé: ao lado de "Estado das coletas" entra a data da última atualização dos dados, que sai do herói.

#### Página inicial

- Herói enxuto. O `h1` mantém o começo "Atividade de políticos brasileiros", que o E2E verifica, e perde a cauda: "Atividade de políticos brasileiros, com fonte e data". Uma frase de apoio com até 20 palavras: "Votos, despesas, proposições, discursos, candidaturas e atos de quem exerce ou exerceu mandato federal, estadual ou municipal desde 2022." Motivo: dizer o que o site é em uma leitura.
- A busca entra no herói, logo abaixo da frase. Meta: topo do campo abaixo de 420 px em 390 px (hoje 726 px) e de 300 px em 1366 px (hoje 509 px). Motivo: a ação principal na primeira tela.
- A frase "Sem nota, ranking nem juízo. O que a fonte não publica aparece como 'sem registro na fonte'." vai para baixo da busca, em texto secundário, com os links Metodologia e Política editorial. Continua na primeira tela do computador. Motivo: a ressalva importa, mas não antes da oferta.
- Os números viram uma linha compacta no celular ("1.867 políticos · 1.167 em exercício · 24 fontes") e continuam cartões no computador. Motivo: escala orienta, e cabe em uma linha.
- "Índice gerado em" e "fontes com aviso" saem do herói e vão para a linha acima dos resultados: "1.167 de 1.867 políticos · dados de 07/11/2026 · 7 fontes com aviso", com o link para `/estado`. Motivo: é informação de rodapé da lista, não de apresentação.
- Filtros: situação e esfera sempre visíveis, inclusive no celular (quatro chips cabem em duas linhas). Cargo, UF e partido em "Mais filtros" no celular e à vista no computador. Chips de UF só a partir de 48 rem. Chips de partido conforme a regra da seção "Tensões". Motivo: o filtro que orienta (esfera) à vista; o que ranqueia, fora.
- Cartões, contagem, "Mostrar mais", vazio e erro: sem mudança.

#### Perfil

- Cabeçalho em três níveis. Nível 1: avatar, nome, cargo e local, situação, partido e o aviso de posse ou saída. Nível 2: links oficiais e foto, como botões. Nível 3: linha discreta com "Lado a lado", "JSON do perfil" e "Imprimir ou salvar em PDF", e as redes declaradas ao TSE como links em linha, com o rótulo de declaração do próprio candidato. A lista mantém `aria-label="Redes e sites declarados ao TSE"`. Motivo: o que a pessoa veio ver sobe; o técnico desce sem sumir.
- Faixa de blocos no celular com degradê nas bordas quando houver conteúdo escondido (`mask-image`, desligado em `forced-colors`). Alternativa de menor risco: chips em até duas linhas com quebra, sem rolagem lateral, quando houver até 8 blocos. Motivo: dar pista do que está fora da tela.
- Cada bloco ganha um link de âncora ao lado do título, com o ícone `link` já existente e nome acessível "Link para o bloco {título}". O endereço por bloco já existe; só ganha pista.
- Os chips "Todos os registros por ano" sobem para o topo do bloco, ao lado da proveniência. A tabela "Mais recentes" fica abaixo. Motivo: o caminho para tudo antes da amostra.
- Botões de filtro de órgãos e selos clicáveis com `min-height: 2rem` (32 px), conforme o ADR-0012.
- Barra de despesa com o novo token `--barra` (3,43:1 e 4,09:1 sobre a trilha).
- Links de fonte nas tabelas com texto "ver na fonte" e, quando houver, "ver snapshot". Motivo: links distintos para o leitor de tela.

#### Detalhe anual

- Título com o nome: "Votos de {nome} em 2026". Migalhas e `h1` continuam. Baixa prioridade.

#### Comparar

Correções que não dependem do ADR novo:

- "Votações registradas" passa a somar `votos_registrados` das linhas de participação e a mostrar o período: "319 votos registrados em 507 votações nominais, 2022 a 2026".
- Despesas e proposições mostram o período de cada perfil, a partir dos anos com registro.
- "sem registro na fonte" em todo lugar. Sugestões com o rótulo correto ("Mesmo cargo e mesma UF"). Selo de situação igual ao do perfil.
- Mensagem de "nenhum resultado" na busca, mensagem de perfil não encontrado para `p1` ou `p2` inválidos e esqueleto durante a carga.
- Lista de resultados sem `role="listbox"`: lista comum de botões, com a contagem em `aria-live="polite"`.
- Nome e cargo com quebra de linha em vez de reticências. Títulos em caixa de frase. Margens de `main`. Foco visível. Campos de busca com o mesmo componente da página inicial.
- O `h1` "Comparação Parlamentar" e os rótulos dos campos ficam até o ADR novo, porque o E2E os verifica (`web/e2e/navegacao.spec.ts:99-103`). A mudança para "Lado a lado" entra com o ADR e com o ajuste do teste no mesmo commit.

#### Dados abertos

- Título "Dados abertos", em caixa de frase. O E2E verifica "Dados Abertos e Downloads" (`web/e2e/navegacao.spec.ts:115`); o teste muda no mesmo commit.
- Três seções: "Arquivos completos" (CSV nacional, CSV federal, JSON do índice), "Arquivos por estado" como tabela (UF, registros, tamanho, baixar) e "Dicionário de dados". Sem "dump", "REST / Estático" e "UTF-8" como estrutura. O cartão do índice diz "JSON · 1.867 perfis · UTF-8".
- Selo de formato neutro (`.selo` base) nos três formatos.
- Campo de filtro com rótulo visível "Filtrar por estado", `aria-label` igual ao rótulo e foco visível.
- Esqueleto durante a carga do manifesto e mensagem de erro com saída, no padrão da inicial.
- A citação usa o endereço publicado até o mantenedor confirmar o domínio próprio.

#### Estado das coletas

- Três marcadores distintos. Concluída: selo neutro com o ícone `verificado`. Incompleta: selo âmbar com meio círculo. Falha: selo âmbar com traço. Sem coleta: selo neutro com traço, como hoje. Motivo: o ponto azul volta a significar só "em exercício".
- Resumo no topo: "17 de 24 fontes com coleta concluída, 2 incompletas, 5 com falha".
- Nome legível para as 24 coletas, como em `docs/FONTES.md`.
- Link de cada coleta para a seção dela em Fontes: fica fora. Os títulos de `docs/FONTES.md` seguem o catálogo de fontes, não os passos de coleta, e o link quebraria sem aviso quando um título mudasse.

#### Documentos

- Coluna de texto com `--largura-leitura` (40 rem). Em 1366 px, cerca de 72 caracteres por linha.
- No celular, o botão "Neste documento" fica fixo no topo, como a faixa do perfil.
- Migalhas não entram.

### Paleta e tipografia

Direção: papel e tinta.
Neutros frios com um toque de azul, uma única cor de destaque em azul-tinta institucional e âmbar só para aviso.
O azul é a cor dos portais oficiais brasileiros (gov.br, Senado, TSE) e não identifica campo político.
Nenhum vermelho; nenhum verde com amarelo.
A mudança é de ajuste, não de identidade: os matizes ficam, os passos entre fundo, superfície e borda crescem, e a barra de despesa passa a ser visível.

Problemas concretos dos tokens atuais, com razão de contraste:

- `--barra` sobre `--superficie-3`: 1,68:1 no claro e 2,00:1 no escuro. Sobre `--superficie`: 2,05:1 e 2,54:1. Abaixo de 3:1.
- `--superficie` sobre `--fundo`: 1,08:1 no claro e 1,07:1 no escuro. Cartão quase igual ao fundo.
- `--borda` sobre `--fundo`: 1,29:1 no claro. A moldura do cartão some sobre a página.
- `--largura-leitura` definido e nunca usado; `--r-cartao` usado quatro vezes e nunca definido.
- `--aviso` cobre dois estados (incompleta e falha) e o selo "ativo" cobre quatro sentidos.
- O anel de foco (`--foco`) está correto em contraste, mas qualquer regra vence a declaração global.

Tokens de cor de `web/src/styles/base.css`, hoje e propostos:

| Token | Claro hoje | Claro proposto | Escuro hoje | Escuro proposto | Motivo |
|---|---|---|---|---|---|
| `--fundo` | `#f4f6f8` | `#eef1f4` | `#0e1216` | `#0e1317` | superfície sobre fundo passa de 1,08:1 para 1,13:1 |
| `--superficie` | `#ffffff` | `#ffffff` | `#151a20` | `#161c23` | cartão branco; no escuro um passo acima do fundo |
| `--superficie-2` | `#eef1f5` | `#e7ebf0` | `#1c232b` | `#1e262f` | cabeçalho de tabela e caixas se distinguem da superfície |
| `--superficie-3` | `#e4e9ef` | `#d9dfe6` | `#252e38` | `#29323c` | trilha da barra e esqueleto |
| `--texto` | `#141a21` | `#121820` | `#e7ebf0` | `#e9edf2` | sem mudança perceptível |
| `--texto-2` | `#424e5c` | `#3b4754` | `#b6c0cb` | `#bac4cf` | texto secundário acima de 7:1 em toda superfície |
| `--texto-3` | `#566170` | `#56626f` | `#9aa6b3` | `#9aa6b3` | rótulos pequenos acima de 4,5:1 em toda superfície |
| `--borda` | `#d5dbe3` | `#c6ced7` | `#2a333d` | `#303a45` | moldura visível sobre o fundo |
| `--borda-controle` | `#727f8e` | `#66727f` | `#728090` | `#7b8999` | borda de campo acima de 4:1 |
| `--destaque` | `#1f4e79` | `#1b4d7e` | `#8ab4e8` | `#90b9ea` | link e marca; um pouco mais de saturação |
| `--destaque-forte` | `#163b5d` | `#143a60` | `#b3d0f5` | `#b9d4f5` | hover do botão primário |
| `--destaque-suave` | `#e3edf8` | `#dfe9f4` | `#1b2c42` | `#1c2e46` | fundo de item ativo e avatar |
| `--sobre-destaque` | `#ffffff` | `#ffffff` | `#0e1216` | `#0e1317` | texto sobre destaque |
| `--aviso` | `#7a4100` | `#7a4100` | `#f0b45b` | `#f0b45b` | mantém |
| `--aviso-fundo` | `#fff4e0` | `#fcf1dc` | `#2b2113` | `#2b2113` | um passo mais distante da superfície |
| `--aviso-borda` | `#e3b867` | `#d8ab5a` | `#6b5128` | `#6b5128` | decorativa; o ícone e o texto carregam o aviso |
| `--foco` | `#0a58ca` | `#0a58ca` | `#8ab4f8` | `#8ab4f8` | mantém; acima de 5:1 em toda superfície |
| `--barra` | `#9db8d6` | `#4f78a5` | `#3d5c82` | `#6d93c2` | acima de 3:1 sobre a trilha |

Outros tokens: `--largura-leitura` passa de `46rem` para `40rem` e passa a ser usado por `.prosa`.
`--r-cartao` não é criado; os quatro usos passam a `--r-2`.
Fontes, raios, sombras e transição não mudam.
A paleta de impressão (`web/src/styles/impressao.css`) não muda.

Razões de contraste da paleta proposta (claro / escuro). Texto exige 4,5:1; controle e gráfico exigem 3:1.

| Par | Uso | Claro | Escuro |
|---|---|---|---|
| `--texto` sobre `--fundo` | corpo sobre a página | 15,74 | 15,89 |
| `--texto` sobre `--superficie` | corpo em cartão | 17,84 | 14,59 |
| `--texto` sobre `--superficie-2` | corpo em caixa | 14,90 | 13,00 |
| `--texto` sobre `--superficie-3` | número sobre trilha | 13,29 | 11,05 |
| `--texto-2` sobre `--fundo` | rodapé | 8,36 | 10,57 |
| `--texto-2` sobre `--superficie` | texto secundário | 9,48 | 9,71 |
| `--texto-2` sobre `--superficie-2` | cabeçalho de tabela | 7,92 | 8,65 |
| `--texto-2` sobre `--superficie-3` | texto sobre trilha | 7,06 | 7,36 |
| `--texto-3` sobre `--superficie` | rótulo pequeno | 6,23 | 6,92 |
| `--texto-3` sobre `--superficie-2` | rótulo em caixa | 5,20 | 6,17 |
| `--texto-3` sobre `--superficie-3` | rótulo sobre trilha | 4,64 | 5,25 |
| `--destaque` sobre `--superficie` | link em cartão | 8,72 | 8,42 |
| `--destaque` sobre `--fundo` | link na página | 7,69 | 9,17 |
| `--destaque` sobre `--superficie-2` | link em caixa | 7,28 | 7,51 |
| `--destaque` sobre `--destaque-suave` | item ativo, selo, avatar | 7,10 | 6,75 |
| `--sobre-destaque` sobre `--destaque` | botão primário, chip marcado | 8,72 | 9,17 |
| `--sobre-destaque` sobre `--destaque-forte` | hover do botão primário | 11,64 | 12,27 |
| `--aviso` sobre `--aviso-fundo` | texto de aviso | 7,26 | 8,56 |
| `--texto-2` sobre `--destaque-suave` | texto do herói | 7,72 | 7,77 |
| `--texto` sobre `--destaque-suave` | título do herói | 14,53 | 11,68 |
| `--borda-controle` sobre `--superficie` | borda de campo e chip | 4,91 | 4,80 |
| `--borda-controle` sobre `--superficie-2` | borda de campo em caixa | 4,10 | 4,28 |
| `--borda-controle` sobre `--fundo` | borda de chip na página | 4,33 | 5,23 |
| `--foco` sobre `--fundo` | anel de foco | 5,68 | 8,86 |
| `--foco` sobre `--superficie` | anel de foco em cartão | 6,44 | 8,14 |
| `--foco` sobre `--superficie-2` | anel de foco em caixa | 5,38 | 7,25 |
| `--barra` sobre `--superficie-3` | barra sobre a trilha | 3,43 | 4,09 |
| `--barra` sobre `--superficie` | barra sobre o cartão | 4,60 | 5,39 |
| `--destaque` sobre `--superficie-3` | borda ativa sobre trilha | 6,49 | 6,38 |
| `--aviso-borda` sobre `--aviso-fundo` | decorativa, sem exigência | 1,90 | 2,13 |
| `--borda` sobre `--superficie` | decorativa, sem exigência | 1,59 | 1,48 |
| `--borda` sobre `--fundo` | decorativa, sem exigência | 1,40 | 1,62 |
| `--superficie` sobre `--fundo` | passo de superfície | 1,13 | 1,09 |
| `--superficie-2` sobre `--superficie` | passo de superfície | 1,20 | 1,12 |

Tipografia:

- Fonte do sistema, como no ADR-0012.
- Corpo em 1 rem com entrelinha 1,55. Prosa dos documentos em 1,0625 rem com entrelinha 1,7, em coluna de 40 rem.
- Mínimo de 0,8125 rem (13 px) para qualquer texto. Os quatro usos de 0,75 rem sobem.
- Uma classe só para rótulo de seção em maiúsculas pequenas: 0,8125 rem, peso 700, espaçamento 0,04 em, cor `--texto-3`. Substitui as seis variantes.
- Títulos em caixa de frase em todas as rotas. Peso 700 em `h1` e `h2`, como hoje.
- Números com algarismos tabulares (`.numero`) e valores em moeda alinhados à direita, como hoje.
- Nome de pessoa como vem da fonte. A caixa alta dos perfis do TSE se resolve no dado, não no CSS.

### Plano em fases

Esforço: P até meio dia, M até dois dias, G acima disso.
Risco para o E2E e o axe: baixo quando nenhum seletor ou asserção muda; médio quando um texto verificado muda e o teste é ajustado no mesmo commit; alto quando um fluxo testado some.

#### Fase 1: CSS e tokens

| # | Item | Arquivos | Esforço | Risco E2E e axe | ADR novo |
|---|---|---|---|---|---|
| 1 | Novos valores dos tokens de cor e `theme-color` | `web/src/styles/base.css`, `web/src/index.html` | P | baixo; contrastes conferidos acima | não |
| 2 | `--r-cartao` vira `--r-2` nos quatro usos | `web/src/styles/comparar.css`, `web/src/styles/dados-abertos.css` | P | baixo | não |
| 3 | Remover `outline: none` dos dois campos; anel em `:focus-within` nos contêineres de busca | `web/src/styles/comparar.css`, `web/src/styles/dados-abertos.css` | P | baixo | não |
| 4 | Retirar largura e margem próprias das páginas de comparação e de dados abertos | `web/src/styles/comparar.css`, `web/src/styles/dados-abertos.css` | P | baixo | não |
| 5 | Mínimo de 13 px e classe única de rótulo de seção | `web/src/styles/componentes.css`, `perfil.css`, `doc.css`, `casca.css`, `dados-abertos.css`, `comparar.css` e os templates que usam as classes antigas | M | baixo | não |
| 6 | `min-height: 2rem` em botões de filtro de tabela e em selos clicáveis | `web/src/styles/tabelas.css`, `web/src/styles/componentes.css` | P | baixo | não |
| 7 | Barra de despesa com o novo `--barra` e borda na trilha em `forced-colors` | `web/src/styles/perfil.css` | P | baixo | não |
| 8 | `.prosa` com `--largura-leitura` | `web/src/styles/doc.css` | P | baixo | não |
| 9 | Degradê nas bordas da faixa de blocos no celular | `web/src/styles/perfil.css` | P | baixo; desligar em `forced-colors` | não |
| 10 | Nome e cargo da comparação com quebra em vez de reticências | `web/src/styles/comparar.css` | P | baixo | não |
| 11 | Números do herói como linha compacta no celular | `web/src/styles/lista.css` | P | baixo | não |
| 12 | Marca e botão de menu na mesma linha no celular (feito com o item 13) | `web/src/styles/casca.css` | P | baixo | não |

#### Fase 2: estrutura

| # | Item | Arquivos | Esforço | Risco E2E e axe | ADR novo |
|---|---|---|---|---|---|
| 13 | Menu com botão `aria-expanded` abaixo de 64 rem; dois grupos a partir de 64 rem; "Comparar" sai do menu principal | `web/src/app/app.html`, `web/src/app/app.ts`, `web/src/app/icone/icone.ts`, `web/src/styles/casca.css` | M | baixo; o teste de teclado usa só o link de pular; conferir no axe o nome do botão | não; a saída de "Comparar" do menu fica registrada no ADR-0036 |
| 14 | Herói enxuto com a busca dentro; ressalva abaixo da busca | `web/src/app/lista/lista.html`, `web/src/styles/lista.css` | M | médio; o `h1` mantém "Atividade de políticos brasileiros" e o rótulo mantém "Buscar por nome" | não |
| 15 | Linha de estado acima dos resultados; herói sem data e sem aviso | `web/src/app/lista/lista.html`, `web/src/app/lista/lista.ts`, `web/src/styles/lista.css` | P | baixo | não |
| 16 | Esfera visível no celular; chips de UF só a partir de 48 rem; partido alfabético e só com até 12 partidos no recorte | `web/src/app/lista/lista.html`, `web/src/app/lista/lista.ts`, `web/src/styles/lista.css` | M | médio; o teste de UF usa a lista de seleção, que fica; o teste do chip PT depende de os dados sintéticos terem até 12 partidos | não; volta à regra do ADR-0012 |
| 17 | Cabeçalho do perfil em três níveis | `web/src/app/perfil/perfil.html`, `web/src/styles/perfil.css` | M | baixo; a lista de redes mantém o nome acessível | não |
| 18 | Chips de ano no topo do bloco | `web/src/app/perfil/perfil.html`, `web/src/styles/perfil.css` | P | baixo; o teste de emendas acha o link pelo nome dentro do bloco | não |
| 19 | Correções factuais, de texto, de ARIA e de estados na comparação | `web/src/app/comparar/comparar.html`, `web/src/app/comparar/comparar.ts`, `web/src/styles/comparar.css` | M | baixo se o `h1` e os rótulos ficarem; o axe passa a aceitar a lista de resultados | não para as correções; sim para o reenquadramento |
| 20 | Dados abertos: títulos, tabela por estado, selo neutro, rótulo do filtro, esqueleto e erro | `web/src/app/dados-abertos/dados-abertos.html`, `dados-abertos.ts`, `web/src/styles/dados-abertos.css` | M | médio se o `h1` mudar; ajustar `web/e2e/navegacao.spec.ts:115`; o seletor do CSV nacional fica | não |
| 21 | Estado das coletas com marcadores distintos, resumo e nome de todas as coletas | `web/src/app/estado/estado.ts`, `web/src/styles/componentes.css` | P | baixo | não |
| 22 | Vocabulário de ausência unificado | `web/src/app/comparar/comparar.html` | P | baixo | não |
| 23 | Links de fonte nas tabelas com texto distinto | `web/src/app/perfil/tabela.html` | P | baixo | não |

#### Fase 3: interações novas

| # | Item | Arquivos | Esforço | Risco E2E e axe | ADR novo |
|---|---|---|---|---|---|
| 24 | Link de âncora por bloco, com nome acessível por bloco | `web/src/app/perfil/perfil.html`, `web/src/styles/perfil.css` | P | baixo | não |
| 25 | "Imprimir ou salvar em PDF" no perfil, com `window.print()` | `web/src/app/perfil/perfil.html`, `web/src/app/perfil/perfil.ts` | P | baixo | não |
| 26 | Tema com três opções no painel do menu; o botão de alternar fica a partir de 64 rem | `web/src/app/app.html`, `web/src/app/app.ts`, `web/src/styles/casca.css` | M | médio; o rótulo acessível deve conter o texto visível (`web/src/app/tema.test.ts`) | não |
| 27 | Botão "Neste documento" fixo no topo no celular | `web/src/app/doc/doc.html`, `web/src/styles/doc.css` | P | baixo | não |
| 28 | Busca nas páginas internas, no painel do menu e, a partir de 72 rem, no cabeçalho; leva à inicial com `?q=` | `web/src/app/app.html`, `web/src/app/app.ts`, `web/src/styles/casca.css` | M | baixo; na inicial o cabeçalho não mostra a busca, para não haver dois `role="search"` | não |
| 29 | ADR-0036 do lado a lado: regras, `h1`, saída do menu e ajuste dos testes | `docs/adr/`, `web/src/app/comparar/`, `web/e2e/navegacao.spec.ts`, `web/e2e/acessibilidade.spec.ts` | G | alto; três asserções mudam | sim |

### Critérios de sucesso

Verificáveis sem rastreamento: por inspeção, por script local e por sessão de teste presencial.

#### Cliques até cada recurso

| Recurso | Hoje (computador / celular) | Meta |
|---|---|---|
| Busca na primeira tela | 0, a 509 px / 0, a 726 px | 0, abaixo de 300 px / 0, abaixo de 420 px |
| Esfera | 0 / 1 | 0 / 0 |
| Cargo, UF e partido | 0 / 1 | 0 / 1 |
| Perfil | 1 / 1 | 1 / 1 |
| Primeiro bloco do perfil (390 px) | 1 clique e 972 px de rolagem | 1 clique e menos de 700 px |
| Todos os registros de um ano | 2 / 2 | 2 / 2, antes da tabela |
| Fonte do bloco | 2 / 2 | 2 / 2 |
| Lado a lado com resultado | 1 clique e 4 interações | 2 cliques a partir do perfil, com sugestão alfabética |
| CSV nacional | 2 / 2 | 2 / 2 |
| JSON do perfil | 2 / 2 | 2 / 2 |
| Estado das coletas | 1 / 1 | 1 / 1 |
| Tema específico | 1 a 2 / 1 a 2 | 1 a 2 / 2, com rótulo |
| Impressão | sem pista | 2, pelo perfil |
| Endereço de um bloco | 2 e copiar do endereço | 2, pelo link de âncora |
| Metodologia, Fontes, Contribuir | 1 / 1 | 1 / 2 |
| Altura do cabeçalho em 390 px | 193 px | até 64 px |
| Chips na inicial com dados reais | 19 | até 8 |

#### Roteiro de teste com 5 pessoas

Participantes: cinco pessoas fora do projeto, sem formação em dados; duas usam só o celular; uma usa teclado ou leitor de tela.
Sessão de 30 minutos, pensando em voz alta.
O observador anota em papel ou planilha local: tempo, cliques, primeiro clique, sucesso, comentário.
Nada é gravado no site.

1. Olhe a página inicial por dez segundos e diga, com suas palavras, o que o site oferece. Sucesso: menciona "político" e "fonte oficial" ou "o que fez".
2. Encontre um deputado federal do seu estado e diga o partido e se está em exercício.
3. No perfil dessa pessoa, descubra de onde vem a informação do bloco de mandatos e quando foi capturada.
4. Descubra quanto essa pessoa gastou da cota em 2024 e qual foi a maior categoria.
5. Abra todos os votos dessa pessoa em 2025 e encontre um voto sobre um tema qualquer que você escolher.
6. Envie para alguém o endereço do bloco de emendas desse perfil.
7. Baixe a tabela com todos os políticos do seu estado.
8. Descubra se os dados do site estão atualizados e se alguma fonte está com problema.
9. Mude o site para o tema escuro.
10. Encontre a explicação de como o site conta a participação em votações.

Critérios: quatro de cinco completam cada tarefa sem ajuda; primeiro clique correto em 80 % das tarefas; tempo mediano até 60 segundos nas tarefas 2, 7 e 9; nota de facilidade de 1 a 5 com média 4 ou mais; nenhuma pessoa descreve o site como "ranking" ou "avaliação".

#### Checklist heurístico

Nielsen, uma pergunta por heurística:

1. Visibilidade do estado: a linha de resultados diz quantos, de quantos e de quando são os dados? O chip ativo do sumário acompanha a rolagem?
2. Correspondência com o mundo real: algum título usa "dump", "REST", "índice" ou sigla sem explicação?
3. Controle e liberdade: todo filtro tem remoção individual e "Limpar tudo"? Voltar do perfil devolve a lista com os filtros?
4. Consistência: todos os títulos em caixa de frase? Uma só frase para ausência? Um só sinal para "em exercício"?
5. Prevenção de erro: a busca ignora acento e caixa? O estado vazio oferece saída?
6. Reconhecimento em vez de memória: todo recurso tem pista visível (âncora, impressão, mais blocos)?
7. Flexibilidade: o endereço guarda busca, filtros e bloco? O teclado alcança tudo?
8. Estética minimalista: a primeira tela do celular tem cabeçalho, título, frase e busca, e nada mais?
9. Erros: toda falha de carga tem mensagem com saída (inicial, perfil, detalhe, comparação, dados abertos, estado, documentos)?
10. Ajuda: Metodologia e Política editorial a um clique de qualquer página?

WCAG 2.2 AA, além do axe no CI:

- 1.3.1 e 4.1.2: nenhum `role` sem os filhos exigidos; todo campo com rótulo visível igual ao acessível.
- 1.4.3 e 1.4.11: pares da tabela acima conferidos nos dois temas depois da implementação.
- 1.4.10: nada perdido em 320 px e em zoom de 400 %.
- 1.4.12: nenhum texto cortado com espaçamento de 1,5 de entrelinha e 0,12 em de letra.
- 2.4.7: anel visível em todo controle, por Tab, nos dois temas e em `forced-colors`.
- 2.4.11: nenhum controle focado fica totalmente escondido pela faixa fixa do perfil ou pelo botão fixo dos documentos.
- 2.5.8: todo alvo com 24 px ou mais; os do projeto com 32 px.
- 2.5.3: o nome acessível do botão de tema e do botão de menu contém o texto visível.
- `prefers-reduced-motion`: nenhum degradê ou rolagem animada quando ativo.
- Teclado: Tab chega ao link de pular; o menu do celular abre, fecha e devolve o foco ao botão.

## Ideias que exigem dado novo

Fora desta revisão. Cada uma pede fonte, campo ou tabela que o ETL ainda não publica.

- Nome em caixa normal nos perfis do TSE. O TSE publica em maiúsculas; a fonte do nome de urna ou uma regra de caixa revisada precisa entrar no ETL.
- Nome por extenso do partido ao lado da sigla ("UNIÃO", "PODE"). Tabela de partidos do TSE.
- Tipo de proposição por extenso ("REQ", "PL", "PEC"). Tabelas de referência da Câmara e do Senado.
- Nome do município para entes fora de São Paulo. A tabela `ENTES_MUNICIPAIS` só tem dois municípios paulistas; a tabela do IBGE resolve.
- Período coberto por bloco, publicado pelo ETL, para que o lado a lado e o perfil mostrem "2023 a 2026" sem calcular no navegador.
- Uma seção por fonte em `docs/FONTES.md`, para o estado das coletas ligar cada fonte à sua descrição.
- Resumo do que mudou desde a execução anterior, publicado pelo ETL, para uma linha "novidades de hoje" sem rastrear ninguém.
- Fotos continuam fora, pelo ADR-0012.

## Consequências

- CSS um pouco menor: seis regras de rótulo viram uma, duas páginas perdem largura e margem próprias, e `--r-cartao` some.
- Testes a ajustar no mesmo commit de cada item: `web/e2e/navegacao.spec.ts:115` (título de dados abertos) na fase 2; `web/e2e/navegacao.spec.ts:99-103` e `web/e2e/acessibilidade.spec.ts:14` com o ADR do lado a lado.
- O `h1` da inicial, o rótulo da busca, os nomes de bloco, os nomes das listas e os parâmetros de URL não mudam.
- O roadmap ganha uma seção para esta revisão quando a fase 1 fechar, com o estado real e as limitações.

## Alternativas consideradas

- Biblioteca de componentes ou fonte web: descartadas, como no ADR-0012.
- Barra de abas fixa no rodapé do celular: descartada. Cinco itens não cobrem o site e duplicam o menu.
- Esconder blocos vazios ou reordenar blocos por quantidade: descartado. Fere o princípio 1 da política editorial.
- Retirar as redes declaradas ao TSE do cabeçalho do perfil: descartado. O ADR-0031 as colocou lá; aqui só perdem peso visual.
- Cor vermelha para falha de coleta: descartada. Vermelho é cor de campo político no Brasil; a neutralidade da paleta vale mais que a convenção. A forma do marcador e o texto distinguem os estados.
- Trocar o azul por cinza puro: descartado. Sem cor de destaque, link e botão primário perdem reconhecimento.
- Paginação por rota na lista: descartada, como no ADR-0012.

## Limitações desta análise

- Nenhum teste com pessoas foi feito. O roteiro acima é a próxima etapa.
- Nenhum leitor de tela real foi usado. O achado de foco e o de `listbox` vêm da leitura do código e de medição no Chromium.
- A inspeção visual cobriu só o Chromium, três perfis e os dados publicados em 2026-11-07.
- As razões de contraste seguem a fórmula da WCAG 2.x, não o APCA.
- O domínio da citação em dados abertos não foi verificado.
- Alturas e posições variam com o conteúdo de cada perfil e com a fonte do sistema de cada aparelho.
