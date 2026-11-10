#!/usr/bin/env bash
# Restaura, no CI, o cache das casas coletadas na rede do mantenedor (ADR-0034). Sem o segredo, não faz nada.
# Grava em SEMENTE (GITHUB_ENV) as fontes restauradas, para o workflow pular a coleta delas.
#
# Variáveis: SEMENTE_CHAVE (segredo), GH_TOKEN e GITHUB_ENV (do Actions).
set -euo pipefail

RELEASE=semente-coleta-local
ASSET=semente-coleta-local.tar.gz.enc

if [[ -z "${SEMENTE_CHAVE:-}" ]]; then
  echo "semente: segredo ausente; nenhuma casa restaurada"
  exit 0
fi

pasta=$(mktemp -d)
trap 'rm -rf "$pasta"' EXIT
if ! gh release download "$RELEASE" --pattern "$ASSET" --dir "$pasta" > /dev/null 2>&1; then
  echo "semente: release $RELEASE sem o asset; nenhuma casa restaurada"
  exit 0
fi
if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -md sha256 -pass env:SEMENTE_CHAVE \
    -in "$pasta/$ASSET" -out "$pasta/semente.tar.gz" 2> /dev/null; then
  echo "semente: a chave não abre o asset (envio em andamento?); nenhuma casa restaurada"
  exit 0
fi

# Só entram caminhos esperados: cache bruto e cursores das casas da semente.
if tar -tzf "$pasta/semente.tar.gz" | grep -vqE '^(raw/(almg|alrs|ales|alepe|cldf|alba)(/.*)?|publicado/_meta/(almg|alrs|ales|alepe|cldf|alba)\.json)$'; then
  echo "semente: o pacote traz caminho inesperado; nada restaurado"
  exit 1
fi
mkdir -p data
tar -xzf "$pasta/semente.tar.gz" -C data

fontes=$(tar -tzf "$pasta/semente.tar.gz" | sed -nE 's#^publicado/_meta/([a-z]+)\.json$#\1#p' | sort -u | tr '\n' ' ')
echo "SEMENTE= ${fontes}" >> "$GITHUB_ENV"
for fonte in $fontes; do
  echo "semente: $fonte restaurada, coleta de $(jq -r '.ultima_coleta_ok // .terminado_em // "data desconhecida"' "data/publicado/_meta/$fonte.json")"
done
