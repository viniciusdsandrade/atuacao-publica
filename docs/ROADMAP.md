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
- A API da Câmara responde 504 e recusa conexões em escala a partir do runner do GitHub Actions (execuções de 2026-11-01 e 2026-11-02), e também respondeu 504 em 2026-10-30 de outra rede. Com o cache preenchido, os blocos usam a cópia anterior e ficam marcados como desatualizados. Desde 2026-11-02 a coleta tem orçamento de tempo, discursos são complementares e a falha em poucos deputados deixa a fonte parcial, não em falha. A execução diária roda às 06:17 UTC. Em 2026-11-10 o orçamento de 75 minutos se esgotou, mas as requisições que já esperavam vaga no cliente HTTP seguiram tentando contra os 504, e o passo foi encerrado no limite de 90 minutos. A Câmara saiu parcial, com a cópia anterior de 93 arquivos de discursos. Corrigido: o cliente HTTP também respeita o prazo da coleta.
- Proposições da ALES no ano aberto: em 2026-11-10, o cache tinha todas as páginas de 2026, mas menos itens únicos que o total da API (1.549 de 1.626 indicações, 540 de 550 projetos de lei). As páginas do ano aberto renovam em rodízio semanal, uma por dia, e a lista desloca a cada proposição nova: item que muda de página entre duas renovações fica de fora até o rodízio seguinte. No site, 2.195 autorias de 2026, contra 2.241 na publicação anterior. Corrigido no mesmo dia: as páginas de uma lista que desloca (proposições da ALES, da ALECE e da ALESC, verba e proposições da ALBA) renovam todas no mesmo dia da semana (`SEMANAL_EM_LISTA`). No ensaio local, os 2.374 itens de 2026 da API da ALES voltaram a bater com o total declarado. No site desde a publicação de 2026-11-10 às 19:48 UTC: 2.298 autorias de 2026 e 12.331 no total. O deploy do Pages expirou quatro vezes antes de passar, sem incidente no status do GitHub.
- DivulgaCandContas bloqueia o User-Agent do projeto. O ETL usa os arquivos do TSE.
- A agenda presidencial existe só em HTML, uma página por dia. Desde 2026-11-02 é coletada pela página do dia (ADR-0011); 2022 vem do Internet Archive, e dias sem captura ficam sem registro.
- O e-Agendas não traz a Presidência da República.
- O Planalto recusou conexão a partir do ambiente de validação. Os atos normativos vêm da API do Senado.
- A listagem de autorias do Senado (`/processo`) não traz temas.
- O Senado não publica presença em evento equivalente à da Câmara.
- O TSE registra a eleição, não o exercício do cargo. Renúncia ou sucessão de governador entra por contribuição com fonte T0.
- Oito governadores eleitos em 2022 têm candidatura a outro cargo em 2026 no próprio TSE: senador no AC, AM, ES, MT, PA e PB e presidente em GO e MG. Para concorrer a outro cargo, o governador renuncia até seis meses antes do pleito (CF, art. 14, § 6º). Corrigido em 2026-11-07 pelo ADR-0033: quem tem candidatura a outro cargo na eleição seguinte sai de em exercício, com o mandato encerrado no prazo legal de renúncia. A regra também corrige cinco governadores eleitos em 2018 que concorreram ao Senado em 2022. Quem assumiu esses governos segue sem perfil, e a data exata da renúncia só vem por contribuição com fonte T0.
- Planos de governo: só das eleições de 2022 e 2026.
- O site não embute fotos oficiais, para não gerar requisição do navegador do visitante a terceiros.
- SAPL do piloto: `ordemdia` e `expedientemateria` paginam de forma instável. A coleta vai por sessão. Os votos sem registro de votação ou sem item votado ficam fora e aparecem na saída do `normalize`.
- SAPL do piloto: a fonte não publica orientação de partido nem texto de discurso. Matérias com sequência de 11 dígitos na ementa (número de protocolo) são removidas pela regra de bloqueio de CPF e contam como rejeição em `_meta/execucao.json`.
- TCE-SP: um mês por chamada, com cerca de 2 meses de defasagem. A fonte identifica só o órgão, não o gabinete do prefeito.
- Portal da transparência da câmara do piloto: reprografia e correspondência vêm sem valor e ficam fora.
- O arquivo de candidaturas do TSE de 2024 vem com o CPF mascarado. Desde 2026-11-06, o título de eleitor, completo no arquivo, liga essa eleição às demais da mesma pessoa (ADR-0003). Quando o título é ambíguo (9 candidaturas em 2026-11-06), o vínculo usa nome civil e data de nascimento. Tudo em memória.
- SAPL do piloto: o filtro de votos por registro de votação admite votos anteriores a 2022, porque os ids de registro não seguem a ordem das sessões. Esses votos ficam fora, e a quantidade aparece na saída do `normalize`.
- Até 2026-11-02, o bloco de órgãos e frentes saía vazio para deputados e senadores por um erro no nome do campo normalizado. Corrigido.
- Agenda da Presidência: a primeira carga no CI foi concluída em execuções sucessivas com coleta em lotes de 40 dias. Dias já coletados ficam no cache; dias sem captura no arquivo ficam sem registro ou com cópia anterior preservada em caso de timeout. No site, 5.480 compromissos no presidente vigente (2023 a 2026) e 721 no presidente de 2019 a 2023.
- Portal da Transparência: o ZIP anual de viagens respondeu 405 intermitente ao runner do GitHub Actions em 2026-11-02 (2024 a 2026). Corrigido com repetição automática em caso de 405; viagens de 2024 a 2026 publicadas no site com passagens e diárias.
- Emendas parlamentares (ADR-0030): no ensaio local de 2026-11-06, 7 códigos de autor, com 122 emendas, ficaram fora do vínculo. Quatro têm dois perfis com o mesmo nome: "ATILA LIRA" (dois códigos), "MARIANA CARVALHO" e "ELMANO FERRER". Três não casam com nome nem nome de urna de perfil: "REINHOLD STEPHANES JUNIOR", "PROFESSORA DORINHA SEABRA" e "DR. ALLAN GARCES". Corrigido em 2026-11-13 pelo ADR-0052: o vínculo passa a usar o nome civil da casa e o nome sem título, e desempata homônimos pelo mandato federal nos anos das emendas. No ensaio local de 2026-11-13, os 886 códigos de autor ficaram ligados (29.772 emendas), e nenhum ficou fora. No site desde a publicação de 2026-11-13 das 07:18 UTC: 29.695 emendas de 886 códigos ligadas, e nenhum código fora.
- Emendas parlamentares: o arquivo não atribui a um parlamentar as emendas de bancada, de comissão e de relator. Elas ficam fora do perfil.
- Redes declaradas ao TSE (ADR-0031): em 2026, a fonte publicou a maior parte dos endereços todo em maiúsculas. O site normaliza só o esquema e o domínio; link cujo caminho diferencia maiúsculas pode não abrir. Não validado quais.
- Elmano Férrer tem dois perfis não fundidos: `sen-5531` (senador de 2015 a 2023) e `cam-234406` (deputado desde 2025-11-04). Nome civil e data de nascimento conferem no TSE e no Senado. O vínculo do Senado procura só candidaturas a senador da cobertura, e a eleição dele ao Senado foi em 2014. Corrigido em 2026-11-13 pelo ADR-0052: sem candidatura a senador, vale o CPF da única pessoa do TSE com o mesmo nome civil e a mesma data de nascimento. No ensaio local, os dois perfis viraram um (`cam-234406`, com `sen-5531` como alias), e doze senadores ganharam candidaturas do TSE. No site desde a publicação de 2026-11-13 das 07:18 UTC: `sen-5531`, `tse-10002544107` e `tse-240002538368` são aliases, 135 dos 142 perfis do Senado têm candidaturas do TSE, e a trava do ADR-0006 não disparou (2.523 perfis, 118 aliases).
- Em 2026-11-02 o `publish` do CI parou com "dois perfis com o identificador sapl-3509502-306": um ex-deputado novo no arquivo era o mesmo vereador já publicado, e o vínculo entre casas não os fundiu, porque o TSE mascara o CPF de 2024. Corrigido em dois pontos. O vínculo passa a usar também nome civil e data de nascimento. E o id publicado só passa a um perfil se não for o id derivado de outro, o que vale mesmo quando a fusão falha.
- A lista da página inicial exibe 48 cartões por vez. A busca do navegador (Ctrl+F) só acha o que está na tela. Use a busca do site.
- Não validado: leitor de tela real (VoiceOver, NVDA, TalkBack) e aparelhos móveis físicos. A auditoria de acessibilidade foi automatizada e por teclado.
- A auditoria de acessibilidade (axe, WCAG 2.1 AA) roda no CI sobre dados sintéticos, só no Chromium e no estado inicial das páginas (ADR-0028). Dado real longo, outros navegadores e estados interativos seguem sem varredura automática.
- ALESE: o sistema legislativo (`aleselegis.al.se.leg.br`) não aceitou conexão do runner do GitHub Actions nas execuções de 2026-11-12 (15 tentativas, todas com tempo esgotado). O portal (`al.se.leg.br`) respondeu. Sem as fichas no cache, a normalização parava com erro, e a publicação de 2026-11-13 das 00:19 UTC não tinha nenhum perfil de Sergipe. Corrigido em 2026-11-13: a normalização aceita a ficha ausente, e a casa vem da coleta local cifrada (ADR-0034). Os dados atualizam só quando a coleta local roda. Na publicação das 07:18 UTC, ainda sem a semente, entraram os 24 perfis só com mandato e candidaturas. Na das 13:04 UTC, com a semente coletada às 12:54 UTC, entraram comissões e proposições.
- ALESE: 21.576 das 27.800 proposições do ensaio local de 2026-11-13 são do tipo "Requerimento de Voto". O tipo vem da fonte e entra como os demais.
- ALESE: no ensaio local de 2026-11-13, 22 dos 24 deputados ficaram ligados a candidaturas do TSE, e não os 24 registrados em 2026-11-11. Em dois, o nome parlamentar difere do nome de urna: "Carminha Paiva" (urna "CARMINHA") e "Luizão Donatrampi" (urna "LUIZAO DONA TRAMPI"). O vínculo não casa por palavras soltas. No site em 2026-11-13, os mesmos 22 de 24.
- ALAP CEAP: no ensaio local de 2026-11-12, quatro gabinetes da fonte não casam com deputado em exercício e ficam fora das despesas: CARLOS LOBATO - MDB, DELEGADO INÁCIO - PDT, KAKA BARBOSA - PL e PAULO NOGUEIRA - PT.
- ALAP CEAP: dois lançamentos de 2025 (setembro e outubro) trazem a categoria já corrompida no HTML do portal (`Aparelhos, Equipamentos e Mquinas para uso em Escritrrrio`). O ETL reproduz o texto da fonte.
- O site só vai ao ar pelo workflow `dados` (agendado ou manual). Mudança só de interface espera a próxima execução.
- ALMG, ALRS, ALES, ALEPE e CLDF não aceitam conexão do runner do GitHub Actions: a conexão TCP expira. Nenhuma das cinco teve coleta válida no CI até 2026-11-06 (issues #5, #6 e #7 para as três primeiras). A coleta local funciona. Desde a publicação de 2026-11-07 das 19:04 UTC, as cinco estão no site pela coleta local cifrada (ADR-0034).
- Teste de 2026-11-06 com provas do Globalping, nos endereços que o ETL usa. Nenhuma das cinco responde à Azure (eastus) nem à AWS (us-east-1).
  - ALRS (API na porta 5000 e portal), ALES e CLDF respondem a todas as redes testadas no Brasil: residenciais e datacenters da AWS, do Google Cloud e da Oracle em São Paulo. ALRS e ALES também respondem à Vultr em São Paulo.
  - A ALMG, em São Paulo, recusa AWS e Oracle e responde a Google Cloud e Vultr. Redes residenciais funcionam.
  - A ALEPE recusa todos os datacenters testados, inclusive os de São Paulo. Só a rede residencial testada respondeu.
  - A ALEPA respondeu a todas as redes, nos EUA e no Brasil.
  - Um proxy numa nuvem no Brasil, fora da AWS e da Oracle, resolve quatro casas. Só um runner em rede residencial resolve as cinco.
  - Caminho escolhido em 2026-11-06: túnel SSH por uma máquina no Google Cloud em São Paulo, para ALMG, ALRS, ALES e CLDF (ADR-0029). O passo do workflow e o script estão prontos. Pendente: o mantenedor criar a máquina e os segredos (`docs/MAINTAINER_CHECKLIST.md`). A ALEPE segue fora.
  - Em 2026-11-07, os endereços alternativos dessas casas também recusaram conexão dos EUA. Enquanto a máquina não existe, as cinco casas vêm da coleta local do mantenedor, enviada ao CI num pacote cifrado (ADR-0034, `scripts/semente-enviar.sh`). Os dados delas atualizam só quando a coleta local roda.
- ALRS: até 2026-11-07, 112 votos de quatro projetos ficavam fora do site. A matéria cita processo SEI ("000005461-01.00/21"), cuja sequência tem a forma de CPF, e a guarda do `publish` descartava o voto. O adaptador passou a tirar essa sequência da matéria, como já se faz nas ementas das outras casas.
- Coleta local de 2026-11-07: o DNS da rede do mantenedor deixou de resolver `al.rs.gov.br`, e a ALRS saiu parcial, com cópia anterior. A semente foi refeita com a JVM lendo um arquivo de hosts (`-Djdk.net.hosts.file`) com os endereços de um resolvedor público. O TLS seguiu validando o nome.
- Em 2026-11-07, o deploy do Pages estourou o tempo de 10 minutos quatro vezes com o mesmo artefato, que estava correto. O deploy anterior, de outro artefato, levou 30 s. Uma execução nova, com artefato novo, publicou.
- ALEPE: o servidor da verba indenizatória responde 200 com lista vazia, em vez de erro, em parte das consultas. A coleta de 2026-11-07, a 4 consultas por segundo, guardou 39 de 74 listas de meses e 166 de 2.311 notas como vazias, e o site publicou 6.696 lançamentos (R$ 68,19 milhões), com R$ 6,07 milhões em "Sem detalhamento na fonte" que a fonte detalha. A causa foi corrigida em 2026-11-08: intervalo de 0,9 s por consulta, confirmação de toda lista vazia (inclusive a já guardada) e varredura final. Uma auditoria a 1 consulta por segundo achou o cache igual à fonte. O defeito foi achado por conferência manual: nenhum teste automático detecta resposta vazia que o servidor dá por engano.
- Outras fontes de despesa por deputado e mês, conferidas contra a fonte depois do defeito da ALEPE, em 2026-11-08: na ALMG, as 28 respostas vazias do cache (27 listas de comissões e 1 de datas de verba) são vazias na fonte também. Na ALEGO, 30 pares deputado e mês sorteados entre os 1.765 que o resumo da fonte lista têm o mesmo número de lançamentos e o mesmo valor no site; uma amostra pequena não prova que não haja lacuna. Na ALRS e na ALES, uma amostra de 8 respostas vazias de cada uma, consultada a 1 requisição por segundo em 2026-11-08, confirmou que são vazias na fonte oficial. A ALERJ não foi conferida.
- ALRS: nas despesas parlamentares por categoria (`alrs-gastos`), em quatro consultas mensais (em dois deputados) a página da própria fonte apresenta divergência de centavos entre o total informado no resumo e a soma dos itens da tabela. O adaptador registra o aviso no log e publica a soma calculada das categorias.
- ALEPE, CLDF, ALES e ALRS: o bloco de mandatos traz só a legislatura atual (2023 a 2027), mas proposições e votos começam em 2022 para quem já era deputado. O perfil mostra a atuação de 2022 sem o mandato daquele ano.
- A API da ALMG restringe certos recursos como `/proposicoes/pesquisa` (`Access Denied`). Deputados, comissões e verbas indenizatórias são abertos e funcionam via API v2. As proposições vêm do CSV anual dos dados abertos (`almg-proposicoes`).
- ALESC: as despesas dos gabinetes (`alesc-despesas`) cobrem 2023 a 2026 e entram só para os 40 deputados em exercício. Em 2026-11-06, 4.355 de 54.524 linhas não correspondiam a nenhum deles e foram descartadas; a contagem aparece na saída do `normalize`. A fonte não traz CNPJ nem número de documento, e o favorecido vem vazio em cerca de 21% das linhas.
- ALESC: o favorecido MEI vem como nome e CPF. A sequência de 11 dígitos é removida na normalização (`Guarda.semCpf`), como na Câmara, no Senado, na ALESP e na ALMG.
- ALEP: a consulta das verbas de ressarcimento por deputado (`api/ressarcimento`) exige reCAPTCHA v3, conforme o script da própria página (2026-11-06). O ETL não contorna CAPTCHA, então essas despesas não são coletadas (`alep-ressarcimento`, bloqueado).
- ALERJ: as despesas vêm da API do DOCIGP desde 2026-11-07 (`alerj-docigp`). Três deputados em exercício têm `remote_id` nulo (ids 344, 514 e 515) e entram pelo mapa fixo 344→131, 514→135 e 515→136, com o envelope `data` ou `rows`. No site em 2026-11-09, às 00:32 UTC, 31.062 lançamentos em 70 deputados. A conexão oscila: nesta execução 4 perfis ficaram sem atualização, com cópia anterior. A Azure (eastus) recebeu resposta, e a AWS nos EUA e o Google Cloud e a Oracle em São Paulo não.
- ALEGO: as votações nominais (`alego-votacoes`) estão no adaptador. Em 2026-11-09 o DNS de `transparencia.al.go.leg.br` resolvia e a conexão TCP na porta 443 expirava sem completar, a partir do runner do GitHub Actions e de sondas no Brasil e no exterior. Os JSON anuais de votações ficaram inalcançáveis. O bloco de votos não entrou no site. As 51.156 despesas saíram da cópia em cache.
- CMSP (capital): só vereadores em exercício entram. Os 9 suplentes sem exercício e quem deixou a vereança na legislatura ficam fora. O serviço não tem voto nominal por vereador, e os perfis ficam sem votações e sem foto. As proposições cobrem, desde 2022, só o que está em tramitação, virou lei ou foi vetado: o serviço não informa o autor do projeto encerrado sem virar lei, nem separa autor de coautor.
- CMSP: 4 dos 55 vereadores ficaram sem vínculo com o TSE porque o nome parlamentar difere do nome de urna (por exemplo, uma palavra só). O vínculo ignora só a pontuação ("Jr.", "Dra."), porque casar por palavras soltas ligaria homônimos parciais e atribuiria candidaturas e bens a outra pessoa.
- CMSP: a fonte deixa cargo antigo da Mesa sem data de fim quando há sucessor. Em cargo exclusivo (Mesa, presidência e vice-presidência de comissão), vale o de início mais recente entre os vereadores em exercício.
- Capital: a eleição de 2020 não entra, porque o eleito prefeito deixou o cargo em 2021 e a regra de mandato do projeto supõe os quatro anos do eleito. A despesa do Executivo também não: a API do TCE-SP devolve lista vazia para a capital, e o Diário Oficial da Cidade respondeu 403 à requisição direta.
- Até 2026-11-06, nove adaptadores (ALECE, ALEGO, ALEPE, ALERJ, ALEPA, ALES, ALRS, ALESC e CLDF) gravavam o bloco `fontes` como lista, e o `Vinculo` lê um objeto por bloco. Os perfis dessas casas saíam com os blocos sem fonte e com `atualizado_em` nulo (verificado no perfil `alerj-505` publicado). Corrigido: as fontes de cada bloco vêm da proveniência dos registros (`Registros.fontesDosBlocos`), e o `Vinculo` interrompe o `publish` se receber uma lista. No ensaio local das treze casas, nenhum bloco com registro ficou sem fonte.
- Até 2026-11-07, a ALESP repetia a mesma fonte a cada registro (um perfil citava 2.106 vezes o mesmo arquivo de despesas), e o bloco de participação em votações saía sem fonte. Corrigido no adaptador. O `publish` passou a tirar fontes repetidas de qualquer bloco e, se um adaptador não declarar a fonte de um bloco com registro, a citar a proveniência dos registros, com o caso contado no log.
- Até 2026-11-06, o cargo `deputado_distrital` não estava na tabela de blocos do `Publicador`, e os perfis da CLDF saíam sem bloco nenhum, nem mandato. Corrigido, com um teste que confere os blocos de todo cargo do schema.


## Publicação

- Pages e execução diária ativos desde 2026-11-01 (`ETL_AGENDADO` e `PUBLICAR_PAGES` em `true`). Passos em `docs/MAINTAINER_CHECKLIST.md`.

## Casas estaduais e municipais no site

Estado da publicação de 2026-11-10, às 23:06 UTC, com 2.116 perfis. "Perfis" conta os perfis com o identificador da casa, inclusive os fundidos com outra casa.

| Casa | Coleta no CI | Perfis | Vínculo com o TSE | Blocos além de mandato e candidaturas |
|---|---|---|---|---|
| ALESP (SP) | ok | 153 | 140 | votações em comissão, proposições, despesas e comissões |
| ALMG (MG) | ok, pela coleta local cifrada | 110 | 109 | comissões, despesas (118.453 lançamentos em 107 deputados) e proposições (58.861 autorias em 108 deputados) |
| ALEP (PR) | parcial: 4 sessões passaram a responder 404, com cópia anterior | 54 | 51 | comissões, proposições (24.343) e votações nominais (197.815 votos em 51 deputados) |
| ALBA (BA) | ok, pela coleta local cifrada | 63 | 63 | comissões, verba indenizatória (17.430 lançamentos em 63 deputados, R$ 130,27 milhões, sem fornecedor), proposições (9.753 autorias em 63 deputados) e presença em sessão plenária (27.972 registros em 63 deputados, sendo 21.917 presenças, 259 faltas justificadas e 5.796 faltas) |
| ALERJ (RJ) | ok; o DOCIGP oscila e já deixou perfis sem atualização, com cópia anterior | 70 | 70 | despesas (31.062 lançamentos em 70 deputados) |
| ALRS (RS) | ok, pela coleta local cifrada | 55 | 54 | comissões, proposições (4.504 em 55 deputados), gastos por categoria e mês (27.690 linhas em 55 deputados, R$ 35,82 milhões, sem fornecedor) e votos em plenário (33.204 votos em 55 deputados, sem bloco de participação) |
| ALECE (CE) | ok | 54 | 51 | comissões, despesas (15.511 lançamentos), votações nominais (2.439 votos) e proposições (5.553 em 53 deputados) |
| ALESC (SC) | ok | 40 | 40 | despesas, comissões, proposições (2.103 da 20ª legislatura em 40 deputados) votações nominais (5.674 votos em 39 deputados, sem bloco de participação) e presença em sessão plenária (16.707 registros em 40 deputados, 4.400 deles de ausência justificada) |
| ALEGO (GO) | parcial: a conexão TCP 443 da API de votações expirou sem completar; despesas da cópia em cache | 43 | 43 | comissões e frentes e despesas (51.156 lançamentos) |
| ALES (ES) | ok, pela coleta local cifrada | 30 | 29 | comissões, proposições (12.331 autorias em 30 deputados) e cotas por produto e mês (3.108 linhas em 30 deputados, R$ 7,72 milhões em valor solicitado, sem fornecedor) |
| ALEPA (PA) | ok | 41 | 40 | comissões e votações nominais (7.044 votos em 41 deputados, com participação por ano) |
| ALEPE (PE) | ok, pela coleta local cifrada | 49 | 49 | comissões, proposições (26.911 autorias em 49 deputados) e despesas (10.464 lançamentos em 44 deputados, R$ 104,85 milhões) |
| CLDF (DF) | ok, pela coleta local cifrada | 24 | 24 | comissões e proposições (26.067 autorias em 24 deputados) |
| ALMT (MT) | ok | 24 | 24 | Mesa Diretora e deputados em exercício |
| ALEMS (MS) | ok | 24 | 24 | Mesa Diretora e deputados em exercício |
| ALPB (PB) | ok | 36 | 36 | Mesa Diretora e proposições legislativas via SAPL |
| ALEAM (AM) | ok | 24 | 24 | Mesa Diretora e proposições legislativas via SAPL |
| ALEAL (AL) | ok | 27 | 27 | Mesa Diretora e proposições legislativas via SAPL |
| ALERO (RO) | ok | 24 | 24 | Mesa Diretora e proposições legislativas via SAPL |
| ALEPI (PI) | ok | 30 | 30 | Mesa Diretora e proposições legislativas via SAPL |
| ALRN (RN) | ok | 24 | 24 | Mesa Diretora e deputados em exercício |
| ALETO (TO) | ok | 24 | 24 | Mesa Diretora e proposições legislativas via SAPL |
| ALESE (SE) | ok | 24 | 24 | Mesa Diretora e deputados em exercício |
| ALAP (AP) | ok, pela coleta local cifrada | 24 | 24 | Mesa Diretora e deputados em exercício |
| CMSP (capital) | ok | 55 | 51 | comissões e proposições (6.547) |
| Câmara do piloto (SAPL) | ok | 50 | 50 | votos, presença, falas, matérias, despesas e comissões |

Conferido nos 841 perfis das 14 casas da tabela, inclusive os fundidos com outra casa: os 5.175 blocos com registro citam fonte e data. As linhas de ALMG e CLDF repetem a conferência anterior: perfis fundidos com a Câmara somam os blocos das duas casas, e a contagem só da casa não foi refeita. Os 9 registros da ALEPE (e-mail na ementa), os 23 da CLDF e os 3 da ALRS (número de telefone na ementa) que a guarda do `publish` removeu não entram nas contagens. A participação em votações da ALESP saía sem fonte até 2026-11-07 (ver "Limitações conhecidas"). Os oito governadores eleitos em 2022 que concorreram a outro cargo saíram de em exercício (ADR-0033), e restam 19 governadores em exercício.

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

## Revisão de UX e UI (ADR-0035 e ADR-0036)

Concluída em 2026-11-07, sem mudar dado, rota nem parâmetro de URL.

| Fase | Entrega | Estado |
|---|---|---|
| 1. CSS e tokens | Tokens de cor novos nos dois temas, rótulo de seção único, mínimo de 13 px, alvo de 32 px em filtros e selos clicáveis, barra de despesa com contraste, coluna de leitura de 40 rem nos documentos, degradê na faixa de blocos do celular | concluída |
| 2. Estrutura | Cabeçalho de uma linha com menu abaixo de 64 rem e dois grupos a partir daí; busca na apresentação da inicial; data dos dados na linha de resultados; esfera à vista no celular; partidos em ordem alfabética só em recortes de até 12; perfil em três níveis; anos no topo de cada bloco; lado a lado corrigido; dados abertos com tabela por estado; estado das coletas com marcador por estado e nome de todas as coletas; links de fonte com texto próprio | concluída |
| 3. Interações | Link de endereço por bloco, impressão pelo perfil, tema em três opções no menu, sumário fixo dos documentos no celular, busca nas páginas internas, ADR-0036 do lado a lado | concluída |

Também corrigido: o clique no sumário do perfil e dos documentos deixava o bloco ou o título embaixo da faixa fixa do celular.
O roteador passa a usar o `scroll-padding-top` do CSS como deslocamento.

Depois da publicação, o controle de tema virou um interruptor entre claro e escuro: trilho em pílula e bolinha, à esquerda no tema escuro e à direita no claro.
Ele substitui o botão de três estados (Auto, Claro e Escuro) e as três opções do menu do celular (ADR-0035, item 26).
Sem escolha salva, o site segue o aparelho. Quando o interruptor volta ao tema do aparelho, a escolha salva some.

Medidas no Chromium, com o CSS e o HTML novos sobre o site publicado:

| Medida | Antes | Depois |
|---|---|---|
| Altura do cabeçalho em 390 px | 193 px | 61 px |
| Topo do campo de busca em 390 px | 726 px | 360 px |
| Topo do campo de busca em 1366 px | 509 px | 331 px |
| Primeiro bloco do perfil de referência em 390 px | 972 px | 737 px |
| Chips de filtro na inicial com dados reais | 19 | 8 |

Verificação de 2026-11-07:

- Antes do push: checagem de tipos dos templates com `ngc --noEmit`, em modo estrito, sem erro. Testes unitários de `rotulos` e `tema`: 20 de 20. Novos: casa legislativa do lado a lado e período dos anos.
- CI do commit `0fe78e0`: build, testes unitários e E2E com axe passaram (69 testes, 1 pulado: o de teclado no celular emulado).
- Publicado no mesmo dia pelo workflow `dados`, com a fonte `contrib` e as demais do cache.
- No site publicado, no Chromium: axe-core (WCAG 2.0 e 2.1, A e AA) sem achados em 10 rotas, em 390 e 1366 px, nos dois temas.
- No site publicado: Esc fecha o menu e devolve o foco ao botão; o título do bloco e o do documento param abaixo da faixa fixa; o foco vai ao lado a lado montado; a busca do cabeçalho leva à lista com `?q=`.
- Sem transbordo horizontal em 320 px em nenhuma rota. Nenhuma requisição a terceiros e nenhum erro de console.

Limitações:

- Em 1366 px, a busca da inicial ficou a 331 px do topo. A meta do ADR-0035 era abaixo de 300 px.
- Não validado: teste com pessoas (roteiro no ADR-0035), leitor de tela real, Firefox e WebKit.
- Fica fora: link de cada coleta para a seção dela em Fontes (ver ADR-0035).

## Fase 2 (depois da POC)

### Esfera federal

1. Emendas parlamentares individuais (ADR-0030): concluído em 2026-11-06.
   - Fonte `cgu-portal-arquivos`: ZIP único `emendas-parlamentares/UNICO`, sem chave, com política semanal (ADR-0005).
   - Bloco `emendas` de deputados federais e senadores: contagem por tipo e soma de cada valor por ano do orçamento no perfil; cada emenda no detalhe do ano.
   - Ensaio local: 29.558 de 29.680 emendas individuais desde 2022 (99,6%), de 879 códigos de autor, ligadas a perfis federais. Em 815 de 839 perfis com localidade identificável, ao menos 80% das emendas vão para a UF do perfil.
   - O perfil cresce no máximo 2,6 KB. Os arquivos de detalhe somam cerca de 13 MB ao site.
   - Testes: parse do CSV latin-1 e agrupamento pelo código do autor (`CguTest`), exemplos de schema, fluxo e WCAG da página de detalhe no E2E.
   - Fora do vínculo e fora do arquivo: ver "Limitações conhecidas".

### Eleições (TSE)

1. Financiamento de campanha (ADR-0032): concluído em 2026-11-06.
   - Fonte `tse-dados-abertos`: prestação de contas de 2018 a 2026, só receitas e despesas contratadas, lidas por Range (ADR-0005, item 10). A primeira carga baixa cerca de 530 MB, contra cerca de 3,8 GB dos ZIPs.
   - O cache guarda o recorte das candidaturas cobertas, só com as colunas usadas e sem documento nem nome de pessoa física: 64 MB.
   - Bloco `financiamento_campanha` em todos os cargos: receitas por fonte e por origem, despesas contratadas por tipo e os maiores fornecedores com CNPJ, por eleição.
   - Ensaio local: 1.800 dos 1.867 perfis com o bloco preenchido. Na eleição de 2026, 1.428 perfis com receitas, num total de R$ 3,09 bilhões.
   - Testes: leitura de ZIP por Range com CRC-32 (`ZipRemotoTest`), recorte e somas sem pessoa física (`TseTest`), exemplos de schema, fluxo e WCAG no E2E.

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
    - No site: 41 perfis desde a publicação de 2026-11-06, a primeira com a coleta da ALEPA no CI.
    - Executivo estadual do Pará: Governador Helder Barbalho coberto pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alepa` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alepa` no workflow `.github/workflows/dados.yml`.
15. ALEPE e Governo do Estado de Pernambuco (décimo segundo estado da federação, Região Nordeste, ADR-0025): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional aberto da ALEPE (`https://www.alepe.pe.gov.br`), endpoint de parlamentares da legislatura (`https://www.alepe.pe.gov.br/parlamentares/`), perfis individuais (`https://www.alepe.pe.gov.br/parlamentar/{slug}/`), comissões temáticas (`https://www.alepe.pe.gov.br/comissao/{slug}/`), fotos oficiais em WebP e JPEG de alta resolução e portais estaduais de transparência (`pe-transparencia`) e diário oficial (`pe-diario-oficial`).
    - Modelo e identificador: identificador canônico `alepe-{slug}` e chave `alepe` em `ids_externos` de `schemas/politico.schema.json` (regex `^[a-z0-9-]+$`).
    - Adaptador `Alepe.java` implementado e coberto por testes unitários (`AlepeTest.java`): ingestão e parsing dos 49 deputados estaduais em exercício na 20ª Legislatura (2023–2027), fotos oficiais, extração de nome civil, naturalidade e biografia, comissões temáticas permanentes e especiais populando `orgaos_e_frentes` com papéis oficiais e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `PE` em `UFS_ESTADUAIS` de `Tse.java` (1.811 candidaturas a deputado estadual em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 49 dos 49 deputados ficaram vinculados.
    - No site: nenhum perfil da ALEPE, porque a coleta falha no CI (ver "Limitações conhecidas").
    - Executivo estadual de Pernambuco: Governadora Raquel Lyra coberta pelo arquivo histórico via candidaturas do TSE e portais estaduais catalogados.
    - Pipeline e workflow: registro de `alepe` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch alepe` no workflow `.github/workflows/dados.yml`.
16. CLDF e Governo do Distrito Federal (décimo terceiro ente da federação, Poder Legislativo Distrital, ADR-0026): concluído em 2026-11-06.
    - Fontes catalogadas e validadas em `docs/FONTES.md`: portal institucional da CLDF (`https://www.cl.df.gov.br`), endpoint de deputados da legislatura (`https://www.cl.df.gov.br/deputados-2023-2026`), perfis individuais (`https://www.cl.df.gov.br/web/guest/{slug}`), comissões permanentes estruturadas (`https://www.cl.df.gov.br/comissoes-permanentes`), fotos oficiais em alta resolução e portais do GDF (`df-transparencia` e `df-dodf`).
    - Modelo e identificador: identificador canônico `cldf-{slug}` e chave `cldf` em `ids_externos` de `schemas/politico.schema.json` (regex `^[a-z0-9-]+$`). Suporte ao cargo `deputado_distrital` nos schemas e na interface web. Na tabela de blocos do `Publicador`, só a partir de 2026-11-06 (ver "Limitações conhecidas").
    - Adaptador `Cldf.java` implementado e coberto por testes unitários (`CldfTest.java`): ingestão e parsing dos 24 deputados distritais em exercício na 9ª Legislatura (2023–2027), fotos oficiais, biografias oficiais da CLDF, 16 comissões permanentes populando `orgaos_e_frentes` com titulares e suplentes, e minimização de dados de contato.
    - Vínculo com o TSE: inclusão de `DF` em `UFS_ESTADUAIS` e `"DEPUTADO DISTRITAL"` em `CARGOS` de `Tse.java` (2.024 candidaturas a deputado distrital em 2018, 2022 e 2026). No ensaio local de 2026-11-06, 24 dos 24 deputados ficaram vinculados.
    - No site: nenhum perfil da CLDF, porque a coleta falha no CI (ver "Limitações conhecidas").
    - Executivo distrital do DF: Governador Ibaneis Rocha coberto pelo arquivo histórico via candidaturas do TSE e portais do GDF catalogados.
    - Pipeline e workflow: registro de `cldf` em `Main.java`, `Publicador.java`, `Vinculo.java` e passo `fetch cldf` no workflow `.github/workflows/dados.yml`.
17. Enriquecimento Temático: visualização expandida de Comissões Permanentes e Frentes Parlamentares na interface web com filtro por órgão e papel institucional.
18. Comparador de Atuação Parlamentar: concluído em 2026-11-06.
    - Rota `/comparar` no site: dois parlamentares da mesma casa lado a lado, só com dado factual (votações nominais, proposições, órgãos e frentes, despesas).
    - Seleção por busca, com sugestões da mesma UF e da mesma bancada, botão para inverter a ordem e atalho em cada perfil.
    - Revisto em 2026-11-07 pelo ADR-0036: virou "Lado a lado", aceita só parlamentares da mesma casa, mostra o período de cada número e saiu do menu principal.
19. Dumps de dados abertos: concluído em 2026-11-06.
    - O `publish` gera `politicos.csv` (todos), `politicos-federal.csv` e `politicos-{uf}.csv`, com um manifest JSON de metadados e contagens. O comando `dumps` refaz os arquivos a partir do `index.json` existente.
    - Rota `/dados-abertos` no site, com downloads diretos, dicionário de dados, citação e a licença CC BY 4.0.
20. Despesas das assembleias estaduais: concluído em 2026-11-06 para a ALESC e a ALEGO, e em 2026-11-07 para a ALECE e a ALERJ. A ALEP está bloqueada por CAPTCHA.
    - Fonte `alesc-despesas`: demonstrativos anuais em CSV (2023 a 2026), catalogada em `contrib/fontes.yaml` e `docs/FONTES.md`.
    - Adaptador `Alesc.java` (`AlescTest.java`): 50.169 lançamentos de 2023 a 2026 vinculados aos 40 deputados em exercício, todos com despesas. Cada lançamento traz categoria, favorecido, data de referência e valor. Estorno (valor negativo) entra como veio.
    - Privacidade: a sequência de 11 dígitos do favorecido MEI é removida.
    - Fonte `alego-despesas`: verba indenizatória mensal em JSON, com um detalhe por deputado e mês, de 2023 a 2026. O id do deputado na transparência é o mesmo do perfil.
    - Adaptador `Alego.java` (`AlegoTest.java`): no ensaio local de 2026-11-06, 51.156 lançamentos nos 43 deputados em exercício, 96,6% com o CNPJ do fornecedor. Entra o valor indenizado, com a data do documento. CPF de prestador pessoa física é descartado.
    - A primeira carga tem cerca de 1.800 requisições e leva mais de uma execução do CI. O cache incremental continua de onde parou.
    - Fonte `alece-despesas`: verba de desempenho parlamentar em CSV mensal, de 2023 a 2026. O deputado vem pelo nome, e o vínculo ignora acento e pontuação.
    - Adaptador `Alece.java` (`AleceTest.java`): no ensaio local de 2026-11-07, 15.444 lançamentos nos 54 deputados em exercício, 80% com CNPJ. A descrição do empenho é texto livre e não entra; a categoria é o nome da verba. 1.657 linhas de quem não está em exercício ficaram fora.
    - Fonte `alerj-docigp`: orçamento mensal da verba de gabinete no DOCIGP, com os lançamentos embutidos. O `remote_id` é o id do perfil.
    - Adaptador `Alerj.java` (`AlerjTest.java`): no ensaio local de 2026-11-07, 30.330 lançamentos em 67 dos 70 deputados, 95% com CNPJ. A categoria é o centro de custo oficial. Crédito, transporte e devolução de saldo ficam fora.
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
23. Fontes de atuação das assembleias: levantamento de 2026-11-06, ainda sem adaptador.
    - Cada endereço respondeu a requisição real no levantamento. O acesso a partir do exterior só foi testado onde a tabela diz.
    - Proposições da ALMG: implementadas em 2026-11-07 (`almg-proposicoes`), pelo CSV anual dos dados abertos, em que o id do autor é o id do perfil. A ALMG recusa conexão de fora do Brasil, e o CSV entra pela coleta local cifrada (ADR-0034). No ensaio local, 58.861 autorias de 2022 a 2026 em 108 dos 110 deputados, com tema da indexação oficial em 84%. Requerimentos vêm sem ementa.
    - Proposições da ALEPE: implementadas em 2026-11-07 (`alepe-proposicoes`), pelas listas XML anuais de projetos, indicações e requerimentos da API de dados abertos. O autor vem pelo nome parlamentar, que casou com os 49 deputados em exercício. Entra pela coleta local cifrada. No ensaio local, 26.920 autorias de 2022 a 2026; 9 ementas com e-mail ficam para a guarda do `publish`.
    - Proposições da CLDF: implementadas em 2026-11-07 (`cldf-proposicoes`), pela API pública do PLe, em páginas de 2.000 por POST. A autoria vem por nome, que casou com os 24 deputados em exercício. Entra pela coleta local cifrada. No ensaio local, 26.090 autorias de 2022 a 2026; 23 ementas com número de telefone ficam para a guarda do `publish`.
    - Despesas da ALEPE: implementadas em 2026-11-07 (`alepe-despesas`), pela verba indenizatória do portal de transparência: lista de deputados, legenda de rubricas por ano, meses, documentos e notas. O id do deputado no portal difere do id do perfil, e o vínculo é pelo nome. No ensaio local, 10.450 lançamentos de 2022 a 2026 em 44 deputados, R$ 104.790.852,37, igual à soma dos totais dos 3.335 documentos. Três perfis ficam sem despesa por falta de id no portal, e dois por não terem documento. A fonte responde lista vazia, sem erro, em parte das consultas, e a primeira publicação (2026-11-07, 6.696 lançamentos e R$ 68,19 milhões) saiu incompleta por isso: ver "Limitações conhecidas".
    - Despesas da CLDF: os cinco arquivos de 2022 a 2026 do conjunto `verbas-indenizatorias` do CKAN foram baixados e lidos em 2026-11-07, e não entram. Os formatos mudam de um ano para outro e dentro do mesmo arquivo: cabeçalho com outro nome em 2025, data como número serial do Excel, como "01 de agosto de 2023", como m/d/aaaa e como dd/mm/aaaa hh:mm, valor com vírgula, com ponto e com "R$". Em 2026, 112 das 276 datas têm os dois primeiros campos até 12, e não há como saber se é dia ou mês. Há linhas com o valor na coluna da data e datas vazias (58 em 2026, 87 em 2025). O nome do parlamentar ora é o civil, ora o de urna, e o arquivo traz o CPF do deputado e do prestador. Importar exigiria adivinhar data e autoria, e o projeto não inventa dado.
    - Votos em plenário da ALRS: implementados em 2026-11-07 (`alrs-votos-plenario`), por deputado e ano no portal da transparência, com o mesmo id do perfil. A fonte só lista o voto dado, sem ausência: não há bloco de participação. Entra pela coleta local cifrada. No ensaio local, 33.204 votos de 2022 a 2026 nos 55 deputados.
    - Proposições da ALES: implementadas em 2026-11-07 (`ales-proposicoes`), pela API NoPaper, nos tipos de autoria parlamentar (PL, PLC, PEC, PR, PDL, indicações e requerimentos). A API derruba a página inteira quando um item tem campo nulo: o ETL refaz a página item a item, e 52 itens de 2022 e 2023 ficam fora. Entra pela coleta local cifrada. No ensaio local, 12.311 autorias de 2022 a 2026 nos 30 deputados.
    - Votações da ALECE: implementadas em 2026-11-07 (`alece-votacoes`), com a lista pública e uma planilha XLSX por matéria. No ensaio local, 2.427 votos de 61 votações nos 54 deputados, com participação por ano.
    - Proposições da ALEP: implementadas em 2026-11-07, pela API pública (`alep-proposicoes`), que só aceita o filtro no corpo de um POST. O `Coleta` ganhou `obterPostOpcional` para isso. No ensaio local, 24.238 autorias nos 54 deputados.
    - Votações da ALEP: implementadas em 2026-11-07 (`alep-votacoes`). Os XLSX por sessão trazem o parlamentar pelo nome civil, e a ponte com o perfil é o nome civil da candidatura do TSE ligada a ele. No ensaio local, 197.559 votos de 4.135 itens em 51 dos 54 deputados; os 3 sem vínculo com o TSE ficam sem votos.
    - Cotas parlamentares da ALES: implementadas em 2026-11-08 (`ales-cotas`), pela API do portal de transparência (`CotasParlamentaresNovoTable`), por gabinete e mês. Os valores vêm agregados por produto e mês, sem identificação de fornecedor nem documento. O vínculo com o perfil é feito pelo nome do titular do gabinete. Entra pela coleta local cifrada (ADR-0034). No site e no ensaio local, 3.108 cotas por produto e mês de 2022 a 2026 nos 30 deputados, totalizando R$ 7.724.090,96, sem divergências de soma.
    - Proposições da ALRS: implementadas em 2026-11-08 (`alrs-proposicoes`), pela página de proposições por deputado no portal da Assembleia (`/deputados/{id}/proposicoes`). Entra pela coleta local cifrada. No ensaio local, 4.507 proposições de 2022 a 2026 nos 55 deputados (todos com ao menos uma proposição). No site, 4.504: a guarda do `publish` removeu 3 por número de telefone na ementa.
    - Despesas da ALRS: implementadas em 2026-11-08 (`alrs-gastos`), pelo portal da transparência por deputado, ano e mês (`/parlamentares/gastos/pesquisa`). A fonte traz o total mensal por categoria (23 categorias), sem fornecedor nem documento. Entra pela coleta local cifrada. No site e no ensaio local, 27.684 lançamentos mensais por categoria de 2022 a 2026 nos 55 deputados, somando R$ 35.820.754,75 (4 meses com divergência de centavos entre o total e as parcelas na própria fonte).
    - Proposições da ALECE: implementadas em 2026-11-08 (`alece-proposicoes`), pelas listagens HTML paginadas da 31ª legislatura (2023–2026), cobrindo PL, PI, PDL, PLC, PEC e PR. O autor vem pelo nome. Sequência de 11 dígitos na ementa (CPF) é removida. A listagem é UTF-8 com BOM e, em algumas páginas, um byte Windows-1252 (0x96) no meio do texto; o ETL lê com substituição. No site em 2026-11-09, 5.573 proposições em 53 deputados.
    - Votações nominais da ALEGO: implementadas em 2026-11-08 (`alego-votacoes`), pelo JSON anual de detalhes (`/api/transparencia/votacoes.json?todos=true&ano={ano}&tipo=detalhes`). O parlamentar vem pelo nome, sem título e sem patente. Entram os blocos `votos` e `participacao_votacoes`. Em 2026-11-09 o DNS resolvia e a conexão TCP na porta 443 expirava sem completar. Os JSON anuais ficaram inalcançáveis, e o bloco de votos não entrou no site.
    - Proposições da ALESC: implementadas em 2026-11-08 (`alesc-proposicoes`), pelo e-Legis paginado por iniciativa (`/proposicoes/processo-legislativo?iniciativa={slug}`), da 20ª legislatura a partir de 2023-02-01. Autoria e proponente vêm da listagem. O que é anterior fica fora. No site em 2026-11-09, 2.103 proposições em 40 deputados.
    - Despesas da ALERJ: em 2026-11-08, o adaptador passou a cobrir os 70 deputados em exercício quando o `remote_id` do DOCIGP é nulo, com mapa fixo 344→131, 514→135 e 515→136 e leitura do envelope `data` ou `rows`. No site em 2026-11-09, 31.062 lançamentos em 70 deputados.
    - Votações nominais da ALEPA: implementadas em 2026-11-10 (`alepa-votacoes`), pela lista mensal (`/Legislativo/VotacoesNominais?ano={ano}&mes={mes}`) e pelo detalhe de cada votação, em HTML por GET. Cada detalhe lista os 41 deputados em exercício na data, e quem não votou fica na seção NÃO VOTARAM. O deputado vem pelo nome; quatro grafias diferentes da do perfil ficam numa tabela fixa. Entram os blocos `votos` e `participacao_votacoes`. A coleta é no CI, sem semente, e o passo ganhou orçamento de 22 minutos para a primeira carga. No ensaio local de 2026-11-10, 7.044 votos de 198 votações, de 2022-03-22 a 2026-11-03, nos 41 deputados. No site desde a publicação de 2026-11-10 às 01:56 UTC, com os mesmos 7.044 votos e participação nos 41 perfis. A primeira carga levou 12 minutos no CI.
    - Votações nominais da ALESC: implementadas em 2026-11-10 (`alesc-votacoes`), pela lista de sessões plenárias do e-Legis, pela ordem do dia de cada sessão realizada e pelo extrato de cada votação nominal. O extrato só responde com o cabeçalho `X-Requested-With` e lista só quem votou: entra o bloco `votos`, sem participação. A coleta é no CI, sem semente, e o passo ganhou orçamento de 22 minutos para a primeira carga, que levou 15,6 minutos no ensaio local. No ensaio local de 2026-11-10, 5.674 votos de 226 votações, de 2023-03-21 a 2026-06-16, em 39 dos 40 deputados. No site desde a publicação de 2026-11-10 às 02:38 UTC, com os mesmos 5.674 votos em 39 perfis. A primeira carga levou 16 minutos no CI.
    - Presença da ALESC em sessão plenária: implementada em 2026-11-10 (`alesc-presenca`), pela página de presença de cada sessão ordinária no e-Legis, que lista os deputados como "Presente" ou "Ausência justificada". As duas situações entram no bloco `eventos`, e o site ganhou a coluna Presença; na contagem por tipo, a ausência justificada conta à parte. O schema de evento passou a aceitar `ausencia_justificada`. No ensaio local de 2026-11-10, 16.707 registros de 459 sessões (2023-02-02 a 2026-11-05) nos 40 deputados: 12.307 presenças e 4.400 ausências justificadas. No site desde a publicação de 2026-11-10 às 16:12 UTC, com os mesmos números.
    - Despesas e proposições da ALBA: implementadas em 2026-11-10. A verba indenizatória (`alba-despesas`) vem da lista anual do portal, 20 lançamentos por página, com nota, categoria e valor; o fornecedor fica no detalhe de cada lançamento, que não é coletado, e cada despesa aponta para ele. As proposições (`alba-proposicoes`) vêm da API pública do NoPaper, a mesma da ALES, com o autor pelo nome civil. Nas execuções do CI de 2026-11-10, a API do NoPaper respondeu 403 ao runner e a lista da verba ficou lenta (cerca de 9 s por página): a ALBA passou a entrar pela coleta local cifrada (ADR-0034, atualização de 2026-11-10). A primeira carga levou 55 minutos no ensaio local. No ensaio local de 2026-11-10, 17.426 lançamentos de 2022 a 2026 nos 63 deputados (R$ 130.228.729,08) e 9.750 autorias. As votações nominais não foram encontradas: as sessões publicam ata, ordem do dia, notas taquigráficas, áudio, vídeo e a presença em plenário, sem votação nominal com voto individual. No site desde a publicação de 2026-11-10 às 18:17 UTC, pela semente: 17.430 lançamentos (R$ 130,27 milhões) e 9.753 autorias nos 63 deputados.
    - Presença da ALBA em sessão plenária: implementada em 2026-11-10 (`alba-presenca`), pela lista de sessões filtrada pelo documento Presença em Plenário no NoPaper e pela página de presença de cada sessão. As situações "Presente", "Falta Justificada" e "Falta" entram no bloco `eventos` (`registrada`, `ausencia_justificada` e `falta`), e o site ganhou o rótulo Falta; na contagem por tipo, falta e ausência justificada contam à parte da presença registrada. O schema de evento passou a aceitar `falta`. A situação "Licenciado" (39 registros no total da legislatura) fica fora por indicar afastamento temporário do mandato e não comparecimento à sessão. No ensaio local de 2026-11-10, 27.972 registros de 481 sessões (2023-02-01 a 2026-11-09) nos 63 deputados: 21.917 presenças, 259 faltas justificadas e 5.796 faltas. Os registros de 9 nomes sem perfil em exercício ficam fora. No site desde a publicação de 2026-11-10 às 23:06 UTC, com os mesmos números.
    - ALMT (Mato Grosso): décimo quarto ente federativo integrado em 2026-11-11 (ADR-0037). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico (`almt-deputados`, `almt-deputados-detalhes` e `almt-mesa-diretora`). 24 deputados estaduais em exercício na 20ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais em alta resolução, links institucionais e composição da Mesa Diretora nas 4 sessões legislativas da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALEMS (Mato Grosso do Sul): décimo quinto ente federativo integrado em 2026-11-11 (ADR-0038), consolidando 100% de cobertura da Região Centro-Oeste (DF, GO, MT e MS), ao lado de Sul (100%) e Sudeste (100%). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico (`alems-deputados`, `alems-deputados-detalhes` e `alems-mesa-diretora`). 24 deputados estaduais em exercício na 12ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais em alta resolução, links institucionais e composição da Mesa Diretora da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALPB (Paraíba): décimo sexto ente federativo integrado em 2026-11-11 (ADR-0039), elevando a cobertura do Nordeste para 4 estados (BA, PE, CE e PB) e totalizando 16 unidades federativas no projeto. Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL 3 (`https://sapl3.al.pb.leg.br/api/`: `alpb-deputados`, `alpb-parlamentares`, `alpb-mesa-diretora`, `alpb-filiacao` e `alpb-portal`). 36 deputados estaduais em exercício na 20ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 36/36 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALEAM (Amazonas): décimo sétimo ente federativo integrado em 2026-11-11 (ADR-0040), ampliando a cobertura da Região Norte para os dois maiores estados (PA e AM) e alcançando 17 unidades federativas no projeto. Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL (`https://sapl.al.am.leg.br/api/`: `aleam-deputados`, `aleam-parlamentares`, `aleam-mesa-diretora`, `aleam-filiacao` e `aleam-portal`). 24 deputados estaduais em exercício na 20ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALEAL (Alagoas): décimo oitavo ente federativo integrado em 2026-11-11 (ADR-0041), assegurando maioria absoluta na Região Nordeste (5 de 9 estados, 55,5% de cobertura regional: BA, PE, CE, PB e AL) e totalizando 18 unidades federativas no projeto (mais de 66% do país coberto). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL (`https://sapl.al.al.leg.br/api/`: `aleal-deputados`, `aleal-parlamentares`, `aleal-mesa-diretora`, `aleal-filiacao` e `aleal-portal`). 27 deputados estaduais em exercício na 20ª Legislatura (2023–2027, id 35), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 27/27 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALERO (Rondônia): décimo nono ente federativo integrado em 2026-11-11 (ADR-0042), ampliando a cobertura da Região Norte para 3 estados (PA, AM e RO) e totalizando 19 unidades federativas no projeto (mais de 70% do país coberto). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL (`https://sapl.al.ro.leg.br/api/`: `alero-deputados`, `alero-parlamentares`, `alero-mesa-diretora`, `alero-filiacao` e `alero-portal`). 24 deputados estaduais em exercício na 11ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALEPI (Piauí): vigésimo ente federativo integrado em 2026-11-11 (ADR-0043), alcançando 6 de 9 estados na Região Nordeste (66,7% de cobertura regional: BA, PE, CE, PB, AL e PI) e totalizando o marco de 20 unidades federativas no projeto (quase 75% do território nacional coberto). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL (`https://sapl.al.pi.leg.br/api/`: `alepi-deputados`, `alepi-parlamentares`, `alepi-mesa-diretora`, `alepi-filiacao` e `alepi-portal`). 30 deputados estaduais em exercício na 20ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 30/30 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALRN (Rio Grande do Norte): vigésimo primeiro ente federativo integrado em 2026-11-11 (ADR-0044), alcançando 7 de 9 estados na Região Nordeste (77,8% de cobertura regional: BA, PE, CE, PB, AL, PI e RN) e totalizando 21 unidades federativas no projeto (mais de 77% do território nacional coberto). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via portal oficial da ALRN (`https://www.al.rn.leg.br/`: `alrn-deputados`, `alrn-parlamentares`, `alrn-mesa-diretora` e `alrn-portal`). 24 deputados estaduais em exercício na 63ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALETO (Tocantins): vigésimo segundo ente federativo integrado em 2026-11-11 (ADR-0046), ampliando a cobertura da Região Norte para 4 estados (PA, AM, RO e TO) e alcançando o total de 22 unidades federativas no projeto (mais de 81% do território nacional coberto). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL 3.1 (`https://sapl.al.to.leg.br/api/`: `aleto-deputados`, `aleto-parlamentares`, `aleto-mesa-diretora`, `aleto-filiacao`, `aleto-proposicoes` e `aleto-portal`). 24 deputados estaduais titulares da 10ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias, proposições legislativas e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALESE (Sergipe): vigésimo terceiro ente federativo integrado em 2026-11-11 (ADR-0047), alcançando 8 de 9 estados na Região Nordeste (88,9% de cobertura regional: BA, PE, CE, PB, AL, PI, RN e SE) e totalizando 23 unidades federativas no projeto (mais de 85% do território nacional coberto). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via portal oficial da ALESE (`https://al.se.leg.br/`: `alese-deputados`, `alese-parlamentares`, `alese-mesa-diretora` e `alese-portal`). 24 deputados estaduais em exercício na 20ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias e composição da Mesa Diretora da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna. Proposições e comissões implementadas em 2026-11-13 (`alese-proposicoes`), pela consulta de produção legislativa do sistema da casa (`consulta-producao.aspx`, por autor e tipo) e pela ficha do parlamentar. No ensaio local de 2026-11-13, 419 arquivos coletados em 16 minutos, sem falha: 27.800 proposições de 2022 a 2026 nos 24 deputados (todos com ao menos uma) e 329 cargos em órgãos e comissões. Nos 16 deputados sem produção anterior a 2022, o total publicado é igual à contagem que a ficha da fonte mostra. No mesmo dia a casa passou à coleta local cifrada (ADR-0034) e entrou no túnel (ADR-0029): ver "Limitações conhecidas". No site desde a publicação de 2026-11-13 das 13:04 UTC: 24 perfis, 27.801 proposições (todos os deputados com ao menos uma) e 329 cargos em órgãos e comissões.
    - ALEMA (Maranhão): vigésimo quarto ente federativo integrado em 2026-11-11 (ADR-0048), completando o marco histórico de 100% de cobertura federativa na Região Nordeste (todos os 9 estados integrados: BA, PE, CE, PB, AL, PI, RN, SE e MA) e totalizando 24 unidades federativas no projeto (quase 90% do território nacional coberto: 23 estados mais o Distrito Federal). Portal oficial da ALEMA (`https://www.al.ma.leg.br/`: `alema-deputados`, `alema-parlamentares`, `alema-mesa-diretora` e `alema-portal`). Em 2026-11-12 passou à coleta local cifrada (ADR-0034) e entrou no túnel (ADR-0029) após timeout TCP no runner dos EUA; coleta local validou 43 perfis normalizados. Cruzamento determinístico de deputados com candidaturas do TSE via nomes civis e de urna.
    - ALEAC (Acre): vigésimo quinto ente federativo integrado em 2026-11-11 (ADR-0049), ampliando a cobertura da Região Norte para 5 de 7 estados (71,4%: PA, AM, RO, TO e AC) e alcançando o marco de 25 unidades federativas no projeto (mais de 92% do território nacional coberto: 24 estados mais o Distrito Federal). Coleta aberta diretamente no CI do GitHub Actions sem bloqueio geográfico via API REST nativa do SAPL 3 (`https://sapl.al.ac.leg.br/api/`: `aleac-deputados`, `aleac-parlamentares`, `aleac-mesa-diretora`, `aleac-filiacao`, `aleac-proposicoes` e `aleac-portal`). 24 deputados estaduais titulares da 16ª Legislatura (2023–2027), com perfis biográficos, fotografias oficiais, mandatos, filiações partidárias, proposições legislativas e composição da Mesa Diretora nas sessões legislativas da legislatura. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna.
    - ALERR (Roraima): vigésimo sexto ente federativo integrado em 2026-11-11 (ADR-0050), ampliando a cobertura da Região Norte para 6 de 7 estados (85,7%: PA, AM, RO, TO, AC e RR) e totalizando 26 unidades federativas no projeto (mais de 96% do território nacional coberto: 25 estados mais o Distrito Federal). API REST nativa do SAPL 3 (`https://sapl.al.rr.leg.br/api/`: `alerr-deputados`, `alerr-parlamentares`, `alerr-mesa-diretora`, `alerr-filiacao`, `alerr-proposicoes` e `alerr-portal`). Em 2026-11-12 passou à coleta local cifrada (ADR-0034) após HTTP 403 no runner dos EUA; coleta local validou 25 perfis normalizados. Cruzamento determinístico de deputados com candidaturas do TSE via nomes civis e de urna.
    - ALAP (Amapá): vigésimo sétimo ente federativo integrado em 2026-11-11 (ADR-0051), completando o marco histórico definitivo de 100% DE COBERTURA FEDERATIVA NACIONAL (todos os 26 estados mais o Distrito Federal plenamente integrados no projeto Atuação Pública: 100% do território nacional coberto). Portal oficial da ALAP (`https://www.al.ap.leg.br/`: `alap-deputados`, `alap-parlamentares`, `alap-mesa-diretora`, `alap-ceap` e `alap-portal`). Em 2026-11-12 passou à coleta local cifrada (ADR-0034) e entrou no túnel (ADR-0029) após timeout TCP no runner dos EUA; coleta local validou 24 deputados estaduais em exercício normalizados. Cruzamento determinístico de 24/24 deputados com candidaturas do TSE via nomes civis e de urna. Despesas da CEAP implementadas em 2026-11-12 (`alap-ceap`), pelos endpoints PHP do portal de transparência (`mes_ceap_json.php`, `gabinete_ceap_json.php`, `pagina.php?pg=ceap&acao=buscar` e `ceap_exibir_detalhado.php`), de 2023-02 a 2026-09. No ensaio local de 2026-11-12, duas normalizações iguais: 6.013 lançamentos nos 24 deputados em exercício, R$ 39.368.932,56, com 5.662 CNPJ e 5.392 números de documento. Quatro gabinetes sem perfil em exercício ficaram de fora (CARLOS LOBATO - MDB, DELEGADO INÁCIO - PDT, KAKA BARBOSA - PL e PAULO NOGUEIRA - PT).
    - Túnel pelo Brasil (ADR-0029): `scripts/tunel-brasil.sh` e o checklist de manutenção passaram a encaminhar também `transparencia.al.rs.gov.br:443`, `www3.al.es.gov.br:443`, `ple.cl.df.gov.br:443`, `sapl.al.ac.leg.br:443`, `sapl.al.rr.leg.br:443`, `www.al.ma.leg.br:443` e `www.al.ap.leg.br:443`.
    - Correções de normalização e trava do TSE AP (2026-11-12): `Alrn`, `Alese`, `Alema` e `Alap` passaram a ler HTMLs compactados `.gz` via `Texto.lerArquivo`, com ensaio local ao vivo validando 24 perfis em ALRN, 24 em ALESE, 43 em ALEMA, 25 em ALERR e 24 em ALAP e testes unitários gravando `.html.gz`. Pendência aberta no `publish`: `consulta_cand_2026_AP.csv` do TSE foi publicado em 2026-11-12 com cargos proporcionais em `#NULO`, acionando a trava do ADR-0006 para `tse-30002530514`, `tse-30002536305` e `tse-30002536306`; o workflow `dados.yml` passou a repassar `vars.ETL_PERMITIR_REMOCAO` para liberação explícita quando confirmada ou até o TSE restabelecer os dados. Na execução de 2026-11-12 das 23:08 UTC, o `publish` passou com a variável vazia. Na publicação resultante (2026-11-13 das 00:19 UTC), os três identificadores não estão no índice nem nos aliases. Não validado: como saíram.

    | Casa | Despesas | Proposições com autor | Votações nominais |
    |---|---|---|---|
    | ALERJ | implementado em 2026-11-07; fallback dos 70 em exercício em 2026-11-08 | o sistema Domino recusa parâmetros de listagem | não encontrado |
    | ALECE | implementado em 2026-11-07 | implementado em 2026-11-08 | implementado em 2026-11-07 |
    | ALEGO | implementado em 2026-11-06 | API NoPaper, autor por nome | implementado em 2026-11-08 |
    | ALEP | bloqueado por reCAPTCHA | implementado em 2026-11-07 | implementado em 2026-11-07, pelo nome civil do TSE |
    | ALMG | implementado, pela coleta local cifrada | implementado em 2026-11-07, pela coleta local cifrada | só em texto livre na tramitação |
    | ALBA | implementado em 2026-11-10, pela lista anual do portal; fornecedor só no detalhe | implementado em 2026-11-10, pela API NoPaper, autor pelo nome civil | não encontrado em 2026-11-10: sessões só com documentos e presença (presença em alba-presenca) |
    | CLDF | testado em 2026-11-07 e não implementado: data ambígua nos arquivos do CKAN | implementado em 2026-11-07, pela coleta local cifrada | exige login |
    | ALEPE | implementado em 2026-11-07, pela coleta local cifrada | implementado em 2026-11-07, pela coleta local cifrada | só resultado agregado |
    | ALES | implementado em 2026-11-08, pela coleta local cifrada | implementado em 2026-11-07, pela coleta local cifrada | boletins em PDF |
    | ALRS | implementado em 2026-11-08, pela coleta local cifrada | implementado em 2026-11-08, pela coleta local cifrada | implementado em 2026-11-07, pela coleta local cifrada |
    | ALESC | implementado em 2026-11-06 | implementado em 2026-11-08 | implementado em 2026-11-10, pelo extrato de votação do e-Legis; lista só quem votou; no site desde 2026-11-10 |
    | ALEPA | painel interno de BI, sem fornecedor | callback de componente, paginação não validada | implementado em 2026-11-10, por HTML por GET: lista mensal (`/Legislativo/VotacoesNominais?ano={ano}&mes={mês}`, sem paginação) e detalhe por votação com as seções SIM, NÃO, ABSTENÇÃO e NÃO VOTARAM, nome e partido; 198 votações de 2022 a 2026, no site desde 2026-11-10 |
    | ALMT | não implementado | não implementado | não implementado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALEMS | não implementado | não implementado | não implementado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALPB | não implementado | implementado via SAPL (ADR-0045) | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALEAM | não implementado | implementado via SAPL (ADR-0045) | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALEAL | não implementado | implementado via SAPL (ADR-0045) | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALERO | não implementado | implementado via SAPL (ADR-0045) | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALEPI | não implementado | implementado via SAPL (ADR-0045) | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALRN | não implementado | não implementado | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALETO | não implementado | implementado via SAPL (ADR-0046) | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALESE | não implementado | não implementado | não encontrado (Mesa Diretora integrada em orgaos_e_frentes) |
    | ALAP | implementado em 2026-11-12, pela coleta local cifrada; CEAP por gabinete e subelemento desde 2023-02 | não implementado | não encontrado |

### Redes sociais

- Perfil verificado do político como fonte T1, só com snapshot arquivado.
- Rótulo obrigatório: "declaração do próprio político".
- Endereços de redes e sites declarados ao TSE (ADR-0031): concluído em 2026-11-06.
  - Fonte `tse-dados-abertos`: `rede_social_candidato_{ano}.zip` de 2018 a 2026. Só o endereço entra, sem conteúdo da página.
  - Cada candidatura publica `redes_declaradas`; o topo do perfil mostra os endereços da candidatura mais recente que declarou algum, como declaração do próprio candidato.
  - Ensaio local: 99.386 endereços nas candidaturas cobertas; 1.758 dos 1.833 perfis com candidatura mostram endereços. Ficaram fora 2.583 por telefone, e-mail ou CPF, 6.381 por não serem endereço e 463 além do limite de 20 por candidatura.
  - Testes: telefone e e-mail, esquema, texto que não é endereço e nome da rede (`TseTest`), exemplos de schema, fluxo e WCAG no E2E.

### Processos judiciais, sanções e condenações

Só entram depois de um ADR que defina:

- critérios de fonte oficial;
- status processual explícito em cada registro;
- regra de presunção de inocência na exibição.

### Fora do escopo até novo ADR

Contas de usuário, comentários, denúncias e análises automáticas.
