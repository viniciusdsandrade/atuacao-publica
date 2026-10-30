# Atuação Pública

Portal público sobre a atividade de políticos brasileiros: o que cada um fez, faz e declara que vai fazer.
Todo registro vem de fonte oficial e traz a proveniência: fonte, URL de origem e data de captura.

Estado: prova de conceito na esfera federal. Detalhes em `docs/ROADMAP.md`.

## O que é

- Votos e participação em votações nominais (Câmara e Senado).
- Despesas da cota parlamentar, por mês e por categoria, com o detalhe de cada lançamento.
- Proposições de autoria, discursos, eventos com presença registrada, órgãos, comissões e frentes.
- Candidaturas no TSE: resultado, total de bens declarados e plano de governo.
- Atos do Poder Executivo federal e agenda oficial da Presidência.
- Posicionamentos declarados, só por contribuição revisada e com snapshot arquivado.

Escopo: deputados federais (513), senadores (81), Presidência da República e os 27 governadores.

## O que não é

- Não dá nota, ranking, adjetivo nem juízo.
- Não usa imprensa, boato, rede social de terceiros nem texto gerado por IA sobre político.
- Não guarda CPF, título de eleitor, telefone, e-mail pessoal, endereço ou dado de familiar.
- Não tem conta de usuário, comentário, cookie nem rastreador.

## Entra ou não entra

| Entra | Não entra |
|---|---|
| Voto registrado em votação nominal | "Presença" calculada a partir de votações |
| Despesa da cota parlamentar, com fornecedor e CNPJ | CPF de fornecedor pessoa física |
| Ementa e situação oficial de proposição | Opinião sobre a proposição |
| Resumo oficial de discurso | Resumo feito por IA |
| Declaração no site oficial do mandato, com snapshot | Postagem de terceiros sobre o político |
| Resultado eleitoral e total de bens declarados (TSE) | Descrição de cada bem |
| Ato normativo e veto do Executivo federal | Notícia sobre o ato |
| Agenda oficial publicada pelo Planalto, com snapshot | Agenda divulgada pela imprensa |

## Como os dados chegam

```text
fontes oficiais ──fetch──> data/raw ──normalize──> data/normalizado ──publish──> data/publicado ──> site estático
```

1. `fetch` baixa os arquivos oficiais para `data/raw`, com hash e data de captura.
2. `normalize` converte para o modelo canônico e descarta dado pessoal.
3. `publish` vincula as fontes, monta um JSON por político, valida cada arquivo contra `schemas/` e grava o relatório `_meta/execucao.json`.
4. O site lê os JSON publicados. Nenhum dado do visitante sai do navegador.

Fontes e status: `docs/FONTES.md`. Cálculos e limites: `docs/METODOLOGIA.md`. Decisões: `docs/adr/`.

## Rodar o ETL na sua máquina

Requisitos: git e Java 25 ou mais recente.
A primeira coleta transfere cerca de 1 GB (as propostas de governo do TSE vêm em ZIPs grandes, dos quais só a lista de arquivos fica guardada), ocupa cerca de 250 MB em `data/raw` e leva de 20 a 40 minutos.

1. Clone o repositório e entre na pasta.
2. Compile:

   ```bash
   cd etl && ./mvnw -q -B package && cd ..
   ```

3. Colete. Rode sempre a partir da raiz do repositório:

   ```bash
   java -Xmx4g -jar etl/target/etl.jar fetch
   ```

   Para uma fonte só: `fetch camara`, `fetch senado`, `fetch tse` ou `fetch executivo`.
4. Normalize: `java -Xmx4g -jar etl/target/etl.jar normalize`
5. Publique: `java -Xmx4g -jar etl/target/etl.jar publish`
6. Confira o resultado em `data/publicado/_meta/execucao.json`.

Configuração por variável de ambiente: `.env.example`.
Falha de uma fonte não derruba as demais: o cursor `data/publicado/_meta/{fonte}.json` registra a falha, e o site marca o bloco como desatualizado.

## Disparar o ETL no GitHub

1. Abra **Actions > dados > Run workflow**.
2. Escolha a fonte (`todas` ou uma) e confirme.
3. O resumo da execução mostra os perfis publicados e o status de cada fonte.

A execução diária (06:17 UTC) e o deploy no GitHub Pages ficam desligados até o mantenedor ativar. Passos em `docs/MAINTAINER_CHECKLIST.md`.

## Rodar o site na sua máquina

Requisitos: Node.js 24. Rode antes o ETL até o `publish`.

1. `cd web && npm ci`
2. `npm start`
3. Abra o endereço que o terminal mostrar.

O `npm start` liga `data/publicado` ao site em `dados/`.

## Contribuir

Há três caminhos. Todos terminam em revisão humana.

1. **Corrigir um dado:** abra uma issue com o modelo **Correção de dado** e informe a fonte oficial.
2. **Adicionar um registro manual** (posicionamento declarado, agenda oficial, link oficial, mandato):
   1. Arquive a página da fonte em <https://web.archive.org/save> e copie a URL do snapshot.
   2. Crie o arquivo `contrib/{esfera}/{cargo}/{politico_id}/{tipo}.yaml`.
   3. Valide com `java -jar etl/target/etl.jar normalize contrib` e `java -jar etl/target/etl.jar fetch contrib`.
   4. Abra o pull request.
3. **Adicionar ou revalidar uma fonte:** edite `contrib/fontes.yaml` e `docs/FONTES.md`, rode `fetch fontes` e abra o pull request.

Passo a passo, campos e exemplo completo: `CONTRIBUTING.md`.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `etl/` | ETL em Java (módulo único, CLI `fetch`, `normalize`, `publish`) |
| `web/` | site em Angular |
| `schemas/` | JSON Schemas do modelo, exemplos válidos e inválidos |
| `contrib/` | catálogo de fontes, regras de mandato e registros manuais |
| `docs/` | fontes, metodologia, política editorial, roadmap, ADRs, tarefas |
| `scripts/` | verificações de privacidade |
| `.github/` | CI, pipeline de dados e modelos de issue e pull request |

## Licenças

- Código: MIT (`LICENSE`).
- Dados publicados: CC BY 4.0 (`LICENSE-DADOS.md`).
- Titular: os contribuidores do projeto.
