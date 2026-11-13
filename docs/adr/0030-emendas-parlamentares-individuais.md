# ADR-0030: Emendas parlamentares individuais

- Status: aceita
- Data: 2026-11-06

## Contexto

A emenda individual ao orçamento é ato do deputado federal e do senador no exercício do mandato (CF, art. 166, § 9º).
Desde a EC 105/2019, ela pode chegar a estados e municípios por transferência especial ou por transferência com finalidade definida (CF, art. 166-A).
O Portal da Transparência publica, sem chave, um ZIP único com todas as emendas desde 2014: `download-de-dados/emendas-parlamentares/UNICO`.
A URL redireciona para `dadosabertos-download.cgu.gov.br`. Em 2026-11-06, o ZIP tinha 32,5 MB e é regerado a cada dia.
O ZIP traz três CSVs: `EmendasParlamentares.csv`, `EmendasParlamentares_PorFavorecido.csv` e `EmendasParlamentares_Convenios.csv`.
Cada linha do primeiro traz código e nome do autor, tipo, número, localidade de aplicação, função, programa, ação e seis valores: empenhado, liquidado, pago e restos a pagar inscritos, cancelados e pagos.
A fonte identifica o autor por código e nome. Não traz CPF nem outro cadastro.
O nome varia entre linhas do mesmo código. Há grafias diferentes da mesma pessoa ("CAPITAO DERRITE" e "GUILHERME DERRITE").
Há também emenda de ex-parlamentar indicada por outro, escrita como "NOVO (EX-PARLAMENTAR ANTIGO, NOS TERMOS ...)".
A emenda de bancada, a de comissão e a de relator não têm um parlamentar como autor.
O favorecido pessoa física vem com nome e documento mascarado.

## Decisão

1. Entram só as emendas individuais: "Emenda Individual - Transferências com Finalidade Definida" e "Emenda Individual - Transferências Especiais".
2. Entram os orçamentos a partir de 2022, início da cobertura (ADR-0009).
3. Os arquivos de favorecidos e de convênios não entram. O de favorecidos traz nome de pessoa física.
4. O vínculo usa o código do autor. O código liga ao perfil federal (deputado ou senador) cujo nome é um dos nomes do autor na fonte, sem acento e sem pontuação.
5. Sem perfil com esse nome, vale o nome de urna de uma candidatura do perfil. O nome do perfil tem precedência porque é o nome parlamentar, o mesmo que a fonte usa.
6. Código com mais de um perfil possível fica fora, como o código sem perfil. Homônimo não recebe emenda de outra pessoa.
7. Quando o nome do autor na linha difere do nome mais frequente do código, o registro traz o texto da fonte em `autor_na_fonte`. A emenda de ex-parlamentar indicada por outro fica no perfil do autor do código, com esse texto à vista.
8. O perfil traz a contagem por tipo e, por ano do orçamento, a contagem e a soma de cada valor. Cada valor é somado só com ele mesmo.
9. Cada emenda fica no detalhe do ano (`politicos/{id}/emendas-{ano}.json.gz`).
10. Política de cache `SEMANAL` (ADR-0005). Falha na coleta deixa a fonte `cgu` parcial, sem derrubar cartão e viagens.

## Consequências

- No ensaio local de 2026-11-06, o arquivo trouxe 29.680 emendas individuais de 886 códigos de autor desde 2022.
- O vínculo ligou 29.558 delas (99,6%), de 879 códigos, a perfis federais.
- Ficaram fora 7 códigos, com 122 emendas. Quatro têm dois perfis com o mesmo nome: homônimos, ou a mesma pessoa em dois perfis não fundidos. Três não casam com nome nem com nome de urna de perfil.
- Conferência independente: em 815 dos 839 perfis com localidade identificável, ao menos 80% das emendas vão para a UF do perfil. A mediana é 100%.
- O perfil cresce no máximo 2,6 KB. Os arquivos de detalhe somam cerca de 13 MB ao site.
- A mesma pessoa pode ter dois códigos de autor, por exemplo um como deputada e outro como senadora. Cada código é ligado por conta própria.

## Alternativas consideradas

- API do Portal da Transparência: exige chave (`docs/FONTES.md`). O arquivo único dispensa chave e cobre todo o histórico.
- Vínculo pelo nome de cada linha, sem o código: perde as grafias diferentes do mesmo autor.
- Vínculo por palavras soltas do nome: liga homônimos parciais, risco que o vínculo dos vereadores da capital já evita.
- Lista de todas as emendas no perfil: um parlamentar passa de cem emendas, e os maiores perfis já passam do orçamento de tamanho (ADR-0001).
- Tabela de aliases revisada para os códigos fora do vínculo: fica para depois. Cada entrada precisa de conferência humana com fonte oficial.

## Atualização em 2026-11-13

- O ADR-0052 revê os itens 5 e 6 da decisão: entram o nome civil da casa e o nome sem título, e o homônimo é desempatado pelo mandato federal nos anos das emendas.
- No ensaio local de 2026-11-13, os 7 códigos que estavam fora foram ligados.
