# Convenções para agentes de código

Este arquivo vale para qualquer agente de código e para pessoas.
Leia também `docs/POLITICA_EDITORIAL.md` e `docs/METODOLOGIA.md`.

## Privacidade do mantenedor e do repositório

- Nunca escreva no repositório: nome civil, e-mail, telefone, endereço, cidade, CEP, caminho absoluto com nome de usuário, hostname, locale, fuso horário local, IP ou chave.
- Ignore dado pessoal visível no ambiente (configuração do git, variáveis, nomes de pastas). Não reproduza esse dado em lugar nenhum.
- Use caminhos relativos à raiz do repositório.
- Configure tudo por variável de ambiente. Placeholders ficam em `.env.example`.
- Datas do ETL em UTC e ISO 8601. Não fixe fuso no código.
- Faça commit com `TZ=UTC`, para que a data do commit não revele o fuso local.
- `.gitignore` cobre `.env*`, `*.local`, `data/raw`, `data/normalizado`, `data/publicado`, `node_modules`, `target`, `build`, `dist`, pastas de IDE, logs e `.DS_Store`.
- Proteções:
  - `scripts/privacy-check.sh` lê `.privacy-patterns` (local, ignorado pelo git, uma regex por linha) e bloqueia o commit em caso de match;
  - `.githooks/pre-commit` roda o `privacy-check` e o gitleaks. Ative com `git config core.hooksPath .githooks`;
  - o CI roda o gitleaks e o `privacy-check`;
  - `scripts/audit-history.sh` varre todo o histórico antes de o repositório ficar público.
- Não configure `user.name` nem `user.email`. A identidade de autor é decisão de quem mantém o repositório.
- Não adicione `Co-Authored-By`, rodapé "Generated with", nome de ferramenta ou link de sessão em commit, PR ou documento.
- `pom.xml` e `package.json` não têm campo de autor.
- O nome do projeto fica em `etl/src/main/java/etl/Projeto.java` e no README.
- Fixtures de teste usam dado oficial público já minimizado ou dado sintético. Nunca dado do mantenedor.
- O piloto municipal aparece só como "piloto municipal", sem justificar a escolha da cidade.

## Regras de trabalho

- Ordem: ler, planejar (ADR e roadmap), implementar, verificar, commitar.
- Pare antes de ação destrutiva: force push, `reset --hard`, apagar dados, deploy em produção.
- Se o ambiente não compilar ou não tiver rede, não force. Verifique por leitura de código, deixe o CI compilar e registre a pendência em `docs/ROADMAP.md`.
- Commits pequenos, um tema por commit.
- Mensagem de commit em português, no modo indicativo ("adiciona adaptador da Câmara"), sem travessão e sem menção a IA.
- Staging seletivo: `git add <caminhos>`. Nunca `git add -A` nem `git add .`.
- Java idiomático: records, tipos explícitos, loop simples quando ficar mais legível que stream.
- Dependências do ETL: HTTP da JDK, Jackson, validador de JSON Schema e JUnit.
- Angular com o mínimo necessário: componentes standalone, sem NgRx, sem SSR, sem kit de UI pesado.
- Testes só onde protegem regra de dados: parse de CSV latin-1, normalização de nome, deduplicação de despesas, bloqueio de CPF e telefone. Fixtures pequenas.
- Documentação em pt-BR, frases curtas, uma ideia por frase.
- Comentário de código só onde a regra de negócio não é óbvia.
- Não invente dado nem endpoint. O que não foi validado fica marcado como não validado.
- Ao fechar cada fase, atualize `docs/ROADMAP.md` com o estado real e as limitações.
