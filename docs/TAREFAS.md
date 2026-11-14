# Boas primeiras tarefas

Tarefas pequenas, com escopo fechado. Abra uma issue antes de começar, para evitar trabalho duplicado.

## Dados e fontes

1. **Revalidar uma fonte estadual.** Escolha uma UF em `docs/FONTES.md`. Confira se as duas URLs respondem. Atualize `validado_em` em `contrib/fontes.yaml`.
2. **Registrar a agenda oficial de um dia.** Siga o caminho (b) de `CONTRIBUTING.md` com o tipo `agenda_oficial` e a fonte `planalto-agenda`.
3. **Registrar um link oficial.** Adicione o site oficial do mandato de um parlamentar que não tem `urlWebsite` na Câmara (tipo `link_oficial`).
4. **Confirmar a licença de uma fonte.** Ache o termo de uso publicado pela Câmara ou pelo Senado. Atualize o campo `licenca` em `contrib/fontes.yaml`.
5. **Revalidar o Querido Diário.** Teste a API com um cliente TLS atualizado. Registre o resultado em `docs/FONTES.md`.

## Código

1. **Leitor de tela.** A auditoria automatizada (axe-core, WCAG 2.2 AA) e a de teclado passaram em 2026-11-14. Percorra a lista, um perfil, uma tabela e um documento com VoiceOver, NVDA ou TalkBack e registre o que ficar confuso.
2. **Fotos sem chamada a terceiros.** Espelhe as fotos oficiais no próprio site durante o `publish`, com a URL oficial como proveniência.
3. **Temas das autorias do Senado.** Avalie buscar `/processo/{id}` só para os processos de cada senador e preencher `temas`.

## Documentação

1. **Revisar o README como contribuidor novo.** Siga os passos do zero. Anote onde parou ou precisou perguntar.
