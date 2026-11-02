# Metodologia

Este documento explica como cada número é calculado e quais são os limites de cada cálculo.
As fontes estão descritas em `docs/FONTES.md`.

## Regras gerais

- **Cobertura temporal.** Começa em 2022-01-01 (ADR-0009, que revisa o ADR-0006): a 56ª legislatura entra pela parte a partir dessa data, e os mandatos executivos em curso nela entram com os atos a partir dela. Só entra quem exerceu mandato a partir dessa data. A cobertura só cresce: quem sai do cargo continua no arquivo. Histórico anterior a 2022 fica fora. No piloto municipal, a 18ª legislatura da câmara (2021 a 2024) entra pela parte a partir de 2022-01-01, e o mandato de prefeito de 2021 a 2025 entra com as despesas a partir dessa data.
- **Transição.** As datas de posse vêm dos dados: `contrib/mandatos.yaml` para presidente e governador, e a legislatura da Câmara para deputado e senador. A troca de mandatos não exige mudança de código.
- **Situação.** Cada perfil diz se a pessoa está em exercício. A situação mostrada é a do papel principal:
  - deputado e senador: a situação informada pela casa legislativa (por exemplo, `Exercício`);
  - presidente e governador: "Mandato vigente" ou "Mandato encerrado em {data}". O TSE registra a eleição; o exercício é deduzido das datas legais de posse. Renúncia ou sucessão entram por contribuição com fonte T0;
  - eleito que ainda não tomou posse: "Eleito em {ano}; posse prevista em {data}";
  - eleito que a casa ainda não registra em exercício depois da data de posse: "Eleito em {ano}; sem registro de exercício".
- **Ausência.** Campo sem valor na fonte é `null` no JSON e "sem registro na fonte" na tela.
- **Datas.** ISO 8601. Instantes de captura em UTC.
- **Atualização.** Cada bloco do perfil mostra a data de captura. Quando a última coleta de uma fonte falha, o bloco mostra o aviso "fonte desatualizada" e mantém o último dado válido.
- **Renovação.** A cada execução, o ETL baixa de novo os arquivos do ano corrente. Os do ano anterior são baixados de novo até 31 de março. Os anos mais antigos vêm da cópia já coletada. O detalhe e as frentes de cada deputado são renovados uma vez por semana, em rodízio. Partido e UF vêm da lista diária da Câmara.
- **Coleta parcial.** Se um arquivo não puder ser atualizado e existir cópia de coleta anterior, o ETL usa a cópia, com a data de captura original. A fonte fica com status `parcial`, e o bloco mostra o aviso. Depois de 3 falhas no mesmo tipo de arquivo, o ETL não tenta de novo naquela execução. Cada coleta tem um orçamento de tempo (`ETL_ORCAMENTO_MINUTOS`): esgotado, o restante vem do cache. Na Câmara, discursos e frentes são complementares, e um deputado sem detalhe ou histórico coletado, sem cópia anterior, fica fora da publicação até a próxima execução; a fonte fica parcial, não em falha.
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

### Câmara municipal (piloto, SAPL)

1. Universo: votos individuais de `sessao/votoparlamentar` cujo registro de votação (`sessao/registrovotacao`) aponta para um item da ordem do dia ou do expediente da legislatura vigente.
2. Data: o voto individual não traz data confiável (votos migrados trazem a data da migração). A data é a do item votado (`data_ordem` da ordem do dia ou do expediente).
3. `votacoes_nominais`: votos individuais do item 1, por ano.
4. `votos_registrados`: votos `Sim`, `Não` ou `Abstenção`.
5. `por_tipo`: contagem de cada valor da fonte, inclusive `Não Votou` e `Ausente`.
6. `sem_registro`: a fonte lista só quem teve voto lançado. O campo fica `null`.
7. Voto sem registro de votação ou sem item votado na coleta fica fora. A quantidade aparece na saída do `normalize`.

### Limites

- Votação simbólica não tem voto individual e fica fora.
- As casas registram ausência de forma diferente. Os números das casas não são comparáveis entre si.
- No piloto municipal, a orientação de partido e de governo fica `null`: a fonte não publica orientação.

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

### Câmara municipal (piloto, portal da transparência)

- Fonte: API interna do portal da transparência da câmara, por vereador e ano.
- Categorias: `Combustível` e `Material de escritório` (`custo_total` de cada lançamento), `Hospedagem` e `Passagens` (`valor_hospedagem` e `valor_passagens` de cada viagem).
- Ponte com o SAPL: campo `idSAPL` da lista de vereadores do portal.
- A fonte não publica fornecedor, CNPJ nem documento fiscal. Esses campos ficam `null`.
- Reprografia e correspondência vêm só em quantidade, sem valor, e ficam fora.
- Não há verba de gabinete nem ressarcimento na fonte. O total não é comparável com a cota parlamentar federal.

