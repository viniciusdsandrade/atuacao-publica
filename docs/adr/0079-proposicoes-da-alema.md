# ADR-0079: Proposições da ALEMA

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Na ALEMA, os 43 deputados tinham só mandatos e cargos na Mesa (ADR-0042).
O SAPL antigo da Assembleia redireciona para um sistema novo (`alemalegis.al.ma.leg.br`), com uma API pública em JSON.
Um levantamento de 2026-11-21 testou as fontes de proposições da ALEPA, ALMT, ALEMS, ALRN e ALEMA. A ALEMA tem a fonte mais simples: a lista já traz tipo, número, ano, ementa, situação, data de apresentação, autores e o arquivo do texto.

## Decisão

1. A coleta da ALEMA passa a trazer as proposições da 20ª legislatura pela lista `legislative-matter/filter/access`, com o período de apresentação desde 2023-02-01, 200 por página, em ordem de id (`alema-legis`).
2. Entram os tipos legislativos: PEC, PLO, PLC, PDL e PRE. Indicação, requerimento, moção, mensagem e medida provisória ficam de fora, como na ALERJ (ADR-0078).
3. A situação é a da fonte (`situation`). A data é a de apresentação. O link do texto é o download do primeiro arquivo da matéria.
4. A autoria vem do campo `authorNames`, pelo nome, com a ligação comum da ALERJ (`AutoriaPorNome`), agora com uma terceira tentativa: a palavra do meio abreviada ("Andreia M. Rezende"), só quando um único deputado tem o nome.
5. O campo pode ter vários autores separados por vírgula, e o nome de comissão também tem vírgula. A divisão só vale quando alguma parte é um deputado.
6. A coleta não chama os endpoints de parlamentar da API: eles trazem CPF, RG e endereço.
7. As páginas renovam juntas uma vez por semana. A ALEMA recusa conexão de fora do Brasil: o cache vem da semente da coleta local (ADR-0034), que já inclui a casa.

## Consequências

- 2.899 proposições da legislatura, com 2.374 autorias ligadas a 41 deputados.

## Limitações

- Autoria de quem deixou a Assembleia antes do início da coleta não tem perfil e fica de fora, como na ALERJ.
- Nome parcial ("Janaina Lima" para a deputada registrada como "Janaina") não se liga.
- A sessão plenária, a presença e o voto por deputado existem na API, mas só com sessões a partir de 2026-10. Não coletados.

## Alternativas consideradas

- **Autoria pelo endpoint de autoria de cada matéria**: descartada nesta etapa. São cerca de 22 mil chamadas, contra 113 páginas da lista.
