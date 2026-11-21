# ADR-0073: A posse dos sucessores encerra o mandato na assembleia

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O ADR-0071 tira de em exercício o perfil de casa legislativa cujos mandatos registrados pela casa já acabaram.
A data de fim vem da fonte, ou da constante do adaptador.

Em duas casas, essa data é posterior à posse dos sucessores:

- CLDF: o adaptador termina a 9ª legislatura em 2027-01-31, e a posse dos distritais eleitos em 2026 é em 2027-01-06, pela regra de `contrib/mandatos.yaml` (ADR-0070);
- ALRS: o adaptador termina a 56ª legislatura em 2027-01-31, e a posse é no mesmo dia.

Sem mudança, os 24 distritais da legislatura encerrada seguiriam em exercício no site de 2027-01-06 a 2027-01-31, e os 55 da ALRS, em 2027-01-31.

## Decisão

1. No `publish`, perfil de assembleia em exercício sai de em exercício quando já houve a posse dos eleitos numa eleição posterior à de todos os mandatos que a casa registra.
2. A posse vem das regras de deputado estadual e distrital de `contrib/mandatos.yaml`, por UF, com a eleição de cada regra.
3. A situação é "Mandato encerrado em {data da posse}", no mesmo formato do ADR-0071.
4. Mandato sem eleição, lista vazia ou UF sem regra decidem a favor do que a casa diz.
5. A saída do `publish` conta os casos de cada casa.

A comparação é pela eleição, não pela data de início.
Motivo: as fontes dão datas de início diferentes da posse legal (a CLDF registra 2023-01-01). Uma regra futura com a posse de 2023 não pode encerrar o mandato de quem tomou posse nela.

## Consequências

- Na CLDF, os distritais da legislatura encerrada saem de em exercício em 2027-01-06. Na ALRS, em 2027-01-31.
- Nas demais assembleias, a posse é no dia seguinte ao fim registrado, e a regra do ADR-0071 já age no mesmo dia.
- Quando o adaptador cobrir a legislatura nova, o reeleito tem um mandato da eleição de 2026, e a regra deixa de agir para ele.

## Limitações

- A regra só vale para deputado estadual e distrital, as únicas posses com regra em `contrib/mandatos.yaml`.

## Alternativas consideradas

- **Mudar a constante de fim nos adaptadores da CLDF e da ALRS**: descartada. A constante descreve a legislatura como a fonte a publica, e o erro voltaria em outra casa com o mesmo descompasso.
- **Comparar a posse com a data de início do mandato**: descartada pelo motivo acima.
