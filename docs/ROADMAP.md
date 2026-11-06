# Roadmap

Estado real de cada fase. Atualizado ao fechar cada fase.

## POC (esfera federal)

| Fase | Entrega | Estado |
|---|---|---|
| 0 | Reconhecimento das fontes, `docs/FONTES.md`, ADR-0001 e ADR-0002 | concluída |
| 1 | Esqueleto do monorepo, documentos, templates, CI | concluída: CI verde no GitHub Actions |
| 2 | JSON Schemas do modelo e de `contrib/`, exemplos, validação | concluída: 10 schemas, 32 exemplos, `normalize contrib` no CI |
| 3 | ETL da Câmara | concluída: 513 deputados; votos, participação, despesas, proposições, discursos, eventos, órgãos e frentes |
| 4 | ETL do Senado | concluída: 81 senadores; votos, participação, CEAPS, autorias, discursos, comissões |
| 5 | Executivo e TSE | concluída: candidaturas de 2018, 2022 e 2026; Presidente e 27 governadores; 3.317 atos normativos |
| 6 | Site | concluída: lista com busca e filtros, perfil com proveniência por bloco, detalhe anual, Metodologia, Fontes e Contribuir |
| 7 | Pipeline agendado, deploy, relatório de execução | concluída: workflow `dados` verificado no GitHub Actions, com as quatro fontes coletadas e 622 perfis publicados; agendamento e deploy dependem de ativação pelo mantenedor |
| 8 | Contribuição de ponta a ponta | concluída: registro real de agenda oficial validado no CI do pull request #1 e integrado após revisão humana |

## Arquivo histórico (ADR-0006)

| Etapa | Estado |
|---|---|
| Trava do publish, identificadores estáveis e aliases | concluída |
| Ensaio de data futura (`ETL_AGORA`) | concluída |
| Presidente e governadores: mandato vigente, encerrado ou com posse prevista | concluída: ensaio de 5 e 6 de janeiro de 2027 sem perfil perdido |
| Eleitos em 2026 que aguardam posse | concluída: 240 perfis novos (209 deputados, 18 senadores, 13 governadores), 862 no total |
| Senado: todos os que exerceram mandato desde 2023, não só quem está no cargo | concluída: 107 senadores, 26 deles fora de exercício (suplentes que exerceram, renúncias, licenças) |
| Câmara: todos os que exerceram mandato desde 2023, não só quem está no cargo | concluída: 648 deputados, 135 deles fora de exercício (renúncia, licença, suplentes que assumiram) |
| Site: situação, filtro de quem está em exercício e redirecionamento de aliases | concluída: filtro por em exercício, eleitos aguardando posse e fora de exercício |
| Cobertura desde 2022-01-01 (ADR-0009): 56ª legislatura pela parte a partir dessa data, mandato presidencial de 2019 | concluída em 2026-11-02 |

## Limitações conhecidas

