# Catálogo de fontes

Este documento lista as fontes usadas ou avaliadas pelo projeto.
O catálogo legível por máquina fica em `contrib/fontes.yaml`.
Os dois arquivos mudam juntos.

## Como ler

- **Nível T0**: fonte primária oficial do Estado.
- **Nível T1**: primeira parte (site do mandato, do partido). Sempre rotulada como "declaração do próprio político".
- **Nível T2**: facilitador com metodologia aberta e rastreável a uma fonte T0. O registro publicado aponta para a URL T0.
- **Status**:
  - `validado`: chamada real com resposta e formato esperados;
  - `validado com limitação`: responde, mas com restrição descrita;
  - `bloqueado`: o servidor recusa o cliente do projeto. Não contornamos bloqueios;
  - `não validado`: sem chamada bem-sucedida até agora.
- Datas de validação em UTC.
- User-Agent usado: `atuacao-publica/0.1 (validacao de fontes)`.

## Resumo das fontes federais

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `camara-api` | Câmara dos Deputados, API v2 | T0 | JSON | nenhuma | validado com limitação | 2026-10-30 |
| `camara-arquivos` | Câmara dos Deputados, arquivos anuais | T0 | JSON (gzip) | nenhuma | validado | 2026-10-30 |
| `camara-cotas` | Câmara dos Deputados, cota parlamentar (CEAP) | T0 | JSON em ZIP | nenhuma | validado | 2026-10-30 |
| `senado-api` | Senado Federal, dados abertos | T0 | JSON (via `Accept`) | nenhuma | validado | 2026-10-30 |
| `senado-ceaps` | Senado Federal, CEAPS | T0 | CSV latin-1 | nenhuma | validado | 2026-10-30 |
| `tse-dados-abertos` | TSE, portal de dados abertos (CKAN) | T0 | CSV latin-1 em ZIP | nenhuma | validado | 2026-10-30 |
| `tse-cdn` | TSE, arquivos dos dados abertos (CDN) | T0 | CSV latin-1 e PDF em ZIP | nenhuma | validado | 2026-10-30 |
| `tse-divulgacand` | TSE, DivulgaCandContas | T0 | JSON | nenhuma | bloqueado | 2026-10-30 |
| `cgu-portal-api` | CGU, API do Portal da Transparência | T0 | JSON | chave `chave-api-dados` | validado com limitação | 2026-10-30 |
| `cgu-portal-arquivos` | CGU, downloads do Portal da Transparência | T0 | CSV em ZIP | nenhuma | validado | 2026-11-06 |
| `cgu-eagendas` | CGU, e-Agendas (dados abertos) | T0 | CSV em ZIP | nenhuma | validado com limitação | 2026-10-30 |
| `planalto-agenda` | Presidência, agenda oficial | T0 | HTML | nenhuma | validado com limitação | 2026-10-30 |
| `planalto-legislacao` | Presidência, legislação | T0 | HTML | nenhuma | não validado | 2026-10-30 |
| `dou` | Imprensa Nacional, Diário Oficial da União | T0 | HTML | nenhuma | validado com limitação | 2026-10-30 |
| `normas-leg` | Portal Normas (LexML) | T0 | HTML | nenhuma | validado | 2026-10-30 |
| `t1-site-mandato` | Site oficial do mandato ou do gabinete (genérica) | T1 | HTML | nenhuma | validado com limitação | 2026-10-30 |
| `t1-site-partido` | Site oficial do partido (genérica) | T1 | HTML | nenhuma | validado com limitação | 2026-10-30 |
| `base-dos-dados` | Base dos Dados | T2 | SQL e CSV | conta para consultas | validado com limitação | 2026-10-30 |
| `querido-diario` | Querido Diário (OKBR) | T2 | JSON | nenhuma | não validado | 2026-10-30 |
| `serenata` | Operação Serenata de Amor | T2 | código e CSV | nenhuma | validado com limitação | 2026-10-30 |

## Detalhe das fontes federais

### `camara-api`: Câmara dos Deputados, API v2

- URL base: `https://dadosabertos.camara.leg.br/api/v2`
- Endpoints validados com resposta 200:
  - `/deputados`: 513 deputados em exercício numa chamada com `itens=1000`.
  - `/deputados?idLegislatura={n}&itens=1000`: todos os que exerceram mandato na legislatura. Na 57ª, 882 registros de 648 pessoas: a pessoa se repete a cada troca de partido (validado em 2026-10-31).
  - `/legislaturas/{id}`: início e fim de uma legislatura. Responde objeto, e não lista como `/legislaturas?data=`.
  - `/deputados/{id}`: traz `cpf`, `dataNascimento`, `redeSocial`, `urlWebsite` e gabinete com telefone e e-mail.
  - `/deputados/{id}/historico`: mudanças de situação (`Exercício`, `FIM_MANDATO` e outras) com `dataHora` e `idLegislatura`.
  - `/deputados/{id}/discursos`: `sumario` oficial, `tipoDiscurso`, `dataHoraInicio`, `urlTexto` (pode vir `null`).
  - `/deputados/{id}/eventos`, `/deputados/{id}/frentes`, `/deputados/{id}/orgaos`, `/deputados/{id}/ocupacoes`.
  - `/votacoes/{id}/votos`, `/votacoes/{id}/orientacoes`, `/proposicoes/{id}/temas`, `/eventos/{id}/deputados`.
  - `/legislaturas/57`: início em 2023-02-01 e fim em 2027-01-31.
- Limitações e armadilhas:
  - `/deputados/{id}/despesas` responde 200 com lista vazia para todos os deputados testados, em 2025 e 2026. Usamos `camara-cotas`.
  - Não há endpoint de presença em sessão de plenário. Publicamos "participação em votações nominais". Nunca chamamos isso de presença.
  - Presença em evento (reunião de comissão, sessão) vem de `/eventos/{id}/deputados` e do arquivo `eventosPresencaDeputados`. A lista traz só quem esteve presente. Não traz motivo de ausência.
  - `/frentes` devolve todas as legislaturas. Filtramos as legislaturas cobertas, a partir da 57ª.
  - Em 2026-10-30, `/deputados/{id}/frentes` respondeu 504 depois de 15 segundos, de forma repetida. O ETL usou a cópia da coleta anterior (ADR-0005).
  - A partir do runner do GitHub Actions, a API respondeu 504 e recusou conexão em escala nas execuções de 2026-11-01 e 2026-11-02 (326 e 212 respostas 504, 173 timeouts de conexão), enquanto respondia normalmente de outra rede. A coleta da Câmara no CI depende do cache de dados brutos e do orçamento de tempo (`ETL_ORCAMENTO_MINUTOS`).
  - O `sumario` de discurso traz entidades HTML (`&nbsp;`).
  - Dados descartados na ingestão: `cpf`, `dataNascimento`, e-mail, telefone e gabinete.
  - Redes sociais ficam para a fase 2.

### `camara-arquivos`: arquivos anuais da Câmara

- URL base: `https://dadosabertos.camara.leg.br/arquivos`
- O servidor aceita `Accept-Encoding: gzip`. O arquivo de orientações de 2026 cai de 749 KB para 19 KB.
- Os arquivos são regerados todo dia (cabeçalho `Last-Modified`).
- Arquivos validados para 2026, com tamanho sem compressão:

| Arquivo | Tamanho | Registros em 2026 |
|---|---|---|
| `votacoes/json/votacoes-{ano}.json` | 8,5 MB | 7.581 |
| `votacoesVotos/json/votacoesVotos-{ano}.json` | 32,6 MB | 53.126 |
| `votacoesOrientacoes/json/votacoesOrientacoes-{ano}.json` | 0,7 MB | 1.904 |
| `votacoesObjetos/json/votacoesObjetos-{ano}.json` | 21,8 MB | 32.649 |
| `proposicoes/json/proposicoes-{ano}.json` | 74,2 MB | não contado |
| `proposicoesAutores/json/proposicoesAutores-{ano}.json` | 31,6 MB | 59.534 |
| `proposicoesTemas/json/proposicoesTemas-{ano}.json` | 6,5 MB | 25.788 |
| `eventos/json/eventos-{ano}.json` | 1,6 MB | 1.608 |
| `eventosOrgaos/json/eventosOrgaos-{ano}.json` | não medido | 1.608 |
| `eventosPresencaDeputados/json/eventosPresencaDeputados-{ano}.json` | 16,8 MB | 61.781 |
| `orgaosDeputados/json/orgaosDeputados-L57.json` | 7,8 MB | 13.334 |
| `frentesDeputados/json/frentesDeputados.json` | 157,7 MB | todas as legislaturas |

- Observações:
  - Em 2026, 155 votações tiveram voto nominal, 126 delas no Plenário (`siglaOrgao = PLEN`).
  - Valores de `voto`: `Sim`, `Não`, `Abstenção`, `Obstrução`, `Artigo 17` e vazio (466 registros em 2026).
  - `siglaBancada` mistura partido (`PL`), federação (`Fdr PT-PCdoB-PV`), bloco truncado (`Bl UniPpPsd...`), `Governo`, `Maioria`, `Minoria` e `Oposição`. Há rótulos truncados (`Solidaried`).
  - `orgaosDeputados` e `proposicoesTemas` identificam deputado e proposição só pela URI.
  - `frentesDeputados` é grande. Usamos `/deputados/{id}/frentes` da `camara-api`.

### `camara-cotas`: cota parlamentar (CEAP)

- URL: `https://www.camara.leg.br/cotas/Ano-{ano}.json.zip`
- Tamanho: cerca de 7,8 MB por ano (ZIP). O JSON de 2026 tem 130 MB e 119.543 registros.
- Atualização diária (`Last-Modified` às 06:24 UTC em 2026-10-29).
- Campos com dado pessoal, descartados na ingestão: `cpf` (do deputado), `passageiro`, `cnpjCPF` quando tem 11 dígitos (1.271 registros em 2026).
- O campo `fornecedor` pode conter CPF no nome de MEI (376 registros em 2026). A normalização remove a sequência de 11 dígitos.
- 555 registros de 2026 não têm `idDeputado`. São gastos de liderança partidária. Ficam fora dos perfis.
- `cnpjCPF` vem formatado. Guardamos só os 14 dígitos de CNPJ.
- Valores (`valorLiquido`, `valorDocumento`, `valorGlosa`) vêm como texto com ponto decimal.
- Escolhemos o JSON, e não o CSV: projetos de referência relatam aspas sem escape no CSV.

### `senado-api`: Senado Federal, dados abertos

- URL base: `https://legis.senado.leg.br/dadosabertos`
- Formato: XML por padrão. JSON com `Accept: application/json`.
- Documentação OpenAPI: `/dadosabertos/v3/api-docs` (versão 4.1.3.108, 157 caminhos).
- Endpoints validados com resposta 200:
  - `/senador/lista/atual`: 81 senadores com mandato, legislaturas e exercícios.
  - `/senador/lista/legislatura/{numero}`: titulares e suplentes da legislatura, com os períodos de exercício (`Exercicios`: início, fim e causa do afastamento). Na 57ª, 246 mandatos; 107 pessoas exerceram mandato desde 2023-02-01 (validado em 2026-10-31).
  - `/senador/{codigo}`, `/senador/{codigo}/mandatos`, `/senador/{codigo}/discursos`, `/senador/{codigo}/comissoes`, `/senador/{codigo}/licencas`, `/senador/{codigo}/cargos`.
  - `/votacao?dataInicio=AAAA-MM-DD&dataFim=AAAA-MM-DD`: array na raiz, com `votos[]` embutido (81 votos por votação).
  - `/processo?codigoParlamentarAutor={codigo}`: todos os processos do autor, com `identificacao`, `ementa`, `situacaoAtual`, `dataApresentacao`, `urlDocumento`. O ETL filtra pela data de apresentação.
  - `/plenario/votacao/orientacaoBancada/{AAAAMMDD}/{AAAAMMDD}`: orientação por liderança. Liga com `/votacao` por `sequencialVotacao`.
  - `/legislacao/lista?tipo={LEI|DEC|MPV|LCP}&ano={ano}`: normas federais. Em 2026: 204 leis, 332 decretos, 66 medidas provisórias, 14 leis complementares.
  - `/legislacao/{id}`: detalhe da norma, com link para `normas.leg.br`.
  - `/materia/vetos/{ano}`: redireciona para `/dados/ListaVetosAnoCN{ano}.json`.
