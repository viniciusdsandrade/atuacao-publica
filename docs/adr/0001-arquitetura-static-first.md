# ADR-0001: Arquitetura static-first

- Status: aceita
- Data: 2026-10-30

## Contexto

O portal publica dados de cerca de 620 políticos federais.
Os dados vêm de fontes oficiais que mudam no máximo uma vez por dia.
Ninguém escreve dados pelo portal: o visitante só lê.
A contribuição humana passa por pull request revisado.
O projeto precisa de pouco código, custo zero de hospedagem e nenhum rastreamento de visitante.

## Decisão

1. Um ETL em lote gera arquivos JSON estáticos.
2. Uma SPA estática lê esses arquivos no navegador.
3. O GitHub Pages hospeda a SPA e os dados, publicados por workflow.
4. Não há servidor, banco de dados, autenticação nem painel administrativo.
5. O branch `main` guarda só código, schemas, `contrib/` e `docs/`.
6. Os dados publicados vão para o artefato do Pages e, opcionalmente, para uma Release. Nunca vão para `main`.
7. O cache de dados brutos (`data/raw`) fica no cache do GitHub Actions, com hash e data de captura.

## Consequências

- Hospedagem sem custo e sem servidor para manter.
- A busca roda no navegador sobre um índice pequeno (`index.json`).
- Cada arquivo tem orçamento de tamanho: perfil de até cerca de 200 KB. Detalhes por ano ficam em arquivos separados.
- Cada execução regenera o conjunto publicado a partir do cache de dados brutos.
- O visitante não envia dado nenhum: sem cookies, sem analytics, sem CDN externa.

## Quando reavaliar

Abrir novo ADR quando um requisito concreto exigir servidor. Exemplos:

- busca textual sobre milhões de registros;
- contas de usuário ou comentários;
- API de escrita.

## Alternativas consideradas

- Servidor com banco de dados e API: mais código, operação contínua e superfície de ataque maior.
- Pré-renderização (SSR ou SSG) de uma página HTML por político: fora do escopo da POC, que veta SSR.
- Ferramentas de publicação de dados prontas em outra linguagem: adicionariam uma linguagem ao núcleo.
