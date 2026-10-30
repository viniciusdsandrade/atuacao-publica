#!/usr/bin/env bash
# Bloqueia commit que contenha dado pessoal do mantenedor ou do ambiente.
# Padrões: uma regex estendida por linha em .privacy-patterns (arquivo local, ignorado pelo git)
# ou no arquivo indicado por PRIVACY_PATTERNS_FILE.
# Uso:
#   scripts/privacy-check.sh            verifica todos os arquivos versionados
#   scripts/privacy-check.sh --staged   verifica só o conteúdo preparado para commit
# Nunca imprime o trecho encontrado: só arquivo e linha.
set -euo pipefail

raiz=$(git rev-parse --show-toplevel)
padroes="${PRIVACY_PATTERNS_FILE:-$raiz/.privacy-patterns}"

# Caminho absoluto de pasta pessoal denuncia o usuário em qualquer máquina.
genericos='(/Users/|/home/)[A-Za-z0-9._-]+/|[A-Za-z]:\\Users\\'

lista_padroes=$(mktemp)
trap 'rm -f "$lista_padroes"' EXIT
printf '%s\n' "$genericos" > "$lista_padroes"
if [[ -s "$padroes" ]]; then
  grep -v '^[[:space:]]*$' "$padroes" >> "$lista_padroes" || true
else
  echo "privacy-check: $padroes ausente; só os padrões genéricos serão usados" >&2
fi

if [[ "${1:-}" == "--staged" ]]; then
  arquivos=$(git diff --cached --name-only --diff-filter=ACMR)
  ler() { git show ":$1"; }
else
  arquivos=$(git ls-files)
  ler() { cat "$raiz/$1"; }
fi

falhas=0
while IFS= read -r arquivo; do
  [[ -z "$arquivo" ]] && continue
  linhas=$(ler "$arquivo" | grep -I -E -i -n -f "$lista_padroes" | cut -d: -f1 | paste -sd, - || true)
  if [[ -n "$linhas" ]]; then
    echo "privacy-check: padrão pessoal em $arquivo (linhas $linhas)" >&2
    falhas=1
  fi
  if printf '%s\n' "$arquivo" | grep -E -i -q -f "$lista_padroes"; then
    echo "privacy-check: padrão pessoal no nome do arquivo $arquivo" >&2
    falhas=1
  fi
done <<< "$arquivos"

if [[ $falhas -ne 0 ]]; then
  echo "privacy-check: commit bloqueado. Remova o dado e tente de novo." >&2
  exit 1
fi
echo "privacy-check: nenhum padrão encontrado"