- `/deputados/{id}/despesas` da Câmara responde vazio. O ETL usa o arquivo anual de cotas.
- `/deputados/{id}/frentes` respondeu 504 seguidamente em 2026-10-30. O ETL manteve a cópia da coleta anterior e marcou a fonte como parcial (ADR-0005).
- A API da Câmara responde 504 e recusa conexões em escala a partir do runner do GitHub Actions (execuções de 2026-11-01 e 2026-11-02), e também respondeu 504 em 2026-10-30 de outra rede. Com o cache preenchido, os blocos usam a cópia anterior e ficam marcados como desatualizados. Desde 2026-11-02 a coleta tem orçamento de tempo, discursos são complementares e a falha em poucos deputados deixa a fonte parcial, não em falha. A execução diária roda às 06:17 UTC.
- DivulgaCandContas bloqueia o User-Agent do projeto. O ETL usa os arquivos do TSE.
- A agenda presidencial existe só em HTML, uma página por dia. Desde 2026-11-02 é coletada pela página do dia (ADR-0011); 2022 vem do Internet Archive, e dias sem captura ficam sem registro.
- O e-Agendas não traz a Presidência da República.
- O Planalto recusou conexão a partir do ambiente de validação. Os atos normativos vêm da API do Senado.
- A listagem de autorias do Senado (`/processo`) não traz temas.
- O Senado não publica presença em evento equivalente à da Câmara.
- O TSE registra a eleição, não o exercício do cargo. Renúncia ou sucessão de governador entra por contribuição com fonte T0.
- Planos de governo: só das eleições de 2022 e 2026.
- O site não embute fotos oficiais, para não gerar requisição do navegador do visitante a terceiros.
- SAPL do piloto: `ordemdia` e `expedientemateria` paginam de forma instável. A coleta vai por sessão. Os votos sem registro de votação ou sem item votado ficam fora e aparecem na saída do `normalize`.
- SAPL do piloto: a fonte não publica orientação de partido nem texto de discurso. Matérias com sequência de 11 dígitos na ementa (número de protocolo) são removidas pela regra de bloqueio de CPF e contam como rejeição em `_meta/execucao.json`.
- TCE-SP: um mês por chamada, com cerca de 2 meses de defasagem. A fonte identifica só o órgão, não o gabinete do prefeito.
- Portal da transparência da câmara do piloto: reprografia e correspondência vêm sem valor e ficam fora.
- O arquivo de candidaturas do TSE de 2024 vem com o CPF mascarado. O vínculo dessa eleição com as demais usa nome civil e data de nascimento, em memória.
- SAPL do piloto: o filtro de votos por registro de votação admite votos anteriores a 2022, porque os ids de registro não seguem a ordem das sessões. Esses votos ficam fora, e a quantidade aparece na saída do `normalize`.
- Até 2026-11-02, o bloco de órgãos e frentes saía vazio para deputados e senadores por um erro no nome do campo normalizado. Corrigido.
- Agenda da Presidência: a primeira carga no CI foi concluída em execuções sucessivas com coleta em lotes de 40 dias. Dias já coletados ficam no cache; dias sem captura no arquivo ficam sem registro ou com cópia anterior preservada em caso de timeout. No site, 5.480 compromissos no presidente vigente (2023 a 2026) e 721 no presidente de 2019 a 2023.
- Portal da Transparência: o ZIP anual de viagens respondeu 405 intermitente ao runner do GitHub Actions em 2026-11-02 (2024 a 2026). Corrigido com repetição automática em caso de 405; viagens de 2024 a 2026 publicadas no site com passagens e diárias.
- Em 2026-11-02 o `publish` do CI parou com "dois perfis com o identificador sapl-3509502-306": um ex-deputado novo no arquivo era o mesmo vereador já publicado, e o vínculo entre casas não os fundiu, porque o TSE mascara o CPF de 2024. Corrigido em dois pontos. O vínculo passa a usar também nome civil e data de nascimento. E o id publicado só passa a um perfil se não for o id derivado de outro, o que vale mesmo quando a fusão falha.
- A lista da página inicial exibe 48 cartões por vez. A busca do navegador (Ctrl+F) só acha o que está na tela. Use a busca do site.
- Não validado: leitor de tela real (VoiceOver, NVDA, TalkBack) e aparelhos móveis físicos. A auditoria de acessibilidade foi automatizada e por teclado.
- A auditoria de interface roda em scripts locais. Ela não está no CI.
- O site só vai ao ar pelo workflow `dados` (agendado ou manual). Mudança só de interface espera a próxima execução.
- A API de dados abertos da ALMG (`dadosabertos.almg.gov.br`) descarta conexões SYN de datacenters estrangeiros (geo-blocking/firewall de rede nos runners do GitHub Actions/Azure nos EUA). O download completo inicial foi executado em ambiente nacional e mantido no cache incremental; o step `fetch almg` usa `continue-on-error: true`.
- A API da ALMG restringe certos recursos como `/proposicoes/pesquisa` (`Access Denied`). Deputados, comissões e verbas indenizatórias são abertos e funcionam via API v2.


## Ativação pendente (decisão do mantenedor)

- Pages e execução diária: passos em `docs/MAINTAINER_CHECKLIST.md`. Nada é publicado até essa ativação.

## Cobertura 2022 a 2026 (ADR-0009)

Alvo fixado em 2026-11-02: nível federal e município piloto, de 2022-01-01 a 2026-10-31. Cobertura após as etapas A a F: o que tem fonte publicada está coletado; resta só a defasagem das próprias fontes (TCE-SP e cartão de pagamento, cerca de 2 meses).

