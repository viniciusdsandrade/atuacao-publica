# ADR-0069: Parada da coleta quando a restauração do cache falha

- **Status**: Aceita
- **Data**: 2026-11-18

## Contexto

O workflow `dados` restaura no começo o cache de dados brutos e cursores (ADR-0003, ADR-0061).
Com o cache, cada fonte baixa só o que mudou.

Na execução manual de 2026-11-17, o GitHub achou o cache de 981 MiB, mas não entregou o download.
A mensagem foi "Failed to restore: Egress is over the account limit".
A ação de cache trata essa falha como cache ausente.

A coleta recomeçou do zero.
Câmara, SAPL e agenda levaram 2h10 juntas; na execução anterior, com cache, levaram 15 minutos.
O job estourou o limite de 3 horas na ALEP, e a publicação não rodou.

O passo final guardou o cache mesmo assim.
O cache novo, de 759 MiB, não trazia as fontes que não chegaram a rodar.
Como a restauração pega o cache mais recente, a execução seguinte partiria dele.

## Decisão

1. Depois das restaurações, um passo confere se algum cache foi baixado.
2. Se nenhum foi baixado e o repositório tem cache de dados brutos, o passo falha e a coleta não começa.
3. Com esse passo em falha, nenhum cache é guardado.
4. Sem nenhum cache no repositório, a coleta segue do zero, como na primeira execução.

O passo fica em `scripts/cache-conferir.sh`.
O job ganha a permissão `actions: read`, para listar os caches.

## Consequências

- Falha de restauração custa um minuto de execução, não três horas.
- O site e o cache ficam como estavam até a próxima execução.
- O alerta de fonte sem coleta válida continua rodando.

## Limitações

- O cache parcial guardado em 2026-11-17 foi apagado à mão, para a execução seguinte partir do cache completo.
- Se o limite de tráfego persistir, nenhuma execução coleta até ele ceder.

## Alternativas consideradas

- **Restaurar de novo depois de uma pausa**: descartada. O limite é da conta e pode durar horas.
- **Coletar sem cache e só não guardar no fim**: descartada. Gasta três horas e ainda não publica.
