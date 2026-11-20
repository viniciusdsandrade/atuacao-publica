# ADR-0068: Conferência dos votos das assembleias em SAPL e dos blocos de outra esfera nos perfis municipais

- **Status**: Aceita
- **Data**: 2026-11-17

## Contexto

O ADR-0063 trouxe os votos de ALPB, ALEAM, ALERO e ALEPI sem conferência contra a fonte.
Só a ALEAC teve ensaio local completo.
O ADR-0067 conferiu os dados municipais, mas deixou de fora os blocos de outra esfera em perfil fundido.

A conferência usou o pacote publicado em 2026-11-17 às 07:10 UTC.
Cada registro foi comparado com a fonte oficial, numa leitura separada da coleta do projeto.

### Votos das assembleias em SAPL

A coleta lê `sessao/votoparlamentar/?data_hora__year={ano}`, em páginas.
A conferência leu a mesma API por outro caminho: `?parlamentar={id}`, todas as páginas de cada deputado com perfil.
Entraram os votos a partir de 2023-02-01, início da legislatura.
A chave é o parlamentar e a votação. Foram comparados o voto, a data e hora, a matéria e o resultado.

| Casa | Votos no site | Resultado |
|---|---|---|
| ALPB | 4.554 | 4.554 iguais |
| ALERO | 7.740 | 7.740 iguais |
| ALEPI | 3.172 | 3.172 iguais; 76 votos dados depois da captura de 2026-11-15 ainda fora |
| ALEAM | 61.309 | 61.309 iguais |

Nenhum voto do site estava ausente da fonte.
Nenhuma votação tinha mais de um voto do mesmo parlamentar na fonte.

### Blocos de outra esfera nos perfis municipais

Nove perfis municipais trazem dado de outra esfera.

- Roberto Alves, vereador do piloto, foi deputado federal na 56ª legislatura. Entram os blocos federais de 2022-01-01 a 2023-01-31.
- Janaina Paschoal, vereadora da capital, foi deputada estadual na 19ª legislatura da ALESP. Entram os blocos de 2022.
- Sete vereadores da capital foram eleitos deputado federal em 2026. Entra o mandato com posse prevista.

O texto do art. 29 da Constituição nos mandatos de prefeito vem do `planalto-legislacao`, mas é da esfera municipal e não entrou aqui.

| Perfil | Bloco | Comparado com | Resultado |
|---|---|---|---|
| Roberto Alves | Mandato federal | `/deputados/{id}/historico` | igual: titular de 2019-02-01 a 2023-01-31 |
| Roberto Alves | 389 votos | `votacoesVotos-2022.csv` | 389 iguais em votação, voto e data |
| Roberto Alves | Participação: 427 votações, 38 sem registro | `votacoes-2022.csv` | 427 votações nominais do plenário desde a reassunção, em 2022-03-10 |
| Roberto Alves | 146 presenças | `eventosPresencaDeputados-2022.csv` e `-2023.csv` | 146 iguais |
| Roberto Alves | 382 despesas da cota, R$ 394.649,94 | `/deputados/{id}/despesas` da 56ª legislatura | 382 iguais em documento e valor |
| Roberto Alves | 42 proposições | `/proposicoes?idDeputadoAutor` | 42 iguais em tipo, número, ano e ementa |
| Roberto Alves | 6 discursos | `/deputados/{id}/discursos` | 6 iguais |
| Roberto Alves | 26 cargos em órgãos | `orgaosDeputados-L56.json` e `-L57.json` | ver o defeito 1 |
| Roberto Alves | 262 frentes | `/deputados/{id}/frentes`, 56ª legislatura | 262 iguais |
| Roberto Alves | 32 emendas | `EmendasParlamentares.csv` da CGU | 32 iguais nos seis valores |
| Janaina Paschoal | Mandato estadual | portal de parlamentares da ALESP | igual: de 2019-03-15 a 2023-03-14 |
| Janaina Paschoal | 38 votos em comissão, 16 despesas e 52 proposições | arquivos abertos da ALESP, nas cópias do Internet Archive (conferência de 2026-11-20) | 38 votos iguais em votação, data e voto; 16 despesas iguais em ano, mês, categoria, valor, fornecedor e CNPJ (R$ 5.143,65); 51 proposições iguais e uma repetida (defeito 5) |
| Sete eleitos em 2026 | Mandato federal com posse prevista | resultado do TSE e `/legislaturas/57` | 7 iguais: eleitos, posse em 2027-02-01, 58ª legislatura |