| Etapa | Entrega | Estimativa | Estado |
|---|---|---|---|
| A | 2022 federal: Câmara e Senado desde 2022-01-01 (56ª legislatura), Presidência com mandato de 2019 a 2023 | 4 a 5 h | concluída em 2026-11-02: 896 deputados (33 da 56ª sem exercício desde 2022 ficam fora), 146 senadores, 4.076 atos; 9 pessoas com perfil em duas casas fundidas num perfil só |
| B | Campinas 2022 a 2024: SAPL com 18ª legislatura, transparência, TSE 2020, prefeito anterior, vice-prefeito, TCE | 4 a 5 h | concluída em 2026-11-02: 50 vereadores, 35.647 votos, 20.253 lançamentos de despesa, prefeito com os mandatos de 2021 e 2025 e 56 meses de despesas, vice-prefeito publicado |
| C | Atos normativos municipais no prefeito (SAPL) | 2 h | concluída em 2026-11-02: 3.904 normas de 2022 a 2026, 3.902 no período dos mandatos |
| D | Comissões dos vereadores (SAPL) | 2 h | concluída em 2026-11-02: 1.161 participações em comissão desde 2022 |
| E | Cartão de pagamento e viagens da Presidência (Portal da Transparência, ZIPs sem chave) | 4 a 5 h | concluída em 2026-11-02: 12 meses no mandato de 2019 a 2023 e 45 no vigente; cartão por unidade gestora, diárias, passagens e outros gastos; viagens de 2024 a 2026 validadas e publicadas no site |
| F | Agenda da Presidência: validação do padrão por dia e do arquivo de 2022; adaptador só se passar | 1 h, depois 4 a 6 h | concluída em 2026-11-02: 6.201 compromissos no site (5.480 no presidente vigente de 2023 a 2026 e 721 no de 2019 a 2023); 2022 pelo Internet Archive |

Sem fonte publicada, fora do alvo: agenda do prefeito, presença em evento no Senado, verba de gabinete da câmara municipal.

## Interface do site (ADR-0012)

Concluída em 2026-11-02. A interface foi refeita sem mudar dado, rota nem fonte.

| Etapa | Entrega | Estado |
|---|---|---|
| Casca | Cabeçalho e rodapé novos, alternância manual de tema (Auto, Claro e Escuro) com persistência em storage, anel de foco com contraste, foco movido ao conteúdo na troca de página, título de aba por perfil, rolagem restaurada ao voltar | concluída |
| Página inicial | Apresentação do site com números do índice, busca por palavras, situação e esfera com contagem, filtros no endereço, "Mostrar mais", estados de carga e de erro | concluída |
| Estado das coletas | Página própria (`#/estado`) e resumo na página inicial | concluída |
| Perfil | Sumário por blocos com o bloco em leitura, endereço por bloco, blocos sem registro mais baixos, cabeçalho fixo nas tabelas (sticky header), tabelas em cartões no celular, despesas por ano, link para o JSON do perfil | concluída |
| Detalhe anual | Migalhas, troca de ano, filtro em todos os campos, paginação e cabeçalho fixo nas tabelas | concluída |
| Documentos | Tipografia de leitura, sumário por seção, links externos em outra aba, tabelas e código rolantes por teclado | concluída |
| Impressão | Perfil e documentos em preto sobre branco, com o endereço de cada fonte | concluída |

Verificação de 2026-11-02, com scripts locais (Playwright e axe-core, fora do repositório):

- axe-core sem achados em 96 combinações de página, largura (1366, 390 e 320 px) e tema, no Chromium. Sem achados também nas amostras de Firefox e WebKit.
- Fluxos de lista, perfil, detalhe, documentos e teclado passam no Chromium, no Firefox e no WebKit, também na versão de produção servida em subcaminho.
- Nenhuma requisição a terceiros e nenhum erro de console, com a CSP intacta.

## Fase 2 (depois da POC)

### Esfera estadual e municipal

