#!/usr/bin/env bash
# Empacota e cifra os dados brutos e cursores antes do actions/cache/save (ADR-0061).
# Sem CACHE_CHAVE, remove resíduos cifrados e deixa o save aberto tradicional atuar.
set -euo pipefail

SAIDA="${GITHUB_OUTPUT:-/dev/null}"

emitir() {
  echo "usar_cifrado=$1" >> "$SAIDA"
}

if [[ -z "${CACHE_CHAVE:-}" ]]; then
  rm -rf .cache-cifrado
  emitir "false"
  exit 0
fi

itens=()
[[ -d data/raw ]] && itens+=("raw")
[[ -d data/publicado/_meta ]] && itens+=("publicado/_meta")
[[ -f data/publicado/index.json ]] && itens+=("publicado/index.json")

if [[ ${#itens[@]} -eq 0 ]]; then
  rm -rf .cache-cifrado
  emitir "false"
  exit 0
fi

pasta=$(mktemp -d)
trap 'rm -rf "$pasta"' EXIT
COPYFILE_DISABLE=1 tar -czf "$pasta/dados.tar.gz" -C data "${itens[@]}"

mkdir -p .cache-cifrado
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -md sha256 -pass env:CACHE_CHAVE \
  -in "$pasta/dados.tar.gz" -out .cache-cifrado/dados.tar.gz.enc

echo "cache cifrado: pacote gerado ($(du -h .cache-cifrado/dados.tar.gz.enc | cut -f1))"
emitir "true"
