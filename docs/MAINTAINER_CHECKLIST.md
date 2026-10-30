# Checklist do mantenedor

## Proteções de privacidade

| Ferramenta | Quando roda | O que faz |
|---|---|---|
| `scripts/privacy-check.sh --staged` | pre-commit | bloqueia o commit se o conteúdo preparado casar com algum padrão |
| `scripts/privacy-check.sh` | CI | verifica todos os arquivos versionados |
| gitleaks | pre-commit (se instalado) e CI | procura segredos no histórico |
| `scripts/audit-history.sh` | antes de abrir o repositório | varre todas as versões, nomes de arquivo, autor, committer, mensagens e fuso das datas |

### Arquivo de padrões

1. Crie `.privacy-patterns` na raiz. O git ignora esse arquivo.
2. Escreva uma regex estendida por linha: nome civil, e-mails, login, nome de usuário do sistema, hostname, cidade, caminhos de pasta pessoal.
3. Escape caracteres especiais de regex (`.`, `+`, `(`).
4. Os scripts nunca imprimem o trecho encontrado. Eles imprimem só arquivo e linha.
5. Padrões genéricos já embutidos: caminho absoluto de pasta pessoal no macOS, no Linux e no Windows.

### Padrões no CI

1. Crie o segredo `PRIVACY_PATTERNS` em **Settings > Secrets and variables > Actions**.
2. Cole o conteúdo de `.privacy-patterns`.
3. Sem o segredo, o CI usa só os padrões genéricos.

### Ativar o hook local

```bash
git config core.hooksPath .githooks
```

### Commits sem fuso local

Faça commit com `TZ=UTC git commit ...`. A auditoria acusa commit com fuso diferente de `+0000`.

## Ativar a publicação (Pages e pipeline agendado)

O pipeline de dados não roda e não publica nada até o mantenedor ativar:

1. Em **Settings > Pages**, escolha a origem **GitHub Actions**. No GitHub gratuito, o Pages exige repositório público.
2. Em **Settings > Secrets and variables > Actions > Variables**, crie `ETL_AGENDADO = true` para ligar a execução diária.
3. Crie `PUBLICAR_PAGES = true` para ligar o deploy.
4. Dispare **Actions > dados > Run workflow** para a primeira carga.

## Checklist de abertura do repositório

1. Rode `scripts/audit-history.sh` com o `.privacy-patterns` completo.
2. Resolva cada achado:
   - conteúdo: remova o dado e reescreva o histórico, ou publique uma cópia limpa como repositório novo;
   - autor e committer: reescreva com `git filter-repo --mailmap`, usando uma identidade pública escolhida por você, ou publique uma cópia limpa;
   - fuso: reescreva as datas para UTC ou publique uma cópia limpa.
3. Rotacione toda chave que já passou pelo ambiente de desenvolvimento (por exemplo, a chave do Portal da Transparência).
4. Revise `LICENSE` e `LICENSE-DADOS.md`. Troque "os contribuidores do projeto" pelo titular definitivo, se houver.
5. Revise nomes e metadados: `etl/pom.xml`, `web/package.json`, descrição do repositório, tópicos.
6. Confirme que `main` não contém dados publicados (`data/` está no `.gitignore`).
7. Ative a proteção de branch em `main` (revisão obrigatória, CI obrigatório).
8. Ative **Private vulnerability reporting** em **Settings > Code security**.
9. Torne o repositório público.
