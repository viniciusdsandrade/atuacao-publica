#!/usr/bin/env bash
# Abre uma issue quando uma fonte passa de ALERTA_DIAS dias sem coleta válida (padrão: 3).
# Vale para a fonte que falha e para a casa da coleta local (ADR-0034) cuja semente envelheceu: nela o cursor diz
# "ok", mas com a data da última coleta feita na máquina do mantenedor.
# Fecha a issue quando a coleta da fonte volta. Uma issue aberta por fonte, com o rótulo "coleta".
# Lê os cursores em data/publicado/_meta/{fonte}.json, ou na pasta indicada por ALERTA_META.
# Precisa de gh autenticado (no CI, GH_TOKEN) e de jq.
# Uso:
#   scripts/alerta-coleta.sh                    abre e fecha issues
#   ALERTA_SIMULAR=1 scripts/alerta-coleta.sh   só mostra o que faria
set -euo pipefail
shopt -s nullglob

raiz=$(git rev-parse --show-toplevel)
pasta="${ALERTA_META:-$raiz/data/publicado/_meta}"
dias="${ALERTA_DIAS:-3}"
rotulo=coleta

executar() {
  if [[ "${ALERTA_SIMULAR:-}" == 1 ]]; then
    echo "alerta-coleta: simulação: $*"
  else
    "$@"
  fi
}

# Endpoint REST de issues, não a busca: a busca demora a enxergar issue recém-criada.
abertas=$(gh api --method GET "repos/{owner}/{repo}/issues" -f state=open -f labels="$rotulo" -f per_page=100 \
  --jq '[.[] | select(.pull_request == null) | {number, title}]')
execucao=""
if [[ -n "${GITHUB_RUN_ID:-}" ]]; then
  execucao="Execução: ${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
fi

conferidos=0
for cursor in "$pasta"/*.json; do
  [[ "$(basename "$cursor")" == execucao.json ]] && continue
  conferidos=$((conferidos + 1))
  fonte=$(jq -r '.fonte' "$cursor")
  status=$(jq -r '.status' "$cursor")
  titulo="coleta sem dado válido: $fonte"
  numeros=$(jq -r --arg t "$titulo" '.[] | select(.title == $t) | .number' <<< "$abertas")
  # Última coleta válida mais antiga que o limite, com qualquer status. Sem nenhuma coleta válida, só conta a falha:
  # fonte nova, que ainda não rodou, não é alerta.
  falhando=$(jq -r --argjson dias "$dias" '
    if .ultima_coleta_ok == null then .status == "falha"
    else (now - (.ultima_coleta_ok | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601)) > $dias * 86400 end' "$cursor")

  if [[ "$falhando" == true && -z "$numeros" ]]; then
    corpo=$(jq -r --argjson dias "$dias" --arg execucao "$execucao" '
      (if .ultima_coleta_ok == null then "A fonte `\(.fonte)` não tem nenhuma coleta válida.\n\n"
       else "A fonte `\(.fonte)` está sem coleta válida há mais de \($dias) dias.\n\n" end)
      + "- Status da última execução: \(.status)\n"
      + "- Última execução: \(.ultima_execucao // "nenhuma")\n"
      + "- Última coleta válida: \(.ultima_coleta_ok // "nenhuma")\n"
      + "- Erros:\n"
      + (if (.erros | length) == 0 then "  - nenhum registrado" else [.erros[:5][] | "  - " + .] | join("\n") end) + "\n\n"
      + (if .status == "falha" then "O site mantém o último dado válido e marca os blocos da fonte como desatualizados.\n"
         else "O status não é de falha e a data não anda: é o caso de casa da coleta local (ADR-0034) com a semente antiga. Confira o agendamento ou rode `scripts/semente-enviar.sh`.\n\nO site mostra o dado da última coleta local, com a data dela, sem o aviso de fonte desatualizada.\n" end)
      + "Esta issue fecha sozinha quando a coleta voltar.\n"
      + (if $execucao == "" then "" else "\n" + $execucao + "\n" end)' "$cursor")
    executar gh label create "$rotulo" --color B60205 --description "Fonte sem coleta válida" --force
    executar gh issue create --title "$titulo" --label "$rotulo" --body "$corpo"
    echo "alerta-coleta: $fonte sem coleta válida; issue aberta"
  elif [[ -n "$numeros" && "$falhando" != true && ( "$status" == ok || "$status" == parcial ) ]]; then
    ok=$(jq -r '.ultima_coleta_ok' "$cursor")
    for numero in $numeros; do
      executar gh issue close "$numero" --comment "A coleta voltou: status $status, coleta válida em $ok."
      echo "alerta-coleta: $fonte voltou; issue #$numero fechada"
    done
  fi
done
echo "alerta-coleta: $conferidos fonte(s) conferida(s)"