1. Piloto municipal: prefeitura e câmara de vereadores, com um adaptador por fonte.
   - Levantamento e validação das fontes: concluído em 2026-10-31 (`docs/FONTES.md`, seção "Piloto municipal").
   - A câmara tem dados abertos (SAPL): mandatos, votos nominais, presença, autoria e normas.
   - A prefeitura não tem dados abertos nem agenda oficial publicada. O portal da transparência exige chave.
   - Modelo municipal e adaptador do SAPL: concluído em 2026-10-31 (ADR-0007, schemas com cargos `prefeito`, `vice_prefeito` e `vereador`, identificador `sapl-{ibge}-{id}`, adaptador `Sapl.java` e testes).
   - Coleta, publicação e cruzamento com o TSE de 2024: concluído em 2026-10-31 (38 vereadores com mandatos, votos e candidaturas do TSE vinculadas; prefeito de Campinas publicado via TSE e contribuição de mandato; total de 1.053 perfis publicados).
   - Adaptação da interface web para navegação municipal: concluído em 2026-10-31 (filtro de esfera, busca integrada por município, rótulos de localização, exibição de entes municipais nas tabelas de mandatos e candidaturas, e grid responsivo de filtros).
   - Blocos completos do vereador: concluído em 2026-11-01. Votos datados pelo item votado (a coleta anterior trazia só a primeira página, com a data da migração), participação por ano, presença em sessão, falas como orador, matérias de autoria e despesas do portal da transparência da câmara. Em 2026-11-01: 13.098 votos, 4.526 presenças, 1.350 falas, 37.018 matérias e 8.359 lançamentos de despesa nos 38 perfis.
   - Despesas do Executivo municipal no perfil do prefeito, pelo TCE-SP (ADR-0008): concluído em 2026-11-01. Valor pago pela administração direta, somado por mês, de 2025-01 a 2026-08 (defasagem da fonte).
   - Pipeline agendado: passos `fetch sapl` e `fetch tcesp` no workflow `dados`, com cursor próprio em `_meta/`. Metodologia, fontes e README cobrem o nível municipal.
   - Pendente: a primeira carga pública depende da ativação pelo mantenedor. Atos e agenda do prefeito seguem por contribuição com fonte T0.
2. Assembleias legislativas e governos estaduais: um adaptador por estado.
   - ALESP (São Paulo): primeira carga e integração concluídas em 2026-11-03 (ADR-0013).
   - Levantamento e validação das fontes concluídos em `docs/FONTES.md`: API REST de processo legislativo (`https://legis-api-portal-prd.al.sp.gov.br`) e repositório de dados abertos em XML (`https://www.al.sp.gov.br/repositorioDados/`).
   - Modelo: adição do cargo `deputado_estadual`, identificador `alesp-{matricula}` e chave `alesp` em `ids_externos`.
   - Entregas concluídas:
     a. Schemas e modelo de dados (`deputado_estadual` e `alesp`).
     b. Adaptador `Alesp.java`: coleta com cache e normalização em streaming (StAX) de deputados, mandatos, despesas e comissões. 153 deputados estaduais da 19ª e 20ª legislaturas normalizados.
     c. Proteção de dados: descarte estrito de CPFs de fornecedores nas despesas de gabinete e normalização de URLs de fotos com espaços.
     d. Vínculo com candidaturas estaduais do TSE: 10 perfis de outras casas fundidos na mesma pessoa.
     e. Suporte na interface web para filtragem e visualização de deputados estaduais.
     f. Publicação: 1.479 perfis publicados no total com deploy no GitHub Pages (153 deputados da ALESP sem rejeição).
     g. Proposituras e autoria: ingestão em streaming (StAX em ZIP) de `proposituras.zip` e `documento_autor.zip`, com vínculo por `idAutor` e identificação por natureza (PL, PLC, PEC, etc.).
     h. Votações nominais e datação: ingestão de `comissoes_permanentes_reunioes.xml` associada a `comissoes_permanentes_votacoes.xml`, permitindo a datação e o cômputo da participação anual dos deputados estaduais nas comissões permanentes.
3. Gastos, atos normativos e agenda de governadores a partir dos portais estaduais catalogados em `docs/FONTES.md`:
   - Governo do Estado de São Paulo: concluído em 2026-11-03 (ADR-0014).
   - Fontes catalogadas e validadas: Plataforma SP-Agendas (`https://spagendas.sp.gov.br`, SGGD), Portal da Transparência de SP (`https://www.transparencia.sp.gov.br`) e repositório oficial de normas do Estado (`sp-legislacao`).
   - Modelo: expansão dos blocos do cargo `governador` em `Publicador.java` para paridade com `presidente` e `prefeito` (`despesas_executivo`, `agenda_oficial`, `atos_normativos`).
   - Adaptador `ExecutivoSp.java` implementado: ingestão de compromissos da SP-Agendas (formato OpenXML/XLSX via StAX em streaming com deduplicação), despesas orçamentárias estaduais e atos normativos (decretos e leis estaduais promulgadas pelo Executivo) integrados ao perfil do Governador de SP.
   - Pipeline: passo `fetch executivosp` adicionado ao workflow `dados.yml`.
   - Publicação: perfil do Governador de São Paulo publicado com sucesso no site (deploy GitHub Pages) exibindo agenda oficial, despesas e atos normativos.
