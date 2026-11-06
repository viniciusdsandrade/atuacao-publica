# ADR-0022: Nono estado: ALEGO e Governo de Goiás

- Status: aceita
- Data: 2026-11-06

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais das diversas macrorregiões do país.
Após a consolidação integral da Região Sul (Paraná com ALEP, Rio Grande do Sul com ALRS e Santa Catarina com ALESC), do Sudeste (São Paulo com ALESP, Minas Gerais com ALMG e Rio de Janeiro com ALERJ) e do Nordeste (Bahia com ALBA e Ceará com ALECE), a inclusão de Goiás (GO) marca a **estreia do projeto na Região Centro-Oeste** e expande o catálogo para nove unidades da federação.
O levantamento técnico das fontes oficiais de Goiás validou:
1. A Assembleia Legislativa do Estado de Goiás (ALEGO) disponibiliza portal institucional moderno e aberto (`https://portal.al.go.leg.br/`), servido via HTTPS na porta 443 padrão, sem exigência de autenticação e com tempos de resposta rápidos.
2. A listagem de parlamentares em exercício na 20ª Legislatura (2023–2027) é servida em `https://portal.al.go.leg.br/deputados/em-exercicio`, contendo os deputados estaduais em tabela semântica com identificadores numéricos oficiais de perfil (`/deputados/perfil/{id}`), siglas partidárias e nomes parlamentares.
3. As páginas de perfil individual (`/deputados/perfil/{id}`) e perfil biográfico (`/deputados/perfil-biografico/{id}`) trazem fotografias oficiais de alta resolução no repositório institucional (`https://saba.al.go.leg.br/`), nome civil explícito, datas e municípios de nascimento, histórico partidário e biografia oficial.
4. As composições de comissões permanentes e frentes parlamentares são servidas em `https://portal.al.go.leg.br/comissoes/{id}`, contendo presidente, vice-presidente, titulares e suplentes correlacionados diretamente pelos links de perfil dos deputados.
5. No Poder Executivo estadual, o Governador Ronaldo Caiado (UNIÃO) exerce mandato consecutivo até 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`go-transparencia`, `https://www.transparencia.go.gov.br/`) e Diário Oficial (`go-diario-oficial`, `https://diariooficial.abc.go.gov.br/`).

## Decisão

1. Expansão interestadual: inclusão de Goiás (UF: `GO`, ente: `GO`) na esfera estadual do projeto, inaugurando a presença na Região Centro-Oeste.
2. Identificador canônico do deputado estadual da ALEGO: `alego-{id}`, onde `{id}` é o identificador numérico oficial utilizado nas rotas da Assembleia (ex.: `alego-808`, `alego-51`, `alego-137`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alego` em `schemas/politico.schema.json` com padrão `^[0-9]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alego-portal`, `alego-deputados` e `alego-deputados-detalhes` em `contrib/fontes.yaml` (`go-transparencia` e `go-diario-oficial` adicionados para o Executivo goiano).
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, e-mails institucionais, números de gabinete e menções a parentes (filhos, cônjuges, filiação) são estritamente descartados durante a extração e normalização, em cumprimento rigoroso a `AGENTS.md` e verificação com `Guarda`.
   - Nomes civis, nomes de urna e dados biográficos são utilizados para vinculação em memória com as bases de candidaturas do TSE.
7. Vínculo determinístico com o TSE:
   - Inclusão de Goiás (`GO`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018, 2022 e 2026.
   - Vinculação por nome civil e nome de urna com as bases eleitorais do TSE.
   - Parlamentares da ALEGO com mandatos anteriores na Câmara Federal ou Senado são fundidos deterministicamente no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALEGO (`Alego.java`):
   - Coleta (`fetch`): armazena em `data/raw/alego/` a listagem `deputados.html`, perfis individuais `perfis/{id}.html`, perfis biográficos `biografias/{id}.html` e comissões `comissoes/{id}.html`.
   - Normalização (`normalize`): gera `data/normalizado/alego/pessoas.json` e `blocos/{id}.json` populando mandatos, fotos oficiais, comissões em `orgaos_e_frentes` e links oficiais.
9. Executivo estadual:
   - O Governador de Goiás é vinculado ao catálogo de portais oficiais (`go-transparencia` e `go-diario-oficial`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir nove estados brasileiros (SP, MG, PR, BA, RJ, RS, CE, SC e GO), expandindo seu alcance para quatro das cinco macrorregiões do país (Sul, Sudeste, Nordeste e Centro-Oeste).
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