### Minimização

- CPF de fornecedor pessoa física é descartado. Só CNPJ (14 dígitos) é publicado.
- Sequência de 11 dígitos no nome do fornecedor (CPF no nome de MEI) é removida.
- Nome de passageiro é descartado.

## Proposições

- **Câmara.** Arquivos `proposicoesAutores` dos anos da legislatura, filtrados pelo deputado. Tipo, número, ano, ementa, situação e link vêm de `proposicoes`. Temas vêm de `proposicoesTemas`. O campo `ordem_assinatura` e o campo `proponente` vêm da fonte.
- **Senado.** `/processo?codigoParlamentarAutor={codigo}`, filtrado por apresentação a partir do início da cobertura (2022-01-01). A listagem não traz temas: o campo fica `null`.
- **Câmara municipal (piloto).** `materia/materialegislativa?autores={autor}&ano={ano}` do SAPL, por ano da legislatura vigente. O `autor` vem de `base/autor` pelo `object_id` do parlamentar. Entram todos os tipos de matéria, inclusive indicações e requerimentos. Situação: `resultado` da fonte, ou "Em tramitação" e "Tramitação encerrada" pelo campo `em_tramitacao`. Temas ficam vazios: a fonte não classifica.

## Discursos

- **Câmara.** `/deputados/{id}/discursos`. Publica tipo, data, fase, sumário oficial e link do texto quando a fonte informa.
- **Senado.** `/senador/{codigo}/discursos`. Publica tipo de uso da palavra, data, resumo oficial e link do texto.
- **Câmara municipal (piloto).** `sessao/oradorexpediente` e `sessao/orador` do SAPL. A fonte registra só a ordem de fala e a hora: tipo ("Orador do Expediente" ou "Orador das Explicações Pessoais"), data e hora da sessão e observação. Não há texto nem resumo.
- Entidades HTML do texto oficial (`&nbsp;`) viram caractere comum. O conteúdo não muda.

## Eventos e presença em eventos

- **Câmara.** Eventos com presença registrada em `eventosPresencaDeputados`, com tipo, situação e descrição de `eventos` e órgão de `eventosOrgaos`.
- A lista da fonte traz só presentes. O projeto não calcula taxa de presença em evento.
- **Senado.** Sem fonte equivalente na POC: "sem registro na fonte".
- **Câmara municipal (piloto).** Presença em sessão plenária de `sessao/sessaoplenariapresenca` do SAPL, nas sessões da legislatura vigente já realizadas. Tipo, número e data vêm de `sessao/sessaoplenaria`. A fonte lista só presentes. O projeto não calcula taxa de presença.

## Órgãos e frentes

- **Câmara.** `orgaosDeputados-L{legislatura}` de cada legislatura coberta (cargo, início e fim) e `/deputados/{id}/frentes` filtrado pelas legislaturas cobertas.
- **Senado.** `/senador/{codigo}/comissoes`, com participação ativa em algum momento desde o início da cobertura (2022-01-01).
- **Câmara municipal (piloto).** `comissoes/participacao` do SAPL por vereador, com a comissão e o período vindos de `comissoes/composicao` (coletada por período), `comissoes/comissao` e `comissoes/periodo`. Papel: cargo da fonte (`Presidente`, `Membro`, `Relator`, `Suplente`); membro não titular recebe o sufixo "(suplente)". Entram as participações cujo fim é posterior ao início da cobertura ou está aberto.

## Candidaturas

- Fonte: arquivos `consulta_cand` e `bem_candidato` do TSE das eleições gerais que alimentam mandatos vigentes: as dos últimos 8 anos (mandato de senador). Em 2026: 2018, 2022 e 2026. A lista de anos sai do calendário eleitoral (anos com resto 2 na divisão por 4), sem ano fixo no código.
- Cargos lidos: Presidente, vice, governador, vice, senador, suplentes de senador e deputado federal.
- Resultado: `DS_SIT_TOT_TURNO` do último turno disputado.
- Bens: soma de `VR_BEM_CANDIDATO` do candidato. A descrição de cada bem não é publicada.
- Plano de governo (Presidência e governos estaduais): arquivo PDF dentro do ZIP `proposta_governo_{ano}_{UF}.zip` do TSE. O perfil publica a URL do ZIP e o caminho do arquivo. O ETL confere o nome no índice do ZIP: `{UF}/{ANO}{UF}{SQ}.pdf` em 2022 e `{UF}/{ANO}{UF}{SQ}_01.pdf` em 2026. Vale o primeiro arquivo do candidato.
- Planos de governo: só das duas eleições mais recentes (2022 e 2026). Planos de 2018 ficam fora da POC.
- Quando o ZIP da fonte não traz o arquivo do candidato, o campo fica "sem registro na fonte". Em 2022, 1 dos 28 eleitos para cargo executivo está nessa situação.
- Vínculo com o perfil: CPF, em memória, para deputados. Nome civil, UF e data de nascimento, em memória, para senadores. O vínculo entre eleições usa CPF; quando o TSE mascara o CPF (arquivo de 2024), usa nome civil e data de nascimento, também em memória. Quem passou de uma casa a outra dentro da cobertura (deputado que virou senador ou vereador) tem um perfil só, com os blocos das duas casas; a ponte é o CPF ou, sem ele, o nome civil com a data de nascimento. Nem CPF nem data de nascimento são gravados.
- **Piloto municipal.** Eleições de 2020 e 2024 (`consulta_cand` e `bem_candidato`), filtradas pelo município piloto. O SAPL não publica CPF: a candidatura mais recente com o mesmo nome civil ou nome de urna normalizado, dentro do município, serve de ponte; as demais eleições da pessoa vêm pelo CPF dessa candidatura, em memória, como nas outras casas. Planos de governo municipais: só a partir de 2022, como no federal; 2020 fica sem plano.