4. ALMG e Governo de Minas Gerais (segundo estado da federação, ADR-0015): concluído em 2026-11-03.
   - Fontes catalogadas e validadas: API REST v2 de dados abertos da ALMG (`https://dadosabertos.almg.gov.br/api/v2/`), portal oficial e fotos dos parlamentares (`almg-deputados`, `almg-deputados-detalhes`, `almg-despesas`, `almg-portal`).
   - Modelo e identificador: identificador canônico `almg-{id}` e chave `almg` em `ids_externos` de `schemas/politico.schema.json`.
   - Adaptador `Almg.java` implementado e coberto por testes unitários (`AlmgTest.java`): pesquisa de deputados da 19ª e 20ª legislaturas, detalhes biográficos, participações em comissões e verbas indenizatórias mensais de 2022 a 2026.
   - Vínculo com o TSE: vinculação determinística por nome civil completo (`nomeServidor`) e data de nascimento em memória, integrando candidaturas de 2018, 2022 e 2026. 10 perfis de outras casas fundidos na mesma pessoa.
   - Proteção estrita de privacidade: retenção exclusiva de CNPJs de fornecedores (14 dígitos), descarte de CPFs de prestadores pessoas físicas, remoção de CPF embutido na razão social de MEIs com `Guarda.semCpf` e blindagem de dados de gabinetes.
   - Executivo estadual de MG: Governador Romeu Zema integrado via TSE e catálogo oficial de portais estaduais (`mg-transparencia` e `mg-diario-oficial`), com mandatos de 2019-2023 (eleição 2018) e 2023-2027 (eleição 2022) no arquivo histórico.
   - Pipeline e workflow: registro de `almg` no ponto de entrada CLI (`Main.java`), publicador, vínculo e passo `fetch almg` em `.github/workflows/dados.yml`.
   - Publicação: 110 deputados estaduais da ALMG normalizados e publicados sem nenhuma rejeição de schema; total de 214 registros de Minas Gerais integrados (110 estaduais, 94 federais, 9 senadores e o Governador).
