# ADR-0028: Testes de ponta a ponta e auditoria de acessibilidade no CI

- **Status**: Aceita
- **Data**: 2026-11-06

## Contexto

O CI do site só construía o Angular (`npm run build`).
Os testes unitários da web (`npm test`) existiam, mas não rodavam no CI.
A auditoria de acessibilidade existia só como verificação manual e local.
Uma mudança de interface ficava sem rede de segurança, e regressões de acessibilidade só apareciam por inspeção.

## Decisão

1. **Ferramentas**: Playwright Test (`@playwright/test` 1.64.1) e axe-core por `@axe-core/playwright` 4.14.0. Ficam em `devDependencies`, na versão exata. Só Chromium. Foram escolhidas versões com mais de duas semanas de publicação: a 1.65.0 do Playwright saiu em 2026-11-05.
2. **Dados**: sintéticos, montados em memória por `web/e2e/dados-sinteticos.mjs` a partir de `schemas/exemplos/validos`. O ETL já valida esses exemplos em cada build, então eles têm a forma real. Nomes e números são fictícios. O servidor `web/e2e/servidor.mjs` usa só Node e escuta em `127.0.0.1`. Nenhuma chamada a terceiros.
3. **Fluxos (E2E)**: lista (busca, UF e chip de partido), perfil (cartão, rota direta e perfil inexistente), comparação, dados abertos, documento, rota desconhecida e teclado (link de pular para o conteúdo). Os seletores usam papéis e rótulos acessíveis.
4. **Acessibilidade**: axe com as tags WCAG 2.0 A e AA e 2.1 A e AA. Nove rotas, nos temas claro e escuro, no desktop e no celular emulado (Pixel 7). Qualquer violação falha o teste.
5. **CI**: o job `web` passa a rodar testes unitários, build, instalação do Chromium e a suíte. Em falha, o relatório sobe como artefato por sete dias, com a action fixada por SHA (ADR-0004).
6. **Uso local**: `cd web && npx playwright install chromium && npm run e2e`.

## Achados da primeira varredura

A primeira execução achou dois defeitos reais. Ambos foram corrigidos na origem:

- O nome acessível do botão de tema não continha o texto visível "Auto" (WCAG 2.5.3, rótulo no nome). O rótulo passou a conter "Auto", "Claro" ou "Escuro", e um teste unitário protege a regra.
- A tabela rolável de `/dados-abertos` não tinha foco por teclado (WCAG 2.1.1). Os três contêineres `.tabela-rolagem` viraram regiões focáveis e nomeadas.

## Consequências

- Regressão de fluxo e de acessibilidade passa a falhar o CI, e não a ser descoberta depois.
- O CI baixa o Chromium a cada execução, o que acrescenta cerca de um minuto.
- Duas devDependencies novas, só de teste. O site publicado não muda de tamanho.

## Limitações

- O axe não substitui teste com leitor de tela real (VoiceOver, NVDA, TalkBack). Segue não validado.
- Os dados são sintéticos. Tabelas longas e casos raros de dado real não são varridos.
- Só Chromium. Firefox e WebKit ficam de fora.
- A varredura cobre o estado inicial de cada página, não todos os estados interativos.

## Alternativas consideradas

- Rodar os testes contra o `data/publicado` real: descartado. O CI de código não tem os dados, e o resultado mudaria a cada coleta.
- `pa11y` ou `lighthouse-ci`: descartado. Playwright já cobre o fluxo, e o axe roda na mesma sessão.
- Cypress: descartado. Mais pesado, e o Playwright dispensa servidor de teste próprio.