- Limitações e armadilhas:
  - Marcados como obsoletos na OpenAPI: `/senador/{codigo}/votacoes`, `/senador/{codigo}/autorias`, `/plenario/lista/votacao/...`, `/materia/votacoes/{codigo}`. Não usamos.
  - Elemento único vem como objeto, e não como array. O ETL normaliza.
  - `/votacao` aceita no máximo 1 ano por consulta (relato de projeto de referência). Consultamos por mês.
  - Códigos de voto vistos: `Sim`, `Não`, `Abstenção`, `Votou` (votação secreta), `Presidente (art. 51 RISF)`, `P-NRV`, `AP`, `LS`, `NCom`. A documentação de referência cita também `MIS`, `LP`, `LAP`.
  - A orientação usa nomes de partido diferentes da sigla do senador (`Progressistas`, `Republicanos`).
  - `/processo` não traz temas na listagem.
  - O filtro `tramitouLegislaturaAtual=S` perde autorias: para um senador testado em 2026-10-31, 284 desde 2023-02-01 com o filtro e 298 sem ele. Numa legislatura nova, perderia também as da anterior. Não usamos o filtro.
  - A lista por legislatura traz suplente que nunca assumiu. O ETL só inclui quem tem período de exercício no intervalo coberto.
  - Dados descartados: e-mail, telefones, endereço, data de nascimento.

### `senado-ceaps`: Senado Federal, CEAPS

- URL: `https://www.senado.leg.br/transparencia/LAI/verba/despesa_ceaps_{ano}.csv`
- Validado para 2023, 2024, 2025 e 2026 (3,8 MB a 5,8 MB).
- Codificação ISO-8859-1. Separador `;`. Campos entre aspas. Fim de linha CRLF.
- A primeira linha é metadado (`"ULTIMA ATUALIZACAO";"29/10/2026 02:02"`). A segunda é o cabeçalho.
- Cabeçalho: `ANO;MES;SENADOR;TIPO_DESPESA;CNPJ_CPF;FORNECEDOR;DOCUMENTO;DATA;DETALHAMENTO;VALOR_REEMBOLSADO;COD_DOCUMENTO`.
- Decimal com vírgula. Data `dd/mm/aaaa`.
- O senador vem só pelo nome, em maiúsculas. A junção usa nome normalizado sem acentos.
- O arquivo de 2026 traz 90 nomes distintos (titulares e suplentes que exerceram mandato).
- Não há URL de documento fiscal publicada pela fonte.

### `tse-dados-abertos`: TSE, dados abertos

- Portal CKAN: `https://dadosabertos.tse.jus.br`. API: `/api/3/action/package_show?id=candidatos-{ano}`.
- Licença declarada: Creative Commons Attribution (`cc-by`).
- Arquivos validados (CDN `https://cdn.tse.jus.br/estatistica/sead/odsele`):

| Arquivo | Tamanho (ZIP) |
|---|---|
| `consulta_cand/consulta_cand_2018.zip` | 4,7 MB |
| `consulta_cand/consulta_cand_2022.zip` | 4,4 MB |
| `consulta_cand/consulta_cand_2026.zip` | 3,3 MB |
| `bem_candidato/bem_candidato_2018.zip` | 5,1 MB |
| `bem_candidato/bem_candidato_2022.zip` | 5,4 MB |
| `bem_candidato/bem_candidato_2026.zip` | 3,8 MB |
| `proposta_governo/proposta_governo_{ano}_{UF}.zip` | 8 MB a 18 MB por UF |
| `consulta_cand/rede_social_candidato_{ano}.zip` | 0,6 MB (2018) a 20,2 MB (2024) |
| `prestacao_contas/prestacao_de_contas_eleitorais_candidatos_{ano}.zip` | 306 MB (2018) a 1,33 GB (2024), lido por Range |

- Cada ZIP de candidatos traz um CSV por UF, `BR` (Presidência) e `BRASIL` (todos).
- Codificação ISO-8859-1. Separador `;`. Campos de texto entre aspas e campos numéricos sem aspas.
- O arquivo de 2026 já traz o resultado do 1º e do 2º turno em `DS_SIT_TOT_TURNO`: 1 presidente eleito, 27 governadores, 54 senadores, 513 deputados federais (385 por QP e 128 por média).
- `NR_CPF_CANDIDATO`, `NR_TITULO_ELEITORAL_CANDIDATO` e `DS_EMAIL` vêm preenchidos. São descartados na ingestão. O CPF e o título servem só de chave de vínculo em memória.
- No arquivo de 2024, `NR_CPF_CANDIDATO` vem mascarado (`-4`) nas 463.885 linhas. `NR_TITULO_ELEITORAL_CANDIDATO` vem completo, com 12 dígitos, em 463.839 delas (verificado em 2026-11-06).
- O 2º turno aparece como uma segunda linha do candidato (`NR_TURNO = 2`).
- Bens: `SQ_CANDIDATO` e `VR_BEM_CANDIDATO` com vírgula decimal. `DS_BEM_CANDIDATO` pode descrever endereço. Publicamos só o total.
- Propostas de governo: PDFs dentro do ZIP da UF, com nome `{UF}/{ANO}{UF}{SQ_CANDIDATO}.pdf`.
- Redes e sites declarados (ADR-0031): `rede_social_candidato_{ano}.zip`, validado em 2026-11-06 para 2018 a 2026. Um CSV por UF e `_BRASIL`. Colunas usadas: `SQ_CANDIDATO`, `NR_ORDEM_REDE_SOCIAL` e `DS_URL`.
- No arquivo de 2026, a maior parte dos endereços vem toda em maiúsculas (35.244 dos cerca de 55 mil com esquema), e milhares de entradas não são endereço: texto com espaço, "@perfil" solto, palavra sem ponto. De 2018 a 2024, o campo vem quase só com URLs.
- Parte dos endereços leva a telefone: conversa, link curto e convite de grupo do WhatsApp, Telegram por número. Esses ficam fora na ingestão.
- Prestação de contas (ADR-0032): validada em 2026-11-06 para 2018 a 2026. O ZIP de 2022 mudou em 2026-11-01, e os de 2018 e 2020 mudaram de tamanho desde setembro: a fonte republica anos fechados.
- Cada ZIP traz, por UF e `_BRASIL`, `receitas_candidatos`, `receitas_candidatos_doador_originario`, `despesas_contratadas_candidatos` e `despesas_pagas_candidatos`. Layout igual de 2018 a 2026: 60 colunas nas receitas e 53 nas despesas contratadas.
- A CDN aceita Range (`Accept-Ranges: bytes`). O ETL lê só receitas e despesas contratadas: `_BRASIL` na eleição geral e `_SP` na municipal. Na primeira carga, cerca de 530 MB das entradas, contra cerca de 3,8 GB dos ZIPs.
- Colunas usadas: `SQ_CANDIDATO`, `SQ_RECEITA`, `DS_FONTE_RECEITA`, `DS_ORIGEM_RECEITA` e `VR_RECEITA`; `SQ_DESPESA`, `DS_ORIGEM_DESPESA`, `NR_CPF_CNPJ_FORNECEDOR`, `NM_FORNECEDOR`, `NM_FORNECEDOR_RFB` e `VR_DESPESA_CONTRATADA`.
- As linhas trazem nome e CPF de doador e de fornecedor pessoa física, CPF do candidato e do vice, gênero e cor ou raça do candidato. Nenhum desses campos entra no cache nem no site.

### `tse-divulgacand`: DivulgaCandContas

- URL: `https://divulgacandcontas.tse.jus.br`
- A página e a API REST respondem 403 ao User-Agent do projeto. Não contornamos.
- O projeto usa os arquivos de `tse-dados-abertos` no lugar desta fonte.

### `cgu-portal-api` e `cgu-portal-arquivos`: Portal da Transparência

- API: `https://api.portaldatransparencia.gov.br/api-de-dados`. Chamada sem chave responde 401.
- A chave é gratuita, obtida com conta gov.br em `/api-de-dados/cadastrar-email`, e vai no cabeçalho `chave-api-dados`.
- Limites documentados: 400 requisições por minuto, 700 entre 0h e 6h.
- A documentação (`/v3/api-docs`, sem chave) lista `/api-de-dados/cartoes`, `/api-de-dados/viagens`, `/api-de-dados/viagens/{id}` e `/api-de-dados/viagens-por-cpf`.
- Downloads sem chave: `https://portaldatransparencia.gov.br/download-de-dados/cpgf/{AAAAMM}` e `/viagens/{ano}`. Redirecionam (302) para ZIP em `dadosabertos-download.cgu.gov.br`.
- Uso no projeto (ADR-0010): bloco `despesas_executivo` do presidente, com o órgão 20101 (Presidência da República) somado por mês e categoria.
- `cpgf/{AAAAMM}`: ZIP de cerca de 300 KB com um CSV (latin-1, ponto e vírgula): órgão superior, órgão, unidade gestora, ano e mês do extrato, CPF mascarado e nome do portador, CNPJ ou CPF e nome do favorecido, transação, data e valor. Na Presidência, em junho de 2025, 1.027 de 1.141 transações vieram como "Informações protegidas por sigilo", com valor e sem data.
- `viagens/{ano}`: ZIP de cerca de 160 MB com `{ano}_Viagem.csv` (814 mil linhas em 2025), `_Passagem`, `_Trecho` e `_Pagamento`. A viagem traz órgão superior e solicitante, CPF mascarado e nome do viajante, cargo, período, destinos, motivo e valores de diárias, passagens, devolução e outros gastos. O nome do ZIP muda a cada geração (`2025_20260920_Viagens.zip`), e a URL de download não.
- Velocidade variável: o mesmo ZIP levou mais de 10 minutos numa tentativa e 8 segundos em outra, em 2026-11-02. O mesmo GET às vezes responde 405 e logo depois 200; o cliente HTTP repete a chamada nesse caso.
- `emendas-parlamentares/UNICO` (ADR-0030): redireciona (302) para `dadosabertos-download.cgu.gov.br/PortalDaTransparencia/saida/emendas-parlamentares/EmendasParlamentares.zip`. Validado em 2026-11-06: 32,5 MB, regerado a cada dia, com `Accept-Ranges`.
- O ZIP traz `EmendasParlamentares.csv` (94.827 linhas, de 2014 a 2026), `EmendasParlamentares_PorFavorecido.csv` (828.756 linhas) e `EmendasParlamentares_Convenios.csv`.
- Codificação ISO-8859-1, separador `;`, campos entre aspas, fim de linha CRLF. Decimal com vírgula.
- Colunas usadas: `Código da Emenda`, `Ano da Emenda`, `Tipo de Emenda`, `Código do Autor da Emenda`, `Nome do Autor da Emenda`, `Número da emenda`, `Localidade de aplicação do recurso`, `Nome Função`, `Nome Subfunção`, `Nome Programa`, `Nome Ação` e os seis valores (`Valor Empenhado`, `Valor Liquidado`, `Valor Pago`, `Valor Restos A Pagar Inscritos`, `Valor Restos A Pagar Cancelados`, `Valor Restos A Pagar Pagos`).
- Tipos: individual com finalidade definida (80.049 linhas), individual por transferência especial (5.108), de relator (3.892), de bancada (3.588) e de comissão (2.190).
- Desde 2022, toda emenda individual tem código de emenda e código de autor. Linhas anteriores trazem "Sem informação" no código.
- O autor vem só por código e nome. O nome muda de grafia entre linhas do mesmo código, e a emenda de ex-parlamentar indicada por outro aparece como "NOVO (EX-PARLAMENTAR ANTIGO, NOS TERMOS ...)".
- No arquivo de favorecidos, a pessoa física vem com nome e documento mascarado (181.588 linhas). Esse arquivo não é lido.

### `cgu-eagendas`: e-Agendas

- Arquivo: `https://dadosabertos-download.cgu.gov.br/dados_e-agendas/dados_e-agendas.zip` (205 MB, 48 CSVs mensais, atualização mensal).
- CSV em UTF-8, separador vírgula.
- Limitação: setembro de 2026 não tem nenhum registro com cargo "Presidente da República". A agenda presidencial não está nesta base.

### `planalto-agenda`: agenda do Presidente da República

- URL: `https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica`, que redireciona para a pasta do mandato vigente (`agenda-do-presidente-da-republica-lula/agenda-do-presidente-da-republica`).
- Só HTML, uma página por dia: `{base}/{AAAA-MM-DD}` (200 com agenda; 404 sem agenda publicada). A semana corrente aparece na página principal com `data-day`. Não há API.
- Estrutura por compromisso: `item-compromisso`, `compromisso-inicio` (`09h30`), `compromisso-fim` opcional, `compromisso-titulo`, `compromisso-participantes` (lista), `compromisso-local` e link `vcal_view`. Igual em 2022 e 2026.
- Mandato de 2019 a 2023: as páginas saíram do ar; o Internet Archive tem capturas por dia (`web.archive.org/web/{AAAAMMDD}120000id_/{url}`), conferido em 2026-11-02. O archive.org responde 429 a chamadas rápidas: a coleta espera 4 s entre chamadas.
- Decisão revista pelo ADR-0011: coleta automática da página de cada dia, com o endereço base na regra de mandato. Contribuição manual com snapshot continua aceita.

### `planalto-legislacao`: legislação no Planalto

