# Metodologia

Este documento explica como cada número é calculado e quais são os limites de cada cálculo.
As fontes estão descritas em `docs/FONTES.md`.

## Regras gerais

- **Cobertura temporal.** Começa em 2022-01-01 (ADR-0009, que revisa o ADR-0006): a 56ª legislatura entra pela parte a partir dessa data, e os mandatos executivos em curso nela entram com os atos a partir dela. Só entra quem exerceu mandato a partir dessa data. A cobertura só cresce: quem sai do cargo continua no arquivo. Histórico anterior a 2022 fica fora. No piloto municipal, a 18ª legislatura da câmara (2021 a 2024) entra pela parte a partir de 2022-01-01, e o mandato de prefeito de 2021 a 2025 entra com as despesas a partir dessa data.
- **Transição.** As datas de posse vêm dos dados: `contrib/mandatos.yaml` para presidente e governador, e a legislatura da Câmara para deputado e senador. A troca de mandatos não exige mudança de código.
- **Situação.** Cada perfil diz se a pessoa está em exercício. A situação mostrada é a do papel principal:
  - deputado e senador: a situação informada pela casa legislativa (por exemplo, `Exercício`);
  - presidente e governador: "Mandato vigente" ou "Mandato encerrado em {data}". O TSE registra a eleição; o exercício é deduzido das datas legais de posse. Renúncia ou sucessão entram por contribuição com fonte T0;
  - chefe do Executivo com candidatura a outro cargo na eleição seguinte, no próprio TSE: "Deixou o cargo para concorrer a {cargo}". O mandato termina no prazo de renúncia, seis meses antes do pleito (Constituição, art. 14, § 6º), e o texto avisa que o TSE não informa a data exata (ADR-0033);
  - eleito que ainda não tomou posse: "Eleito em {ano}; posse prevista em {data}";
  - eleito que a casa ainda não registra em exercício depois da data de posse: "Eleito em {ano}; sem registro de exercício".
- **Ausência.** Campo sem valor na fonte é `null` no JSON e "sem registro na fonte" na tela.
- **Datas.** ISO 8601. Instantes de captura em UTC.
- **Atualização.** Cada bloco do perfil mostra a data de captura. Quando a última coleta de uma fonte falha, o bloco mostra o aviso "fonte desatualizada" e mantém o último dado válido.
- **Renovação.** A cada execução, o ETL baixa de novo os arquivos do ano corrente. Os do ano anterior são baixados de novo até 31 de março. Os anos mais antigos vêm da cópia já coletada. O detalhe e as frentes de cada deputado são renovados uma vez por semana, em rodízio. Partido e UF vêm da lista diária da Câmara.
- **Coleta parcial.** Se um arquivo não puder ser atualizado e existir cópia de coleta anterior, o ETL usa a cópia, com a data de captura original. A fonte fica com status `parcial`, e o bloco mostra o aviso. Depois de 3 falhas no mesmo tipo de arquivo, o ETL não tenta de novo naquela execução. Cada coleta tem um orçamento de tempo (`ETL_ORCAMENTO_MINUTOS`): esgotado, o restante vem do cache. O prazo vale também para a requisição que já esperava vaga ou nova tentativa no cliente HTTP. Na Câmara, discursos e frentes são complementares, e um deputado sem detalhe ou histórico coletado, sem cópia anterior, fica fora da publicação até a próxima execução; a fonte fica parcial, não em falha.
- **Lista paginada.** Quando a fonte pagina uma lista que desloca a cada item novo, todas as páginas do período aberto renovam no mesmo dia. Com um dia por página, um item que muda de página entre duas renovações ficaria fora.
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

### ALEGO

1. Universo: JSON anual de detalhes (`/api/transparencia/votacoes.json?todos=true&ano={ano}&tipo=detalhes`), um arquivo por ano da 20ª legislatura (2023–2026).
2. Vínculo: nome do parlamentar na pauta, sem acento, sem pontuação, sem título e sem patente.
3. Entram os blocos `votos` e `participacao_votacoes`.

