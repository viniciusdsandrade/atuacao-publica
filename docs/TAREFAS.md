# Boas primeiras tarefas

Tarefas pequenas, com escopo fechado. Abra uma issue antes de começar, para evitar trabalho duplicado.

## Dados e fontes

1. **Revalidar uma fonte estadual.** Escolha uma UF em `docs/FONTES.md`. Confira se as duas URLs respondem. Atualize `validado_em` em `contrib/fontes.yaml`.
2. **Registrar a agenda oficial de um dia.** Siga o caminho (b) de `CONTRIBUTING.md` com o tipo `agenda_oficial` e a fonte `planalto-agenda`.
3. **Registrar um link oficial.** Adicione o site oficial do mandato de um parlamentar que não tem `urlWebsite` na Câmara (tipo `link_oficial`).
4. **Confirmar a licença de uma fonte.** Ache o termo de uso publicado pela Câmara ou pelo Senado. Atualize o campo `licenca` em `contrib/fontes.yaml`.
5. **Revalidar o Querido Diário.** Teste a API com um cliente TLS atualizado. Registre o resultado em `docs/FONTES.md`.

## Código

1. **Ampliar a lista de palavras de juízo.** O lint de `normalize contrib` usa uma lista curta. Proponha termos com exemplo de uso indevido.
2. **Apelidos de partido na orientação do Senado.** Confira a tabela de apelidos (`Progressistas` = `PP`) contra os rótulos de 2023 a 2026.
3. **Acessibilidade do site.** Rode uma auditoria de acessibilidade no navegador e corrija um problema por pull request.
4. **Tamanho dos perfis.** Meça o maior `politicos/{id}.json` publicado e proponha um corte, se passar de 200 KB.

## Documentação

1. **Revisar o README como contribuidor novo.** Siga os passos do zero. Anote onde parou ou precisou perguntar.
2. **Glossário.** Explique em uma frase cada sigla de voto do Senado (`AP`, `LS`, `NCom`, `P-NRV`, `MIS`, `LP`, `LAP`), com a fonte da definição.
