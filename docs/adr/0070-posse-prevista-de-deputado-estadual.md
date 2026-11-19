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

Nas outras UFs, um acervo local de legislação serviu para achar o dispositivo e a fonte oficial de cada constituição.
O texto citado em cada regra foi conferido, literalmente, no snapshot do Internet Archive da fonte oficial.
Onde não havia snapshot com o texto, a captura foi pedida ao Internet Archive em 2026-11-19.
A maioria das constituições põe a posse a partir de 1º de fevereiro, como a Constituição Federal para a Câmara.
Há exceções: o Rio Grande do Sul dá posse em 31 de janeiro, e Roraima, desde a Emenda Constitucional estadual nº 91/2023, em 5 de janeiro.
No Distrito Federal, o cargo é deputado distrital, e a posse segue a Lei Orgânica: 6 de janeiro, desde a Emenda à Lei Orgânica nº 131/2024.

## Decisão

1. `contrib/mandatos.yaml` aceita regra de `deputado_estadual` e de `deputado_distrital`, com a UF em `ente`. O schema exige a UF nesses cargos.
2. A regra vale só para os eleitos da UF dela. Ela cita o texto da constituição estadual, com snapshot, como as regras do Executivo.
3. Antes da posse, o eleito ganha o mandato com a situação "Eleito em {ano}; posse prevista em {data}".
4. Depois da posse, vale o mandato que a assembleia registra. Sem ele, o eleito fica com "Eleito em {ano}; sem registro de exercício na Assembleia Legislativa", como o deputado federal no ADR-0006.
5. A primeira regra é a de São Paulo, eleição de 2026: posse em 2027-02-01 e fim em 2031-02-01, início da legislatura seguinte. A fonte é `alesp-legislacao`, o portal de legislação da ALESP (T0).
6. Têm regra, além de São Paulo, 23 UFs. Em AC, AL, AM, AP, BA, CE, ES, GO, MA, MS, MT, PA, PB, PE, PI, PR, RN, RO, SC, SE e TO, a posse é em 2027-02-01; no RS, em 2027-01-31; e em RR, em 2027-01-05. A fonte de cada uma é `constituicao-{uf}`, da assembleia ou do portal de legislação do governo do estado. No AM, a Constituição não fixa a data, e a regra cita o Regimento Interno da ALEAM, art. 4º (`aleam-regimento`). No DF, a regra de `deputado_distrital` cita a Lei Orgânica, art. 66, na redação da Emenda à Lei Orgânica nº 135, de 11/11/2026: posse em 2027-01-06 (`lei-organica-df`, texto compilado do SINJ-DF).
7. Ficam sem regra, até haver fonte com snapshot:
   - MG: a Constituição fixa uma janela, de 1º a 15 de fevereiro. O Regimento Interno da ALMG põe a primeira reunião preparatória em 1º de fevereiro (art. 5º), mas a ALMG recusa conexão de fora do Brasil, e o Internet Archive não a alcança;
   - RJ: a base de textos da ALERJ não entregou o texto em 2026-11-19, e o snapshot da página não o guarda.
8. Quando a constituição não fixa o dia, a regra pode citar o regimento interno da assembleia, com snapshot, como a constituição.
9. Em RR, as notas do texto oficial sobre as ADIs 6654, 6658 e 6703 tratam da reeleição da Mesa, segundo o STF, e não da data da posse.

## Consequências

- Os eleitos deputado estadual ou distrital em 2026 nas 25 UFs com regra ganham o mandato com posse prevista, inclusive os quatro vereadores dos perfis municipais.
- Eleito sem perfil ganha um perfil do TSE, como os deputados federais eleitos.
- As duas UFs do item 7 seguem sem posse prevista até ganharem regra própria.
- No AP, o TSE ainda não marca nenhum eleito de 2026 para deputado, estadual ou federal. A regra do AP só tem efeito quando a totalização entrar.
- Depois da posse, cada adaptador de assembleia precisa da legislatura nova. Até lá, os eleitos ficam sem registro de exercício.

## Alternativas consideradas

- **Data fixa no código, por UF**: descartada. A data muda por emenda, como mudou em São Paulo em 2019, e a regra precisa citar o texto legal.
- **A data da posse federal para todos os estados**: descartada. A posse estadual segue a constituição de cada estado, e São Paulo usou 15 de março até 2023.
- **Só os quatro perfis municipais**: descartada. A regra vale para todos os eleitos da UF, como no deputado federal.
