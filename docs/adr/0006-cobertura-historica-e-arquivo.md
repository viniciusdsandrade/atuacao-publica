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

1. A cobertura começa na 57ª legislatura (2023-02-01) e nos mandatos executivos iniciados em 2023. Ela só cresce. Revisão (ADR-0009, 2026-11-02): o início passa a 2022-01-01, dentro da 56ª legislatura.
2. A cobertura do TSE começa na eleição de 2018, a dos senadores em exercício em 2023, e não encolhe com o passar dos anos. Os planos de governo cobrem as eleições do Executivo desde 2022.
3. Há perfil para toda pessoa que exerceu mandato coberto, inclusive suplente que assumiu e quem saiu antes do fim.
4. Há perfil para quem foi eleito e ainda não tomou posse:
   - presidente e governador: as datas de posse e de fim vêm de `contrib/mandatos.yaml`, com fonte T0;
   - deputado e senador: a posse é o início da legislatura seguinte, o dia depois do fim da legislatura vigente segundo a API da Câmara (Constituição, art. 57, § 4º).
5. Depois da data de posse, eleito que a casa legislativa ainda não registra em exercício continua no arquivo, com a situação "sem registro de exercício".
6. Cada perfil informa se a pessoa está em exercício (`em_exercicio`) e a situação do papel principal: o mandato em exercício; sem ele, a posse prevista; sem ela, o mandato mais recente. O perfil traz os blocos de todos os cargos que a pessoa ocupou.
7. O arquivo é reconstruído a partir das fontes oficiais a cada execução. Ele não depende da publicação anterior: se o cache sumir, a execução seguinte publica o mesmo arquivo.
8. Identificador estável: o perfil mantém o identificador já publicado para qualquer um dos seus identificadores externos (ADR-0002, item 5). Identificador que deixou de ser canônico entra em `aliases`, e a SPA redireciona. Registro manual feito para um alias vale para o perfil.
9. Trava do publish: se um identificador da publicação anterior não aparece na nova, nem como alias, o publish para antes de gravar. O site continua com a publicação anterior. Remoção deliberada exige listar o identificador em `ETL_PERMITIR_REMOCAO`.
10. Correção manual de mandato (fonte T0) substitui só o mandato automático do mesmo cargo no mesmo período. Os outros mandatos da pessoa ficam.
11. Ensaio de troca: a variável `ETL_AGORA` fixa o instante da execução. Com ela, o publish roda sobre os dados já normalizados como se fosse uma data futura. O fetch recusa a variável.

## Consequências

- A lista do site separa quem está em exercício, quem saiu e quem aguarda posse.
- O volume cresce a cada legislatura. Os dados de quem saiu são de período fechado e vêm do cache.
- A falha de normalização de uma fonte bloqueia o publish, em vez de apagar perfis. A falha do job e o alerta de coleta avisam o mantenedor.
- A troca de mandatos não exige mudança de código: as datas vêm dos dados das casas e de `contrib/mandatos.yaml`.

## Alternativas consideradas

- Copiar a publicação anterior e congelar os perfis de quem saiu: depende do cache do Actions, que pode sumir, e não preserva a atividade anterior de quem continua.
- Guardar cada publicação num branch de dados, com o histórico do git como trilha de auditoria: registra toda mudança feita pelas fontes no dado já publicado. Fica como evolução futura.
