# ADR-0070: Posse prevista de deputado estadual

- **Status**: Proposta
- **Data**: 2026-11-19

## Contexto

O ADR-0006 dá perfil a quem foi eleito e ainda não tomou posse.
A data de posse vem de `contrib/mandatos.yaml` para presidente e governador, e da legislatura da Câmara para deputado federal e senador.
Deputado estadual ficou de fora, porque a posse dele segue a constituição de cada estado.
O ADR-0068 registrou a lacuna nos perfis municipais: quatro vereadores eleitos deputado estadual em 2026, todos em São Paulo.

O ADR-0068 dizia que, em São Paulo, a legislatura começa em 15 de março. Isso valeu até a legislatura iniciada em 2023.
A Emenda Constitucional estadual nº 47, de 14/03/2019, mudou o art. 9º, § 2º, da Constituição do Estado: a posse passa a ser a partir de 1º de fevereiro.
O ADCT da mesma Constituição, art. 1º-A, parágrafo único, encerra a legislatura iniciada em 2023 em 31 de janeiro de 2027 e começa a seguinte em 1º de fevereiro.
O adaptador da ALESP já usa 2027-01-31 como fim da 20ª legislatura.

O texto foi lido em 2026-11-19 no portal de legislação da ALESP, que respondeu 200 enquanto o repositório de dados abertos respondia 503.
O snapshot do Internet Archive de 2026-10-02 traz os dois trechos literais.

## Decisão

1. `contrib/mandatos.yaml` aceita regra de `deputado_estadual`, com a UF em `ente`. O schema exige a UF nesse cargo.
2. A regra vale só para os eleitos da UF dela. Ela cita o texto da constituição estadual, com snapshot, como as regras do Executivo.
3. Antes da posse, o eleito ganha o mandato com a situação "Eleito em {ano}; posse prevista em {data}".
4. Depois da posse, vale o mandato que a assembleia registra. Sem ele, o eleito fica com "Eleito em {ano}; sem registro de exercício na Assembleia Legislativa", como o deputado federal no ADR-0006.
5. A primeira regra é a de São Paulo, eleição de 2026: posse em 2027-02-01 e fim em 2031-02-01, início da legislatura seguinte. A fonte é `alesp-legislacao`, o portal de legislação da ALESP (T0).

## Consequências

- Os eleitos deputado estadual em São Paulo em 2026 ganham o mandato com posse prevista, inclusive os quatro vereadores dos perfis municipais.
- Eleito sem perfil ganha um perfil do TSE, como os deputados federais eleitos.
- As outras 26 UFs seguem sem posse prevista até ganharem regra própria, com o texto da constituição de cada uma.
- Depois de 2027-02-01, o adaptador da ALESP precisa da 21ª legislatura. Até lá, os eleitos de São Paulo ficam sem registro de exercício.

## Alternativas consideradas

- **Data fixa no código, por UF**: descartada. A data muda por emenda, como mudou em São Paulo em 2019, e a regra precisa citar o texto legal.
- **A data da posse federal para todos os estados**: descartada. A posse estadual segue a constituição de cada estado, e São Paulo usou 15 de março até 2023.
- **Só os quatro perfis municipais**: descartada. A regra vale para todos os eleitos da UF, como no deputado federal.
