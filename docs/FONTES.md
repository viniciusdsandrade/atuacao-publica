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
  - `/frentes` devolve todas as legislaturas. Filtramos `idLegislatura = 57`.
  - Em 2026-10-30, `/deputados/{id}/frentes` respondeu 504 depois de 15 segundos, de forma repetida. O ETL usou a cópia da coleta anterior (ADR-0005).
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
  - `/senador/{codigo}`, `/senador/{codigo}/mandatos`, `/senador/{codigo}/discursos`, `/senador/{codigo}/comissoes`, `/senador/{codigo}/licencas`, `/senador/{codigo}/cargos`.
  - `/votacao?dataInicio=AAAA-MM-DD&dataFim=AAAA-MM-DD`: array na raiz, com `votos[]` embutido (81 votos por votação).
  - `/processo?codigoParlamentarAutor={codigo}&tramitouLegislaturaAtual=S`: 379 processos para o senador testado, com `identificacao`, `ementa`, `situacaoAtual`, `dataApresentacao`, `urlDocumento`.
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
- Fora da POC: gastos do Executivo entram na fase 2.

### `cgu-eagendas`: e-Agendas

- Arquivo: `https://dadosabertos-download.cgu.gov.br/dados_e-agendas/dados_e-agendas.zip` (205 MB, 48 CSVs mensais, atualização mensal).
- CSV em UTF-8, separador vírgula.
- Limitação: setembro de 2026 não tem nenhum registro com cargo "Presidente da República". A agenda presidencial não está nesta base.

### `planalto-agenda`: agenda do Presidente da República

- URL: `https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica`
- Só HTML, uma página por dia. Não há API.
- Decisão: a agenda entra por contribuição manual com snapshot. Não há raspagem.

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

## Fora da política de fontes

Imprensa, blogs, agregadores sem metodologia, redes sociais de terceiros, mensagens, vídeos não oficiais, Wikipedia e rankings de terceiros.

## Pendências de validação

- `querido-diario`: revalidar com cliente TLS atualizado.
- `planalto-legislacao`: revalidar a partir de outro ambiente.
- `cgu-portal-api`: validar com chave real antes da fase 2.
- Licenças da Câmara e do Senado: confirmar o termo de uso publicado por cada casa.
