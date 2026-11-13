# ADR-0053: Vínculo por palavras do nome só com pessoa única

- Status: aceita
- Data: 2026-11-13

## Contexto

Em 23 assembleias, o vínculo do deputado com o TSE tinha um último recurso.
Sem nome civil nem nome de urna igual ao nome parlamentar, valia a primeira candidatura cujo nome continha todas as palavras do nome parlamentar.
A busca percorria um mapa sem ordem definida e parava no primeiro achado, sem conferir se havia outro.
Com dois candidatos possíveis, o escolhido dependia da ordem do mapa.
A regra dos vereadores da capital já recusava esse caminho: casar por palavras soltas liga homônimos parciais e atribui candidaturas e bens a outra pessoa.
O trecho se repetia em 90 pontos do `Vinculo`.

Conferência de 2026-11-13 nos 824 perfis dessas casas com vínculo no site: em 783, o nome parlamentar é igual a um nome de urna do perfil.
Nos outros 41, o nome de urna é variação do nome parlamentar, e todos os que estão em exercício têm candidatura a deputado estadual de 2022 na UF, eleita ou suplente.
Nenhum vínculo errado foi achado. Em 3 dos 41, duas pessoas do TSE cabiam na regra.

## Decisão

1. Os 90 trechos viram uma função só, `unicaPorPalavras`.
2. O vínculo por palavras vale quando aponta para uma única pessoa. Pessoa é nome civil e data de nascimento no TSE.
3. Com mais de uma pessoa, vale a única com candidatura eleita no ciclo eleitoral vigente (ADR-0052). Sem uma única eleita, não há vínculo por esse caminho, e o log registra o nome parlamentar e a quantidade de pessoas.
4. Antes das palavras, vale o nome igual a menos dos espaços ("Donatrampi" e "DONA TRAMPI"), com a mesma exigência de pessoa única.
5. Nome parlamentar sem nenhuma palavra não casa com ninguém.
6. A ordem das etapas anteriores não muda: nome civil da casa, nome de urna e nome sem título.

## Consequências

- Ensaio local de 2026-11-13, com o código anterior e o novo sobre os mesmos dados de 22 das 23 casas: 2.344 perfis nos dois.
- Um perfil mudou: `alese-569` ganhou as candidaturas de 2018, 2022 e 2026, pelo nome igual a menos do espaço. Nenhum perfil perdeu vínculo.
- Nenhum caso caiu no log de mais de uma pessoa: os 3 casos com duas pessoas foram resolvidos pela eleita no ciclo vigente ou por etapa anterior.
- Publicação do CI de 2026-11-13 das 15:27 UTC, com as 23 casas: comparados os 2.523 perfis com os da publicação das 13:04 UTC, só `alese-569` mudou de vínculo. Nenhum perfil perdeu vínculo nem trocou de candidaturas, e o log não registrou caso com mais de uma pessoa.
- A publicação deixa de depender da ordem de um mapa.
- O `Vinculo` perdeu cerca de 490 linhas repetidas.

## Limitações

- A ALEGO ficou fora do ensaio local: a fonte não respondia em 2026-11-13, e não havia cópia local. Na publicação do CI, feita com o cache da casa, os vínculos dela não mudaram.
- Suplente em exercício com homônimo parcial eleito no mesmo ciclo fica sem vínculo por esse caminho, ou ligado ao eleito se só o eleito couber na regra. Não há caso conhecido.
- Nome parlamentar que não é nome de urna, nem parte dele, segue sem vínculo. É o caso de "Carminha Paiva", com nome de urna "CARMINHA".

## Alternativas consideradas

- Remover o vínculo por palavras: descartada. Dos 41 vínculos não exatos, 32 cabem na regra com uma pessoa só, e estão certos.
- Manter a primeira encontrada, com mapa ordenado: descartada. Fica reproduzível, mas continua escolhendo entre homônimos.
- Tabela manual de vínculos por contribuição: fica para depois. Hoje `contrib/` só aceita mandatos.
