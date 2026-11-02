# ADR-0010: Cartão de pagamento e viagens da Presidência da República

- Status: aceita
- Data: 2026-11-02

## Contexto

O perfil do Presidente tem mandato, candidaturas, atos normativos e agenda por contribuição, mas nenhum gasto.
O Portal da Transparência publica, sem chave, o cartão de pagamento do Governo Federal (um ZIP por mês, `download-de-dados/cpgf/{AAAAMM}`, cerca de 300 KB) e as viagens a serviço (um ZIP por ano, `download-de-dados/viagens/{ano}`, cerca de 160 MB), conferidos em 2026-11-02 (`docs/FONTES.md`).
Os dois arquivos trazem dado pessoal: CPF mascarado e nome do portador do cartão, nome do favorecido, CPF mascarado e nome do viajante.
No cartão, a maior parte das transações da Presidência vem como "Informações protegidas por sigilo": sem data nem favorecido, mas com o valor.

## Decisão

1. O perfil de presidente ganha o bloco `despesas_executivo`, no mesmo formato agregado do ADR-0008: valor por mês e por categoria, com a quantidade de lançamentos, sem arquivo de detalhe.
2. Entra o órgão `20101`, Presidência da República, do órgão superior `20000`. Ficam fora a Empresa Brasil de Comunicação e o Gabinete da Vice-Presidência, que têm ordenador próprio e não pertencem ao perfil do Presidente.
3. Cartão: soma do `VALOR TRANSAÇÃO` por ano e mês do extrato e por unidade gestora, na categoria "Cartão de pagamento: {unidade gestora}". Transação sob sigilo entra pelo valor. Portador e favorecido nunca são lidos.
4. Viagens: só as com situação `Realizada`, no mês da data de início, nas categorias "Diárias de viagem" (diárias menos devolução), "Passagens" e "Outros gastos de viagem". Viajante, cargo e motivo nunca são lidos.
5. O ZIP anual de viagens não fica no cache: a coleta guarda só as linhas do órgão superior Presidência da República, no mesmo formato CSV. O meta registra a URL e o hash do ZIP original.
6. Rótulo no site: "cartão de pagamento e viagens da Presidência da República no período do mandato". A fonte não informa quem autorizou cada gasto, e o site não afirma isso.
7. Adaptador `Cgu.java`, fonte `cgu` na CLI. A ligação com o perfil usa o período do mandato em `contrib/mandatos.yaml` e o ente `BR`, como os atos normativos.

## Consequências

- O Presidente passa a ter um bloco de gastos com proveniência (URL do arquivo e data de captura).
- O valor do cartão da Presidência é, em grande parte, gasto do Gabinete de Segurança Institucional e da Agência Brasileira de Inteligência, que estão no órgão. A categoria mostra a unidade gestora.
- O cache de dados brutos cresce cerca de 20 MB por ano, não 160 MB.
- Os 6 meses mais recentes do cartão e o ano corrente de viagens são renovados toda semana; o resto vem do cache.

## Alternativas consideradas

- API do Portal da Transparência com chave: descartado. Exige cadastro, e os arquivos abertos trazem o mesmo dado.
- Publicar o lançamento individual: descartado. Nome de portador, favorecido e viajante não entram no site.
- Somar todo o órgão superior Presidência da República: descartado. Inclui empresa pública e o gabinete do Vice-Presidente.