- URL: `https://www.planalto.gov.br/ccivil_03/`
- A conexão foi encerrada pelo servidor (`Connection reset by peer`) a partir do ambiente de validação.
- O texto arquivado no Wayback Machine continua legível. Exemplo usado: EC 111/2021, snapshot de 2026-10-23.
- Os atos normativos da POC vêm de `senado-api` (`/legislacao` e `/materia/vetos`).

### `dou`: Diário Oficial da União

- URL: `https://www.in.gov.br/leiturajornal` (HTML, 200).
- Fora da POC. A página de dados abertos da Imprensa Nacional responde 200 e não foi avaliada a fundo.

### `normas-leg`: Portal Normas

- URL: `https://normas.leg.br/?urn={urn}`. Responde 200.
- Serve de link humano para o texto das normas listadas pela `senado-api`.

### Fontes T1 genéricas

- `t1-site-mandato`: site oficial do mandato ou do gabinete. Referência para conferir o domínio: perfil do parlamentar na casa legislativa (`https://www.camara.leg.br/deputados/quem-sao`, 200).
- `t1-site-partido`: site oficial do partido. Referência: lista de partidos do TSE (`https://www.tse.jus.br/partidos/partidos-registrados-no-tse`), que responde 403 ao User-Agent do projeto. A revisão confere no navegador.
- Registro de fonte T1 sempre aparece com o rótulo "declaração do próprio político".
- A URL de origem não precisa estar no domínio de `url_base` (`dominio_livre: true`). A revisão humana confere o domínio.

### Fontes T2

- `base-dos-dados` (`https://basedosdados.org`): responde 200. Consultas exigem conta. Não usada na POC.
- `querido-diario` (`https://api.queridodiario.ok.org.br`): falha de handshake TLS com o cliente local (LibreSSL 3.3.6). O site responde 302. Revalidar com outro cliente.
- `serenata` (`https://github.com/okfn-brasil/serenata-de-amor`): repositório ativo, último push em 2024-01-31, sem atualização frequente. Serve de referência de método.

## Governadores: portais estaduais

Na POC, o perfil de governador vem do TSE e traz dois links oficiais da UF: o portal da transparência e o diário oficial.
Gastos e agenda por estado ficam para a fase 2, com um adaptador por estado.
Todas as 54 URLs responderam ao User-Agent do projeto em 2026-10-30.
Nenhum site bloqueou o cliente.

| UF | id | Tipo | Nome | URL | HTTP | Observação |
|---|---|---|---|---|---|---|
| AC | `ac-diario-oficial` | diário oficial | Diário Oficial do Estado do Acre | https://diario.ac.gov.br/ | 200 |  |
| AC | `ac-transparencia` | transparência | Portal de Transparência do Estado do Acre | https://transparencia.ac.gov.br/ | 200 |  |
| AL | `al-diario-oficial` | diário oficial | Diário Oficial do Estado de Alagoas | https://diario.imprensaoficial.al.gov.br/ | 200 | aplicação exige JavaScript |
| AL | `al-transparencia` | transparência | Portal da Transparência de Alagoas | https://transparencia.al.gov.br/ | 200 |  |
| AM | `am-diario-oficial` | diário oficial | Diário Oficial do Estado do Amazonas | https://diario.imprensaoficial.am.gov.br/ | 200 |  |
| AM | `am-transparencia` | transparência | Portal da Transparência do Governo do Estado do Amazonas | https://www.transparencia.am.gov.br/ | 200 |  |
| AP | `ap-diario-oficial` | diário oficial | Diário Oficial do Estado do Amapá | https://diofe.portal.ap.gov.br/ | 200 | endereço antigo diariooficial.ap.gov.br não resolve DNS |
| AP | `ap-transparencia` | transparência | Portal da Transparência do Governo do Estado do Amapá | https://www.transparencia.ap.gov.br/ | 200 |  |
| BA | `ba-diario-oficial` | diário oficial | Diário Oficial do Estado da Bahia | https://dool.egba.ba.gov.br/ | 200 |  |
| BA | `ba-transparencia` | transparência | Transparência Bahia | https://www.transparencia.ba.gov.br/ | 200 |  |
| CE | `ce-diario-oficial` | diário oficial | Diário Oficial do Estado do Ceará | https://www.ce.gov.br/diario-oficial/ | 200 | página do DOE no portal do governo |
| CE | `ce-transparencia` | transparência | Ceará Transparente | https://cearatransparente.ce.gov.br/ | 200 | transparencia.ce.gov.br respondeu 502; URL linkada por ce.gov.br |
| DF | `df-diario-oficial` | diário oficial | Diário Oficial do Distrito Federal (DODF) | https://www.dodf.df.gov.br/ | 202 | GET responde 202 com o conteúdo do DODF |
| DF | `df-transparencia` | transparência | Portal da Transparência do Distrito Federal | https://www.transparencia.df.gov.br/ | 200 |  |
| ES | `es-diario-oficial` | diário oficial | Diário Oficial do Estado do Espírito Santo | https://ioes.dio.es.gov.br/ | 200 |  |
| ES | `es-transparencia` | transparência | Portal da Transparência do Espírito Santo | https://transparencia.es.gov.br/ | 200 |  |
| GO | `go-diario-oficial` | diário oficial | Diário Oficial do Estado de Goiás | https://diariooficial.abc.go.gov.br/ | 200 |  |
| GO | `go-transparencia` | transparência | Transparência Goiás | https://transparencia.go.gov.br/ | 200 |  |
| MA | `ma-diario-oficial` | diário oficial | Diário Oficial do Estado do Maranhão (DOEMA) | https://diariooficial.ma.gov.br/ | 200 |  |
| MA | `ma-transparencia` | transparência | Portal da Transparência do Maranhão | https://www.transparencia.ma.gov.br/ | 200 |  |
| MG | `mg-diario-oficial` | diário oficial | Diário Oficial de Minas Gerais (Jornal Minas Gerais) | https://www.jornalminasgerais.mg.gov.br/ | 200 |  |
| MG | `mg-transparencia` | transparência | Portal da Transparência de Minas Gerais | https://www.transparencia.mg.gov.br/ | 200 |  |
| MS | `ms-diario-oficial` | diário oficial | Diário Oficial do Estado de Mato Grosso do Sul | https://www.diariooficial.ms.gov.br/ | 200 | endereço antigo spdo.ms.gov.br não resolve DNS |
| MS | `ms-transparencia` | transparência | Portal da Transparência de Mato Grosso do Sul | https://www.transparencia.ms.gov.br/ | 200 |  |
| MT | `mt-diario-oficial` | diário oficial | Diário Oficial do Estado de Mato Grosso (IOMAT) | https://www.iomat.mt.gov.br/ | 200 |  |
| MT | `mt-transparencia` | transparência | Portal da Transparência de Mato Grosso | https://www.transparencia.mt.gov.br/ | 200 |  |
| PA | `pa-diario-oficial` | diário oficial | Diário Oficial do Estado do Pará (IOEPA) | https://www.ioepa.com.br/portal/ | 200 | domínio próprio (ioepa.com.br); a raiz redireciona para HTTP |
| PA | `pa-transparencia` | transparência | Portal da Transparência do Pará | https://www.sistemas.pa.gov.br/portaltransparencia/ | 200 | transparencia.pa.gov.br tem certificado TLS expirado; URL linkada por pa.gov.br |
| PB | `pb-diario-oficial` | diário oficial | Diário Oficial do Estado da Paraíba | https://auniao.pb.gov.br/servicos/doe | 200 |  |
| PB | `pb-transparencia` | transparência | Portal da Transparência da Paraíba | https://transparencia.pb.gov.br/ | 200 |  |
| PE | `pe-diario-oficial` | diário oficial | Diário Oficial do Estado de Pernambuco | https://diariooficial.cepe.com.br/ | 200 | domínio próprio (cepe.com.br) |
| PE | `pe-transparencia` | transparência | Portal da Transparência de Pernambuco | https://transparencia.pe.gov.br/ | 200 |  |
| PI | `pi-diario-oficial` | diário oficial | Diário Oficial do Estado do Piauí | https://www.diario.pi.gov.br/ | 200 |  |
| PI | `pi-transparencia` | transparência | Portal da Transparência do Piauí | https://transparencia.pi.gov.br/ | 200 |  |
| PR | `pr-diario-oficial` | diário oficial | Diário Oficial do Estado do Paraná | https://dioe.pr.gov.br/ | 200 |  |
| PR | `pr-transparencia` | transparência | Portal da Transparência do Paraná | https://www.transparencia.pr.gov.br/ | 200 |  |
| RJ | `rj-diario-oficial` | diário oficial | Diário Oficial do Estado do Rio de Janeiro (IOERJ) | https://www.ioerj.com.br/ | 200 | domínio próprio (ioerj.com.br) |
| RJ | `rj-transparencia` | transparência | Portal da Transparência do Estado do Rio de Janeiro | https://www.transparencia.rj.gov.br/ | 200 |  |
| RN | `rn-diario-oficial` | diário oficial | Diário Oficial do Estado do Rio Grande do Norte | https://www.diariooficial.rn.gov.br/dei/dorn3/ | 200 | a raiz redireciona para HTTP com 404; URL do sistema em HTTPS |
| RN | `rn-transparencia` | transparência | Portal da Transparência do Rio Grande do Norte | https://www.transparencia.rn.gov.br/ | 200 |  |
| RO | `ro-diario-oficial` | diário oficial | Diário Oficial do Estado de Rondônia (DIOF) | https://diof.ro.gov.br/ | 200 |  |
| RO | `ro-transparencia` | transparência | Portal da Transparência de Rondônia | https://transparencia.ro.gov.br/ | 200 |  |
| RR | `rr-diario-oficial` | diário oficial | Diário Oficial do Estado de Roraima | https://www.imprensaoficial.rr.gov.br/ | 200 |  |
| RR | `rr-transparencia` | transparência | Portal de Transparência do Estado de Roraima | https://transparencia.rr.gov.br/ | 200 |  |
| RS | `rs-diario-oficial` | diário oficial | Diário Oficial do Estado do Rio Grande do Sul | https://www.diariooficial.rs.gov.br/ | 200 |  |
| RS | `rs-transparencia` | transparência | Portal da Transparência do Rio Grande do Sul | https://www.transparencia.rs.gov.br/ | 200 |  |
| SC | `sc-diario-oficial` | diário oficial | Diário Oficial do Estado de Santa Catarina | https://portal.doe.sea.sc.gov.br/ | 200 | redireciona por JavaScript para caminho versionado |
| SC | `sc-transparencia` | transparência | Portal da Transparência do Poder Executivo de Santa Catarina | https://www.transparencia.sc.gov.br/ | 200 |  |
| SE | `se-diario-oficial` | diário oficial | Diário Oficial do Estado de Sergipe | https://iose.se.gov.br/diario-oficial | 200 | IOSE, sucessora da Segrase (domínio antigo sem DNS) |
| SE | `se-transparencia` | transparência | Portal da Transparência de Sergipe | https://www.transparencia.se.gov.br/ | 200 |  |
| SP | `sp-diario-oficial` | diário oficial | Diário Oficial do Estado de São Paulo | https://doe.sp.gov.br/ | 200 |  |
| SP | `sp-transparencia` | transparência | Portal da Transparência do Estado de São Paulo | https://www.transparencia.sp.gov.br/ | 200 |  |
| TO | `to-diario-oficial` | diário oficial | Diário Oficial do Estado do Tocantins | https://diariooficial.to.gov.br/ | 200 | PDFs servidos por doe.to.gov.br (raiz responde 403) |
| TO | `to-transparencia` | transparência | Portal da Transparência do Tocantins | https://transparencia.to.gov.br/ | 200 | aplicação exige JavaScript; www falha no TLS |

## Piloto municipal

Prefeitura e câmara de vereadores de Campinas (SP), código IBGE 3509502.
Validação em 2026-10-31, com o User-Agent do projeto e intervalo de 2,5 s ou mais por domínio.
Nenhum servidor bloqueou o cliente.

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `campinas-camara-sapl` | Câmara Municipal, SAPL (API REST) | T0 | JSON e PDF | nenhuma | validado | 2026-10-31 |
| `campinas-camara-transparencia` | Câmara Municipal, portal da transparência (API interna) | T0 | JSON | nenhuma | validado com limitação | 2026-10-31 |
| `campinas-camara-diario-oficial` | Câmara Municipal, Diário Oficial do Legislativo | T0 | HTML com JSON embutido | nenhuma | validado com limitação | 2026-10-31 |
| `campinas-camara-agenda-presidencia` | Câmara Municipal, agenda da Presidência | T0 | HTML | nenhuma | validado com limitação | 2026-10-31 |
| `campinas-pmc-diario-oficial` | Prefeitura, Diário Oficial do Município | T0 | JSON (índice) e PDF | nenhuma | validado com limitação | 2026-10-31 |
| `campinas-pmc-biblioteca-juridica` | Prefeitura, Biblioteca Jurídica (legislação municipal) | T0 | HTML (iso-8859-1) | nenhuma | validado com limitação | 2026-10-31 |
| `campinas-pmc-transparencia` | Prefeitura, portal da transparência | T0 | página e API JSON | chave `x-api-key` | não validado | 2026-10-31 |
| `tce-sp-transparencia-api` | TCE-SP, API de despesas e receitas dos municípios | T0 | JSON | nenhuma | validado com limitação | 2026-10-31 |
| `tce-sp-conjunto-dados` | TCE-SP, arquivos anuais de despesas e receitas | T0 | CSV latin-1 em ZIP | nenhuma | validado com limitação | 2026-10-31 |

