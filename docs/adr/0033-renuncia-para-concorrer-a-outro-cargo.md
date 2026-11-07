# ADR-0033: Fim do mandato do chefe do Executivo que concorre a outro cargo

- **Status**: Aceita
- **Data**: 2026-11-07

## Contexto

O mandato de presidente, governador e prefeito vem do TSE, que registra a eleição, e das datas legais de `contrib/mandatos.yaml`.
Até aqui, renúncia e sucessão só entravam por contribuição com fonte T0.
Em 2026-11-06, o site mostrava em exercício oito governadores eleitos em 2022 que têm, no próprio TSE, candidatura a outro cargo em 2026: senador no AC, AM, ES, MT, PA e PB e presidente em GO e MG.
Para concorrer a outro cargo, o chefe do Executivo renuncia até seis meses antes do pleito (Constituição, art. 14, § 6º).
Logo, esses oito não estavam em exercício em novembro de 2026.

## Decisão

1. Para presidente, governador e prefeito, o ETL procura candidatura da mesma pessoa a outro cargo na eleição seguinte (quatro anos depois da eleição do mandato). A pessoa é a do CPF ou, sem ele, a do nome civil com a data de nascimento (ADR-0003). Reeleição para o mesmo cargo não conta.
2. O prazo de renúncia é seis meses antes do primeiro turno, que cai no primeiro domingo de outubro (art. 77): 2026-04-04 na eleição de 2026, 2022-04-02 na de 2022.
3. Com essa candidatura e passado o prazo, o mandato termina no prazo, e o perfil sai de "em exercício". A situação diz o cargo disputado, o prazo e a base legal, e avisa que o TSE não informa a data exata.
4. A candidatura do TSE entra entre as fontes do bloco de mandatos, ao lado da regra de `contrib/mandatos.yaml`.
5. O vice não entra na regra: a Constituição não exige que ele renuncie para concorrer.
6. Contribuição com fonte T0 que traga a data exata da renúncia continua valendo e prevalece.

## Consequências

- No ensaio local de 2026-11-07, os oito governadores de 2022 saem de em exercício. Cinco governadores eleitos em 2018 que concorreram ao Senado em 2022 (AL, CE, MA, PI e TO) deixam de aparecer com o mandato encerrado em 2023-01-01: o fim passa a ser o prazo de 2022-04-02.
- Quem reelegeu ou não concorreu segue como antes.
- O fim do mandato é o prazo legal, não a data do ato. O texto da situação diz isso.

## Limitações

- Quem assumiu o governo, o vice ou o presidente da assembleia, não tem perfil.
- A data exata da renúncia só vem por contribuição com fonte T0 (diário oficial do estado).
- Sem CPF nem nome civil e nascimento, a candidatura a outro cargo não é achada e a regra não se aplica.

## Alternativas consideradas

- Registrar cada renúncia por contribuição com o diário oficial: mais precisa, mas manual em oito estados. Fica como refinamento.
- Manter "em exercício" até contribuição: descartado. O próprio TSE mostra a candidatura que torna o exercício impossível.
