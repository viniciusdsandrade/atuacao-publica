# ADR-0029: Túnel SSH por uma máquina no Brasil para as casas que recusam conexão de fora do país

- **Status**: Aceita. Ativação pendente: o mantenedor cria a máquina e os segredos.
- **Data**: 2026-11-06

## Contexto

O workflow `dados` roda em runners do GitHub Actions nos EUA.
Cinco casas não aceitam conexão desses runners: a conexão TCP expira.
São ALMG, ALRS, ALES, ALEPE e CLDF.
Nenhuma delas teve coleta válida no CI, e o site não tem perfil dessas casas.
A coleta a partir de uma rede no Brasil funciona.

Teste de 2026-11-06 com provas do Globalping, nos endereços que o ETL usa:

| Casa | Azure eastus e AWS us-east-1 | AWS, Google Cloud e Oracle em São Paulo | Rede residencial no Brasil |
|---|---|---|---|
| ALRS (portas 443 e 5000) | recusa | responde | responde |
| ALES | recusa | responde | responde |
| CLDF | recusa | responde | responde |
| ALMG | recusa | só o Google Cloud responde (AWS e Oracle recusam) | responde |
| ALEPE | recusa | recusa | responde |

A Vultr em São Paulo também respondeu para ALMG, ALRS e ALES. A CLDF não foi testada nela.

## Decisão

1. Uma máquina virtual no Google Cloud em São Paulo (`southamerica-east1`) serve de saída para quatro casas: ALMG, ALRS, ALES e CLDF. É a única nuvem testada que respondeu para as quatro.
2. O caminho é um túnel SSH, não um proxy HTTP:
   - o cliente HTTP da JDK não fala SOCKS;
   - um proxy HTTP com senha mandaria a credencial sem criptografia até a máquina.
3. O passo `túnel pelo Brasil` do workflow roda `scripts/tunel-brasil.sh` antes das coletas:
   - abre um encaminhamento local por host (`ssh -L`), com um endereço de loopback para cada um, porque todos usam a porta 443;
   - fixa a chave do servidor por `known_hosts`, sem confiar na primeira conexão;
   - aponta os quatro nomes para o loopback no `/etc/hosts` do runner, só depois de o túnel abrir.
4. Na máquina, a chave do usuário do túnel só permite encaminhar para esses quatro hosts (`restrict,port-forwarding,permitopen=...`), sem shell.
5. Segredos do repositório: `TUNEL_SSH_DESTINO`, `TUNEL_SSH_CHAVE` e `TUNEL_SSH_KNOWN_HOSTS`. Sem eles, o passo não faz nada, e as coletas falham como antes.
6. O ETL não muda. Ele resolve os nomes pelo sistema e conecta como sempre.

## Consequências

- ALMG, ALRS, ALES e CLDF passam a ser coletadas pelo CI, com o mesmo código da coleta local.
- A máquina tem custo mensal e precisa de manutenção (atualizações do sistema). O mantenedor confere o preço na calculadora do provedor.
- Se a máquina cair, o passo falha sem derrubar o job, e só as coletas dessas casas falham.
- O endereço da máquina fica só em segredo. O script não o imprime, porque o log do Actions fica público se o repositório abrir.

## Limitações

- A ALEPE segue fora: recusa todos os datacenters testados, inclusive os de São Paulo. Só uma rede residencial resolveria.
- A lista de hosts está no script e na chave da máquina. Casa nova com o mesmo bloqueio exige mudar os dois.
- Não validado de ponta a ponta: depende da máquina, que ainda não existe. O teste de rede foi feito com o Globalping, não com o túnel.

## Alternativas consideradas

- Proxy HTTP com senha (por exemplo, tinyproxy): descartado. A senha iria sem criptografia, e a porta do proxy ficaria exposta.
- Runner próprio em rede residencial: resolveria as cinco casas, inclusive a ALEPE. Descartado pelo mantenedor em 2026-11-06: a máquina precisa estar ligada no horário da execução, e o nome do runner aparece nos logs.
- `sshuttle`: descartado. Exige Python na máquina e não permite restringir a chave aos destinos.
- Aceitar a limitação: descartado pelo mantenedor em 2026-11-06.
