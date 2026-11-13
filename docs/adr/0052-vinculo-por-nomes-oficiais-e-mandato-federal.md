# ADR-0052: Vínculo por nomes oficiais, mandato federal e ciclo eleitoral vigente

- Status: aceita
- Data: 2026-11-13

## Contexto

O ADR-0030 deixou fora do vínculo de emendas o código de autor com mais de um perfil possível e o código sem perfil.
Em 2026-11-12, a publicação tinha 7 códigos fora, com 122 emendas.
Quatro tinham dois perfis com o mesmo nome parlamentar: pai e filho com mandatos em anos diferentes, duas deputadas homônimas de estados diferentes e a mesma pessoa em dois perfis não fundidos.
Três usavam, na fonte, um nome que o perfil não tinha: o nome civil, ou o nome parlamentar com ou sem título ("DR.", "PROFESSORA").

O vínculo do Senado com o TSE procurava só candidatura a senador ou a suplente, na UF do mandato.
Senador eleito antes de 2018 e candidato depois a outro cargo ficava sem candidaturas no perfil.
Se a mesma pessoa voltasse à Câmara, ficava com dois perfis (`sen-5531` e `cam-234406`).

Nas casas estaduais, entre candidaturas com o mesmo nome valia a mais recente.
Com o arquivo de 2026 do TSE, a mais recente passou a ser, em alguns casos, a de um homônimo que só concorreu em 2026.
O perfil `alesc-paulinha` recebeu a candidatura de 2026 de outra pessoa com o mesmo nome de urna.

## Decisão

1. Emendas: o perfil federal guarda, em memória, os nomes parlamentares e os nomes civis que a própria casa publica.
   Na Câmara: `ultimoStatus.nome`, `ultimoStatus.nomeEleitoral` e `nomeCivil`. No Senado: `NomeParlamentar` e `NomeCompletoParlamentar`.
   Perfis fundidos somam os nomes das duas casas.
2. Emendas: a ordem de busca do código do autor passa a ser nome parlamentar, nome de urna, nome civil da casa e, por último, o nome sem título.
   Cada etapa só roda se a anterior não achou perfil. A comparação ignora acento, ponto e vírgula.
3. Emendas: código com mais de um perfil possível só é ligado se um único deles teve mandato federal em todos os anos de orçamento das emendas do código.
   Mandato que alcança qualquer dia do ano conta. Sem um perfil único, o código continua fora.
4. Senado: sem candidatura a senador ou a suplente na UF, vale o CPF da única pessoa do TSE com o mesmo nome civil e a mesma data de nascimento, em qualquer cargo.
   Se o mesmo nome e a mesma data aparecem com dois CPFs, não há vínculo.
5. Casas estaduais: entre candidaturas com o mesmo nome, vale primeiro a eleita, depois a do ciclo eleitoral da legislatura em curso ou anterior, depois a mais recente.
   O ciclo vigente muda em 1º de fevereiro do ano seguinte ao da eleição.
6. Títulos reconhecidos: entram "Doutor", "Doutora", "Engenheiro", "Engenheira", "Eng.", "Ap." e "Apóstolo".
   Na ALEP, o nome de urna sem título só vale quando aponta para uma única pessoa.
7. O nome civil segue só em memória (ADR-0003). Nenhum nome novo é publicado.

## Consequências

- Ensaio local de 2026-11-13, com o código anterior e o novo sobre os mesmos dados: 29.772 emendas individuais de 886 códigos.
- Antes: 29.649 emendas de 879 códigos ligadas, e 7 códigos fora, com 123 emendas. Depois: os 886 códigos ligados, nenhum fora.
- Os 7 códigos conferidos um a um: entre as localidades com UF identificável, a mais frequente é a UF do perfil.
- Os dois códigos "ATILA LIRA" foram para perfis diferentes: orçamentos de 2022 e 2023 no mandato encerrado em 2023-01-31, e de 2024 a 2026 no mandato em exercício.
- `sen-5531` e `cam-234406` passaram a ser um perfil só. O id antigo vira alias (ADR-0006).
- Doze senadores ganharam candidaturas do TSE. Quatro deputados estaduais com título ou vírgula no nome parlamentar também.
- `alesc-paulinha` passou a ter as candidaturas de 2018, 2022 e 2026 da deputada eleita em 2022.
- Dois perfis de eleitos em 2026 (`tse-10002544107` e `tse-240002538368`) eram a mesma pessoa de um perfil de casa legislativa. Passam a alias desse perfil.
- Publicação sobre a anterior, no mesmo ensaio: 2.095 perfis, 18 aliases e nenhum identificador sumido. A trava do ADR-0006 não disparou.

## Limitações

- O ensaio usou o cache local, sem dez assembleias (ALEAC, ALEAL, ALEAM, ALEGO, ALEMS, ALEPI, ALERO, ALESP, ALETO e ALPB). Nelas, o efeito aparece só na publicação do CI.
- Emenda de orçamento de um ano é apresentada no ano anterior. A regra do mandato usa o ano do orçamento, que é o que a fonte publica.
- Código com dois perfis homônimos e mandatos nos mesmos anos continua fora.
- O vínculo por nome civil e data de nascimento supõe que duas pessoas com os dois dados iguais tenham CPFs diferentes na base do TSE. O arquivo de 2024 mascara o CPF (ADR-0003).

## Alternativas consideradas

- Tabela de aliases revisada à mão para os códigos fora (ADR-0030): descartada por ora. Os nomes oficiais das casas resolveram os 7 códigos sem lista manual.
- Desempate de homônimo pela UF das localidades das emendas: descartada. A maior parte das emendas tem localidade "MÚLTIPLO" ou "Nacional".
- Vínculo do Senado por nome civil sem data de nascimento: descartada. Liga homônimos.
