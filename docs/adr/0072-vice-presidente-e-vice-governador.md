# ADR-0072: Vice-presidente e vice-governador

- **Status**: Aceita
- **Data**: 2026-11-20

## Contexto

O modelo cobre o vice só no município (`vice_prefeito`, ADR-0009).
Na União e nos estados, o vice não tinha perfil.

Isso deixa uma lacuna visível.
Oito governadores eleitos em 2022 têm candidatura a outro cargo em 2026, no AC, AM, ES, GO, MG, MT, PA e PB.
Pelo ADR-0033, eles saem de em exercício no prazo de renúncia.
Quem assumiu esses oito governos não tinha perfil no site.

O TSE já publica as candidaturas a vice-presidente e a vice-governador, e a coleta já as guarda.
Nos dados normalizados de 2026-11-15, cada eleição geral (2018, 2022 e 2026) tem 27 vice-governadores e 1 vice-presidente eleitos.

## Decisão

1. Entram os cargos `vice_presidente` e `vice_governador`, no schema comum, nas regras de mandato e no caminho de contribuição.
2. O perfil do vice tem os blocos do `vice_prefeito`: mandatos, candidaturas, financiamento de campanha e posicionamentos declarados.
3. O mandato vem de `contrib/mandatos.yaml`, com as mesmas datas do titular da chapa:
   - vice-presidente: posse em 2019-01-01, 2023-01-01 e 2027-01-05, pelo art. 77, § 1º ("A eleição do Presidente da República importará a do Vice-Presidente com ele registrado"), e pelo art. 78 (posse conjunta);
   - vice-governador eleito em 2018: art. 28 na redação da EC 16/1997, com posse em 1º de janeiro;
   - vice-governador eleito em 2022 e em 2026: art. 28 na redação da EC 111/2021, com posse dos sucessores em 6 de janeiro.
4. Os textos citados foram conferidos, literalmente, nos snapshots do Internet Archive de 2026-10-23, no dia 2026-11-20.
5. A regra de renúncia para concorrer do ADR-0033 não vale para o vice. A Constituição, art. 14, § 6º, só exige a renúncia do chefe do Executivo.
6. A chapa presidencial é federal, com ente `BR` e sem UF, como o presidente.
7. As regras dos vices são aplicadas numa passada própria, depois das demais regras de mandato e dos deputados e senadores eleitos.
   Motivo: três deputados estaduais eleitos em 2026 também foram eleitos vice-governador em 2022. Com a regra do vice aplicada antes, o perfil deles nascia com o id da candidatura a vice, e o id já publicado sumia sem alias, o que dispara a trava do ADR-0006.

## Consequências

- Os oito estados cujo governador saiu para concorrer passam a ter o perfil do vice eleito em 2022, com o mandato de vice vigente.
- O vice-presidente e os vice-governadores eleitos em 2026 aparecem com posse prevista.
- Vice que já tem perfil em outra casa ganha o cargo no mesmo perfil, pelo vínculo do TSE.

## Limitações

- O perfil não diz que o vice assumiu o governo. A sucessão entra só por contribuição com fonte T0, como o Diário Oficial do estado (ADR-0033). Não validado: a data em que cada vice assumiu.
- O vice não tem agenda, despesas nem atos próprios: o projeto não tem fonte validada para isso.

## Alternativas consideradas

- **Mostrar o vice como governador depois do prazo de renúncia do titular**: descartada. O vice pode ter renunciado também, e a data da posse não vem do TSE.
- **Esperar só os vices eleitos em 2026**: descartada. A lacuna dos oito estados existe até 2027-01-06.