As eleições municipais vêm de `tse-cdn`: `consulta_cand_2024.zip` (61,7 MiB) e `consulta_cand_2020.zip` (81,4 MiB).
Em 2024, o município (`SG_UE` 62910) tem 1 prefeito, 1 vice-prefeito e 33 vereadores eleitos, todos no 1º turno.

### `campinas-camara-sapl`: SAPL da Câmara Municipal

- URL base: `https://sapl.campinas.sp.leg.br/api/`. A raiz lista 133 endpoints.
- Endpoints validados com resposta 200:
  - `parlamentares/legislatura/`: 19 legislaturas. A vigente é a 19ª, de 2025-01-01 a 2028-12-31.
  - `parlamentares/mandato/?legislatura=19`: 38 mandatos, com `data_inicio_mandato`, `data_fim_mandato`, `titular` e `tipo_afastamento`. Cobre suplente que assumiu.
  - `parlamentares/parlamentar/?ativo=True`: 33 ativos, 327 no total.
  - `parlamentares/filiacao/`: partido com data de filiação e de desfiliação.
  - `sessao/votoparlamentar/?parlamentar={id}&votacao__gte={registro}`: votos individuais, 130.617 no total. O filtro por registro de votação restringe à legislatura vigente.
  - `sessao/registrovotacao/?ordem__gte={ordem}` e `?expediente__gte={expediente}`: registro de cada votação, com `ordem` ou `expediente` e o resultado em `__str__`.
  - `sessao/ordemdia/?sessao_plenaria={id}` e `sessao/expedientemateria/?sessao_plenaria={id}`: itens votados de cada sessão, com `data_ordem` e matéria em `__str__`.
  - `sessao/sessaoplenaria/`: lista inteira (2.102 sessões em 2026-11-01), com `data_inicio`, `hora_inicio`, `tipo`, `numero` e `legislatura_numero`.
  - `sessao/sessaoplenariapresenca/?parlamentar={id}&sessao_plenaria__gte={sessao}`: presença em sessão. `sessao/presencaordemdia/` traz a presença na ordem do dia.
  - `materia/materialegislativa/?autores={autor}&ano={ano}`: matérias de autoria. `materia/tipomaterialegislativa/` dá sigla e descrição de cada tipo. `base/autor/?object_id={id}` liga o parlamentar ao autor.
  - `norma/normajuridica/?ano={ano}&tipo={tipo}`: 58.068 normas. Tipos usados: 1 (lei), 2 (lei complementar) e 4 (decreto). Traz `data`, `numero`, `ementa` e `texto_integral` (PDF). Página: `/norma/{id}`.
  - `comissoes/participacao/?parlamentar={id}`, `comissoes/composicao/?periodo={id}`, `comissoes/comissao/`, `comissoes/periodo/` e `comissoes/cargocomissao/`: comissões. A lista geral de composição pagina de forma instável (330 únicos em 335); por período cabe numa página.
  - `sessao/orador/` e `sessao/oradorexpediente/`, com `?parlamentar=` e `sessao_plenaria__gte`: ordem e tempo de fala.
- Limitações e armadilhas:
  - De 2 a 6 s por chamada. Não declara limite de taxa.
  - Pagina com `page` e `page_size`, no máximo 100 por página. `sessaoplenaria` ignora `page_size` e devolve a lista inteira, inclusive sessões futuras.
  - Filtro booleano exige `True`: `ativo=true` responde 400.
  - Filtros de intervalo valem em campos diretos (`data_ordem__gte`) e em chaves (`votacao__gte`, `sessao_plenaria__gte`, `ordem__gte`). Filtro por relação indireta (`sessao_plenaria__legislatura`) e `ordering` são ignorados em silêncio.
  - `ordemdia` e `expedientemateria` vêm ordenados por data, com empate instável: a mesma lista paginada repete e omite itens entre páginas (136 de 1.754 em 2026-11-01). As demais listas vêm por id e paginam de forma estável. Por isso os itens são coletados por sessão, numa página só.
  - `votoparlamentar` não traz `ordem` nem `expediente`, e `data_hora` vem nula ou com a data da migração do sistema (2024-01-04). A data do voto vem do item votado: voto -> `votacao` (registro) -> `ordem` ou `expediente` -> `data_ordem`.
  - `autoria.autor` aponta para `base/autor`, e não para o parlamentar. A ligação é por `autor.object_id`.
  - Discursos: só ordem e tempo do orador. `url_discurso` vem vazio.
  - Não há chave comum com o TSE: o vínculo é por nome, partido e legislatura.
  - Dados descartados na ingestão: telefone e e-mail de gabinete.
- Volume da coleta: cerca de 1.500 chamadas na primeira carga da legislatura (itens votados de 253 sessões, registros, votos, presenças, falas e matérias de 38 vereadores), em cerca de 10 minutos. Depois, renovação semanal em rodízio.

### `campinas-camara-transparencia`: portal da transparência da Câmara Municipal

- URL base: `https://transparencia.campinas.sp.leg.br/api/v1/`. API interna da página, sem documentação.
- Endpoints validados com resposta 200 (lidos do script da página em 2026-11-01):
  - `portal/vereadores/filtro_vereador_ativo_inativo/?status=True` (e `False`): traz `id`, `idSAPL` e `idAutoriaSAPL`, a ponte com o SAPL.
  - `despesas-parlamentares-e-administrativas/despesas-com-{categoria}-por-vereador/filtro-por-vereador-ano/?vereador={id}&ano={ano}`, com `categoria` em `combustivel`, `material-de-escritorio`, `reprografia` e `correspondencia`. `.../anos/?vereador={id}` lista os anos com lançamento.
  - `despesas-parlamentares-e-administrativas/hospedagens-e-viagens-parlamentares/por-ano/?ano={ano}`: viagens de todos os vereadores, com `valor_hospedagem` e `valor_passagens`.
- Limitações e armadilhas:
  - Pode mudar sem aviso.
  - Não há verba de gabinete nem ressarcimento.
  - "Ativo" não é "em exercício": o exercício vem do mandato no SAPL.
  - Valores vêm como texto, com ponto decimal (`"137.15"`).
  - Combustível e material de escritório trazem `custo_total`. Reprografia e correspondência trazem só quantidade.
  - Não há fornecedor, CNPJ nem documento fiscal.
  - `viagens-de-parlamentares/por-ano/` respondeu 404 em 2026-11-01.

### `campinas-camara-diario-oficial` e `campinas-camara-agenda-presidencia`

- Diário Oficial do Legislativo: `https://diario.campinas.sp.leg.br/`, com o texto integral em JSON embutido na página. Sem API formal; os filtros não estão confirmados.
- Agenda da Presidência: `https://www.campinas.sp.leg.br/comunicacao/agenda-de-eventos-da-presidencia`. Só em HTML; o iCalendar responde sem eventos. Cobre só o presidente da Câmara.

### Fontes da Prefeitura

- `campinas-pmc-diario-oficial`: índice em `https://portal-adm.campinas.sp.gov.br/api/v2/publicacoes-dom`, de 1995 a 2026, e edições em PDF. API interna, sem documentação. Sem texto estruturado.
- `campinas-pmc-biblioteca-juridica`: `https://bibliotecajuridica.campinas.sp.gov.br/`. Busca por POST, resultado em HTML iso-8859-1.
- `campinas-pmc-transparencia`: a API responde 401 sem a chave `x-api-key`. A página usa uma chave no navegador. Não usamos essa chave: o acesso deve ser pedido à Prefeitura.

### `tce-sp-transparencia-api` e `tce-sp-conjunto-dados`: Tribunal de Contas do Estado de São Paulo

- API: `https://transparencia.tce.sp.gov.br/api/json/`. `municipios`, `despesas/{municipio}/{ano}/{mes}` e `receitas/{municipio}/{ano}/{mes}`.
- Arquivos anuais: `https://transparencia.tce.sp.gov.br/conjunto-de-dados`, de 2008 a 2025, do estado inteiro (`despesas-2025.zip` tem 1,96 GiB).
- Uso no projeto (ADR-0008): bloco `despesas_executivo` do prefeito, com o valor pago pelo órgão da administração direta, somado por mês. Nenhum lançamento individual é publicado.
- Campos da resposta: `orgao`, `mes`, `evento` (`Empenhado`, `Valor Liquidado`, `Valor Pago`, `Anulação`, `Reforço`), `nr_empenho`, `id_fornecedor`, `nm_fornecedor`, `dt_emissao_despesa` e `vl_despesa` (texto com vírgula decimal).
- Limitações e armadilhas:
  - Um mês por resposta, de 3 a 8 MB, sem paginação. Defasagem de cerca de 2 meses: em 2026-11-01, outubro e setembro de 2026 responderam lista vazia.
  - Identifica só o órgão: não separa o gabinete do prefeito nem o vereador.
  - Os eventos contábeis são etapas da mesma despesa. Somar eventos diferentes conta o mesmo gasto mais de uma vez.
  - Valores em texto, com formatos diferentes na despesa e na receita.
  - Fornecedor pessoa física aparece com parte do CPF. O dado não entra no site.
  - Os dados são declarados pelo próprio município.

### Lacunas do piloto

- A Prefeitura não tem portal de dados abertos.
- Não há agenda oficial do prefeito publicada.
- Não há registro estruturado de exercício ou licença do prefeito.
- Despesas do gabinete do prefeito e diárias do Executivo não estão abertas. Pedido por LAI sugerido.

## Assembleia Legislativa do Estado de São Paulo (ALESP)

Poder Legislativo do Estado de São Paulo (ADR-0013).
Validação em 2026-11-03, com o User-Agent do projeto.
Nenhum servidor bloqueou o cliente.

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `alesp-deputados` | ALESP, portal de parlamentares e API REST | T0 | JSON e XML | nenhuma | validado | 2026-11-03 |
| `alesp-despesas` | ALESP, despesas de gabinete | T0 | XML | nenhuma | validado com limitação | 2026-11-03 |
| `alesp-comissoes` | ALESP, comissões e membros | T0 | XML | nenhuma | validado | 2026-11-03 |
| `alesp-comissoes-reunioes` | ALESP, reuniões de comissões permanentes | T0 | XML | nenhuma | validado | 2026-11-03 |
| `alesp-comissoes-votacoes` | ALESP, votações em comissões permanentes | T0 | XML | nenhuma | validado | 2026-11-03 |
| `alesp-proposituras` | ALESP, proposituras e autores | T0 | XML em ZIP | nenhuma | validado | 2026-11-03 |
| `alesp-sessoes` | ALESP, sessões plenárias e pautas | T0 | JSON e PDF | nenhuma | validado com limitação | 2026-11-03 |

### `alesp-deputados`: parlamentares da ALESP

- URL base da API REST: `https://legis-api-portal-prd.al.sp.gov.br/`
- Repositório de dados abertos: `https://www.al.sp.gov.br/repositorioDados/deputados/deputados.xml`
- Endpoints validados com resposta 200:
  - `/parlamentar-portal/todos`: lista todos os parlamentares históricos com `idParlamentar`, `nuMatricula`, `txNomeParlamentar`, `txFoto`, `isEmExercicio` e `listaLegislaturas` (153 deputados na 19ª e 20ª legislaturas).
  - `/parlamentar-portal/detalhes/{matricula}`: detalhes biográficos, partido, áreas de atuação e base eleitoral.
  - `/lista-presenca-parlamentar/{matricula}/{mes}/{ano}`: presenças em plenário por mês e ano.
  - `deputados.xml`: catálogo consolidado dos 94 deputados ativos no momento.
- Identificador no projeto: `alesp-{matricula}` (ADR-0013).
- Dados descartados na ingestão: telefone, sala e e-mail institucional do gabinete.

### `alesp-despesas`: despesas com verba de gabinete