5. Gastos do Executivo federal (cartão de pagamento e viagens) pelo Portal da Transparência (concluído no ADR-0010 e ADR-0009).
6. ALEP e Governo do Paraná (terceiro estado da federação, ADR-0016): concluído em 2026-11-03.
   - Fontes catalogadas e validadas em `docs/FONTES.md`: portal oficial e CDN da ALEP (`https://www.assembleia.pr.leg.br` e `https://storage2.assembleia.pr.leg.br`), endpoints de deputados em exercício (`/deputados/conheca`), perfis individuais (`/deputados/perfil/{slug}`), comissões permanentes e temporárias (`/atividade-parlamentar/comissoes-permanentes-e-temporarias`) e portais estaduais de transparência (`pr-transparencia`) e diário oficial (`pr-dioe`).
   - Modelo e identificador: identificador canônico `alep-{slug}` e chave `alep` em `ids_externos` de `schemas/politico.schema.json`. Atualização de `politico_id` em `schemas/comum.schema.json` para suporte a slugs nominais compostos de até seis partes hifenizadas.
   - Leitura transparente de compressão: utilitário `Texto.lerArquivo` com suporte a arquivos compactados em `.gz` pelo mecanismo de cache HTTP.
   - Adaptador `Alep.java` implementado e coberto por testes unitários (`AlepTest.java`): ingestão e parsing dos cards oficiais de deputados, fotos em alta resolução, perfis individuais e accordions de comissões.
   - Comissões parlamentares: 457 vínculos de comissões permanentes e temporárias extraídos com papéis parlamentares (Presidente, Vice-Presidente, Membro, Suplente, Secretários).
   - Vínculo com o TSE: atualização de `Tse.java` para indexar candidaturas de deputados estaduais das UFs cobertas (SP, MG, PR); 51 dos 54 deputados da ALEP vinculados deterministicamente às suas candidaturas das eleições de 2018, 2022 e 2026.
   - Fusão entre casas legislativas: 13 perfis fundidos no catálogo nacional, unificando parlamentares com passagens pela Câmara Federal e ALEP (ex.: Ney Leprevost `cam-204384`, com alias automático `alep-ney-leprevost`).
   - Executivo estadual do Paraná: Governador Ratinho Junior coberto pelo arquivo histórico via candidaturas do TSE (2018 e 2022) e portais estaduais catalogados.
   - Pipeline e workflow: registro de `alep` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alep` no workflow `.github/workflows/dados.yml`.
   - Publicação: 54 deputados estaduais da ALEP normalizados e publicados sem nenhuma rejeição de schema; total de 122 registros do Paraná (54 estaduais, 42 federais, 4 senadores e o Governador) integrados ao catálogo nacional de 1.486 perfis.
7. ALBA e Governo da Bahia (quarto estado da federação, ADR-0017): concluído em 2026-11-04.
   - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALBA (`https://www.al.ba.gov.br`), endpoint de deputados em exercício (`/deputados/deputados-estaduais`), perfis biográficos individuais (`/deputados/deputado-estadual/{id}`), comissões permanentes e temporárias (`/comissoes/composicao` e `/comissoes/composicao/{id}`), fotos oficiais no repositório (`/fserver/:imagensAlbanet:...`) e portais estaduais de transparência (`ba-transparencia`) e diário oficial (`ba-diario-oficial`).
   - Modelo e identificador: identificador canônico `alba-{id}` (numérico oficial) e chave `alba` em `ids_externos` de `schemas/politico.schema.json`.
   - Adaptador `Alba.java` implementado e coberto por testes unitários (`AlbaTest.java`): ingestão e parsing dos cards oficiais de deputados, fotos oficiais, perfis biográficos com extração de nome civil e data de nascimento, e composições de comissões com presidente, vice-presidente, titulares e suplentes.
   - Vínculo com o TSE: inclusão de `BA` em `UFS_ESTADUAIS` de `Tse.java` (+2.199 candidaturas estaduais indexadas); 63 dos 63 deputados estaduais da ALBA (100% de cobertura) vinculados deterministicamente às suas candidaturas das eleições de 2018, 2022 e 2026.
   - Executivo estadual da Bahia: Governador Jerônimo Rodrigues coberto pelo arquivo histórico via candidaturas do TSE (2022) e portais estaduais catalogados.
   - Pipeline e workflow: registro de `alba` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alba` no workflow `.github/workflows/dados.yml`.
   - Publicação: 63 deputados estaduais da ALBA normalizados e publicados sem nenhuma rejeição de schema; total de 132 registros da Bahia (63 estaduais, 65 federais, 3 senadores e o Governador) integrados ao catálogo nacional de 1.547 perfis.
8. ALERJ e Governo do Rio de Janeiro (quinto estado da federação, ADR-0018): concluído em 2026-11-05.
   - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALERJ (`https://www.alerj.rj.gov.br`), endpoint de deputados em exercício (`/Deputados/QuemSao`), perfis individuais e atuação (`/Deputados/PerfilDeputado/{id}?Legislatura=20`), fotos oficiais no repositório (`/Uploads/PerfilDeputado/Imagem/...`) e portais estaduais de transparência (`rj-transparencia`) e diário oficial (`rj-diario-oficial`).
   - Modelo e identificador: identificador canônico `alerj-{id}` (numérico oficial) e chave `alerj` em `ids_externos` de `schemas/politico.schema.json`.
   - Adaptador `Alerj.java` implementado e coberto por testes unitários (`AlerjTest.java`): ingestão e parsing dos cards oficiais de deputados, fotos oficiais, extração de posse formal e biografias oficiais.
   - Vínculo com o TSE: inclusão de `RJ` em `UFS_ESTADUAIS` de `Tse.java` (+2.785 candidaturas estaduais indexadas); 70 dos 70 deputados estaduais da ALERJ (100% de cobertura) vinculados deterministicamente às suas candidaturas das eleições de 2018, 2022 e 2026.
   - Executivo estadual do Rio de Janeiro: Governador Cláudio Castro coberto pelo arquivo histórico via candidaturas do TSE (2018 e 2022) e portais estaduais catalogados.
   - Pipeline e workflow: registro de `alerj` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alerj` no workflow `.github/workflows/dados.yml`.
   - Publicação: 70 deputados estaduais da ALERJ normalizados e publicados sem nenhuma rejeição de schema.
9. ALRS e Governo do Rio Grande do Sul (sexto estado da federação, ADR-0019): concluído em 2026-11-05.
   - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALRS (`https://www.al.rs.gov.br` e `https://ww4.al.rs.gov.br`), API em JSON de deputados em exercício (`https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados`), páginas parlamentares individuais e comissões (`/deputados/{id}` e `/deputados/{id}/comissoes`), fotos oficiais no repositório (`https://ww2.al.rs.gov.br/filerepository/fotografias/...`) e portais estaduais de transparência (`rs-transparencia`) e diário oficial (`rs-diario-oficial`).
   - Modelo e identificador: identificador canônico `alrs-{id}` (numérico oficial) e chave `alrs` em `ids_externos` de `schemas/politico.schema.json`.
   - Adaptador `Alrs.java` implementado e coberto por testes unitários (`AlrsTest.java`): ingestão e parsing dos 55 deputados estaduais em exercício na 56ª Legislatura (2023–2027), fotos oficiais em alta resolução, higienização de títulos e minimização de dados de contato.
   - Vínculo com o TSE: inclusão de `RS` em `UFS_ESTADUAIS` de `Tse.java` (+3.200 candidaturas estaduais indexadas); 50 deputados estaduais com ID canônico `alrs-{id}` e 5 deputados com alias automático `alrs-xxx -> tse-yyy` mantendo estabilidade histórica.
   - Executivo estadual do Rio Grande do Sul: Governador Eduardo Leite coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
   - Limitação documentada: a API em JSON da ALRS na porta `:5000` requer conectividade direta que sofre restrição de saída (connect timeout) em runners de cloud internacional (Azure `eastus`); a coleta local e testes operam normalmente.
