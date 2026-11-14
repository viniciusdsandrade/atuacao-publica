# ADR-0034: Coleta local cifrada para as casas que recusam conexão de fora do Brasil

- **Status**: Aceita
- **Data**: 2026-11-07

## Contexto

ALMG, ALRS, ALES, ALEPE e CLDF não aceitam conexão do runner do GitHub Actions nos EUA (ADR-0029).
Em 2026-11-07, os endereços alternativos dessas casas também falharam a partir da Azure e da AWS nos EUA: o portal de dados da CLDF, a API do processo legislativo da CLDF, a API NoPaper da ALES, os serviços e os dados abertos da ALEPE, o portal da ALMG e os portais da ALRS.
Todos responderam a uma rede residencial no Brasil.
O túnel do ADR-0029 depende de uma máquina em nuvem no Brasil, que o mantenedor adiou.
O repositório é público, e um asset de release fica público.
O cache bruto dessas casas tem CPF de fornecedor (verbas da ALMG) e telefone e e-mail de gabinete, que o projeto não publica.

## Decisão

1. O mantenedor coleta as cinco casas na rede dele, com `scripts/semente-enviar.sh`: o próprio ETL baixa, como numa execução comum, e o script empacota o cache bruto e o cursor de cada casa.
2. O pacote é cifrado com AES-256 (`openssl enc`, PBKDF2 com 200 mil iterações). A chave é aleatória, nova a cada envio, e vai só para o segredo `SEMENTE_CHAVE` do repositório. Ela não é impressa nem gravada em disco.
3. O pacote cifrado é o asset da release `semente-coleta-local`. O asset sobe antes do segredo: se o envio falhar, o par anterior de asset e chave continua valendo.
4. No workflow `dados`, `scripts/semente-restaurar.sh` baixa e decifra o pacote antes da normalização. Ele só aceita caminhos do cache e dos cursores das cinco casas. As casas restauradas pulam a coleta no CI, que falharia.
5. O cursor de cada casa vem na semente. O relatório da execução mostra a data da coleta local, e o alerta de fonte sem coleta válida há mais de três dias avisa quando a semente envelhece.
6. Quando o túnel do ADR-0029 estiver ativo, a release é apagada e as casas voltam a ser coletadas pelo CI.

## Consequências

- As cinco casas entram no site com o mesmo código de normalização e de publicação das demais.
- Os dados dessas casas só atualizam quando o mantenedor roda o script.
- O asset público não revela nada sem a chave.

## Limitações

- A atualização depende de ação manual. Sem ela, os blocos dessas casas envelhecem e o alerta de coleta dispara.
- A cifra é simétrica e sem assinatura: a integridade do asset depende de só quem escreve no repositório poder trocá-lo.
- Antes de qualquer mudança de chave, um envio em andamento pode deixar uma execução sem semente. O passo do CI não falha: só não restaura.

## Atualização em 2026-11-10: ALBA

- Nas execuções do `dados` de 2026-11-10, a API pública do NoPaper da ALBA (`albalegis.nopapercloud.com.br`), que dá as proposições, respondeu HTTP 403 ao runner do GitHub Actions. Da rede do mantenedor, respondeu 200.
- No mesmo dia, a lista da verba indenizatória no portal da ALBA respondeu a cerca de uma página a cada 9 segundos no CI, e o orçamento de 30 minutos não bastou para renovar o ano aberto.
- A ALBA entrou na semente: o script de envio coleta as seis casas, o de restauração aceita o cache e o cursor da ALBA, e o passo da ALBA no CI só roda sem semente.

## Atualização em 2026-11-13: ALESE

- Nas execuções do `dados` de 2026-11-12, o sistema legislativo da ALESE (`aleselegis.al.se.leg.br`) não aceitou conexão do runner do GitHub Actions. Da rede do mantenedor, respondeu 200.
- O portal da casa (`al.se.leg.br`) respondeu ao runner. Só ele não basta: fichas, comissões e proposições vêm do sistema legislativo.
- A ALESE entrou na semente: o script de envio coleta as dez casas, o de restauração aceita o cache e o cursor da ALESE, e o passo da ALESE no CI só roda sem semente.
- A ALEMA, a ALERR e a ALAP tinham entrado em 2026-11-12, pelo mesmo motivo.

## Atualização em 2026-11-13: Câmara Municipal de Caraguatatuba

- Na execução do `dados` de 2026-11-13 às 22:04 UTC, o sistema legislativo da Câmara Municipal de Caraguatatuba (`sistema.camaracaragua.sp.gov.br`) não aceitou conexão do runner do GitHub Actions: cinco tentativas, todas com tempo esgotado na conexão. Da rede do mantenedor, respondeu 200 no mesmo dia.
- A coleta para na primeira chamada, que é a esse servidor. O portal da Câmara e o portal da transparência do município não chegaram a ser chamados pelo runner.
- A casa entrou na semente (ADR-0057): o script de envio coleta as onze casas, o de restauração aceita o cache e o cursor de `caraguatatuba`, e o passo da casa no CI só roda sem semente.
- O cache da casa não tem telefone, e-mail nem biografia de vereador, nem nome de servidor: a coleta já os retira.

## Atualização em 2026-11-14: troca sem janela, coleta agendada e alerta de semente antiga

- **Troca de pacote e chave.** O envio apagava o asset, subia o novo e só então trocava o segredo. Uma execução do `dados` que começasse nesse intervalo ficava sem semente e tentava coletar as casas bloqueadas. O asset passa a ter no nome a impressão da chave (os 16 primeiros dígitos do SHA-256 dela), e o CI baixa o asset da chave que tem em mãos. O pacote anterior fica na release até o envio seguinte. O nome fixo de antes ainda é aceito enquanto existir.
- **Coleta agendada.** `scripts/semente-agendar.sh` agenda a coleta diária no macOS, pelo launchd. Ela roda num clone separado do de trabalho, com cache próprio: atualiza para a `main` remota, compila e chama `scripts/semente-enviar.sh`. Uma trava impede duas coletas ao mesmo tempo.
- **Horário.** O padrão é 01:30 no horário da máquina. O `dados` agendado roda às 06:17 UTC e pega a semente da noite. Se a máquina estiver dormindo, o launchd roda a coleta quando ela acordar.
- **Alerta.** O texto deste ADR dizia que o alerta de coleta avisava da semente antiga. Não avisava: só olhava fonte com falha, e o cursor da semente diz "ok". `scripts/alerta-coleta.sh` passa a abrir issue quando a última coleta válida tem mais de três dias, com qualquer status.
- **Simulação.** `SEMENTE_SIMULAR=1` monta e cifra o pacote com o cache que existe, sem coletar, sem enviar e sem trocar o segredo.
- Com o agendamento ativo, a semente sai do clone agendado. O envio à mão a partir do clone de trabalho continua possível, mas leva o cache de lá, que deixa de ser o mais novo.

## Alternativas consideradas

- Release sem cifra: descartada. O repositório é público, e o cache tem CPF e contatos de gabinete.
- Enviar o normalizado em vez do bruto: descartada. O normalizado de algumas casas traz nome civil e data de nascimento, que o ADR-0003 mantém só em memória, e o CI deixaria de reproduzir a normalização.
- Runner na máquina do mantenedor: oferecido em 2026-11-07, não escolhido.
- Máquina em nuvem no Brasil (ADR-0029): adiada pelo mantenedor.
