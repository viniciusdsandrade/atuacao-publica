#!/usr/bin/env bash
# Coleta, numa rede no Brasil, as casas que recusam conexão de fora do país e envia o cache cifrado ao CI (ADR-0034).
# Roda na máquina do mantenedor, na raiz do repositório, com o ETL compilado (etl/target/etl.jar) e o gh autenticado.
#
# A cada envio, uma chave nova e aleatória cifra o pacote (AES-256, openssl). A chave vai só para o segredo
# SEMENTE_CHAVE do repositório: não é impressa nem gravada em disco. O asset público fica ilegível sem ela.
#
# O nome do asset traz a impressão da chave (os 16 primeiros dígitos do SHA-256 dela). O CI procura o asset da chave
# que tem em mãos: a troca de pacote e chave não deixa nenhuma execução sem semente, mesmo com uma em andamento.
#
# SEMENTE_SIMULAR=1: não coleta, não envia e não troca o segredo. Monta e cifra o pacote com o cache que já existe
# e mostra o que faria.
set -euo pipefail

FONTES=(almg alrs ales alepe cldf alba alema alerr alap alese caraguatatuba)
RELEASE=semente-coleta-local
simular="${SEMENTE_SIMULAR:-}"

[[ -f etl/target/etl.jar ]] || { echo "semente: compile o ETL antes (cd etl && ./mvnw -q package -DskipTests)"; exit 1; }

if [[ "$simular" == 1 ]]; then
  echo "semente: simulação: sem coleta"
else
  for fonte in "${FONTES[@]}"; do
    echo "semente: coletando $fonte"
    java -Xmx4g -jar etl/target/etl.jar fetch "$fonte"
  done
fi

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
[[ ${#itens[@]} -gt 0 ]] || { echo "semente: nenhum cache das casas em data/; nada a enviar"; exit 1; }
# Sem os metadados do macOS (arquivos ._*) no pacote.
COPYFILE_DISABLE=1 tar -czf "$pasta/semente.tar.gz" -C data "${itens[@]}"

SEMENTE_CHAVE=$(openssl rand -base64 48)
export SEMENTE_CHAVE
impressao=$(printf '%s' "$SEMENTE_CHAVE" | openssl dgst -sha256 -r | cut -c1-16)
asset="semente-$impressao.tar.gz.enc"
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -md sha256 -pass env:SEMENTE_CHAVE \
  -in "$pasta/semente.tar.gz" -out "$pasta/$asset"

if [[ "$simular" == 1 ]]; then
  unset SEMENTE_CHAVE
  echo "semente: simulação: enviaria $asset com ${#itens[@]} itens ($(du -h "$pasta/$asset" | cut -f1)) e trocaria o segredo"
  exit 0
fi

if ! gh release view "$RELEASE" > /dev/null 2>&1; then
  gh release create "$RELEASE" --title "Semente da coleta local" --latest=false \
    --notes "Cache cifrado das casas que recusam conexão de fora do Brasil (ADR-0034). Ilegível sem o segredo do CI."
fi
# O asset sobe antes do segredo: se o envio falhar, o CI segue com o par anterior de asset e chave.
gh release upload "$RELEASE" "$pasta/$asset"
printf '%s' "$SEMENTE_CHAVE" | gh secret set SEMENTE_CHAVE
unset SEMENTE_CHAVE
# Ficam o pacote novo e o anterior: a execução do CI que já leu a chave antiga ainda acha o pacote dela.
gh release view "$RELEASE" --json assets --jq '.assets | sort_by(.createdAt) | reverse | .[2:] | .[].name' \
  | while IFS= read -r antigo; do
      gh release delete-asset "$RELEASE" "$antigo" --yes
      echo "semente: asset antigo removido: $antigo"
    done
echo "semente: enviada com ${#itens[@]} itens ($(du -h "$pasta/$asset" | cut -f1))"
