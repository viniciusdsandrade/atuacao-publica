# ADR-0071: Virada de legislatura nas assembleias

- **Status**: Aceita
- **Data**: 2026-11-20

## Contexto

As legislaturas estaduais e a distrital iniciadas em 2023 terminam entre 2027-01-05 e 2027-01-31.
A seguinte começa com a posse dos eleitos em 2026 (ADR-0070).

Os adaptadores das 27 casas estaduais e distrital fixam no código a legislatura que cobrem.
Exemplo: `Alems.FIM_LEG_12 = 2027-01-31`.
Cada um dá a quem a casa lista como em exercício o mandato dessa legislatura, com eleição de 2022.

O `publish` não confere essa marca contra a data do mandato.
A pessoa sai em exercício porque o adaptador disse, mesmo com o mandato encerrado.

Depois da posse, a listagem das casas passa a trazer a legislatura nova.
Sem mudança, o site publicaria dois erros:

- o reeleito em exercício num mandato que já acabou;
- o estreante com um mandato de 2023 a 2027 que ele não exerceu.

O segundo erro é dado inventado, o que a política editorial proíbe.

O ADR-0059 já guarda, para 17 casas, a data em que cada parlamentar apareceu pela primeira vez na listagem (`primeiroVistoEm`).

## Decisão

1. No `publish`, perfil de casa legislativa só fica em exercício se algum mandato registrado pela casa não acabou.
   Se todos os mandatos têm fim anterior a hoje, o perfil sai de em exercício, com a situação "Mandato encerrado em {fim}".
   Mandato sem data de fim decide a favor do que a casa diz.
   A regra vale para todas as casas, federais, estaduais e municipais.
   Na assembleia, a trava espera a posse dos sucessores, quando `contrib/mandatos.yaml` tem a regra da UF.
   Motivo: o SAPL da ALEPI encerra os 42 mandatos em 2026-12-31, e o adaptador da ALERR, em 2026-12-31, mas a posse seguinte é em 2027-02-01 no Piauí e em 2027-01-05 em Roraima (ADR-0070).
   Quem está no cargo segue nele até a posse seguinte.
2. No histórico do ADR-0059, quem aparece na listagem pela primeira vez depois do fim da legislatura coberta não entra na normalização dessa legislatura.
   A pessoa segue no arquivo acumulado, com a data em que apareceu.
   O fim de cada legislatura vem das constantes dos próprios adaptadores, numa tabela em `HistoricoDeputados`.
3. Na ALESC, a ficha publicada no portal só conta para a legislatura coberta se a data de publicação estiver dentro dela.
4. A cobertura da legislatura nova é trabalho de cada adaptador, depois da posse, com a fonte validada.
   Até lá, o eleito fica com o mandato do ADR-0070: "sem registro de exercício na Assembleia Legislativa".
5. A saída do `publish` e do `normalize` conta os casos de cada casa.

## Consequências

- A troca de legislatura não publica mandato que a fonte não registra.
- Entre a posse e a atualização de cada adaptador, os deputados estaduais aparecem fora de exercício, com o mandato anterior encerrado. Isso é o que o projeto consegue afirmar com a fonte.
- Despesas, proposições e votos da legislatura nova ficam de fora para o estreante até o adaptador cobrir essa legislatura.

## Limitações

- Na Câmara e no Senado, a legislatura vem da API e muda sozinha. A trava só age se a API demorar a trazer a legislatura nova.
- Uma reconstrução do cache do zero depois da posse marca todo mundo como visto depois do fim. Os perfis da casa sumiriam, e a trava do ADR-0006 bloqueia a publicação. A falha é ruidosa de propósito.
- Na ALESC, a regra que aceita a ficha por despesa na legislatura (`temDespesaNaLegislatura`) olha os anos até o ano corrente e não foi limitada ao fim da legislatura.
- Na CLDF, o adaptador termina a 9ª legislatura em 2027-01-31, e a posse seguinte é em 2027-01-06. A trava só age em 2027-02-01. Entre as duas datas, os distritais da legislatura encerrada seguem em exercício no site. O mesmo vale um dia para a ALRS, com posse em 2027-01-31. Resolvido pelo ADR-0073.
- Os adaptadores que leem o SAPL filtram pela legislatura da fonte. Não foi conferido se o filtro inclui a legislatura nova.

## Alternativas consideradas

- **Atualizar os 26 adaptadores antes da posse**: descartada como única medida. A legislatura nova ainda não existe nas fontes, e o número e o identificador dela em cada sistema não foram validados.
- **Esconder os perfis estaduais depois da posse**: descartada. A trava do ADR-0006 impede perfil que some, e o histórico da legislatura encerrada continua válido.
- **Tirar a data do mandato da trava e usar só a listagem**: descartada. É o comportamento atual, que produz os dois erros.
