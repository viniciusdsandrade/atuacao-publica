#!/usr/bin/env bash
# Coleta, numa rede no Brasil, as casas que recusam conexão de fora do país e envia o cache cifrado ao CI (ADR-0034).
# Roda na máquina do mantenedor, na raiz do repositório, com o ETL compilado (etl/target/etl.jar) e o gh autenticado.
#
# A cada envio, uma chave nova e aleatória cifra o pacote (AES-256, openssl). A chave vai só para o segredo
# SEMENTE_CHAVE do repositório: não é impressa nem gravada em disco. O asset público fica ilegível sem ela.
set -euo pipefail

FONTES=(almg alrs ales alepe cldf alba alema alerr alap alese)
RELEASE=semente-coleta-local
ASSET=semente-coleta-local.tar.gz.enc

[[ -f etl/target/etl.jar ]] || { echo "semente: compile o ETL antes (cd etl && ./mvnw -q package -DskipTests)"; exit 1; }

for fonte in "${FONTES[@]}"; do
  echo "semente: coletando $fonte"
  java -Xmx4g -jar etl/target/etl.jar fetch "$fonte"
done

pasta=$(mktemp -d)
trap 'rm -rf "$pasta"' EXIT
itens=()
for fonte in "${FONTES[@]}"; do
  if [[ -d "data/raw/$fonte" ]]; then
    itens+=("raw/$fonte")
  fi
  if [[ -f "data/publicado/_meta/$fonte.json" ]]; then
    itens+=("publicado/_meta/$fonte.json")
  fi
done
# Sem os metadados do macOS (arquivos ._*) no pacote.
COPYFILE_DISABLE=1 tar -czf "$pasta/semente.tar.gz" -C data "${itens[@]}"

SEMENTE_CHAVE=$(openssl rand -base64 48)
export SEMENTE_CHAVE
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -md sha256 -pass env:SEMENTE_CHAVE \
  -in "$pasta/semente.tar.gz" -out "$pasta/$ASSET"

if ! gh release view "$RELEASE" > /dev/null 2>&1; then
  gh release create "$RELEASE" --title "Semente da coleta local" --latest=false \
    --notes "Cache cifrado das casas que recusam conexão de fora do Brasil (ADR-0034). Ilegível sem o segredo do CI."
fi
# O asset sobe antes do segredo: se o envio falhar, o CI segue com o par anterior de asset e chave.
gh release upload "$RELEASE" "$pasta/$ASSET" --clobber
printf '%s' "$SEMENTE_CHAVE" | gh secret set SEMENTE_CHAVE
unset SEMENTE_CHAVE
echo "semente: enviada com ${#itens[@]} itens ($(du -h "$pasta/$ASSET" | cut -f1))"
