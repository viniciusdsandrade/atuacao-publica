#!/usr/bin/env bash
# Para a execução quando existe cache de dados brutos e nenhum foi baixado (ADR-0069).
# Sem o cache, a coleta recomeça do zero, estoura o limite do job e guarda um cache parcial por cima do bom.
# Na primeira execução não há cache, e a coleta segue.
set -euo pipefail

existentes=$(gh cache list --repo "$GITHUB_REPOSITORY" --key dados- --limit 1 --json key --jq length)

if [[ "$existentes" -gt 0 ]]; then
  echo "::error::o repositório tem cache de dados brutos, mas a restauração falhou; a coleta não começa e nenhum cache é guardado"
  exit 1
fi

echo "cache: nenhum cache de dados brutos no repositório; a coleta começa do zero"
