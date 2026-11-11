# ADR-0048: Vigésimo quarto ente federativo: ALEMA e Governo do Maranhão

## Contexto

O projeto integra 23 entes federativos: o nível federal, 21 estados (SP, MG, PR, BA, RJ, RS, CE, SC, GO, ES, PA, PE, DF, MT, MS, PB, AM, AL, RO, PI, RN, TO e SE) e o município piloto de São Paulo.

O estado do Maranhão possui 42 vagas na Assembleia Legislativa do Estado do Maranhão (ALEMA). O portal institucional oficial da ALEMA (`https://www.al.ma.leg.br/sitealema/deputados/`) publica a relação atualizada dos deputados estaduais em exercício na 20ª Legislatura (2023 a 2027), acompanhados de fotografias oficiais em alta resolução, filiações partidárias e páginas institucionais com biografia e atuação parlamentar.

O Poder Executivo estadual é chefiado pelo Governador Carlos Brandão (PSB), reeleito em 2022, cujos dados eleitorais e prestação de contas constam na base aberta do Tribunal Superior Eleitoral (TSE).

Com a integração do Maranhão, a Região Nordeste atinge a marca histórica de **100% de cobertura federativa** (todos os 9 estados da região integrados: MA, PI, CE, RN, PB, PE, AL, SE e BA), consolidando 24 entes federativos em todo o território nacional.

## Decisão

1. Integrar o estado do Maranhão (sigla `MA`) como o vigésimo quarto ente federativo do projeto.
2. Criar a classe `Alema` no pacote `etl.fontes` para realizar a coleta e normalização dos deputados estaduais em exercício na 20ª Legislatura, biografias com proteção de dados pessoais (`Guarda.semCpf`), fotografias institucionais e composição da Mesa Diretora presidida pela deputada Iracema Vale.
3. Habilitar a UF "MA" em `Tse.java` para processamento das candidaturas oficiais de 2022 a Governador e Deputado Estadual.
4. Efetuar o vínculo determinístico em `Vinculo.java` entre os parlamentares da ALEMA, o Governador e as candidaturas registradas no TSE.
5. Catalogar as novas fontes oficiais em `contrib/fontes.yaml` e documentá-las em `docs/FONTES.md`.

## Consequências

- O projeto alcança 24 entes federativos integrados (União, 22 estados, DF e 2 municípios).
- A Região Nordeste se torna a 4ª macrorregião com 100% de cobertura (junto a Sul, Sudeste e Centro-Oeste).
- Cidadãos maranhenses passam a dispor de transparência pública completa sobre seus representantes legislativos e executivos.
