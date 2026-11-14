#!/usr/bin/env bash
# Restaura, no CI, o cache das casas coletadas na rede do mantenedor (ADR-0034). Sem o segredo, não faz nada.
# Grava em SEMENTE (GITHUB_ENV) as fontes restauradas, para o workflow pular a coleta delas.
#
# O asset é o da chave que o CI tem: o nome traz a impressão dela (16 primeiros dígitos do SHA-256).
# O nome fixo, de antes da impressão, ainda vale enquanto a release tiver um asset com ele.
#
# Variáveis: SEMENTE_CHAVE (segredo), GH_TOKEN e GITHUB_ENV (do Actions).
set -euo pipefail

RELEASE=semente-coleta-local
ASSET_ANTIGO=semente-coleta-local.tar.gz.enc

if [[ -z "${SEMENTE_CHAVE:-}" ]]; then
  echo "semente: segredo ausente; nenhuma casa restaurada"
  exit 0
fi

pasta=$(mktemp -d)
trap 'rm -rf "$pasta"' EXIT
impressao=$(printf '%s' "$SEMENTE_CHAVE" | openssl dgst -sha256 -r | cut -c1-16)
asset="semente-$impressao.tar.gz.enc"
if ! gh release download "$RELEASE" --pattern "$asset" --dir "$pasta" > /dev/null 2>&1; then
  asset="$ASSET_ANTIGO"
  if ! gh release download "$RELEASE" --pattern "$asset" --dir "$pasta" > /dev/null 2>&1; then
    echo "semente: release $RELEASE sem o asset desta chave; nenhuma casa restaurada"
    exit 0
  fi
fi
if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -md sha256 -pass env:SEMENTE_CHAVE \
    -in "$pasta/$asset" -out "$pasta/semente.tar.gz" 2> /dev/null; then
  echo "semente: a chave não abre o asset; nenhuma casa restaurada"
  exit 0
fi

# Só entram caminhos esperados: cache bruto e cursores das casas da semente.
if tar -tzf "$pasta/semente.tar.gz" | grep -vqE '^(raw/(almg|alrs|ales|alepe|cldf|alba|alema|alerr|alap|alese|caraguatatuba)(/.*)?|publicado/_meta/(almg|alrs|ales|alepe|cldf|alba|alema|alerr|alap|alese|caraguatatuba)\.json)$'; then
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
