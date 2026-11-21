# ADR-0074: Sucessão nos governos estaduais de 2026

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Oito governadores eleitos em 2022 saíram para concorrer em 2026 (ADR-0033).
O ADR-0072 deu perfil aos vices, mas o perfil não dizia quem assumiu o governo.
O ADR-0033 encerra o mandato do titular no prazo legal de renúncia, 2026-04-04, e avisa que o TSE não informa a data exata.

A data da posse do sucessor e a da renúncia só vêm de fonte oficial, por contribuição (ADR-0006).
Toda contribuição precisa de snapshot no Internet Archive.
Os diários oficiais desses estados não têm snapshot, e em vários deles a posse não sai publicada.

## Decisão

1. A sucessão entra em `contrib/estadual/governador/{id}/mandato.yaml`, com fonte T0 e snapshot já existente no Internet Archive.
   Ordem de preferência: diário oficial, portal da assembleia, portal de notícias do governo do estado.
2. No perfil do vice que assumiu, a contribuição traz:
   - o mandato de governador, da posse até 2027-01-06, fim dos mandatos de 2022 pelo art. 28 na redação da EC 111/2021;
   - o fim do mandato de vice, na data da posse no governo.
3. No perfil do titular, a contribuição troca o fim no prazo de renúncia pela data da fonte.
4. Mandato de contribuição vigente de outro cargo passa a ser o papel principal do perfil.
   Encerrado, ele disputa o papel principal como fora de exercício, pela data de início.
5. A contribuição muda a situação do papel principal só se o mandato dela está vigente ou substitui o mandato desse papel.
   Motivo: quatro desses vices foram eleitos governador em 2026. Em 2027, a sucessão de 2026 não pode tirar de exercício quem tomou posse por eleição.
6. A contribuição substitui o mandato da regra de `contrib/mandatos.yaml` do mesmo cargo e período.
   Antes, o mandato da regra ficava, porque também traz contribuidor, e o perfil saía com dois mandatos de 2023.
7. Quatro fontes entram na allowlist, todas T0 e de primeira parte: `almt-noticias`, `pb-auniao-noticias`, `ac-agencia-noticias` e `es-governo-noticias`.

## Datas e fontes

| UF | Quem assumiu | Posse | Titular | Fim do titular | Fonte citada |
|---|---|---|---|---|---|
| AC | Mailza Gomes | 2026-04-02 | Gladson Cameli | 2026-04-02 | Agência de Notícias do Acre |
| ES | Ricardo Ferraço | 2026-04-02 | Renato Casagrande | 2026-04-02 | notícias do governo do ES |
| GO | Daniel Vilela | 2026-03-31 | Ronaldo Caiado | 2026-03-31 | portal da Alego |
| MG | Mateus Simões | 2026-03-22 | Romeu Zema | 2026-03-22 | portal da ALMG |
| MT | Otaviano Pivetta | 2026-03-31 | Mauro Mendes | 2026-03-31 | notícias da ALMT |
| PA | Hana Ghassan | 2026-04-02 | Helder Barbalho | 2026-04-02 | portal da Alepa |
| PB | Lucas Ribeiro | 2026-04-02 | João Azevêdo | 2026-04-02 | A União, jornal oficial do estado |
| AM | presidente da Assembleia, interino (sem perfil) | 2026-04-06 no expediente do DOE | Wilson Lima; o vice Tadeu de Souza também saiu | 2026-04-04 | expediente do Diário Oficial do Estado |

Em GO, MT e PB, a notícia diz o dia da semana ou "ontem", e a data vem da data de publicação da própria notícia. O comentário de cada arquivo diz isso.

## Consequências

- Sete perfis de vice passam a governador em exercício, e 26 governos têm governador em exercício no site.
- Os sete titulares ganham a data da fonte no lugar do prazo legal. No AM, o governador e o vice saem com o mandato encerrado em 2026-04-04.
- Em 2027-01-07, quem foi eleito governador em 2026 sai com o mandato vigente da eleição, e os demais, com o mandato encerrado em 2027-01-06.

## Limitações

- AM: o governador e o vice saíram, e o presidente da Assembleia assumiu o governo. A fonte citada é o expediente do Diário Oficial do Estado, com snapshot: a edição nº 35.675, de 2026-04-01, traz governador e vice; a seguinte, nº 35.676, de 2026-04-06, traz o presidente da Assembleia como governador e nenhum vice. O dia da saída, 2026-04-04, vem do Diário Oficial Eletrônico da Aleam (edições extras 2420 e 2421), que não tem snapshot: o servidor da Aleam respondeu 503 à captura do Internet Archive pedida em 2026-11-21. Não validado com snapshot: o dia exato.
- MT: o ato de renúncia publicado no IOMAT (ATO nº 647/2026) diz "com efeitos a partir de 01/04/2026", e a ALMT deu posse ao vice em 2026-03-31. O IOMAT não tem snapshot. Como em GO, o mandato do titular termina na posse do sucessor, 2026-03-31, pela fonte com snapshot.
- A notícia da ALMT diz que o mandato vai até 31 de dezembro de 2026. O projeto usa 2027-01-06, da regra da EC 111/2021 em `contrib/mandatos.yaml`.
- O governador interino do AM não tem perfil: o projeto não cobre o cargo de presidente de assembleia como chefe do Executivo.

## Alternativas consideradas

- **Pedir capturas novas ao Internet Archive**: não feito nesta etapa. Fica a critério do mantenedor, que pode capturar os PDFs do DOE de MT e do diário da Aleam e trocar as fontes.
- **Deduzir a posse do vice do prazo de renúncia**: descartada no ADR-0072. No AM, o vice também renunciou.
