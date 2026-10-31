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

### Autoria dos commits

Decisão do mantenedor: a autoria dos commits é dele.
O e-mail de autor é o `noreply` do GitHub (`<id>+<login>@users.noreply.github.com`).
Esse e-mail vincula o commit à conta sem expor e-mail pessoal.
A identidade fica na configuração local do clone (`git config user.name` e `git config user.email`).
A auditoria não confere autoria por padrão. Use `scripts/audit-history.sh --autoria` para conferir.

### Commits sem fuso local

Faça commit com `TZ=UTC git commit ...`. A auditoria acusa commit com fuso diferente de `+0000`.

## Ativar a publicação (Pages e pipeline agendado)

O pipeline de dados não roda e não publica nada até o mantenedor ativar:

1. Em **Settings > Pages**, escolha a origem **GitHub Actions**. No GitHub gratuito, o Pages exige repositório público.
2. Em **Settings > Secrets and variables > Actions > Variables**, crie `ETL_AGENDADO = true` para ligar a execução diária.
3. Crie `PUBLICAR_PAGES = true` para ligar o deploy.
4. Dispare **Actions > dados > Run workflow** para a primeira carga.
5. Mantenha as issues ligadas no repositório: o pipeline abre uma issue com o rótulo `coleta` quando uma fonte passa de 3 dias sem coleta válida.

## Checklist de abertura do repositório

1. Rode `scripts/audit-history.sh` com o `.privacy-patterns` completo.
2. Resolva cada achado:
   - conteúdo: remova o dado e reescreva o histórico, ou publique uma cópia limpa como repositório novo;
   - fuso: reescreva as datas para UTC ou publique uma cópia limpa.
3. Confira que todo commit usa o e-mail `noreply` do GitHub (`git log --format=%ae | sort -u`).
4. Rotacione toda chave que já passou pelo ambiente de desenvolvimento (por exemplo, a chave do Portal da Transparência).
5. Revise `LICENSE` e `LICENSE-DADOS.md`. Troque "os contribuidores do projeto" pelo titular definitivo, se houver.
6. Revise nomes e metadados: `etl/pom.xml`, `web/package.json`, descrição do repositório, tópicos.
7. Confirme que `main` não contém dados publicados (`data/` está no `.gitignore`).
8. Ative a proteção de branch em `main` (revisão obrigatória, CI obrigatório).
9. Ative **Private vulnerability reporting** em **Settings > Code security**.
10. Torne o repositório público.
