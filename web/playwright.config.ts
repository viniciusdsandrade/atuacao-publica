import { defineConfig, devices } from '@playwright/test';

const PORTA = Number(process.env['E2E_PORTA'] ?? 4173);
const EM_CI = !!process.env['CI'];

// Testes de ponta a ponta e de acessibilidade (ADR-0028). Rodam contra o build do site (npm run build)
// servido por e2e/servidor.mjs, com dados sintéticos. Nenhuma chamada de rede externa.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: EM_CI,
  retries: EM_CI ? 1 : 0,
  reporter: EM_CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://127.0.0.1:${PORTA}`,
    locale: 'pt-BR',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'celular', use: { ...devices['Pixel 7'] } },
  ],
  webServer: {
    command: 'node e2e/servidor.mjs',
    url: `http://127.0.0.1:${PORTA}/`,
    reuseExistingServer: !EM_CI,
    timeout: 30_000,
  },
});
