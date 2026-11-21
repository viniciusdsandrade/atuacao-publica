# ADR-0082: Despesas do Executivo do Rio Grande do Sul

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O levantamento do ADR-0075 achou no RS a despesa mensal do Estado desde 2012, em arquivos pesados: um ZIP por mês, de 12 a 16 MB, com um CSV de cerca de 250 MB e um lançamento por linha, com o favorecido.
A agenda do governador fica atrás de um firewall de aplicação, e os decretos só saem em HTML com sessão.

## Decisão

1. Um adaptador `executivors`, com a fonte T0 `rs-despesa-do-estado`: os pacotes anuais "Despesa do Estado" do portal de dados abertos, de 2022 em diante.
2. A coleta soma cada arquivo assim que baixa (`obterDerivado`) e guarda só a soma: o valor da fase Pagamento do Poder Executivo, por mês da data do pagamento e por órgão, com a contagem de pagamentos. O favorecido nunca é lido.
3. O CSV é lido linha a linha, separado por ponto e vírgula. As colunas usadas vêm antes dos campos de texto livre; linha com data ou valor ilegível fica de fora e entra na contagem do log.
4. O poder e a fase se comparam sem caixa: até 2025-01 o arquivo escreve "PODER EXECUTIVO".
5. Um arquivo por mês. Há recurso cujo nome diz um mês e o arquivo, outro ("Abril" de 2022 baixa o arquivo de maio): os dois são versões do mesmo mês. Vale o recurso cujo nome é o mês do arquivo.
6. Os três últimos meses renovam toda semana, porque chegam incompletos; os anteriores vêm do cache.

## Consequências

- O perfil de Eduardo Leite ganha 51 meses de despesa, de 2022-01 a 2026-07, entre R$ 3,9 bilhões e R$ 9,5 bilhões por mês.

## Limitações

- Sem arquivo próprio na fonte: 2022-04, 2023-02, 2023-06 e 2023-08.
- Agosto e setembro de 2026 tinham menos de 20 KB em 2026-11-21, e saem sem dado até a fonte completar.
- A despesa é a do Executivo inteiro, por órgão.
- Não validado: se o portal responde ao runner do GitHub, fora do Brasil.

## Alternativas consideradas

- **Guardar os arquivos no cache**: descartada. Seriam cerca de 700 MB só de 2022 em diante.