## Executivo

- **Mandatos.** Datas de início e fim vêm de `contrib/mandatos.yaml`, com proveniência na EC 111/2021: eleitos em 2022 tomaram posse em 2023-01-01; os mandatos vão até 2027-01-05 (Presidente) e 2027-01-06 (governadores).
- **Governadores.** O TSE registra a eleição, não o exercício do cargo. Renúncia, afastamento ou sucessão dependem de ato estadual. Correção desse tipo entra por contribuição com fonte T0 (diário oficial do estado).
- **Atos normativos.** Leis, leis complementares, medidas provisórias e decretos de `/legislacao/lista` do Senado, e vetos de `/materia/vetos/{ano}`, publicados no período do mandato. A fonte não informa quem assinou cada ato. O bloco se chama "atos do Poder Executivo federal no período do mandato".
- **Agenda oficial (ADR-0011).** Página de cada dia da agenda do Planalto, no endereço dado por `contrib/mandatos.yaml`. Dia com 404 fica sem registro. Para o mandato de 2019 a 2023, a página vem da captura do Internet Archive do próprio dia. Cada compromisso publica data, hora, título literal, local e participantes como na fonte. Registro manual com snapshot continua aceito e não repete compromisso coletado.
- **Cartão de pagamento e viagens da Presidência (ADR-0010).** Bloco `despesas_executivo` do presidente, pelos arquivos abertos do Portal da Transparência: `cpgf/{AAAAMM}` (um por mês do mandato) e `viagens/{ano}`. Entra só o órgão 20101, Presidência da República. Cartão: `VALOR TRANSAÇÃO` somado por ano e mês do extrato e por unidade gestora; transação sob sigilo entra pelo valor. Viagens: só as realizadas, no mês da data de início, em diárias (menos devolução), passagens e outros gastos. Portador, favorecido e viajante nunca são lidos. Os 6 meses mais recentes do cartão e o ano corrente de viagens renovam toda semana.
- **Despesas do Executivo municipal (piloto, ADR-0008).** Bloco `despesas_executivo` do prefeito. Fonte: `despesas/{municipio}/{ano}/{mes}` da API do TCE-SP, para cada mês do mandato já iniciado. Entram só os lançamentos do órgão cujo nome começa com "PREFEITURA MUNICIPAL" (administração direta) e do evento `Valor Pago`. O ETL soma por mês e por órgão e conta os pagamentos. Fornecedor, empenho e data de cada lançamento não são publicados. Os 6 meses mais recentes são renovados toda semana; os anteriores vêm do cache. Mês sem resposta da fonte fica sem registro.
- **Atos normativos municipais (piloto, ADR-0009).** Leis, leis complementares e decretos de `norma/normajuridica` do SAPL, por ano e tipo, publicados no período de cada mandato de prefeito. Os tipos seguem o schema federal: `LEI`, `LCP` e `DEC`. A fonte não informa quem assinou cada ato: o bloco se chama "atos normativos do município no período do mandato".
- **Prefeito e vice-prefeito (piloto).** Os perfis vêm das candidaturas eleitas no TSE de 2020 e de 2024. Os mandatos vêm de `contrib/mandatos.yaml` (posse em 1º de janeiro do ano seguinte à eleição e fim na posse do sucessor, art. 29 da Constituição). A prefeitura não publica dados abertos: agenda entra por contribuição com fonte T0.

## Validação antes de publicar

1. Schema JSON de cada arquivo publicado.
2. Campos obrigatórios de proveniência.
3. Regex de bloqueio para CPF, telefone e e-mail em todo texto publicado.
4. Arquivo que falha na validação não é publicado. A falha aparece em `_meta/execucao.json`.
