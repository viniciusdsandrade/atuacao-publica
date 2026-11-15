# ADR-0065: Revisão da confiabilidade dos dados municipais

- **Status**: Aceita
- **Data**: 2026-11-15

## Contexto

O site cobria três municípios: o piloto, com SAPL (ADR-0007), a capital (ADR-0027) e Caraguatatuba (ADR-0057).
Em 2026-11-15, os 137 perfis municipais publicados foram conferidos contra as fontes oficiais.

O que a conferência achou:

| Município | Achado | Medida |
|---|---|---|
| Piloto | Vereadores "em exercício" a mais | O site mostrava 36. A lista de ativos da câmara tem 33, uma pessoa por cadeira. |
| Piloto | Afastamento pelo número do tipo | Um perfil saía com "Fora de exercício (3)". O tipo 3 na fonte é "Cassação". |
| Piloto | Voto com grafia dupla e marca sem sentido | Nos 50 perfis: 169 votos "Nao" ao lado de 1.860 "Não"; 40 "[", 40 "]" e 6 "-1". |
| Piloto | Tipo de sessão com palavra repetida | 33 presenças em "Sessão Sessão de Julgamento". |
| Capital | Condição "Titular" sem base na fonte | Os 55 em exercício saíam como "Titular". Dois têm resultado "SUPLENTE" no TSE em 2024. |
| Capital | Vereador sem candidatura ligada | 4 dos 55 em exercício, todos eleitos em 2024. |
| Caraguatatuba | Nenhum defeito do projeto | 15 em exercício, os 15 eleitos em 2024 no TSE. |

Causas:

- **Piloto, em exercício.** A regra era "mandato vigente sem afastamento, ou na lista de ativos". O titular licenciado segue com o mandato aberto e sem afastamento registrado no SAPL. Ele contava junto com o suplente que ocupa a cadeira.
- **Piloto, afastamento.** O campo `tipo_afastamento` do mandato é o identificador do tipo. A descrição fica em outra lista (`parlamentares/tipoafastamento`), que o projeto não lia.
- **Piloto, votos.** O campo do voto na fonte é texto livre. O voto "Nao" não contava como voto registrado. As marcas "[" e "]" estão em itens que a câmara registra como "Não votado": não houve votação, e o site mostrava um voto.
- **Piloto, tipo de sessão.** O site punha "Sessão" na frente do tipo, e a câmara tem um tipo que já se chama "Sessão de Julgamento".
- **Capital, condição.** O serviço da câmara não diz quem é titular e quem é suplente. O adaptador escrevia "Titular" para todo vereador em exercício.
- **Capital, vínculo.** O serviço só tem o nome parlamentar. O vínculo exigia nome parlamentar igual ao nome de urna ou ao nome civil. Os quatro têm nome de urna diferente: uma palavra só, ou um sobrenome que não está no nome parlamentar.

### Conferência por amostra, registro a registro

Feita em 2026-11-15, com os registros mais recentes de cada perfil da amostra, contra a fonte:

| Município | O que foi comparado | Resultado |
|---|---|---|
| Piloto | Voto individual de 4 vereadores, contra `sessao/votoparlamentar` | 40 de 40 iguais |
| Piloto | Presença em sessão e data da sessão, de 4 vereadores | 23 de 23 iguais; 1 consulta sem resposta |
| Piloto | Número, ano, ementa e autoria de proposições | 20 de 20 iguais; 1 consulta sem resposta |
| Piloto | Combustível e material de escritório de 1 vereador em 2026 | Contagem e soma iguais nas duas categorias |
| Capital | Proposições de 4 vereadores, na lista de cada um no serviço da câmara | 80 de 80 presentes |
| Caraguatatuba | Marcas de presença de 6 listas de sessão | 6 de 6 iguais, com 15 presenças cada |
| Caraguatatuba | Ementa na página da matéria, de 3 vereadores | 9 de 9 iguais |
| Caraguatatuba | Diárias de 2026, linha a linha pela regra de nomes do ADR-0057 | 45 diárias e R$ 37.150,00 na fonte e no site |
| Piloto | Comissão, início e fim de 1 vereador, contra `comissoes/participacao` | 41 de 41 iguais |
| Piloto | Data da sessão e ordem do orador nos discursos de 1 vereador | 8 de 8 iguais |
| Piloto | Número, data e ementa de atos normativos | 6 de 6 iguais; 662 atos de 2026 no site e 667 na fonte no dia seguinte à captura |
| Piloto e Caraguatatuba | Despesa da prefeitura em 3 meses, contra o TCE-SP | Contagem e soma iguais nos 3 |
| Capital | Órgãos e comissões de 5 vereadores, no serviço da câmara | 11 de 11 presentes |
| Caraguatatuba | Comissões atuais, na página do portal | 16 de 16 presentes |
| Caraguatatuba | Cargo e data de início na ficha do sistema legislativo, de 3 vereadores | 8 de 8 iguais |
| Os três | Receitas e despesas de campanha de 2024, dos 125 perfis com financiamento, contra os arquivos do TSE no cache | 250 de 250 totais iguais |