10. ALECE e Governo do Estado do Ceará (sétimo estado da federação, ADR-0020): concluído em 2026-11-05.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALECE (`https://www.al.ce.gov.br`), endpoint de deputados em exercício (`/deputados`), perfis biográficos individuais (`/deputados/{slug}`), comissões permanentes (`/comissoes-permanentes/{slug}/composicao`), fotos oficiais no repositório (`/image/...`) e portais estaduais de transparência (`ce-transparencia`) e diário oficial (`ce-diario-oficial`).
    - Modelo e identificador: identificador canônico `alece-{slug}` (slug textual oficial) e chave `alece` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alece.java` implementado e coberto por testes unitários (`AleceTest.java`): ingestão e parsing dos 54 deputados estaduais em exercício na 31ª Legislatura (2023–2027), fotos oficiais, extração de biografias, 20 comissões permanentes populando `orgaos_e_frentes` e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `CE` em `UFS_ESTADUAIS` de `Tse.java` (+1.500 candidaturas estaduais indexadas); 52 deputados com identificador canônico `alece-{slug}` e 2 deputados fundidos com mandatos federais/senado mantendo redirecionamento de alias.
    - Executivo estadual do Ceará: Governador Elmano de Freitas coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alece` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alece` no workflow `.github/workflows/dados.yml`.
11. ALESC e Governo do Estado de Santa Catarina (oitavo estado da federação, ADR-0021): concluído em 2026-11-05.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALESC (`https://www.alesc.sc.gov.br`), endpoint de deputados em exercício (`/deputados/`), perfis biográficos individuais (`/deputado/{slug}/`), comissões permanentes e temporárias (`https://portalelegis.alesc.sc.gov.br/comissoes-permanentes`), fotos oficiais em alta resolução e portais estaduais de transparência (`sc-transparencia`) e diário oficial (`sc-diario-oficial`).
    - Modelo e identificador: identificador canônico `alesc-{slug}` (slug textual oficial) e chave `alesc` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alesc.java` implementado e coberto por testes unitários (`AlescTest.java`): ingestão e parsing dos 40 deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais em resolução original, extração de biografias, comissões permanentes e minimização estrita de dados de contato.
    - Vínculo com o TSE: inclusão de `SC` em `UFS_ESTADUAIS` de `Tse.java` (+1.100 candidaturas estaduais indexadas); 100% dos deputados estaduais da ALESC vinculados deterministicamente às suas candidaturas das eleições de 2018, 2022 e 2026 (incluindo normalização de títulos religiosos como "Padre Pedro Baldissera").
    - Executivo estadual de Santa Catarina: Governador Jorginho Mello coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Cobertura regional: consolidação de 100% de cobertura dos estados da Região Sul do Brasil (PR, RS, SC).
    - Pipeline e workflow: registro de `alesc` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alesc` no workflow `.github/workflows/dados.yml`.
