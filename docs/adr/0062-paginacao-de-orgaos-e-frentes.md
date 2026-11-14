# ADR-0062: Paginação de órgãos, comissões e frentes parlamentares

- **Data**: 2026-11-14
- **Status**: Aceito

## Contexto

O [ADR-0008](0008-detalhes-sob-demanda.md) manteve `orgaos_e_frentes` inteiro dentro do arquivo principal do perfil (`politicos/{id}.json`). Com o acúmulo de frentes parlamentares na Câmara dos Deputados e no Senado Federal ao longo das legislaturas cobertas, dezenas de perfis ultrapassaram 200 KB. Em verificação nos dados publicados, 71 perfis superavam esse patamar e o maior deles (`cam-98057.json`) chegava a 280.138 bytes, dos quais 189.455 bytes pertenciam apenas a 648 entradas de `orgaos_e_frentes` (77 órgãos/comissões e 571 frentes).

Ao mesmo tempo, o cabeçalho do perfil e a página de comparação leem de imediato os cargos de liderança em andamento e a lista resumida de comissões e órgãos, sem exigir novo download antes de exibir o perfil.

## Decisão

1. **Limite inline em `Publicador`**:
   - Perfis com até 40 registros em `orgaos_e_frentes` mantêm a lista completa em `registros` dentro de `politicos/{id}.json`, sem arquivo auxiliar.
   - Perfis acima de 40 registros gravam a lista completa ordenada em `politicos/{id}/orgaos_e_frentes.json.gz`, validada pelo schema `detalhe` com `tipo = "orgaos_e_frentes"` e `ano = null`.
   - No perfil, o bloco passa a publicar `por_tipo`, `arquivo_completo` (`"politicos/{id}/orgaos_e_frentes.json.gz"`) e `recentes`.
   - A seleção de `recentes` preserva todas as entradas que não são `frente` (`orgao` e `comissao`), todas as `frente`s cujo `papel` indica liderança e até 20 das demais `frente`s (ordenadas da data de início mais recente para a mais antiga e pelo nome).

2. **Contrato dos schemas**:
   - `schemas/detalhe.schema.json` passa a incluir `"orgaos_e_frentes"` em `tipo`, exigindo `ano: null` para esse tipo e mantendo `ano` inteiro maior ou igual a 2000 para os blocos anuais.
   - `schemas/politico.schema.json` (`bloco_orgaos`) exige exatamente uma de duas representações: `registros` inline (sem `recentes` nem `arquivo_completo`) ou `recentes` acompanhado de `arquivo_completo` (sem `registros`).

3. **Frontend sob demanda**:
   - O bloco no perfil continua exibindo `por_tipo` (com rótulos legíveis para comissão, frente parlamentar e órgão) e as lideranças ativas logo na abertura da página.
   - Quando `arquivo_completo` está presente, a tabela exibe primeiro `recentes` e oferece o botão para baixar e descompactar o `.json.gz` completo na própria seção, com estados de carregamento e erro.

## Consequências

- Todos os perfis publicados permanecem abaixo de 200 KB no carregamento inicial (na publicação sobre os 2.368 perfis reais, `cam-98057.json` caiu de 280.138 para 129.771 bytes, o maior arquivo passou a 170.979 bytes e nenhum perfil superou 200 KB; o detalhe `orgaos_e_frentes.json.gz` de `cam-98057` ocupou 17.546 bytes).
- Nenhuma comissão ou cargo de liderança desaparece da visão inicial do perfil nem do Lado a lado.
- O arquivo auxiliar mantém o mesmo formato de proveniência por registro e a mesma compactação gzip já usados nos detalhes anuais.
