import { expect, test } from '@playwright/test';

// Fluxos principais do site, com dados sintéticos (e2e/dados-sinteticos.mjs).
// Os seletores usam papéis e rótulos acessíveis: se o teste não acha o controle, o leitor de tela também não acharia.

test.describe('lista de políticos', () => {
  test('a página inicial começa pelas portas de entrada, sem listar ninguém', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Atividade de políticos brasileiros');
    await expect(page.getByRole('heading', { level: 2, name: 'Por onde começar' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Pelo estado' }).getByRole('link', { name: /São Paulo/ })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Pelo cargo' }).getByRole('link', { name: /Senador/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);
  });

  test('o estado escolhido abre a lista, e Voltar retorna ao ponto de partida', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation', { name: 'Pelo estado' }).getByRole('link', { name: /Santa Catarina/ }).click();
    await expect(page).toHaveURL(/uf=SC/);
    await expect(page.getByRole('link', { name: /Irene Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 2, name: /de [\d.]+ políticos/ })).toBeFocused();

    await page.goBack();
    await expect(page.getByRole('heading', { level: 2, name: 'Por onde começar' })).toBeVisible();
    await expect(page.getByRole('link', { name: /Irene Exemplo/ })).toHaveCount(0);
  });

  test('"Ver todos" mostra os cartões do índice, e "Limpar tudo" volta ao ponto de partida', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('link', { name: /Ver todos os \d+ em exercício/ }).click();
    await expect(page).toHaveURL(/lista=completa/);
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Leandro Exemplo/ })).toBeVisible();

    await page.getByRole('button', { name: 'Limpar tudo' }).click();
    await expect(page.getByRole('heading', { level: 2, name: 'Por onde começar' })).toBeVisible();
  });

  test('com um estado escolhido, os cargos do recorte viram chips', async ({ page }) => {
    await page.goto('/#/?uf=SP');
    await page.getByRole('radio', { name: /^Senador/ }).check({ force: true });
    await expect(page.getByRole('link', { name: /Fabio Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);
  });

  test('busca por nome e limpa a busca', async ({ page }) => {
    await page.goto('/');
    const busca = page.getByLabel(/Buscar por nome/);
    await busca.fill('Leandro');
    await expect(page.getByRole('link', { name: /Leandro Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);

    // Sem busca e sem recorte, a página volta ao ponto de partida.
    await page.getByRole('button', { name: 'Limpar a busca' }).click();
    await expect(page.getByRole('heading', { level: 2, name: 'Por onde começar' })).toBeVisible();
    await expect(page.getByRole('link', { name: /Leandro Exemplo/ })).toHaveCount(0);
  });

  test('Enter na busca leva o foco à contagem, logo acima dos cartões', async ({ page }) => {
    await page.goto('/');
    const busca = page.getByLabel(/Buscar por nome/);
    await busca.fill('Leandro');
    // O retorno fica ao lado do campo: a lista está abaixo dos filtros, fora da primeira tela.
    await expect(page.getByRole('button', { name: 'Ver na lista' })).toBeVisible();
    await busca.press('Enter');
    await expect(page.getByRole('heading', { level: 2, name: /de [\d.]+ políticos/ })).toBeFocused();
  });

  test('filtra por UF no seletor', async ({ page }) => {
    await page.goto('/#/?lista=completa');
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
    await page.goto('/#/?lista=completa');
    await page.getByRole('radio', { name: /^PT\b/ }).check({ force: true });
    await expect(page.getByRole('link', { name: /Bruno Exemplo/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toHaveCount(0);

    await page.getByRole('button', { name: /Remover filtro/ }).first().click();
    await expect(page.getByRole('link', { name: /Ana Exemplo/ })).toBeVisible();
  });
});

test.describe('perfil', () => {
  test('abre o perfil pelo cartão e mostra os blocos', async ({ page }) => {
    await page.goto('/#/?lista=completa');
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

  test('perfil de vereador de Caraguatatuba diz o que a câmara publica', async ({ page }) => {
    await page.goto('/#/politico/sagl-3510500-950016');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Paula Exemplo');
    await expect(page.getByText('Caraguatatuba (SP)').first()).toBeVisible();
    // Diária não é cota parlamentar: na esfera municipal o bloco tem outro título (ADR-0057).
    await expect(page.getByRole('heading', { level: 2, name: 'Despesas informadas pela câmara municipal' })).toBeVisible();
    // A situação de tramitação fica vazia, e a nota do bloco diz por quê.
    await expect(page.getByText(/não mantém a situação de tramitação/)).toBeVisible();
  });

  test('diária mostra destino e motivo abaixo da categoria', async ({ page }) => {
    await page.goto('/#/politico/sagl-3510500-950016/despesas/2026');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('2026');
    await expect(page.getByText('Destino: São Paulo/SP - Brasil. Motivo: Reunião de exemplo.')).toBeVisible();
  });

  test('falha de rede no perfil oferece tentar de novo e recupera', async ({ page }) => {
    let falhar = true;
    await page.route('**/dados/politicos/cam-900001.json', (rota) => (falhar ? rota.abort('failed') : rota.continue()));
    await page.goto('/#/politico/cam-900001');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Não foi possível carregar o perfil');
    falhar = false;
    await page.getByRole('button', { name: 'Tentar de novo' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Ana Exemplo');
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
