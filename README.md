# Atuação Pública

Portal público sobre a atividade de políticos brasileiros: o que cada um fez, faz e declara que vai fazer.
Todo registro vem de fonte oficial e traz a proveniência.

> Esboço. Este README será revisado ao fim da fase 7. Estado das fases em `docs/ROADMAP.md`.

## O que é

- Votações e votos, participação em votações nominais, despesas, proposições, discursos, eventos, órgãos e frentes.
- Candidaturas, resultado, bens declarados e plano de governo.
- Atos normativos e agenda oficial do Executivo.
- Posicionamentos declarados, só por contribuição revisada e com snapshot.

Escopo da POC: deputados federais (513), senadores (81), Presidência da República e os 27 governadores.

## O que não é

- Não dá nota, ranking, adjetivo nem juízo.
- Não usa imprensa, boato, rede social de terceiros nem texto gerado por IA sobre político.
- Não guarda CPF, título de eleitor, telefone, e-mail pessoal, endereço ou dado de familiar.
- Não tem conta de usuário, comentário nem rastreador.

## Entra ou não entra

| Entra | Não entra |
|---|---|
| Voto registrado em votação nominal (Câmara, Senado) | "Presença" calculada a partir de votações |
| Despesa da cota parlamentar, com fornecedor e CNPJ | CPF de fornecedor pessoa física |
| Ementa e situação oficial de proposição | Opinião sobre a proposição |
| Resumo oficial de discurso | Resumo feito por IA |
| Declaração no site oficial do mandato, com snapshot | Postagem de terceiros sobre o político |
| Resultado eleitoral e total de bens declarados (TSE) | Descrição e endereço de cada bem |
| Ato normativo e veto do Executivo federal | Notícia sobre o ato |

## Como os dados chegam

```text
fontes oficiais ──fetch──> data/raw ──normalize──> data/normalizado ──publish──> data/publicado ──> site estático
```

1. `fetch` baixa os dados brutos e guarda hash e data de captura.
2. `normalize` converte para o modelo canônico e descarta dado pessoal.
3. `publish` monta um JSON por político, os índices e o relatório `_meta`, e valida cada arquivo contra o schema.
4. O site (SPA estática) lê os JSON publicados. Nenhum dado do visitante sai do navegador.

Fontes e status de validação: `docs/FONTES.md`. Cálculos e limites: `docs/METODOLOGIA.md`.

## Rodar o ETL

Requisitos: Java 25 ou mais recente.

1. Compile: `cd etl && ./mvnw -q -B package && cd ..`
2. Baixe os dados de uma fonte: `java -jar etl/target/etl.jar fetch camara`
3. Normalize: `java -jar etl/target/etl.jar normalize`
4. Publique: `java -jar etl/target/etl.jar publish`

Fontes: `camara`, `senado`, `tse`, `executivo`, `contrib`. Sem fonte, o comando roda todas.
Configuração por variável de ambiente: veja `.env.example`.

## Rodar o site

Requisitos: Node.js 24.

1. `cd web && npm ci`
2. `npm start`
3. Abra o endereço mostrado no terminal.

## Contribuir

Três caminhos: corrigir um dado, adicionar um registro manual, adicionar ou revalidar uma fonte.
Passo a passo com exemplo completo em `CONTRIBUTING.md`.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `etl/` | ETL em Java (módulo único, CLI `fetch`, `normalize`, `publish`) |
| `web/` | site em Angular |
| `schemas/` | JSON Schemas do modelo e de `contrib/` |
| `contrib/` | registros manuais e catálogo de fontes |
| `docs/` | fontes, metodologia, política editorial, roadmap, ADRs |
| `scripts/` | verificações de privacidade |

## Licenças

- Código: MIT (`LICENSE`).
- Dados: CC BY 4.0 (`LICENSE-DADOS.md`).
- Titular: os contribuidores do projeto.
