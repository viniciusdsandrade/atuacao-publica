# ADR-0059: Preservação de ex-deputados e suplentes nas assembleias e câmaras

- **Data**: 2026-11-14
- **Status**: Aceito

## Contexto

O [ADR-0006](0006-cobertura-historica-e-arquivo.md) determina que toda pessoa que exerceu mandato coberto mantém perfil publicado, mesmo após deixar o exercício, e que a publicação é bloqueada quando um perfil da publicação anterior desaparece.

Em 2026-11-14, o workflow diário de coleta falhou no passo `publish` porque a listagem de deputados da ALESC (`/deputados/`) foi atualizada após o retorno do titular e a saída da suplente `alesc-carolline-sarda`. Como o adaptador da ALESC iterava apenas sobre os nomes da listagem ativa (`deputados.html`), o perfil sumiu na normalização e ativou a trava do ADR-0006.

Uma auditoria nos demais adaptadores mostrou que a Câmara dos Deputados, o Senado Federal, a ALESP e os adaptadores SAPL (piloto municipal, `alpb`, `aleam`, `aleal`, `alero`, `alepi`, `aleto`, `aleac`, `alerr`) já consultam o histórico de mandatos ou a lista completa da legislatura. Já os demais 17 adaptadores estaduais que leem listagens apenas dos parlamentares em exercício (`alap`, `alba`, `alece`, `alego`, `alema`, `alems`, `alep`, `alepa`, `alepe`, `alerj`, `ales`, `alesc`, `alese`, `almt`, `alrn`, `alrs`, `cldf`) e a `cmsp` (que filtra pela ocupação vigente de gabinete) ficavam sujeitos ao mesmo problema a qualquer licença, renúncia, cassação ou troca de suplente.

Na ALESC, verificou-se também que o site oficial expõe todas as páginas individuais (`/deputado/{slug}/`) no índice `post_team-sitemap.xml`, incluindo suplentes e licenciados da 20ª legislatura (com data de publicação no portal a partir de `2023-02-01` ou atuação em despesas, votações e proposições desde a mesma data).

## Decisão

1. **Registro acumulado de parlamentares vistos na coleta (`HistoricoDeputados`)**:
   - Os adaptadores estaduais baseados em listagem de parlamentares em exercício e a `cmsp` passam a manter o arquivo `data/raw/{fonte}/historico-deputados.json` no cache bruto.
   - O registro é atualizado no `fetch` antes e depois de renovar a listagem principal (evitando perda mesmo quando apenas `fetch` roda na coleta local cifrada do ADR-0034) e novamente no `normalize`.
   - Quem esteve em exercício na legislatura coberta e sai da listagem ativa permanece normalizado com `em_exercicio = false` e `situacao = "Fora de exercício"`, preservando a proveniência (`Prov`), o último partido observado, a foto e o vínculo com despesas, proposições, votações e comissões.
   - Quando a pessoa retorna à listagem ativa em atualização futura, volta automaticamente para `em_exercicio = true`.

2. **Descoberta oficial complementar na ALESC (`post_team-sitemap.xml`)**:
   - Além do histórico acumulado no cache, `Alesc` coleta `https://www.alesc.sc.gov.br/post_team-sitemap.xml` e as páginas individuais `/deputado/{slug}/`.
   - Slugs ausentes de `/deputados/` entram como `Fora de exercício` na 20ª legislatura quando têm registro prévio na listagem da legislatura, data de publicação da ficha no portal a partir de `2023-02-01` ou atuação registrada em despesas, sessões plenárias ou proposições desde `2023-02-01`. Isso evita incluir deputados exclusivos de legislaturas anteriores já encerradas.
   - Quando a ficha da ALESC informa explicitamente `Situação` (`Suplente` ou `Titular`), o valor preenche `condicao` do mandato.

## Consequências

- Trocas de suplentes, licenças e afastamentos deixam de quebrar o `publish` em todas as assembleias cobertas e na CMSP.
- Despesas, proposições e votações de suplentes e licenciados da 20ª legislatura na ALESC deixam de ficar órfãos na normalização.
- Como os demais 16 portais estaduais consultados não expõem índice equivalente de suplentes/afastados em endpoint aberto, uma reconstrução a partir do zero fora do cache recupera apenas quem estiver na listagem ativa no momento da reconstrução; enquanto o cache bruto (`actions/cache` e `semente`) for preservado, o histórico da legislatura fica protegido desde a primeira coleta em que cada parlamentar apareceu.
