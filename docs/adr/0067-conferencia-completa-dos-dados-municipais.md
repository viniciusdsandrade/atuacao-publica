# ADR-0067: Conferência completa dos dados municipais e correções que ela trouxe

- **Status**: Aceita
- **Data**: 2026-11-15

## Contexto

O ADR-0065 conferiu os dados municipais por amostra.
Em 2026-11-15, depois da publicação de Paulínia (ADR-0066), a conferência foi refeita com todos os registros.
O ponto de partida foi o pacote que o workflow `dados` publicou naquele dia: 156 perfis municipais e 1.256 arquivos de detalhe.
Cada registro foi comparado com a fonte oficial, numa leitura separada da coleta do projeto.

### O que conferiu

| Município | Registros do site | Resultado |
|---|---|---|
| Piloto | 74 mandatos | 74 iguais |
| Piloto | 34.666 votos | 34.666 iguais |
| Piloto | 1.053 votações (data, matéria e resultado) | 1.051 iguais; 2 com data diferente da sessão |
| Piloto | 176 resumos anuais de participação | 176 iguais aos votos publicados |
| Piloto | 11.682 presenças em sessão | 11.660 distintas, todas na fonte; 22 repetidas |
| Piloto | 408 sessões (data, hora, tipo e número) | 408 iguais |
| Piloto | 3.589 discursos | 3.589 iguais |
| Piloto | 1.161 cargos em comissão (sigla, início e fim) | 1.161 iguais |
| Piloto | 87.933 proposições (número, ano e ementa) | 87.582 distintas, todas iguais; 351 repetidas |
| Piloto | 20.561 despesas por vereador (data e valor) | 20.561 iguais |
| Piloto | 3.922 atos normativos (número, data e ementa) | 3.911 distintos, todos iguais; 11 repetidos |
| Capital | 55 vereadores em exercício, mandato, partido e nome | 55 iguais |
| Capital | 161 cargos em comissão e na Mesa | 161 iguais |
| Capital | 6.515 proposições (identificação, situação, ementa e data) | 6.505 nas listas do vereador; 20 ementas mudaram na fonte |
| Caraguatatuba | 32 mandatos e 28 cargos na Mesa | 60 iguais à ficha do sistema legislativo |
| Caraguatatuba | 16 cargos em comissões atuais | 16 iguais |
| Caraguatatuba | 1.190 presenças (marca e dia, em 80 listas) | 1.190 iguais |
| Caraguatatuba | 3.859 proposições (tipo, número, ano, data e ementa) | 3.859 iguais |
| Caraguatatuba | 46 diárias (empenho, data e valor) | 46 com linha na fonte |
| Paulínia | 17 vereadores (nome, partido e mandato) e 19 cargos | 36 iguais ao portal |
| Os quatro | 135 meses de despesa de prefeitura, contra o TCE-SP | 135 com contagem e soma iguais |
| Os quatro | 327 candidaturas e os bens declarados, contra os arquivos do TSE | 327 iguais |
| Os quatro | 12 mandatos de prefeito e vice | 12 com candidatura eleita no TSE para o mesmo cargo e eleição |
| Os quatro | 628 registros de financiamento de campanha | ver o defeito 1 |

Nenhum registro publicado estava ausente da fonte, fora os casos de "O que mudou na fonte depois da captura".

### Defeitos achados

1. **Financiamento de campanha, no site inteiro.** O arquivo do TSE repete a chave (`SQ_RECEITA`, `SQ_DESPESA`) a cada item da mesma receita ou despesa. Cada linha tem a sua descrição e o seu valor. O projeto somava só a primeira linha de cada chave (ADR-0032, item 7).
   - Prova: o arquivo `despesas_pagas_candidatos`. Nas candidaturas dos perfis municipais de 2018 a 2026, 2.754 das 2.762 chaves repetidas têm total pago igual à soma das linhas. Nas outras 8 não há pagamento, ou o pagamento é parcial. Em nenhuma o total pago é o da primeira linha.
   - Tamanho: nas candidaturas cobertas, a despesa contratada saía 15,4% menor em 2018, 14,9% em 2020, 15,7% em 2022, 7,0% em 2024 e 15,7% em 2026. Entre 50% e 70% das candidaturas eram afetadas. Nas receitas, de 0,2% a 0,4%.
2. **Financiamento sem lançamento.** A prestação entregue sem movimento vem com uma linha de chave `-1` e valor zero. O site mostrava "1 lançamento" de R$ 0,00, com categoria "(vazio na fonte)".
3. **Piloto, listas paginadas.** A lista de matérias por autor não vem por id nem por data: a matéria nova entra no meio e desloca as seguintes de página. Cada página renovava num dia da semana. A matéria que mudava de página entre duas renovações sumia, e a que voltava aparecia duas vezes.
   - No site: 351 proposições, 22 presenças e 11 atos normativos repetidos.
   - Fora do site: 381 proposições apresentadas antes da captura, 28 presenças de sessões antigas e 5 decretos de 2026.
   - Num vereador, as 24 matérias de 2026 que faltavam estavam no começo da 4ª e da 7ª páginas da lista.