12. ALEGO e Governo do Estado de Goiás (nono estado da federação, ADR-0022): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALEGO (`https://portal.al.go.leg.br`), endpoint de deputados em exercício (`/deputados/em-exercicio`), perfis individuais (`/deputados/perfil/{id}`), perfis biográficos (`/deputados/perfil-biografico/{id}`), comissões permanentes e frentes parlamentares (`/comissoes/{id}`), fotos oficiais em alta resolução (`https://saba.al.go.leg.br/`) e portais estaduais de transparência (`go-transparencia`) e diário oficial (`go-diario-oficial`).
    - Modelo e identificador: identificador canônico `alego-{id}` (numérico oficial) e chave `alego` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alego.java` implementado e coberto por testes unitários (`AlegoTest.java`): ingestão e parsing dos deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais em resolução original, extração de nome civil e datas de nascimento, órgãos e frentes parlamentares populando `orgaos_e_frentes` e minimização estrita de dados de contato e informações familiares.
    - Vínculo com o TSE: inclusão de `GO` em `UFS_ESTADUAIS` de `Tse.java` (+1.800 candidaturas estaduais indexadas); vínculo determinístico dos deputados estaduais da ALEGO às suas candidaturas das eleições de 2018, 2022 e 2026.
    - Executivo estadual de Goiás: Governador Ronaldo Caiado coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Cobertura regional: estreia oficial do projeto na Região Centro-Oeste do Brasil.
    - Pipeline e workflow: registro de `alego` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alego` no workflow `.github/workflows/dados.yml`.
13. ALES e Governo do Estado do Espírito Santo (décimo estado da federação, ADR-0023): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALES (`https://www.al.es.gov.br`), endpoint de deputados em exercício (`/Deputado/Lista`), perfis individuais (`/Deputado/{slug}`), comissões permanentes (`/Comissao/{slug}`), fotos oficiais em alta resolução (`/appdata/anexos_internet/portal/deputados/`) e portais estaduais de transparência (`es-transparencia`) e diário oficial (`es-dioe`).
    - Modelo e identificador: identificador canônico `ales-{id}` (numérico oficial) e chave `ales` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Ales.java` implementado e coberto por testes unitários (`AlesTest.java`): ingestão e parsing dos 30 deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais, extração de nome civil e datas de nascimento, 18 comissões permanentes populando `orgaos_e_frentes` e minimização estrita de dados de contato.
    - Vínculo com o TSE: inclusão de `ES` em `UFS_ESTADUAIS` de `Tse.java` (+1.000 candidaturas estaduais indexadas); 100% dos deputados estaduais da ALES vinculados deterministicamente às suas candidaturas das eleições de 2018, 2022 e 2026.
    - Executivo estadual do Espírito Santo: Governador Renato Casagrande coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Cobertura regional: consolidação de 100% de cobertura dos quatro estados da Região Sudeste do Brasil (SP, MG, RJ, ES), totalizando duas macrorregiões inteiras (Sul e Sudeste).
    - Pipeline e workflow: registro de `ales` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch ales` no workflow `.github/workflows/dados.yml`.
14. Décimo primeiro estado da federação: avaliação técnica e planejamento para Pernambuco (ALEPE — Região Nordeste) ou Pará (ALEPA — Região Norte).

### Redes sociais

- Perfil verificado do político como fonte T1, só com snapshot arquivado.
- Rótulo obrigatório: "declaração do próprio político".

### Processos judiciais, sanções e condenações

Só entram depois de um ADR que defina:

- critérios de fonte oficial;
- status processual explícito em cada registro;
- regra de presunção de inocência na exibição.

### Fora do escopo até novo ADR

Contas de usuário, comentários, denúncias e análises automáticas.