### ALEPA

1. Universo: votações da lista mensal de votações nominais, desde 2022, com o detalhe de cada uma. Cada detalhe lista os deputados em exercício na data, inclusive quem não votou.
2. Vínculo: nome do deputado na página de votos, sem acento e sem pontuação, igual ao nome do perfil. Grafia diferente da do perfil fica numa tabela fixa, conferida deputado a deputado.
3. `votacoes_nominais`: votações em que o deputado aparece, em qualquer seção, por ano.
4. `votos_registrados`: votos `Sim`, `Não` ou `Abstenção`.
5. `sem_registro`: a seção "NÃO VOTARAM", que entra como `Não votou`.
6. `por_tipo`: contagem de cada seção.

### Limites

- Votação simbólica não tem voto individual e fica fora.
- As casas registram ausência de forma diferente. Os números das casas não são comparáveis entre si.
- No piloto municipal, a orientação de partido e de governo fica `null`: a fonte não publica orientação.
- Na ALRS, a fonte lista só o voto dado (Sim ou Não), por deputado e ano. Sem ausência registrada, o perfil tem os votos e não tem bloco de participação.
- Na ALESC, o extrato de cada votação nominal lista só quem votou, com nome e voto, sem partido. Sem ausência registrada, o perfil tem os votos e não tem bloco de participação. A votação simbólica da ordem do dia não tem voto individual e fica fora.
- Na ALES, as votações nominais saem só em boletim PDF. O ETL não lê PDF.
- Na ALEPA, a seção NÃO VOTARAM não separa ausência de presença sem voto. A página de votos não traz ementa nem orientação de bancada.

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

### Assembleias com total por categoria

- ALRS e ALES não publicam fornecedor nem nota. Cada linha é um total por categoria (ALRS) ou por produto (ALES) em um mês, sem CNPJ. Não é uma nota fiscal, e o total do deputado é a soma dessas linhas.
- ALES: o valor é o solicitado pelo gabinete.
- ALEPE: documento com total e sem nota entra como uma linha, na categoria "Sem detalhamento na fonte".
- ALBA: a lista da verba indenizatória traz o lançamento com número da nota, categoria e valor, sem fornecedor. O fornecedor fica no detalhe do lançamento, que o projeto não coleta; a despesa publicada aponta para esse detalhe. A competência dá o ano e o mês, e a data fica vazia.
- CLDF: os arquivos de verbas indenizatórias do CKAN não entram. Os formatos de data misturam dia e mês, número serial de planilha e texto; em 2026, 112 datas têm dia e mês indistinguíveis. O projeto não adivinha a data.

### Minimização

- CPF de fornecedor pessoa física é descartado. Só CNPJ (14 dígitos) é publicado.
- Sequência de 11 dígitos no nome do fornecedor (CPF no nome de MEI) é removida.
- Nome de passageiro é descartado.

## Proposições

- **Câmara.** Arquivos `proposicoesAutores` dos anos da legislatura, filtrados pelo deputado. Tipo, número, ano, ementa, situação e link vêm de `proposicoes`. Temas vêm de `proposicoesTemas`. O campo `ordem_assinatura` e o campo `proponente` vêm da fonte.
- **Senado.** `/processo?codigoParlamentarAutor={codigo}`, filtrado por apresentação a partir do início da cobertura (2022-01-01). A listagem não traz temas: o campo fica `null`.
- **ALECE.** Listagens HTML paginadas da 31ª legislatura (2023–2026). Tipos: projeto de lei (PL), indicação (PI), decreto legislativo (PDL), lei complementar (PLC), emenda constitucional (PEC) e resolução (PR). O autor vem pelo nome. Sequência de 11 dígitos na ementa (CPF) é removida.
- **ALESC.** Portal e-Legis paginado por iniciativa parlamentar. Cobre a 20ª legislatura, a partir de 2023-02-01. O que é anterior fica fora. Autoria e proponente vêm da listagem; o slug da iniciativa no portal pode ser o apelido ou o título parlamentar.
- **ALBA.** API pública do NoPaper, por ano, de 2022 em diante. Entram todos os tipos com autor ou coautor deputado em exercício. O autor vem pelo nome civil, ligado ao nome civil do perfil, sem a marca "(INATIVO)" que a fonte põe no fim do nome. O primeiro autor é o proponente. Sequência de 11 dígitos na ementa (CPF) é removida.
- **Câmara municipal (piloto).** `materia/materialegislativa?autores={autor}&ano={ano}` do SAPL, por ano da legislatura vigente. O `autor` vem de `base/autor` pelo `object_id` do parlamentar. Entram todos os tipos de matéria, inclusive indicações e requerimentos. Situação: `resultado` da fonte, ou "Em tramitação" e "Tramitação encerrada" pelo campo `em_tramitacao`. Temas ficam vazios: a fonte não classifica.