4. **Capital, projeto que virou norma.** A lista de tramitação do serviço segue trazendo o projeto que já virou decreto ou resolução. A lista anual registra a norma (`tipoNorma`, `numeroNorma`, `anoNorma`, `dataNorma`). O site mostrava "Em tramitação" em 390 das 6.515 proposições: 376 PDL e 14 PR.
5. **Capital, lista anual fechada.** A lista de ano passado ficava no cache para sempre. A fonte muda a ementa e registra a norma anos depois: 20 ementas do site estavam sem a nota que a câmara acrescentou.
6. **Piloto, data do voto.** Em 2 das 1.053 votações, o item votado traz uma data anterior à da sessão, num dia sem sessão. É a data em que a pauta foi montada. O site datava os votos por ela.
7. **Paulínia, nota de despesas.** A nota dizia que o projeto não tinha fonte validada. O portal da Câmara declara que não há cota parlamentar nem verba indenizatória.

### O que mudou na fonte depois da captura

- Piloto: 90 votos das últimas sessões, 7 presenças, 6 discursos, 143 proposições, 24 despesas e 11 atos normativos entraram na fonte depois da coleta semanal de cada lista.
- Capital: 10 proposições saíram das listas do vereador e 9 entraram depois da captura.
- TSE: os arquivos de prestação de contas de 2022, 2024 e 2026 foram republicados em 2026-11-15, dois dias depois da captura. 16 dos 628 registros de financiamento mudaram de valor com a republicação.

### O que fica fora por regra do projeto

- Piloto: 1.388 votos de itens que a câmara registra como "Não votado" (ADR-0065).
- Piloto: 2 decretos que a fonte lista em 2025 com data de 1946, fora da cobertura.
- Caraguatatuba: 6 listas de presença sem marcação, 4 delas de sessões futuras, e 10 marcas de licença.
- Caraguatatuba: 1 diária de 2026 com cargo "VEREADORA" na fonte. O nome do beneficiário não corresponde a nenhum vereador do sistema legislativo. O site não a atribui a ninguém (ADR-0057).

## Decisão

1. **Financiamento: o valor soma todas as linhas da chave.** A quantidade conta cada chave uma vez. Vale para o total, para as somas por fonte, por origem e por tipo, e para os maiores fornecedores. Substitui o item 7 do ADR-0032.
2. **Financiamento: a linha de chave `-1` e valor zero não conta.** O registro sai com total zero e quantidade zero.
3. **Piloto: as páginas de uma lista renovam todas na mesma execução.** No dia de rodízio da lista, ou já, quando as cópias em cache são de coletas com mais de 12 horas de diferença. Vale também para a lista de período fechado.
4. **Piloto: o item repetido em duas páginas entra uma vez**, pelo `id`.
5. **Capital: o projeto da lista de tramitação que a lista anual registra como norma sai com a norma**, como "Decreto nº 90/2024, de 2024-10-29". O tipo sai como a fonte grafa.
6. **Capital: as listas anuais renovam toda semana**, inclusive as de anos passados.
7. **Piloto: quando o item votado traz data anterior à da sessão, vale a data da sessão.** Nos demais casos segue a data do item.
8. **Paulínia: a nota do bloco de despesas repete a declaração da Câmara**, com a data em que foi lida.

## Consequências

- A despesa contratada de campanha sobe em mais da metade das candidaturas. O total passa a bater com o total pago que a fonte registra.
- Na primeira execução depois desta mudança, o piloto baixa de novo todas as listas com páginas de dias diferentes: cerca de 1.500 chamadas, dentro do orçamento de tempo do passo.
- As 380 proposições da capital que ainda estão na lista de tramitação passam a mostrar o decreto ou a resolução.
- A capital baixa 5 listas anuais por semana, em rodízio.

## Limitações

- A conferência compara o site com a fonte num dia. O que a fonte muda depois só entra na coleta seguinte: até 7 dias nas listas semanais.
- Nas receitas não há arquivo de pagamento para a prova. A regra vale por ter a mesma estrutura: as 12 chaves repetidas de 2024 são doações estimáveis com um item por linha, nenhuma com linhas idênticas.
- A republicação de eleição fechada pelo TSE só entra quando o recorte é refeito (ADR-0032).
- Na capital, o projeto que sai da lista de tramitação sem virar lei some do perfil: o serviço não informa o autor dele.
- No piloto, a lista de matérias de ano fechado não renova. A situação de uma matéria antiga pode ficar desatualizada.
- Paulínia segue sem proposições, votações e presença: a Câmara os publica só no SISCAM, cujo `robots.txt` recusa o projeto (ADR-0066).
- A capital não tem votos, presença nem despesas por vereador no serviço da câmara.
- Blocos de outra esfera em perfil fundido (mandato federal ou estadual anterior) não entraram nesta conferência.

## Alternativas consideradas

- **Manter "uma vez por chave" no financiamento**: descartada. O total pago da própria fonte mostra que as linhas são itens, e não cópias.
- **Tirar só as linhas idênticas em todas as colunas**: descartada. Nas 96 chaves de 2024 com linhas idênticas, o total pago também é a soma das linhas.
- **Piloto: uma pasta por lista, com a política de lista que já existe**: descartada. Muda o caminho de todos os arquivos no cache e força a recoleta dos anos fechados.
- **Capital: escrever "Decreto Legislativo" no lugar de "Decreto"**: descartada. O site repete o tipo que a fonte registra.
- **Piloto: datar o voto sempre pela sessão**: descartada. Em sessão de mais de um dia, a data do item é a mais precisa.
