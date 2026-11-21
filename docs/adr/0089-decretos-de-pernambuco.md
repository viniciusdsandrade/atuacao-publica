# ADR-0089: Decretos do Governo de Pernambuco

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O levantamento do ADR-0075 achou os decretos de PE, BA e SC em HTML.
Em PE, o sistema de legislação da Alepe publica, numa página por ano, todos os decretos do Executivo, com número, data de publicação no Diário Oficial, ementa e link do texto.
Na BA, o Legisla Bahia só negocia TLS com troca de chave RSA, que o Java 25 desativa por padrão. Em SC, os filtros do portal de legislação não foram validados.

## Decisão

1. Um adaptador `executivope`, com a fonte T0 `alepe-legislacao`: a lista de decretos do Executivo de cada ano, desde 2022. O ano aberto renova uma vez por semana.
2. A data do ato é a da publicação no Diário Oficial, a que a lista traz. Decreto sem publicação na lista, ou com data depois de hoje (um decreto de 2025 sai publicado em 2054), fica de fora.
3. Os decretos vão para o governador de cada período, como nos outros estados (ADR-0075).
4. Os decretos da BA não entram: o projeto não relaxa a configuração de segurança da JVM para uma fonte. A decisão fica com o mantenedor.

## Consequências

- 9.576 decretos de 2022 a 2026. O perfil de Raquel Lyra ganha 7.327, desde 2023.

## Limitações

- A data é a da publicação, não a da assinatura.
- Os decretos orçamentários (créditos suplementares) entram com os demais: a lista não separa.
- Não validado: se a Alepe responde ao runner do GitHub, fora do Brasil.
