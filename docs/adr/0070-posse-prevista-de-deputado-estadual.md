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

Nas outras UFs, o acervo `leis` do mantenedor serviu para achar o dispositivo e a fonte oficial de cada constituição.
O texto citado em cada regra foi conferido, literalmente, no snapshot do Internet Archive da fonte oficial.
Onde não havia snapshot com o texto, a captura foi pedida ao Internet Archive em 2026-11-19.
A maioria das constituições põe a posse a partir de 1º de fevereiro, como a Constituição Federal para a Câmara.
Há exceções: o Rio Grande do Sul dá posse em 31 de janeiro, e Roraima, desde a Emenda Constitucional estadual nº 91/2023, em 5 de janeiro.

## Decisão

1. `contrib/mandatos.yaml` aceita regra de `deputado_estadual`, com a UF em `ente`. O schema exige a UF nesse cargo.
2. A regra vale só para os eleitos da UF dela. Ela cita o texto da constituição estadual, com snapshot, como as regras do Executivo.
3. Antes da posse, o eleito ganha o mandato com a situação "Eleito em {ano}; posse prevista em {data}".
4. Depois da posse, vale o mandato que a assembleia registra. Sem ele, o eleito fica com "Eleito em {ano}; sem registro de exercício na Assembleia Legislativa", como o deputado federal no ADR-0006.
5. A primeira regra é a de São Paulo, eleição de 2026: posse em 2027-02-01 e fim em 2031-02-01, início da legislatura seguinte. A fonte é `alesp-legislacao`, o portal de legislação da ALESP (T0).
6. Têm regra, além de São Paulo, 17 UFs: AC, AP, BA, CE, ES, MS, MT, PA, PB, PE, PI, RN, RO, SC, SE e TO, com posse em 2027-02-01, e RS, com posse em 2027-01-31. A fonte de cada uma é `constituicao-{uf}`, da assembleia ou do portal de legislação do governo do estado.
7. Ficam sem regra, até haver fonte e data:
   - AL, MA e PR: o texto do acervo vem de órgão que não publica a norma (polícia militar, universidade e secretaria de ciência e tecnologia);
   - GO e RJ: a fonte oficial monta o texto na hora, e o snapshot não guarda o texto;
   - AM: a Constituição não fixa a data da posse, que fica no Regimento Interno;
   - MG: a Constituição fixa uma janela, de 1º a 15 de fevereiro, e não um dia;
   - RR: a Emenda Constitucional estadual nº 91/2023 fixa 5 de janeiro, e o texto oficial remete à ADI 6654 no STF, cujo efeito não foi conferido;
   - DF: o cargo é deputado distrital, e a regra estaria na Lei Orgânica do Distrito Federal.

## Consequências

- Os eleitos deputado estadual em 2026 nas 18 UFs com regra ganham o mandato com posse prevista, inclusive os quatro vereadores dos perfis municipais.
- Eleito sem perfil ganha um perfil do TSE, como os deputados federais eleitos.
- As nove UFs do item 7 seguem sem posse prevista até ganharem regra própria.
- No AP, o TSE ainda não marca nenhum eleito de 2026 para deputado, estadual ou federal. A regra do AP só tem efeito quando a totalização entrar.
- Depois da posse, cada adaptador de assembleia precisa da legislatura nova. Até lá, os eleitos ficam sem registro de exercício.

## Alternativas consideradas

- **Data fixa no código, por UF**: descartada. A data muda por emenda, como mudou em São Paulo em 2019, e a regra precisa citar o texto legal.
- **A data da posse federal para todos os estados**: descartada. A posse estadual segue a constituição de cada estado, e São Paulo usou 15 de março até 2023.
- **Só os quatro perfis municipais**: descartada. A regra vale para todos os eleitos da UF, como no deputado federal.
