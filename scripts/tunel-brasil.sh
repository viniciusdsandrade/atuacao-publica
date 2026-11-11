#!/usr/bin/env bash
# Túnel SSH por uma máquina no Brasil para as casas que recusam conexão de fora do país (ADR-0029).
# Roda no runner do GitHub Actions, antes das coletas. Sem os três segredos, não faz nada.
#
# Variáveis de ambiente (segredos do repositório):
#   TUNEL_SSH_DESTINO      usuario@host da máquina no Brasil
#   TUNEL_SSH_CHAVE        chave privada do usuário do túnel
#   TUNEL_SSH_KNOWN_HOSTS  linha de known_hosts da máquina, que fixa a chave do servidor
#   TUNEL_SSH_PORTA        porta do SSH (opcional, padrão 22)
#
# Nenhuma mensagem mostra o endereço da máquina: o log do Actions fica público se o repositório abrir.
set -euo pipefail

if [[ -z "${TUNEL_SSH_DESTINO:-}" || -z "${TUNEL_SSH_CHAVE:-}" || -z "${TUNEL_SSH_KNOWN_HOSTS:-}" ]]; then
  echo "túnel: segredos ausentes. ALMG, ALRS, ALES, CLDF, ALEAC e ALERR seguem sem coleta no CI."
  exit 0
fi

# Um endereço de loopback por host, porque todos usam a porta 443. A ALRS usa também a 5000.
# A ALEPE fica fora: recusa também datacenters no Brasil.
ROTAS=(
  "127.0.0.11 dadosabertos.almg.gov.br 443"
  "127.0.0.12 ww4.al.rs.gov.br 443 5000"
  "127.0.0.13 www.al.es.gov.br 443"
  "127.0.0.14 www.cl.df.gov.br 443"
  "127.0.0.15 transparencia.al.rs.gov.br 443"
  "127.0.0.16 www3.al.es.gov.br 443"
  "127.0.0.17 ple.cl.df.gov.br 443"
  "127.0.0.18 sapl.al.ac.leg.br 443"
  "127.0.0.19 sapl.al.rr.leg.br 443"
)

pasta=$(mktemp -d)
chmod 700 "$pasta"
printf '%s\n' "$TUNEL_SSH_CHAVE" > "$pasta/chave"
chmod 600 "$pasta/chave"
printf '%s\n' "$TUNEL_SSH_KNOWN_HOSTS" > "$pasta/known_hosts"

encaminhamentos=()
for rota in "${ROTAS[@]}"; do
  read -r -a campos <<< "$rota"
  for porta in "${campos[@]:2}"; do
    encaminhamentos+=(-L "${campos[0]}:${porta}:${campos[1]}:${porta}")
  done
done

# Porta 443 local exige root. ExitOnForwardFailure faz o ssh sair com erro se algum encaminhamento falhar.
if ! sudo ssh -f -N -q -p "${TUNEL_SSH_PORTA:-22}" -i "$pasta/chave" \
    -o UserKnownHostsFile="$pasta/known_hosts" -o StrictHostKeyChecking=yes \
    -o BatchMode=yes -o IdentitiesOnly=yes -o ExitOnForwardFailure=yes \
    -o ServerAliveInterval=30 -o ServerAliveCountMax=6 -o ConnectTimeout=20 -o LogLevel=QUIET \
    "${encaminhamentos[@]}" "$TUNEL_SSH_DESTINO" > /dev/null 2>&1; then
  rm -f "$pasta/chave"
  echo "túnel: a conexão SSH falhou. As coletas dessas casas seguem pela rede do runner e devem falhar."
  exit 1
fi
rm -f "$pasta/chave"

for rota in "${ROTAS[@]}"; do
  read -r -a campos <<< "$rota"
  for porta in "${campos[@]:2}"; do
    if ! timeout 5 bash -c "exec 3<>/dev/tcp/${campos[0]}/${porta}" 2> /dev/null; then
      echo "túnel: a porta local de ${campos[1]}:${porta} não abriu."
      exit 1
    fi
  done
done

# Só com o túnel de pé os nomes passam a apontar para ele.
for rota in "${ROTAS[@]}"; do
  read -r -a campos <<< "$rota"
  echo "${campos[0]} ${campos[1]}"
done | sudo tee -a /etc/hosts > /dev/null

for url in "https://dadosabertos.almg.gov.br/api/v2/legislaturas/pesquisa_deputados?leg=20&formato=json" \
    "https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados" \
    "https://www.al.es.gov.br/Deputado/Lista" \
    "https://www.cl.df.gov.br/deputados-2023-2026" \
    "https://transparencia.al.rs.gov.br/" \
    "https://www3.al.es.gov.br/" \
    "https://ple.cl.df.gov.br/" \
    "https://sapl.al.ac.leg.br/api/parlamentares/legislatura/" \
    "https://sapl.al.rr.leg.br/api/parlamentares/legislatura/"; do
  codigo=$(curl -s -o /dev/null --max-time 30 -w '%{http_code}' "$url" || true)
  echo "túnel: ${url%%\?*} respondeu ${codigo:-sem resposta}"
done
