# Metodologia

Este documento explica como cada número é calculado e quais são os limites de cada cálculo.
As fontes estão descritas em `docs/FONTES.md`.

## Regras gerais

- **Cobertura temporal.** Começa no início da legislatura vigente da Câmara (`/legislaturas?data=hoje`). Na 57ª legislatura, o início é 2023-02-01. No Executivo, começa no início do mandato (2023-01-01). Histórico anterior fica fora da POC.
- **Transição.** A legislatura vigente e os mandatos vêm dos dados. Em fevereiro de 2027, a nova legislatura entra sem mudança de código.
- **Ausência.** Campo sem valor na fonte é `null` no JSON e "sem registro na fonte" na tela.
- **Datas.** ISO 8601. Instantes de captura em UTC.
- **Atualização.** Cada bloco do perfil mostra a data de captura. Quando a última coleta de uma fonte falha, o bloco mostra o aviso "fonte desatualizada" e mantém o último dado válido.
- **Coleta parcial.** Se um arquivo não puder ser atualizado e existir cópia de coleta anterior, o ETL usa a cópia, com a data de captura original. A fonte fica com status `parcial`, e o bloco mostra o aviso. Depois de 3 falhas seguidas no mesmo tipo de arquivo, o ETL não tenta de novo naquela execução.
- **URL de origem.** Cada registro aponta para o recurso na fonte oficial (por exemplo, a votação na API da Câmara). Quando o dado saiu de um arquivo anual em massa, o arquivo aparece na lista de fontes do bloco, com hash e data de captura em `_meta/{fonte}.json`.
- **Fotos.** O site não embute a foto oficial: carregar a imagem faria o navegador do visitante chamar o servidor da fonte. O perfil traz o link para a foto.

## Participação em votações nominais

Não é presença. É a contagem de votos registrados em votações nominais.

### Câmara

1. Universo: votações do Plenário (`siglaOrgao = PLEN`) com pelo menos um voto individual no arquivo `votacoesVotos`.
2. Exercício: intervalos em que o deputado estava com situação `Exercício` em `/deputados/{id}/historico`. Registros com situação nula (troca de nome ou partido) não mudam o intervalo.
3. `votacoes_nominais`: votações do universo realizadas durante o exercício (pelo instante `dataHoraRegistro` da votação).
4. `votos_registrados`: votações do item 3 em que o deputado votou `Sim`, `Não`, `Abstenção` ou `Obstrução`.
5. `por_tipo`: contagem de cada valor da fonte, inclusive `Artigo 17` (presidente da sessão, que não vota por regra) e valor vazio.
6. `sem_registro`: votações do item 3 em que o deputado não aparece na lista da fonte.

### Senado

1. Universo: votações de `/votacao` em que o senador aparece em `votos[]`, nas sessões do Senado (`casaSessao = SF`) e do Congresso (`CN`). O Senado lista todos os senadores, inclusive ausentes. A coluna "órgão" dos votos mostra `SF` ou `CN`.
2. `votos_registrados`: votos `Sim`, `Não`, `Abstenção`, `Obstrução` ou `Votou` (votação secreta).
3. `por_tipo`: contagem de cada sigla da fonte, como `AP` (atividade parlamentar), `LS` (licença saúde), `NCom` (não compareceu), `P-NRV` (presente sem registrar voto) e `Presidente (art. 51 RISF)`.

### Limites

- Votação simbólica não tem voto individual e fica fora.
- As duas casas registram ausência de forma diferente. Os números das casas não são comparáveis entre si.

## Orientação de voto

- **Partido.** A orientação vale quando a sigla da bancada é a sigla do partido do parlamentar na data do voto. Também vale quando a bancada é uma federação (`Fdr PT-PCdoB-PV`) e o partido está no nome da federação. Comparação sem acento e sem diferença de maiúsculas. Rótulo truncado pela fonte (`Solidaried`) vale como prefixo de ao menos 6 letras. Apelidos fixos: `Progressistas` = `PP`.
- **Bloco.** Bloco parlamentar com nome truncado (`Bl UniPpPsd...`) não permite saber os partidos. Nesse caso a orientação do partido fica `null`.
- **Governo.** Bancada `Governo`.
- O campo `orientacao_partido_bancada` guarda o rótulo da bancada usada.

## Despesas

### Câmara (CEAP)

- Fonte: arquivo anual `Ano-{ano}.json.zip`.
- Valor: `valorLiquido` (valor do documento menos glosa).
- Mês e ano: campos `mes` e `ano` da fonte.
- Categoria: campo `descricao` da fonte, sem alteração.
- Registros sem `idDeputado` (gastos de liderança) ficam fora dos perfis.
- Valores negativos (compensação de passagem, por exemplo) entram na soma. O total é líquido.
- Deduplicação: dois registros são o mesmo quando coincidem em deputado, documento (`idDocumento`), parcela, número, data de emissão, valor líquido, fornecedor, CNPJ, mês, ano e subcota.

### Senado (CEAPS)

