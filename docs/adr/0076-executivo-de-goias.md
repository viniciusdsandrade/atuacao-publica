# ADR-0076: Decretos e despesas do Governo de Goiás

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O levantamento de 2026-11-21 (ADR-0075) achou em Goiás a despesa mais fácil de publicar entre os estados testados.
A execução orçamentária sai nos dados abertos do estado já somada por mês, órgão, função, elemento e ação, sem favorecido.
A Casa Civil publica a legislação estadual numa API JSON, com filtro por tipo e período.
O estado não publica a agenda do governador em formato estruturado.

## Decisão

1. Um adaptador `executivogo`, com duas fontes T0:
   - `go-legisla`: os decretos numerados (tipo 3), um ano por consulta, desde 2022;
   - `go-execucao-orcamentaria`: o pacote CKAN da execução orçamentária, com um ZIP por ano fechado e um CSV por mês do ano corrente.
2. A despesa é o valor pago, somado por mês e por órgão. O registro tem o formato dos de São Paulo.
3. A contagem é de linhas da fonte com valor pago, não de pagamentos: cada linha já é um total. O site mostra "Lançamentos" e diz isso na nota do bloco.
4. Os endereços dos arquivos vêm do próprio pacote (`package_show`). O ano fechado vem do ZIP. O ZIP de 2003 a 2025 fica de fora, porque repetiria o último ano.
5. A nota de método do governo estadual passa a ser por UF. Estado sem fonte validada diz isso no bloco vazio, em vez de citar a fonte de São Paulo.

## Consequências

- O perfil de Ronaldo Caiado ganha 869 decretos e 51 meses de despesa, de 2022-01 a 2026-03.
- O de Daniel Vilela ganha 113 decretos e 7 meses, de 2026-03 a 2026-09.
- O valor pago mensal do Executivo goiano fica entre R$ 1,9 bilhão e R$ 4,0 bilhões no período.

## Limitações

- O mês da sucessão (2026-03) aparece no perfil dos dois governadores, com o total do mês inteiro. A fonte não separa os dias.
- A despesa é a do Executivo inteiro, por órgão, e não só a do Gabinete do Governador.
- Não validado: se a API da Casa Civil e o CKAN de Goiás respondem ao runner do GitHub, fora do país. Se não responderem, a fonte entra na semente da coleta local (ADR-0034).
- O decreto orçamentário (tipo 5) fica de fora.

## Alternativas consideradas

- **Somar por elemento de despesa**: descartada nesta etapa. O órgão é a leitura mais direta e é o recorte do Executivo municipal (ADR-0008).
- **Usar a página da norma no portal da Casa Civil como link**: descartada. A página é montada por JavaScript e não traz o texto no HTML.
