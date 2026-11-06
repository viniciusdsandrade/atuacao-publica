# ADR-0027: Segundo município piloto: Câmara Municipal de São Paulo (CMSP)

- **Status**: Aceita
- **Data**: 2026-11-06

## Contexto

O piloto municipal (ADR-0007) cobre uma câmara que usa SAPL.
O plano prevê um segundo município, capital de estado, para provar que o modelo municipal aceita outra casa e outro sistema.

Duas câmaras de capital foram avaliadas em 2026-11-06:

- Câmara Municipal de Curitiba: `www.cmc.pr.gov.br` respondeu 403 à requisição direta. O projeto não contorna bloqueio.
- Câmara Municipal de São Paulo: o serviço oficial SPLegisWS (`https://splegisws.saopaulo.sp.leg.br/ws/ws2.asmx`) responde JSON por GET, sem autenticação.

A CMSP não usa SAPL.
O serviço devolve:

- `VereadoresCMSPJSON`: 364 vereadores desde a década de 1990, com `chave`, nome, mandatos, filiações e cargos em comissões e na Mesa. Não traz CPF, telefone nem e-mail.
- `OcupacaoGabineteJSON`: ocupação dos 55 gabinetes por legislatura. O `codigo` é a `chave` do vereador.
- `ComissoesCMSPJSON`: comissões, com sigla.

A lista de mandatos vigentes traz 64 nomes: 55 titulares e 9 suplentes sem exercício.
Só a ocupação de gabinete diz quem está em exercício.

## Decisão

1. **Escopo**: vereadores em exercício na 19ª Legislatura (2025 a 2028), com mandato, partido, comissões e Mesa Diretora.
2. **Em exercício**: o vereador ocupa um gabinete vigente na legislatura mais recente e tem mandato vigente. A ligação entre gabinete e vereador usa a chave, não o nome.
3. **Identificador**: `cmsp-{chave}`. Chave em `ids_externos`: `cmsp`, regex `^[0-9]+$`. Cargo `vereador`, esfera `municipal`, ente `3550308`, uf `SP`.
4. **Fontes**: `cmsp-splegisws` (API) e `cmsp-portal` (link oficial). Sem foto: o portal não expõe foto por vereador em HTML estático, e o projeto não executa JavaScript de terceiros.
5. **Vínculo com o TSE**: nome de urna e nome civil normalizados, dentro da unidade eleitoral `71072`. As chaves de vereador passam a incluir o município, porque dois municípios podem ter vereadores com o mesmo nome.
6. **Eleições cobertas**: cada unidade eleitoral municipal ganha a sua primeira eleição. A de São Paulo começa em 2024. Em 2020 o eleito prefeito deixou o cargo em 2021 e o vice assumiu. A regra de mandato do projeto (art. 29, II da Constituição, em `contrib/mandatos.yaml`) supõe que o eleito exerce os quatro anos, e isso seria falso para essa eleição.
7. **Executivo**: prefeito e vice de 2024 entram pelo TSE, com regras de mandato do ente `3550308` em `contrib/mandatos.yaml`.
8. **Privacidade**: o adaptador lê só `chave`, nome, mandatos, filiações e cargos. Nenhum campo de contato é lido.

## Consequências

- O modelo municipal passa a cobrir duas câmaras, uma com SAPL e outra com serviço próprio.
- O vínculo de vereadores com o TSE deixa de depender de um município único.
- O perfil do vereador de São Paulo tem mandato, partido, comissões e Mesa. Candidaturas aparecem a partir de 2024.

## Limitações

- Proposições, votações nominais e presença ficam para outra fase. O serviço não tem operação de voto nominal por vereador.
- Despesas do Executivo: a API do TCE-SP devolve lista vazia para a capital (verificado em 2026-11-06). Não há fonte validada, e o bloco `despesas_executivo` não existe para o prefeito de São Paulo.
- Os 9 suplentes sem exercício e quem deixou a vereança na legislatura ficam fora.
- Não validado a partir do runner do GitHub Actions. O passo `fetch cmsp` usa `continue-on-error: true`, como as outras casas.

## Alternativas consideradas

- Câmara Municipal de Curitiba: descartada pelo 403.
- Raspar a página de membros da CMSP: descartada. Os cartões não vêm no HTML estático.
- Marcar em exercício pela lista de mandatos: descartada. Ela inclui suplentes sem exercício.
- Cobrir a eleição de 2020 da capital: descartada pela sucessão no cargo de prefeito.
