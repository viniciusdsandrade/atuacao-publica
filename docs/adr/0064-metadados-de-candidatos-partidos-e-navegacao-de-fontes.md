# ADR-0064: Metadados de candidatos, partidos, navegação de fontes e WCAG 2.2 AA

- **Status**: Aceita
- **Data**: 2026-11-14

## Contexto

Com a ampliação da cobertura estadual e a entrada de candidatos ligados apenas ao TSE, apareceram lacunas de leitura e navegação na interface:

1. **Nomes do TSE em maiúsculas.** Candidatos eleitos ou suplentes sem vínculo com casa legislativa recebiam o `nome_urna` bruto do CSV do TSE, inteiro em caixa alta (`ANDRÉ DO PRADO`, `MARIA DA PENHA`).
2. **Siglas sem nome por extenso.** Partidos nos selos e filtros e tipos de proposição nos resumos (`REQ`, `RPD`, `PRL`, `RQS`, `PLP`) apareciam apenas como sigla técnica. Colocar o nome completo do partido em cada item de `index.json` aumentaria o arquivo principal e mudaria o esquema sem ganho real.
3. **Nota de eventos desatualizada.** A nota de `eventos` em `rotulos.ts` ainda não citava ALPB, ALEAM, ALERO, ALEPI e ALEAC após a entrada de votações e presenças via SAPL.
4. **Estado das coletas sem salto direto para a fonte.** A lista de fontes em `#/estado` mostrava o nome de cada fonte sem ligação direta para a ficha correspondente em `docs/FONTES.md`. Além disso, `Doc` lia o fragmento da URL uma única vez ao concluir a carga, não movia o foco para o título na chegada por rota e não reagia a mudanças de fragmento já dentro de `#/fontes`.
5. **Requisitos da WCAG 2.2 nível AA.** Faltava checar e garantir alvo mínimo em links isolados de interface (migalhas, links de fonte em tabelas e metadados), compensação de cabeçalho fixo de tabela para foco não encoberto (`scroll-padding-top` no contêiner com rolagem), escape de teclado no sumário móvel e varredura automatizada com marcação `wcag22aa` nos temas claro e escuro e em tela estreita.

## Decisão

1. **`Texto.capitalizarNome` no ETL.** Nomes em caixa uniforme (tudo maiúsculo ou tudo minúsculo) passam para capitalização de nome próprio brasileiro. As preposições e a conjunção `da`, `das`, `de`, `do`, `dos` e `e` ficam em minúscula depois da primeira palavra. Partes separadas por hífen ou apóstrofo são capitalizadas, algarismos romanos finais (`II`, `III`) ficam em maiúsculas e grafias mistas já vindas das casas ficam intactas. `Vinculo.perfilDoEleito` aplica a função ao definir `p.nome`. O campo `nome_urna` no bloco de candidaturas permanece como na fonte original para preservar rastreabilidade e chaves de vínculo.
2. **Nomes completos de partidos por tabela de código.** `Partidos.nomeCompleto` no ETL e `NOMES_DOS_PARTIDOS` / `nomeDoPartido` em `web/src/app/rotulos.ts` mapeiam as siglas canônicas do TSE (ativas e históricas) sem alterar `schemas/indice.schema.json`. A interface exibe sigla e nome completo no filtro de partidos, no selo do perfil e na ficha de comparação, e a busca da lista indexa também o nome por extenso. Nos cartões da lista, o texto visível permanece compacto na sigla, com o nome completo no rótulo acessível.
3. **Legenda de tipos de proposição e nota de eventos.** `TIPOS_PROPOSICAO` e `rotuloTipoProposicao` em `web/src/app/rotulos.ts` traduzem as siglas oficiais da Câmara, do Senado e dos sistemas SAPL exibidas nos resumos `por_tipo` do perfil e da comparação, sem duplicar termos quando a fonte já entrega o tipo por extenso. `NOTAS['eventos']` passa a incluir ALPB, ALEAM, ALERO, ALEPI e ALEAC.
4. **Âncoras diretas de `#/estado` para `#/fontes#doc-...`.** Cada identificador de fonte em `Estado` aponta para a seção exata gerada a partir dos títulos de `docs/FONTES.md`. Um teste unitário verifica todas as chaves contra o Markdown real de `docs/FONTES.md` para impedir âncoras quebradas em futuras edições. `Doc` passa a reagir ao sinal `fragmento()` sempre que o documento está pronto, rolando até a seção e movendo o foco programático para o título correspondente.
5. **Ajustes e cobertura WCAG 2.2 AA.**
   - Links de ação em migalhas, células de tabela (`ver na fonte`, `inteiro teor`), metadados e rodapé ganham altura mínima confortável (`min-height: 2rem` ou equivalente em bloco clicável) e espaçamento para não violar tamanho e separação mínima de alvo.
   - `.tabela-rolagem` recebe `scroll-padding-top` quando o cabeçalho fica fixo no topo da área de rolagem, mantendo o elemento focado visível acima das linhas seguintes e abaixo do cabeçalho.
   - O sumário móvel de `Doc` fecha com `Escape` devolvendo o foco ao botão que o abriu.
   - Os testes de Playwright verificam `wcag22aa` nos dois temas (claro e escuro), em computador e em celular (`390px`), cobrindo também blocos expandidos e a navegação `#/estado` para `#/fontes#doc-...`.

## Consequências

- Perfis vindos só do TSE passam a aparecer legíveis nas listas e cabeçalhos após a próxima coleta e publicação de vínculo.
- O arquivo `index.json` não cresce com a repetição dos nomes completos dos partidos.
- Novos tipos de proposição ou novas fontes adicionadas a `contrib/fontes.yaml` são protegidos pelo teste de âncoras no frontend e caem na sigla bruta quando não estão no dicionário de tipos.
- Auditorias manuais com leitores de tela reais, teste em aparelho móvel físico e testes de usabilidade com cinco pessoas continuam necessários fora da automação.