- URL base: `https://www.al.sp.gov.br/repositorioDados/deputados/despesas_gabinetes.xml`
- Arquivo único consolidado com cerca de 171 MB, cobrindo despesas mensais de todos os deputados desde 2015.
- Campos do XML: `Ano`, `Matricula`, `Mes`, `Valor`, `CNPJ`, `Deputado`, `Tipo`, `Fornecedor`.
- Limitações e privacidade de dados:
  - O campo `CNPJ` contém números de 14 dígitos (CNPJ de pessoa jurídica) e de 11 dígitos (CPF de pessoa física).
  - O ETL aplica `Texto.cnpj` (mantendo apenas 14 dígitos) e `Guarda.semCpf`, descartando qualquer CPF de fornecedor.
  - Valores são processados em streaming via leitor StAX para não sobrecarregar a memória heap.

### `alesp-comissoes` e `alesp-comissoes-votacoes`: comissões permanentes

- URLs base:
  - `https://www.al.sp.gov.br/repositorioDados/processo_legislativo/comissoes.xml`
  - `https://www.al.sp.gov.br/repositorioDados/processo_legislativo/comissoes_membros.xml`
  - `https://www.al.sp.gov.br/repositorioDados/processo_legislativo/comissoes_permanentes_votacoes.xml` (cerca de 67 MB)
- Votações nominais estruturadas em comissão: campo `TipoVoto` com parecer favorável, contrário ou em separado.

### `alesp-proposituras` e `alesp-sessoes`: proposituras e plenário

- URLs base:
  - `https://www.al.sp.gov.br/repositorioDados/processo_legislativo/proposituras.zip`
  - `https://www.al.sp.gov.br/repositorioDados/processo_legislativo/documento_autor.zip`
  - `https://legis-api-portal-prd.al.sp.gov.br/votacao/{ano}`
  - `https://legis-api-portal-prd.al.sp.gov.br/sessoes-plenarias/`
- Limitações:
  - Matérias votadas em plenário estão abertas em JSON, mas os votos nominais individuais dos deputados em plenário são publicados em PDF do painel eletrônico (`urlArquivoPrincipal`).
  - O projeto não realiza parsing de PDF do painel nesta fase, priorizando dados primários estruturados.

## Governo do Estado de São Paulo (Executivo Estadual)

Poder Executivo do Estado de São Paulo (ADR-0014).
Validação em 2026-11-03, com o User-Agent do projeto.
Nenhum servidor bloqueou o cliente.

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `sp-agendas` | Plataforma SP-Agendas (SGGD) | T0 | JSON e HTML | nenhuma | validado | 2026-11-03 |
| `sp-transparencia` | Portal da Transparência do Estado de São Paulo | T0 | CSV e JSON | nenhuma | validado | 2026-11-03 |
| `sp-diario-oficial` | Diário Oficial do Estado de São Paulo | T0 | HTML e PDF | nenhuma | validado | 2026-11-03 |
| `sp-legislacao` | Repositório de Normas do Estado de São Paulo | T0 | XML em ZIP | nenhuma | validado | 2026-11-03 |

### `sp-agendas`: agenda oficial do Governador de São Paulo

- URL base: `https://spagendas.sp.gov.br/`
- Regulamentação: Decreto Estadual nº 69.475, sistema eletrônico de integridade e transparência da Secretaria de Gestão e Governo Digital (SGGD).
- Endpoints validados com resposta 200 via HTTP/2:
  - `/Home/GetOrgaosPorNaturezaJuridica?naturezaJuridicaId=...`: lista de órgãos públicos do estado (`40e96b34-9c5c-436c-82bd-e79d9f080697` = Gabinete do Governador).
  - `/Home/GetCargosPorOrgao?orgaoId=...`: cargos por órgão (`ef4562ac-5970-4647-92ed-bcc38432ed4c` = Governador).
  - `/Home/Exportar?usuarioId=...&dataInicial=...&dataFinal=...`: exportação de compromissos públicos com data, horário, título e local.

### `sp-transparencia`: despesas do Executivo Estadual

- URL base: `https://www.transparencia.sp.gov.br/Despesas`
- Consulta e exportação mensal por órgão e elemento econômico (`/Despesas/ExportarCsv`).
- Metodologia de agregação: despesas consolidadas por mês e categoria no perfil do Governador (ADR-0014), sem lançamentos individuais de pessoas físicas.

### `sp-legislacao`: atos normativos e decretos do Governador

- URL base: `https://www.al.sp.gov.br/repositorioDados/legislacao/legislacao_normas.zip`
- Acervo oficial de consolidação legislativa do Estado de São Paulo, cobrindo normas históricas e atualizado diariamente.
- Campos do XML: `Data`, `Ementa`, `IdNorma`, `Numero`, `IdTipo`, `Promulg`, `Situacao`, `URLFicha`.
- Filtragem no projeto: normas com `Promulg == "E"` (Poder Executivo) a partir de 2022, cobrindo Decretos (`DEC`, IdTipo 3), Leis Ordinárias (`LEI`, IdTipo 9) e Leis Complementares (`LCP`, IdTipo 2).

## Assembleia Legislativa do Estado de Minas Gerais (ALMG)

Poder Legislativo do Estado de Minas Gerais (ADR-0015).
Validação em 2026-11-03 com o User-Agent do projeto.
Licença Creative Commons Atribuição 4.0 Internacional.

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `almg-deputados` | ALMG, deputados e legislaturas | T0 | JSON | nenhuma | validado | 2026-11-03 |
| `almg-deputados-detalhes` | ALMG, biografia e comissões | T0 | JSON | nenhuma | validado | 2026-11-03 |
| `almg-despesas` | ALMG, verbas indenizatórias | T0 | JSON | nenhuma | validado | 2026-11-03 |
| `almg-portal` | ALMG, portal oficial e fotos | T0 | HTML e JPG | nenhuma | validado | 2026-11-03 |
| `almg-proposicoes` | ALMG, proposições por ano | T0 | CSV | nenhuma | validado | 2026-11-07 |

### `almg-deputados`: catálogo e legislaturas

- URL base: `https://dadosabertos.almg.gov.br/api/v2/legislaturas/`
- Endpoints validados com resposta 200:
  - `/pesquisa_deputados?leg=19&formato=json`: 79 parlamentares que exerceram mandato na 19ª legislatura (2019-2023).
  - `/pesquisa_deputados?leg=20&formato=json`: 86 parlamentares que exerceram mandato na 20ª legislatura (2023-2027).
- Identificador canônico no projeto: `almg-{id}`.

### `almg-deputados-detalhes`: perfil biográfico e comissões

- URL base: `https://dadosabertos.almg.gov.br/api/v2/deputados/`
- Endpoints validados:
  - `/{id}?formato=json`: dados biográficos completos com `nome`, `nomeServidor` (nome civil), `dataNascimento`, `naturalidadeMunicipio`, `naturalidadeUf` e histórico de legislaturas. Permite vínculo determinístico com o repositório do TSE.
  - `/{id}/participacoes_comissoes?formato=json`: órgãos e comissões permanentes e especiais com cargo (`Presidente`, `Vice-Presidente`, `Efetivo`, `Suplente`) e datas de vigência.
- Fotos oficiais: `https://www.almg.gov.br/export/sites/portal/a-assembleia/deputados/fotos/{id}.jpg`.

### `almg-despesas`: verbas indenizatórias parlamentares

- URL base: `https://dadosabertos.almg.gov.br/api/v2/prestacao_contas/verbas_indenizatorias/`
- Endpoints validados:
  - `/deputados/{id}/datas?formato=json`: meses de competência fechados.
  - `/deputados/{id}/{ano}/{mes}?formato=json`: prestações de contas individuais com data de emissão, tipo de despesa, valor reembolsado, nome do emitente, documento fiscal e CPF/CNPJ.
- Proteção de privacidade: o ETL retém apenas CNPJ válido de 14 dígitos e descarta sequências de 11 dígitos referentes a CPFs de pessoas físicas.

### `almg-proposicoes`: proposições

- URL base: `https://dadosabertos.almg.gov.br/arquivo/proposicoes/download?ano={ano}&tipo=CSV`. Um arquivo por ano, de 2,6 MB (2022) a 10 MB (2025). Validação em 2026-11-07.
- O servidor responde 204, sem corpo, a `Accept: text/csv`. Com `Accept: */*`, entrega o arquivo.
- CSV em UTF-8, com vírgula e aspas. Campo entre aspas pode ter quebra de linha.
- Campos usados: `SiglaTipoProposicao`, `TipoProposicao`, `Numero`, `Ano`, `Ementa`, `Indexacao`, `Situacao`, `DataPublicacao` e `Autores`.
- `Autores` é JSON, com `id`, `nome` e `partido` de cada autor. O `id` é o mesmo do deputado na API. Na validação, todas as 58.861 autorias de 2022 a 2026 casaram com deputados das legislaturas 19 e 20: 108 dos 110 têm proposição.
- Requerimentos (RQC, RQN e RQO), 85% das 50.478 linhas de 2022 a 2026, vêm sem ementa.
- Linha sem autor fica fora. É o caso de todas as mensagens, vetos, ofícios, indicações, propostas de ação legislativa e relatórios.
- Temas: primeiro nível de cada termo `/Tema/` da coluna `Indexacao` (23 áreas, como "Educação" e "Saúde Pública"). Termos de destinatário, localidade e tempo ficam fora.
- Página oficial de cada proposição: `https://www.almg.gov.br/projetos-de-lei/{sigla}/{numero}/{ano}`. Validada para os 13 tipos do CSV. Número inexistente responde 200 com a página "Erro 404".
- Recusa conexão de fora do Brasil, como o resto da ALMG (ADR-0029). Entra pela coleta local cifrada (ADR-0034).

## Assembleia Legislativa do Estado do Paraná (ALEP)

Poder Legislativo do Estado do Paraná (ADR-0016).
Validação em 2026-11-03 com o User-Agent do projeto.

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `alep-deputados` | ALEP, deputados estaduais | T0 | HTML | nenhuma | validado | 2026-11-03 |
| `alep-deputados-detalhes` | ALEP, perfis individuais e biografias | T0 | HTML | nenhuma | validado | 2026-11-03 |
| `alep-comissoes` | ALEP, comissões permanentes e temporárias | T0 | HTML | nenhuma | validado | 2026-11-03 |
| `alep-transparencia` | ALEP, portal da transparência | T0 | HTML e XLSX | nenhuma | validado | 2026-11-03 |
| `alep-ressarcimento` | ALEP, verbas de ressarcimento dos deputados | T0 | JSON | reCAPTCHA v3 | bloqueado | 2026-11-06 |
| `alep-portal` | ALEP, portal institucional e fotos | T0 | HTML e JPG | nenhuma | validado | 2026-11-03 |
| `alep-proposicoes` | ALEP, proposições pela API pública | T0 | JSON (POST) | nenhuma | validado | 2026-11-07 |

### `alep-deputados`: catálogo e legislaturas

- URL base: `https://www.assembleia.pr.leg.br/deputados/conheca`
- 54 deputados estaduais em exercício na 20ª legislatura (2023–2027), com slug, nome parlamentar, partido e foto institucional oficial.
- Identificador canônico no projeto: `alep-{slug}`.

### `alep-deputados-detalhes`: perfil biográfico e comissões

- URL base: `https://www.assembleia.pr.leg.br/deputados/perfil/`
- Trajetória pública, dados biográficos, temas de atuação e histórico de atuação.
- Comissões parlamentares: `https://www.assembleia.pr.leg.br/atividade-parlamentar/comissoes-permanentes-e-temporarias`, com composição de membros efetivos e presidências.
- Fotografias oficiais: hospedadas em `https://storage2.assembleia.pr.leg.br/img/`.

### `alep-transparencia`: execução orçamentária e despesas

- URL base: `https://transparencia.assembleia.pr.leg.br/`
- Demonstrativos consolidados de despesas orçamentárias e pagamentos em `https://storage2.assembleia.pr.leg.br/file/public-media/transparencia/despesas/consolidados/`.

### `alep-ressarcimento`: verbas de ressarcimento (bloqueado)

- URL base: `https://transparencia.assembleia.pr.leg.br/receitas-e-despesas/verbas-de-ressarcimento`
- O script da página (`assets/js/ressarcimento.min.js`) lista os meses em `api/ressarcimento_meses` sem token. A consulta em `api/ressarcimento` (POST) envia `X-Recaptcha-Token` com reCAPTCHA v3. Verificado em 2026-11-06.
- O ETL não contorna CAPTCHA. Por isso as despesas por deputado da ALEP não são coletadas.

### `alep-proposicoes`: proposições

