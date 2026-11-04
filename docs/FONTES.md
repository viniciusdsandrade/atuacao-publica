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
| `cgu-portal-arquivos` | CGU, downloads do Portal da Transparência | T0 | CSV em ZIP | nenhuma | validado | 2026-10-30 |
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

- Cada ZIP de candidatos traz um CSV por UF, `BR` (Presidência) e `BRASIL` (todos).
- Codificação ISO-8859-1. Separador `;`. Campos de texto entre aspas e campos numéricos sem aspas.
- O arquivo de 2026 já traz o resultado do 1º e do 2º turno em `DS_SIT_TOT_TURNO`: 1 presidente eleito, 27 governadores, 54 senadores, 513 deputados federais (385 por QP e 128 por média).
- `NR_CPF_CANDIDATO`, `NR_TITULO_ELEITORAL_CANDIDATO` e `DS_EMAIL` vêm preenchidos. São descartados na ingestão. O CPF serve só de chave de vínculo em memória.
- O 2º turno aparece como uma segunda linha do candidato (`NR_TURNO = 2`).
- Bens: `SQ_CANDIDATO` e `VR_BEM_CANDIDATO` com vírgula decimal. `DS_BEM_CANDIDATO` pode descrever endereço. Publicamos só o total.
- Propostas de governo: PDFs dentro do ZIP da UF, com nome `{UF}/{ANO}{UF}{SQ_CANDIDATO}.pdf`.

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

## Assembleia Legislativa do Estado do Paraná (ALEP)

Poder Legislativo do Estado do Paraná (ADR-0016).
Validação em 2026-11-03 com o User-Agent do projeto.

| id | Fonte | Nível | Formato | Autenticação | Status | Validado em |
|---|---|---|---|---|---|---|
| `alep-deputados` | ALEP, deputados estaduais | T0 | HTML | nenhuma | validado | 2026-11-03 |
| `alep-deputados-detalhes` | ALEP, perfis individuais e biografias | T0 | HTML | nenhuma | validado | 2026-11-03 |
| `alep-comissoes` | ALEP, comissões permanentes e temporárias | T0 | HTML | nenhuma | validado | 2026-11-03 |
| `alep-transparencia` | ALEP, portal da transparência | T0 | HTML e XLSX | nenhuma | validado | 2026-11-03 |
| `alep-portal` | ALEP, portal institucional e fotos | T0 | HTML e JPG | nenhuma | validado | 2026-11-03 |

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


## Fora da política de fontes

Imprensa, blogs, agregadores sem metodologia, redes sociais de terceiros, mensagens, vídeos não oficiais, Wikipedia e rankings de terceiros.

## Pendências de validação

- `querido-diario`: revalidar com cliente TLS atualizado.
- `planalto-legislacao`: revalidar a partir de outro ambiente.
- `cgu-portal-api`: validar com chave real antes da fase 2.
- Licenças da Câmara e do Senado: confirmar o termo de uso publicado por cada casa.
