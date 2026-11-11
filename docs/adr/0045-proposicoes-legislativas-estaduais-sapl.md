# ADR-0045: Proposições legislativas nas assembleias estaduais com SAPL (ALPB, ALEAM, ALEAL, ALERO e ALEPI)

## Contexto

As assembleias legislativas dos estados da Paraíba (ALPB, ADR-0039), Amazonas (ALEAM, ADR-0040), Alagoas (ALEAL, ADR-0041), Rondônia (ALERO, ADR-0042) e Piauí (ALEPI, ADR-0043) utilizam o Sistema de Apoio ao Processo Legislativo (SAPL 3.1) desenvolvido pelo Programa Interlegis do Senado Federal.

Até o momento, a integração dessas casas cobria os deputados estaduais em exercício, filiações partidárias, cargos na Mesa Diretora, mandatos e biografias. Os blocos de proposições permaneciam vazios, aguardando a ingestão de dados de matérias legislativas.

Todas essas instalações do SAPL expõem a mesma API REST em formato JSON, com endpoints para identificação de autores (`/api/base/autor/?object_id={pid}`), catálogo de tipos de matérias (`/api/materia/tipomaterialegislativa/`) e busca de matérias legislativas por autor e por ano (`/api/materia/materialegislativa/?autores={autor_id}&ano={ano}`).

## Decisão

1. Implementar um módulo reutilizável `SaplMateria` no pacote `etl.fontes` para unificar a coleta, paginação e normalização de proposições legislativas em casas com SAPL REST API.
2. Na fase de coleta (`fetch`):
   - Baixar o catálogo de tipos de matéria (`tipos-materia.json`) uma vez por semana.
   - Para cada deputado estadual em exercício, obter seu identificador de autor via `/api/base/autor/?object_id={pid}`.
   - Para os anos da legislatura atual (2023 a 2027), paginar e baixar as matérias legislativas apresentadas pelo parlamentar.
3. Na fase de normalização (`normalize`):
   - Mapear os tipos de proposição com base no catálogo oficial do SAPL.
   - Extrair número, ano, identificação, ementa, situação de tramitação, data de apresentação e link oficial do inteiro teor em PDF.
   - Preencher o array `proposicoes` no arquivo de bloco do parlamentar e associar o respectivo registro de proveniência (`Prov`).
4. Cadastrar as fontes `alpb-proposicoes`, `aleam-proposicoes`, `aleal-proposicoes`, `alero-proposicoes` e `alepi-proposicoes` em `contrib/fontes.yaml` e documentá-las em `docs/FONTES.md`.

## Consequências

- Os perfis de deputados estaduais da ALPB, ALEAM, ALEAL, ALERO e ALEPI passam a exibir o histórico real de projetos de lei, requerimentos, indicações e outras proposições legislativas.
- O código de ingestão permanece conciso e sustentável através do reaproveitamento do motor comum `SaplMateria`.
- A coleta respeita as políticas de cache semanais e anuais, sem sobrecarregar os servidores públicos das assembleias.
