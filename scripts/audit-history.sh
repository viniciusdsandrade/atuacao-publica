#!/usr/bin/env bash
# Varre todo o histórico do git antes de tornar o repositório público.
# Usa os mesmos padrões de scripts/privacy-check.sh.
# Verifica: conteúdo de todas as versões, nomes de arquivo, mensagens de commit
# e fuso horário das datas.
# A autoria dos commits é do mantenedor, por decisão registrada em docs/MAINTAINER_CHECKLIST.md.
# Com --autoria, o script também confere autor e committer contra os padrões.
# Nunca imprime o trecho encontrado: só commit, arquivo ou campo.
set -euo pipefail

conferir_autoria=0
[[ "${1:-}" == "--autoria" ]] && conferir_autoria=1

raiz=$(git rev-parse --show-toplevel)
cd "$raiz"
padroes="${PRIVACY_PATTERNS_FILE:-$raiz/.privacy-patterns}"
genericos='(/Users/|/home/)[A-Za-z0-9._-]+/|[A-Za-z]:\\Users\\'

lista_padroes=$(mktemp)
trap 'rm -f "$lista_padroes"' EXIT
printf '%s\n' "$genericos" > "$lista_padroes"
if [[ -s "$padroes" ]]; then
  grep -v '^[[:space:]]*$' "$padroes" >> "$lista_padroes" || true
else
  echo "audit-history: $padroes ausente; só os padrões genéricos serão usados" >&2
fi

achados=0
commits=$(git rev-list --all)

relatar() {
  # Imprime as linhas recebidas com prefixo e marca achado se houver alguma.
  local saida
  saida=$(sed "s/^/  $1: /")
  if [[ -n "$saida" ]]; then
    echo "$saida"
    achados=1
  fi
}

echo "1/4 conteúdo de todas as versões"
if [[ -n "$commits" ]]; then
  # git grep imprime commit:arquivo; o trecho fica de fora por causa de -l.
  relatar "conteúdo" < <(git grep -I -E -i -l -f "$lista_padroes" $commits -- . 2>/dev/null | sort -u || true)
fi

echo "2/4 nomes de arquivo"
relatar "arquivo" < <(git log --all --name-only --format= | sort -u | grep -E -i -f "$lista_padroes" || true)

echo "3/4 mensagens de commit (autoria só com --autoria)"
while IFS=$'\t' read -r hash autor email_autor committer email_committer; do
  if [[ $conferir_autoria -eq 1 ]]; then
    for campo in autor email_autor committer email_committer; do
      if printf '%s\n' "${!campo}" | grep -E -i -q -f "$lista_padroes"; then
        echo "  commit ${hash:0:12}: campo $campo"
        achados=1
      fi
    done
  fi
  if git log -1 --format='%B' "$hash" | grep -E -i -q -f "$lista_padroes"; then
    echo "  commit ${hash:0:12}: mensagem"
    achados=1
  fi
done < <(git log --all --format='%H%x09%an%x09%ae%x09%cn%x09%ce')

echo "4/4 fuso horário diferente de UTC"
fusos=$(git log --all --format='%H %ad %cd' --date=format:'%z' | awk '$2!="+0000" || $3!="+0000" {print "  commit " substr($1,1,12) ": fuso " $2 " / " $3}')
if [[ -n "$fusos" ]]; then
  echo "$fusos"
  achados=1
fi

if [[ $achados -ne 0 ]]; then
  echo "audit-history: há achados. Veja docs/MAINTAINER_CHECKLIST.md antes de abrir o repositório." >&2
  exit 1
fi
echo "audit-history: nenhum achado"
