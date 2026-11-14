#!/usr/bin/env bash
# Agenda a coleta local no macOS, pelo launchd, num clone separado do de trabalho (ADR-0034).
# Roda na raiz do clone de trabalho, com o gh autenticado.
#
# Uso:
#   scripts/semente-agendar.sh [--destino PASTA] [--hora HH:MM] [--copiar-cache]
#   scripts/semente-agendar.sh --remover [--destino PASTA]
#
#   --destino       onde ficam o clone, o cache e o registro (padrão: ~/.atuacao-publica/semente)
#   --hora          horário local da coleta diária (padrão: 01:30; o workflow dados roda às 06:17 UTC)
#   --copiar-cache  leva para o clone o cache das casas da semente que já está neste clone de trabalho
#   --remover       tira o agendamento; o clone e o cache ficam na pasta de destino
set -euo pipefail

ROTULO=atuacao-publica.semente
destino="$HOME/.atuacao-publica/semente"
hora=01:30
copiar=0
remover=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --destino) destino=$2; shift 2 ;;
    --hora) hora=$2; shift 2 ;;
    --copiar-cache) copiar=1; shift ;;
    --remover) remover=1; shift ;;
    *) echo "semente-agendar: opção desconhecida: $1"; exit 2 ;;
  esac
done

[[ "$(uname)" == Darwin ]] || { echo "semente-agendar: só há agendamento pronto para macOS (launchd)"; exit 1; }
agente="$HOME/Library/LaunchAgents/$ROTULO.plist"
dominio="gui/$(id -u)"

if [[ $remover == 1 ]]; then
  launchctl bootout "$dominio/$ROTULO" 2> /dev/null || true
  rm -f "$agente"
  echo "semente-agendar: agendamento removido; o clone e o cache continuam em $destino"
  exit 0
fi

[[ "$hora" =~ ^([01][0-9]|2[0-3]):([0-5][0-9])$ ]] || { echo "semente-agendar: hora inválida: $hora (use HH:MM)"; exit 2; }
horas=$((10#${BASH_REMATCH[1]}))
minutos=$((10#${BASH_REMATCH[2]}))
[[ "$destino" != *[\<\>\&\"]* ]] || { echo "semente-agendar: a pasta de destino não pode ter <, >, & nem aspas"; exit 2; }
for comando in git gh java openssl tar rsync; do
  command -v "$comando" > /dev/null || { echo "semente-agendar: falta o comando $comando"; exit 1; }
done
gh auth status > /dev/null 2>&1 || { echo "semente-agendar: o gh não está autenticado (gh auth login)"; exit 1; }
[[ -f scripts/semente-enviar.sh ]] || { echo "semente-agendar: rode na raiz do repositório"; exit 1; }

mkdir -p "$destino/logs"
clone="$destino/repo"
if [[ ! -d "$clone/.git" ]]; then
  # Endereço https do repositório: o agendador não tem o agente de SSH da sessão.
  git clone --quiet "$(gh repo view --json url --jq .url).git" "$clone"
  echo "semente-agendar: clone criado em $clone"
fi

if [[ $copiar == 1 ]]; then
  fontes=$(sed -nE 's/^FONTES=\((.*)\)$/\1/p' scripts/semente-enviar.sh)
  for fonte in $fontes; do
    if [[ -d "data/raw/$fonte" ]]; then
      mkdir -p "$clone/data/raw/$fonte"
      rsync -a --delete "data/raw/$fonte/" "$clone/data/raw/$fonte/"
    fi
    if [[ -f "data/publicado/_meta/$fonte.json" ]]; then
      mkdir -p "$clone/data/publicado/_meta"
      cp "data/publicado/_meta/$fonte.json" "$clone/data/publicado/_meta/$fonte.json"
    fi
  done
  echo "semente-agendar: cache copiado para $clone/data ($(du -sh "$clone/data" | cut -f1))"
fi

# O agendador não lê o perfil do shell: o PATH leva as pastas dos comandos que valem hoje.
caminho=$(for comando in git gh java openssl tar; do dirname "$(command -v "$comando")"; done | awk '!visto[$0]++' | paste -sd: -)
caminho="$caminho:/usr/bin:/bin:/usr/sbin:/sbin"
java_home=""
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_home="    <key>JAVA_HOME</key><string>$JAVA_HOME</string>"
fi

mkdir -p "$(dirname "$agente")"
cat > "$agente" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$ROTULO</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>$clone/scripts/semente-agendada.sh</string>
  </array>
  <key>WorkingDirectory</key><string>$clone</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key><string>$caminho</string>
    <key>SEMENTE_LOG</key><string>$destino/logs/semente.log</string>
$java_home
  </dict>
  <key>StartCalendarInterval</key>
  <dict>
    <key>Hour</key><integer>$horas</integer>
    <key>Minute</key><integer>$minutos</integer>
  </dict>
  <key>StandardOutPath</key><string>$destino/logs/semente.log</string>
  <key>StandardErrorPath</key><string>$destino/logs/semente.log</string>
  <key>ProcessType</key><string>Background</string>
  <key>LowPriorityIO</key><true/>
  <key>Nice</key><integer>10</integer>
</dict>
</plist>
PLIST
plutil -lint "$agente" > /dev/null
launchctl bootout "$dominio/$ROTULO" 2> /dev/null || true
launchctl bootstrap "$dominio" "$agente"

echo "semente-agendar: coleta local agendada para as $hora, todo dia, no horário desta máquina"
echo "  rodar agora:  launchctl kickstart $dominio/$ROTULO"
echo "  registro:     $destino/logs/semente.log"
echo "  remover:      scripts/semente-agendar.sh --remover"