- URL base: `http://webservices.assembleia.pr.leg.br/api/public/proposicao/filtrar`. POST com o filtro em JSON no corpo, por exemplo `{"ano": 2025, "numeroMaximoRegistro": 50000}`. GET responde 400. Validação em 2026-11-07.
- Uma consulta traz o ano inteiro: 5.476 proposições em 2025, cerca de 4 MB.
- Campos usados: `codigo`, `tipoProposicao`, `numero`, `ano`, `ementa`, `status` (TRAMITANDO ou ARQUIVADA), `dataRecebimento` e `autor`. A `siglaTipoProposicao` vem nula, e o tipo fica por extenso.
- `autor` é texto, com vários autores separados por vírgula ("DEPUTADO FULANO, DEPUTADA BELTRANA"). O vínculo usa o nome parlamentar sem o cargo, sem acento e pontuação, e com "DR" e "DRA" por extenso. Casa com o nome do perfil ou com o slug.
- Dois autores usam nome diferente do perfil e do slug ("ARILSON CHIORATO" e "BAZANA"). Uma tabela no adaptador liga cada um ao único deputado da lista da ALEP com esse nome.
- Na validação, 24.238 autorias de 2023 a 2026 nos 54 deputados em exercício. Autores que não são deputados em exercício (Poder Executivo, comissões, ex-deputados) ficam fora.
- O HTTPS do host tem certificado autoassinado, e a URL documentada é HTTP. Respondeu a provas nos EUA (Azure e AWS) e no Brasil.

### `alep-votacoes`: votações nominais

- URL base: `https://transparencia.assembleia.pr.leg.br/api/votacoes`. A API exige `Accept: application/json`; sem ele, responde 403. Validação em 2026-11-07.
- `/sessoes?ano={ano}&mes={mes}` lista as sessões do mês (id e nome); mês sem sessão vem como lista vazia. `/dados-abertos?sessao={id}` aponta um ZIP em `storage2.assembleia.pr.leg.br` com um XLSX por item votado; sessão sem arquivo responde 404. A pesquisa principal (`/api/votacoes`) exige reCAPTCHA v3 e não é usada.
- O XLSX traz item, autores, sessão, data, tipo de votação, ementa e resultado, e uma linha por parlamentar com nome civil, partido e voto (SIM, NÃO, ABSTENÇÃO ou NÃO VOTOU). Só entra votação de tipo NOMINAL.
- O voto vem pelo nome civil, e o perfil da ALEP usa o nome parlamentar. A ponte é o TSE: o `Vinculo` liga o perfil à candidatura, e o nome civil dessa candidatura liga o arquivo de votos ao perfil. Nada é casado por palavra solta.
- Na validação: 527 sessões de 2023 a 2026, 456 com ZIP, 4.135 itens com votação nominal e 197.559 votos em 51 dos 54 deputados em exercício. Os 3 sem vínculo com o TSE ficam sem votos.
- O bloco de votos cita o ZIP de cada sessão, como cada voto: o perfil de um deputado da ALEP chega a 130 KB.

## Fontes da Bahia (ALBA & Governo do Estado)

### `alba-deputados`: parlamentares em exercício

- URL base: `https://www.al.ba.gov.br/deputados/deputados-estaduais`
- 63 deputados estaduais em exercício na 20ª legislatura (2023–2027), com ID numérico, nome parlamentar, partido e foto oficial.
- Identificador canônico no projeto: `alba-{id}` (ex.: `alba-910629`).

### `alba-deputados-detalhes`: perfil biográfico e histórico

- URL base: `https://www.al.ba.gov.br/deputados/deputado-estadual/`
- Nome civil completo oficial, data de nascimento, naturalidade, formação educacional, mandatos eletivos anteriores, filiação partidária e histórico parlamentar.
- Dados de gabinete e contato descartados na ingestão por regra estrita de privacidade.

### `alba-comissoes`: comissões permanentes e temporárias

- URL base: `https://www.al.ba.gov.br/comissoes/composicao`
- Composição detalhada por comissão (`/comissoes/composicao/{id}`), identificando Presidente, Vice-Presidente, Membros Titulares e Membros Suplentes.

### `ba-transparencia` e `ba-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://www.transparencia.ba.gov.br/`
- Diário Oficial: `https://dool.egba.ba.gov.br/`
- Mandato do Governador Jerônimo Rodrigues integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes do Rio de Janeiro (ALERJ & Governo do Estado)

### `alerj-deputados`: parlamentares em exercício

- URL base: `https://www.alerj.rj.gov.br/Deputados/QuemSao`
- 70 deputados estaduais em exercício na 13ª legislatura (2023–2027), com ID numérico oficial, nome parlamentar, partido e foto oficial.
- Identificador canônico no projeto: `alerj-{id}` (ex.: `alerj-505`).

### `alerj-deputados-detalhes`: perfil biográfico e atuação

- URL base: `https://www.alerj.rj.gov.br/Deputados/PerfilDeputado/`
- Data formal de posse na legislatura atual, histórico partidário, mandatos em legislaturas anteriores e biografia oficial.
- Dados de gabinete, telefones e e-mails descartados na ingestão por regra estrita de privacidade.

### `alerj-portal`: portal institucional e fotos

- URL base: `https://www.alerj.rj.gov.br/`
- Fotos oficiais dos parlamentares hospedadas em `/Uploads/PerfilDeputado/Imagem/`.
- Despesas e verba de gabinete por deputado: ver `alerj-docigp`.

### `alerj-docigp`: verba de gabinete (DOCIGP)

- URL base: `https://docigp.alerj.rj.gov.br/api/v1`. JSON, sem autenticação. Validação em 2026-11-07.
- `/congressmen?page={n}`: 137 parlamentares em 7 páginas. O `remote_id` é o id do perfil na ALERJ (`alerj-{id}`).
- `/congressmen/{id}/legislatures/2/budgets?page={n}`: um orçamento por mês da legislatura de 2023 a 2027, do mais novo para o mais velho, 20 por página. Cada orçamento já traz os lançamentos (`entries`): data, valor, objeto, centro de custo, número do documento e fornecedor.
- `/cost-centers`: os centros de custo. Os de código em algarismo romano (I a XI e subitens) são as despesas da verba. Os de código numérico são crédito, transporte de saldo e devolução de saldo, e ficam fora.
- A despesa vem com valor negativo e entra positiva. A categoria é o nome do centro de custo, sem o código.
- O fornecedor traz CNPJ ou CPF e endereço. Só o CNPJ entra; o endereço não é guardado.
- Um mês novo desloca todas as páginas. Quando a primeira página de um deputado é renovada, as outras são renovadas na mesma execução.
- Na validação: 30.330 lançamentos de 2023 a 2026 em 67 dos 70 deputados em exercício, 95% com CNPJ. Os ids 344, 514 e 515 não constam do DOCIGP.
- A conexão oscila. Na validação, 12 tentativas esgotaram o tempo e foram repetidas. Entre provas do Globalping, a Azure (eastus), a AWS em São Paulo e uma rede residencial receberam resposta; a AWS nos EUA e o Google Cloud e a Oracle em São Paulo não.

### `rj-transparencia` e `rj-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://www.transparencia.rj.gov.br/`
- Diário Oficial (IOERJ): `https://www.ioerj.com.br/`
- Mandato do Governador Cláudio Castro integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes do Rio Grande do Sul (ALRS & Governo do Estado)

### `alrs-deputados`: parlamentares em exercício

- URL base: `https://ww4.al.rs.gov.br:5000/listarDestaqueDeputados`
- 55 deputados estaduais em exercício na 56ª legislatura (2023–2027), com ID numérico oficial (`idDeputado`), nome parlamentar, partido e foto oficial.
- Identificador canônico no projeto: `alrs-{id}` (ex.: `alrs-2144`).

### `alrs-deputados-detalhes`: perfil biográfico e comissões

- URL base: `https://ww4.al.rs.gov.br/deputados/`
- Páginas institucionais dos deputados estaduais e comissões parlamentares integradas (`/deputados/{id}/comissoes`).
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `alrs-portal`: portal institucional e fotos

- URL base: `https://ww4.al.rs.gov.br/`
- Repositório de fotos oficiais dos parlamentares em alta resolução hospedadas em `https://ww2.al.rs.gov.br/filerepository/fotografias/`.

### `alrs-votos-plenario`: votos em plenário

- URL base: `https://transparencia.al.rs.gov.br/parlamentares/votos-plenario/pesquisa?solicitante={id}&ano={ano}`. O `solicitante` é o id do perfil (`alrs-{id}`). Validação em 2026-11-07.
- Uma página HTML por deputado e ano, de cerca de 1 MB (58 KB comprimida). Cada voto é um JSON no atributo `data-item`, com as entidades HTML do atributo e os escapes do JSON (`\u00e3`).
- Campos: `nomeDeputado`, `dataVotacao`, `tipoProjeto`, `numProposicao`, `anoProposicao`, `materia`, `voto` e `resultadoVotacao`.
- A página só lista o voto dado: Sim ou Não. Não informa ausência nem abstenção. Por isso o perfil não tem bloco de participação.
- A votação não tem id na fonte, e a hora vem sempre 00:00. O id junta proposição e data. A mesma proposição pode ter mais de uma votação no dia (o projeto e o requerimento de preferência, por exemplo): a segunda leva o sufixo `-2`. A matéria distingue uma da outra.
- A página de 2022 existe para quem já era deputado. Entram 2022 a 2026: 275 páginas para os 55 deputados.
- Na validação, 33.204 votos nos 55 deputados: 30.909 Sim e 2.295 Não. Por ano: 4.856 (2022), 7.819 (2023), 7.697 (2024), 8.090 (2025) e 4.742 (2026). 4.556 votos são a segunda votação ou seguinte da mesma proposição no dia.
- Recusa conexão de fora do Brasil (ADR-0029). Entra pela coleta local cifrada (ADR-0034).

### `rs-transparencia` e `rs-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://www.transparencia.rs.gov.br/` e `https://transparencia.al.rs.gov.br/`
- Diário Oficial: `https://www.diariooficial.rs.gov.br/`
- Mandato do Governador Eduardo Leite integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes do Ceará (ALECE & Governo do Estado)

### `alece-deputados`: parlamentares em exercício

- URL base: `https://www.al.ce.gov.br/deputados`
- Deputados estaduais da 31ª legislatura (2023–2027), com slug textual oficial, nome parlamentar, partido e foto oficial.
- Identificador canônico no projeto: `alece-{slug}` (ex.: `alece-agenor-neto`).

### `alece-deputados-detalhes`: perfil biográfico e atuação

- URL base: `https://www.al.ce.gov.br/deputados/` e `https://www.al.ce.gov.br/comissoes-permanentes/{slug}/composicao`
- Biografia parlamentar detalhada contendo nome civil, data e município de nascimento, e histórico político.
- Comissões permanentes: 20 órgãos temáticos com presidente, vice-presidente, titulares e suplentes populando `orgaos_e_frentes`.
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `alece-portal`: portal institucional e fotos

- URL base: `https://www.al.ce.gov.br/`
- Repositório de fotos oficiais dos parlamentares hospedadas em `https://www.al.ce.gov.br/image/`.

### `alece-despesas`: verba de desempenho parlamentar

- URL base: `https://transparencia.al.ce.gov.br/despesas/verba-desempenho-parlamentar/csv?mes={mm}&ano={aaaa}`. CSV com `;`, UTF-8 com BOM. Validação em 2026-11-07.
- O mês precisa de dois dígitos: com `mes=5`, o CSV vem vazio.
- Colunas: `DEPUTADO;PERIODO;EMPENHO;DESCRICAO;CNPJ;CREDOR;VALOR`. A primeira linha do arquivo vem vazia, antes do cabeçalho.
- O deputado vem pelo nome ("DEP FULANO"). O vínculo ignora acento e pontuação e tira o complemento que a fonte às vezes junta ao nome (" - SERVICOS POSTAIS"). Erro de digitação não casa e é descartado.
- A descrição do empenho é texto livre, um por lançamento, e não vira categoria. A categoria é o nome da verba.
- A coluna `CNPJ` traz CPF no reembolso a pessoa física, inclusive ao próprio deputado. O CPF é descartado.
- Na validação: 15.444 lançamentos de 2023 a 2026 nos 54 deputados em exercício, 80% com CNPJ. 1.657 linhas de quem não está em exercício, ou com nome digitado errado, ficaram fora. 673 lançamentos negativos (estornos) entram como vieram.
- O servidor responde 403 ao User-Agent padrão do curl e 200 ao do projeto. Respondeu a provas nos EUA (Azure e AWS) e no Brasil.

### `alece-votacoes`: votações nominais

- URL base: `https://transparencia.al.ce.gov.br/consultas-gerais/votacao-nominal`. Validação em 2026-11-07.
- A página lista, numa tabela, legislatura, sessão, data (dd/mm/aaaa) e matéria de cada votação nominal, com um PDF e uma planilha XLSX (`/uploads/votacao_nominal_materia_csv/{id}.xlsx`). O índice em CSV (`votacaoNominalcsv`) não traz a data.
- A planilha traz sessão, presidente e matéria no topo e depois uma linha por deputado: número, nome parlamentar, partido e o voto marcado numa das colunas SIM, NÃO e ABSTENÇÃO. A marca "(P)" ou "(V)" junto ao voto não entra.
- Deputado listado sem coluna marcada vira "Sem voto registrado" e conta na participação como votação sem registro.
- O vínculo usa a mesma chave de nome das despesas (`alece-despesas`).
- Na validação: 61 votações de 2023 a 2026 e 2.427 votos nos 54 deputados em exercício. 377 linhas de quem não está em exercício ficaram fora.
- O ETL lê a planilha com `Planilha.java`: só a primeira aba, sem DTD nem entidade externa.

