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

## Atualizar a semente da coleta local (ADR-0034)

ALMG, ALRS, ALES, ALEPE, CLDF, ALEMA e ALAP recusam conexão de fora do Brasil; na ALBA e na ALERR, as APIs respondem 403 ao runner do GitHub. Na ALESE, o sistema legislativo (`aleselegis.al.se.leg.br`) não aceita conexão do runner. Na Câmara Municipal de Caraguatatuba, o sistema legislativo (`sistema.camaracaragua.sp.gov.br`) também não. Enquanto o túnel não estiver ativo, os dados dessas onze casas vêm da sua rede.

1. Na raiz do repositório, compile o ETL: `cd etl && ./mvnw -q package -DskipTests && cd ..`.
2. Com o `gh` autenticado, rode `scripts/semente-enviar.sh`. Ele coleta as onze casas, cifra o cache com uma chave nova e envia o pacote à release `semente-coleta-local`, trocando o segredo `SEMENTE_CHAVE`.
3. A execução seguinte do `dados` restaura a semente e publica as onze casas. O relatório mostra a data da coleta local.
4. Repita quando o alerta de fonte sem coleta válida abrir issue para uma delas, ou quando quiser dados mais novos.
5. Com o túnel ativo, apague a release (`gh release delete semente-coleta-local --cleanup-tag`) e o segredo.

## Ativar o túnel pelo Brasil (ADR-0029)

ALMG, ALRS, ALES, CLDF, ALEMA, ALEAC, ALERR, ALAP e ALESE recusam conexão ou respondem 403 ao runner nos EUA. O túnel faz essas coletas saírem por uma máquina no Brasil.

1. Crie uma máquina pequena no Google Cloud, na região `southamerica-east1` (São Paulo), com Debian. Reserve um IP externo fixo: sem ele, o segredo muda quando o IP muda. Na rede padrão, a porta 22 já fica aberta, e o login por senha já vem desligado.
2. Numa máquina de confiança, gere a chave do túnel: `ssh-keygen -t ed25519 -N "" -C tunel-ci -f tunel-ci`.
3. Na máquina do Google Cloud, crie o usuário do túnel, sem shell:

   ```
   sudo useradd --create-home --shell /usr/sbin/nologin tunel
   sudo install -d -m 700 -o tunel -g tunel ~tunel/.ssh
   ```

4. Grave em `~tunel/.ssh/authorized_keys`, com dono `tunel` e modo 600, uma linha só, com o conteúdo de `tunel-ci.pub` no fim:

   ```
   restrict,port-forwarding,permitopen="dadosabertos.almg.gov.br:443",permitopen="ww4.al.rs.gov.br:443",permitopen="ww4.al.rs.gov.br:5000",permitopen="www.al.es.gov.br:443",permitopen="www.cl.df.gov.br:443",permitopen="transparencia.al.rs.gov.br:443",permitopen="www3.al.es.gov.br:443",permitopen="ple.cl.df.gov.br:443",permitopen="sapl.al.ac.leg.br:443",permitopen="sapl.al.rr.leg.br:443",permitopen="www.al.ma.leg.br:443",permitopen="www.al.ap.leg.br:443",permitopen="aleselegis.al.se.leg.br:443" ssh-ed25519 AAAA... tunel-ci
   ```

   A chave só encaminha para esses treze destinos e não abre shell.
5. Fixe a chave do servidor: rode `ssh-keyscan -t ed25519 <IP>` e confira a impressão digital (`ssh-keygen -lf`) com a mostrada no console serial da máquina.
6. Em **Settings > Secrets and variables > Actions > Secrets**, crie:
   - `TUNEL_SSH_DESTINO`: `tunel@<IP>`;
   - `TUNEL_SSH_CHAVE`: o conteúdo de `tunel-ci`, a chave privada;
   - `TUNEL_SSH_KNOWN_HOSTS`: a linha do `ssh-keyscan` conferida no passo 5.
7. Apague a chave privada da máquina de confiança.
8. Dispare **Actions > dados > Run workflow** com `fonte=almg`. O passo `túnel pelo Brasil` mostra o código HTTP de cada site, e o relatório da execução deve trazer `almg` com status `ok`.

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
