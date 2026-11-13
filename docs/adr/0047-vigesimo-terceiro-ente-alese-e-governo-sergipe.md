# ADR-0047: Vigésimo terceiro ente federativo: ALESE e Governo de Sergipe

## Contexto

O projeto integra 22 entes federativos: o nível federal, 20 estados (SP, MG, PR, BA, RJ, RS, CE, SC, GO, ES, PA, PE, DF, MT, MS, PB, AM, AL, RO, PI, RN e TO) e o município piloto de São Paulo.

O estado de Sergipe possui 24 deputados estaduais na Assembleia Legislativa do Estado de Sergipe (ALESE). A ALESE disponibiliza em seu portal institucional (`https://al.se.leg.br/deputados/`) a relação completa dos 24 deputados estaduais em exercício na 20ª Legislatura (2023 a 2027), acompanhados de fotografias oficiais em alta resolução, filiações partidárias e vínculos para suas páginas individuais no Sistema de Processo Legislativo da ALESE (`https://aleselegis.al.se.leg.br/spl/parlamentar.aspx?id={id}`). O portal também publica a composição da Mesa Diretora da legislatura (`https://al.se.leg.br/mesa-diretora/`).

O Poder Executivo de Sergipe é chefiado pelo Governador Fábio Mitidieri, eleito em 2022, cujos dados eleitorais e prestação de contas constam na base aberta do Tribunal Superior Eleitoral (TSE).

Com a inclusão de Sergipe, a cobertura da Região Nordeste sobe para 8 de seus 9 estados (88,9% da região), restando apenas o Maranhão para completar 100% do Nordeste.

## Decisão

1. Integrar o estado de Sergipe (sigla `SE`) como o vigésimo terceiro ente federativo do projeto.
2. Criar a classe `Alese` no pacote `etl.fontes` para realizar a coleta e normalização dos 24 deputados estaduais da 20ª Legislatura, biografias oficiais sanitizadas com `Guarda.semCpf`, fotografias institucionais e cargos na Mesa Diretora.
3. Habilitar a UF "SE" em `Tse.java` para processamento das candidaturas oficiais de 2022 a Governador e Deputado Estadual.
4. Efetuar o matching determinístico em `Vinculo.java` entre os parlamentares da ALESE, o Governador e as candidaturas registradas no TSE.
5. Catalogar as novas fontes oficiais em `contrib/fontes.yaml` e documentá-las em `docs/FONTES.md`.

## Consequências

- O projeto alcança 23 entes federativos integrados (União, 21 estados, DF e 2 municípios).
- A Região Nordeste atinge 88,9% de cobertura territorial e representativa.
- Cidadãos de Sergipe passam a contar com visão consolidada da atuação de seus 24 deputados estaduais e do Governador.

## Atualização em 2026-11-13: proposições e comissões

- A ficha do parlamentar no sistema legislativo lista os cargos em comissões e conselhos, com datas, e os pares de tipo e autor da produção legislativa, com a contagem de cada um.
- O adaptador passa a ler os cargos da legislatura em curso para o bloco de órgãos e frentes. Conselho entra como órgão.
- As proposições vêm da consulta de produção (`consulta-producao.aspx?tipo={tipo}&autor={autor}`), catalogada como `alese-proposicoes`. Entram as apresentadas desde 2022.
- A consulta é um formulário ASP.NET. Listagem com mais de 10 itens é pedida com 100 por página. A página seguinte sai de um `__doPostBack` com o `__VIEWSTATE` da anterior. A coleta para na primeira página cujo último item é anterior a 2022.
- Ementa passa por `Guarda.sanitizar`, que tira CPF, telefone e e-mail.
- Ensaio local de 2026-11-13: 178 listagens, 27.800 proposições nos 24 deputados. As listagens vêm em ordem decrescente de data, sem inversão nos 29.134 itens lidos. Nenhuma parou antes de chegar a 2022.
- O sistema legislativo não aceita conexão do runner do CI. A casa vem da coleta local cifrada (ADR-0034).
- Sem a ficha no cache, o deputado segue no perfil pela lista do portal, sem biografia, comissões e proposições.