## Emendas parlamentares (ADR-0030)

- Fonte: `EmendasParlamentares.csv`, no ZIP único `download-de-dados/emendas-parlamentares/UNICO` do Portal da Transparência. Sem chave.
- Entram as emendas individuais de deputados federais e senadores, dos orçamentos a partir de 2022: transferência com finalidade definida e transferência especial.
- Emendas de bancada, de comissão e de relator ficam fora. A fonte não as atribui a um parlamentar.
- Valores como a fonte publica, por emenda: empenhado, liquidado, pago e restos a pagar inscritos, cancelados e pagos. O perfil soma cada campo por ano do orçamento. Um campo nunca é somado a outro.
- Localidade de aplicação como na fonte: município com UF, estado com "(UF)", "Nacional" ou "MÚLTIPLO".
- Vínculo com o perfil: pelo código do autor na fonte. O código liga ao deputado ou senador cujo nome é um dos nomes do autor na fonte, sem acento e sem pontuação. Sem perfil com esse nome, vale o nome de urna de uma candidatura do perfil. Código com mais de um perfil possível fica fora.
- Quando o nome do autor na linha difere do nome mais frequente do código, a emenda mostra o texto da fonte como "Autor na fonte". É o caso da emenda de ex-parlamentar indicada por outro.
- Favorecidos e convênios não entram: o arquivo de favorecidos traz nome de pessoa física.

## Discursos

- **Câmara.** `/deputados/{id}/discursos`. Publica tipo, data, fase, sumário oficial e link do texto quando a fonte informa.
- **Senado.** `/senador/{codigo}/discursos`. Publica tipo de uso da palavra, data, resumo oficial e link do texto.
- **Câmara municipal (piloto).** `sessao/oradorexpediente` e `sessao/orador` do SAPL. A fonte registra só a ordem de fala e a hora: tipo ("Orador do Expediente" ou "Orador das Explicações Pessoais"), data e hora da sessão e observação. Não há texto nem resumo.
- Entidades HTML do texto oficial (`&nbsp;`) viram caractere comum. O conteúdo não muda.

## Eventos e presença em eventos

- **Câmara.** Eventos com presença registrada em `eventosPresencaDeputados`, com tipo, situação e descrição de `eventos` e órgão de `eventosOrgaos`.
- A lista da fonte traz só presentes. O projeto não calcula taxa de presença em evento.
- **Senado.** Sem fonte equivalente na POC: "sem registro na fonte".
- **ALESC.** Presença em sessão plenária ordinária, pela página de presença de cada sessão no e-Legis. A fonte lista os deputados da sessão como "Presente" ou "Ausência justificada", e as duas situações entram: `registrada` e `ausencia_justificada`. Na contagem por tipo do perfil, a ausência justificada conta à parte. A sessão extraordinária não tem presença publicada e fica fora. O projeto não calcula taxa de presença.
- **ALBA.** Presença em sessão plenária, pela página de presença de cada sessão no NoPaper. A fonte lista os deputados da sessão como "Presente", "Falta Justificada" ou "Falta", e as três situações entram: `registrada`, `ausencia_justificada` e `falta`. Na contagem por tipo do perfil, a ausência justificada e a falta contam à parte da presença registrada. A situação "Licenciado" fica fora por indicar afastamento temporário do mandato e não comparecimento à sessão. O projeto não calcula taxa de presença.
- **Câmara municipal (piloto).** Presença em sessão plenária de `sessao/sessaoplenariapresenca` do SAPL, nas sessões da legislatura vigente já realizadas. Tipo, número e data vêm de `sessao/sessaoplenaria`. A fonte lista só presentes. O projeto não calcula taxa de presença.

