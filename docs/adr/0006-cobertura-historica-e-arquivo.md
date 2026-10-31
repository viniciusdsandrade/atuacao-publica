# ADR-0006: Cobertura histórica: o site como arquivo

- Status: aceita
- Data: 2026-10-31

## Contexto

A POC cobria só quem está no cargo: a legislatura vigente e os mandatos executivos vigentes.
O Presidente e os governadores eleitos em 2026 tomam posse em 5 e 6 de janeiro de 2027.
Deputados e senadores tomam posse em 1º de fevereiro de 2027.
Na troca, quem sai sumiria do site, e os links para o perfil quebrariam.
Quem continua perderia a atividade da legislatura anterior.
O publish apaga e recria todos os perfis, sem trava.
Numa execução de teste com a API da Câmara fora do ar, ele publicou 109 perfis em vez de 622.
Decisão do mantenedor: o site funciona como biblioteca, histórico e auditoria. Nenhum perfil sai.

## Decisão

1. A cobertura começa na 57ª legislatura (2023-02-01) e nos mandatos executivos iniciados em 2023. Ela só cresce.
2. Há perfil para toda pessoa que exerceu mandato coberto, inclusive suplente que assumiu e quem saiu antes do fim.
3. Há perfil para quem foi eleito e ainda não tomou posse. A data de posse vem de `contrib/mandatos.yaml`, com fonte T0.
4. Cada perfil informa se a pessoa está em exercício (`em_exercicio`) e a situação do mandato mais recente.
5. O arquivo é reconstruído a partir das fontes oficiais a cada execução. Ele não depende da publicação anterior: se o cache sumir, a execução seguinte publica o mesmo arquivo.
6. Identificador estável: o perfil mantém o identificador já publicado para qualquer um dos seus identificadores externos (ADR-0002, item 5). Identificador que deixou de ser canônico entra em `aliases`, e a SPA redireciona.
7. Trava do publish: se um identificador da publicação anterior não aparece na nova, nem como alias, o publish para antes de gravar. O site continua com a publicação anterior. Remoção deliberada exige listar o identificador em `ETL_PERMITIR_REMOCAO`.
8. Ensaio de troca: a variável `ETL_AGORA` fixa o instante da execução. Com ela, o normalize e o publish rodam sobre os dados já coletados como se fosse uma data futura.

## Consequências

- A lista do site separa quem está em exercício, quem saiu e quem aguarda posse.
- O volume cresce a cada legislatura. Os dados de quem saiu são de período fechado e vêm do cache.
- A falha de normalização de uma fonte bloqueia o publish, em vez de apagar perfis. A falha do job e o alerta de coleta avisam o mantenedor.
- A troca de mandatos não exige mudança de código: as datas vêm dos dados das casas e de `contrib/mandatos.yaml`.

## Alternativas consideradas

- Copiar a publicação anterior e congelar os perfis de quem saiu: depende do cache do Actions, que pode sumir, e não preserva a atividade anterior de quem continua.
- Guardar cada publicação num branch de dados, com o histórico do git como trilha de auditoria: registra toda mudança feita pelas fontes no dado já publicado. Fica como evolução futura.