Nenhum registro da amostra diferiu da fonte.
Os defeitos achados estão na contagem de quem está em exercício, nos rótulos e na grafia, e não no conteúdo dos registros.

## Decisão

1. **Piloto: em exercício é quem a câmara lista como ativo.** Sem a lista, vale a regra anterior, do mandato vigente sem afastamento.
2. **Piloto: mandato vigente de quem não está na lista de ativos sai como "Fora de exercício".** A data de fim do mandato segue a da fonte.
3. **Piloto: o afastamento sai pela descrição do tipo**, lida de `parlamentares/tipoafastamento`. Tipo sem descrição na lista sai como "Fora de exercício", sem o número.
4. **Piloto: voto com grafia única.** "Sim", "Não" e "Abstenção" saem numa grafia só, qualquer que seja a caixa e o acento na fonte. Valor sem nenhuma letra vira ausência de registro. Os outros valores saem como a fonte grafa.
5. **Piloto: item "Não votado" fica fora dos votos** e da contagem de votações nominais.
6. **Piloto: o tipo de sessão que já começa com "Sessão" não ganha a palavra de novo.**
7. **Capital: a condição do mandato fica vazia.** O projeto não escreve "Titular" nem "Suplente" quando a fonte não diz.
8. **Capital: último recurso do vínculo com o TSE.** Sem nome de urna nem nome civil igual ao nome parlamentar, vale a candidatura a vereador que cumpre tudo isto:
   - é do mesmo município e foi eleita na eleição municipal vigente;
   - é do mesmo partido que a câmara informa hoje;
   - todas as palavras do nome de urna estão no nome parlamentar, ou todas as do nome parlamentar estão no nome de urna ou no nome civil;
   - é a única pessoa que cabe na regra. Com duas, não há vínculo, e o log registra o nome.
9. **Título não conta como palavra** nessa regra: "Dr.", "Professor", "Pastor", "Sargento", "Vereador", "Júnior", "Filho" e semelhantes.
10. **A mesma regra vale em Paulínia** (ADR-0066), depois do nome civil e do nome de urna.
11. **O catálogo não consulta fonte com status "bloqueado"** na revalidação de endereços (`fetch fontes`). É a fonte que recusa o projeto, por resposta ou por `robots.txt`.

## Consequências

- O piloto passa a mostrar 33 vereadores em exercício. Os três que saem da contagem seguem com perfil, como "Fora de exercício".
- O perfil cassado do piloto passa a dizer "Fora de exercício (Cassação)", que é o registro da câmara.
- Os 55 vereadores da capital perdem o rótulo "Titular".
- Os quatro vereadores da capital sem vínculo ganham candidaturas e financiamento de campanha.
- No piloto, os votos "Nao" passam a contar como voto registrado, e os itens não votados saem da contagem de votações nominais.
- A regra do item 8 relaxa a recusa de vínculo por palavras da capital (ADR-0053). O partido, a eleição vigente e a exigência de pessoa única limitam o risco.

## Limitações

- Vereador que mudou de partido depois da eleição não casa pela regra do item 8.
- Suplente em exercício não casa pela regra do item 8: ela só olha os eleitos.
- A capital não tem o nome civil na fonte. Com dois eleitos possíveis no mesmo partido, o vereador fica sem vínculo.
- Na capital, o início do mandato do suplente é o da legislatura, como o serviço informa. A data real da posse não é publicada pelo serviço.
- Em Caraguatatuba, a ficha da câmara grafa o fim do mandato de dois jeitos na mesma legislatura: 2028-12-31 e 2029-01-01. O site repete a fonte.
- Caraguatatuba depende da coleta local cifrada (ADR-0034): os dados só atualizam quando ela roda.
- A conferência dos registros foi por amostra, e não registro a registro em todos os perfis. Só o financiamento de campanha de 2024 foi conferido em todos.
- O financiamento foi comparado com o recorte dos arquivos do TSE no cache do projeto, e não com um segundo download da fonte.
- A capital não tem votos, presença nem despesas por vereador no site: não havia o que conferir nesses blocos.

## Alternativas consideradas

- **Piloto: tirar do site quem não está na lista de ativos**: descartada. Quem exerceu mandato coberto mantém perfil (ADR-0006).
- **Capital: deduzir "Titular" ou "Suplente" do resultado no TSE**: descartada. Mistura duas fontes num campo que diz o que a câmara registra, e o suplente pode ter sido efetivado.
- **Capital: casar por palavras sem exigir partido e eleição**: descartada. É o caminho que o ADR-0053 recusou, por ligar homônimos parciais.
- **Tabela manual de vínculos**: fica para depois. Hoje `contrib/` só aceita mandatos.