## Órgãos e frentes

- **Câmara.** `orgaosDeputados-L{legislatura}` de cada legislatura coberta (cargo, início e fim) e `/deputados/{id}/frentes` filtrado pelas legislaturas cobertas.
- **Senado.** `/senador/{codigo}/comissoes`, com participação ativa em algum momento desde o início da cobertura (2022-01-01).
- **ALMT.** Composição da Mesa Diretora por sessão legislativa da 20ª Legislatura (2023 a 2026), com cargos institucionais (Presidente, Vice-Presidentes e Secretários) e vigência anual de cada biênio regimental.
- **ALEMS.** Composição da Mesa Diretora da 12ª Legislatura (2023–2027), com cargos institucionais (Presidente, Vice-Presidentes e Secretários) e vigência regimental dos biênios.
- **ALPB.** Composição da Mesa Diretora nas sessões legislativas da 20ª Legislatura (2023–2027) via SAPL, com cargos institucionais (Presidente, Vice-Presidentes, Secretários e Corregedores).
- **ALEAM.** Composição da Mesa Diretora nas sessões legislativas da 20ª Legislatura (2023–2027) via SAPL, com cargos institucionais (Presidente, Vice-Presidentes, Secretários, Ouvidor e Corregedor).
- **ALEAL.** Composição da Mesa Diretora nas sessões legislativas da 20ª Legislatura (2023–2027) via SAPL, com cargos institucionais (Presidente, Vice-Presidentes, Secretários e Suplentes).
- **Câmara municipal (piloto).** `comissoes/participacao` do SAPL por vereador, com a comissão e o período vindos de `comissoes/composicao` (coletada por período), `comissoes/comissao` e `comissoes/periodo`. Papel: cargo da fonte (`Presidente`, `Membro`, `Relator`, `Suplente`); membro não titular recebe o sufixo "(suplente)". Entram as participações cujo fim é posterior ao início da cobertura ou está aberto.

## Candidaturas

