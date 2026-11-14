#!/usr/bin/env bash
# Restaura, no CI, o cache cifrado de dados brutos e cursores (ADR-0061).
# Sem CACHE_CHAVE ou sem pacote cifrado, apenas sinaliza fallback para o cache aberto legado.
set -euo pipefail

SAIDA="${GITHUB_OUTPUT:-/dev/null}"
PACOTE=".cache-cifrado/dados.tar.gz.enc"

emitir() {
  echo "cifrado_restaurado=$1" >> "$SAIDA"
}

if [[ -z "${CACHE_CHAVE:-}" ]]; then
  emitir "false"
  exit 0
fi

if [[ ! -f "$PACOTE" ]]; then
  echo "cache cifrado: pacote ausente; tentando fallback aberto"
  emitir "false"
  exit 0
fi

pasta=$(mktemp -d)
trap 'rm -rf "$pasta"' EXIT

if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -md sha256 -pass env:CACHE_CHAVE \
    -in "$PACOTE" -out "$pasta/dados.tar.gz" 2> /dev/null; then
  echo "cache cifrado: chave atual não abre o pacote; reconstruindo a partir do fallback ou coleta"
  rm -f "$PACOTE"
  emitir "false"
  exit 0
fi

# Só entram os três caminhos previstos pelo workflow dados.yml.
if tar -tzf "$pasta/dados.tar.gz" | grep -vqE '^(raw(/.*)?|publicado/_meta(/.*)?|publicado/index\.json)$'; then
  echo "cache cifrado: o pacote traz caminho inesperado; nada restaurado"
  exit 1
fi

mkdir -p data
tar -xzf "$pasta/dados.tar.gz" -C data
rm -rf .cache-cifrado
echo "cache cifrado: dados brutos e cursores restaurados"
emitir "true"
