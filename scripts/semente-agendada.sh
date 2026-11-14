#!/usr/bin/env bash
# Coleta local agendada (ADR-0034): atualiza o clone, compila o ETL e envia a semente.
# Roda pelo agendador do sistema, num clone separado do de trabalho, criado por scripts/semente-agendar.sh.
# O clone de trabalho fica livre: nada aqui mexe nele, e uma compilação lá não derruba a coleta daqui.
#
# Uma trava impede duas coletas ao mesmo tempo. Trava com mais de seis horas é de execução que morreu, e é desfeita.
# SEMENTE_SIMULAR=1 passa para o envio: atualiza e compila, mas não coleta, não envia e não troca o segredo.
# SEMENTE_RAMO troca o ramo seguido, para ensaio (padrão: main).
set -euo pipefail

raiz=$(cd "$(dirname "$0")/.." && pwd)
cd "$raiz"
trava="${TMPDIR:-/tmp}/atuacao-publica-semente.trava"
ramo="${SEMENTE_RAMO:-main}"
soltar() { rmdir "$trava" 2> /dev/null || true; }

if [[ "${SEMENTE_AGENDADA_FASE:-1}" == 1 ]]; then
  if [[ -d "$trava" && -n "$(find "$trava" -maxdepth 0 -mmin +360 2> /dev/null)" ]]; then
    echo "semente agendada: trava antiga desfeita"
    soltar
  fi
  if ! mkdir "$trava" 2> /dev/null; then
    echo "semente agendada: outra coleta em andamento; nada a fazer"
    exit 0
  fi
  trap soltar EXIT
  # O registro da execução anterior fica; o que passa de 2 MB perde o começo.
  if [[ -n "${SEMENTE_LOG:-}" && -f "$SEMENTE_LOG" && $(wc -c < "$SEMENTE_LOG") -gt 2097152 ]]; then
    recorte=$(tail -n 2000 "$SEMENTE_LOG")
    printf '%s\n' "$recorte" > "$SEMENTE_LOG"
  fi
  echo "semente agendada: início em $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  git fetch --quiet origin "$ramo"
  git checkout --quiet --detach "origin/$ramo"
  echo "semente agendada: código em $(git rev-parse --short HEAD)"
  # O arquivo deste script pode ter mudado com a atualização: a segunda fase roda a versão nova.
  trap - EXIT
  SEMENTE_AGENDADA_FASE=2 exec /usr/bin/env bash "$raiz/scripts/semente-agendada.sh"
fi

trap soltar EXIT
(cd etl && ./mvnw -B -ntp -q package -DskipTests)
scripts/semente-enviar.sh
echo "semente agendada: fim em $(date -u +%Y-%m-%dT%H:%M:%SZ)"
