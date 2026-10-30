# ADR-0004: Ferramentas e versões

- Status: aceita
- Data: 2026-10-30

## Contexto

A especificação pede Java LTS suportado pelo CI, Angular na versão LTS verificada na execução e o mínimo de dependências.
Também pede avaliar DuckDB via JDBC para transformações em SQL.

## Versões verificadas em 2026-10-30

| Ferramenta | Versão | Critério |
|---|---|---|
| Java | 25 (LTS, suporte até 2030) | `endoflife.date/api/oracle-jdk.json`. O build compila com `release 25`. |
| Angular | 21.2 (tag `v21-lts` no npm, suporte até 2027-06-30) | `npm view @angular/core dist-tags`. A versão `latest` (22) está em suporte ativo, não em LTS. |
| Node.js | 24 (LTS) | Compatível com Angular 21 (`^20.19 \|\| ^22.12 \|\| ^24`). |
| Maven | 3.9.16, via Maven Wrapper só com script | A versão 4 ainda é candidata (`4.0.0-rc-7`). |
| Jackson | 3.2.4 (`tools.jackson`) | O validador de JSON Schema 3.x usa Jackson 3. Jackson 3 já serializa `java.time`. |
| Validador de JSON Schema | networknt 3.0.8 | Draft 2020-12, biblioteca pronta. |
| JUnit | 6.1.3 | |

## Decisão

1. ETL em Java 25, módulo Maven único, com as dependências: Jackson (databind, CSV, YAML), validador networknt e JUnit.
2. `etl/mvnw` dispensa instalar o Maven.
3. Site em Angular 21, componentes standalone, sem zone.js, sem SSR, sem kit de UI.
4. GitHub Actions fixadas por SHA de commit, com a versão em comentário.

## DuckDB: avaliado, não adotado na POC

- A carga cabe em memória: os maiores arquivos anuais têm até cerca de 150 MB sem compressão.
- O CSV latin-1 do Senado e do TSE é lido com Jackson CSV, sem código de parser próprio.
- As agregações (por mês, por categoria, por ano) são poucas e simples.
- O DuckDB JDBC traz biblioteca nativa de dezenas de MB e um segundo paradigma (SQL ao lado de Java).
- O resultado final é um JSON por político, montado em Java de qualquer forma.

Reavaliar quando entrar uma fonte com junções pesadas ou volume acima da memória do runner (por exemplo, votação por seção eleitoral do TSE).

## Consequências

- Uma linguagem no ETL, uma no site.
- Atualizar Angular para a próxima LTS é um `ng update` antes de 2027-06-30.
