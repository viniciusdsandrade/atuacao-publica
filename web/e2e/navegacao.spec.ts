import { expect, test } from '@playwright/test';

// Fluxos principais do site, com dados sintéticos (e2e/dados-sinteticos.mjs).
// Os seletores usam papéis e rótulos acessíveis: se o teste não acha o controle, o leitor de tela também não acharia.

test.describe('lista de políticos', () => {
  test('mostra o título e os cartões do índice', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Atividade de políticos brasileiros');
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Leandro Exemplo/ })).toBeVisible();
  });

  test('busca por nome e limpa a busca', async ({ page }) => {
    await page.goto('/');
    const busca = page.getByLabel(/Buscar por nome/);
    await busca.fill('Leandro');
    await expect(page.getByRole('link', { name: /Leandro Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);

    await page.getByRole('button', { name: 'Limpar a busca' }).click();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toBeVisible();
  });

  test('filtra por UF no seletor', async ({ page }) => {
    await page.goto('/');
    const uf = page.getByLabel('UF', { exact: true });
    if (!(await uf.isVisible())) {
      // No celular os filtros extras ficam recolhidos.
      await page.getByRole('button', { name: /Mais filtros/ }).click();
    }
    await uf.selectOption('SC');
    await expect(page.getByRole('link', { name: /Irene Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);
  });

  test('chip de partido filtra a lista e o filtro pode ser removido', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('radio', { name: /^PT\b/ }).check({ force: true });
    await expect(page.getByRole('link', { name: /Bruno Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);

    await page.getByRole('button', { name: /Remover filtro/ }).first().click();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toBeVisible();
  });
});

test.describe('perfil', () => {
  test('abre o perfil pelo cartão e mostra os blocos', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: /Ana Exemplo/ }).first().click();
    await expect(page).toHaveURL(/#\/politico\/cam-900001$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Ana Exemplo');
    await expect(page.getByRole('navigation', { name: 'Blocos do perfil' })).toBeVisible();
  });

  test('emendas mostram a soma por ano e levam ao detalhe do ano com o autor na fonte', async ({ page }) => {
    await page.goto('/#/politico/cam-900001');
    const bloco = page.locator('#bloco-emendas');
    await expect(bloco.getByRole('heading', { level: 2 })).toContainText('Emendas individuais ao Orçamento da União');
    await expect(bloco.getByRole('table', { name: 'Por ano do orçamento' })).toContainText('2026');
    await bloco.getByRole('link', { name: /2026/ }).click();
    await expect(page).toHaveURL(/#\/politico\/cam-900001\/emendas\/2026$/);
    await expect(page.getByText(/Autor na fonte: PESSOA EXEMPLO/)).toBeVisible();
  });

  test('financiamento de campanha abre a eleição com receitas por fonte e fornecedores com CNPJ', async ({ page }) => {
    await page.goto('/#/politico/cam-900001');
    const bloco = page.locator('#bloco-financiamento_campanha');
    await expect(bloco.getByRole('heading', { level: 2 })).toContainText('Financiamento de campanha');
    // O primeiro summary do bloco é o da proveniência; a eleição é a sanfona de cada ano.
    await bloco.locator('details.ano-despesa > summary').first().click();
    await expect(bloco.getByRole('table', { name: 'Receitas por fonte em 2026' })).toContainText('FUNDO ESPECIAL');
    await expect(bloco.getByRole('table', { name: 'Maiores fornecedores com CNPJ em 2026' })).toContainText('99999999000199');
  });

  test('redes e sites declarados ao TSE aparecem no topo com o rótulo de declaração do próprio candidato', async ({ page }) => {
    await page.goto('/#/politico/tse-990000000010');
    await expect(page.getByText(/declarados ao TSE pelo próprio candidato, na eleição de 2022/)).toBeVisible();
    const lista = page.getByRole('list', { name: 'Redes e sites declarados ao TSE' });
    await expect(lista.getByRole('link', { name: /campanha\.exemplo\.invalid/ })).toHaveAttribute('href', 'https://campanha.exemplo.invalid/');
  });

  test('perfil de vereador da capital abre pela rota direta', async ({ page }) => {
    await page.goto('/#/politico/cmsp-940012');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Leandro Exemplo');
    await expect(page.getByText('São Paulo').first()).toBeVisible();
  });

  test('perfil inexistente mostra a mensagem de erro', async ({ page }) => {
    await page.goto('/#/politico/nao-existe');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Perfil não encontrado');
  });
});

test.describe('lado a lado', () => {
  test('põe dois parlamentares da mesma casa lado a lado', async ({ page }) => {
    await page.goto('/#/comparar');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Lado a lado');

    await page.getByLabel('Escolha o primeiro parlamentar:').fill('Ana');
    await page.getByRole('button', { name: /Ana Exemplo/ }).click();
    await page.getByLabel('Escolha o segundo parlamentar:').fill('Bruno');
    await page.getByRole('button', { name: /Bruno Exemplo/ }).click();

    const comparativo = page.getByRole('region', { name: 'Dados comparativos' });
    await expect(comparativo).toContainText('Ana Exemplo');
    await expect(comparativo).toContainText('Bruno Exemplo');
  });
});

test.describe('dados abertos e documentos', () => {
  test('lista os arquivos do manifest com link de download', async ({ page }) => {
    await page.goto('/#/dados-abertos');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Dados abertos');
    await expect(page.locator('a[href$="dados/dumps/politicos.csv"]').first()).toBeVisible();
  });

  test('a metodologia carrega o documento', async ({ page }) => {
    await page.goto('/#/metodologia');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('rota desconhecida volta para a lista', async ({ page }) => {
    await page.goto('/#/nao-existe');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Atividade de políticos brasileiros');
  });
});

test.describe('teclado', () => {
  test('o primeiro Tab chega ao link de pular para o conteúdo, e Enter leva ao conteúdo principal', async ({ page, isMobile }) => {
    test.skip(isMobile, 'Teclado físico não se aplica ao celular emulado.');
    await page.goto('/');
    await page.keyboard.press('Tab');
    const pular = page.locator('a[href="#conteudo"]').first();
    await expect(pular).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('main#conteudo')).toBeFocused();
  });
});