- Fonte: arquivos `consulta_cand` e `bem_candidato` do TSE das eleições gerais que alimentam mandatos vigentes: as dos últimos 8 anos (mandato de senador). Em 2026: 2018, 2022 e 2026. A lista de anos sai do calendário eleitoral (anos com resto 2 na divisão por 4), sem ano fixo no código.
- Cargos lidos: Presidente, vice, governador, vice, senador, suplentes de senador e deputado federal.
- Resultado: `DS_SIT_TOT_TURNO` do último turno disputado.
- Bens: soma de `VR_BEM_CANDIDATO` do candidato. A descrição de cada bem não é publicada.
- Plano de governo (Presidência e governos estaduais): arquivo PDF dentro do ZIP `proposta_governo_{ano}_{UF}.zip` do TSE. O perfil publica a URL do ZIP e o caminho do arquivo. O ETL confere o nome no índice do ZIP: `{UF}/{ANO}{UF}{SQ}.pdf` em 2022 e `{UF}/{ANO}{UF}{SQ}_01.pdf` em 2026. Vale o primeiro arquivo do candidato.
- Planos de governo: só das duas eleições mais recentes (2022 e 2026). Planos de 2018 ficam fora da POC.
- Quando o ZIP da fonte não traz o arquivo do candidato, o campo fica "sem registro na fonte". Em 2022, 1 dos 28 eleitos para cargo executivo está nessa situação.
- Vínculo com o perfil: CPF, em memória, para deputados. Nome civil, UF e data de nascimento, em memória, para senadores. O vínculo entre eleições usa CPF. Quando o TSE mascara o CPF (arquivo de 2024), o título de eleitor, completo no mesmo arquivo, traz o CPF da mesma pessoa em outra eleição. Isso só vale se a correspondência for inequívoca: um CPF por título, um título por CPF e a mesma data de nascimento. Sem ela, o vínculo usa nome civil e data de nascimento, também em memória. Quem passou de uma casa a outra dentro da cobertura (deputado que virou senador ou vereador) tem um perfil só, com os blocos das duas casas; a ponte é o CPF ou, sem ele, o nome civil com a data de nascimento. Nem CPF, nem título, nem data de nascimento são gravados.
- Redes e sites (ADR-0031): endereços que o próprio candidato declarou ao TSE no registro da candidatura, do arquivo `rede_social_candidato`. Só o endereço é publicado, com o nome da rede tirado do domínio. O site não lê nem arquiva o conteúdo das páginas.
- Ficam fora: endereço que leva a telefone ou e-mail (WhatsApp, exceto canal; Telegram por número; `tel:` e `mailto:`), texto que não é endereço e o que passa de 20 endereços por candidatura, na ordem declarada. Esquema e domínio vão para minúsculas; o caminho fica como a fonte publica.
- O topo do perfil mostra os endereços da candidatura mais recente que declarou algum, com o ano da eleição. O endereço é o declarado naquela eleição: a conta pode ter mudado depois.
- **Piloto municipal.** Eleições de 2020 e 2024 (`consulta_cand` e `bem_candidato`), filtradas pelo município piloto. O SAPL não publica CPF: a candidatura mais recente com o mesmo nome civil ou nome de urna normalizado, dentro do município, serve de ponte; as demais eleições da pessoa vêm pelo CPF dessa candidatura, em memória, como nas outras casas. Planos de governo municipais: só a partir de 2022, como no federal; 2020 fica sem plano.

## Financiamento de campanha (ADR-0032)

- Fonte: prestação de contas eleitorais dos candidatos, do TSE. Entradas de receitas e de despesas contratadas: CSV `_BRASIL` na eleição geral e `_SP` na municipal.
- Candidaturas: as mesmas do bloco de candidaturas, ligadas ao perfil pela chave `SQ_CANDIDATO`.
- Receitas: total, quantidade e soma por fonte (`DS_FONTE_RECEITA`: Fundo Especial, Fundo Partidário, outros recursos) e por origem (`DS_ORIGEM_RECEITA`). Fonte e origem repartem o mesmo total: o site não soma uma com a outra.
- Despesas contratadas: total, quantidade e soma por tipo (`DS_ORIGEM_DESPESA`), com os até 10 maiores fornecedores com CNPJ pelo valor contratado.
- Cada receita e cada despesa contam uma vez, pela chave da fonte (`SQ_RECEITA` e `SQ_DESPESA`). Valores como a fonte registra.
- Doador e fornecedor pessoa física entram só nas somas. Nome e CPF nunca são publicados. Fornecedor com CNPJ aparece pelo nome da Receita Federal, quando a fonte traz, sem a sequência de CPF do nome de MEI.
- Ficam fora: despesas pagas (regime de caixa) e doador originário.
- A eleição mais recente renova por semana: a prestação final chega até o 30º dia depois da eleição, ou até o 20º depois do segundo turno, e ainda pode ser retificada.

## Executivo

- **Mandatos.** Datas de início e fim vêm de `contrib/mandatos.yaml`, com proveniência na EC 111/2021: eleitos em 2022 tomaram posse em 2023-01-01; os mandatos vão até 2027-01-05 (Presidente) e 2027-01-06 (governadores).
- **Governadores.** O TSE registra a eleição, não o exercício do cargo. Renúncia, afastamento ou sucessão dependem de ato estadual. Correção desse tipo entra por contribuição com fonte T0 (diário oficial do estado). A exceção é a candidatura a outro cargo, que o próprio TSE registra e que obriga a renúncia (ADR-0033).
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