### `ce-transparencia` e `ce-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://transparencia.ce.gov.br/`
- Diário Oficial: `https://www.doe.seplag.ce.gov.br/`
- Mandato do Governador Elmano de Freitas integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes de Santa Catarina (ALESC & Governo do Estado)

### `alesc-deputados`: parlamentares em exercício

- URL base: `https://www.alesc.sc.gov.br/deputados/`
- 40 deputados estaduais em exercício na 20ª legislatura (2023–2027), com slug textual oficial, nome parlamentar, partido e foto oficial.
- Identificador canônico no projeto: `alesc-{slug}` (ex.: `alesc-alex-brasil`, `alesc-altair-silva`).

### `alesc-deputados-detalhes`: perfil biográfico e comissões

- URL base: `https://www.alesc.sc.gov.br/deputado/` e `https://portalelegis.alesc.sc.gov.br/comissoes-permanentes`
- Biografia parlamentar contendo dados de nascimento, formação educacional e atuação parlamentar.
- Comissões permanentes e temporárias com identificação de Presidente, Vice-Presidente e Membros via Portal e-Legis.
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `alesc-portal`: portal institucional e fotos

- URL base: `https://www.alesc.sc.gov.br/`
- Repositório de fotos oficiais dos parlamentares em alta resolução hospedadas no portal institucional.

### `alesc-despesas`: despesas dos gabinetes parlamentares

- URL base: `https://transparencia.alesc.sc.gov.br/gabinetes-parlamentares/csv/{ano}`
- Demonstrativos anuais em CSV (2023–2026), UTF-8 com BOM e separador `;`. Colunas: verba, descrição, conta, favorecido, trecho, data de referência e valor.
- Verbas como diárias, reembolsos, passagens, telefone, veículos, almoxarifado e aluguel. Valor negativo é estorno e entra como veio.
- O vínculo com o deputado usa o nome da conta (nome parlamentar ou civil, com mapa de apelidos no adaptador). Conta que não corresponde a um dos 40 deputados em exercício é descartada, e a contagem aparece na saída do `normalize`.
- Privacidade: o favorecido MEI vem como nome e CPF. O ETL remove a sequência de 11 dígitos (`Guarda.semCpf`). A coluna `trecho` não é usada.
- A fonte não traz CNPJ nem número de documento, e o favorecido vem vazio em cerca de 21% das linhas.

### `sc-transparencia` e `sc-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://www.transparencia.sc.gov.br/`
- Diário Oficial: `https://portal.doe.sea.sc.gov.br/`
- Mandato do Governador Jorginho Mello integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes de Goiás (ALEGO & Governo do Estado)

### `alego-deputados`: parlamentares em exercício

- URL base: `https://portal.al.go.leg.br/deputados/em-exercicio`
- Deputados estaduais da 20ª legislatura (2023–2027), com ID numérico oficial, nome parlamentar e partido.
- Identificador canônico no projeto: `alego-{id}` (ex.: `alego-808`, `alego-51`, `alego-137`).

### `alego-deputados-detalhes`: perfil individual, biografia e comissões

- URL base: `https://portal.al.go.leg.br/deputados/perfil/`, `https://portal.al.go.leg.br/deputados/perfil-biografico/` e `https://portal.al.go.leg.br/comissoes/`
- Perfil com nome civil completo (`Nome Civil: ...`), data e município de nascimento, e histórico de legislaturas.
- Comissões permanentes e frentes parlamentares populando `orgaos_e_frentes` com papéis oficiais (Presidente, Vice-Presidente, Titular, Suplente).
- Dados de gabinete, telefones, e-mails institucionais e informações de parentesco descartados na ingestão por regra estrita de privacidade.

### `alego-portal`: portal institucional e fotos

- URL base: `https://portal.al.go.leg.br/`
- Repositório de fotos oficiais dos parlamentares em alta resolução hospedadas no servidor institucional `https://saba.al.go.leg.br/`.

### `alego-despesas`: verba indenizatória

- URL base: `https://transparencia.al.go.leg.br/api/transparencia/verbas_indenizatorias`. JSON, sem autenticação. Validação em 2026-11-06.
- Resumo do mês: `.json?ano={ano}&mes={mes}&todos=true`, com `deputado.id`, `valor_apresentado` e `valor_indenizado` por deputado.
- Detalhe: `/exibir.json?ano={ano}&mes={mes}&deputado_id={id}`. Grupos e subgrupos de despesa, e em cada lançamento o fornecedor (`nome`, `cnpj_cpf`), a data do documento, o número e os valores apresentado e indenizado.
- O `deputado.id` é o mesmo id do portal institucional (`alego-{id}`).
- O projeto usa o valor indenizado, que é o que a Casa pagou. Ano e mês são os da verba; a data é a do documento.
- `/periodos` lista os meses publicados: de 2014 a 2026, com 2026 até setembro na validação.
- O detalhe traz também o e-mail e as redes do gabinete. O projeto não guarda esses campos.
- Respondeu a provas nos EUA (Azure e AWS) e no Brasil.

### `go-transparencia` e `go-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://www.transparencia.go.gov.br/`
- Diário Oficial: `https://diariooficial.abc.go.gov.br/`
- Mandato do Governador Ronaldo Caiado integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes do Espírito Santo (ALES & Governo do Estado)

### `ales-deputados`: parlamentares em exercício

- URL base: `https://www.al.es.gov.br/Deputado/Lista`
- Deputados estaduais da 20ª legislatura (2023–2027), com ID numérico oficial (`idDeputado`) e nome parlamentar.
- Identificador canônico no projeto: `ales-{id}` (ex.: `ales-305`, `ales-27`, `ales-349`).

### `ales-deputados-detalhes`: perfil individual, biografia e comissões

- URL base: `https://www.al.es.gov.br/Deputado/` e `https://www.al.es.gov.br/Comissao/`
- Perfil com nome civil completo, data e município de nascimento, partido e histórico parlamentar ("Sobre").
- 18 comissões permanentes populando `orgaos_e_frentes` com papéis oficiais (Presidente, Vice-Presidente, Membro Efetivo, Membro Suplente).
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `ales-portal`: portal institucional e fotos

- URL base: `https://www.al.es.gov.br/`
- Repositório de fotos oficiais dos parlamentares em alta resolução hospedadas no portal institucional `/appdata/anexos_internet/portal/deputados/`.

### `ales-proposicoes`: proposições

- URL base: `https://www3.al.es.gov.br/api/publico/proposicao/?pag={n}&qtd=20&ano={ano}&sigla={sigla}`. API NoPaper. Validação em 2026-11-07.
- Entram os tipos de autoria parlamentar: PL, PLC, PEC, PR, PDL, IND e REQ. Em 2024: 696 PL, 1.699 indicações, 140 PDL, 63 requerimentos, 58 PLC, 24 PR e 2 PEC. O ano tem 8.386 itens de todos os tipos, como pareceres e boletins de votação.
- Campos usados: `id`, `sigla`, `tipo`, `numero`, `ano`, `assunto` (ementa), `data`, `situacao` (Tramitando, Arquivado ou Apensado), `arquivo` (PDF do texto) e `AutorRequerenteDados.nomeRazao`. Nas amostras, `CoAutores` veio sempre nulo.
- Cada item traz a tramitação inteira, de 5 a 20 KB. O tempo do servidor cresce com o número de itens: a página de 200 leva cerca de 20 s.
- Quando um item tem campo nulo, a API responde 200 com `{"erro": true, "mensagem": "Nullable object must have a value."}` para a página inteira. Em 2022, o PL na posição 124 derrubava a página. O ETL usa página de 20 e refaz a página com erro item a item (`qtd=1`); só o item com defeito fica fora. A consulta sem tipo, em página grande, também responde erro.
- O autor vem pelo nome. O vínculo usa o nome sem acento e sem pontuação, igual ao nome do perfil. O gabinete (`setor`) não serve de vínculo: há proposição cujo autor não é o titular do gabinete.
- Duas grafias diferem do perfil e têm um só deputado possível: "Engenherio José Esmeraldo", com erro de digitação, e "Fábio Duarte de Almeida", nome completo registrado no gabinete do próprio deputado. Uma tabela no adaptador liga as duas.
- Na validação, 17.273 itens distintos de 2022 a 2026 nos sete tipos. 12.311 são de deputado em exercício, e os 30 têm proposição. Os outros 4.962 são de comissões, do Governador, de ex-deputados e de suplentes.
- 29 páginas vieram com erro e foram refeitas item a item. 52 itens não abrem nem sozinhos e ficam fora: 26 PL, 11 PR, 8 PDL e 5 PLC de 2023, e um PL e um PR de 2022.
- A primeira coleta levou 36 minutos. Depois, só o ano corrente atualiza, uma vez por semana.
- Recusa conexão de fora do Brasil (ADR-0029). Entra pela coleta local cifrada (ADR-0034).

### `es-transparencia` e `es-dioe`: Executivo estadual

- Transparência orçamentária: `https://transparencia.es.gov.br/`
- Diário Oficial: `https://ioes.dio.es.gov.br/`
- Mandato do Governador Renato Casagrande integrado ao modelo e ao arquivo histórico via candidaturas do TSE.
- O portal institucional da ALES (`www.al.es.gov.br`) descarta conexões de datacenters estrangeiros (timeout de conexão em runners internacionais do GitHub Actions); a coleta em ambiente nacional e a suíte de testes operam normalmente.


## Fontes do Pará (ALEPA & Governo do Estado)

### `alepa-deputados`: parlamentares em exercício

- URL base: `https://www.alepa.pa.gov.br/Home/Page/Deputados`
- Deputados estaduais da 60ª legislatura (2023–2027), com ID numérico oficial (`IdApPortAgentePolitico`), nome parlamentar, foto oficial e partido.
- Identificador canônico no projeto: `alepa-{id}` (ex.: `alepa-1024`, `alepa-1011`).

### `alepa-deputados-detalhes`: perfil individual, biografia e comissões

- URL base: `https://www.alepa.pa.gov.br/Institucional/Deputado/` e `https://www.alepa.pa.gov.br/Home/Page/ComissoeseRepresentacoes`
- Perfil com nome civil completo, data de nascimento, partido e biografia oficial ("bioContent").
- Comissões permanentes e representações populando `orgaos_e_frentes` com vínculos determinísticos pelos identificadores oficiais dos parlamentares (Presidente, Vice-Presidente, Titular e Suplente).
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `alepa-portal`: portal institucional e fotos

- URL base: `https://www.alepa.pa.gov.br/`
- Repositório de fotos oficiais dos parlamentares em alta resolução hospedadas no portal institucional `https://alepa.quartertec.com.br/Content/Portal/PerfilAgentePolitico/`.

### `pa-transparencia` e `pa-ioepa`: Executivo estadual

- Transparência orçamentária: `https://www.transparencia.pa.gov.br/`
- Diário Oficial: `https://www.ioepa.com.br/`
- Mandato do Governador Helder Barbalho integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes de Pernambuco (ALEPE & Governo do Estado)

### `alepe-deputados`: parlamentares em exercício

- URL base: `https://www.alepe.pe.gov.br/parlamentares/`
- Deputados estaduais da 20ª legislatura (2023–2027), com slug oficial, nome parlamentar, foto oficial e sigla partidária.
- Identificador canônico no projeto: `alepe-{slug}` (ex.: `alepe-abimael-santos`, `alepe-dani-portela`).

### `alepe-deputados-detalhes`: perfil individual, biografia e comissões

- URL base: `https://www.alepe.pe.gov.br/parlamentar/` e `https://www.alepe.pe.gov.br/comissao/`
- Perfil com nome civil completo, naturalidade, partido, redes sociais oficiais e biografia parlamentar oficial.
- Comissões permanentes e especiais populando `orgaos_e_frentes` com vínculos determinísticos pelos identificadores dos parlamentares (Presidente, Vice-Presidente e Titular).
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `alepe-portal`: portal institucional e fotos

- URL base: `https://www.alepe.pe.gov.br/`
- Repositório de fotos oficiais dos parlamentares em WebP e JPEG de alta resolução hospedadas no portal em `wp-content`.

### `alepe-proposicoes`: proposições

