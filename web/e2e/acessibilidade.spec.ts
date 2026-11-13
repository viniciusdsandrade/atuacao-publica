import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Auditoria WCAG 2.1 AA com axe-core (ADR-0028). Cada rota é varrida nos temas claro e escuro,
// no desktop e no celular emulado. Qualquer violação falha o teste.
const TAGS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

const ROTAS: Array<[nome: string, caminho: string]> = [
  ['ponto de partida', '/'],
  ['lista completa', '/#/?lista=completa'],
  ['lista de um estado, com chips de cargo', '/#/?uf=SP'],
  ['perfil de deputado federal', '/#/politico/cam-900001'],
  ['emendas de um ano', '/#/politico/cam-900001/emendas/2026'],
  ['perfil de vereador da capital', '/#/politico/cmsp-940012'],
  ['perfil de vereador de Caraguatatuba', '/#/politico/sagl-3510500-950016'],
  ['perfil de governador', '/#/politico/tse-990000000010'],
  ['lado a lado', '/#/comparar'],
  ['dados abertos', '/#/dados-abertos'],
  ['estado das coletas', '/#/estado'],
  ['metodologia', '/#/metodologia'],
  ['fontes', '/#/fontes'],
];

async function esperarConteudo(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  await page.waitForLoadState('networkidle');
}

for (const tema of ['claro', 'escuro'] as const) {
  test.describe(`tema ${tema}`, () => {
    test.use({ colorScheme: tema === 'escuro' ? 'dark' : 'light' });

    for (const [nome, caminho] of ROTAS) {
      test(`${nome} sem violações de WCAG 2.1 AA`, async ({ page }) => {
        await page.goto(caminho);
        await esperarConteudo(page);

        const resultado = await new AxeBuilder({ page }).withTags(TAGS_WCAG).analyze();
        const resumo = resultado.violations.map((v) => ({
          regra: v.id,
          impacto: v.impact,
          ocorrencias: v.nodes.length,
          exemplos: v.nodes.slice(0, 3).map((n) => n.target.join(' ')),
        }));
        expect(resumo, `violações em ${caminho}`).toEqual([]);
      });
    }
  });
}