### Defeitos e diferenças achados

1. **Câmara, cargo em órgão repetido.** O cargo que atravessa a troca de legislatura vem igual no arquivo das duas legislaturas. O projeto juntava os arquivos sem tirar a repetição. No perfil de Roberto Alves, a CMO de 2022-05-04 a 2023-02-01 aparecia duas vezes. Nos arquivos da 56ª e da 57ª legislatura, 53 linhas se repetem, de 53 deputados: 52 da CMO e 1 do GTCL.
2. **Câmara, sinal da despesa na API.** Na 56ª legislatura, a API devolve com sinal negativo o valor líquido de 328 das 382 despesas de Roberto Alves. O arquivo anual de cotas, que o projeto usa, traz o valor positivo. O valor absoluto é igual nas 382. Não é defeito do projeto.
3. **Câmara, fim do cargo diferente entre arquivo e API.** No arquivo da 56ª legislatura, um período de Roberto Alves na CCTI termina em 2021-05-19. A API diz 2022-02-02. O projeto repete o arquivo.
4. **Câmara, sessões canceladas.** A API lista Roberto Alves em 6 sessões canceladas de 2022. O arquivo de presença não as traz, e o site também não.
5. **ALESP, proposição repetida.** O `proposituras.xml` repete o mesmo documento em registros iguais: 197 dos 30.603 documentos de 2022 em diante, na captura de 2024, nenhum com conteúdo diferente. O projeto criava uma proposição por registro. No perfil de Janaina Paschoal, a RCN 1620/2022 aparecia duas vezes. Os autores (`documento_autor.xml`) não se repetem.

## Decisão

1. **Câmara: o cargo em órgão com mesmo órgão, cargo, início e fim entra uma vez**, mesmo vindo do arquivo de duas legislaturas.
2. Os votos das quatro assembleias em SAPL passam a contar como conferidos contra a fonte.
3. Os blocos de Janaina Paschoal na ALESP são conferidos contra as cópias dos arquivos oficiais no Internet Archive, porque o repositório de dados abertos seguia em HTTP 503. Atualização em 2026-11-20: conferidos.
4. **ALESP: cada documento entra uma vez nas proposições**, mesmo que o arquivo o repita.

## Consequências

- Até 53 perfis federais perdem um cargo repetido na lista de órgãos.
- Perfis da ALESP perdem proposições repetidas. Na captura de 2024, os documentos repetidos de 2022 em diante somam 276 vínculos de autor a mais, inclusive de autores que não são deputados.

## Limitações

- A conferência compara o site com a fonte num dia. Voto dado depois da captura entra na renovação semanal da lista do ano.
- Método: a conferência dos blocos de Janaina Paschoal usou as cópias do Internet Archive dos arquivos oficiais, porque o repositório de dados abertos da ALESP respondeu HTTP 503 de 2026-11-17 a 2026-11-20. As cópias são das votações em comissão de 2025-11-01, das reuniões de 2026-01-23, das despesas de 2024-05-21, dos autores de 2024-05-31 e das proposituras de 2024-06-17, e todas cobrem o período dela, de 2022 a 2023-03-14. A API do portal da ALESP não entrou nesta conferência.
- Os cargos em órgãos da Câmara seguem o arquivo da legislatura inteira, inclusive os períodos anteriores a 2022 (docs/METODOLOGIA.md, "Órgãos e frentes").
- Eleito deputado estadual em 2026 não ganha mandato com posse prevista. O ADR-0006 tira a data de posse da legislatura da Câmara, que só vale para deputado federal e senador. A posse estadual segue a constituição de cada estado; em São Paulo, a legislatura começa em 15 de março. Nos perfis municipais, são quatro casos. Correção em 2026-11-19: desde a Emenda Constitucional estadual nº 47/2019, a legislatura de São Paulo começa em 1º de fevereiro a partir de 2027. O ADR-0070 resolve os quatro casos.

## Alternativas consideradas

- **Comparar os votos das assembleias pela mesma consulta anual da coleta**: descartada. Repetiria o caminho da coleta e não pegaria item perdido entre páginas.
- **Tirar a repetição de órgãos pelo nome**: descartada. O mesmo órgão pode ter dois períodos com o mesmo cargo, e os dois são registros.
