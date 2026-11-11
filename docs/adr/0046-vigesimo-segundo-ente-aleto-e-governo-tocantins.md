# ADR-0046: Vigésimo segundo ente federativo: ALETO e Governo do Tocantins

## Contexto

O projeto já integra 21 entes federativos: o nível federal, 19 estados (SP, MG, PR, BA, RJ, RS, CE, SC, GO, ES, PA, PE, DF, MT, MS, PB, AM, AL, RO, PI e RN) e o município piloto.

O estado do Tocantins possui 24 deputados estaduais na Assembleia Legislativa do Estado do Tocantins (ALETO). A ALETO opera a API REST aberta do Sistema de Apoio ao Processo Legislativo (SAPL 3.1) em `https://sapl.al.to.leg.br/api/`. Essa API expõe os parlamentares em exercício na 10ª Legislatura (2023 a 2027), identificador da legislatura 11, dados de mandatos, composição da Mesa Diretora, fotografias institucionais e proposições legislativas.

O Poder Executivo do Tocantins é chefiado pelo Governador Wanderlei Barbosa, eleito em 2022, cujos dados eleitorais oficiais estão disponíveis nos repositórios públicos do Tribunal Superior Eleitoral (TSE).

## Decisão

1. Integrar o estado do Tocantins (sigla `TO`) como o vigésimo segundo ente federativo.
2. Implementar a classe `Aleto` no pacote `etl.fontes` consumindo a API REST do SAPL 3.1 da ALETO.
3. Coletar os deputados da 10ª Legislatura (2023 a 2027), cargos na Mesa Diretora, biografias com proteção de dados pessoais (`Guarda.semCpf`) e proposições legislativas via módulo compartilhado `SaplMateria`.
4. Habilitar a UF "TO" em `Tse.java` para ingestão das candidaturas oficiais de 2022 a Governador e Deputado Estadual.
5. Efetuar o vínculo determinístico dos deputados da ALETO e do Governador em `Vinculo.java`.
6. Cadastrar as fontes oficiais em `contrib/fontes.yaml` e documentá-las em `docs/FONTES.md`.

## Consequências

- O projeto alcança 22 entes federativos integrados com transparência pública.
- A cobertura regional na Região Norte se expande com dados estruturados da ALETO e do Executivo tocantinense.
- O site passa a exibir os deputados estaduais do Tocantins, sua atuação legislativa e histórico eleitoral.
