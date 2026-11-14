# ADR-0058: Sigla de partido unificada pela grafia do TSE

- **Status**: Aceita
- **Data**: 2026-11-13

## Contexto

Cada casa legislativa grafa o partido do seu jeito.
O perfil repetia a grafia da casa, e o site usa esse campo no filtro por partido, nos chips e nos dumps.

Na publicação de 2026-11-13 das 22:58 UTC, o índice tinha 57 grafias para pouco mais de 30 partidos:

| Partido | Grafias no índice |
|---|---|
| União Brasil | `UNIÃO`, `União`, `UNIAO`, `UB`, `UNIÃO BRASIL`, `União Brasil` e `UNI&#227;O` |
| Republicanos | `REPUBLICANOS`, `Republicanos`, `REP`, `REPUB`, `REPUBLICA` e `REPUBLICANO` |
| PCdoB | `PCdoB`, `PC do B`, `PC DO B` e `PCDOB` |
| Solidariedade | `SOLIDARIEDADE`, `SD` e `SDD` |
| Podemos | `PODE`, `PODEMOS` e `Podemos` |
| Sem partido | `S/P`, `S.PART.` e `S/Partido` |

Quem filtrava por "UNIÃO" via 182 dos 224 perfis do partido.
Quatro perfis saíam com entidade HTML no partido, e quatro no endereço da foto.

O TSE também muda de grafia: o arquivo de candidaturas grafa `PC do B` até 2024 e `PCDOB` em 2026.

## Decisão

1. **O perfil mostra a sigla que o TSE usa.** Vale a grafia do arquivo de candidaturas (`SG_PARTIDO`) da eleição mais recente em que o partido aparece.
2. **A comparação ignora caixa, acento, espaço e pontuação.** `União`, `UNIAO` e `uniao` são a mesma sigla.
3. **Nome por extenso e abreviação da casa entram por uma tabela curta**, em `Partidos.java`: `CIDAD`, `PODEMOS`, `PROGRESSISTAS`, `REP`, `REPUB`, `REPUBLICA`, `REPUBLICANO`, `SD`, `SDD`, `UB` e `UNIÃO BRASIL`.
4. **"Sem partido" é um valor.** Quando a casa informa que o parlamentar não tem partido (`S/P`, `S.PART.`, `S/Partido`), o perfil mostra "Sem partido". Não é ausência de registro.
5. **Sigla antiga não vira a do sucessor.** `DEM`, `PSL`, `PPS` e `PRB` continuam como estão: era o partido da pessoa na data.
6. **Grafia sem correspondência sai como a casa publica**, sem entidade HTML, e aparece na saída do `publish`.
7. **Escopo**: o partido do perfil, que vai também para o índice e para os dumps. O `partido_na_data` de cada voto e o partido de cada candidatura continuam como o registro da fonte.
8. **Onde roda**: no vínculo, depois de todos os papéis da pessoa. Os adaptadores continuam gravando a grafia da casa no normalizado.
9. **Endereço de foto**: a publicação troca entidade HTML pelo caractere e põe em percent-encoding o que não é ASCII.

## Consequências

- No ensaio local de 2026-11-13, com 2.369 perfis, a sigla mudou em 173 e o índice ficou com 32 partidos mais "Sem partido". Nenhuma grafia ficou sem correspondência.
- O filtro por partido passa a reunir todos os perfis do partido.
- O PCdoB aparece como `PCDOB`, que é a grafia do TSE em 2026. A grafia muda sozinha se o TSE mudar.
- Uma casa que passar a usar grafia nova, fora da tabela, volta a partir o filtro. A saída do `publish` lista a grafia.

## Limitações

- O partido na data de cada voto não é unificado: a linha do voto mostra a grafia da casa.
- Sem o TSE normalizado, só a tabela vale, com a grafia de reserva dela.
- A tabela é mantida à mão.

## Alternativas consideradas

- **Lista fixa de siglas no código**: descartada. Envelhece a cada registro, fusão ou mudança de grafia. O arquivo do TSE já traz a lista.
- **Unificar em cada adaptador**: descartada. São mais de 30 casas, e três já tinham regra própria, cada uma diferente.
- **Levar sigla antiga para a do partido sucessor**: descartada. Afirmaria uma filiação que a pessoa não teve.
- **Usar a grafia que o partido usa (`PCdoB`)**: descartada. O projeto segue a fonte oficial, e não há lista oficial com essa grafia nos dados abertos.
- **Tratar "sem partido" como ausência de registro**: descartada. A tela diria "sem registro na fonte" quando a fonte registra que não há partido.