- URL base: `https://dadosabertos.alepe.pe.gov.br/api/v1/proposicoes/{lista}/?ano={ano}`, com `lista` igual a `projetos`, `indicacoes` ou `requerimentos`. Validação em 2026-11-07.
- XML em UTF-8. Cada item traz `docid`, `numero`, `ano`, `tipo`, `subtipo`, `ementa` e `dataPublicacao`, e a lista de autores com `nome` e `tipo` (DEPUTADO, COMISSAO ou EXTERNO). A lista não traz situação de tramitação.
- Em 2024: 945 projetos, 3.220 indicações e 1.322 requerimentos. A ementa de indicações e requerimentos é o texto em HTML, com entidades. O ETL tira a marcação e troca as entidades pelo caractere.
- O autor vem só pelo nome parlamentar. O vínculo usa o nome sem acento e sem pontuação, igual ao nome do perfil. Os 49 deputados em exercício casaram. Na validação, 26.920 autorias de 2022 a 2026; as 3.045 de 30 ex-deputados ficam fora.
- Página oficial de cada proposição: `https://proposicoes.alepe.pe.gov.br/proposicoes/detalhes?docid={docid}&tipoprop={p, i ou r}`. É a rota da aplicação oficial de consulta, lida no código dela. A página carrega o item por `.../api/v1/proposicoes/projetos/?docid={docid}&tipoprop={p, i ou r}`, que respondeu 200 para os três tipos.
- O arquivo de indicações passa de 100 mil caracteres de entidades, o limite do leitor XML do JDK desde a versão 24. O leitor recusa DTD, e o ETL sobe esse limite só para essas listas.
- Recusa conexão de fora do Brasil (ADR-0029). Entra pela coleta local cifrada (ADR-0034).

### `alepe-despesas`: verba indenizatória

- URL base: `https://www.alepe.pe.gov.br/servicos/transparencia/adm/`. Os endereços saíram do código do Portal da Transparência (`transparencia.alepe.pe.gov.br`), uma aplicação de página única. Validação em 2026-11-07.
- `../dep/deputados.php?leg=-16` lista 163 deputados de várias legislaturas, com `id` do portal e `nome`. O `id` não é o do perfil. A lista traz e-mail, que o ETL não lê.
- `verbaindenizatoria-rubricas.php?ano={ano}`: a legenda das rubricas do ano. A rubrica de cada nota é o `numero_categoria`. O portal usa a posição na lista, que dá o mesmo resultado.
- `verbaindenizatoria-dep-meses.php?dep={id}&ano={ano}`: os meses com documento.
- `verbaindenizatoria.php?dep={id}&ano={ano}&mes={mes}`: os documentos do mês. `tipo` P é o pedido; outro valor é complemento. Traz `docid`, `numero` e `total` (com vírgula decimal). O campo `mes` vem com acento quebrado, e o ETL usa o mês da consulta.
- `verbaindenizatorianotas.php?docid={docid}`: as notas do documento, com `rubrica`, `sequencial`, `data` (dd/mm/aaaa), `cnpj`, `empresa` e `valor`. Não traz número de nota fiscal: o campo de documento da despesa leva "Pedido {número}" ou "Complemento {número}".
- No Abimael Santos, de 2023 a 2026, a soma das notas bateu com o total de cada um dos 51 documentos, e todos os CNPJs tinham 14 dígitos. A verba atual, com rubricas por ano, começa em maio de 2022.
- O vínculo com o perfil é pelo nome, sem acento e sem pontuação. Nome repetido na lista do portal fica fora. "João Paulo do PT" (nome do perfil) não casa com "João Paulo" (PT, id 4496), que não tem verba de 2022 a 2026 e pode ser um homônimo antigo. "Cayo Albino" e "Junior Matuto" não estão na lista. Os três ficam sem despesa.
- Na validação, 2.186 documentos de 46 deputados, de 2022 a 2026, com R$ 63.617.616,07 no total. 44 deputados têm verba. Delegada Gleide Ângelo e Izaías Régis têm id no portal e nenhum documento no período.
- 32 documentos (16 pedidos e 16 complementos, R$ 1.079.962,47 no total) saem na fonte com total e sem nenhuma nota. Cada um vira uma linha só, com a categoria "Sem detalhamento na fonte" e a fonte no próprio documento. Sem isso, o total do deputado ficaria abaixo do que a fonte declara. Com as linhas, o total publicado é igual à soma dos totais dos documentos.
- 6.581 lançamentos: 1.067 em 2022 (a partir de maio), 2.207 em 2023, 2.302 em 2024, 862 em 2025 e 143 em 2026. A fonte lista menos meses com documento em 2025 (338 meses de deputado) e em 2026 (92) do que em 2023 (478) e em 2024 (498). Não se sabe se é uso menor da verba ou publicação atrasada.
- 111 notas ficam sem CNPJ: o campo vem vazio ou com CPF, e o ETL descarta o CPF. A fonte grafa o mesmo fornecedor com caixa diferente ("DAVIDSON MENDONÇA FIGUEIROA", "Davidson Mendonça Figueiroa"), e o ETL mantém o texto.
- Recusa conexão de fora do Brasil (ADR-0029). Entra pela coleta local cifrada (ADR-0034).

### `pe-transparencia` e `pe-diario-oficial`: Executivo estadual

- Transparência orçamentária: `https://web.transparencia.pe.gov.br/`
- Diário Oficial: `https://diariooficial.cepe.com.br/` (CEPE)
- Mandato da Governadora Raquel Lyra integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Fontes do Distrito Federal (CLDF & Governo do Distrito Federal)

### `cldf-deputados`: parlamentares distritais em exercício

- URL base: `https://www.cl.df.gov.br/deputados-2023-2026`
- Deputados distritais da 9ª legislatura (2023–2027), com slug oficial, nome parlamentar, foto oficial e sigla partidária.
- Identificador canônico no projeto: `cldf-{slug}` (ex.: `cldf-chico-vigilante`, `cldf-fabio-felix`, `cldf-paula-belmonte`).

### `cldf-deputados-detalhes`: perfil individual, biografia e comissões

- URL base: `https://www.cl.df.gov.br/web/guest/` e `https://www.cl.df.gov.br/comissoes-permanentes`
- Perfil biográfico individual e atuação parlamentar no portal Liferay da CLDF.
- 16 comissões permanentes populando `orgaos_e_frentes` com vínculos determinísticos pelos identificadores e nomes dos parlamentares (Presidente, Vice-Presidente, Membro e Suplente).
- Dados de gabinete, telefones e e-mails institucionais descartados na ingestão por regra estrita de privacidade.

### `cldf-portal`: portal institucional e fotos

- URL base: `https://www.cl.df.gov.br/`
- Repositório de fotos oficiais dos parlamentares em alta resolução hospedadas no portal em `/documents/`.

### `cldf-proposicoes`: proposições

- URL base: `https://ple.cl.df.gov.br/pleservico/api/public/proposicao/filter?page={n}&size=2000&sort=id,asc`. POST com o filtro no corpo, por exemplo `{"ano": 2024}`. Validação em 2026-11-07.
- A página tem no máximo 2.000 itens. O campo `last` vem sempre falso, e a página além do total responde 500. O ETL calcula o número de páginas pelo `totalElements` da primeira. Com a ordem por `id`, as páginas de 2022 a 2026 não repetem item, e a soma bate com o total.
- Itens por ano: 3.339 (2022), 6.903 (2023), 4.931 (2024), 4.921 (2025) e 3.133 (2026, até a validação).
- Campos usados: `id`, `tipoProposicao`, `siglaNumeroAno`, `ementa`, `autoria`, `etapa`, `temaNome` e `dataLeitura`. A lista não traz situação: a etapa da tramitação entra como situação, como a fonte escreve. Os temas vêm entre `#` ("#Educação#Urbanismo#").
- `autoria` é texto, com os autores separados por vírgula e às vezes repetidos. Só entra autor "Deputado" ou "Deputada" cujo nome, sem o cargo e sem acento, é o de um deputado em exercício. Os 24 casaram. Nome de comissão com vírgula conta como dois autores na `ordem_assinatura`.
- Na validação, 26.090 autorias de 2022 a 2026. As de 15 ex-deputados, do Poder Executivo, da Mesa e das comissões ficam fora. Uma autoria grafa "Roosevelt Vilela Pires", nome diferente do perfil, e também fica fora.
- Página oficial de cada proposição: `https://ple.cl.df.gov.br/#/proposicao/{id}/consultar`. É o link que a própria aplicação do PLe monta, lido no código dela. Os dados vêm de `.../api/public/proposicao/{id}/detalhe`, que respondeu 200 com o item.
- A aplicação usa reCAPTCHA só na verificação de autenticidade de documento. A consulta de proposições não pede token.
- Recusa conexão de fora do Brasil (ADR-0029). Entra pela coleta local cifrada (ADR-0034).

### `df-transparencia` e `df-dodf`: Executivo distrital

- Transparência orçamentária: `https://www.transparencia.df.gov.br/`
- Diário Oficial: `https://dodf.df.gov.br/`
- Mandato do Governador Ibaneis Rocha integrado ao modelo e ao arquivo histórico via candidaturas do TSE.


## Segundo município piloto: São Paulo (CMSP & Prefeitura)

Capital do estado, código IBGE `3550308` e unidade eleitoral `71072` no TSE (ADR-0027).
Validação em 2026-11-06.

### `cmsp-splegisws`: vereadores, gabinetes, comissões e proposições

- URL base: `https://splegisws.saopaulo.sp.leg.br/ws/ws2.asmx`. Serviço ASMX com operações `*JSON` por GET, sem autenticação.
- `VereadoresCMSPJSON`: 364 vereadores desde a década de 1990, com `chave`, nome, mandatos, filiações e cargos em comissões e na Mesa. Não traz CPF, telefone nem e-mail.
- `OcupacaoGabineteJSON`: ocupação dos 55 gabinetes por legislatura. O `codigo` é a `chave` do vereador. É o que diz quem está em exercício: a lista de mandatos vigentes traz 64 nomes, com 9 suplentes sem exercício.
- `ComissoesCMSPJSON`: comissões, com sigla.
- Identificador canônico no projeto: `cmsp-{chave}`.
- A fonte deixa cargo antigo da Mesa sem data de fim quando há sucessor. Em cargo exclusivo (Mesa, presidência e vice-presidência de comissão), vale o de início mais recente entre os vereadores em exercício.
- O serviço não tem operação de voto nominal por vereador.
- Proposições, validadas em 2026-11-06:
  - `LeisAprovadasPorPromoventeJSON`, `ProjetosVetadosPorPromoventeJSON` e `ProjetosEmTramitacaoPorPromoventeJSON`, com `Codigo` igual à `chave` do vereador. Trazem os projetos de que o vereador é promovente, autor ou coautor: os que viraram lei (com número e data de publicação), os vetados (com o tipo de veto) e os em tramitação.
  - `ProjetosPorAnoJSON?Ano={ano}`: todos os documentos do ano, com `tipo`, `numero`, `ano`, `data` e `ementa`, sem autor. Dá a ementa e a data dos projetos das listas de autoria.
  - O projeto encerrado sem virar lei fica fora. `ProjetosEncerradosJSON` não traz o autor, e `ProjetosAutoresJSON` pede uma chamada por projeto.
  - A fonte não separa autor de coautor. O campo `proponente` fica nulo.
  - Na validação: 6.495 proposições de 2022 a 2026 nos 55 vereadores em exercício, de 4.084 projetos distintos (PL, PDL, PLO e PR). Situação: 5.501 em tramitação, 951 viraram lei e 43 com veto total.

### `cmsp-portal`: portal institucional

- URL base: `https://www.saopaulo.sp.leg.br/`
- A página de membros (`/vereadores/membros/`) é o link oficial de cada perfil.
- Os cartões individuais, com foto, não vêm no HTML estático, e o projeto não executa JavaScript de terceiros. Por isso os perfis ficam sem foto.

### `sp-capital-transparencia`: Executivo municipal

- URL base: `https://transparencia.prefeitura.sp.gov.br/`
- Prefeito e vice entram pelas candidaturas do TSE de 2024. A eleição de 2020 não entra: o eleito prefeito deixou o cargo em 2021 e o vice assumiu, e a regra de mandato do projeto supõe os quatro anos do eleito.
- Despesa do Executivo: a API do TCE-SP devolve lista vazia para a capital (2026-11-06). Sem fonte validada.
- O Diário Oficial da Cidade (`diariooficial.prefeitura.sp.gov.br`) respondeu 403 à requisição direta de teste. Não foi catalogado.


## Fora da política de fontes

Imprensa, blogs, agregadores sem metodologia, redes sociais de terceiros, mensagens, vídeos não oficiais, Wikipedia e rankings de terceiros.

## Pendências de validação

- `querido-diario`: revalidar com cliente TLS atualizado.
- `planalto-legislacao`: revalidar a partir de outro ambiente.
- `cgu-portal-api`: validar com chave real antes da fase 2.
- Licenças da Câmara e do Senado: confirmar o termo de uso publicado por cada casa.
