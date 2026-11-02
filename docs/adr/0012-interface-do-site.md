# ADR-0012: Interface do site

- Status: aceita
- Data: 2026-11-02

## Contexto

A interface da prova de conceito cumpre as funções: busca, filtros, perfil com proveniência por bloco, detalhe anual e documentos.
Falta cuidado com quem chega pela primeira vez e com quem usa o celular.
Problemas observados na versão publicada:

- a página inicial não diz o que o site é, e os filtros são cinco listas de seleção em fila;
- filtros e posição da lista se perdem ao abrir um perfil e voltar;
- o estado das coletas fica depois de centenas de cartões;
- o perfil empilha todos os blocos, sem navegação, e o bloco vazio ocupa a mesma altura do cheio;
- tabelas de seis ou oito colunas exigem rolagem lateral no celular, e essa rolagem não é acessível por teclado;
- o anel de foco é amarelo claro, sem contraste suficiente, e a troca de página não move o foco;
- os documentos não têm sumário.

## Decisão

1. Mesma base técnica: Angular standalone, sinais e CSS próprio. Sem biblioteca de componentes, de ícones ou de fontes. A CSP continua `default-src 'self'` e o site continua sem chamar terceiros. Ícone é SVG embutido. A fonte é a do sistema.
2. Paleta única e neutra, com tema claro e escuro pelo sistema. Nenhuma cor identifica partido, cargo, situação ou voto.
3. A interface não ordena nem compara políticos. A lista é alfabética. Barra de despesa compara só períodos do mesmo perfil (política editorial, princípios 1 e 2).
4. O estado dos filtros vai para o endereço (`#/?q=...&situacao=...`). Quem compartilha o link compartilha a busca, e quem volta de um perfil encontra os filtros.
5. A lista mostra 48 cartões por vez, com "Mostrar mais".
6. Situação e esfera viram controles segmentados com contagem. No celular, só a situação fica à vista: esfera, cargo, UF e partido ficam em "Mais filtros", como listas de seleção.
7. O perfil ganha navegação por blocos (barra lateral no computador, faixa fixa no celular) e destaque do bloco em leitura. Bloco sem registro continua na mesma ordem e com a mesma frase para todos os políticos, só que mais baixo. Cada bloco tem endereço próprio (`#/politico/{id}#bloco-{nome}`).
8. Tabelas viram cartões quando o contêiner tem menos de 44 rem (62 rem na tabela de candidaturas, de oito colunas). A semântica de tabela se mantém com papéis ARIA.
9. O estado das coletas ganha página própria (`#/estado`), com resumo na página inicial e link no rodapé.
10. Meta de acessibilidade: WCAG 2.2 AA. Foco visível com contraste, alvo de toque de pelo menos 32 px (40 px ou mais em botões e campos), `prefers-reduced-motion`, `forced-colors`, foco movido ao conteúdo na troca de página e título de aba por perfil. A verificação roda com axe-core e com teclado, de 320 px a 1366 px de largura, em scripts locais fora do repositório.

## Consequências

- Mais CSS (cerca de 33 kB, 6 kB transferidos), sem dependência nova.
- A página inicial deixa de renderizar todos os cartões de uma vez.
- Fotos continuam fora do site, para não gerar requisição a terceiros. O cartão usa as iniciais.
- A rolagem do sumário não usa `scrollIntoView` em contêiner fixo: no Safari ele rola a página inteira.
- Cor, barra ou ordenação que sugira ranking exige novo ADR.

## Alternativas consideradas

- Biblioteca de componentes: descartada. Peso, tema próprio e estilos injetados em conflito com a CSP e com o ADR-0001.
- Fontes web: descartada. Exigem requisição a terceiros ou arquivos grandes empacotados.
- Paginação por rota: descartada. "Mostrar mais" preserva a rolagem e guarda o estado na URL com um parâmetro.
- Esconder blocos vazios: descartada. Quebra a regra de mesmos campos para todos (política editorial, princípio 1).
