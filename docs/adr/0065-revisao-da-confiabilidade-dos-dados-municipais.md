# ADR-0065: Revisão da confiabilidade dos dados municipais

- **Status**: Proposta
- **Data**: 2026-11-15

## Contexto

O site cobria três municípios: o piloto, com SAPL (ADR-0007), a capital (ADR-0027) e Caraguatatuba (ADR-0057).
Em 2026-11-15, os 137 perfis municipais publicados foram conferidos contra as fontes oficiais.

O que a conferência achou:

| Município | Achado | Medida |
|---|---|---|
| Piloto | Vereadores "em exercício" a mais | O site mostrava 36. A lista de ativos da câmara tem 33, uma pessoa por cadeira. |
| Piloto | Afastamento pelo número do tipo | Um perfil saía com "Fora de exercício (3)". O tipo 3 na fonte é "Cassação". |
| Capital | Condição "Titular" sem base na fonte | Os 55 em exercício saíam como "Titular". Dois têm resultado "SUPLENTE" no TSE em 2024. |
| Capital | Vereador sem candidatura ligada | 4 dos 55 em exercício, todos eleitos em 2024. |
| Caraguatatuba | Nenhum defeito do projeto | 15 em exercício, os 15 eleitos em 2024 no TSE. |

Causas:

- **Piloto, em exercício.** A regra era "mandato vigente sem afastamento, ou na lista de ativos". O titular licenciado segue com o mandato aberto e sem afastamento registrado no SAPL. Ele contava junto com o suplente que ocupa a cadeira.
- **Piloto, afastamento.** O campo `tipo_afastamento` do mandato é o identificador do tipo. A descrição fica em outra lista (`parlamentares/tipoafastamento`), que o projeto não lia.
- **Capital, condição.** O serviço da câmara não diz quem é titular e quem é suplente. O adaptador escrevia "Titular" para todo vereador em exercício.
- **Capital, vínculo.** O serviço só tem o nome parlamentar. O vínculo exigia nome parlamentar igual ao nome de urna ou ao nome civil. Os quatro têm nome de urna diferente: uma palavra só, ou um sobrenome que não está no nome parlamentar.

## Decisão

1. **Piloto: em exercício é quem a câmara lista como ativo.** Sem a lista, vale a regra anterior, do mandato vigente sem afastamento.
2. **Piloto: mandato vigente de quem não está na lista de ativos sai como "Fora de exercício".** A data de fim do mandato segue a da fonte.
3. **Piloto: o afastamento sai pela descrição do tipo**, lida de `parlamentares/tipoafastamento`. Tipo sem descrição na lista sai como "Fora de exercício", sem o número.
4. **Capital: a condição do mandato fica vazia.** O projeto não escreve "Titular" nem "Suplente" quando a fonte não diz.
5. **Capital: último recurso do vínculo com o TSE.** Sem nome de urna nem nome civil igual ao nome parlamentar, vale a candidatura a vereador que cumpre tudo isto:
   - é do mesmo município e foi eleita na eleição municipal vigente;
   - é do mesmo partido que a câmara informa hoje;
   - todas as palavras do nome de urna estão no nome parlamentar, ou todas as do nome parlamentar estão no nome de urna ou no nome civil;
   - é a única pessoa que cabe na regra. Com duas, não há vínculo, e o log registra o nome.
6. **Título não conta como palavra** nessa regra: "Dr.", "Professor", "Pastor", "Sargento", "Vereador", "Júnior", "Filho" e semelhantes.
7. **A mesma regra vale em Paulínia** (ADR-0066), depois do nome civil e do nome de urna.
8. **O catálogo não consulta fonte com status "bloqueado"** na revalidação de endereços (`fetch fontes`). É a fonte que recusa o projeto, por resposta ou por `robots.txt`.

## Consequências

- O piloto passa a mostrar 33 vereadores em exercício. Os três que saem da contagem seguem com perfil, como "Fora de exercício".
- O perfil cassado do piloto passa a dizer "Fora de exercício (Cassação)", que é o registro da câmara.
- Os 55 vereadores da capital perdem o rótulo "Titular".
- Os quatro vereadores da capital sem vínculo ganham candidaturas e financiamento de campanha.
- A regra do item 5 relaxa a recusa de vínculo por palavras da capital (ADR-0053). O partido, a eleição vigente e a exigência de pessoa única limitam o risco.

## Limitações

- Vereador que mudou de partido depois da eleição não casa pela regra do item 5.
- Suplente em exercício não casa pela regra do item 5: ela só olha os eleitos.
- A capital não tem o nome civil na fonte. Com dois eleitos possíveis no mesmo partido, o vereador fica sem vínculo.
- Na capital, o início do mandato do suplente é o da legislatura, como o serviço informa. A data real da posse não é publicada pelo serviço.
- Em Caraguatatuba, a ficha da câmara grafa o fim do mandato de dois jeitos na mesma legislatura: 2028-12-31 e 2029-01-01. O site repete a fonte.
- Caraguatatuba depende da coleta local cifrada (ADR-0034): os dados só atualizam quando ela roda.
- Não conferido: voto a voto e proposição a proposição contra a fonte. A conferência olhou quem está em exercício, mandatos, vínculo com o TSE e marcas de dado desatualizado.

## Alternativas consideradas

- **Piloto: tirar do site quem não está na lista de ativos**: descartada. Quem exerceu mandato coberto mantém perfil (ADR-0006).
- **Capital: deduzir "Titular" ou "Suplente" do resultado no TSE**: descartada. Mistura duas fontes num campo que diz o que a câmara registra, e o suplente pode ter sido efetivado.
- **Capital: casar por palavras sem exigir partido e eleição**: descartada. É o caminho que o ADR-0053 recusou, por ligar homônimos parciais.
- **Tabela manual de vínculos**: fica para depois. Hoje `contrib/` só aceita mandatos.
