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
| 7 | Pipeline agendado, deploy, relatório de execução | concluída: workflow `dados` verificado no GitHub Actions, com as quatro fontes coletadas e 622 perfis publicados; agendamento diário e deploy no GitHub Pages ativos desde 2026-11-01 |
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
- Oito governadores eleitos em 2022 têm candidatura a outro cargo em 2026 no próprio TSE: senador no AC, AM, ES, MT, PA e PB e presidente em GO e MG. Para concorrer a outro cargo, o governador renuncia até seis meses antes do pleito (CF, art. 14, § 6º). Em 2026-11-06, o site ainda mostra os oito em exercício até 2027-01-06, e quem assumiu esses governos não tem perfil. Correção pendente: registrar cada renúncia com fonte T0 (diário oficial do estado) ou definir a regra num ADR.
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
- A auditoria de acessibilidade (axe, WCAG 2.1 AA) roda no CI sobre dados sintéticos, só no Chromium e no estado inicial das páginas (ADR-0028). Dado real longo, outros navegadores e estados interativos seguem sem varredura automática.
- O site só vai ao ar pelo workflow `dados` (agendado ou manual). Mudança só de interface espera a próxima execução.
- ALMG, ALRS e ALES não aceitam conexão do runner do GitHub Actions: a conexão TCP expira. Nenhuma das três teve coleta válida no CI, e o site não tem perfil dessas casas (issues #5, #6 e #7). A coleta local funciona.
- Teste de 2026-11-06 com provas do Globalping, nos endereços que o ETL usa:
  - ALRS (API na porta 5000 e portal) e ALES respondem a todas as redes testadas no Brasil: residenciais e datacenters da AWS, do Google Cloud, da Oracle e da Vultr em São Paulo. Não respondem à Azure (eastus) nem à AWS (us-east-1).
  - A ALMG não responde à Azure nem à AWS nos EUA. Em São Paulo, recusa AWS e Oracle e responde a Google Cloud e Vultr. Redes residenciais funcionam.
  - Um runner ou um proxy no Brasil resolve as três, desde que fora da AWS e da Oracle. A escolha é do mantenedor.
- A API da ALMG restringe certos recursos como `/proposicoes/pesquisa` (`Access Denied`). Deputados, comissões e verbas indenizatórias são abertos e funcionam via API v2.
- ALESC: as despesas dos gabinetes (`alesc-despesas`) cobrem 2023 a 2026 e entram só para os 40 deputados em exercício. Em 2026-11-06, 4.355 de 54.524 linhas não correspondiam a nenhum deles e foram descartadas; a contagem aparece na saída do `normalize`. A fonte não traz CNPJ nem número de documento, e o favorecido vem vazio em cerca de 21% das linhas.
- ALESC: o favorecido MEI vem como nome e CPF. A sequência de 11 dígitos é removida na normalização (`Guarda.semCpf`), como na Câmara, no Senado, na ALESP e na ALMG.
- ALEP: a consulta das verbas de ressarcimento por deputado (`api/ressarcimento`) exige reCAPTCHA v3, conforme o script da própria página (2026-11-06). O ETL não contorna CAPTCHA, então essas despesas não são coletadas (`alep-ressarcimento`, bloqueado).
- ALERJ: não foi localizado conjunto de dados abertos de despesas ou verba de gabinete por deputado até 2026-11-06. Não validado.
- CMSP (capital): só vereadores em exercício entram. Os 9 suplentes sem exercício e quem deixou a vereança na legislatura ficam fora. O serviço não tem voto nominal por vereador, e os perfis ficam sem votações e sem foto. As proposições cobrem, desde 2022, só o que está em tramitação, virou lei ou foi vetado: o serviço não informa o autor do projeto encerrado sem virar lei, nem separa autor de coautor.
- CMSP: 4 dos 55 vereadores ficaram sem vínculo com o TSE porque o nome parlamentar difere do nome de urna (por exemplo, uma palavra só). O vínculo ignora só a pontuação ("Jr.", "Dra."), porque casar por palavras soltas ligaria homônimos parciais e atribuiria candidaturas e bens a outra pessoa.
- CMSP: a fonte deixa cargo antigo da Mesa sem data de fim quando há sucessor. Em cargo exclusivo (Mesa, presidência e vice-presidência de comissão), vale o de início mais recente entre os vereadores em exercício.
- Capital: a eleição de 2020 não entra, porque o eleito prefeito deixou o cargo em 2021 e a regra de mandato do projeto supõe os quatro anos do eleito. A despesa do Executivo também não: a API do TCE-SP devolve lista vazia para a capital, e o Diário Oficial da Cidade respondeu 403 à requisição direta.
- Até 2026-11-06, nove adaptadores (ALECE, ALEGO, ALEPE, ALERJ, ALEPA, ALES, ALRS, ALESC e CLDF) gravavam o bloco `fontes` como lista, e o `Vinculo` lê um objeto por bloco. Os perfis dessas casas saíam com os blocos sem fonte e com `atualizado_em` nulo (verificado no perfil `alerj-505` publicado). Corrigido: as fontes de cada bloco vêm da proveniência dos registros (`Registros.fontesDosBlocos`), e o `Vinculo` interrompe o `publish` se receber uma lista. No ensaio local das treze casas, nenhum bloco com registro ficou sem fonte.
- Até 2026-11-06, o cargo `deputado_distrital` não estava na tabela de blocos do `Publicador`, e os perfis da CLDF saíam sem bloco nenhum, nem mandato. Corrigido, com um teste que confere os blocos de todo cargo do schema.


## Publicação

- Pages e execução diária ativos desde 2026-11-01 (`ETL_AGENDADO` e `PUBLICAR_PAGES` em `true`). Passos em `docs/MAINTAINER_CHECKLIST.md`.

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
   - Publicado no site. A agenda do prefeito segue por contribuição com fonte T0, porque a prefeitura não publica agenda.
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
   - Vínculo com o TSE: vinculação determinística por nome civil completo (`nomeServidor`) e data de nascimento em memória, integrando candidaturas de 2018, 2022 e 2026. 10 perfis de outras casas fundidos na mesma pessoa. No ensaio local de 2026-11-06, 109 dos 110 deputados ficaram vinculados.
   - Proteção estrita de privacidade: retenção exclusiva de CNPJs de fornecedores (14 dígitos), descarte de CPFs de prestadores pessoas físicas, remoção de CPF embutido na razão social de MEIs com `Guarda.semCpf` e blindagem de dados de gabinetes.
   - Executivo estadual de MG: Governador Romeu Zema integrado via TSE e catálogo oficial de portais estaduais (`mg-transparencia` e `mg-diario-oficial`), com mandatos de 2019-2023 (eleição 2018) e 2023-2027 (eleição 2022) no arquivo histórico.
   - Pipeline e workflow: registro de `almg` no ponto de entrada CLI (`Main.java`), publicador, vínculo e passo `fetch almg` em `.github/workflows/dados.yml`.
   - Normalização local: 110 deputados estaduais, sem rejeição de schema.
   - No site: nenhum perfil da ALMG, porque a coleta falha no CI (ver "Limitações conhecidas").
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
   - Vínculo com o TSE: inclusão de `BA` em `UFS_ESTADUAIS` de `Tse.java` (2.199 candidaturas a deputado estadual em 2018, 2022 e 2026); 63 dos 63 deputados da ALBA vinculados às suas candidaturas.
   - Executivo estadual da Bahia: Governador Jerônimo Rodrigues coberto pelo arquivo histórico via candidaturas do TSE (2022) e portais estaduais catalogados.
   - Pipeline e workflow: registro de `alba` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alba` no workflow `.github/workflows/dados.yml`.
   - Publicação: 63 deputados estaduais da ALBA normalizados e publicados sem nenhuma rejeição de schema; total de 132 registros da Bahia (63 estaduais, 65 federais, 3 senadores e o Governador) integrados ao catálogo nacional de 1.547 perfis.
8. ALERJ e Governo do Rio de Janeiro (quinto estado da federação, ADR-0018): concluído em 2026-11-05.
   - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALERJ (`https://www.alerj.rj.gov.br`), endpoint de deputados em exercício (`/Deputados/QuemSao`), perfis individuais e atuação (`/Deputados/PerfilDeputado/{id}?Legislatura=20`), fotos oficiais no repositório (`/Uploads/PerfilDeputado/Imagem/...`) e portais estaduais de transparência (`rj-transparencia`) e diário oficial (`rj-diario-oficial`).
   - Modelo e identificador: identificador canônico `alerj-{id}` (numérico oficial) e chave `alerj` em `ids_externos` de `schemas/politico.schema.json`.
   - Adaptador `Alerj.java` implementado e coberto por testes unitários (`AlerjTest.java`): ingestão e parsing dos cards oficiais de deputados, fotos oficiais, extração de posse formal e biografias oficiais.
   - Vínculo com o TSE: inclusão de `RJ` em `UFS_ESTADUAIS` de `Tse.java` (5.292 candidaturas a deputado estadual em 2018, 2022 e 2026); 70 dos 70 deputados da ALERJ vinculados às suas candidaturas.
   - Executivo estadual do Rio de Janeiro: Governador Cláudio Castro coberto pelo arquivo histórico via candidaturas do TSE (2018 e 2022) e portais estaduais catalogados.
   - Pipeline e workflow: registro de `alerj` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alerj` no workflow `.github/workflows/dados.yml`.
   - Publicação: 70 deputados estaduais da ALERJ normalizados e publicados sem nenhuma rejeição de schema.
9. ALRS e Governo do Rio Grande do Sul (sexto estado da federação, ADR-0019): concluído em 2026-11-05.
   - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALRS (`https://www.al.rs.gov.br` e `https://ww4.al.rs.gov.br`), API em JSON de deputados em exercício (`https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados`), páginas parlamentares individuais e comissões (`/deputados/{id}` e `/deputados/{id}/comissoes`), fotos oficiais no repositório (`https://ww2.al.rs.gov.br/filerepository/fotografias/...`) e portais estaduais de transparência (`rs-transparencia`) e diário oficial (`rs-diario-oficial`).
   - Modelo e identificador: identificador canônico `alrs-{id}` (numérico oficial) e chave `alrs` em `ids_externos` de `schemas/politico.schema.json`.
   - Adaptador `Alrs.java` implementado e coberto por testes unitários (`AlrsTest.java`): ingestão e parsing dos 55 deputados estaduais em exercício na 56ª Legislatura (2023–2027), fotos oficiais em alta resolução, higienização de títulos e minimização de dados de contato.
   - Vínculo com o TSE: inclusão de `RS` em `UFS_ESTADUAIS` de `Tse.java` (2.225 candidaturas a deputado estadual em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 54 dos 55 deputados ficaram vinculados.
   - Executivo estadual do Rio Grande do Sul: Governador Eduardo Leite coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
   - No site: nenhum perfil da ALRS, porque a coleta falha no CI (ver "Limitações conhecidas").
10. ALECE e Governo do Estado do Ceará (sétimo estado da federação, ADR-0020): concluído em 2026-11-05.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALECE (`https://www.al.ce.gov.br`), endpoint de deputados em exercício (`/deputados`), perfis biográficos individuais (`/deputados/{slug}`), comissões permanentes (`/comissoes-permanentes/{slug}/composicao`), fotos oficiais no repositório (`/image/...`) e portais estaduais de transparência (`ce-transparencia`) e diário oficial (`ce-diario-oficial`).
    - Modelo e identificador: identificador canônico `alece-{slug}` (slug textual oficial) e chave `alece` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alece.java` implementado e coberto por testes unitários (`AleceTest.java`): ingestão e parsing dos 54 deputados estaduais em exercício na 31ª Legislatura (2023–2027), fotos oficiais, extração de biografias, 20 comissões permanentes populando `orgaos_e_frentes` e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `CE` em `UFS_ESTADUAIS` de `Tse.java` (1.547 candidaturas a deputado estadual em 2018, 2022 e 2026); 51 dos 54 deputados vinculados no site em 2026-11-06. 52 perfis têm o identificador `alece-{slug}`, e 2 foram fundidos com perfis de outra casa.
    - Executivo estadual do Ceará: Governador Elmano de Freitas coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alece` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alece` no workflow `.github/workflows/dados.yml`.
11. ALESC e Governo do Estado de Santa Catarina (oitavo estado da federação, ADR-0021): concluído em 2026-11-05.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALESC (`https://www.alesc.sc.gov.br`), endpoint de deputados em exercício (`/deputados/`), perfis biográficos individuais (`/deputado/{slug}/`), comissões permanentes e temporárias (`https://portalelegis.alesc.sc.gov.br/comissoes-permanentes`), fotos oficiais em alta resolução e portais estaduais de transparência (`sc-transparencia`) e diário oficial (`sc-diario-oficial`).
    - Modelo e identificador: identificador canônico `alesc-{slug}` (slug textual oficial) e chave `alesc` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alesc.java` implementado e coberto por testes unitários (`AlescTest.java`): ingestão e parsing dos 40 deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais em resolução original, extração de biografias, comissões permanentes e minimização estrita de dados de contato.
    - Vínculo com o TSE: inclusão de `SC` em `UFS_ESTADUAIS` de `Tse.java` (1.500 candidaturas a deputado estadual em 2018, 2022 e 2026); 40 dos 40 deputados vinculados no site em 2026-11-06. O vínculo ignora títulos como "Padre" antes do nome.
    - Executivo estadual de Santa Catarina: Governador Jorginho Mello coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alesc` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alesc` no workflow `.github/workflows/dados.yml`.
12. ALEGO e Governo do Estado de Goiás (nono estado da federação, ADR-0022): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALEGO (`https://portal.al.go.leg.br`), endpoint de deputados em exercício (`/deputados/em-exercicio`), perfis individuais (`/deputados/perfil/{id}`), perfis biográficos (`/deputados/perfil-biografico/{id}`), comissões permanentes e frentes parlamentares (`/comissoes/{id}`), fotos oficiais em alta resolução (`https://saba.al.go.leg.br/`) e portais estaduais de transparência (`go-transparencia`) e diário oficial (`go-diario-oficial`).
    - Modelo e identificador: identificador canônico `alego-{id}` (numérico oficial) e chave `alego` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alego.java` implementado e coberto por testes unitários (`AlegoTest.java`): ingestão e parsing dos deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais em resolução original, extração de nome civil e datas de nascimento, órgãos e frentes parlamentares populando `orgaos_e_frentes` e minimização estrita de dados de contato e informações familiares.
    - Vínculo com o TSE: inclusão de `GO` em `UFS_ESTADUAIS` de `Tse.java` (2.302 candidaturas a deputado estadual em 2018, 2022 e 2026); 43 dos 43 deputados vinculados no site em 2026-11-06.
    - Executivo estadual de Goiás: Governador Ronaldo Caiado coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alego` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alego` no workflow `.github/workflows/dados.yml`.
13. ALES e Governo do Estado do Espírito Santo (décimo estado da federação, ADR-0023): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALES (`https://www.al.es.gov.br`), endpoint de deputados em exercício (`/Deputado/Lista`), perfis individuais (`/Deputado/{slug}`), comissões permanentes (`/Comissao/{slug}`), fotos oficiais em alta resolução (`/appdata/anexos_internet/portal/deputados/`) e portais estaduais de transparência (`es-transparencia`) e diário oficial (`es-dioe`).
    - Modelo e identificador: identificador canônico `ales-{id}` (numérico oficial) e chave `ales` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Ales.java` implementado e coberto por testes unitários (`AlesTest.java`): ingestão e parsing dos 30 deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais, extração de nome civil e datas de nascimento, 18 comissões permanentes populando `orgaos_e_frentes` e minimização estrita de dados de contato.
    - Vínculo com o TSE: inclusão de `ES` em `UFS_ESTADUAIS` de `Tse.java` (1.558 candidaturas a deputado estadual em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 29 dos 30 deputados ficaram vinculados.
    - Executivo estadual do Espírito Santo: Governador Renato Casagrande coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `ales` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch ales` no workflow `.github/workflows/dados.yml`.
    - No site: nenhum perfil da ALES, porque a coleta falha no CI (ver "Limitações conhecidas").
14. ALEPA e Governo do Estado do Pará (décimo primeiro estado da federação, ADR-0024): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALEPA (`https://www.alepa.pa.gov.br`), endpoint de deputados da legislatura (`https://www.alepa.pa.gov.br/Home/Page/Deputados`), perfis individuais (`https://www.alepa.pa.gov.br/Institucional/Deputado/{id}`), comissões permanentes e representações (`https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes`), fotos oficiais em alta resolução (`https://alepa.quartertec.com.br/`) e portais estaduais de transparência (`pa-transparencia`) e diário oficial (`pa-ioepa`).
    - Modelo e identificador: identificador canônico `alepa-{id}` (numérico oficial) e chave `alepa` em `ids_externos` de `schemas/politico.schema.json`.
    - Adaptador `Alepa.java` implementado e coberto por testes unitários (`AlepaTest.java`): ingestão e parsing dos 41 deputados estaduais em exercício na 60ª Legislatura (2023–2027), fotos oficiais, extração de nome civil e datas de nascimento, comissões permanentes populando `orgaos_e_frentes` com papéis oficiais e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `PA` em `UFS_ESTADUAIS` de `Tse.java` (1.761 candidaturas a deputado estadual em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 40 dos 41 deputados ficaram vinculados.
    - Executivo estadual do Pará: Governador Helder Barbalho coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alepa` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alepa` no workflow `.github/workflows/dados.yml`.
15. ALEPE e Governo do Estado de Pernambuco (décimo segundo estado da federação, Região Nordeste, ADR-0025): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALEPE (`https://www.alepe.pe.gov.br`), endpoint de parlamentares da legislatura (`https://www.alepe.pe.gov.br/parlamentares/`), perfis individuais (`https://www.alepe.pe.gov.br/parlamentar/{slug}/`), comissões temáticas (`https://www.alepe.pe.gov.br/comissao/{slug}/`), fotos oficiais em WebP e JPEG de alta resolução e portais estaduais de transparência (`pe-transparencia`) e diário oficial (`pe-diario-oficial`).
    - Modelo e identificador: identificador canônico `alepe-{slug}` e chave `alepe` em `ids_externos` de `schemas/politico.schema.json` (regex `^[a-z0-9-]+$`).
    - Adaptador `Alepe.java` implementado e coberto por testes unitários (`AlepeTest.java`): ingestão e parsing dos 49 deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais, extração de nome civil, naturalidade e biografia, comissões temáticas permanentes e especiais populando `orgaos_e_frentes` com papéis oficiais e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `PE` em `UFS_ESTADUAIS` de `Tse.java` (1.811 candidaturas a deputado estadual em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 49 dos 49 deputados ficaram vinculados.
    - Executivo estadual de Pernambuco: Governadora Raquel Lyra coberta pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alepe` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alepe` no workflow `.github/workflows/dados.yml`.
16. CLDF e Governo do Distrito Federal (décimo terceiro ente da federação, Poder Legislativo Distrital, ADR-0026): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional da CLDF (`https://www.cl.df.gov.br`), endpoint de deputados da legislatura (`https://www.cl.df.gov.br/deputados-2023-2026`), perfis individuais (`https://www.cl.df.gov.br/web/guest/{slug}`), comissões permanentes estruturadas (`https://www.cl.df.gov.br/comissoes-permanentes`), fotos oficiais em alta resolução e portais do GDF (`df-transparencia` e `df-dodf`).
    - Modelo e identificador: identificador canônico `cldf-{slug}` e chave `cldf` em `ids_externos` de `schemas/politico.schema.json` (regex `^[a-z0-9-]+$`). Suporte ao cargo `deputado_distrital` nos schemas e na interface web. Na tabela de blocos do `Publicador`, só a partir de 2026-11-06 (ver "Limitações conhecidas").
    - Adaptador `Cldf.java` implementado e coberto por testes unitários (`CldfTest.java`): ingestão e parsing dos 24 deputados distritais em exercício na 9ª Legislatura (2023–2027), fotos oficiais, biografias oficiais da CLDF, 16 comissões permanentes populando `orgaos_e_frentes` com titulares e suplentes, e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `DF` em `UFS_ESTADUAIS` e `"DEPUTADO DISTRITAL"` em `CARGOS` de `Tse.java` (2.024 candidaturas a deputado distrital em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 24 dos 24 deputados ficaram vinculados.
    - Executivo distrital do DF: Governador Ibaneis Rocha coberto pelo arquivo histórico via candidaturas do TSE e portais do GDF catalogados.
    - Pipeline e workflow: registro de `cldf` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch cldf` no workflow `.github/workflows/dados.yml`.
17. Enriquecimento Temático: visualização expandida de Comissões Permanentes e Frentes Parlamentares na interface web com filtro por órgão e papel institucional.
18. Comparador de Atuação Parlamentar: concluído em 2026-11-06.
    - Rota `/comparar` no site: dois parlamentares da mesma casa lado a lado, só com dado factual (votações nominais, proposições, órgãos e frentes, despesas).
    - Seleção por busca, com sugestões da mesma UF e da mesma bancada, botão para inverter a ordem e atalho em cada perfil.
19. Dumps de dados abertos: concluído em 2026-11-06.
    - O `publish` gera `politicos.csv` (todos), `politicos-federal.csv` e `politicos-{uf}.csv`, com um manifest JSON de metadados e contagens. O comando `dumps` refaz os arquivos a partir do `index.json` existente.
    - Rota `/dados-abertos` no site, com downloads diretos, dicionário de dados, citação e a licença CC BY 4.0.
20. Despesas das assembleias estaduais: concluído em 2026-11-06 para a ALESC; ALEP e ALERJ não validadas.
    - Fonte `alesc-despesas`: demonstrativos anuais em CSV (2023 a 2026), catalogada em `contrib/fontes.yaml` e `docs/FONTES.md`.
    - Adaptador `Alesc.java` (`AlescTest.java`): 50.169 lançamentos de 2023 a 2026 vinculados aos 40 deputados em exercício, todos com despesas. Cada lançamento traz categoria, favorecido, data de referência e valor. Estorno (valor negativo) entra como veio.
    - Privacidade: a sequência de 11 dígitos do favorecido MEI é removida.
    - ALEP e ALERJ: ver "Limitações conhecidas".
21. Segundo município piloto: São Paulo (capital), Câmara Municipal e Prefeitura (ADR-0027): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: serviço SPLegisWS da CMSP (`cmsp-splegisws`), portal institucional (`cmsp-portal`) e Portal da Transparência da Prefeitura (`sp-capital-transparencia`).
    - Modelo e identificador: identificador canônico `cmsp-{chave}` e chave `cmsp` em `ids_externos` de `schemas/politico.schema.json`. Cargo `vereador`, esfera `municipal`, ente `3550308`.
    - Adaptador `Cmsp.java` implementado e coberto por testes unitários (`CmspTest.java`): 55 vereadores em exercício na 19ª Legislatura (2025 a 2028), com mandato, partido, 154 cargos em comissões e 7 na Mesa. Em exercício é quem ocupa um gabinete vigente: a lista de mandatos traz 64 nomes, com 9 suplentes sem exercício.
    - Vínculo com o TSE: a unidade eleitoral `71072` entra a partir da eleição de 2024, e 51 dos 55 vereadores foram vinculados pelo nome de urna. Prefeito e vice de 2024 entram com regras de mandato do ente `3550308` em `contrib/mandatos.yaml`.
    - Generalização municipal: cada unidade eleitoral tem o seu código IBGE e a sua primeira eleição (`Tse.UES_MUNICIPAIS`). As regras de mandato municipais valem só para o ente delas, e os mapas de vínculo de vereador são por município. Na comparação do `publish` antes e depois, os 52 perfis do piloto anterior ficaram idênticos.
    - Pipeline e workflow: registro de `cmsp` em `Main.java`, `Publicador.java` e `Vinculo.java` e passo `fetch cmsp` no workflow `.github/workflows/dados.yml`.
    - Proposições, em 2026-11-06: leis aprovadas, vetos e projetos em tramitação de cada vereador desde 2022, pelas consultas por promovente do SPLegisWS, com ementa e data da lista anual de projetos. No ensaio local, 6.495 proposições nos 55 vereadores, todas com ementa, data e fonte.
22. Testes de ponta a ponta e acessibilidade no CI (ADR-0028): concluído em 2026-11-06.
    - Playwright (Chromium) e axe-core, na versão exata, contra o build do site com dados sintéticos (`web/e2e/`).
    - 60 testes: 24 de fluxo (lista, perfil, comparação, dados abertos, documento e teclado) e 36 de WCAG 2.1 AA (nove rotas, temas claro e escuro, desktop e celular emulado).
    - O job `web` do CI passa a rodar testes unitários, build e a suíte. Em falha, o relatório sobe como artefato.
    - A primeira varredura achou dois defeitos reais, já corrigidos: o nome acessível do botão de tema não continha o texto visível (WCAG 2.5.3) e a tabela rolável de `/dados-abertos` não tinha foco por teclado (WCAG 2.1.1).

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