- Fonte: CSV anual `despesa_ceaps_{ano}.csv`.
- Valor: `VALOR_REEMBOLSADO`.
- Categoria: `TIPO_DESPESA`.
- Junção com o senador: nome da coluna `SENADOR` igual ao `NomeParlamentar` da lista atual, depois de tirar acentos, padronizar maiúsculas e espaços.
- Nome sem correspondência na lista atual (ex-senador ou suplente fora de exercício) fica fora dos perfis. A contagem aparece em `_meta/senado.json`.
- Deduplicação: linhas idênticas em todas as colunas contam uma vez.
- A fonte não publica URL do documento fiscal.

### Minimização

- CPF de fornecedor pessoa física é descartado. Só CNPJ (14 dígitos) é publicado.
- Sequência de 11 dígitos no nome do fornecedor (CPF no nome de MEI) é removida.
- Nome de passageiro é descartado.

## Proposições

- **Câmara.** Arquivos `proposicoesAutores` dos anos da legislatura, filtrados pelo deputado. Tipo, número, ano, ementa, situação e link vêm de `proposicoes`. Temas vêm de `proposicoesTemas`. O campo `ordem_assinatura` e o campo `proponente` vêm da fonte.
- **Senado.** `/processo?codigoParlamentarAutor={codigo}&tramitouLegislaturaAtual=S`, filtrado por apresentação a partir do início da legislatura. A listagem não traz temas: o campo fica `null`.

## Discursos

- **Câmara.** `/deputados/{id}/discursos`. Publica tipo, data, fase, sumário oficial e link do texto quando a fonte informa.
- **Senado.** `/senador/{codigo}/discursos`. Publica tipo de uso da palavra, data, resumo oficial e link do texto.
- Entidades HTML do texto oficial (`&nbsp;`) viram caractere comum. O conteúdo não muda.

## Eventos e presença em eventos

- **Câmara.** Eventos com presença registrada em `eventosPresencaDeputados`, com tipo, situação e descrição de `eventos` e órgão de `eventosOrgaos`.
- A lista da fonte traz só presentes. O projeto não calcula taxa de presença em evento.
- **Senado.** Sem fonte equivalente na POC: "sem registro na fonte".

## Órgãos e frentes

- **Câmara.** `orgaosDeputados-L{legislatura}` (cargo, início e fim) e `/deputados/{id}/frentes` filtrado pela legislatura vigente.
- **Senado.** `/senador/{codigo}/comissoes`, com participação ativa em algum momento da legislatura vigente.

## Candidaturas

- Fonte: arquivos `consulta_cand` e `bem_candidato` do TSE das eleições gerais que alimentam mandatos vigentes: as dos últimos 8 anos (mandato de senador). Em 2026: 2018, 2022 e 2026. A lista de anos sai do calendário eleitoral (anos com resto 2 na divisão por 4), sem ano fixo no código.
- Cargos lidos: Presidente, vice, governador, vice, senador, suplentes de senador e deputado federal.
- Resultado: `DS_SIT_TOT_TURNO` do último turno disputado.
- Bens: soma de `VR_BEM_CANDIDATO` do candidato. A descrição de cada bem não é publicada.
- Plano de governo (Presidência e governos estaduais): arquivo PDF dentro do ZIP `proposta_governo_{ano}_{UF}.zip` do TSE. O perfil publica a URL do ZIP e o caminho do arquivo. O ETL confere o nome no índice do ZIP: `{UF}/{ANO}{UF}{SQ}.pdf` em 2022 e `{UF}/{ANO}{UF}{SQ}_01.pdf` em 2026. Vale o primeiro arquivo do candidato.
- Planos de governo: só das duas eleições mais recentes (2022 e 2026). Planos de 2018 ficam fora da POC.
- Quando o ZIP da fonte não traz o arquivo do candidato, o campo fica "sem registro na fonte". Em 2022, 1 dos 28 eleitos para cargo executivo está nessa situação.
- Vínculo com o perfil: CPF, em memória, para deputados. Nome civil, UF e data de nascimento, em memória, para senadores. O vínculo entre eleições usa CPF. O CPF nunca é gravado.

## Executivo

- **Mandatos.** Datas de início e fim vêm de `contrib/mandatos.yaml`, com proveniência na EC 111/2021: eleitos em 2022 tomaram posse em 2023-01-01; os mandatos vão até 2027-01-05 (Presidente) e 2027-01-06 (governadores).
- **Governadores.** O TSE registra a eleição, não o exercício do cargo. Renúncia, afastamento ou sucessão dependem de ato estadual. Correção desse tipo entra por contribuição com fonte T0 (diário oficial do estado).
- **Atos normativos.** Leis, leis complementares, medidas provisórias e decretos de `/legislacao/lista` do Senado, e vetos de `/materia/vetos/{ano}`, publicados no período do mandato. A fonte não informa quem assinou cada ato. O bloco se chama "atos do Poder Executivo federal no período do mandato".
- **Agenda oficial.** A agenda presidencial existe só em HTML. Ela entra por contribuição manual com snapshot.

## Validação antes de publicar

1. Schema JSON de cada arquivo publicado.
2. Campos obrigatórios de proveniência.
3. Regex de bloqueio para CPF, telefone e e-mail em todo texto publicado.
4. Arquivo que falha na validação não é publicado. A falha aparece em `_meta/execucao.json`.
