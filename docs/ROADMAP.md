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
- O tema do site segue o sistema. Não há alternância manual.
- O cabeçalho das tabelas não fica fixo na rolagem.
- A lista da página inicial exibe 48 cartões por vez. A busca do navegador (Ctrl+F) só acha o que está na tela. Use a busca do site.
- Não validado: leitor de tela real (VoiceOver, NVDA, TalkBack) e aparelhos móveis físicos. A auditoria de acessibilidade foi automatizada e por teclado.
- A auditoria de interface roda em scripts locais. Ela não está no CI.
- O site só vai ao ar pelo workflow `dados` (agendado ou manual). Mudança só de interface espera a próxima execução.

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
| E | Cartão de pagamento e viagens da Presidência (Portal da Transparência, ZIPs sem chave) | 4 a 5 h | concluída em 2026-11-02: 12 meses no mandato de 2019 a 2023 e 45 no vigente; cartão por unidade gestora, diárias, passagens e outros gastos |
| F | Agenda da Presidência: validação do padrão por dia e do arquivo de 2022; adaptador só se passar | 1 h, depois 4 a 6 h | concluída em 2026-11-02: 6.209 compromissos em 1.767 dias (372 sem agenda publicada); 723 de 2022 pelo Internet Archive |

Sem fonte publicada, fora do alvo: agenda do prefeito, presença em evento no Senado, verba de gabinete da câmara municipal.

## Interface do site (ADR-0012)

Concluída em 2026-11-02. A interface foi refeita sem mudar dado, rota nem fonte.

| Etapa | Entrega | Estado |
|---|---|---|
| Casca | Cabeçalho e rodapé novos, tema claro e escuro, anel de foco com contraste, foco movido ao conteúdo na troca de página, título de aba por perfil, rolagem restaurada ao voltar | concluída |
| Página inicial | Apresentação do site, busca por palavras, situação e esfera com contagem, filtros no endereço, "Mostrar mais", estados de carga e de erro | concluída |
| Estado das coletas | Página própria (`#/estado`) e resumo na página inicial | concluída |
| Perfil | Sumário por blocos com o bloco em leitura, endereço por bloco, blocos sem registro mais baixos, tabelas em cartões no celular, despesas por ano, link para o JSON do perfil | concluída |
| Detalhe anual | Migalhas, troca de ano, filtro em todos os campos e paginação | concluída |
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
2. Assembleias legislativas e governos estaduais: um adaptador por estado. O portal de dados abertos da ALESP (`https://www.al.sp.gov.br/dados-abertos/`) tem catálogo em HTML, ainda não validado.
3. Gastos e agenda de governadores a partir dos portais estaduais catalogados em `docs/FONTES.md`.
4. Gastos do Executivo federal (cartão de pagamento e viagens) pelo Portal da Transparência.

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
