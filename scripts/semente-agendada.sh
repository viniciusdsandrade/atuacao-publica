#!/usr/bin/env bash
# Coleta local agendada (ADR-0034): atualiza o clone, compila o ETL e envia a semente.
# Roda pelo agendador do sistema, num clone separado do de trabalho, criado por scripts/semente-agendar.sh.
# O clone de trabalho fica livre: nada aqui mexe nele, e uma compilação lá não derruba a coleta daqui.
#
# Uma trava impede duas coletas ao mesmo tempo. Trava com mais de seis horas é de execução que morreu, e é desfeita.
# SEMENTE_SIMULAR=1 passa para o envio: atualiza e compila, mas não coleta, não envia e não troca o segredo.
# SEMENTE_RAMO troca o ramo seguido, para ensaio (padrão: main).
# SEMENTE_HORA (HH:MM, horário local da coleta diária) e SEMENTE_ESTADO (arquivo com a hora UTC do último envio) vêm
# do agendamento, que roda também na entrada da sessão. Com os dois, não há coleta quando o último envio já cobre o
# horário diário mais recente.
set -euo pipefail

raiz=$(cd "$(dirname "$0")/.." && pwd)
cd "$raiz"
trava="${TMPDIR:-/tmp}/atuacao-publica-semente.trava"
ramo="${SEMENTE_RAMO:-main}"
hora="${SEMENTE_HORA:-}"
estado="${SEMENTE_ESTADO:-}"
soltar() { rmdir "$trava" 2> /dev/null || true; }

# O envio cobre o horário diário mais recente quando veio depois dele ou até seis horas antes:
# a coleta feita na entrada da sessão, à noite, já serve para a madrugada seguinte.
envio_cobre_o_horario() {
  [[ "$hora" =~ ^([01][0-9]|2[0-3]):[0-5][0-9]$ && -n "$estado" && -s "$estado" ]] || return 1
  local enviado marco
  enviado=$(head -n 1 "$estado")
  [[ "$enviado" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || return 1
  marco=$(date -j -f '%Y-%m-%d %H:%M:%S' "$(date +%Y-%m-%d) $hora:00" +%s)
  if (( marco > $(date +%s) )); then
    marco=$(( marco - 86400 ))
  fi
  (( $(date -j -u -f '%Y-%m-%dT%H:%M:%SZ' "$enviado" +%s) >= marco - 6 * 3600 ))
}

if [[ "${SEMENTE_AGENDADA_FASE:-1}" == 1 ]]; then
  if [[ "${SEMENTE_SIMULAR:-}" != 1 ]] && envio_cobre_o_horario; then
    echo "semente agendada: o envio de $(head -n 1 "$estado") já cobre o horário das $hora; nada a fazer"
    exit 0
  fi
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
  # Ao acordar do repouso, a interface de rede pode levar alguns segundos para responder ao DNS.
  espera="${SEMENTE_ESPERA_REDE_S:-60}"
  tentativas=$(( (espera + 2) / 3 ))
  if [[ "$espera" -gt 0 ]]; then
    for ((i = 1; i <= tentativas; i++)); do
      if git ls-remote --quiet origin "$ramo" > /dev/null 2>&1; then
        break
      fi
      if [[ "$i" -eq 1 ]]; then
        echo "semente agendada: aguardando rede após despertar (até ${espera}s)"
      fi
      if [[ "$i" -lt "$tentativas" ]]; then
        sleep 3
      fi
    done
  fi
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
fim=$(date -u +%Y-%m-%dT%H:%M:%SZ)
if [[ -n "$estado" && "${SEMENTE_SIMULAR:-}" != 1 ]]; then
  printf '%s\n' "$fim" > "$estado"
fi
echo "semente agendada: fim em $fim"
