# ADR-0083: Despesas do Executivo da Bahia

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O levantamento do ADR-0075 achou na Bahia a despesa já somada por mês, órgão, função e elemento, sem favorecido, num ZIP único de cerca de 230 MB com todos os anos desde 2013 (`VW_PAINEL_DESPESA.csv`, cerca de 2,4 GB descompactado).
O mesmo ZIP traz outras visões, como a de empenhos, com o credor e o CPF ou CNPJ.

## Decisão

1. Um adaptador `executivoba`, com a fonte T0 `ba-despesas`: o pacote `despesas` do portal de dados abertos.
2. A coleta baixa o ZIP uma vez por semana, soma o painel assim que baixa (`obterDerivado`) e guarda só a soma: o valor pago do poder Executivo por mês e órgão, de 2022 em diante. As outras visões do ZIP não são lidas.
3. A contagem é de linhas da fonte com valor pago, não de pagamentos, como em Goiás (ADR-0076). O site mostra "Lançamentos".

## Consequências

- O perfil de Jerônimo Rodrigues ganha 47 meses de despesa, de 2023-01 a 2026-11, entre R$ 2,2 bilhões e R$ 9,0 bilhões por mês. O mês corrente sai parcial.

## Limitações

- A despesa é a do Executivo inteiro, por órgão.
- Agenda e decretos do governador não têm fonte coletável validada (docs/FONTES.md).
- Não validado: se o portal responde ao runner do GitHub, fora do Brasil.

## Alternativas consideradas

- **Ler o arquivo por faixa de bytes**: descartada. O arquivo muda inteiro a cada atualização.
